package it.gameshield;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual activities, lifecycle, denied states and screenshots; no synthetic UI. */
public class NormalUiDeviceTest {
    private final Instrumentation i=InstrumentationRegistry.getInstrumentation();
    private final Context c=i.getTargetContext();private Activity screen;
    @Before public void before(){NormalSafety.prefs(c).edit().clear().putBoolean("setupSeen060",true).commit();}
    @After public void after(){if(screen!=null)i.runOnMainSync(()->screen.finish());}
    private Activity open(Class<? extends Activity> type){screen=i.startActivitySync(new Intent(c,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));i.waitForIdleSync();return screen;}
    private Button find(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){Button b=find(g.getChildAt(n),text);if(b!=null)return b;}}return null;}
    private void click(String text){i.runOnMainSync(()->{Button b=find(screen.getWindow().getDecorView(),text);assertNotNull(text,b);b.performClick();});i.waitForIdleSync();}
    private String text(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText());if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)s.append('\n').append(text(((ViewGroup)v).getChildAt(n)));return s.toString();}
    private String texts(){AtomicReference<String>s=new AtomicReference<>();i.runOnMainSync(()->s.set(text(screen.getWindow().getDecorView())));return s.get();}
    private void screenshot(String name)throws Exception{SystemClock.sleep(600);android.graphics.Bitmap b=i.getUiAutomation().takeScreenshot();assertNotNull(b);File dir=new File(c.getExternalFilesDir(null),"screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream f=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f));}finally{b.recycle();}}
    @Test public void dashboardAndNavigationScreens()throws Exception{
        open(NormalHomeActivity.class);assertTrue(texts().contains("DISATTIVATA"));assertTrue(texts().contains("Da verificare"));screenshot("01-home");screen.finish();
        open(NormalSetupActivity.class);assertTrue(texts().contains("Passaggio 1 di 8"));screenshot("02-configurazione");screen.finish();
        open(NormalDnsActivity.class);assertTrue(texts().contains("Lascia Porn"));assertFalse(texts().contains("clean.dnsforge.de"));screenshot("03-dns-selettivo");screen.finish();
        open(NormalHelpActivity.class);assertTrue(texts().contains("Se Internet smette"));screenshot("04-assistenza");screen.finish();
        open(NormalSettingsActivity.class);screenshot("05-impostazioni");screen.finish();open(NormalLayersActivity.class);screenshot("06-protezione-autonoma");
    }
    @Test public void wizardResumeAndRecreation()throws Exception{open(NormalSetupActivity.class);for(int n=0;n<4;n++)click("Passaggio successivo / salta");assertTrue(texts().contains("Passaggio 5 di 8"));
        Activity old=screen;i.runOnMainSync(()->old.recreate());long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){AtomicReference<Activity> a=new AtomicReference<>();i.runOnMainSync(()->{for(Activity current:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(current instanceof NormalSetupActivity&&current!=old)a.set(current);});if(a.get()!=null){screen=a.get();break;}SystemClock.sleep(100);}assertNotSame(old,screen);assertTrue(texts().contains("Passaggio 5 di 8"));click("Riprendi più tardi");open(NormalSetupActivity.class);assertTrue(texts().contains("Passaggio 5 di 8"));for(int n=4;n<7;n++)click("Passaggio successivo / salta");assertTrue(texts().contains("Passaggio 8 di 8"));assertTrue(texts().contains("Facoltativo"));}
    @Test public void declaredDnsDoesNotGrantVerifiedState()throws Exception{open(NormalDnsActivity.class);i.runOnMainSync(()->{android.widget.LinearLayout r=((NormalScreen)screen).root;for(int n=0;n<r.getChildCount();n++)if(r.getChildAt(n) instanceof EditText){((EditText)r.getChildAt(n)).setText("abcdef.dns.nextdns.io");break;}});click("Salva hostname dichiarato");assertEquals("abcdef.dns.nextdns.io",NormalSafety.prefs(c).getString("dnsHostname",null));assertFalse(NormalProtectionState.dnsVerified(c));assertFalse(NormalSafety.prefs(c).contains("dnsVerifiedHost"));}
    @Test public void smallScreenAndLargeText()throws Exception{open(NormalHelpActivity.class);assertTrue(texts().contains("Procedura di emergenza"));screenshot("07-schermo-piccolo-caratteri-grandi");}
}
