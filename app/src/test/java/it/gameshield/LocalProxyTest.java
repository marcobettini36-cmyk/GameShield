package it.gameshield;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Exercises the actual loopback SOCKS server, authentication and DNS framing. */
public class LocalProxyTest {
    private static final class Network implements ProxyNetwork {
        final AtomicInteger connects = new AtomicInteger();
        final DomainRules rules = new DomainRules(Arrays.asList("casino.com"));
        int echoPort;
        public Socket socket(InetAddress ip, int port) throws IOException { connects.incrementAndGet(); Socket s = new Socket("127.0.0.1", echoPort); s.setSoTimeout(3000); return s; }
        public DatagramSocket datagram() throws IOException { return new DatagramSocket() { @Override public void connect(InetAddress ip, int port) { super.connect(InetAddress.getLoopbackAddress(), echoPort); } }; }
        public byte[] dns(byte[] q) throws IOException { return Dns.refused(q, rules.blocks(Dns.question(q)) ? 3 : 2); }
        public InetAddress resolve(String d) throws IOException { return InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10}); }
        public boolean denied(String d, int port, boolean udp) { return rules.blocks(d) || port == 853 || (udp && port == 443); }
    }
    private static Socket client(LocalProxy proxy) throws IOException { Socket c = new Socket("127.0.0.1", proxy.port()); c.setSoTimeout(5000); return c; }
    private static boolean auth(Socket c, String password) throws IOException {
        DataOutputStream out = new DataOutputStream(c.getOutputStream()); DataInputStream in = new DataInputStream(c.getInputStream());
        out.write(new byte[]{5, 1, 2}); out.flush(); assertEquals(5, in.readUnsignedByte()); assertEquals(2, in.readUnsignedByte());
        out.writeByte(1); out.writeByte(4); out.writeBytes("user"); out.writeByte(password.length()); out.writeBytes(password); out.flush();
        assertEquals(1, in.readUnsignedByte()); return in.readUnsignedByte() == 0;
    }
    private static int request(Socket c, int command, String domain, int port) throws IOException {
        DataOutputStream out = new DataOutputStream(c.getOutputStream()); out.write(new byte[]{5, (byte) command, 0});
        if (domain == null) out.write(new byte[]{1, (byte) 203, 0, 113, 10}); else { out.writeByte(3); out.writeByte(domain.length()); out.writeBytes(domain); }
        out.writeShort(port); out.flush(); DataInputStream in = new DataInputStream(c.getInputStream()); byte[] reply = new byte[10]; in.readFully(reply);
        assertEquals(5, reply[0]); if (reply[1] != 0) return -(reply[1] & 255); return ((reply[8] & 255) << 8) | (reply[9] & 255);
    }
    @Test public void rejectsWrongPassword() throws Exception {
        Network n = new Network(); try (LocalProxy p = new LocalProxy(n, "user", "secret"); Socket c = client(p)) { assertFalse(auth(c, "wrong")); assertEquals(0, n.connects.get()); }
    }
    @Test public void rejectsNoAuthenticationMethod() throws Exception {
        try (LocalProxy p = new LocalProxy(new Network(), "user", "secret"); Socket c = client(p)) { c.getOutputStream().write(new byte[]{5, 1, 0}); assertEquals(5, c.getInputStream().read()); assertEquals(255, c.getInputStream().read()); }
    }
    @Test public void blocksDomainBeforeOutboundConnection() throws Exception {
        Network n = new Network(); try (LocalProxy p = new LocalProxy(n, "user", "secret"); Socket c = client(p)) { assertTrue(auth(c, "secret")); assertEquals(-2, request(c, 1, "new.casino.com", 443)); assertEquals(0, n.connects.get()); }
    }
    @Test public void forwardsAllowedTcpBothDirections() throws Exception {
        Network n = new Network();
        try (ServerSocket echo = new ServerSocket(0); LocalProxy p = new LocalProxy(n, "user", "secret"); Socket c = client(p)) {
            n.echoPort = echo.getLocalPort();
            Thread peer = new Thread(() -> { try (Socket s = echo.accept()) { byte[] b = new byte[5]; new DataInputStream(s.getInputStream()).readFully(b); s.getOutputStream().write(b); s.getOutputStream().flush(); } catch (IOException ignored) {} }); peer.start();
            assertTrue(auth(c, "secret")); assertTrue(request(c, 1, null, 12345) > 0);
            byte[] sent = {1, 2, 3, 4, 5}; c.getOutputStream().write(sent); c.getOutputStream().flush(); byte[] got = new byte[5]; new DataInputStream(c.getInputStream()).readFully(got); assertArrayEquals(sent, got);
            peer.join(3000); assertFalse(peer.isAlive()); assertEquals(1, n.connects.get());
        }
    }
    @Test public void framesAndFiltersTcpDns() throws Exception {
        try (LocalProxy p = new LocalProxy(new Network(), "user", "secret"); Socket c = client(p)) {
            assertTrue(auth(c, "secret")); assertTrue(request(c, 1, null, 53) >= 0);
            byte[] q = Dns.query("casino.com", 123); DataOutputStream out = new DataOutputStream(c.getOutputStream()); out.writeShort(q.length); out.write(q); out.flush();
            DataInputStream in = new DataInputStream(c.getInputStream()); byte[] a = new byte[in.readUnsignedShort()]; in.readFully(a); assertEquals(123, Dns.u16(a, 0)); assertEquals(3, a[3] & 15);
        }
    }
    @Test public void framesAndFiltersUdpDns() throws Exception {
        try (LocalProxy p = new LocalProxy(new Network(), "user", "secret"); Socket c = client(p); DatagramSocket udp = new DatagramSocket()) {
            udp.setSoTimeout(5000); assertTrue(auth(c, "secret")); int port = request(c, 3, null, 0); assertTrue(port > 0);
            ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(b); out.write(new byte[]{0, 0, 0, 1, (byte) 198, 18, 0, 2}); out.writeShort(53); out.write(Dns.query("casino.com", 77));
            byte[] sent = b.toByteArray(); udp.send(new DatagramPacket(sent, sent.length, InetAddress.getByName("127.0.0.1"), port)); DatagramPacket reply = new DatagramPacket(new byte[1024], 1024); udp.receive(reply);
            byte[] answer = Arrays.copyOfRange(reply.getData(), 10, reply.getLength()); assertEquals(77, Dns.u16(answer, 0)); assertEquals(3, answer[3] & 15);
        }
    }
    @Test public void forwardsMultipleUdpRepliesUsingStableSocket() throws Exception {
        Network n = new Network();
        try (DatagramSocket echo = new DatagramSocket(); LocalProxy p = new LocalProxy(n, "user", "secret"); Socket c = client(p); DatagramSocket udp = new DatagramSocket()) {
            n.echoPort = echo.getLocalPort(); echo.setSoTimeout(5000); udp.setSoTimeout(5000);
            Thread peer = new Thread(() -> {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[10], 10); echo.receive(packet);
                    for (byte b : new byte[]{42, 43}) echo.send(new DatagramPacket(new byte[]{b}, 1, packet.getSocketAddress()));
                } catch (IOException ignored) { }
            }); peer.start();
            assertTrue(auth(c, "secret")); int port = request(c, 3, null, 0);
            ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(b); out.write(new byte[]{0, 0, 0, 1, (byte) 203, 0, 113, 10}); out.writeShort(12345); out.writeByte(9);
            byte[] sent = b.toByteArray(); udp.send(new DatagramPacket(sent, sent.length, InetAddress.getLoopbackAddress(), port));
            for (int expected : new int[]{42, 43}) { DatagramPacket reply = new DatagramPacket(new byte[100], 100); udp.receive(reply); assertEquals(11, reply.getLength()); assertEquals(expected, reply.getData()[10]); }
            peer.join(3000); assertFalse(peer.isAlive());
        }
    }
}
