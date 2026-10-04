package it.gameshield;
import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
final class NormalPinCrypto {
    static boolean valid(char[] pin) {
        if(pin.length<6 || pin.length>12) return false;
        for(char c:pin) if(c<'0' || c>'9') return false;
        return true;
    }
    static String create(char[] pin) throws GeneralSecurityException {
        if(!valid(pin)) throw new IllegalArgumentException("Usa da 6 a 12 cifre");
        byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
        return "1:"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(pin,salt));
    }
    static boolean verify(char[] pin,String stored) throws GeneralSecurityException {
        if(!valid(pin) || stored==null) return false;
        try {
            String[] p=stored.split(":",-1); if(p.length!=3 || !p[0].equals("1")) return false;
            byte[] salt=Base64.getDecoder().decode(p[1]), hash=Base64.getDecoder().decode(p[2]);
            return salt.length==16 && hash.length==32 && MessageDigest.isEqual(hash,derive(pin,salt));
        } catch(IllegalArgumentException e) { return false; }
    }
    private static byte[] derive(char[] pin,byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(pin,salt,210_000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
