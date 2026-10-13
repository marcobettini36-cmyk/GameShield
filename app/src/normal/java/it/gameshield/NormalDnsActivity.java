package it.gameshield;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

public final class NormalDnsActivity extends NormalScreen {
    private boolean busy;
    @Override public void onCreate(Bundle b){super.onCreate(b);layout("DNS selettivo: solo gioco d'azzardo");
        label("Un livello indipendente e facoltativo",22);
        label("Il DNS privato può filtrare senza VPN usando le liste del provider, non le regole manuali GameShield. Il provider riceve le query DNS: leggi la sua informativa. Nessun accesso al tuo account, nessuna modifica automatica ad Android, nessun arresto della VPN per i test.",16);
        label("① Crea la tua configurazione NextDNS",22);
        label("Apri my.nextdns.io. Crea o seleziona la tua configurazione e conserva l'accesso. GameShield non chiede credenziali.",16);
        button("Apri configurazione NextDNS",()->openUrl("https://my.nextdns.io/"));
        label("② Attiva solo Gambling",22);
        label("In Parental Control aggiungi Gambling (gioco d'azzardo). Lascia Porn (contenuti per adulti) disattivata. Controlla anche altre categorie, liste Privacy, SafeSearch e regole personali: non attivare filtri non desiderati. Non possiamo leggere queste scelte né certificare l'assenza di altri blocchi.",16);
        label("③ Inserisci il tuo hostname personale",22);
        label("Dalla pagina Setup copia l'hostname DNS-over-TLS completo con il tuo identificativo. Non usare un indirizzo NextDNS generico, URL, password o token. Puoi scegliere anche un provider personalizzato.",16);
        EditText host=new EditText(this);host.setSingleLine(true);host.setHint("Hostname DNS-over-TLS personale");host.setText(NormalSafety.prefs(this).getString("dnsHostname",""));root.addView(host);
        button("Salva hostname dichiarato",()->{String valid=NormalDnsConfig.hostname(host.getText().toString());if(valid==null){message("Inserisci l'hostname personale completo, senza https:// o credenziali.");return;}NormalSafety.prefs(this).edit().putString("dnsHostname",valid).remove("dnsVerifiedHost").apply();message("Salvato nell'app: Android e filtro sono ancora da verificare.");});
        button("Copia hostname personale",()->{String valid=NormalDnsConfig.hostname(host.getText().toString());if(valid==null){message("Hostname non valido. Copialo dalla pagina Setup del provider.");return;}getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Hostname DNS personale",valid));message("Copiato. Configuralo tu in Android.");});
        label("④ Configura Android",22);
        label("Samsung: Connessioni → Altre impostazioni di rete → DNS privato. Altri Android: Rete e Internet → DNS privato. Scegli Hostname provider, incolla e salva. Se Internet non risponde, torna ad Automatico. I nomi possono variare.",16);
        button("Apri impostazioni DNS privato",()->{try{startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS"));}catch(ActivityNotFoundException|SecurityException e){try{startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}catch(ActivityNotFoundException|SecurityException ignored){message("Apri Impostazioni e cerca DNS privato.");}}});
        label("⑤ Verifica il filtro senza cambiare la VPN",22);
        TextView result=label("Da verificare. Un hostname inserito non dimostra che il filtro funzioni.",16);
        EditText sample=new EditText(this);sample.setSingleLine(true);sample.setHint("Dominio campione nella blacklist");sample.setText("bet365.com");root.addView(sample);
        button("Verifica DNS e navigazione",()->{if(busy)return;String domain=DomainRules.normalize(sample.getText().toString());if(domain==null){message("Inserisci un dominio valido senza percorso.");return;}
            busy=true;NormalSafety.prefs(this).edit().remove("dnsVerifiedHost").apply();result.setText("Verifica in corso. La VPN resta invariata.");
            worker.execute(()->{String report;try{if(!new RuleStore(this).rules().blocks(domain))report="Scegli un dominio nella blacklist o nelle tue regole. Nessuna query inviata.";else {
                String testedHost=NormalProtectionState.actualDns(this);android.net.Network testedNetwork=NormalProtectionState.dnsNetwork(this);
                report=NormalDnsProbe.run(this,domain);
                if(report.contains("Campione filtrato senza VPN;")&&testedHost!=null&&testedNetwork!=null&&java.util.Objects.equals(testedHost,NormalProtectionState.actualDns(this))&&testedNetwork.equals(NormalProtectionState.dnsNetwork(this)))NormalSafety.prefs(this).edit().putString("dnsVerifiedHost",testedHost).putLong("dnsVerifiedNetwork",testedNetwork.getNetworkHandle()).putLong("dnsVerifiedElapsed",SystemClock.elapsedRealtime()).putInt("dnsVerifiedBoot",NormalSafety.boot(this)).apply();
            }}catch(Exception e){report="Da verificare: il DNS o Internet non risponde. Controlla rete e hostname e riprova. Se Internet resta assente, scegli Automatico nelle impostazioni DNS. Nessuna modifica automatica.";}
            String text=report;deliver(()->{busy=false;result.setText(text);});});});
        label("Piano gratuito e limiti",22);
        label("NextDNS: 300.000 query al mese gratuite. Superata la quota il DNS risolve ancora ma non filtra fino al rinnovo della quota. Controlla il consumo nel tuo account; GameShield non lo legge. Nessun abbonamento obbligatorio, nessuna promessa di filtro illimitato. Le verifiche locali scadono dopo 15 minuti, cambio hostname o riavvio e riguardano solo un campione. DNS del browser, IP diretti e altre VPN possono aggirare il filtro.",16);
        button("Condizioni ufficiali NextDNS",()->openUrl("https://nextdns.io/pricing"));button("Torna indietro",this::finish);
    }
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(ActivityNotFoundException e){message("Apri "+url+" nel browser.");}}
}
