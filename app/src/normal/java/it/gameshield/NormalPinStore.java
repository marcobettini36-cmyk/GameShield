package it.gameshield;
import android.content.Context;
import java.security.GeneralSecurityException;
/** App-private salted hash, excluded from backup by the existing application manifest. */
final class NormalPinStore {
    private final android.content.SharedPreferences prefs;
    NormalPinStore(Context context) { prefs=context.getSharedPreferences("normal_disable_pin",Context.MODE_PRIVATE); }
    String hash() { return prefs.getString("hash",null); }
    boolean configured() { return hash()!=null; }
    boolean verify(char[] pin) throws GeneralSecurityException { return NormalPinCrypto.verify(pin,hash()); }
    synchronized boolean replace(String expected,String replacement) {
        if(!java.util.Objects.equals(expected,hash())) return false;
        android.content.SharedPreferences.Editor e=prefs.edit();
        if(replacement==null) e.remove("hash"); else e.putString("hash",replacement);
        return e.commit();
    }
}
