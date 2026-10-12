package it.gameshield;
import android.app.admin.DeviceAdminReceiver;
import android.content.*;

/** Voluntary legacy administrator: no owner, wipe, lock or uninstall restriction APIs. */
public final class NormalAdminReceiver extends DeviceAdminReceiver {
    @Override public CharSequence onDisableRequested(Context c,Intent i) {
        return "Disattivando l'amministratore sarà possibile disinstallare GameShield. La scelta resta tua; nessuna attesa viene imposta alle impostazioni Android.";
    }
    @Override public void onEnabled(Context c,Intent i){NormalSafety.check(c);}
    @Override public void onDisabled(Context c,Intent i){NormalSafety.check(c);}
}
