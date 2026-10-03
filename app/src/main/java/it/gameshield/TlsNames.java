package it.gameshield;

import java.nio.charset.StandardCharsets;

/** Reads SNI from a complete first TLS record. ECH/fragmented handshakes remain a documented limit. */
public final class TlsNames {
    private static int u16(byte[] b, int p) { return ((b[p] & 255) << 8) | (b[p + 1] & 255); }
    public static String sni(byte[] b) {
        try {
            if (b.length < 9 || b[0] != 22 || b[5] != 1) return null;
            int p = 43; p += 1 + (b[p] & 255); p += 2 + u16(b, p); p += 1 + (b[p] & 255);
            int end = Math.min(b.length, p + 2 + u16(b, p)); p += 2;
            while (p + 4 <= end) {
                int type = u16(b, p), len = u16(b, p + 2); p += 4;
                if (p + len > end) return null;
                if (type == 0 && len >= 5 && b[p + 2] == 0) {
                    int size = u16(b, p + 3); if (size + 5 > len) return null;
                    return DomainRules.normalize(new String(b, p + 5, size, StandardCharsets.US_ASCII));
                }
                p += len;
            }
        } catch (IndexOutOfBoundsException ignored) { }
        return null;
    }
}
