package it.gameshield;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** RFC 9001/9369 Initial keys are public. Reads ClientHello only; never decrypts application traffic.
 * Unsupported versions, ECH and incomplete CRYPTO messages pass through, not a blanket UDP ban. */
public final class QuicNames {
    private QuicNames() {}
    public static String sni(byte[] packet) {
        try {
            if (packet.length < 32 || (packet[0] & 0xc0) != 0xc0) return null;
            Cursor c = new Cursor(packet); c.p = 1;
            long version = c.fixed(4);
            boolean v2 = version == 0x6b3343cfL;
            if (version != 1 && !v2 || ((packet[0] >> 4) & 3) != (v2 ? 1 : 0)) return null;
            byte[] dcid = c.bytes(c.integer()); c.bytes(c.integer()); c.bytes(c.variable());
            int length = c.variable(), pnOffset = c.p;
            if (length < 20 || pnOffset + length > packet.length || pnOffset + 20 > packet.length) return null;
            byte[] secret = extract(hex(v2 ? "0dede3def700a6db819381be6e269dcbf9bd2ed9" : "38762cf7f55934b34d179ae6a4c80cadccbb7f0a"), dcid);
            byte[] client = expand(secret, "client in", 32);
            String prefix = v2 ? "quicv2 " : "quic ";
            byte[] key = expand(client, prefix + "key", 16), iv = expand(client, prefix + "iv", 12);
            Cipher hp = Cipher.getInstance("AES/ECB/NoPadding");
            hp.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(expand(client, prefix + "hp", 16), "AES"));
            byte[] mask = hp.doFinal(Arrays.copyOfRange(packet, pnOffset + 4, pnOffset + 20));
            byte first = (byte) (packet[0] ^ (mask[0] & 15)); int pnSize = (first & 3) + 1;
            byte[] aad = Arrays.copyOf(packet, pnOffset + pnSize); aad[0] = first;
            long pn = 0;
            for (int i = 0; i < pnSize; i++) { aad[pnOffset + i] ^= mask[i + 1]; pn = (pn << 8) | (aad[pnOffset + i] & 255); }
            for (int i = 0; i < 8; i++) iv[iv.length - 1 - i] ^= (byte) (pn >>> (8 * i));
            Cipher aead = Cipher.getInstance("AES/GCM/NoPadding");
            aead.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv)); aead.updateAAD(aad);
            byte[] plain = aead.doFinal(Arrays.copyOfRange(packet, pnOffset + pnSize, pnOffset + length));
            Cursor frames = new Cursor(plain); byte[] hello = new byte[16384]; BitSet received = new BitSet(hello.length);
            while (frames.p < plain.length) {
                int type = frames.variable();
                if (type == 0 || type == 1) continue; // padding / ping
                if (type == 2 || type == 3) {
                    frames.variable(); frames.variable(); int ranges = frames.variable(); frames.variable();
                    if (ranges > 64) return null;
                    for (int i = 0; i < ranges; i++) { frames.variable(); frames.variable(); }
                    if (type == 3) { frames.variable(); frames.variable(); frames.variable(); }
                } else if (type == 6) {
                    int offset = frames.variable(), size = frames.variable();
                    if (offset > hello.length || size > hello.length - offset) return null;
                    System.arraycopy(frames.bytes(size), 0, hello, offset, size); received.set(offset, offset + size);
                } else return null;
            }
            if (received.nextClearBit(0) < 4 || hello[0] != 1) return null;
            int size = 4 + ((hello[1] & 255) << 16 | (hello[2] & 255) << 8 | hello[3] & 255);
            if (size > hello.length || received.nextClearBit(0) < size) return null;
            byte[] record = new byte[size + 5]; record[0] = 22; record[1] = 3; record[2] = 3;
            record[3] = (byte) (size >>> 8); record[4] = (byte) size;
            System.arraycopy(hello, 0, record, 5, size); return TlsNames.sni(record);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IndexOutOfBoundsException e) { return null; }
    }
    private static byte[] extract(byte[] salt, byte[] value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(salt, "HmacSHA256")); return mac.doFinal(value);
    }
    private static byte[] expand(byte[] secret, String label, int size) throws GeneralSecurityException, IOException {
        byte[] name = ("tls13 " + label).getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(b);
        d.writeShort(size); d.writeByte(name.length); d.write(name); d.writeByte(0); d.writeByte(1);
        return Arrays.copyOf(extract(secret, b.toByteArray()), size);
    }
    private static byte[] hex(String h) {
        byte[] b = new byte[h.length() / 2]; for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16); return b;
    }
    private static final class Cursor {
        final byte[] data; int p;
        Cursor(byte[] data) { this.data = data; }
        int integer() throws EOFException { if (p >= data.length) throw new EOFException(); return data[p++] & 255; }
        long fixed(int size) throws EOFException { long value = 0; while (size-- > 0) value = (value << 8) | integer(); return value; }
        int variable() throws IOException {
            int first = integer(), remaining = (1 << (first >>> 6)) - 1; long value = first & 63;
            while (remaining-- > 0) value = (value << 8) | integer();
            if (value > Integer.MAX_VALUE) throw new IOException("QUIC field too large"); return (int) value;
        }
        byte[] bytes(int size) throws EOFException {
            if (size < 0 || size > data.length - p) throw new EOFException(); byte[] value = Arrays.copyOfRange(data, p, p + size); p += size; return value;
        }
    }
}
