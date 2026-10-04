package it.gameshield;

import android.net.Network;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

/** Explicitly VPN-bound sockets: a protected upstream socket would give a false positive. */
public final class ConnectivityProbe {
    public static final String[] HOSTS = {"google.it", "youtube.com", "wikipedia.org", "github.com"};
    private ConnectivityProbe() {}
    public static final class Result {
        public int dnsOk, tcpOk, httpsOk;
        public final StringBuilder report = new StringBuilder();
        public boolean usable() { return httpsOk > 0; }
        public boolean complete() { return dnsOk == HOSTS.length && tcpOk == HOSTS.length && httpsOk == HOSTS.length; }
        @Override public String toString() { return report.toString(); }
    }
    public static Result check(Network vpn) throws IOException {
        return check(vpn, true);
    }
    public static Result check(Network vpn, boolean tunnel) throws IOException {
        Result result = new Result(); StringBuilder report = result.report;
        if (vpn == null) { report.append("DNS / TCP 443 / HTTPS: rete VPN non disponibile"); return result; }
        for (String host : HOSTS) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            // This invokes Android's resolver on the VPN, including its Private DNS policy.
            InetAddress[] addresses;
            try {
                if (tunnel) {
                    // Bounded wire-level DNS check before invoking Android's native resolver.
                    // A dead relay must not leave getAllByName blocked for minutes.
                    try (DatagramSocket dns = new DatagramSocket()) {
                        vpn.bindSocket(dns); dns.setSoTimeout(2500); dns.connect(InetAddress.getByName("198.18.0.2"), 53);
                        byte[] query = Dns.query(host, 4173); dns.send(new DatagramPacket(query, query.length));
                        DatagramPacket answer = new DatagramPacket(new byte[4096], 4096); dns.receive(answer);
                        if (answer.getLength() < 12 || Dns.u16(answer.getData(), 0) != 4173 || (answer.getData()[3] & 15) != 0)
                            throw new IOException("Risposta DNS non valida o dominio bloccato");
                    }
                }
                addresses = vpn.getAllByName(host);
                if (addresses.length == 0) throw new IOException("DNS senza indirizzi");
                result.dnsOk++;
            } catch (IOException error) { report.append(host).append(": DNS FAIL ").append(error)
                .append("; TCP 443 / HTTPS non eseguiti (DNS)\n"); continue; }
            report.append(host).append(": DNS OK, ");
            try (Socket tcp = connect(vpn, addresses)) { result.tcpOk++; report.append("TCP 443 OK, "); }
            catch (IOException error) { report.append("TCP 443 FAIL ").append(error).append(", "); }
            IOException failure = null; boolean passed = false;
            for (InetAddress address : ordered(addresses)) {
            try (Socket raw = connect(vpn, new InetAddress[]{address});
                 SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                     .createSocket(raw, host, 443, true)) {
                tls.setSoTimeout(5000);
                SSLParameters params = tls.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(params);
                tls.startHandshake();
                tls.getOutputStream().write(("HEAD / HTTP/1.1\r\nHost: " + host
                    + "\r\nUser-Agent: GameShield-connectivity/0.2\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
                tls.getOutputStream().flush();
                String status = new BufferedReader(new InputStreamReader(tls.getInputStream(), StandardCharsets.US_ASCII)).readLine();
                if (status == null || !status.matches("HTTP/1\\.[01] [234][0-9][0-9].*"))
                    throw new IOException(host + ": HTTPS " + status);
                report.append("HTTPS OK (").append(status.split(" ")[1]).append(")\n");
                result.httpsOk++; passed = true; break;
            } catch (IOException error) { failure = error; }
            }
            if (!passed) report.append("HTTPS FAIL ").append(failure).append('\n');
        }
        return result;
    }
    private static List<InetAddress> ordered(InetAddress[] addresses) {
        // Prefer IPv4 but retain working IPv6 on IPv6-only networks.
        List<InetAddress> ordered = new ArrayList<>(Arrays.asList(addresses));
        ordered.sort(Comparator.comparingInt(ip -> ip instanceof Inet4Address ? 0 : 1));
        List<InetAddress> limited = new ArrayList<>(); int v4 = 0, v6 = 0;
        for (InetAddress ip : ordered) if (ip instanceof Inet4Address ? v4++ < 2 : v6++ < 2) limited.add(ip);
        return limited;
    }
    private static Socket connect(Network network, InetAddress[] addresses) throws IOException {
        IOException failure = null;
        for (InetAddress address : ordered(addresses)) {
            Socket socket = new Socket();
            try {
                network.bindSocket(socket); socket.connect(new InetSocketAddress(address, 443), 3000);
                return socket;
            } catch (IOException e) { socket.close(); failure = e; }
        }
        throw new IOException("TCP 443 non inoltrato", failure);
    }
}
