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
        context.getSharedPreferences("shield",0).edit().remove("custom").commit();
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
        for (int i=0;i<450 && (vpn()==null || !ShieldVpnService.running);i++) SystemClock.sleep(200);
        assertTrue("Native tunnel did not start: " + context.getSharedPreferences("shield",0).getString("error","no service error recorded"), ShieldVpnService.running); assertNotNull(vpn());
        // NetworkAgent visibility precedes netd's per-UID access rules on busy Android 15 boot.
        IOException registration = null;
        for(int i=0;i<40;i++) {
            try(Socket socket=new Socket()) { vpn().bindSocket(socket); return vpn(); }
            catch(IOException pending) { registration=pending; SystemClock.sleep(250); }
        }
        throw new IOException("VPN network UID rules not ready: " + cm.getNetworkCapabilities(vpn()),registration);
    }
    @Test public void testAllowedAndBlockedTrafficThroughNativeTunnel() throws Exception {
        // Baseline: don't mistake an unavailable runner network for a VPN regression.
        for (String host : ConnectivityProbe.HOSTS) https(physical, host, null);
        Network tunnel = start();
        for (String host : ConnectivityProbe.HOSTS) {
            byte[] reply = dns(tunnel, host); assertEquals(host, 0, reply[3] & 15);
            InetAddress[] addresses = tunnel.getAllByName(host); assertTrue(host, addresses.length > 0);
            boolean connected = false;
            for (InetAddress address : addresses) {
                try (Socket socket = new Socket()) { tunnel.bindSocket(socket); socket.connect(new InetSocketAddress(address,443),5000); connected = true; break; }
                catch (IOException unavailableFamily) { /* IPv4-only emulator may still receive AAAA replies. */ }
            }
            assertTrue(host+" TCP 443", connected);
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
            for(int i=0;i<1200 && context.getSharedPreferences("shield",0).getInt("consecutiveOutages",0)<3;i++) SystemClock.sleep(200);
            assertEquals(3,context.getSharedPreferences("shield",0).getInt("consecutiveOutages",0));
            assertNotNull("Strong must retain TUN on forwarding failure", vpn());
            assertFalse(context.getSharedPreferences("shield",0).getBoolean("connectivityOk",true));
        } else {
            for(int i=0;i<300 && context.getSharedPreferences("shield",0).getInt("consecutiveOutages",0)==0;i++) SystemClock.sleep(200);
            assertNotNull("One diagnostic failure must not remove VPN",vpn());
            assertEquals(1,context.getSharedPreferences("shield",0).getInt("consecutiveOutages",0));
            for(int i=0;i<900 && vpn()!=null;i++) SystemClock.sleep(200);
            assertNull("Normal must release TUN after failed forwarding",vpn());
            assertFalse(ShieldVpnService.running);
            assertFalse(context.getSharedPreferences("shield",0).getBoolean("wanted",true));
            for(String host:ConnectivityProbe.HOSTS) https(physical,host,null);
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
    @Test public void testStableConcurrentBrowsingAndManualBlock() throws Exception {
        Network tunnel = start();
        // Three minutes, spanning multiple health/self-test cycles. No protected sockets here.
        ExecutorService clients = Executors.newFixedThreadPool(4);
        long until = SystemClock.elapsedRealtime() + 180000;
        int rounds = 0;
        try {
            do {
                List<Future<?>> requests = new ArrayList<>();
                for (String host : ConnectivityProbe.HOSTS) requests.add(clients.submit(() -> {
                    try { https(tunnel, host, null); } catch(IOException e) { throw new RuntimeException(e); }
                }));
                for(Future<?> request:requests) request.get(60,TimeUnit.SECONDS);
                assertTrue("Native engine stopped",ShieldVpnService.running);
                assertEquals("VPN was removed/replaced during normal traffic",tunnel,vpn());
                rounds++; SystemClock.sleep(3000);
            } while(SystemClock.elapsedRealtime()<until);
            https(tunnel,"play.google.com",null);
            https(tunnel,"android.clients.google.com",null);
            String store = shell("am start -n com.android.vending/com.google.android.finsky.activities.MainActivity");
            assertFalse("Google Play launch failed: " + store, store.contains("Error"));
            SystemClock.sleep(8000);
            shell("screencap -p /sdcard/gameshield-store.png");
            shell("uiautomator dump /sdcard/gameshield-store.xml");
            assertEquals("Play Store must not remove VPN",tunnel,vpn());
            android.util.Log.i("GameShieldDeviceTest","Stable VPN: " + rounds + " concurrent rounds over 180 seconds; Google Play HTTPS endpoints OK");
            // Exercise the same stored custom list and RELOAD path as the user interface.
            context.getSharedPreferences("shield",0).edit().putString("custom","example.com").commit();
            context.startForegroundService(new Intent(context,ShieldVpnService.class).setAction("RELOAD"));
            boolean blocked=false;
            for(int i=0;i<60;i++) { if((dns(tunnel,"example.com")[3]&15)==3) { blocked=true; break; } SystemClock.sleep(500); }
            assertTrue("User domain must be blocked",blocked);
            https(tunnel,"google.it",null);
            assertEquals(tunnel,vpn());
        } finally { clients.shutdownNow(); context.getSharedPreferences("shield",0).edit().remove("custom").commit(); }
        context.stopService(new Intent(context,ShieldVpnService.class));
        for(int i=0;i<100 && vpn()!=null;i++) SystemClock.sleep(200);
        assertNull("Normal stop must restore ordinary routes",vpn());
        for(String host:ConnectivityProbe.HOSTS) https(physical,host,null);
    }
    @Test public void testNativeTcpFinAndHalfClose() throws Exception {
        Network tunnel = start();
        int before=peerUploads(tunnel);
        for(int round=0;round<20;round++) {
            try(Socket socket=new Socket()) {
                tunnel.bindSocket(socket); socket.connect(new InetSocketAddress("10.0.2.2",18080),5000); socket.setSoTimeout(10000);
                socket.getOutputStream().write("EOF\n".getBytes(StandardCharsets.US_ASCII)); socket.shutdownOutput();
                byte[] received=LocalProxy.readAll(socket.getInputStream(),100000);
                assertEquals("Native EOF must not reset/truncate",65536,received.length);
                for(byte b:received) assertEquals('x',b);
            }
            try(Socket socket=new Socket()) {
                tunnel.bindSocket(socket); socket.connect(new InetSocketAddress("10.0.2.2",18080),5000); socket.setSoTimeout(10000);
                socket.getOutputStream().write("HALF\n".getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
                assertEquals("ready",new String(LocalProxy.readAll(socket.getInputStream(),100),StandardCharsets.US_ASCII));
                byte[] upload=new byte[65536]; Arrays.fill(upload,(byte)'y');
                socket.getOutputStream().write(upload); socket.shutdownOutput();
            }
            int acknowledged=0;
            for(int i=0;i<50;i++) { acknowledged=peerUploads(tunnel); if(acknowledged>=before+round+1) break; SystemClock.sleep(100); }
            assertEquals("Physical peer must receive the complete half-close upload",before+round+1,acknowledged);
        }
        assertEquals(tunnel,vpn());
        android.util.Log.i("GameShieldDeviceTest","40 native TCP FIN / half-close transfers passed without reset");
    }
    private int peerUploads(Network tunnel) throws IOException {
        try(Socket socket=new Socket()) {
            tunnel.bindSocket(socket); socket.connect(new InetSocketAddress("10.0.2.2",18080),5000); socket.setSoTimeout(10000);
            socket.getOutputStream().write("COUNT\n".getBytes(StandardCharsets.US_ASCII)); socket.shutdownOutput();
            return Integer.parseInt(new String(LocalProxy.readAll(socket.getInputStream(),100),StandardCharsets.US_ASCII).trim());
        }
    }
    private String shell(String command) throws IOException {
        try(android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            return new String(LocalProxy.readAll(input,1000000),StandardCharsets.UTF_8);
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
