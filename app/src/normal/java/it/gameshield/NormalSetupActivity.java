package it.gameshield;
import android.app.*;
import android.content.*;
import android.app.admin.DevicePolicyManager;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

public final class NormalSetupActivity extends NormalScreen {
    private int step;
    private TextView progress,title,explanation,check;
    private android.widget.Button action;
    private static final String[] TITLES={"Filtro Internet","Protezione sempre attiva","Internet solo con protezione","Proteggi anche le applicazioni","Un passaggio prima della rimozione","Un filtro aggiuntivo della rete","Scegli un periodo di protezione","Un aiuto facoltativo"};
    private static final String[] TEXT={
        "Il filtro locale blocca i domini di gioco conosciuti e lascia passare quelli consentiti. Android lo chiama VPN: non è una VPN remota. Richiede il tuo consenso e sostituisce altre VPN.",
        "Android può mantenere GameShield sempre attivo. Nella pagina VPN premi l'ingranaggio accanto a GameShield e attiva VPN sempre attiva. La scelta è tua e resta revocabile.",
        "La voce Blocca connessioni senza VPN impedisce Internet quando il filtro è fermo. È una scelta facoltativa: un guasto può togliere connettività. Puoi disattivarla nelle impostazioni anche in emergenza.",
        "Un livello facoltativo può interrompere app di gioco note e indirizzi visibili nei browser supportati. Android lo chiama Accessibilità. Prima leggi il consenso; non impedisce di aprire Impostazioni o disinstallare.",
        "L'amministratore volontario aggiunge il passaggio Android di disattivazione prima della rimozione, dove supportato. Non blocca il telefono né impone un'attesa al sistema. Puoi revocarlo.",
        "Il DNS privato può filtrare anche senza GameShield VPN. Segui la guida, scegli il tuo hostname personale e verifica il filtro. GameShield non cambia le impostazioni e non spegne la VPN per testarlo.",
        "Puoi scegliere 7, 30 o 90 giorni per ridurre cambiamenti impulsivi nelle impostazioni interne. Attiva prima il filtro. Il periodo non impedisce la revoca Android; l'emergenza resta disponibile.",
        "Una persona fidata è facoltativa. Puoi configurare un codice di rilascio anticipato. Solo nell'APK esterno puoi scegliere anche avvisi SMS, con consenso, permesso e possibili costi. Non è richiesto un abbonamento."};
    @Override public void onCreate(Bundle b){super.onCreate(b);step=b==null?NormalSafety.prefs(this).getInt("setupStep060",0):b.getInt("step",0);step=Math.max(0,Math.min(7,step));layout("Configura protezione");
        progress=label("",15);title=label("",25);explanation=label("",17);check=label("",17);action=button("Configura",this::configure);
        button("Passaggio successivo / salta",()->{if(step<7){step++;save();render();}else finish();});button("Passaggio precedente",()->{if(step>0){step--;save();render();}});
        button("Riprendi più tardi",this::finish);button("Aiuto e procedura di emergenza",()->startActivity(new Intent(this,NormalHelpActivity.class)));render();}
    private void save(){NormalSafety.prefs(this).edit().putInt("setupStep060",step).apply();}
    private void render(){progress.setText("Passaggio "+(step+1)+" di 8 · puoi saltare le opzioni facoltative");title.setText(TITLES[step]);explanation.setText(TEXT[step]+"\n\n"+route());check.setText(verified()?"✓ Verificato sul dispositivo":step==7?"Facoltativo · nessuna persona richiesta":"Da verificare · torna qui dopo la configurazione");check.setTextColor(verified()?0xff58e5aa:0xffffd38a);action.setText(step==0?"Attiva filtro Internet":step==5?"Apri guida DNS selettivo":"Configura questo passaggio");}
    private boolean verified(){switch(step){case 0:return NormalSafety.protectedNow(this);case 1:return Boolean.TRUE.equals(NormalMonitorService.alwaysOn());case 2:return Boolean.TRUE.equals(NormalMonitorService.lockdown());case 3:return NormalProtectionState.apps(this);case 4:return NormalProtectionState.admin(this);case 5:return NormalProtectionState.dnsVerified(this);case 6:return NormalAutonomy.remaining(this)>0;case 7:return NormalSafety.prefs(this).contains("trustedHash");default:return false;}}
    private String route(){boolean samsung="samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER);
        if(step==1||step==2)return samsung?"Samsung: Impostazioni → Connessioni → Altre impostazioni di rete → VPN → GameShield.":"Android: Impostazioni → Rete e Internet → VPN → GameShield. I nomi possono variare.";
        if(step==3)return samsung?"Samsung: Impostazioni → Accessibilità → Applicazioni installate → GameShield.":"Android: Impostazioni → Accessibilità → App scaricate → GameShield.";
        if(step==4)return "Nella schermata Android leggi i privilegi e scegli volontariamente. Non è Device Owner.";
        if(step==5)return "Samsung: Connessioni → Altre impostazioni di rete → DNS privato. Altri Android: Rete e Internet → DNS privato.";
        return "Nessun permesso viene concesso automaticamente. Puoi tornare indietro in qualsiasi momento.";
    }
    private void configure(){try{switch(step){
        case 0:new AlertDialog.Builder(this).setTitle("Consenso al filtro Internet").setMessage(TEXT[0]).setNegativeButton("Annulla",null).setPositiveButton("Continua",(d,w)->{Intent i=VpnService.prepare(this);if(i==null)startForegroundService(new Intent(this,ShieldVpnService.class));else startActivityForResult(i,42);}).show();break;
        case 1:case 2:startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));break;
        case 3:case 6:startActivity(new Intent(this,NormalLayersActivity.class));break;
        case 4:startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,new ComponentName(this,NormalAdminReceiver.class)).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Scelta volontaria e revocabile. Nessun blocco delle impostazioni Android."));break;
        case 5:startActivity(new Intent(this,NormalDnsActivity.class));break;
        case 7:startActivity(new Intent(this,NormalResilienceActivity.class));break;
    }}catch(RuntimeException e){message("Questa pagina non è disponibile. Segui il percorso indicato nelle impostazioni.");}}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r==42&&result==RESULT_OK){try{startForegroundService(new Intent(this,ShieldVpnService.class));}catch(RuntimeException e){message("Filtro non avviato. Riprova dalla schermata principale.");}}}
    @Override protected void onResume(){super.onResume();if(check!=null){render();handler.postDelayed(()->{if(!isFinishing())render();},1500);}}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("step",step);super.onSaveInstanceState(b);}
}
