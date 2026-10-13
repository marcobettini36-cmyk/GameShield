package it.gameshield;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;

public final class NormalHelpActivity extends NormalScreen {
    @Override public void onCreate(Bundle b){super.onCreate(b);layout("Come funziona GameShield");
        section("Tre livelli, scelte trasparenti","① Filtro Internet: blocca domini di gioco ADM e non ADM conosciuti, inclusi sottodomini e regole aggiunte.\n② Protezione applicazioni: interviene sulle app note e gli indirizzi visibili nei browser supportati.\n③ DNS privato: provider esterno scelto da te, con regole proprie. Nessun livello garantisce blocco assoluto di tutti i mirror.");
        section("La protezione può essere revocata?","Sì. Android ti lascia controllare VPN, permessi, impostazioni e disinstallazione. Il periodo autonomo limita solo le modifiche interne; non rende il dispositivo irrevocabile. Persona fidata e SMS sono facoltativi.");
        section("Se Internet smette di funzionare","① Controlla Wi-Fi / dati mobili.\n② In VPN Android verifica Blocca connessioni senza VPN: se il filtro è fermo, questa opzione può fermare Internet.\n③ Se hai configurato un DNS privato che non risponde, torna ad Automatico.\n④ Riapri GameShield e riprova. Nessuna impostazione viene cambiata di nascosto.");
        button("Apri VPN Android",()->open(Settings.ACTION_VPN_SETTINGS));
        section("Se il filtro non si riattiva","Apri GameShield e premi Attiva protezione. Dopo un rilascio volontario l'app non riparte di nascosto. In Impostazioni Normal → Protezione avanzata puoi consentire ripristino e controllare batteria. Su Samsung controlla anche App in sospensione. Arresto forzato, disinstallazione e limiti Android possono impedire avvisi e riavvio.");
        section("Se la protezione applicazioni è disattivata","Samsung: Impostazioni → Accessibilità → Applicazioni installate. Altri Android: Accessibilità → App scaricate. Rileggi il consenso in GameShield e scegli il servizio. Android e i browser possono limitarne l'efficacia. Nessun blocco delle impostazioni.");
        button("Apri Accessibilità Android",()->open(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        section("Modificare o rimuovere il DNS","Samsung: Connessioni → Altre impostazioni di rete → DNS privato. Altri Android: Rete e Internet → DNS privato. Scegli Automatico per rimuovere l'hostname, oppure inserisci un provider personale. Disattivare GameShield non rimuove il DNS configurato da te.");
        button("Guida DNS e verifica",()->startActivity(new Intent(this,NormalDnsActivity.class)));
        section("DNS selettivo e costi","Per NextDNS attiva Gambling e lascia Porn disattivato. Controlla anche liste, SafeSearch e altre categorie: possono filtrare contenuti diversi dal gioco. Piano gratuito: 300.000 query al mese; oltre la quota il DNS continua a risolvere ma non filtra. Non conosciamo la quota del tuo account e non leggiamo le sue impostazioni.");
        section("Emergenza e disattivazione","La richiesta interna resta in attesa e puoi annullarla. Il codice casuale, il PIN facoltativo e la conferma finale restano necessari. L'emergenza è sempre raggiungibile. Le scelte di VPN sempre attiva e DNS privato vanno rimosse da te nelle impostazioni Android, se necessario.");
        button("Procedura di emergenza",()->startActivity(new Intent(this,NormalEmergencyActivity.class)));
        section("Altri limiti","Altre VPN, DNS del browser, IP diretti, nuovi domini ed ECH possono superare alcuni livelli. Il test DNS controlla un campione e la connettività, non l'intera blacklist. Nessuna intercettazione HTTPS. Rivolgiti anche al SerD della tua zona se il gioco ti mette in difficoltà.");button("Torna indietro",this::finish);
    }
    private void section(String title,String body){label(title,22);label(body,16);}
    private void open(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException|SecurityException e){message("Apri manualmente le impostazioni seguendo la guida.");}}
}
