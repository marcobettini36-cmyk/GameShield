package it.gameshield;
import android.content.*;
import android.net.*;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

/** Live external-provider measurement on an explicitly configured disposable emulator. */
public class NormalDnsProviderDeviceTest {
    @Test public void measurePrivateDnsOutsideVpn()throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        NormalSafety.prefs(c).edit().clear().commit();c.stopService(new Intent(c,NormalMonitorService.class));if(ShieldVpnService.running)c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));else c.stopService(new Intent(c,ShieldVpnService.class));
        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);long end=SystemClock.elapsedRealtime()+30_000;LinkProperties lp=null;
        while(SystemClock.elapsedRealtime()<end){Network n=cm.getActiveNetwork();lp=n==null?null:cm.getLinkProperties(n);if(lp!=null&&lp.isPrivateDnsActive()&&"clean.dnsforge.de".equals(lp.getPrivateDnsServerName()))break;SystemClock.sleep(500);}
        Assume.assumeTrue("External DoT provider unavailable/not validated in this emulator",lp!=null&&lp.isPrivateDnsActive()&&"clean.dnsforge.de".equals(lp.getPrivateDnsServerName()));
        assertFalse(NormalDnsProbe.hasVpn(cm));String report=NormalDnsProbe.run(c,"bet365.com");System.out.println("LIVE PRIVATE DNS RESULT: "+report);
        assertTrue(report,report.contains("google.com DNS: OK"));assertTrue(report,report.contains("wikipedia.org DNS: OK"));assertTrue(report,report.contains("github.com DNS: OK"));assertTrue(report,report.contains("HTTPS innocuo: OK"));
        // Record provider classification honestly; provider coverage is not GameShield's blacklist.
        assertTrue(report,report.contains("bet365.com DNS:"));
    }
}
