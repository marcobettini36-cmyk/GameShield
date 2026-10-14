package it.gameshield;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public final class NormalEmergencyActivity extends NormalScreen {
    private boolean credentialPending;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);credentialPending=b!=null && b.getBoolean("credentialPending",false);layout("Rilascio di emergenza");
        label("Mantieni sempre il controllo del dispositivo. In caso di guasto puoi interrompere la protezione; i siti bloccati torneranno accessibili.",17);
        label("Se il blocco senza VPN impedisce Internet: apri VPN Android e disattiva 'Blocca connessioni senza VPN' e 'VPN sempre attiva'. Puoi farlo subito, senza aspettare GameShield.",15);
        button("Apri impostazioni VPN",()->open(new Intent(Settings.ACTION_VPN_SETTINGS)));
        label("Per disinstallare: disattiva GameShield nell'elenco degli amministratori del dispositivo, poi apri le informazioni dell'app. Non vengono imposte attese a queste impostazioni Android.",15);
        button("Apri amministratori del dispositivo",()->open(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
        button("Apri informazioni app / disinstallazione",()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button("Autorizza rilascio d'emergenza dall'app",()->new AlertDialog.Builder(this).setTitle("Conferma emergenza")
            .setMessage("Il rilascio anticipato sospende il ripristino automatico dopo la conferma finale. " + (BuildConfig.AUTOMATIC_SMS_ENABLED ? "Gli avvisi SMS già consentiti restano attivi. " : "") + "Verifica l'identità con il blocco schermo, quando configurato; poi completa la breve conferma anti-impulso.")
            .setNegativeButton("Mantieni protezione",null).setPositiveButton("Procedi",(d,w)->credential()).show());
        button("Torna indietro",this::finish);
    }
    private void open(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){message("Pagina non disponibile: apri manualmente le impostazioni Android.");}}
    private void credential() {
        KeyguardManager kg=getSystemService(KeyguardManager.class);
        if(kg!=null && kg.isDeviceSecure()) {
            Intent verify=kg.createConfirmDeviceCredentialIntent("GameShield: rilascio di emergenza","Conferma il controllo del dispositivo.");
            if(verify==null){message("Verifica non disponibile. Usa le impostazioni Android indicate sopra.");return;}
            credentialPending=true;startActivityForResult(verify,81);
        }else {
            new AlertDialog.Builder(this).setTitle("Dispositivo senza blocco schermo")
                .setMessage("Non è possibile verificare l'identità. Confermi una reale necessità di interrompere la protezione? Dovrai comunque attendere 60 secondi e riscrivere il codice.")
                .setNegativeButton("Annulla",null).setPositiveButton("Confermo emergenza",(d,w)->release()).show();
        }
    }
    private void release(){if(NormalSafety.grant(this)){startActivity(new Intent(this,NormalDisableActivity.class));finish();}else message("Autorizzazione non salvata. La protezione resta invariata.");}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==81 && credentialPending){credentialPending=false;if(result==RESULT_OK)release();else message("Verifica annullata: nessun rilascio autorizzato.");}}
    @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("credentialPending",credentialPending);super.onSaveInstanceState(b);}
}
