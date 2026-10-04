package it.gameshield;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import javax.net.ssl.HttpsURLConnection;
/** Real UI, actual 60-second waits, actual running TUN. No deadline injection. */
public class NormalDisableDeviceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Context c;
    private ConnectivityManager cm;
    private Activity screen;
    @Before public void before()throws Exception{
        c=inst.getTargetContext();cm=c.getSystemService(ConnectivityManager.class);
        stop();c.getSharedPreferences("normal_disable_pin",0).edit().clear().commit();
        assertNull(VpnService.prepare(c));c.startForegroundService(new Intent(c,ShieldVpnService.class));
        long until=SystemClock.elapsedRealtime()+180000;
        while((!ShieldVpnService.running||vpn()==null)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);
        assertTrue(ShieldVpnService.running);assertNotNull(vpn());
        SystemClock.sleep(2500);https();
    }
    @After public void after()throws Exception{if(screen!=null)ui(()->screen.finish());stop();c.getSharedPreferences("normal_disable_pin",0).edit().clear().commit();}
    private void stop(){c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));for(int i=0;i<100&&vpn()!=null;i++)SystemClock.sleep(100);}
    private Network vpn(){for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return n;}return null;}
    private void active(){assertTrue("Protection accidentally stopped",ShieldVpnService.running);assertNotNull(vpn());}
    private void https()throws Exception{Network n=vpn();assertNotNull(n);HttpsURLConnection h=(HttpsURLConnection)n.openConnection(new java.net.URL("https://www.google.it/"));h.setConnectTimeout(10000);h.setReadTimeout(10000);try{assertTrue(h.getResponseCode()>0);}finally{h.disconnect();}}
    private void ui(Runnable r){inst.runOnMainSync(r);inst.waitForIdleSync();}
    private void open(){screen=inst.startActivitySync(new Intent(c,NormalDisableActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();active();}
    private Object field(String name)throws Exception{Field f=NormalDisableActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private DisableSession session()throws Exception{return (DisableSession)field("session");}
    private void click(String name){AtomicReference<Button>b=new AtomicReference<>();ui(()->b.set(find(screen.getWindow().getDecorView(),name)));assertNotNull(name,b.get());ui(()->b.get().performClick());}
    private Button find(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=find(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
    private void enter(String name,String value)throws Exception{EditText e=(EditText)field(name);ui(()->e.setText(value));}
    private void waitCountdown()throws Exception{
        long deadline=SystemClock.elapsedRealtime()+75000;
        Button proceed=(Button)field("proceed");
        while(SystemClock.elapsedRealtime()<deadline){AtomicReference<Boolean>enabled=new AtomicReference<>();ui(()->enabled.set(proceed.isEnabled()));active();if(enabled.get())return;SystemClock.sleep(250);}
        fail("Countdown did not complete");
    }
    private AlertDialog finalDialog()throws Exception{long until=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<until){AlertDialog d=(AlertDialog)field("confirmation");if(d!=null&&d.isShowing())return d;SystemClock.sleep(100);}fail("Final confirmation missing");return null;}
    private void recreate()throws Exception{
        Activity old=screen;ui(old::recreate);awaitScreen(old);
    }
    private void awaitScreen(Activity old)throws Exception{
        long until=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<until){AtomicReference<Activity>a=new AtomicReference<>();ui(()->{for(Activity current:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(current instanceof NormalDisableActivity&&current!=old)a.set(current);});if(a.get()!=null){screen=a.get();return;}SystemClock.sleep(100);}fail("Activity recreation failed");
    }
    @Test public void countdownCancellationRecreationAndFinalConfirmation()throws Exception{
        screen=inst.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        Activity main=screen;click("Disattiva protezione");awaitScreen(main);DisableSession first=session();assertTrue(first.remaining(SystemClock.elapsedRealtime())>=59);
        Button proceed=(Button)field("proceed");assertFalse(proceed.isEnabled());click("Continua");active();
        click("Annulla e mantieni protezione");active();open();assertNotEquals(first.code,session().code);
        long deadline=session().deadline;String code=session().code;
        SystemClock.sleep(1200);recreate();assertEquals(deadline,session().deadline);assertEquals(code,session().code);assertFalse(((Button)field("proceed")).isEnabled());active();
        // Leaving the screen cancels the pending request. Reopening starts all 60 seconds again.
        inst.getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME").close();SystemClock.sleep(1500);active();open();assertTrue(session().remaining(SystemClock.elapsedRealtime())>=59);assertNotEquals(code,session().code);
        https();waitCountdown();https();active();
        enter("code","wrong");click("Continua");assertNull(field("confirmation"));active();
        enter("code",session().code);click("Continua");AlertDialog negative=finalDialog();ui(()->negative.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());active();
        open();assertFalse(((Button)field("proceed")).isEnabled());waitCountdown();enter("code",session().code);click("Continua");finalDialog();
        // Recreation must discard final authorization and require an active code check again.
        recreate();assertNull(field("confirmation"));active();enter("code",session().code);click("Continua");AlertDialog positive=finalDialog();
        ui(()->positive.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        for(int i=0;i<100&&vpn()!=null;i++)SystemClock.sleep(100);
        assertFalse(ShieldVpnService.running);assertNull(vpn());assertFalse(c.getSharedPreferences("shield",0).getBoolean("wanted",true));
        HttpsURLConnection h=(HttpsURLConnection)new java.net.URL("https://www.google.it/").openConnection();h.setConnectTimeout(10000);h.setReadTimeout(10000);try{assertTrue(h.getResponseCode()>0);}finally{h.disconnect();}
    }
    @Test public void optionalPinAndSettingsCurrentPinVerification()throws Exception{
        NormalPinStore store=new NormalPinStore(c);
        screen=inst.startActivitySync(new Intent(c,NormalSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        settingsAttempt("Imposta / modifica PIN",null,"123456","123456",true);assertTrue(store.verify("123456".toCharArray()));click("Torna indietro");
        open();waitCountdown();enter("code",session().code);enter("pin","654321");click("Continua");SystemClock.sleep(2000);assertNull(field("confirmation"));active();
        enter("pin","123456");click("Continua");AlertDialog d=finalDialog();ui(()->d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());active();
        screen=inst.startActivitySync(new Intent(c,NormalSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        settingsAttempt("Imposta / modifica PIN","000000","234567","234567",false);assertTrue(store.verify("123456".toCharArray()));
        settingsAttempt("Imposta / modifica PIN","123456","234567","234567",true);assertTrue(store.verify("234567".toCharArray()));assertFalse(store.verify("123456".toCharArray()));
        settingsAttempt("Rimuovi PIN","000000",null,null,false);assertTrue(store.configured());
        settingsAttempt("Rimuovi PIN","234567",null,null,true);assertFalse(store.configured());active();
        click("Torna indietro");open();assertEquals(View.GONE,((EditText)field("pin")).getVisibility());assertTrue(session().remaining(SystemClock.elapsedRealtime())>=59);click("Annulla e mantieni protezione");active();https();
    }
    private void settingsAttempt(String button,String old,String next,String repeat,boolean success)throws Exception{
        click(button);Field f=NormalSettingsActivity.class.getDeclaredField("activeDialog");f.setAccessible(true);AlertDialog d=(AlertDialog)f.get(screen);
        ui(()->{java.util.List<EditText> inputs=new java.util.ArrayList<>();collect(d.getWindow().getDecorView(),inputs);int offset=old==null?0:1;if(old!=null)inputs.get(0).setText(old);if(next!=null){inputs.get(offset).setText(next);inputs.get(offset+1).setText(repeat);}d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        long until=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<until){AtomicReference<Boolean>done=new AtomicReference<>();ui(()->done.set(!d.isShowing()||d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()));if(done.get())break;SystemClock.sleep(100);}
        assertEquals(success,!d.isShowing());if(d.isShowing())ui(()->d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
    }
    private void collect(View v,java.util.List<EditText> inputs){if(v instanceof EditText)inputs.add((EditText)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),inputs);}}
}
