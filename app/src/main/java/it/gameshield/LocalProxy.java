package it.gameshield;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated loopback SOCKS5 gateway. Only protected outbound sockets leave the VPN. */
public final class LocalProxy implements AutoCloseable {
    private final ProxyNetwork network;
    private final String username, password;
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 384, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private final ThreadPoolExecutor dnsWorkers = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));
    private final Set<Closeable> live = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;
    public LocalProxy(ProxyNetwork network, String username, String password) throws IOException {
        this.network = network; this.username = username; this.password = password;
        listener = new ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"));
        new Thread(this::accept, "gameshield-socks").start();
    }
    public int port() { return listener.getLocalPort(); }
    private void accept() {
        while (running) {
            try { Socket client = listener.accept(); live.add(client); submit(() -> handle(client), client); }
            catch (IOException e) { if (running) close(); }
        }
    }
    private void submit(Runnable task, Closeable resource) {
        try { workers.execute(task); } catch (RejectedExecutionException e) { network.diagnostic("Relay capacity exhausted", new IOException(e)); closeResource(resource); live.remove(resource); }
    }
    private void handle(Socket client) {
        String phase = "SOCKS handshake";
        try (client) {
            client.setSoTimeout(15000);
            DataInputStream in = new DataInputStream(client.getInputStream()); DataOutputStream out = new DataOutputStream(client.getOutputStream());
            if (in.readUnsignedByte() != 5) return;
            int count = in.readUnsignedByte(); boolean auth = false;
            for (int i = 0; i < count; i++) if (in.readUnsignedByte() == 2) auth = true;
            out.write(new byte[]{5, (byte) (auth ? 2 : 255)}); out.flush(); if (!auth) return;
            if (in.readUnsignedByte() != 1) return;
            String u = readText(in), p = readText(in);
            boolean valid = constant(u, username) && constant(p, password);
            out.write(new byte[]{1, (byte) (valid ? 0 : 1)}); out.flush(); if (!valid) return;
            if (in.readUnsignedByte() != 5) return;
            int command = in.readUnsignedByte(); if (in.readUnsignedByte() != 0) return;
            Endpoint endpoint = readEndpoint(in); int port = in.readUnsignedShort();
            if (command == 3) { udp(client, in, out); return; }
            if (command != 1 || port == 0 || network.denied(endpoint.domain, port, false)) { reply(out, 2, 0); return; }
            // This synthetic DNS address has no DoT server. Reject its probe immediately so
            // Android Private DNS Automatic can fall back; real remote DoT remains permitted.
            if (port == 853 && endpoint.ip != null && endpoint.ip.getHostAddress().equals("198.18.0.2")) { reply(out, 5, 0); return; }
            if (port == 53) {
                reply(out, 0, 0); client.setSoTimeout(60000);
                while (running) {
                    int size = in.readUnsignedShort(); byte[] q = new byte[size]; in.readFully(q);
                    byte[] a = network.dns(q); out.writeShort(a.length); out.write(a); out.flush();
                }
                return;
            }
            InetAddress address = endpoint.domain == null ? endpoint.ip : network.resolve(endpoint.domain);
            network.event("TCP destination=" + address.getHostAddress() + ":" + port + " domain=" + endpoint.domain);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) { reply(out, 2, 0); return; }
            phase = "protected TCP connect (port " + port + ")";
            Socket remote = network.socket(address, port); live.add(remote);
            try (remote) {
                reply(out, 0, remote.getLocalPort());
                // Download runs immediately: server-first TCP must not wait for a ClientHello.
                // EOF half-closes only the corresponding output; the other direction stays alive.
                CountDownLatch downloaded = new CountDownLatch(1);
                client.setSoTimeout(0); remote.setSoTimeout(0);
                try {
                    workers.execute(() -> {
                        try { copy(remote.getInputStream(), client.getOutputStream()); client.shutdownOutput(); }
                        catch (IOException error) {
                            if (running && !client.isClosed()) network.diagnostic("TCP return " + address.getHostAddress() + ":" + port, error);
                            closeResource(client); closeResource(remote);
                        } finally { downloaded.countDown(); }
                    });
                } catch (RejectedExecutionException busy) { throw new IOException("TCP return worker unavailable", busy); }
                // Inspect the first TLS record on HTTPS and HTTP Host on port 80 before forwarding.
                if (port == 443 || port == 853 || port == 80) {
                    phase = "first flight (port " + port + ")";
                    client.setSoTimeout(2000); byte[] first = firstFlight(in, port);
                    String host = port == 80 ? httpHost(first) : TlsNames.sni(first);
                    if (network.denied(host, port, false)) return;
                    remote.getOutputStream().write(first); remote.getOutputStream().flush();
                }
                client.setSoTimeout(0);
                phase = "TCP upload";
                try { copy(in, remote.getOutputStream()); remote.shutdownOutput();
                    try { downloaded.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                } finally { closeResource(remote); }
            } finally { live.remove(remote); }
        } catch (IOException error) { network.diagnostic(phase, error); }
        finally { live.remove(client); }
    }
    private void udp(Socket control, DataInputStream controlIn, DataOutputStream controlOut) throws IOException {
        Set<DatagramSocket> outboundSockets = ConcurrentHashMap.newKeySet();
        Map<String, DatagramSocket> routes = new LinkedHashMap<>(16, 0.75f, true);
        DatagramSocket relay = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        try (relay) {
            live.add(relay); relay.setSoTimeout(1000); reply(controlOut, 0, relay.getLocalPort()); control.setSoTimeout(0);
            submit(() -> { try { while (controlIn.read() != -1) {} } catch (IOException ignored) {} finally { relay.close(); } }, control);
            InetSocketAddress source = null;
            while (running && !relay.isClosed() && !control.isClosed()) {
                try {
                    byte[] buffer = new byte[65507]; DatagramPacket packet = new DatagramPacket(buffer, buffer.length); relay.receive(packet);
                    InetSocketAddress sender = (InetSocketAddress) packet.getSocketAddress();
                    if (!sender.getAddress().isLoopbackAddress()) continue;
                    if (source == null) source = sender; if (!source.equals(sender)) continue;
                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer, 0, packet.getLength()));
                    if (in.readUnsignedShort() != 0 || in.readUnsignedByte() != 0) continue;
                    Endpoint target = readEndpoint(in); int port = in.readUnsignedShort();
                    if (port == 0) continue;
                    byte[] data = readAll(in, 65507);
                    if (network.denied(target.domain, port, true)) continue;
                    if ((port == 443 || port == 853) && network.denied(QuicNames.sni(data), port, true)) continue;
                    if (port == 53) {
                        // A slow upstream DNS request must not stall the UDP relay for every app.
                        final InetSocketAddress clientAddress = source;
                        try { dnsWorkers.execute(() -> {
                            try { sendUdp(relay, clientAddress, target, port, network.dns(data)); } catch (IOException error) { if (running) network.diagnostic("UDP DNS forwarding", error); }
                        }); } catch (RejectedExecutionException busy) { sendUdp(relay, source, target, port, Dns.refused(data, 2)); }
                    }
                    else {
                        InetAddress ip = target.domain == null ? target.ip : network.resolve(target.domain);
                        if (ip.isLoopbackAddress() || ip.isAnyLocalAddress() || ip.isMulticastAddress()) continue;
                        String key = ip.getHostAddress() + ":" + port;
                        DatagramSocket outbound = routes.get(key);
                        if (outbound == null || outbound.isClosed()) {
                            routes.entrySet().removeIf(entry -> entry.getValue().isClosed());
                            // Bound resources, but evict the oldest route instead of black-holing new destinations.
                            if (routes.size() >= 64 && outbound == null) {
                                String oldest = routes.keySet().iterator().next(); routes.remove(oldest).close();
                            }
                            outbound = network.datagram(); outbound.connect(ip, port); outbound.setSoTimeout(1000);
                            network.event("UDP ALLOW destination=" + ip.getHostAddress() + ":" + port + " domain=" + target.domain);
                            routes.put(key, outbound); outboundSockets.add(outbound); live.add(outbound);
                            final DatagramSocket connection = outbound; final InetSocketAddress clientAddress = source;
                            submit(() -> {
                                try {
                                    while (running && !relay.isClosed() && !connection.isClosed()) {
                                        try {
                                            DatagramPacket response = new DatagramPacket(new byte[65507], 65507); connection.receive(response);
                                            sendUdp(relay, clientAddress, target, port, Arrays.copyOf(response.getData(), response.getLength()));
                                        } catch (SocketTimeoutException ignored) { }
                                    }
                                } catch (IOException error) { if (running && !connection.isClosed()) network.diagnostic("UDP return " + key, error); }
                                finally { connection.close(); live.remove(connection); outboundSockets.remove(connection); }
                            }, connection);
                        }
                        if (!outbound.isClosed()) outbound.send(new DatagramPacket(data, data.length));
                    }
                } catch (SocketTimeoutException ignored) { } catch (IOException error) { if (running && !relay.isClosed()) network.diagnostic("UDP forwarding", error); }
            }
            live.remove(relay);
        } finally { live.remove(relay); for (DatagramSocket socket : outboundSockets) { socket.close(); live.remove(socket); } }
    }
    private static void sendUdp(DatagramSocket relay, InetSocketAddress source, Endpoint target, int port, byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0); out.writeByte(0); target.write(out); out.writeShort(port); out.write(data);
        byte[] answer = bytes.toByteArray(); if (answer.length <= 65507) relay.send(new DatagramPacket(answer, answer.length, source));
    }
    private static byte[] firstFlight(DataInputStream in, int port) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
        if (port != 80) {
            int first = in.read(); if (first < 0) return bytes.toByteArray(); bytes.write(first);
            if (first != 22) return bytes.toByteArray();
            byte[] header = new byte[5]; header[0] = (byte) first;
            for (int i = 1; i < 5; i++) { int b = in.read(); if (b < 0) return bytes.toByteArray(); header[i] = (byte)b; bytes.write(b); }
            // Port 443 is not necessarily TLS. Never interpret arbitrary bytes as a TLS length.
            if (header[0] != 22 || header[1] != 3) return bytes.toByteArray();
            int size = ((header[3] & 255) << 8) | (header[4] & 255);
            if (size > 18432) return bytes.toByteArray();
            for (int i = 0; i < size; i++) { int b = in.read(); if (b < 0) break; bytes.write(b); }
        } else {
            int last = 0;
            while (bytes.size() < 16384) { int b = in.read(); if (b < 0) break; bytes.write(b); last = (last << 8) | b; if (last == 0x0d0a0d0a) break; }
        }
        } catch (SocketTimeoutException undecidable) {
            // Classification is best effort. Preserve every captured byte and forward unchanged.
        }
        return bytes.toByteArray();
    }
    private static String httpHost(byte[] bytes) {
        for (String line : new String(bytes, StandardCharsets.US_ASCII).split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("host:")) return DomainRules.normalize(line.substring(5).trim().split(":", 2)[0]);
        }
        return null;
    }
    private static boolean constant(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
    private static String readText(DataInputStream in) throws IOException { byte[] b = new byte[in.readUnsignedByte()]; in.readFully(b); return new String(b, StandardCharsets.UTF_8); }
    private static Endpoint readEndpoint(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        if (type == 3) { String domain = DomainRules.normalize(readText(in)); if (domain == null) throw new IOException("Invalid domain"); return new Endpoint(domain, null); }
        if (type != 1 && type != 4) throw new IOException("Invalid address");
        byte[] b = new byte[type == 1 ? 4 : 16]; in.readFully(b); return new Endpoint(null, InetAddress.getByAddress(b));
    }
    private static void reply(DataOutputStream out, int status, int port) throws IOException { out.write(new byte[]{5, (byte) status, 0, 1, 127, 0, 0, 1}); out.writeShort(port); out.flush(); }
    public static byte[] readAll(InputStream in, int limit) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; for (int n; (n = in.read(b)) != -1;) { out.write(b, 0, n); if (out.size() > limit) throw new IOException("Too large"); } return out.toByteArray(); }
    private static void copy(InputStream in, OutputStream out) throws IOException { byte[] b = new byte[16384]; for (int n; (n = in.read(b)) != -1;) { out.write(b, 0, n); out.flush(); } }
    private static void closeResource(Closeable c) { try { c.close(); } catch (IOException ignored) {} }
    @Override public void close() { running = false; closeResource(listener); for (Closeable c : live) closeResource(c); live.clear(); workers.shutdownNow(); dnsWorkers.shutdownNow(); }
    private static final class Endpoint {
        final String domain; final InetAddress ip;
        Endpoint(String domain, InetAddress ip) { this.domain = domain; this.ip = ip; }
        void write(DataOutputStream out) throws IOException {
            if (domain != null) { out.writeByte(3); byte[] b = domain.getBytes(StandardCharsets.US_ASCII); out.writeByte(b.length); out.write(b); }
            else { byte[] b = ip.getAddress(); out.writeByte(b.length == 4 ? 1 : 4); out.write(b); }
        }
    }
}
