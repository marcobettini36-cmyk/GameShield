package it.gameshield;

import android.net.Network;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

/** Explicitly VPN-bound sockets: a protected upstream socket would give a false positive. */
public final class ConnectivityProbe {
    public static final String[] HOSTS = {"google.com", "wikipedia.org", "github.com"};
    private ConnectivityProbe() {}
    public static String check(Network vpn) throws IOException {
        if (vpn == null) throw new IOException("Rete VPN non disponibile");
        StringBuilder report = new StringBuilder();
        for (String host : HOSTS) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            // This invokes Android's resolver on the VPN, including its Private DNS policy.
            InetAddress[] addresses = vpn.getAllByName(host);
            if (addresses.length == 0) throw new IOException(host + ": DNS senza indirizzi");
            report.append(host).append(": DNS OK, ");
            try (Socket tcp = connect(vpn, addresses)) { report.append("TCP 443 OK, "); }
            try (Socket raw = connect(vpn, addresses);
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
            }
        }
        return report.toString();
    }
    private static Socket connect(Network network, InetAddress[] addresses) throws IOException {
        // Prefer IPv4 but retain working IPv6 on IPv6-only networks.
        List<InetAddress> ordered = new ArrayList<>(Arrays.asList(addresses));
        ordered.sort(Comparator.comparingInt(ip -> ip instanceof Inet4Address ? 0 : 1));
        IOException failure = null;
        for (InetAddress address : ordered) {
            Socket socket = new Socket();
            try {
                network.bindSocket(socket); socket.connect(new InetSocketAddress(address, 443), 3000);
                return socket;
            } catch (IOException e) { socket.close(); failure = e; }
        }
        throw new IOException("TCP 443 non inoltrato", failure);
    }
}
