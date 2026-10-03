package it.gameshield;
import android.content.*;
import android.net.VpnService;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        // Android manages Strong always-on; normal can resume an already authorized VPN.
        if (c.getSharedPreferences("shield", 0).getBoolean("wanted", false) && VpnService.prepare(c) == null) {
            try { c.startForegroundService(new Intent(c, ShieldVpnService.class)); }
            catch (IllegalStateException e) { c.getSharedPreferences("shield", 0).edit().putString("error", "Apri GameShield per riattivare la VPN").apply(); }
        }
    }
}
