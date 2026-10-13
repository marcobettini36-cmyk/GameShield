package it.gameshield;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.widget.Button;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import org.junit.*;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Run only on a dedicated emulator: real private storage and Activity lifecycle. */
public class NormalResilienceDeviceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Context c;private Activity screen;
    @Before public void before(){c=inst.getTargetContext();c.stopService(new Intent(c,NormalMonitorService.class));NormalSafety.prefs(c).edit().clear().commit();if(ShieldVpnService.running)c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));else c.stopService(new Intent(c,ShieldVpnService.class));for(int n=0;n<100 && ShieldVpnService.running;n++)SystemClock.sleep(100);c.getSharedPreferences("shield",0).edit().putBoolean("wanted",false).commit();}
    @After public void after(){if(screen!=null)inst.runOnMainSync(()->screen.finish());NormalSafety.prefs(c).edit().clear().commit();NormalJobs.schedule(c);c.stopService(new Intent(c,NormalMonitorService.class));if(ShieldVpnService.running)c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));else c.stopService(new Intent(c,ShieldVpnService.class));}
    @Test public void dayCannotBeSkippedAndSurvivesRecreation()throws Exception {
        screen=inst.startActivitySync(new Intent(c,NormalDisableActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        long wall=NormalSafety.prefs(c).getLong("releaseWall",-1);assertTrue(wall>0);assertTrue(NormalSafety.releaseRemaining(c)>NormalSafety.DAY-10_000);
        Field f=NormalDisableActivity.class.getDeclaredField("proceed");f.setAccessible(true);assertFalse(((Button)f.get(screen)).isEnabled());
        Activity old=screen;inst.runOnMainSync(old::recreate);
        long until=SystemClock.elapsedRealtime()+15_000;
        while(SystemClock.elapsedRealtime()<until){AtomicReference<Activity>a=new AtomicReference<>();inst.runOnMainSync(()->{for(Activity x:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(x instanceof NormalDisableActivity && x!=old)a.set(x);});if(a.get()!=null){screen=a.get();break;}SystemClock.sleep(100);}
        assertNotSame(old,screen);assertEquals(wall,NormalSafety.prefs(c).getLong("releaseWall",-2));assertFalse(((Button)f.get(screen)).isEnabled());
        NormalSafety.cancelRelease(c);assertFalse(NormalSafety.prefs(c).contains("releaseWall"));assertFalse(NormalSafety.granted(c));
    }
    @Test public void trustedCodeIsHashedAndDoesNotIssueStop()throws Exception {
        String hash=NormalPinCrypto.create("123456".toCharArray());assertTrue(NormalTrusted.replace(c,null,hash));NormalSafety.requestRelease(c);
        assertFalse(NormalTrusted.release(c,"654321".toCharArray(),hash));assertTrue(NormalSafety.releaseRemaining(c)>0);
        assertTrue(NormalTrusted.release(c,"123456".toCharArray(),hash));assertEquals(0,NormalSafety.releaseRemaining(c));
        assertEquals(hash,NormalSafety.prefs(c).getString("trustedHash",null));assertNotEquals("123456",hash);
        assertFalse(c.getSharedPreferences("shield",0).getBoolean("wanted",false));
        assertTrue(NormalTrusted.replace(c,hash,null));assertFalse(NormalSafety.granted(c));
    }
    @Test public void deniedSmsPermissionCannotClaimOrSend() {
        Assume.assumeTrue(BuildConfig.AUTOMATIC_SMS_ENABLED);
        Assume.assumeTrue(c.checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED);
        NormalEpisode e=new NormalEpisode();e.observe(true,0,0,NormalSafety.boot(c));e.observe(false,System.currentTimeMillis()-NormalEpisode.SMS_DELAY,SystemClock.elapsedRealtime()-NormalEpisode.SMS_DELAY,NormalSafety.boot(c));assertTrue(NormalSafety.saveEpisode(c,e));
        NormalSafety.prefs(c).edit().putBoolean("smsConsent",true).putString("number","+390000000000").commit();
        NormalSms.maybeSend(c);assertFalse(NormalSafety.episode(c).claimed);assertFalse(NormalSafety.prefs(c).contains("alertAttempt"));assertTrue(NormalSafety.prefs(c).getString("smsIssue","").contains("SEND_SMS"));
    }
    @Test public void episodeClaimSurvivesStorageReloadAndCancelsOnReconnect() {
        NormalEpisode e=new NormalEpisode();e.observe(true,0,0,1);e.observe(false,1,1,1);assertTrue(e.claim(900_001,900_001,1));assertTrue(NormalSafety.saveEpisode(c,e));
        NormalEpisode restored=NormalSafety.episode(c);assertEquals(e.id,restored.id);assertTrue(restored.claimed);assertFalse(restored.claim(1_800_000,1_800_000,2));
        restored.observe(true,1_800_000,1_800_000,2);assertTrue(NormalSafety.saveEpisode(c,restored));assertFalse(NormalSafety.episode(c).off);assertFalse(NormalSafety.episode(c).claimed);
    }
    @Test public void recoveryRestartsAuthorizedTunnelButNeverAfterVoluntaryRelease()throws Exception {
        assertNull(android.net.VpnService.prepare(c));
        NormalSafety.prefs(c).edit().putBoolean("setupSeen060",true).commit();screen=inst.startActivitySync(new Intent(c,NormalHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        c.startForegroundService(new Intent(c,ShieldVpnService.class));awaitProtection();NormalSafety.check(c);
        NormalSafety.prefs(c).edit().putBoolean("recovery",true).putBoolean("paused",false).commit();NormalSafety.startMonitor(c);
        java.util.concurrent.CountDownLatch stopped=new java.util.concurrent.CountDownLatch(1);
        android.content.SharedPreferences shield=c.getSharedPreferences("shield",0);
        android.content.SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->{if("wanted".equals(key)&&!p.getBoolean("wanted",true))stopped.countDown();};
        shield.registerOnSharedPreferenceChangeListener(listener);
        c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));
        assertTrue("STOP transition not observed",stopped.await(30,java.util.concurrent.TimeUnit.SECONDS));shield.unregisterOnSharedPreferenceChangeListener(listener);
        awaitProtection();assertTrue(NormalSafety.protectedNow(c));
        android.net.ConnectivityManager cm=c.getSystemService(android.net.ConnectivityManager.class);android.net.Network vpn=null;
        for(android.net.Network n:cm.getAllNetworks()){android.net.NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)){vpn=n;break;}}
        assertNotNull(vpn);javax.net.ssl.HttpsURLConnection h=(javax.net.ssl.HttpsURLConnection)vpn.openConnection(new java.net.URL("https://www.wikipedia.org/"));h.setConnectTimeout(15_000);h.setReadTimeout(15_000);try{assertTrue(h.getResponseCode()>0);}finally{h.disconnect();}
        assertTrue(NormalSafety.voluntaryStop(c));SystemClock.sleep(5000);NormalSafety.check(c);
        assertFalse(ShieldVpnService.running);assertTrue(NormalSafety.prefs(c).getBoolean("paused",false));assertFalse(NormalSafety.prefs(c).getBoolean("recovery",true));
    }
    private void awaitProtection(){long until=SystemClock.elapsedRealtime()+180_000;while(!NormalSafety.protectedNow(c) && SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);assertTrue("Native tunnel not restored",NormalSafety.protectedNow(c));}
}
