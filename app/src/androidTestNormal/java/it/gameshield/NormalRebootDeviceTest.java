package it.gameshield;
import android.app.*;
import android.content.*;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

/** Two phases separated by an actual emulator reboot in the external runner. */
public class NormalRebootDeviceTest {
    @Test public void prepareBootCommitment()throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();Context c=i.getTargetContext();NormalSafety.prefs(c).edit().clear().commit();
        Activity a=i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        c.startForegroundService(new Intent(c,ShieldVpnService.class));long end=SystemClock.elapsedRealtime()+180_000;
        while(!NormalSafety.protectedNow(c)&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(200);assertTrue(NormalSafety.protectedNow(c));
        assertTrue(NormalAutonomy.start(c,7));NormalSafety.prefs(c).edit().putBoolean("contentConsent",true).putBoolean("recovery",true).putInt("testBootBefore",NormalSafety.boot(c)).putLong("testAutonomyWall",NormalSafety.prefs(c).getLong("autonomyWall",0)).commit();NormalSafety.startMonitor(c);i.runOnMainSync(a::finish);
    }
    @Test public void verifyAfterActualReboot() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotEquals("Runner did not reboot Android",NormalSafety.prefs(c).getInt("testBootBefore",-1),NormalSafety.boot(c));
        assertEquals(NormalSafety.prefs(c).getLong("testAutonomyWall",-1),NormalSafety.prefs(c).getLong("autonomyWall",-2));
        assertTrue(NormalAutonomy.remaining(c)>6*NormalSafety.DAY);assertTrue(NormalContentService.consent(c));assertFalse(NormalSafety.granted(c));
        long until=SystemClock.elapsedRealtime()+60_000;while(!NormalSafety.protectedNow(c)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);
        long layerUntil=SystemClock.elapsedRealtime()+60_000;while(!NormalContentService.rulesReady()&&SystemClock.elapsedRealtime()<layerUntil)SystemClock.sleep(200);
        Bundle evidence=new Bundle();evidence.putString("rebootProtectionState","VPN="+NormalSafety.protectedNow(c)+"; contenuti="+NormalContentService.connected()+"; regole="+NormalContentService.rulesReady());InstrumentationRegistry.getInstrumentation().sendStatus(2,evidence);
        assertTrue("Configured Accessibility service not restored after reboot",NormalContentService.connected());assertTrue(NormalContentService.rulesReady());
        // Persistence is guaranteed by storage; OEM background recovery is best effort.
        assertTrue(NormalSafety.grant(c));assertTrue(NormalSafety.voluntaryStop(c));assertEquals(0,NormalAutonomy.remaining(c));
    }
}
