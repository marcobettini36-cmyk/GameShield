package it.gameshield;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class QuicNamesTest {
    @Test public void rfc9001ClientInitialPublicVector() throws Exception {
        check("rfc9001-client-initial.hex");
    }
    @Test public void rfc9369Version2PublicVector() throws Exception {
        check("rfc9369-client-initial.hex");
    }
    private void check(String resource) throws Exception {
        String text;
        try (java.io.InputStream in = getClass().getResourceAsStream("/" + resource)) {
            assertNotNull(in); text = new String(in.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s", "");
        }
        byte[] packet = new byte[text.length()/2];
        for(int i=0;i<packet.length;i++) packet[i]=(byte)Integer.parseInt(text.substring(2*i,2*i+2),16);
        assertEquals("example.com", QuicNames.sni(packet));
        packet[packet.length - 1] ^= 1; assertNull(QuicNames.sni(packet));
    }
    @Test public void unsupportedAndMalformedDatagramsRemainAllowed() {
        for (int size=0;size<1500;size+=17) assertNull(QuicNames.sni(new byte[size]));
    }
}
