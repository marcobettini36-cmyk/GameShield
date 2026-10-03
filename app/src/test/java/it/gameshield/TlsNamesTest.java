package it.gameshield;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class TlsNamesTest {
    @Test public void parsesSniAndIgnoresUnknownExtensions() throws Exception {
        byte[] hostname = "casino.com".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream payload = new ByteArrayOutputStream(); DataOutputStream p = new DataOutputStream(payload);
        p.writeByte(1); p.write(new byte[3]); p.writeShort(0x303); p.write(new byte[32]); p.writeByte(0); p.writeShort(2); p.writeShort(0x1301); p.writeByte(1); p.writeByte(0);
        p.writeShort(hostname.length + 13); p.writeShort(0xaaaa); p.writeShort(0); p.writeShort(0); p.writeShort(hostname.length + 5); p.writeShort(hostname.length + 3); p.writeByte(0); p.writeShort(hostname.length); p.write(hostname);
        ByteArrayOutputStream record = new ByteArrayOutputStream(); DataOutputStream r = new DataOutputStream(record); r.writeByte(22); r.writeShort(0x301); r.writeShort(payload.size()); r.write(payload.toByteArray());
        assertEquals("casino.com", TlsNames.sni(record.toByteArray()));
    }
    @Test public void malformedInputNeverCrashes() { for (int size = 0; size < 70; size++) assertNull(TlsNames.sni(new byte[size])); }
}
