package it.gameshield;
import android.app.admin.*;
import android.content.*;
import android.content.pm.*;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

/** Shell activates this on the disposable emulator; the app itself uses consent UI. */
public class NormalAdminDeviceTest {
    @Test public void optionalAdministratorHasNoOwnerOrDestructivePolicyAndCanBeReleased()throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        DevicePolicyManager manager=c.getSystemService(DevicePolicyManager.class);ComponentName admin=new ComponentName(c,NormalAdminReceiver.class);
        Assume.assumeTrue("Legacy administrator unavailable on this emulator",manager!=null && manager.isAdminActive(admin));
        assertFalse(manager.isDeviceOwnerApp(c.getPackageName()));assertFalse(manager.isProfileOwnerApp(c.getPackageName()));
        ResolveInfo info=new ResolveInfo();info.activityInfo=c.getPackageManager().getReceiverInfo(admin,PackageManager.GET_META_DATA);
        assertTrue(new DeviceAdminInfo(c.getPackageManager(),info).getUsedPolicies().isEmpty());
        NormalSafety.prefs(c).edit().putBoolean("recovery",false).putBoolean("smsConsent",false).commit();
        assertTrue(NormalSafety.voluntaryStop(c));long until=SystemClock.elapsedRealtime()+15_000;
        while(manager.isAdminActive(admin) && SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        assertFalse("Normal administrator must remain removable",manager.isAdminActive(admin));assertFalse(manager.isDeviceOwnerApp(c.getPackageName()));
    }
}
