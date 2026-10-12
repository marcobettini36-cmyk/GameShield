package it.gameshield;
import android.Manifest;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.view.WindowManager;
import android.widget.*;
import java.util.Arrays;

public final class NormalResilienceActivity extends NormalScreen {
    private TextView state;
    private long authorizedUntil;
    private boolean busy;
    private final Runnable refresh=new Runnable(){public void run(){state.setText(NormalSafety.status(NormalResilienceActivity.this));handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);layout("Protezione avanzata Normal");
        state=label("",17);
        label("1. VPN sempre attiva e blocco senza VPN",21);
        label("Attiva prima la protezione dalla schermata principale. Apri VPN Android, premi l'ingranaggio accanto a GameShield, abilita 'VPN sempre attiva' e, se desideri, 'Blocca connessioni senza VPN'. Quest'ultima scelta può interrompere Internet in caso di guasto: il rilascio d'emergenza è sempre accessibile.",15);
        button("Configura VPN nelle impostazioni Android",()->new AlertDialog.Builder(this).setTitle("Scelta nelle impostazioni Android").setMessage("GameShield non attiva queste opzioni da solo. Il blocco senza VPN blocca Internet quando il filtro è fermo. Conserva le istruzioni di emergenza.")
            .setNegativeButton("Annulla",null).setPositiveButton("Apri VPN Android",(d,w)->open(Settings.ACTION_VPN_SETTINGS)).show());
        label("2. Ripristino e batteria",21);
        button("Abilita / riabilita ripristino automatico",()->guard(()->new AlertDialog.Builder(this).setTitle("Consenso alla supervisione")
            .setMessage("Una notifica permanente mostrerà lo stato della VPN. Dopo una disconnessione GameShield tenta un ripristino solo con consenso VPN valido, rete disponibile e limiti Android rispettati. Tre fallimenti sospendono i tentativi. Un rilascio volontario disabilita il ripristino.")
            .setNegativeButton("Annulla",null).setPositiveButton("Abilita",(d,w)->{NormalSafety.prefs(this).edit().putBoolean("recovery",true).putBoolean("paused",false).putBoolean("recoveryBlocked",false).putInt("retries",0).putLong("recoverAfter",0).commit();NormalSafety.startMonitor(this);}).show()));
        label("Android può ritardare lavori in background durante Doze o risparmio energetico. Su Samsung verifica anche le app in sospensione: la scelta resta tua.",15);
        button("Apri ottimizzazione batteria",()->open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        label("3. Amministratore volontario",21);
        label("L'amministratore aggiunge, dove supportato, il passaggio Android di disattivazione prima della disinstallazione. Non impone l'attesa di 24 ore al sistema, non blocca impostazioni, schermo o ripristino e non configura Device Owner.",15);
        button("Attiva Device Administrator volontariamente",()->guard(this::admin));
        label("4. Persona fidata e attesa di 24 ore",21);
        label("La disattivazione interna richiede 24 ore, codice casuale e conferma finale. La persona fidata può scegliere un codice distinto per autorizzare il rilascio anticipato. Conservalo fuori dal telefono. Nessun PIN viene salvato in chiaro.",15);
        button("Imposta / modifica codice persona fidata",()->guard(()->trusted(false)));
        button("Rimuovi codice persona fidata",()->guard(()->trusted(true)));
        if(BuildConfig.AUTOMATIC_SMS_ENABLED) {
            label("5. Avvisi SMS facoltativi",21);
            label("Numero inserito manualmente, senza accesso a rubrica o SMS ricevuti. Gli avvisi possono costare e richiedere più segmenti SMS. Nessun invio senza consenso e SEND_SMS. La disconnessione deve durare almeno 15 minuti; riconnettere la VPN annulla il timer. Ritardi, arresto forzato e disinstallazione possono impedire gli avvisi.",15);
            button("Configura numero persona fidata",()->guard(this::number));
            button("Usa la SIM SMS predefinita di Android",()->guard(this::sim));
            button("Consenti SMS automatici",()->guard(this::smsConsent));
            button("Revoca consenso SMS",()->{NormalSafety.prefs(this).edit().putBoolean("smsConsent",false).commit();NormalJobs.schedule(this);message("Consenso revocato: nessun nuovo SMS automatico. Un SMS già affidato all'operatore non può essere richiamato.");});
            button("Invia SMS di prova",()->guard(()->new AlertDialog.Builder(this).setTitle("Invio di prova alla persona fidata")
                .setMessage("Inviare un SMS di prova al numero configurato? È un invio reale e può comportare costi.").setNegativeButton("Annulla",null)
                .setPositiveButton("Invia prova",(d,w)->{worker.execute(()->{NormalSms.send(this,true);deliver(()->message(NormalSafety.prefs(this).getString("testIssue","Invio richiesto")));});}).show()));
        }else {
            label("Avvisi SMS automatici non disponibili nell'edizione Play",21);
            label("Questa build esclude SEND_SMS e l'invio automatico. L'APK per distribuzione diretta offre la funzione previo consenso; un'eventuale pubblicazione con SEND_SMS richiederebbe una specifica approvazione Google Play.",15);
        }
        button("Richiedi disattivazione Normal (24 ore)",()->startActivity(new Intent(this,NormalDisableActivity.class)));
        button("Procedura di emergenza",()->startActivity(new Intent(this,NormalEmergencyActivity.class)));
        button("Torna indietro",this::finish);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},73);
    }
    private void open(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException e){message("Pagina non disponibile. Apri manualmente le impostazioni Android.");}}
    private void guard(Runnable action) {
        if(busy)return;String hash=NormalSafety.prefs(this).getString("trustedHash",null);
        if(hash==null || SystemClock.elapsedRealtime()<authorizedUntil){action.run();return;}
        EditText input=secret("Codice persona fidata");
        new AlertDialog.Builder(this).setTitle("Verifica persona fidata").setView(input).setNegativeButton("Annulla",null).setPositiveButton("Verifica",(d,w)->{
            char[] code=input.getText().toString().toCharArray();input.setText("");busy=true;
            worker.execute(()->{boolean ok=NormalTrusted.verify(this,code,hash);Arrays.fill(code,'\0');deliver(()->{busy=false;if(ok && java.util.Objects.equals(hash,NormalSafety.prefs(this).getString("trustedHash",null))){authorizedUntil=SystemClock.elapsedRealtime()+60_000;action.run();}else message("Codice errato o attesa di sicurezza attiva.");});});
        }).show();
    }
    private EditText secret(String hint){EditText e=new EditText(this);e.setHint(hint);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});e.setSaveEnabled(false);e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);return e;}
    private void trusted(boolean remove) {
        String expected=NormalSafety.prefs(this).getString("trustedHash",null);
        if(remove){if(NormalTrusted.replace(this,expected,null)){authorizedUntil=0;message("Codice persona fidata rimosso.");}return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText first=secret("Nuovo codice (6–12 cifre)"),repeat=secret("Ripeti codice");box.addView(first);box.addView(repeat);
        new AlertDialog.Builder(this).setTitle("Codice scelto dalla persona fidata").setView(box).setNegativeButton("Annulla",null).setPositiveButton("Salva",(d,w)->{
            char[] one=first.getText().toString().toCharArray(),two=repeat.getText().toString().toCharArray();first.setText("");repeat.setText("");
            if(!NormalPinCrypto.valid(one)||!Arrays.equals(one,two)){Arrays.fill(one,'\0');Arrays.fill(two,'\0');message("Usa 6–12 cifre uguali in entrambi i campi.");return;}
            busy=true;worker.execute(()->{boolean ok=false;try{String hash=NormalPinCrypto.create(one);ok=NormalTrusted.replace(this,expected,hash);}catch(java.security.GeneralSecurityException ignored){}finally{Arrays.fill(one,'\0');Arrays.fill(two,'\0');}boolean saved=ok;deliver(()->{busy=false;authorizedUntil=0;message(saved?"Codice salvato. Nessun codice in chiaro memorizzato.":"Codice non salvato: ripeti la verifica.");});});
        }).show();
    }
    private void admin() {
        DevicePolicyManager dpm=getSystemService(DevicePolicyManager.class);ComponentName component=new ComponentName(this,NormalAdminReceiver.class);
        if(dpm==null){message("Device Administrator non supportato.");return;}
        if(dpm.isAdminActive(component)){message("Amministratore Normal già attivo. Puoi sempre disattivarlo dalle impostazioni Android.");return;}
        Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,component)
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Attivazione volontaria: aggiunge il passaggio Android di disattivazione prima della disinstallazione. Nessuna restrizione alle impostazioni, nessun Device Owner.");
        try{startActivity(i);}catch(ActivityNotFoundException|SecurityException e){message("Amministrazione non supportata o non consentita dal dispositivo.");}
    }
    private void number() {
        EditText input=new EditText(this);input.setHint("Numero, preferibilmente con + prefisso paese");input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);input.setText(NormalSafety.prefs(this).getString("number",""));input.setSaveEnabled(false);
        new AlertDialog.Builder(this).setTitle("Numero persona fidata").setView(input).setNegativeButton("Annulla",null).setPositiveButton("Salva",(d,w)->{
            String n=input.getText().toString().replaceAll("[\\s()-]","");input.setText("");if(!NormalSms.validNumber(n)){message("Usa 7–15 cifre con prefisso paese facoltativo.");return;}
            NormalSafety.prefs(this).edit().putString("number",n).putBoolean("smsConsent",false).commit();message("Numero salvato. Rinnova il consenso SMS per questo destinatario.");
        }).show();
    }
    private void sim() {
        int sid=SubscriptionManager.getDefaultSmsSubscriptionId();
        if(!SubscriptionManager.isValidSubscriptionId(sid)){message("Nessuna SIM SMS predefinita. Su dual SIM scegli prima una SIM nelle impostazioni Android.");open(Settings.ACTION_WIRELESS_SETTINGS);return;}
        NormalSafety.prefs(this).edit().putInt("sim",sid).commit();message("SIM SMS predefinita riconfermata. Un cambio SIM richiederà nuova conferma.");
    }
    private void smsConsent() {
        if(!NormalSms.validNumber(NormalSafety.prefs(this).getString("number",""))){message("Configura prima il numero della persona fidata.");return;}
        new AlertDialog.Builder(this).setTitle("Consenso esplicito agli SMS automatici")
            .setMessage("Se il filtro resta disattivato per almeno 15 minuti, GameShield invierà automaticamente l'avviso alla persona fidata configurata, anche dopo una disattivazione volontaria. Il messaggio può costare più segmenti SMS. Consenti l'invio senza ulteriori conferme? Puoi revocare il consenso in qualsiasi momento.")
            .setNegativeButton("Non consentire",null).setPositiveButton("Acconsento",(d,w)->{
                NormalSafety.prefs(this).edit().putBoolean("smsConsent",true).commit();
                if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.SEND_SMS},72);
                else NormalSafety.startMonitor(this);
            }).show();
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] results){super.onRequestPermissionsResult(req,p,results);if(req==72){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED){NormalSafety.startMonitor(this);message("Consenso e permesso registrati. Conferma anche la SIM SMS predefinita.");}else{NormalSafety.prefs(this).edit().putBoolean("smsConsent",false).commit();message("Permesso negato: nessun SMS automatico.");}}}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onStop(){handler.removeCallbacks(refresh);authorizedUntil=0;super.onStop();}
}
