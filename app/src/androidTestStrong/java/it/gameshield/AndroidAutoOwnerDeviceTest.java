package it.gameshield;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.*;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
/** Dedicated disposable emulator provisioned by the CI harness. Never on a personal phone. */
public class AndroidAutoOwnerDeviceTest {
    private static final String SECRET="emulator-custodian-fixture";
    private final Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final DevicePolicyManager dpm=c.getSystemService(DevicePolicyManager.class);
    private final ComponentName admin=new ComponentName(c,AdminReceiver.class);
    private Network vpn(){for(Network n:c.getSystemService(ConnectivityManager.class).getAllNetworks()){NetworkCapabilities p=c.getSystemService(ConnectivityManager.class).getNetworkCapabilities(n);if(p!=null&&p.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return n;}return null;}
    private void ready(boolean routing)throws Exception {
        long until=SystemClock.elapsedRealtime()+240000;
        while(SystemClock.elapsedRealtime()<until){
            if(ShieldVpnService.running&&vpn()!=null&&AndroidAutoCompatibilityManager.routingApplied==routing&&c.getSharedPreferences("shield",0).getBoolean("connectivityOk",false))return;
            SystemClock.sleep(250);
        }
        fail("VPN/routing/connectivity did not become ready");
    }
    private void policy(Set<String> expected){assertTrue(dpm.isDeviceOwnerApp(c.getPackageName()));assertTrue(dpm.isUninstallBlocked(admin,c.getPackageName()));assertEquals(c.getPackageName(),dpm.getAlwaysOnVpnPackage(admin));assertTrue(dpm.isAlwaysOnVpnLockdownEnabled(admin));assertEquals(expected,dpm.getAlwaysOnVpnLockdownWhitelist(admin));}
    private void traffic()throws Exception {
        Network n=vpn();assertNotNull(n);byte[] q=Dns.query("playzilla.com",731),b=new byte[2048];
        try(DatagramSocket s=new DatagramSocket()){n.bindSocket(s);s.setSoTimeout(10000);s.send(new DatagramPacket(q,q.length,InetAddress.getByName("198.18.0.2"),53));s.receive(new DatagramPacket(b,b.length));assertEquals(3,b[3]&15);}
        HttpsURLConnection h=(HttpsURLConnection)n.openConnection(new URL("https://www.google.it/"));h.setConnectTimeout(15000);h.setReadTimeout(15000);try{assertTrue(h.getResponseCode()>0);}finally{h.disconnect();}
    }
    private void configure(boolean enabled)throws Exception {
        assertTrue(new Guardian(c).authenticate(SECRET.toCharArray()));
        assertTrue(c.getSharedPreferences("android_auto",0).edit().putBoolean("enabled",enabled).commit());
        Set<String> expected=AndroidAutoCompatibilityManager.requestedExclusions(c);
        new StrongPolicy(c).syncAndroidAutoExceptions(expected);
        c.startForegroundService(new Intent(c,ShieldVpnService.class).setAction("RECONFIGURE_AUTO"));
        ready(expected.contains(AndroidAutoPolicy.HOST));policy(expected);traffic();
    }
    @Test public void lockdownExceptionRemainsNarrowAndArmsReboot()throws Exception {
        assertTrue("CI must provision this disposable emulator",dpm.isDeviceOwnerApp(c.getPackageName()));
        c.getSharedPreferences("guardian",0).edit().clear().commit();new Guardian(c).setup(SECRET.toCharArray());
        c.getSharedPreferences("android_auto",0).edit().putBoolean("enabled",false).commit();
        assertNull("Dedicated emulator must grant VPN app-op and prepare its package",VpnService.prepare(c));
        c.startForegroundService(new Intent(c,ShieldVpnService.class));ready(false);
        // Exercise only the changed policy surface. Do not disable ADB in the test runner.
        dpm.setAlwaysOnVpnPackage(admin,c.getPackageName(),true,Collections.emptySet());
        dpm.setUninstallBlocked(admin,c.getPackageName(),true);policy(Collections.emptySet());
        configure(true);configure(false);configure(true);
        assertTrue(c.getSharedPreferences("shield",0).edit().putBoolean("wanted",true).commit());
        android.util.Log.i("AndroidAutoOwnerTest","lockdown retained; only verified host exception; armed for real emulator reboot");
    }
    @Test public void custodianCleanup()throws Exception {
        if(dpm.isDeviceOwnerApp(c.getPackageName()))new StrongPolicy(c).release(SECRET.toCharArray());
        assertFalse(dpm.isDeviceOwnerApp(c.getPackageName()));
        assertFalse(c.getSharedPreferences("shield",0).getBoolean("wanted",true));
    }
}
