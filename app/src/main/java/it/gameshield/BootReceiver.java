package it.gameshield;
import android.content.*;
import android.net.VpnService;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) return;
        // Android manages Strong always-on; normal can resume an already authorized VPN.
        if (c.getSharedPreferences("shield", 0).getBoolean("wanted", false) && VpnService.prepare(c) == null) {
            try { c.startForegroundService(new Intent(c, ShieldVpnService.class)); }
            catch (IllegalStateException e) { c.getSharedPreferences("shield", 0).edit().putString("error", "Apri GameShield per riattivare la VPN").apply(); }
        }
    }
}
