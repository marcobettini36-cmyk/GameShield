package it.gameshield;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

public final class NormalDnsActivity extends NormalScreen {
    private boolean busy;
    @Override public void onCreate(Bundle b){super.onCreate(b);layout("DNS indipendente: guida e verifica");
        label("Non serve una seconda VPN",21);
        label("Un provider Private DNS può filtrare anche quando la VPN GameShield è spenta. Il provider vede le query DNS e applica liste proprie, non la blacklist/manuali GameShield. DoH del browser, IP diretti e altri resolver possono aggirarlo. Non rende la protezione irrevocabile.",15);
        label("Non imponiamo abbonamenti e non configuriamo un provider automaticamente. Scegli un servizio che dichiari esplicitamente il filtro gambling, condizioni gratuite adeguate e privacy accettabile. DNS malware/famiglia non significa necessariamente gambling. Mullvad ha annunciato la chiusura del DNS pubblico il 2 novembre 2026: non viene consigliato come soluzione stabile.",15);
        label("Android: Impostazioni → rete/connessioni → DNS privato → hostname provider. Su Samsung cerca 'DNS privato'. Copia l'hostname fornito dal provider, verifica Internet e, in caso di problemi, torna ad Automatico. La scelta resta manuale.",15);
        button("Apri impostazioni rete Android",()->{try{startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}catch(ActivityNotFoundException e){message("Apri manualmente Impostazioni e cerca DNS privato.");}});
        TextView result=label("Nessun test eseguito. La VPN non verrà spenta dall'app.",16);
        EditText sample=new EditText(this);sample.setSingleLine(true);sample.setHint("Dominio gambling già bloccato da GameShield");sample.setText("bet365.com");sample.setSaveEnabled(false);root.addView(sample);
        button("Verifica reale quando la VPN è spenta",()->{if(busy)return;String host=DomainRules.normalize(sample.getText().toString());if(host==null){message("Inserisci solo un hostname valido.");return;}
            busy=true;result.setText("Verifica DNS e HTTPS in corso; nessuna impostazione viene cambiata.");
            worker.execute(()->{String report;try{if(!new RuleStore(this).rules().blocks(host))report="Scegli un dominio presente nella blacklist/manuali GameShield. Nessuna query inviata.";else report=NormalDnsProbe.run(this,host);}catch(Exception e){report="NON VERIFICATO: "+e.getClass().getSimpleName()+". Nessuna modifica alla rete; controlla provider e copertura.";}String text=report;deliver(()->{busy=false;result.setText(text);});});});
        button("Torna indietro",this::finish);
    }
}
