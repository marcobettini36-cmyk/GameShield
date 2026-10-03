package it.gameshield;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
public class DnsTest {
    @Test public void roundTripsQuestion() throws Exception { byte[] q = Dns.query("example.org", 1234); assertEquals(1234, Dns.u16(q, 0)); assertEquals("example.org", Dns.question(q)); }
    @Test public void refusedPreservesIdAndQuestion() throws Exception { byte[] q = Dns.query("casino.com", 42), r = Dns.refused(q, 3); assertEquals(42, Dns.u16(r, 0)); assertEquals(3, r[3] & 15); assertEquals(0, Dns.u16(r, 6)); assertEquals(q.length, r.length); assertTrue((r[2] & 128) != 0); }
    @Test(expected = IOException.class) public void rejectsShortPackets() throws Exception { Dns.question(new byte[]{1, 2}); }
    @Test(expected = IOException.class) public void rejectsPointerLoop() throws Exception { byte[] q = new byte[18]; q[5] = 1; q[12] = (byte) 0xc0; q[13] = 12; Dns.question(q); }
    @Test(expected = IOException.class) public void rejectsTruncatedLabel() throws Exception { byte[] q = new byte[14]; q[5] = 1; q[12] = 63; Dns.question(q); }
    @Test public void checksCompressedCname() throws Exception {
        byte[] q = Dns.query("example.org", 42); q[2] = (byte) 0x81; q[7] = 1;
        ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(q); DataOutputStream out = new DataOutputStream(b);
        out.writeShort(0xc00c); out.writeShort(5); out.writeShort(1); out.writeInt(60);
        byte[] alias = Arrays.copyOfRange(Dns.query("casino.com", 0), 12, 24);
        out.writeShort(alias.length); out.write(alias);
        assertTrue(Dns.containsBlockedAlias(b.toByteArray(), new DomainRules(Arrays.asList("casino.com"))));
        assertFalse(Dns.containsBlockedAlias(b.toByteArray(), new DomainRules(Arrays.asList("other.com"))));
    }
    @Test public void parsesIpv4Record() throws Exception {
        byte[] q = Dns.query("example.org", 42); q[2] = (byte) 0x81; q[7] = 1;
        ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(q); DataOutputStream out = new DataOutputStream(b);
        out.writeShort(0xc00c); out.writeShort(1); out.writeShort(1); out.writeInt(60); out.writeShort(4); out.write(new byte[]{1, 2, 3, 4});
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Dns.firstIpv4(b.toByteArray()));
    }
    @Test(expected = IOException.class) public void rejectsOversizeAnswer() throws Exception {
        byte[] q = Dns.query("example.org", 42); q[7] = 1;
        ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(q); DataOutputStream out = new DataOutputStream(b);
        out.writeShort(0xc00c); out.writeShort(1); out.writeShort(1); out.writeInt(60); out.writeShort(65535);
        Dns.containsBlockedAlias(b.toByteArray(), new DomainRules(Arrays.asList("other.com")));
    }
}
