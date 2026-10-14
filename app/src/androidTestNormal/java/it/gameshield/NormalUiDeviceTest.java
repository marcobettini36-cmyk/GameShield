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
    // Start a fresh task so startActivitySync observes onCreate rather than task reuse.
    // This removes Activities only; saved preferences must survive and are asserted below.
    private Activity open(Class<? extends Activity> type){screen=i.startActivitySync(new Intent(c,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));i.waitForIdleSync();return screen;}
    private Button find(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){Button b=find(g.getChildAt(n),text);if(b!=null)return b;}}return null;}
    private void click(String text){i.runOnMainSync(()->{Button b=find(screen.getWindow().getDecorView(),text);assertNotNull(text,b);b.performClick();});i.waitForIdleSync();}
    private String text(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText());if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)s.append('\n').append(text(((ViewGroup)v).getChildAt(n)));return s.toString();}
    private String texts(){AtomicReference<String>s=new AtomicReference<>();i.runOnMainSync(()->s.set(text(screen.getWindow().getDecorView())));return s.get();}
    private void screenshot(String name)throws Exception{SystemClock.sleep(4500);android.graphics.Bitmap b=i.getUiAutomation().takeScreenshot();assertNotNull(b);File dir=new File(c.getExternalFilesDir(null),"screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream f=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f));}finally{b.recycle();}}
    @Test public void dashboardAndNavigationScreens()throws Exception{
        open(NormalHomeActivity.class);assertTrue(texts().contains("DISATTIVATA"));assertTrue(texts().contains("Da verificare"));screenshot("01-home");screen.finish();
        open(NormalSetupActivity.class);assertTrue(texts().contains("Passaggio 1 di 8"));screenshot("02-configurazione");screen.finish();
        open(NormalDnsActivity.class);assertTrue(texts().contains("Passaggio 1 di 6"));assertFalse(texts().contains("clean.dnsforge.de"));screenshot("03-dns-selettivo");screen.finish();
        open(NormalHelpActivity.class);assertTrue(texts().contains("Se Internet smette"));screenshot("04-assistenza");screen.finish();
        open(NormalSettingsActivity.class);screenshot("05-impostazioni");screen.finish();open(NormalLayersActivity.class);screenshot("06-protezione-autonoma");
    }
    @Test public void wizardResumeAndRecreation()throws Exception{open(NormalSetupActivity.class);for(int n=0;n<4;n++)click("Passaggio successivo / salta");assertTrue(texts().contains("Passaggio 5 di 8"));
        Activity old=screen;i.runOnMainSync(()->old.recreate());long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){AtomicReference<Activity> a=new AtomicReference<>();i.runOnMainSync(()->{for(Activity current:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(current instanceof NormalSetupActivity&&current!=old)a.set(current);});if(a.get()!=null){screen=a.get();break;}SystemClock.sleep(100);}assertNotSame(old,screen);assertTrue(texts().contains("Passaggio 5 di 8"));click("Riprendi più tardi");open(NormalSetupActivity.class);assertTrue(texts().contains("Passaggio 5 di 8"));for(int n=4;n<7;n++)click("Passaggio successivo / salta");assertTrue(texts().contains("Passaggio 8 di 8"));assertTrue(texts().contains("Facoltativo"));}
    @Test public void declaredDnsDoesNotGrantVerifiedState()throws Exception{open(NormalDnsActivity.class);for(int n=0;n<3;n++)click("Continua");i.runOnMainSync(()->{android.widget.LinearLayout r=((NormalScreen)screen).root;for(int n=0;n<r.getChildCount();n++)if(r.getChildAt(n) instanceof EditText){((EditText)r.getChildAt(n)).setText("abcdef.dns.nextdns.io");break;}});click("Salva");assertEquals("abcdef.dns.nextdns.io",NormalSafety.prefs(c).getString("dnsHostname",null));assertFalse(NormalProtectionState.dnsVerified(c));assertFalse(NormalSafety.prefs(c).contains("dnsVerifiedHost"));}
    private void setHostname(String value){i.runOnMainSync(()->{for(int n=0;n<((NormalScreen)screen).root.getChildCount();n++){View v=((NormalScreen)screen).root.getChildAt(n);if(v instanceof EditText){((EditText)v).setText(value);return;}}fail("Hostname field missing");});}
    private void shell(String command)throws Exception{try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(i.getUiAutomation().executeShellCommand(command))){while(stream.read()!=-1){}}}
    @Test public void originalLogoAdaptiveMonochromeAndDensityRendering()throws Exception{
        open(NormalHomeActivity.class);android.graphics.drawable.Drawable icon=c.getPackageManager().getApplicationIcon(c.getPackageName());assertTrue(icon instanceof android.graphics.drawable.AdaptiveIconDrawable);
        android.graphics.drawable.AdaptiveIconDrawable adaptive=(android.graphics.drawable.AdaptiveIconDrawable)icon;assertNotNull(adaptive.getMonochrome());assertEquals(R.mipmap.normal_launcher,c.getApplicationInfo().icon);
        File dir=new File(c.getExternalFilesDir(null),"screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());
        int[] sizes={48,72,96,144,192};String[] densities={"mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"};
        for(int n=0;n<sizes.length;n++){int size=sizes[n];android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);icon.setBounds(0,0,size,size);icon.draw(new android.graphics.Canvas(b));assertEquals(0,android.graphics.Color.alpha(b.getPixel(0,0)));int blue=0,red=0;for(int y=0;y<size;y++)for(int x=0;x<size;x++){int pixel=b.getPixel(x,y);if(android.graphics.Color.blue(pixel)>150&&android.graphics.Color.blue(pixel)>android.graphics.Color.red(pixel)+30)blue++;if(android.graphics.Color.red(pixel)>150&&android.graphics.Color.red(pixel)>android.graphics.Color.green(pixel)+50)red++;}assertTrue("Blue shield absent at "+densities[n],blue>size);assertTrue("Red prohibition absent at "+densities[n],red>size);try(FileOutputStream stream=new FileOutputStream(new File(dir,"logo-"+densities[n]+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}b.recycle();}
        android.graphics.Bitmap mono=android.graphics.Bitmap.createBitmap(192,192,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.drawable.Drawable silhouette=adaptive.getMonochrome();silhouette.setBounds(0,0,192,192);silhouette.draw(new android.graphics.Canvas(mono));assertTrue(android.graphics.Color.alpha(mono.getPixel(96,12))==0);int ink=0;for(int y=0;y<192;y++)for(int x=0;x<192;x++)if(android.graphics.Color.alpha(mono.getPixel(x,y))>200)ink++;assertTrue("Empty monochrome silhouette",ink>192);try(FileOutputStream stream=new FileOutputStream(new File(dir,"logo-monocromatico.png"))){mono.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}mono.recycle();
        shell("input keyevent 3");SystemClock.sleep(500);int w=c.getResources().getDisplayMetrics().widthPixels,h=c.getResources().getDisplayMetrics().heightPixels;shell("input swipe "+(w/2)+" "+(h-35)+" "+(w/2)+" "+(h/4)+" 400");screenshot("launcher-android");open(NormalHomeActivity.class);screenshot("logo-schermata-iniziale");
    }
    @Test public void dnsGuidePersistsDraftValidatesAndOpensSettings()throws Exception{
        open(NormalDnsActivity.class);for(int n=0;n<3;n++)click("Continua");setHostname("https://abcdef.dns.nextdns.io");click("Salva");assertFalse(NormalSafety.prefs(c).contains("dnsHostname"));click("Continua");assertTrue(texts().contains("Passaggio 4 di 6"));
        screen.finish();open(NormalDnsActivity.class);assertTrue(texts().contains("Passaggio 4 di 6"));assertTrue(texts().contains("https://abcdef.dns.nextdns.io"));
        i.runOnMainSync(()->c.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("fixture","abcdef.dns.nextdns.io")));click("Incolla");click("Salva");assertEquals("abcdef.dns.nextdns.io",NormalSafety.prefs(c).getString("dnsHostname",null));click("Copia indirizzo DNS");
        assertEquals("abcdef.dns.nextdns.io",c.getSystemService(ClipboardManager.class).getPrimaryClip().getItemAt(0).getText().toString());click("Continua");assertTrue(texts().contains("Passaggio 5 di 6"));
        assertEquals(android.provider.Settings.ACTION_WIRELESS_SETTINGS,NormalDnsActivity.dnsFallbackIntent().getAction());click("Apri impostazioni DNS privato");SystemClock.sleep(1000);assertEquals("com.android.settings",i.getUiAutomation().getRootInActiveWindow().getPackageName().toString());i.runOnMainSync(()->screen.finish());open(NormalDnsActivity.class);assertTrue(texts().contains("Passaggio 5 di 6"));assertFalse(NormalSafety.prefs(c).contains("dnsVerifiedHost"));click("Continua");assertTrue(texts().contains("Passaggio 6 di 6"));assertTrue(texts().contains("non spegne la VPN"));click("Torna indietro");assertTrue(texts().contains("Passaggio 5 di 6"));
    }
    @Test public void dnsGuideRecreationAndAllIllustrations()throws Exception{
        open(NormalDnsActivity.class);for(int n=0;n<6;n++){assertTrue(texts().contains("Passaggio "+(n+1)+" di 6"));assertTrue(texts().contains("ILLUSTRAZIONE DIMOSTRATIVA"));if(n==1)assertTrue(texts().contains("Lascia Pornografia"));if(n==3){setHostname("abcdef.dns.nextdns.io");click("Salva");}screenshot("dns-passaggio-"+(n+1));i.runOnMainSync(()->{LinearLayout r=((NormalScreen)screen).root;for(int k=0;k<r.getChildCount();k++)if(r.getChildAt(k) instanceof NormalDnsDiagram)((ScrollView)r.getParent()).scrollTo(0,Math.max(0,r.getChildAt(k).getTop()-24));});screenshot("dns-passaggio-"+(n+1)+"-illustrazione");if(n<5)click("Continua");}
        Activity old=screen;i.runOnMainSync(()->old.recreate());long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){AtomicReference<Activity>a=new AtomicReference<>();i.runOnMainSync(()->{for(Activity current:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(current instanceof NormalDnsActivity&&current!=old)a.set(current);});if(a.get()!=null){screen=a.get();break;}SystemClock.sleep(100);}assertNotSame(old,screen);assertTrue(texts().contains("Passaggio 6 di 6"));assertFalse(NormalSafety.prefs(c).contains("dnsVerifiedHost"));click("Termina guida");
    }
    @Test public void smallScreenAndLargeText()throws Exception{if("true".equals(InstrumentationRegistry.getArguments().getString("largeText"))){assertTrue(c.getResources().getConfiguration().fontScale>=1.49f);assertTrue(c.getResources().getConfiguration().screenWidthDp<=360);}open(NormalHelpActivity.class);assertTrue(texts().contains("Procedura di emergenza"));screenshot("07-schermo-piccolo-caratteri-grandi");screen.finish();open(NormalHomeActivity.class);assertTrue(texts().contains("Configura protezione"));screenshot("08-home-caratteri-grandi");}
    @Test public void firstLaunchOffersResumableSetup()throws Exception{
        NormalSafety.prefs(c).edit().remove("setupSeen060").commit();Activity home=open(NormalHomeActivity.class);
        long end=SystemClock.elapsedRealtime()+10000;AtomicReference<Activity> wizard=new AtomicReference<>();
        while(SystemClock.elapsedRealtime()<end){i.runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof NormalSetupActivity)wizard.set(a);});if(wizard.get()!=null)break;SystemClock.sleep(100);}
        assertNotNull("First launch did not open configuration",wizard.get());screen=wizard.get();assertTrue(texts().contains("Passaggio 1 di 8"));click("Riprendi più tardi");screen=home;assertTrue(NormalSafety.prefs(c).getBoolean("setupSeen060",false));assertFalse(NormalSafety.protectedNow(c));
    }
}
