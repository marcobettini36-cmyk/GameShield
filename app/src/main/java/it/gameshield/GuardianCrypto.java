package it.gameshield;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class GuardianCrypto {
    private GuardianCrypto() {}
    public static String create(char[] code) throws GeneralSecurityException {
        if (code.length < 8 || code.length > 128) throw new IllegalArgumentException("Usa da 8 a 128 caratteri");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derive(code, salt));
    }
    private static byte[] derive(char[] code, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(code, salt, 210000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    public static boolean verify(char[] code, String stored) throws GeneralSecurityException {
        if (code.length > 128 || stored == null) return false;
        try {
            String[] parts = stored.split(":", -1); if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]), expected = Base64.getDecoder().decode(parts[1]);
            return salt.length == 16 && expected.length == 32 && MessageDigest.isEqual(expected, derive(code, salt));
        } catch (IllegalArgumentException e) { return false; }
    }
}
