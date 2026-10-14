package it.gameshield;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;

/** Guide only: never changes Android DNS, VPN, or external account settings. */
public final class NormalDnsActivity extends NormalScreen {
    private boolean busy;
    private int step;
    private TextView status,progress;
    private android.content.SharedPreferences prefs(){return NormalSafety.prefs(this);}
    @Override public void onCreate(Bundle b){super.onCreate(b);step=NormalDnsGuide.step(b==null?prefs().getInt("dnsGuideStep",0):b.getInt("step"));render();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("step",step);super.onSaveInstanceState(b);}
    @Override protected void onResume(){super.onResume();refresh();}
    private void move(int next){step=NormalDnsGuide.step(next);prefs().edit().putInt("dnsGuideStep",step).apply();render();}
    private void illustration(String text){root.addView(new NormalDnsDiagram(this,step),new android.widget.LinearLayout.LayoutParams(-1,dp(100)));panel("ILLUSTRAZIONE DIMOSTRATIVA\n"+text+"\nSchema indicativo, non una schermata del tuo account. Nomi e posizione possono variare.",16);}
    private void declared(String text){CheckBox check=new CheckBox(this);check.setText(text+" (dichiarato da te)");check.setTextColor(0xffc9ded3);check.setTextSize(16);check.setMinHeight(dp(48));check.setChecked((prefs().getInt("dnsGuideDeclared",0)&(1<<step))!=0);root.addView(check);final int s=step;check.setOnCheckedChangeListener((v,on)->{int bits=prefs().getInt("dnsGuideDeclared",0);prefs().edit().putInt("dnsGuideDeclared",on?bits|(1<<s):bits&~(1<<s)).apply();refresh();});}
    private void render(){
        String[] titles={"Crea il tuo account","Scegli solo gioco d'azzardo","Trova il tuo indirizzo","Salva in GameShield","Configura il telefono","Verifica il risultato"};
        layout("Guida DNS personale");label("Passaggio "+(step+1)+" di 6 · "+titles[step],22);progress=label("",14);status=panel("",16);
        switch(step){
        case 0:
            label("Il DNS è la rubrica di Internet: un servizio esterno può rifiutare gli indirizzi dei siti di gioco anche senza VPN. È un livello facoltativo, con liste diverse da GameShield.",16);
            label("Apri NextDNS nel browser. Scegli Registrati / Sign up oppure Accedi / Log in. Conserva l'accesso con un gestore di password: l'account permette di ritrovare la tua configurazione. Non inserire mai credenziali NextDNS in GameShield.",16);
            illustration("NextDNS\nRegistrati / Accedi\n↓\nLa tua configurazione personale");button("Apri NextDNS",()->openUrl("https://my.nextdns.io/"));
            label("Piano gratuito: 300.000 richieste DNS al mese. Oltre la quota risponde senza filtrare. Controlla il consumo nel tuo account. Il provider riceve le richieste: leggi la sua informativa.",16);button("Limiti ufficiali NextDNS",()->openUrl("https://nextdns.io/pricing"));declared("Ho creato o recuperato il mio account");break;
        case 1:
            label("Apri Controllo parentale (Parental Control), poi Categorie (Categories). Premi Aggiungi una categoria se necessario, scegli Gioco d'azzardo (Gambling) e attiva il relativo interruttore.",16);
            label("Lascia Pornografia (Porn) disattivata. Non serve attivare altre categorie. Controlla anche liste Privacy, SafeSearch, modalità limitata YouTube e regole personali: possono bloccare altri contenuti. GameShield non può leggere queste scelte.",16);
            illustration("Controllo parentale → Categorie\nGioco d'azzardo: ATTIVO\nPornografia: DISATTIVATA\nAltre categorie: non necessarie");declared("Ho controllato le categorie e gli altri filtri");break;
        case 2:
            label("Torna alla pagina della configurazione NextDNS e apri Configurazione / Setup: la pagina con le istruzioni dei dispositivi, distinta dalla scheda Settings / Impostazioni dell'account. Cerca Android e DNS-over-TLS (o DNS-over-TLS/QUIC): significa DNS cifrato, il formato del DNS privato Android.",16);
            label("Copia solo il nome host personale completo. Non copiare l'indirizzo della pagina web, l'ID da solo o un URL HTTPS. Non inventare l'indirizzo: usa quello del tuo profilo.",16);
            illustration("Configurazione / Setup → Android\nDNS-over-TLS\nabcdef.dns.nextdns.io\nESEMPIO FITTIZIO: non usare questo indirizzo");declared("Ho trovato il nome host del mio profilo");break;
        case 3: hostnameForm();break;
        case 4:
            label("Samsung: Impostazioni → Connessioni → Altre impostazioni di connessione → DNS privato → Nome host del provider DNS privato. Incolla l'indirizzo salvato e premi Salva.",16);
            label("Altri Android: Impostazioni → Rete e Internet → DNS privato → Nome host del provider. Se non trovi la voce, cerca DNS privato nelle Impostazioni. Il pulsante può aprire solo la pagina delle connessioni su alcuni telefoni.",16);
            illustration("DNS privato\n○ Automatico\n● Nome host del provider DNS privato\n[Il tuo indirizzo personale]\nSalva");button("Copia indirizzo DNS",this::copySaved);button("Apri impostazioni DNS privato",this::openDnsSettings);
            label("Se Internet smette di funzionare, torna nella stessa pagina, scegli Automatico e premi Salva. Rimuove il filtro DNS esterno, senza cambiare la VPN GameShield.",16);break;
        case 5: verification();break;
        default: throw new IllegalStateException("Guide step");
        }
        button(step==5?"Termina guida":"Continua",()->{if(step==3){String saved=prefs().getString("dnsHostname",null),draft=NormalDnsConfig.hostname(prefs().getString("dnsGuideDraft",saved));if(draft==null||!draft.equals(saved)){message("Salva prima l'indirizzo DNS valido mostrato nel campo oppure esci e riprendi più tardi.");return;}}if(step==5)finish();else move(step+1);});
        button("Torna indietro",()->{if(step>0)move(step-1);else finish();});button("Esci e riprendi più tardi",this::finish);button("Problemi comuni / FAQ",this::faq);refresh();
    }
    private void hostnameForm(){
        label("Incolla il nome host copiato da NextDNS. Viene conservato solo sul telefono. Salvarlo non configura Android e non dimostra il blocco dei siti. Sono supportati anche provider personalizzati.",16);illustration("Indirizzo DNS personale\n[Nome host copiato dal tuo profilo]\nIncolla → Salva");
        EditText host=new EditText(this);host.setSingleLine(true);host.setHint("Indirizzo DNS personale");host.setTextColor(0xfff1fff8);host.setHintTextColor(0xffc9ded3);host.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);host.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);host.setText(prefs().getString("dnsGuideDraft",prefs().getString("dnsHostname","")));root.addView(host);
        host.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){prefs().edit().putString("dnsGuideDraft",s.toString()).apply();}public void afterTextChanged(Editable e){}});
        button("Incolla",()->{try{ClipboardManager clipboard=getSystemService(ClipboardManager.class);ClipData clip=clipboard==null?null:clipboard.getPrimaryClip();if(clip==null||clip.getItemCount()==0){message("Appunti vuoti o non accessibili. Tieni premuto il campo e scegli Incolla.");return;}CharSequence text=clip.getItemAt(0).coerceToText(this);if(text!=null)host.setText(text);}catch(SecurityException e){message("Appunti non accessibili. Inserisci il nome host manualmente.");}});
        button("Salva",()->{String valid=NormalDnsConfig.hostname(host.getText().toString());if(valid==null){host.setError("Usa il nome host completo, senza https://, ID isolato o credenziali.");return;}String previous=prefs().getString("dnsHostname",null);android.content.SharedPreferences.Editor edit=prefs().edit().putString("dnsHostname",valid).putString("dnsGuideDraft",valid);if(!valid.equals(previous))edit.remove("dnsVerifiedHost");edit.apply();refresh();message("Salvato nell'app. Android e filtraggio restano da verificare.");});button("Copia indirizzo DNS",this::copySaved);
    }
    private void copySaved(){String valid=NormalDnsConfig.hostname(prefs().getString("dnsHostname",null));if(valid==null){message("Salva prima un indirizzo DNS valido nel passaggio 4.");return;}getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Indirizzo DNS personale",valid));message("Copiato. Incollalo tu nelle impostazioni Android.");}
    private void refresh(){if(status==null)return;String saved=prefs().getString("dnsHostname",null),actual=NormalProtectionState.actualDns(this);boolean configured=saved!=null&&saved.equals(actual),verified=NormalProtectionState.dnsVerified(this);
        status.setText("Indirizzo salvato: "+(saved==null?"non ancora inserito":saved)+"\nDNS osservato su Android: "+(actual==null?"non osservato / da verificare":actual)+"\nCorrisponde al salvato: "+(configured?"SÌ":"da verificare")+"\nFiltraggio effettivo: "+(verified?"campione verificato recentemente":"DA VERIFICARE"));
        int bits=prefs().getInt("dnsGuideDeclared",0);progress.setText("1 "+((bits&1)!=0?"dichiarato":"da fare")+" · 2 "+((bits&2)!=0?"dichiarato":"da fare")+" · 3 "+((bits&4)!=0?"dichiarato":"da fare")+"\n4 "+(saved!=null?"salvato":"da fare")+" · 5 "+(configured?"osservato":"da verificare")+" · 6 "+(verified?"campione verificato":"da verificare"));}
    static Intent dnsIntent(){return new Intent("android.settings.PRIVATE_DNS_SETTINGS");}
    static Intent dnsFallbackIntent(){return new Intent(Settings.ACTION_WIRELESS_SETTINGS);}
    private void openDnsSettings(){try{startActivity(dnsIntent());}catch(ActivityNotFoundException|SecurityException e){try{startActivity(dnsFallbackIntent());}catch(ActivityNotFoundException|SecurityException ignored){message("Apri Impostazioni e cerca DNS privato.");}}}
    private void verification(){
        label("Il test controlla DNS per Google, Wikipedia e GitHub, HTTPS verso Wikipedia e una risposta DNS bloccante per un campione della blacklist. Non apre siti di gioco e non richiede account di gioco.",16);
        illustration("Telefono → DNS personale\nSiti innocui: devono rispondere\nCampione gambling: risposta DNS bloccante\nNessuna visita al sito gambling");
        label("Con una VPN attiva il test indipendente non può attribuire il risultato al DNS esterno: resta NON VERIFICATO. GameShield non spegne la VPN. Un campione non certifica tutta la blacklist, tutti i browser o l'assenza di altri filtri.",16);
        TextView result=label("Da verificare. Premi il pulsante per una prova reale; completare la guida non equivale a un test.",16);
        EditText sample=new EditText(this);sample.setSingleLine(true);sample.setHint("Dominio campione nella blacklist");sample.setText("bet365.com");root.addView(sample);
        button("Verifica DNS e navigazione",()->{if(busy)return;String domain=DomainRules.normalize(sample.getText().toString());if(domain==null){message("Inserisci un dominio valido senza percorso.");return;}
            busy=true;NormalSafety.prefs(this).edit().remove("dnsVerifiedHost").apply();refresh();result.setText("Verifica in corso. La VPN resta invariata.");
            worker.execute(()->{String report;try{if(!new RuleStore(this).rules().blocks(domain))report="Scegli un dominio nella blacklist o nelle tue regole. Nessuna query inviata.";else {
                String testedHost=NormalProtectionState.actualDns(this);android.net.Network testedNetwork=NormalProtectionState.dnsNetwork(this);
                report=NormalDnsProbe.run(this,domain);
                if(report.contains("Campione filtrato senza VPN;")&&testedHost!=null&&testedNetwork!=null&&java.util.Objects.equals(testedHost,NormalProtectionState.actualDns(this))&&testedNetwork.equals(NormalProtectionState.dnsNetwork(this)))NormalSafety.prefs(this).edit().putString("dnsVerifiedHost",testedHost).putLong("dnsVerifiedNetwork",testedNetwork.getNetworkHandle()).putLong("dnsVerifiedElapsed",SystemClock.elapsedRealtime()).putInt("dnsVerifiedBoot",NormalSafety.boot(this)).apply();
            }}catch(Exception e){report="Da verificare: il DNS o Internet non risponde. Controlla rete e hostname e riprova. Se Internet resta assente, scegli Automatico nelle impostazioni DNS. Nessuna modifica automatica.";}
            String text=report;deliver(()->{busy=false;result.setText(text);refresh();});});});
        label("Le prove valgono 15 minuti e scadono al cambio di rete, nome host o riavvio. Una risposta di dominio inesistente può sembrare un blocco. Quota esaurita, DNS sicuro del browser e altre VPN possono impedire il filtro.",16);button("Apri impostazioni DNS privato",this::openDnsSettings);
    }
    private void faq(){new android.app.AlertDialog.Builder(this).setTitle("Problemi comuni DNS").setMessage("Indirizzo sbagliato? Copia il nome host completo dalla Configurazione / Setup, non l'ID o un URL. Salvarlo qui non cambia Android.\n\nFiltro non funzionante? Controlla il profilo Android, Gambling attivo, quota disponibile e DNS sicuro del browser. Non possiamo leggere le categorie del tuo account.\n\nInternet assente? Impostazioni → DNS privato → Automatico → Salva. Verifica anche Wi-Fi o rete mobile.\n\nVPN disattivata? Torna alla schermata principale e controlla GameShield. Il DNS è un livello separato e non dimostra che la VPN funzioni.\n\nTest non verificato con VPN? La VPN può influenzare il DNS. Non viene spenta automaticamente per il test.").setPositiveButton("Ho capito",null).show();}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(ActivityNotFoundException e){message("Apri "+url+" nel browser.");}}
}
