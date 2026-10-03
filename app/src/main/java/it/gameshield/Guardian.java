package it.gameshield;

import android.content.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public final class Guardian {
    private final android.content.SharedPreferences prefs;
    public Guardian(Context c) { prefs = c.getSharedPreferences("guardian", 0); }
    public boolean configured() { return prefs.contains("hash"); }
    public synchronized void setup(char[] code) throws GeneralSecurityException {
        try {
            if (configured()) throw new IllegalStateException("Codice già configurato");
            if (!prefs.edit().putString("hash", GuardianCrypto.create(code)).commit()) throw new IllegalStateException("Salvataggio fallito");
        } finally { Arrays.fill(code, '\0'); }
    }
    public synchronized boolean authenticate(char[] code) throws GeneralSecurityException {
        try {
            long now = System.currentTimeMillis();
            if (now < prefs.getLong("retryAt", 0)) return false;
            if (GuardianCrypto.verify(code, prefs.getString("hash", null))) {
                prefs.edit().putInt("failures", 0).remove("retryAt").commit(); return true;
            }
            int failures = Math.min(20, prefs.getInt("failures", 0) + 1);
            long wait = failures < 5 ? 0 : Math.min(3600000L, 30000L << Math.min(7, failures - 5));
            prefs.edit().putInt("failures", failures).putLong("retryAt", now + wait).commit(); return false;
        } finally { Arrays.fill(code, '\0'); }
    }
    public long retryAt() { return prefs.getLong("retryAt", 0); }
    public void clearAfterRelease() { prefs.edit().clear().commit(); }
}
