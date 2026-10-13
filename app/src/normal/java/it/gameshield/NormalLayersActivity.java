package it.gameshield;
import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.TextView;

/** Normal-only controls; granting Accessibility is always a manual Android choice. */
public final class NormalLayersActivity extends NormalScreen {
    private TextView state;
    private final Runnable refresh=new Runnable(){public void run(){state.setText(NormalSafety.status(NormalLayersActivity.this));handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);layout("Protezione Autonoma e livelli indipendenti");state=label("",16);
        label("Protezione Autonoma facoltativa",21);
        label("Scegli un periodo. Durante questo periodo la richiesta ordinaria di disattivazione resta in attesa fino alla scadenza e almeno 24 ore dalla richiesta. Il filtro non viene spento durante l'attesa. La persona fidata è opzionale; l'emergenza resta disponibile. Android può sempre revocare VPN e permessi o disinstallare l'app.",15);
        for(int days:new int[]{7,30,90})button("Attiva Protezione Autonoma: "+days+" giorni",()->activate(days));
        button("Richiedi sblocco / disattivazione",()->startActivity(new Intent(this,NormalDisableActivity.class)));
        button("Annulla richiesta di sblocco",()->{NormalSafety.cancelRelease(this);message("Richiesta annullata. Periodo autonomo e protezione restano invariati.");});
        label("Intervento senza VPN",21);
        label("Accessibility può interrompere app gambling note e hostname visibili in Chrome / Samsung Internet, anche con VPN spenta. È reattivo: non sospende le app né impedisce tutto il traffico in background. Nessun blocco delle impostazioni Android. Puoi revocare il servizio nelle impostazioni.",15);
        button("Configura intervento contenuti facoltativo",this::disclosure);
        button("Sospendi intervento contenuti dall'app",()->{if(NormalAutonomy.locked(this)){message("Periodo autonomo attivo: richiedi sblocco o usa l'emergenza. Android resta accessibile.");return;}
            new AlertDialog.Builder(this).setTitle("Sospendere il livello contenuti?").setMessage("La VPN resta invariata. Senza VPN questo livello non interverrà più.").setNegativeButton("Mantieni",null).setPositiveButton("Sospendi",(d,w)->{if(!NormalAutonomy.locked(this))NormalSafety.prefs(this).edit().putBoolean("contentConsent",false).commit();}).show();});
        button("Apri impostazioni Accessibility Android",()->open(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        label("DNS privato indipendente",21);
        button("Guida e verifica DNS senza VPN",()->startActivity(new Intent(this,NormalDnsActivity.class)));
        label("Samsung Knox",21);
        label("Nessuna restrizione Knox attivata: le API richiedono autorizzazioni/licenza Samsung non disponibili a questa app. Non promettiamo una VPN irrevocabile su S23 Ultra / Android 16.",15);
        button("Procedura di emergenza",()->startActivity(new Intent(this,NormalEmergencyActivity.class)));
        button("Torna indietro",this::finish);
    }
    private void activate(int days){
        if(NormalAutonomy.remaining(this)>0){message("Un periodo è già attivo. Non viene accorciato né rinnovato di nascosto.");return;}
        if(!NormalSafety.protectedNow(this)){message("Attiva prima la VPN dalla schermata principale. Questo periodo non concede permessi Android da solo.");return;}
        new AlertDialog.Builder(this).setTitle("Confermi "+days+" giorni?").setMessage("Proteggi le impostazioni interne e attendi la scadenza per il rilascio ordinario. Nessuna persona fidata obbligatoria. Accessibility richiede una configurazione separata. Emergenza e impostazioni Android restano sempre disponibili.")
            .setNegativeButton("Annulla",null).setPositiveButton("Confermo il periodo",(d,w)->{if(NormalSafety.protectedNow(this)&&NormalAutonomy.start(this,days))message("Periodo autonomo iniziato. Nessuna modifica alle impostazioni Android.");else message("Periodo non avviato: verifica la protezione attiva.");}).show();
    }
    private void disclosure(){new AlertDialog.Builder(this).setTitle("Consenso separato: Accessibility")
        .setMessage("GameShield accede al nome dell'app in primo piano e, nei browser supportati, al testo del controllo indirizzo. Con regole fisse interrompe il contenuto gambling e torna alla Home. Non conserva né trasmette questi dati, non legge password o messaggi, non acquisisce screenshot e non blocca Impostazioni, revoca permessi o disinstallazione. Le strutture dei browser e Android 16 possono limitarne la copertura. Il servizio resta revocabile. Consenti questo uso locale?")
        .setNegativeButton("Annulla",null).setPositiveButton("Acconsento: apri Android",(d,w)->{NormalSafety.prefs(this).edit().putBoolean("contentConsent",true).commit();open(Settings.ACTION_ACCESSIBILITY_SETTINGS);}).show();}
    private void open(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException|SecurityException e){message("Apri manualmente la pagina nelle impostazioni Android.");}}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);refresh.run();}
    @Override protected void onStop(){handler.removeCallbacks(refresh);super.onStop();}
}
