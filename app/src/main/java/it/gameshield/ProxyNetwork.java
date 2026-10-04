package it.gameshield;
import java.io.IOException;
import java.net.*;
/** Transport boundary allows real SOCKS protocol tests without Android or a device. */
public interface ProxyNetwork {
    Socket socket(InetAddress address, int port) throws IOException;
    DatagramSocket datagram() throws IOException;
    byte[] dns(byte[] query) throws IOException;
    InetAddress resolve(String domain) throws IOException;
    boolean denied(String domain, int port, boolean udp);
    default void diagnostic(String phase, IOException error) { }
}
