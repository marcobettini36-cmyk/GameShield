package it.gameshield;

import android.net.VpnService;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;

/** Every outbound socket is protected BEFORE connect to prevent VPN recursion. */
public final class ProtectedNetwork {
    private final VpnService vpn;
    private final RuleStore store;
    public final AtomicLong blocked = new AtomicLong();
    public ProtectedNetwork(VpnService vpn, RuleStore store) { this.vpn = vpn; this.store = store; }
    public Socket socket(InetAddress ip, int port) throws IOException {
        Socket socket = new Socket();
        try {
            if (!vpn.protect(socket)) throw new IOException("Impossibile proteggere socket");
            socket.connect(new InetSocketAddress(ip, port), 10000); socket.setSoTimeout(60000); return socket;
        } catch (IOException e) { socket.close(); throw e; }
    }
    public DatagramSocket datagram() throws IOException {
        DatagramSocket socket = new DatagramSocket();
        if (!vpn.protect(socket)) { socket.close(); throw new IOException("Impossibile proteggere UDP"); }
        socket.setSoTimeout(8000); return socket;
    }
    public byte[] dns(byte[] query) throws IOException {
        String domain = Dns.question(query);
        if (store.rules().blocks(domain)) { blocked.incrementAndGet(); return Dns.refused(query, 3); }
        IOException failure = null;
        for (String resolver : new String[]{"1.1.1.1", "9.9.9.9"}) {
            try (DatagramSocket socket = datagram()) {
                socket.connect(InetAddress.getByName(resolver), 53);
                socket.send(new DatagramPacket(query, query.length));
                byte[] buffer = new byte[65507]; DatagramPacket packet = new DatagramPacket(buffer, buffer.length); socket.receive(packet);
                byte[] answer = Arrays.copyOf(buffer, packet.getLength());
                if (answer.length < 12 || Dns.u16(answer, 0) != Dns.u16(query, 0) || (answer[2] & 0x80) == 0) throw new IOException("Invalid DNS reply");
                // Truncated responses are retried over protected TCP.
                if ((answer[2] & 2) != 0) answer = tcpDns(query, InetAddress.getByName(resolver));
                if (Dns.containsBlockedAlias(answer, store.rules())) { blocked.incrementAndGet(); return Dns.refused(query, 3); }
                return answer;
            } catch (IOException e) { failure = e; }
        }
        if (failure != null) return Dns.refused(query, 2);
        throw new IOException("DNS unavailable");
    }
    private byte[] tcpDns(byte[] q, InetAddress resolver) throws IOException {
        try (Socket socket = socket(resolver, 53)) {
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
        boolean deny = port == 853 || (udp && port == 443) || (domain != null && store.rules().blocks(domain));
        if (deny) blocked.incrementAndGet(); return deny;
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
