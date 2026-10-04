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
        countdown.setText(left>0?"Secondi rimanenti: "+left:"Attesa completata");
        boolean ready=left==0;
        codeLabel.setText(ready?"Riscrivi questo codice: "+session.code:"Il codice sarà mostrato al termine dei 60 secondi.");
        code.setEnabled(ready&&!busy);pin.setEnabled(ready&&!busy);proceed.setEnabled(ready&&!busy);
        handler.postDelayed(this,250);
    }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(BuildConfig.STRONG || !ShieldVpnService.running){finish();return;}
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
        countdown=label("",22); codeLabel=label("",18); code=field("Codice di 6 cifre",false);
        boolean required=new NormalPinStore(this).configured();
        pin=field("PIN per disattivazione",true);pin.setVisibility(required?android.view.View.VISIBLE:android.view.View.GONE);
        if(required)label("Inserisci anche il tuo PIN. Se lo hai dimenticato, puoi sempre rimuovere la VPN dalle impostazioni Android o disinstallare l’app.",14);
        error=label("",15);proceed=button("Continua",this::verify);
        button("Annulla e mantieni protezione",this::cancel);
    }
    private void verify() {
        if(busy || interrupted || session.remaining(SystemClock.elapsedRealtime())!=0)return;
        String entered=code.getText().toString();
        if(!session.code.equals(entered)){error.setText("Codice errato. Riscrivi le 6 cifre mostrate.");return;}
        NormalPinStore store=new NormalPinStore(this);String hash=store.hash();
        if(hash==null){verifiedHash=null;finalConfirmation(entered,false,true);return;}
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
        if(interrupted || !session.verify(SystemClock.elapsedRealtime(),entered,pinRequired,pinCorrect))return;
        if(confirmation!=null && confirmation.isShowing())return;
        confirmation=new AlertDialog.Builder(this).setTitle("Confermi di voler disattivare la protezione?")
            .setNegativeButton("Mantieni protezione",(d,w)->cancel())
            .setPositiveButton("Disattiva protezione",(d,w)->{
                if(interrupted || !java.util.Objects.equals(verifiedHash,new NormalPinStore(this).hash())){error.setText("Il PIN è cambiato. Ripeti la verifica.");return;}
                if(session.confirm(true)){
                    interrupted=true;
                    getSharedPreferences("shield",0).edit().putBoolean("wanted",false).apply();
                    startService(new Intent(this,ShieldVpnService.class).setAction("STOP"));finish();
                }
            }).setOnCancelListener(d->cancel()).show();
    }
    @Override protected void onDestroy(){if(confirmation!=null)confirmation.dismiss();super.onDestroy();}
    private void cancel(){interrupted=true;session.cancel();finish();}
    @Override public void onBackPressed(){cancel();}
    @Override protected void onResume(){super.onResume();if(session!=null&&!interrupted)handler.post(tick);}
    @Override protected void onStop(){handler.removeCallbacks(tick);if(session!=null&&!isChangingConfigurations()){cancel();}super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){
        if(session!=null){out.putString("process",PROCESS);out.putLong("deadline",session.deadline);out.putString("code",session.code);out.putBoolean("interrupted",interrupted);}
        // No code input, PIN input or verified state survives Activity recreation.
        super.onSaveInstanceState(out);
    }
}
