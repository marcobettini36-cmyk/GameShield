package it.gameshield;

import android.net.VpnService;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;

/** Every outbound socket is protected BEFORE connect to prevent VPN recursion. */
public final class ProtectedNetwork implements ProxyNetwork {
    private final VpnService vpn;
    private final RuleStore store;
    private final ConnectivityManager connectivity;
    private volatile Network selectedPhysical;
    private final long diagnosticUntil = android.os.SystemClock.elapsedRealtime() + 15 * 60 * 1000;
    public final AtomicLong blocked = new AtomicLong();
    public ProtectedNetwork(VpnService vpn, RuleStore store) {
        this.vpn = vpn; this.store = store; connectivity = vpn.getSystemService(ConnectivityManager.class);
    }
    public synchronized Network underlying() {
        Network selected = null;
        Network active = connectivity.getActiveNetwork();
        NetworkCapabilities retained = selectedPhysical == null ? null : connectivity.getNetworkCapabilities(selectedPhysical);
        if (retained != null && !retained.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                && retained.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) selected = selectedPhysical;
        for (Network candidate : connectivity.getAllNetworks()) {
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(candidate);
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            if (candidate.equals(active)) { selectedPhysical = candidate; return candidate; }
            if (selected == null) selected = candidate;
            else {
                NetworkCapabilities current = connectivity.getNetworkCapabilities(selected);
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        && (current == null || !current.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))) selected = candidate;
            }
        }
        selectedPhysical = selected;
        return selected;
    }
    public Socket socket(InetAddress ip, int port) throws IOException {
        Socket socket = new Socket();
        try {
            // Android new Socket() has no kernel fd yet; protect(Socket) would return false.
            // Materialize a bound fd first, still BEFORE connect, avoiding VPN recursion.
            socket.bind(new InetSocketAddress(0));
            boolean protectedSocket = vpn.protect(socket);
            event("TCP protect=" + protectedSocket + " destination=" + ip.getHostAddress() + ":" + port);
            if (!protectedSocket) throw new IOException("Impossibile proteggere socket");
            Network physical = underlying(); if (physical != null) physical.bindSocket(socket);
            event("TCP bind physical=" + physical + " destination=" + ip.getHostAddress() + ":" + port);
            socket.connect(new InetSocketAddress(ip, port), 10000); socket.setSoTimeout(60000); return socket;
        } catch (IOException e) { socket.close(); throw e; }
    }
    public DatagramSocket datagram() throws IOException {
        DatagramSocket socket = new DatagramSocket();
        boolean protectedSocket = vpn.protect(socket); event("UDP protect=" + protectedSocket);
        if (!protectedSocket) { socket.close(); throw new IOException("Impossibile proteggere UDP"); }
        try {
            Network physical = underlying(); if (physical != null) physical.bindSocket(socket);
            socket.setSoTimeout(2500); return socket;
        } catch (IOException e) { socket.close(); throw e; }
    }
    public byte[] dns(byte[] query) throws IOException {
        String domain = Dns.question(query);
        event("DNS domain=" + domain + " decision=" + (store.rules().blocks(domain) ? "BLOCK" : "ALLOW"));
        if (store.rules().blocks(domain)) { blocked.incrementAndGet(); return Dns.refused(query, 3); }
        LinkedHashSet<InetAddress> resolvers = new LinkedHashSet<>();
        Network physical = underlying();
        LinkProperties properties = physical == null ? null : connectivity.getLinkProperties(physical);
        if (properties != null) resolvers.addAll(properties.getDnsServers());
        resolvers.removeIf(ip -> ip.isLoopbackAddress() || ip.getHostAddress().equals("198.18.0.2"));
        resolvers.add(InetAddress.getByName("1.1.1.1")); resolvers.add(InetAddress.getByName("9.9.9.9"));
        for (InetAddress resolver : resolvers) {
            byte[] answer;
            try (DatagramSocket socket = datagram()) {
                socket.connect(resolver, 53);
                socket.send(new DatagramPacket(query, query.length));
                byte[] buffer = new byte[65507]; DatagramPacket packet = new DatagramPacket(buffer, buffer.length); socket.receive(packet);
                answer = Arrays.copyOf(buffer, packet.getLength());
                if (answer.length < 12 || Dns.u16(answer, 0) != Dns.u16(query, 0) || (answer[2] & 0x80) == 0) throw new IOException("Invalid DNS reply");
                // Truncated responses are retried over protected TCP.
                if ((answer[2] & 2) != 0) answer = tcpDns(query, resolver);
            } catch (IOException e) {
                diagnostic("DNS UDP domain=" + domain + " resolver=" + resolver.getHostAddress(), e);
                // UDP/53 is restricted on some networks. Retry TCP even on timeout, not only TC=1.
                try { answer = tcpDns(query, resolver); } catch (IOException error) { diagnostic("DNS TCP domain=" + domain + " resolver=" + resolver.getHostAddress(), error); continue; }
            }
            if (Dns.containsBlockedAlias(answer, store.rules())) { blocked.incrementAndGet(); return Dns.refused(query, 3); }
            if ((answer[3] & 15) == 2 || (answer[3] & 15) == 5) continue;
            return answer;
        }
        return Dns.refused(query, 2);
    }
    private byte[] tcpDns(byte[] q, InetAddress resolver) throws IOException {
        try (Socket socket = socket(resolver, 53)) {
            socket.setSoTimeout(2500);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream()); out.writeShort(q.length); out.write(q); out.flush();
            DataInputStream in = new DataInputStream(socket.getInputStream()); byte[] answer = new byte[in.readUnsignedShort()]; in.readFully(answer);
            if (answer.length < 12 || Dns.u16(answer, 0) != Dns.u16(q, 0) || (answer[2] & 0x80) == 0) throw new IOException("Invalid DNS TCP reply");
            return answer;
        }
    }
    public InetAddress resolve(String domain) throws IOException {
        return InetAddress.getByAddress(Dns.firstIpv4(dns(Dns.query(domain, new SecureRandom().nextInt(65536)))));
    }
    public boolean denied(String domain, int port, boolean udp) {
        // Ports and DNS transports are not gambling. Dropping QUIC/DoT globally breaks legitimate apps.
        boolean deny = domain != null && store.rules().blocks(domain);
        if (domain != null) event((udp ? "UDP" : "TCP") + " domain=" + domain + " port=" + port + " decision=" + (deny ? "BLOCK" : "ALLOW"));
        if (deny) blocked.incrementAndGet(); return deny;
    }
    @Override public void diagnostic(String phase, IOException error) {
        if (BuildConfig.DEBUG && android.os.SystemClock.elapsedRealtime() < diagnosticUntil) android.util.Log.w("GameShieldTransport", phase + ": " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
    }
    @Override public void event(String message) {
        if (BuildConfig.DEBUG && android.os.SystemClock.elapsedRealtime() < diagnosticUntil) android.util.Log.i("GameShieldTransport", message);
    }
    public byte[] downloadFeed() throws IOException {
        String host = RuleStore.FEED_HOST;
        try (Socket raw = socket(resolve(host), 443);
             SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, 443, true)) {
            SSLParameters params = ssl.getSSLParameters(); params.setEndpointIdentificationAlgorithm("HTTPS"); ssl.setSSLParameters(params); ssl.startHandshake();
            ssl.getOutputStream().write(("GET " + RuleStore.FEED_PATH + " HTTP/1.1\r\nHost: " + host + "\r\nUser-Agent: GameShield/0.1\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            InputStream in = new BufferedInputStream(ssl.getInputStream()); String status = line(in);
            if (!status.startsWith("HTTP/1.1 200") && !status.startsWith("HTTP/1.0 200")) throw new IOException("Feed HTTP: " + status);
            boolean chunked = false; long length = -1; int headerSize = 0;
            for (String h; !(h = line(in)).isEmpty();) {
                if ((headerSize += h.length()) > 16384) throw new IOException("Header troppo grande");
                String lower = h.toLowerCase(Locale.ROOT);
                if (lower.startsWith("transfer-encoding:")) chunked = lower.contains("chunked");
                if (lower.startsWith("content-length:")) { try { length = Long.parseLong(h.substring(15).trim()); } catch (NumberFormatException e) { throw new IOException("Invalid length"); } }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (chunked) {
                while (true) {
                    int size; try { size = Integer.parseInt(line(in).split(";", 2)[0].trim(), 16); } catch (NumberFormatException e) { throw new IOException("Invalid chunk"); }
                    if (size == 0) break; copy(in, out, size); if (!line(in).isEmpty()) throw new IOException("Invalid chunk end");
                }
            } else copy(in, out, length);
            return out.toByteArray();
        }
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
        while ((b = in.read()) != -1) { if (b == '\n') return new String(out.toByteArray(), StandardCharsets.US_ASCII).replace("\r", ""); if (out.size() >= 8192) throw new IOException("Line too long"); out.write(b); }
        throw new EOFException();
    }
    private static void copy(InputStream in, ByteArrayOutputStream out, long size) throws IOException {
        if (size < -1 || size > 32 * 1024 * 1024) throw new IOException("Feed too large");
        byte[] buffer = new byte[8192]; long remaining = size;
        while (remaining != 0) {
            int n = in.read(buffer, 0, remaining < 0 ? buffer.length : (int) Math.min(buffer.length, remaining));
            if (n < 0) { if (remaining > 0) throw new EOFException(); break; }
            out.write(buffer, 0, n); if (out.size() > 32 * 1024 * 1024) throw new IOException("Feed too large");
            if (remaining > 0) remaining -= n;
        }
    }
}
