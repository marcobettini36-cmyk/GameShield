package it.gameshield;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

/** Emulator-only fixture test: actual Android Accessibility event and HOME action. */
public class NormalLayersDeviceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();private Context c;
    @Before public void before(){c=inst.getTargetContext();inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);NormalSafety.prefs(c).edit().clear().commit();c.stopService(new Intent(c,NormalMonitorService.class));if(ShieldVpnService.running)c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));else c.stopService(new Intent(c,ShieldVpnService.class));SystemClock.sleep(2000);}
    @After public void after(){NormalSafety.prefs(c).edit().clear().commit();if(ShieldVpnService.running)c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));c.getSharedPreferences("shield",0).edit().remove("custom").commit();shell("input keyevent KEYCODE_HOME");}
    private void shell(String command){try{inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command).close();SystemClock.sleep(500);}catch(java.io.IOException e){throw new AssertionError(e);}}
    private void accessibility(boolean on){shell("settings put secure enabled_accessibility_services "+(on?"it.gameshield/it.gameshield.NormalContentService":"null"));shell("settings put secure accessibility_enabled "+(on?1:0));long end=SystemClock.elapsedRealtime()+15_000;while(NormalContentService.connected()!=on && SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);assertEquals(on,NormalContentService.connected());}
    private void fixture(){c.startActivity(new Intent().setComponent(new ComponentName("it.gameshield.fixture","it.gameshield.fixture.FixtureActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private String foreground(){android.view.accessibility.AccessibilityNodeInfo r=inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();return r==null||r.getPackageName()==null?"":r.getPackageName().toString();}
    @Test public void independentServiceInterruptsFixtureWithVpnOffAndHonorsRevocation()throws Exception {
        assertFalse(ShieldVpnService.running);NormalSafety.prefs(c).edit().putBoolean("contentConsent",true).commit();accessibility(true);
        long loaded=SystemClock.elapsedRealtime()+120_000;while(!NormalContentService.rulesReady()&&SystemClock.elapsedRealtime()<loaded)SystemClock.sleep(200);assertTrue("Actual rule load failed",NormalContentService.rulesReady());
        long count=NormalContentService.interventions();fixture();long until=SystemClock.elapsedRealtime()+15_000;
        while(SystemClock.elapsedRealtime()<until && NormalContentService.interventions()==count)SystemClock.sleep(100);
        assertTrue("No real HOME intervention observed",NormalContentService.interventions()>count);SystemClock.sleep(500);
        assertFalse("Synthetic gambling app still foreground",foreground().equals("it.gameshield.fixture"));assertFalse(ShieldVpnService.running);
        accessibility(false);fixture();SystemClock.sleep(2000);assertEquals("it.gameshield.fixture",foreground());
    }
    @Test public void noConsentAndSystemSettingsNeverIntervened(){
        accessibility(true);NormalSafety.prefs(c).edit().putBoolean("contentConsent",false).commit();fixture();SystemClock.sleep(2000);assertEquals("it.gameshield.fixture",foreground());
        NormalSafety.prefs(c).edit().putBoolean("contentConsent",true).commit();c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2000);assertEquals("com.android.settings",foreground());
    }
    @Test public void autonomyCancellationKeepsPeriodEmergencyAndExpiryReleaseGate(){
        assertTrue(NormalAutonomy.start(c,7));assertFalse(NormalAutonomy.start(c,30));NormalSafety.requestRelease(c);NormalSafety.cancelRelease(c);assertTrue(NormalAutonomy.remaining(c)>6*NormalSafety.DAY);assertFalse(NormalSafety.voluntaryStop(c));
        assertTrue(NormalSafety.grant(c));assertEquals(0,NormalSafety.releaseRemaining(c));assertTrue(NormalSafety.voluntaryStop(c));assertEquals(0,NormalAutonomy.remaining(c));assertFalse(NormalContentService.consent(c));
        assertTrue(NormalAutonomy.start(c,30));NormalSafety.prefs(c).edit().putLong("autonomyWall",System.currentTimeMillis()-30*NormalSafety.DAY).putInt("autonomyBoot",NormalSafety.boot(c)-1).commit();assertEquals(0,NormalAutonomy.remaining(c));
    }
    @Test public void dnsProbeRefusesToCertifyAutomaticDnsAndKnowsSinkholeResponses()throws Exception {
        assertFalse(ShieldVpnService.running);String result=NormalDnsProbe.run(c,"bet365.com");assertTrue(result,result.startsWith("NON VERIFICATO:"));
        assertTrue(NormalDnsProbe.blocked(java.util.List.of(java.net.InetAddress.getByName("0.0.0.0"))));assertFalse(NormalDnsProbe.blocked(java.util.List.of(java.net.InetAddress.getByName("8.8.8.8"))));
    }
    @Test public void actualVpnKeepsAllowedHttpsAndBlocksGamblingAndManualDomain()throws Exception {
        Activity main=inst.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            assertNull(VpnService.prepare(c));c.startForegroundService(new Intent(c,ShieldVpnService.class));
            long end=SystemClock.elapsedRealtime()+180_000;while(!NormalSafety.protectedNow(c)&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(200);assertTrue(NormalSafety.protectedNow(c));
            ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);Network vpn=null;for(Network n:cm.getAllNetworks())if(cm.getNetworkCapabilities(n).hasTransport(NetworkCapabilities.TRANSPORT_VPN)){vpn=n;break;}assertNotNull(vpn);
            for(String host:new String[]{"www.google.it","www.youtube.com","www.wikipedia.org","github.com"}){
                javax.net.ssl.HttpsURLConnection h=(javax.net.ssl.HttpsURLConnection)vpn.openConnection(new java.net.URL("https://"+host+"/"));h.setConnectTimeout(15_000);h.setReadTimeout(15_000);try{assertTrue(host,h.getResponseCode()>0);}finally{h.disconnect();}
            }
            RuleStore baseline=new RuleStore(c);
            for(String host:new String[]{"bet365.com","playzilla.com","excitewin.com"}){assertTrue(host,baseline.rules().blocks(host));boolean denied;try{denied=NormalDnsProbe.blocked(java.util.Arrays.asList(vpn.getAllByName(host)));}catch(java.net.UnknownHostException expected){denied=true;}assertTrue("Gambling DNS not blocked: "+host,denied);}
            c.getSharedPreferences("shield",0).edit().putString("custom","example.org\n").commit();c.startService(new Intent(c,ShieldVpnService.class).setAction("RELOAD"));
            long reloadUntil=SystemClock.elapsedRealtime()+60_000;
            while(c.getSharedPreferences("shield",0).getInt("rules",0)<baseline.rules().size()+1 && SystemClock.elapsedRealtime()<reloadUntil)SystemClock.sleep(200);
            assertTrue("Manual rule reload did not complete",c.getSharedPreferences("shield",0).getInt("rules",0)>=baseline.rules().size()+1);
            assertTrue("Manual rule not blocked",NormalDnsProbe.blocked(NormalDnsProbe.resolve(vpn,"example.org")));
            assertTrue(NormalSafety.protectedNow(c));assertTrue(NormalDnsProbe.run(c,"bet365.com").contains("una VPN è attiva"));
        }finally{inst.runOnMainSync(main::finish);}
    }
}
