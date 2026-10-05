package it.gameshield;
import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.Arrays;
import java.util.concurrent.*;
/** No pause button. Strong changes require the custodian; Normal respects its optional PIN. */
public final class AndroidAutoSettingsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView state;
    private AlertDialog dialog;
    private boolean busy;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(0xff072d36);getWindow().setNavigationBarColor(0xff072d36);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff1f7f7);root.setPadding(dp(22),dp(28),dp(22),dp(32));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(22),dp(28)+i.getSystemWindowInsetTop(),dp(22),dp(32)+i.getSystemWindowInsetBottom());return i;});
        label(root,"Compatibilità Android Auto",25);state=label(root,"",20);
        label(root,"Permette ad Android Auto di funzionare correttamente mantenendo GameShield attivo quando possibile.",17);
        label(root,"Solo Android Auto ufficiale usa la rete direttamente. Browser, Maps, musica e altre app restano nel filtro. La VPN non viene sospesa; nessun bypass generale o esclusione Google Play Services. Alcuni dispositivi potrebbero comunque rifiutare una VPN attiva: il collegamento con l’auto va verificato.",15);
        if(BuildConfig.STRONG)label(root,"In Strong questa scelta richiede il codice custode. Lockdown e protezioni Device Owner restano attivi.",15);
        button(root,"Modifica compatibilità",this::requestChange);button(root,"Torna indietro",this::finish);render();
    }
    private void render(){state.setText(AndroidAutoCompatibilityManager.enabled(this)?"Compatibilità attiva":"Compatibilità disattivata");}
    private void requestChange(){
        if(busy)return;
        if(!AndroidAutoAuthorization.available(this)){toast("Prima imposta il codice custode dalle impostazioni Strong");return;}
        if(!AndroidAutoAuthorization.required(this)){confirm();return;}
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint(AndroidAutoAuthorization.title());input.setInputType(BuildConfig.STRONG?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        TextView error=new TextView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(8));box.addView(input);box.addView(error);
        dialog=new AlertDialog.Builder(this).setTitle(AndroidAutoAuthorization.title()).setView(box).setNegativeButton("Annulla",null).setPositiveButton("Verifica",null).create();
        AlertDialog current=dialog;current.setOnDismissListener(d->input.setText(""));current.setOnShowListener(d->{current.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);current.getButton(-1).setOnClickListener(v->{
            if(busy)return;char[] secret=input.getText().toString().toCharArray();input.setText("");busy=true;current.getButton(-1).setEnabled(false);
            worker.execute(()->{boolean ok=false;try{ok=AndroidAutoAuthorization.authenticate(this,secret);}catch(Exception e){/* No secrets or raw exception details in logs. */}finally{Arrays.fill(secret,'\0');}
                boolean authenticated=ok;main.post(()->{busy=false;if(isFinishing()||isDestroyed()||!current.isShowing())return;current.getButton(-1).setEnabled(true);if(!authenticated){error.setText("Verifica non riuscita. L’impostazione resta invariata.");return;}current.dismiss();confirm();});});
        });});current.show();
    }
    private void confirm(){
        boolean next=!AndroidAutoCompatibilityManager.enabled(this);
        dialog=new AlertDialog.Builder(this).setTitle(next?"Attiva compatibilità Android Auto?":"Disattiva compatibilità Android Auto?")
            .setMessage("La protezione del resto del telefono resta attiva. Non viene disattivata la VPN. Il cambio delle regole di instradamento può richiedere un breve ristabilimento del tunnel.")
            .setNegativeButton("Annulla",null).setPositiveButton("Conferma",(d,w)->{
                android.content.SharedPreferences p=getSharedPreferences("android_auto",0);boolean previous=AndroidAutoCompatibilityManager.enabled(this);
                if(!p.edit().putBoolean("enabled",next).commit()){toast("Impostazione non salvata");return;}
                try{
                    new StrongPolicy(this).syncAndroidAutoExceptions(AndroidAutoCompatibilityManager.requestedExclusions(this));
                    if(ShieldVpnService.running)startForegroundService(new Intent(this,ShieldVpnService.class).setAction("RECONFIGURE_AUTO"));render();
                }catch(Exception error){p.edit().putBoolean("enabled",previous).commit();
                    try { new StrongPolicy(this).syncAndroidAutoExceptions(AndroidAutoCompatibilityManager.requestedExclusions(this)); }
                    catch(Exception pending) { android.util.Log.w("AndroidAuto","compatibility policy rollback pending"); }
                    toast("Impostazione non applicata. Protezioni mantenute.");render();}
            }).show();
    }
    @Override protected void onStop(){if(dialog!=null)dialog.dismiss();super.onStop();}
    @Override protected void onDestroy(){if(dialog!=null)dialog.dismiss();main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    private TextView label(LinearLayout root,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextColor(0xff072d36);v.setTextSize(size);v.setPadding(0,dp(8),0,dp(8));root.addView(v);return v;}
    private void button(LinearLayout root,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(0xff087f8c);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
