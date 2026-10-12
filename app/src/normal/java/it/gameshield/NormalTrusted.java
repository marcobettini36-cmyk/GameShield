package it.gameshield;
import android.content.Context;
import java.security.GeneralSecurityException;
import java.util.Objects;

final class NormalTrusted {
    static boolean verify(Context c,char[] code,String expected) {return verify(c,code,expected,false);}
    static boolean release(Context c,char[] code,String expected) {return verify(c,code,expected,true);}
    private static boolean verify(Context c,char[] code,String expected,boolean release) {
        synchronized(NormalSafety.LOCK) {
            android.content.SharedPreferences p=NormalSafety.prefs(c);
            if(expected==null || !Objects.equals(expected,p.getString("trustedHash",null)) || System.currentTimeMillis()<p.getLong("trustedRetry",0))return false;
            boolean ok=false;try{ok=NormalPinCrypto.verify(code,expected);}catch(GeneralSecurityException ignored){}
            if(ok){if(!p.edit().putInt("trustedFailures",0).remove("trustedRetry").commit())return false;return !release || NormalSafety.grant(c);}
            int failures=p.getInt("trustedFailures",0)+1;
            p.edit().putInt("trustedFailures",failures).putLong("trustedRetry",failures>=5?System.currentTimeMillis()+60_000:0).commit();return false;
        }
    }
    static boolean replace(Context c,String expected,String replacement) {
        synchronized(NormalSafety.LOCK) {
            android.content.SharedPreferences p=NormalSafety.prefs(c);if(!Objects.equals(expected,p.getString("trustedHash",null)))return false;
            android.content.SharedPreferences.Editor e=p.edit().remove("grantUntil").remove("trustedRetry").putInt("trustedFailures",0);
            if(replacement==null)e.remove("trustedHash");else e.putString("trustedHash",replacement);
            return e.commit();
        }
    }
}
