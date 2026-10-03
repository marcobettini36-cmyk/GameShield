package it.gameshield;

import android.content.*;
import android.net.*;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;
import android.app.Activity;
import android.webkit.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Runs on Android with the actual VpnService, native TUN, SOCKS relay and platform resolver. */
public class TunnelDeviceTest {
    private Context context;
    private ConnectivityManager cm;
    private Network physical;
    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext(); cm = context.getSystemService(ConnectivityManager.class);
        context.stopService(new Intent(context, ShieldVpnService.class)); SystemClock.sleep(1000);
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) physical = n;
        }
        assertNotNull("Underlying network required", physical);
        assertNull("Grant ACTIVATE_VPN app-op on dedicated test emulator first", VpnService.prepare(context));
    }
    @After public void tearDown() throws Exception {
        context.getSharedPreferences("shield", 0).edit().putBoolean("wanted", false).commit();
        context.stopService(new Intent(context, ShieldVpnService.class)); SystemClock.sleep(1000);
    }
    private Network vpn() {
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return n;
        }
        return null;
    }
    private Network start() throws Exception {
        context.startForegroundService(new Intent(context, ShieldVpnService.class));
        for (int i=0;i<150 && (vpn()==null || !ShieldVpnService.running);i++) SystemClock.sleep(200);
        assertTrue("Native tunnel did not start", ShieldVpnService.running); assertNotNull(vpn()); return vpn();
    }
    @Test public void testAllowedAndBlockedTrafficThroughNativeTunnel() throws Exception {
        // Baseline: don't mistake an unavailable runner network for a VPN regression.
        for (String host : ConnectivityProbe.HOSTS) https(physical, host, null);
        Network tunnel = start();
        for (String host : ConnectivityProbe.HOSTS) {
            byte[] reply = dns(tunnel, host); assertEquals(host, 0, reply[3] & 15);
            InetAddress[] addresses = tunnel.getAllByName(host); assertTrue(host, addresses.length > 0);
            try (Socket socket = new Socket()) { tunnel.bindSocket(socket); socket.connect(new InetSocketAddress(addresses[0],443),10000); }
            https(tunnel, host, addresses);
        }
        https(tunnel, "dns.google", null); // Secure DNS provider is accessible, not classified as gambling.
        for (String host : new String[]{"playzilla.com", "excitewin.com", "bet365.it", "stake.com", "new.playzilla.com"})
            assertEquals(host + " should return NXDOMAIN", 3, dns(tunnel, host)[3] & 15);
        // DNS cached before VPN activation must still be stopped by TLS SNI.
        for (String host : new String[]{"playzilla.com", "excitewin.com"}) {
            InetAddress[] cached = physical.getAllByName(host);
            try { https(tunnel, host, cached); fail(host + " cached-IP TLS bypass"); } catch (IOException expected) { }
        }
        for(int i=0;i<180 && !context.getSharedPreferences("shield",0).getBoolean("connectivityOk",false);i++) SystemClock.sleep(200);
        assertTrue(context.getSharedPreferences("shield",0).getString("error","self-test incomplete"),
            context.getSharedPreferences("shield",0).getBoolean("connectivityOk",false));
    }
    @Test public void testRelayFailurePolicyAndInternetRestoration() throws Exception {
        start(); context.startForegroundService(new Intent(context, ShieldVpnService.class).setAction("TEST_BREAK_PROXY"));
        if (BuildConfig.STRONG) {
            SystemClock.sleep(35000); assertNotNull("Strong must retain TUN on forwarding failure", vpn());
            assertFalse(context.getSharedPreferences("shield",0).getBoolean("connectivityOk",true));
        } else {
            for(int i=0;i<225 && vpn()!=null;i++) SystemClock.sleep(200);
            assertNull("Normal must release TUN after failed forwarding",vpn());
            assertFalse(ShieldVpnService.running);
            assertFalse(context.getSharedPreferences("shield",0).getBoolean("wanted",true));
            https(physical,"google.com",null);
        }
    }
    @Test public void testChromiumWebViewBrowsingThroughVpn() throws Exception {
        Network tunnel = start();
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
            new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<WebView> browser = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView view = new WebView(activity); view.getSettings().setJavaScriptEnabled(true);
            activity.setContentView(view); browser.set(view);
        });
        try {
            for (String host : ConnectivityProbe.HOSTS) {
                CountDownLatch done = new CountDownLatch(1); AtomicReference<String> error = new AtomicReference<>();
                AtomicReference<String> title = new AtomicReference<>();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    browser.get().setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView v, String url) { title.set(v.getTitle()); done.countDown(); }
                        @Override public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError e) {
                            if (request.isForMainFrame()) { error.set(e.getDescription().toString()); done.countDown(); }
                        }
                        @Override public void onReceivedSslError(WebView v, SslErrorHandler handler, android.net.http.SslError e) {
                            handler.cancel(); error.set(e.toString()); done.countDown();
                        }
                    });
                    browser.get().loadUrl("https://" + host + "/");
                });
                assertTrue(host+" WebView timeout",done.await(60,TimeUnit.SECONDS));
                assertNull(host+" WebView: "+error.get(),error.get());
                assertNotNull(host+" WebView title",title.get());
                assertFalse(title.get().isEmpty());
                android.util.Log.i("GameShieldDeviceTest", "WebView "+host+": "+title.get());
                assertNotNull("Normal must not fall open during allowed browsing",vpn());
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { browser.get().destroy(); activity.finish(); });
        }
    }
    private byte[] dns(Network network, String host) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            network.bindSocket(socket); socket.setSoTimeout(10000); socket.connect(InetAddress.getByName("198.18.0.2"),53);
            byte[] query = Dns.query(host,12345); socket.send(new DatagramPacket(query,query.length));
            DatagramPacket response = new DatagramPacket(new byte[4096],4096); socket.receive(response);
            byte[] answer = Arrays.copyOf(response.getData(),response.getLength()); assertEquals(12345,Dns.u16(answer,0)); return answer;
        }
    }
    private void https(Network network, String host, InetAddress[] known) throws IOException {
        InetAddress[] addresses = known == null ? network.getAllByName(host) : known;
        IOException failure = null;
        for (InetAddress address : addresses) {
            try (Socket socket = new Socket()) {
                network.bindSocket(socket); socket.connect(new InetSocketAddress(address,443),10000); socket.setSoTimeout(10000);
                try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,host,443,true)) {
                    SSLParameters parameters = tls.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(parameters);
                    tls.startHandshake(); tls.getOutputStream().write(("HEAD / HTTP/1.1\r\nHost: "+host+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    String status = new BufferedReader(new InputStreamReader(tls.getInputStream(),StandardCharsets.US_ASCII)).readLine();
                    if(status==null || !status.startsWith("HTTP/1.")) throw new IOException("No HTTPS response: "+host);
                    android.util.Log.i("GameShieldDeviceTest",host+" "+status); return;
                }
            } catch(IOException e) { failure=e; }
        }
        throw new IOException(host+" HTTPS failed",failure);
    }
}
