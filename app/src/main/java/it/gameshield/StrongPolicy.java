package it.gameshield;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.*;
import java.util.Collections;

public final class StrongPolicy {
    private static final Object POLICY_LOCK=new Object();
    private final Context context;
    private final DevicePolicyManager manager;
    private final ComponentName admin;
    public StrongPolicy(Context c) {
        context = c;
        // Normal never needs an administrator, including during shared status checks.
        // A compile-time flavor guard also removes the admin class from its bundle.
        if (BuildConfig.STRONG) {
            manager = c.getSystemService(DevicePolicyManager.class);
            admin = new ComponentName(c, AdminReceiver.class);
        } else {
            manager = null;
            admin = null;
        }
    }
    public boolean owner() { return BuildConfig.STRONG && manager.isDeviceOwnerApp(context.getPackageName()); }
    public boolean locked() { return owner() && manager.isUninstallBlocked(admin, context.getPackageName()); }
    public void enable() throws android.content.pm.PackageManager.NameNotFoundException {
        synchronized(POLICY_LOCK) { enableLocked(); }
    }
    private void enableLocked() throws android.content.pm.PackageManager.NameNotFoundException {
        if (!owner() || !new Guardian(context).configured()) throw new SecurityException("Device Owner e codice custode richiesti");
        if (!ShieldVpnService.running || !context.getSharedPreferences("shield", 0).getBoolean("connectivityOk", false))
            throw new SecurityException("Prima verifica DNS e HTTPS nel tunnel");
        manager.setAlwaysOnVpnPackage(admin, context.getPackageName(), true, AndroidAutoCompatibilityManager.requestedExclusions(context));
        manager.setUninstallBlocked(admin, context.getPackageName(), true);
        manager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN);
        manager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
        manager.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
        manager.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
        manager.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
        manager.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER);
        if (Build.VERSION.SDK_INT >= 30) {
            manager.setUserControlDisabledPackages(admin, Collections.singletonList(context.getPackageName()));
            manager.setGlobalPrivateDnsModeOpportunistic(admin);
        }
    }
    /** Keep lockdown enabled; only the verified host exception approved in protected settings. */
    void syncAndroidAutoExceptions(java.util.Set<String> packages) throws android.content.pm.PackageManager.NameNotFoundException {
        syncAndroidAutoExceptions(packages,false);
    }
    void syncAndroidAutoExceptions(java.util.Set<String> packages,boolean refreshUidRanges) throws android.content.pm.PackageManager.NameNotFoundException {
        synchronized(POLICY_LOCK) { syncAndroidAutoExceptionsLocked(packages,refreshUidRanges); }
    }
    private void syncAndroidAutoExceptionsLocked(java.util.Set<String> packages,boolean refreshUidRanges) throws android.content.pm.PackageManager.NameNotFoundException {
        if(!AndroidAutoCompatibilityManager.requestedExclusions(context).containsAll(packages))throw new SecurityException("Unverified Android Auto exception");
        if(!owner() || !locked() || !context.getPackageName().equals(manager.getAlwaysOnVpnPackage(admin)) || !manager.isAlwaysOnVpnLockdownEnabled(admin))return;
        java.util.Set<String> existing=manager.getAlwaysOnVpnLockdownWhitelist(admin);
        if(refreshUidRanges || !new java.util.HashSet<>(existing==null?java.util.Collections.emptySet():existing).equals(packages))
            manager.setAlwaysOnVpnPackage(admin,context.getPackageName(),true,packages);
    }
    /** Only called after guardian verification; VPN remains running until restrictions are removed. */
    public void release(char[] code) throws Exception {
        if (!new Guardian(context).authenticate(code)) throw new SecurityException("Codice errato o attesa attiva");
        synchronized(POLICY_LOCK) { releasePoliciesLocked(); }
    }
    private void releasePoliciesLocked() throws android.content.pm.PackageManager.NameNotFoundException {
        if (owner()) {
            manager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN);
            manager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
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
        context.startService(new Intent(context, ShieldVpnService.class).setAction("STOP"));
    }
}
