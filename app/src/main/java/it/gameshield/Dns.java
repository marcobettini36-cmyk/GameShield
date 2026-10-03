package it.gameshield;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Dns {
    public static int u16(byte[] b, int p) throws IOException {
        if (p < 0 || p + 2 > b.length) throw new IOException("Truncated DNS");
        return ((b[p] & 255) << 8) | (b[p + 1] & 255);
    }
    private static String name(byte[] b, int[] cursor) throws IOException {
        StringBuilder out = new StringBuilder(); int p = cursor[0], next = -1, steps = 0;
        while (true) {
            if (p >= b.length || ++steps > 128) throw new IOException("Invalid DNS name");
            int n = b[p++] & 255;
            if (n == 0) break;
            if ((n & 0xc0) == 0xc0) {
                if (p >= b.length) throw new IOException("Truncated pointer");
                if (next < 0) next = p + 1;
                p = ((n & 63) << 8) | (b[p] & 255); continue;
            }
            if (n > 63 || p + n > b.length) throw new IOException("Invalid label");
            if (out.length() > 0) out.append('.');
            out.append(new String(b, p, n, StandardCharsets.US_ASCII)); p += n;
            if (out.length() > 253) throw new IOException("Name too long");
        }
        cursor[0] = next < 0 ? p : next; return out.toString();
    }
    public static String question(byte[] b) throws IOException {
        if (b.length < 12 || u16(b, 4) != 1 || (b[2] & 0x80) != 0) throw new IOException("Invalid query");
        int[] p = {12}; String q = name(b, p);
        if (p[0] + 4 != b.length) {
            if (p[0] + 4 > b.length) throw new IOException("Truncated question");
        }
        return q;
    }
    public static byte[] refused(byte[] query, int rcode) throws IOException {
        question(query); int[] p = {12}; name(query, p);
        byte[] result = Arrays.copyOf(query, p[0] + 4);
        result[2] = (byte) (0x80 | (query[2] & 0x79)); result[3] = (byte) (0x80 | rcode);
        for (int i = 6; i < 12; i++) result[i] = 0;
        return result;
    }
    public static boolean containsBlockedAlias(byte[] b, DomainRules rules) throws IOException {
        if (b.length < 12) throw new IOException("Short DNS");
        int[] p = {12}; int questions = u16(b, 4);
        if (questions != 1) throw new IOException("Invalid questions");
        if (rules.blocks(name(b, p))) return true; p[0] += 4;
        int count = u16(b, 6) + u16(b, 8) + u16(b, 10);
        if (count > 4096) throw new IOException("Too many records");
        for (int i = 0; i < count; i++) {
            String owner = name(b, p); int type = u16(b, p[0]); int len = u16(b, p[0] + 8);
            p[0] += 10; int end = p[0] + len;
            if (end > b.length) throw new IOException("Short record");
            if (rules.blocks(owner)) return true;
            if (type == 5 || type == 39) { int[] alias = {p[0]}; if (rules.blocks(name(b, alias))) return true; }
            p[0] = end;
        }
        return false;
    }
    public static byte[] query(String domain, int id) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(id); out.writeShort(0x100); out.writeShort(1); out.writeShort(0); out.writeShort(0); out.writeShort(0);
        String d = DomainRules.normalize(domain); if (d == null) throw new IOException("Invalid domain");
        for (String s : d.split("\\.")) { out.writeByte(s.length()); out.writeBytes(s); }
        out.writeByte(0); out.writeShort(1); out.writeShort(1); return bytes.toByteArray();
    }
    public static byte[] firstIpv4(byte[] b) throws IOException {
        int[] p = {12}; name(b, p); p[0] += 4;
        for (int i = 0; i < u16(b, 6); i++) {
            name(b, p); int type = u16(b, p[0]), len = u16(b, p[0] + 8); p[0] += 10;
            if (p[0] + len > b.length) throw new IOException("Short answer");
            if (type == 1 && len == 4) return Arrays.copyOfRange(b, p[0], p[0] + 4);
            p[0] += len;
        }
        throw new IOException("No IPv4 answer");
    }
}
