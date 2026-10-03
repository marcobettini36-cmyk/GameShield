package it.gameshield;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.*;
import java.util.Collections;

public final class StrongPolicy {
    private final Context context;
    private final DevicePolicyManager manager;
    private final ComponentName admin;
    public StrongPolicy(Context c) {
        context = c; manager = c.getSystemService(DevicePolicyManager.class); admin = new ComponentName(c, AdminReceiver.class);
    }
    public boolean owner() { return BuildConfig.STRONG && manager.isDeviceOwnerApp(context.getPackageName()); }
    public boolean locked() { return owner() && manager.isUninstallBlocked(admin, context.getPackageName()); }
    public void enable() throws android.content.pm.PackageManager.NameNotFoundException {
        if (!owner() || !new Guardian(context).configured()) throw new SecurityException("Device Owner e codice custode richiesti");
        manager.setAlwaysOnVpnPackage(admin, context.getPackageName(), true);
        manager.setUninstallBlocked(admin, context.getPackageName(), true);
        manager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN);
        manager.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
        manager.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
        manager.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
        manager.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER);
        if (Build.VERSION.SDK_INT >= 30) {
            manager.setUserControlDisabledPackages(admin, Collections.singletonList(context.getPackageName()));
            manager.setGlobalPrivateDnsModeOpportunistic(admin);
        }
    }
    /** Only called after guardian verification; VPN remains running until restrictions are removed. */
    public void release(char[] code) throws Exception {
        if (!new Guardian(context).authenticate(code)) throw new SecurityException("Codice errato o attesa attiva");
        if (owner()) {
            manager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN);
            manager.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
            manager.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
            manager.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
            manager.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER);
            if (Build.VERSION.SDK_INT >= 30) manager.setUserControlDisabledPackages(admin, Collections.emptyList());
            manager.setAlwaysOnVpnPackage(admin, null, false);
            manager.setUninstallBlocked(admin, context.getPackageName(), false);
            // Explicit custodian release: owner is removed, so uninstall becomes possible.
            manager.clearDeviceOwnerApp(context.getPackageName());
        }
        context.getSharedPreferences("shield", 0).edit().putBoolean("wanted", false).commit();
        new Guardian(context).clearAfterRelease();
        context.stopService(new Intent(context, ShieldVpnService.class));
    }
}
