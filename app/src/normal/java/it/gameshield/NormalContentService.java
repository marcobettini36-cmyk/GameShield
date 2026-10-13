package it.gameshield;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Optional event-based content intervention. Never inspects or blocks system UI. */
public final class NormalContentService extends AccessibilityService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile RuleStore store;private volatile NormalAppCatalog apps;
    private volatile boolean ready;private View overlay;
    private volatile boolean destroyed;
    private static volatile long interventions;
    private String candidate;private long generation, allowSettingsUntil;
    private SharedPreferences.OnSharedPreferenceChangeListener shieldListener,safetyListener;
    private static volatile boolean connected;
    private static volatile boolean loaded;
    static boolean connected(){return connected;}
    static boolean rulesReady(){return loaded;}
    static long interventions(){return interventions;}
    static boolean consent(Context c){return NormalSafety.prefs(c).getBoolean("contentConsent",false);}
    static boolean enabledInSettings(Context c){
        try {String value=android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            ComponentName ours=new ComponentName(c,NormalContentService.class);
            if(value!=null)for(String s:value.split(":"))if(ours.equals(ComponentName.unflattenFromString(s)))return true;
        }catch(RuntimeException ignored){}return false;
    }
    @Override protected void onServiceConnected(){
        connected=true;load();
        shieldListener=(p,k)->{if("custom".equals(k)||"updated".equals(k))load();};
        safetyListener=(p,k)->{if("contentConsent".equals(k))handler.post(()->{generation++;hide();candidate=null;if(consent(this))evaluate();});};
        getSharedPreferences("shield",0).registerOnSharedPreferenceChangeListener(shieldListener);
        NormalSafety.prefs(this).registerOnSharedPreferenceChangeListener(safetyListener);
    }
    private void load(){worker.execute(()->{
        try {RuleStore next=new RuleStore(this);NormalAppCatalog catalog;
            try(InputStream in=getAssets().open("gambling-apps.txt")){catalog=new NormalAppCatalog(new InputStreamReader(in,StandardCharsets.UTF_8));}
            if(destroyed)return;
            store=next;apps=catalog;
            handler.post(()->{if(destroyed)return;ready=true;loaded=true;NormalSafety.prefs(this).edit().remove("contentIssue").apply();generation++;hide();candidate=null;evaluate();});
        }catch(IOException|RuntimeException|OutOfMemoryError e){handler.post(()->{if(destroyed)return;ready=false;loaded=false;store=null;apps=null;hide();NormalSafety.prefs(this).edit().putString("contentIssue","Regole indipendenti non disponibili (lettura o memoria): livello contenuti sospeso, VPN invariata.").apply();});}
    });}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        // Ignore our overlay events to avoid UI/IPC loops.
        if(getPackageName().contentEquals(event.getPackageName()==null?"":event.getPackageName()))return;
        handler.removeCallbacks(evaluateTask);handler.postDelayed(evaluateTask,150);
    }
    private final Runnable evaluateTask=this::evaluate;
    private boolean browser(String pkg){return pkg.equals("com.android.chrome")||pkg.equals("com.chrome.beta")||pkg.equals("com.sec.android.app.sbrowser")||pkg.equals("com.sec.android.app.sbrowser.beta");}
    private String reason(AccessibilityNodeInfo node){
        if(node==null||node.getPackageName()==null)return null;
        String p=node.getPackageName().toString();
        if(apps.blocks(p))return "app";
        // A separate synthetic test app, never a release rule or a system package.
        if(BuildConfig.DEBUG && "it.gameshield.fixture".equals(p))return "app";
        if(!browser(p))return null;
        String[] ids=p.startsWith("com.sec.")?new String[]{p+":id/location_bar",p+":id/url_bar"}:new String[]{p+":id/url_bar"};
        for(String id:ids)for(AccessibilityNodeInfo control:node.findAccessibilityNodeInfosByViewId(id)){
            if(!control.isVisibleToUser())continue;
            CharSequence text=control.getText();String host=NormalVisibleHost.parse(text==null?null:text.toString());
            if(host!=null&&store.rules().blocks(host))return "domain";
        }
        return null;
    }
    private void evaluate(){
        if(!ready || !consent(this) || SystemClock.elapsedRealtime()<allowSettingsUntil){hide();candidate=null;return;}
        AccessibilityNodeInfo node=getRootInActiveWindow();String match;
        try{match=reason(node);}catch(RuntimeException e){hide();candidate=null;return;}
        if(match==null){generation++;hide();candidate=null;return;}
        String pkg=node.getPackageName().toString();String key=pkg+":"+match;
        if(!key.equals(candidate)){candidate=key;show();long token=++generation;
            // One deterministic intervention per foreground candidate; no polling/watchdog loop.
            handler.postDelayed(()->{if(token!=generation || !consent(this))return;AccessibilityNodeInfo current=getRootInActiveWindow();
                try{if(current!=null&&pkg.contentEquals(current.getPackageName())&&reason(current)!=null&&performGlobalAction(GLOBAL_ACTION_HOME))interventions++;}catch(RuntimeException ignored){}
                hide();},250);
        }
    }
    private void show(){
        if(overlay!=null)return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,32,32,32);box.setBackgroundColor(0xff101816);
        TextView text=new TextView(this);text.setText("GameShield Normal\nAccesso al gioco d'azzardo interrotto. Questo livello opera anche senza VPN. Le impostazioni Android restano accessibili.");text.setTextColor(0xffeffff6);text.setTextSize(18);box.addView(text);
        Button settings=new Button(this);settings.setText("Impostazioni / emergenza");settings.setOnClickListener(v->{allowSettingsUntil=SystemClock.elapsedRealtime()+3000;generation++;hide();startActivity(new Intent(this,NormalLayersActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});box.addView(settings);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP;
        try{getSystemService(WindowManager.class).addView(box,p);overlay=box;}catch(RuntimeException ignored){/* HOME intervention still works if overlay is unavailable. */}
    }
    private void hide(){if(overlay!=null){try{getSystemService(WindowManager.class).removeView(overlay);}catch(RuntimeException ignored){}overlay=null;}}
    @Override public void onInterrupt(){generation++;hide();candidate=null;}
    @Override public void onDestroy(){destroyed=true;connected=false;loaded=false;handler.removeCallbacksAndMessages(null);hide();worker.shutdownNow();if(shieldListener!=null)getSharedPreferences("shield",0).unregisterOnSharedPreferenceChangeListener(shieldListener);if(safetyListener!=null)NormalSafety.prefs(this).unregisterOnSharedPreferenceChangeListener(safetyListener);super.onDestroy();}
}
