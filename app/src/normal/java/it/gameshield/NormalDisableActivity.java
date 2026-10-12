package it.gameshield;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.*;
import android.view.WindowManager;
import android.widget.*;
import java.util.Arrays;
import java.util.UUID;
/** The only Normal UI path issuing STOP, after explicit final confirmation. */
public final class NormalDisableActivity extends NormalScreen {
    private static final String PROCESS=UUID.randomUUID().toString();
    private DisableSession session;
    private TextView countdown, error, codeLabel;
    private EditText code, pin;
    private Button proceed;
    private boolean busy, interrupted;
    private AlertDialog confirmation;
    private String verifiedHash;
    private final Runnable tick=new Runnable(){public void run(){
        if(interrupted)return;
        int left=session.remaining(SystemClock.elapsedRealtime());
        long wait=NormalSafety.releaseRemaining(NormalDisableActivity.this);
        long seconds=(wait+999)/1000;
        countdown.setText(wait>0?"Attesa di 24 ore: "+(seconds/3600)+" h "+((seconds/60)%60)+" min "+(seconds%60)+" s":left>0?"Secondi rimanenti: "+left:"Attesa completata");
        boolean ready=left==0 && wait==0;
        codeLabel.setText(ready?"Riscrivi questo codice: "+session.code:"Il codice sarà mostrato al termine dell'attesa.");
        code.setEnabled(ready&&!busy);pin.setEnabled(ready&&!busy);proceed.setEnabled(ready&&!busy);
        handler.postDelayed(this,250);
    }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(BuildConfig.STRONG){finish();return;}
        NormalSafety.requestRelease(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(saved!=null && PROCESS.equals(saved.getString("process")) && !saved.getBoolean("interrupted")) {
            session=new DisableSession(saved.getLong("deadline"),saved.getString("code"),true);
        } else {
            android.content.SharedPreferences p=getSharedPreferences("normal_disable_ui",0);
            session=new DisableSession(SystemClock.elapsedRealtime(),p.getString("previous",null));
            p.edit().putString("previous",session.code).apply();
        }
        layout("Disattiva protezione");
        label("Stai per disattivare GameShield.\nDurante la disattivazione i siti bloccati torneranno accessibili.",17);
        label("Questo tempo di attesa serve a evitare disattivazioni impulsive.",15);
        label("L'attesa di 24 ore continua anche chiudendo l'app. La persona fidata può autorizzare un rilascio anticipato. Android mantiene sempre il controllo delle proprie impostazioni.",15);
        button("Sblocco tramite persona fidata",this::trusted);
        button("Procedura di emergenza",()->startActivity(new Intent(this,NormalEmergencyActivity.class)));
        countdown=label("",22); codeLabel=label("",18); code=field("Codice di 6 cifre",false);
        boolean required=new NormalPinStore(this).configured();
        pin=field("PIN per disattivazione",true);pin.setVisibility(required?android.view.View.VISIBLE:android.view.View.GONE);
        if(required)label("Inserisci anche il tuo PIN. Se lo hai dimenticato, puoi sempre rimuovere la VPN dalle impostazioni Android o disinstallare l’app.",14);
        error=label("",15);proceed=button("Continua",this::verify);
        button("Annulla e mantieni protezione",this::cancel);
    }
    private void verify() {
        if(busy || interrupted || session.remaining(SystemClock.elapsedRealtime())!=0 || NormalSafety.releaseRemaining(this)>0)return;
        String entered=code.getText().toString();
        if(!session.code.equals(entered)){error.setText("Codice errato. Riscrivi le 6 cifre mostrate.");return;}
        NormalPinStore store=new NormalPinStore(this);String hash=store.hash();
        if(hash==null || NormalSafety.granted(this)){verifiedHash=hash;finalConfirmation(entered,false,true);return;}
        char[] supplied=pin.getText().toString().toCharArray();pin.setText("");busy=true;proceed.setEnabled(false);
        worker.execute(()->{
            boolean correct=false;
            try{correct=NormalPinCrypto.verify(supplied,hash);}catch(java.security.GeneralSecurityException e){/* No secret logging. */}
            finally{Arrays.fill(supplied,'\0');}
            boolean result=correct;
            deliver(()->{busy=false;
                if(interrupted)return;
                if(!java.util.Objects.equals(hash,store.hash())){error.setText("Il PIN è cambiato. Ripeti la verifica.");return;}
                if(!result){error.setText("PIN errato. La protezione resta attiva.");return;}
                verifiedHash=hash;finalConfirmation(entered,true,true);
            });
        });
    }
    private void finalConfirmation(String entered,boolean pinRequired,boolean pinCorrect){
        if(interrupted || NormalSafety.releaseRemaining(this)>0 || !session.verify(SystemClock.elapsedRealtime(),entered,pinRequired,pinCorrect))return;
        if(confirmation!=null && confirmation.isShowing())return;
        confirmation=new AlertDialog.Builder(this).setTitle("Confermi di voler disattivare la protezione?")
            .setNegativeButton("Mantieni protezione",(d,w)->cancel())
            .setPositiveButton("Disattiva protezione",(d,w)->{
                if(interrupted || NormalSafety.releaseRemaining(this)>0 || !java.util.Objects.equals(verifiedHash,new NormalPinStore(this).hash())){error.setText("Autorizzazione scaduta o PIN cambiato. Ripeti la verifica.");return;}
                if(Boolean.TRUE.equals(NormalMonitorService.alwaysOn())){error.setText("Disattiva prima VPN sempre attiva / blocco senza VPN nelle impostazioni Android.");startActivity(new Intent(android.provider.Settings.ACTION_VPN_SETTINGS));return;}
                if(session.confirm(true)){
                    interrupted=true;
                    if(!NormalSafety.voluntaryStop(this))message("Arresto non eseguito. La VPN può essere ancora attiva: apri le impostazioni Android.");finish();
                }
            }).setOnCancelListener(d->cancel()).show();
    }
    @Override protected void onDestroy(){if(confirmation!=null)confirmation.dismiss();super.onDestroy();}
    private void cancel(){NormalSafety.cancelRelease(this);close();}
    private void close(){interrupted=true;session.cancel();finish();}
    @Override public void onBackPressed(){close();}
    @Override protected void onResume(){super.onResume();if(session!=null&&!interrupted)handler.post(tick);}
    @Override protected void onStop(){handler.removeCallbacks(tick);if(session!=null&&!isChangingConfigurations()){close();}super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){
        if(session!=null){out.putString("process",PROCESS);out.putLong("deadline",session.deadline);out.putString("code",session.code);out.putBoolean("interrupted",interrupted);}
        // No code input, PIN input or verified state survives Activity recreation.
        super.onSaveInstanceState(out);
    }
    private void trusted(){
        String hash=NormalSafety.prefs(this).getString("trustedHash",null);
        if(hash==null){message("Configura prima la persona fidata nelle impostazioni Normal.");return;}
        EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);input.setSaveEnabled(false);input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        new AlertDialog.Builder(this).setTitle("Codice della persona fidata").setView(input).setNegativeButton("Annulla",null).setPositiveButton("Verifica",(d,w)->{
            char[] supplied=input.getText().toString().toCharArray();input.setText("");
            worker.execute(()->{boolean ok=NormalTrusted.release(this,supplied,hash);Arrays.fill(supplied,'\0');deliver(()->{if(ok){message("Rilascio anticipato autorizzato. Ripeti il codice di conferma.");}else message("Codice errato, modificato o attesa di sicurezza attiva.");});});
        }).show();
    }
}
