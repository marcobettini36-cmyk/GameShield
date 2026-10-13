package it.gameshield;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.widget.*;

/** Normal-only launcher; shared and Strong UI remain untouched. */
public final class NormalHomeActivity extends NormalScreen {
    private TextView state,details;
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);layout("Più spazio alla tua vita.");
        label("Protezione dal gioco d'azzardo, sotto il tuo controllo.",17);
        state=panel("",28);
        android.graphics.drawable.Drawable shield=getDrawable(it.gameshield.R.drawable.shield);if(shield!=null&&getResources().getConfiguration().fontScale<=1.3f){shield.setTint(0xff58e5aa);shield.setBounds(0,0,dp(32),dp(32));state.setCompoundDrawables(shield,null,null,null);state.setCompoundDrawablePadding(dp(12));}
        Button configure=button("Configura protezione",()->startActivity(new Intent(this,NormalSetupActivity.class)));configure.setTextColor(0xff092a1b);android.graphics.drawable.GradientDrawable primary=new android.graphics.drawable.GradientDrawable();primary.setColor(0xff58e5aa);primary.setCornerRadius(dp(16));configure.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33000000),primary,null));
        label("I tuoi livelli di protezione",21);details=panel("",16);
        button("Attiva protezione",this::activate);
        button("Disattiva protezione",()->startActivity(new Intent(this,NormalDisableActivity.class)));
        button("Come funziona GameShield",()->startActivity(new Intent(this,NormalHelpActivity.class)));
        button("Impostazioni Normal",()->startActivity(new Intent(this,NormalSettingsActivity.class)));
        label("Le tue regole",22);
        button("Aggiungi dominio o mirror",()->domain(true));button("Verifica un dominio",()->domain(false));
        button("Aggiorna blacklist",()->{if(!ShieldVpnService.running){message("Attiva prima la protezione.");return;}startForegroundService(new Intent(this,ShieldVpnService.class).setAction("UPDATE"));message("Aggiornamento richiesto.");});
        button("Procedura di emergenza",()->startActivity(new Intent(this,NormalEmergencyActivity.class)));
        if(b==null&&!NormalSafety.prefs(this).getBoolean("setupSeen060",false)){NormalSafety.prefs(this).edit().putBoolean("setupSeen060",true).apply();startActivity(new Intent(this,NormalSetupActivity.class));}
    }
    private void render(){String overall=NormalProtectionState.overall(this);state.setText("Protezione "+overall);state.setTextColor(overall.equals("ATTIVA")?0xff58e5aa:overall.equals("PARZIALE")?0xffffd38a:0xffffadad);
        String dns=NormalProtectionState.actualDns(this),saved=NormalSafety.prefs(this).getString("dnsHostname","");
        details.setText("Filtro Internet: "+(NormalSafety.protectedNow(this)?"attivo":"disattivato")
            +"\nProtezione applicazioni: "+(NormalProtectionState.apps(this)?"attiva per app e browser supportati":"non attiva / da configurare")
            +"\nDNS dichiarato: "+(saved.isEmpty()?"non inserito":saved)
            +"\nDNS Android osservato: "+(dns==null?"Da verificare":dns)
            +"\nFiltro DNS: "+(NormalProtectionState.dnsVerified(this)?"campione verificato di recente; copertura limitata":"Da verificare")
            +"\nAmministratore volontario: "+(NormalProtectionState.admin(this)?"attivo":"non attivo (facoltativo)")
            +"\n"+NormalAutonomy.status(this)+"\nPersona fidata: "+(NormalSafety.prefs(this).contains("trustedHash")?"codice configurato":"non configurata (facoltativa)")
            +"\nATTIVA indica i livelli operativi e un campione DNS verificato, non una protezione totale. Amministratore, periodo e persona fidata sono facoltativi.");
    }
    private void activate(){new AlertDialog.Builder(this).setTitle("Attiva il filtro Internet")
        .setMessage("GameShield filtra i siti nel telefono senza intercettare HTTPS. Android chiederà il consenso alla VPN locale; sostituirà eventuali altre VPN. La scelta resta revocabile.")
        .setNegativeButton("Annulla",null).setPositiveButton("Continua",(d,w)->{Intent i=VpnService.prepare(this);if(i==null)launch();else startActivityForResult(i,41);}).show();}
    private void launch(){try{startForegroundService(new Intent(this,ShieldVpnService.class));}catch(RuntimeException e){message("Android non ha avviato il filtro. Riapri l'app e controlla le impostazioni VPN.");}}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r==41&&result==RESULT_OK)launch();}
    private void domain(boolean add){EditText input=new EditText(this);input.setSingleLine();input.setHint("esempio.it");new AlertDialog.Builder(this).setTitle(add?"Blocca un dominio":"Verifica blacklist").setView(input).setNegativeButton("Annulla",null).setPositiveButton(add?"Aggiungi":"Verifica",(d,w)->{String host=DomainRules.normalize(input.getText().toString());if(host==null){message("Inserisci un dominio senza percorso.");return;}
        if(add){android.content.SharedPreferences p=getSharedPreferences("shield",0);p.edit().putString("custom",p.getString("custom","")+host+"\n").apply();if(ShieldVpnService.running)startForegroundService(new Intent(this,ShieldVpnService.class).setAction("RELOAD"));message("Dominio e sottodomini aggiunti.");}
        else worker.execute(()->{try{boolean blocked=new RuleStore(this).rules().blocks(host);deliver(()->message(blocked?"Dominio bloccato da GameShield":"Dominio non presente: puoi aggiungerlo."));}catch(Exception e){deliver(()->message("Verifica non disponibile. Riprova."));}});
    }).show();}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);refresh.run();}
    @Override protected void onStop(){handler.removeCallbacks(refresh);super.onStop();}
}
