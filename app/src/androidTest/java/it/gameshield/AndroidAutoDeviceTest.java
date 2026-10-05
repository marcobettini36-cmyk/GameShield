package it.gameshield;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;
/** Emulator security/UI regression, not a substitute for wired/wireless car testing. */
public class AndroidAutoDeviceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Context c; private Activity screen; private ConnectivityManager cm;
    @Before public void before() throws Exception {
        c=inst.getTargetContext();cm=c.getSystemService(ConnectivityManager.class);stop();
        c.getSharedPreferences("android_auto",0).edit().clear().commit();
        c.getSharedPreferences("guardian",0).edit().clear().commit();
        c.getSharedPreferences("normal_disable_pin",0).edit().clear().commit();
        assertNull(VpnService.prepare(c));c.startForegroundService(new Intent(c,ShieldVpnService.class));
        // Reconfiguration arriving while the large rule snapshot is still loading must be harmless.
        for(int i=0;i<3;i++)c.startForegroundService(new Intent(c,ShieldVpnService.class).setAction("RECONFIGURE_AUTO"));
        long until=SystemClock.elapsedRealtime()+180000;
        while((!ShieldVpnService.running||vpn()==null)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);
        active();SystemClock.sleep(2500);blocked();
    }
    @After public void after(){if(screen!=null)ui(screen::finish);stop();for(String p:new String[]{"android_auto","guardian","normal_disable_pin"})c.getSharedPreferences(p,0).edit().clear().commit();}
    private void stop(){c.stopService(new Intent(c,ShieldVpnService.class));for(int i=0;i<100&&(ShieldVpnService.running||vpn()!=null);i++)SystemClock.sleep(100);SystemClock.sleep(1000);}
    private Network vpn(){for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return n;}return null;}
    private void active(){assertTrue("VPN stopped accidentally",ShieldVpnService.running);assertNotNull(vpn());}
    private void blocked()throws Exception {
        byte[] q=Dns.query("playzilla.com",741);byte[] b=new byte[2048];
        try(DatagramSocket s=new DatagramSocket()){vpn().bindSocket(s);s.setSoTimeout(10000);s.send(new DatagramPacket(q,q.length,InetAddress.getByName("198.18.0.2"),53));DatagramPacket p=new DatagramPacket(b,b.length);s.receive(p);assertEquals(3,b[3]&15);}
    }
    private void ui(Runnable r){inst.runOnMainSync(r);inst.waitForIdleSync();}
    private View find(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){View result=find(((ViewGroup)v).getChildAt(i),text);if(result!=null)return result;}}return null;}
    private EditText input(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText r=input(((ViewGroup)v).getChildAt(i));if(r!=null)return r;}}return null;}
    private AlertDialog dialog()throws Exception{Field f=AndroidAutoSettingsActivity.class.getDeclaredField("dialog");f.setAccessible(true);return (AlertDialog)f.get(screen);}
    private void open(){screen=inst.startActivitySync(new Intent(c,AndroidAutoSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();}
    private void change(){ui(()->{View b=find(screen.getWindow().getDecorView(),"Modifica compatibilità");assertNotNull(b);b.performClick();});}
    @Test public void missingOrUntrustedHostAndForgedConnectionRetainFilter()throws Exception{
        boolean trusted=AndroidAutoCompatibilityManager.trustedHost(c);
        if(!trusted)assertTrue(AndroidAutoCompatibilityManager.requestedExclusions(c).isEmpty());
        c.sendBroadcast(new Intent("androidx.car.app.connection.action.CAR_CONNECTION_UPDATED").putExtra("CarConnectionState",2));
        SystemClock.sleep(1500);assertFalse("Broadcast must not manufacture projection",AndroidAutoCompatibilityManager.projectionConnected);active();blocked();
        open();Activity previous=screen;ui(screen::recreate);SystemClock.sleep(1500);
        ui(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof AndroidAutoSettingsActivity&&a!=previous)screen=a;});
        assertNotSame(previous,screen);active();blocked();
        android.util.Log.i("AndroidAutoDeviceTest","No car attached: host trusted="+trusted+"; spoof broadcast retained filter");
    }
    @Test public void protectedSettingsRequireSecretAndPreserveVpn()throws Exception{
        String secret=BuildConfig.STRONG?"custodian-123":"123456";
        if(BuildConfig.STRONG)new Guardian(c).setup(secret.toCharArray());
        else {Class<?> crypto=Class.forName("it.gameshield.NormalPinCrypto");String hash=(String)crypto.getDeclaredMethod("create",char[].class).invoke(null,(Object)secret.toCharArray());c.getSharedPreferences("normal_disable_pin",0).edit().putString("hash",hash).commit();}
        boolean original=AndroidAutoCompatibilityManager.enabled(c);open();change();AlertDialog auth=dialog();assertTrue(auth.isShowing());
        ui(()->{input(auth.getWindow().getDecorView()).setText("wrong000");auth.getButton(-1).performClick();});SystemClock.sleep(2500);
        assertSame(auth,dialog());assertTrue(auth.isShowing());assertEquals(original,AndroidAutoCompatibilityManager.enabled(c));active();blocked();
        ui(()->{input(auth.getWindow().getDecorView()).setText(secret);auth.getButton(-1).performClick();});
        long until=SystemClock.elapsedRealtime()+15000;while(dialog()==auth&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        AlertDialog finalConfirmation=dialog();assertNotSame(auth,finalConfirmation);ui(()->finalConfirmation.getButton(-2).performClick());assertEquals(original,AndroidAutoCompatibilityManager.enabled(c));active();
        change();AlertDialog again=dialog();ui(()->{input(again.getWindow().getDecorView()).setText(secret);again.getButton(-1).performClick();});until=SystemClock.elapsedRealtime()+15000;while(dialog()==again&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        AlertDialog accepted=dialog();assertNotSame(again,accepted);ui(()->accepted.getButton(-1).performClick());SystemClock.sleep(2500);
        assertEquals(!original,AndroidAutoCompatibilityManager.enabled(c));active();blocked();
    }
}
