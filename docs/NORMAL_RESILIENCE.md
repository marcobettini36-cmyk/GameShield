# Normal 0.4.0: supervisione e rilascio

Le modifiche sono nei sorgenti, manifest e test del flavor Normal. Nessun sorgente
condiviso del motore VPN, di Strong, o patch native è modificato. La baseline
`scripts/strong_normal_change_baseline.json` congela questi file per la regressione
di questo intervento. La compilazione Java Strong è confrontata anche byte per
byte con quella precedente. Strong conserva package, SDK, codice 5 e versione 0.3.2.

## Configurazione

Aprire **Impostazioni Normal → Protezione avanzata Normal / persona fidata**.

1. Attivare la VPN dalla schermata principale. Aprire le impostazioni VPN Android,
   scegliere GameShield e abilitare volontariamente Always-on e, se desiderato,
   “Blocca connessioni senza VPN”. GameShield non li attiva autonomamente.
2. Consentire la supervisione con notifica permanente e i tentativi di ripristino.
   Il supervisore separato controlla TUN nativo e rete VPN del sistema; non modifica
   il forwarding. Ripristina soltanto con consenso VPN ancora valido, rete fisica
   verificata e nei limiti di avvio dei servizi Android. Tre tentativi falliti o
   tre guasti confermati dal filtro sospendono il recupero. Non sostituisce altre VPN.
3. Attivare facoltativamente l'amministratore Normal nel dialogo Android. Non usa
   politiche di password, blocco schermo, wipe, Device Owner o restrizioni di
   disinstallazione proprietarie. Android richiede normalmente la sua disattivazione
   prima della disinstallazione, dove questa amministrazione legacy è supportata.
4. La persona fidata può scegliere un codice locale di 6–12 cifre. È memorizzato
   usando la crittografia PIN Normal già esistente (hash salato, non testo in chiaro).
   Modifica/rimozione e impostazioni sensibili richiedono il codice corrente.
   Cinque errori impongono una pausa di un minuto. Nessun codice compare nei log.

## Disattivazione ed emergenza

La richiesta interna avvia 24 ore persistenti. Chiudere o ricreare l'Activity non
salta né azzera l'attesa; annullare esplicitamente la richiesta la elimina. Restano
codice casuale di sei cifre, attesa breve iniziale, PIN Normal quando configurato
e conferma finale. La persona fidata può autorizzare un rilascio anticipato; il
permesso dura dieci minuti e non attraversa un riavvio. Una modifica del suo codice
invalida l'autorizzazione. La verifica del PIN facoltativo è sostituita dal rilascio
fidato o d'emergenza, senza impedire a chi controlla il dispositivo di uscire.

L'emergenza offre direttamente le pagine VPN, sicurezza/amministratori e informazioni
app Android. Per il rilascio anticipato interno richiede la credenziale del blocco
schermo, quando disponibile; altrimenti mostra due conferme esplicite. Il codice
casuale/attesa breve e la conferma finale restano richiesti. Non viene attivata alcuna
Accessibility Service. Dopo il rilascio volontario il ripristino viene disabilitato
e l'amministratore Normal rimosso; gli SMS eventualmente consentiti restano abilitati
per avvisare della disconnessione. Occorre togliere Always-on/lockdown da Android
perché il sistema non riavvii autonomamente la VPN.

## SMS: APK diretto e AAB Play distinti

L'APK debug Normal include `SEND_SMS`. Nell'interfaccia sono necessari numero manuale,
SIM SMS predefinita, consenso esplicito e permesso runtime. Non legge rubrica, SMS
ricevuti, numeri della SIM o registro chiamate; il numero resta nel dato privato
dell'app, con backup disabilitato. Revocare il consenso è sempre consentito.

Solo dopo aver osservato una protezione attiva, una disconnessione avvia un episodio
di almeno 15 minuti. Una riconnessione lo annulla. Il testo automatico è:

> Avviso GameShield: la protezione contro il gioco d'azzardo risulta disattivata da almeno 15 minuti. Ti consigliamo di contattare la persona che ti ha indicato come riferimento.

La prenotazione è persistita prima di chiamare SmsManager. Episodio, tentativo e parte
identificano i callback; risultati vecchi e duplicati vengono ignorati. Nessun reinvio
automatico dopo esiti parziali o ambigui. Se tutte le parti sono sicuramente non
inviate per radio spenta/copertura assente, il retry attende almeno cinque minuti.
SIM assente/bloccata, cambio SIM, default ambiguo su dual SIM e permesso negato
producono uno stato esplicito. Va riconfermata la SIM dopo un cambio; nessun selettore
SIM viene aperto durante un invio in background. La prova è un SMS reale chiaramente
marcato come prova, richiede conferma e ammette al massimo un tentativo al minuto.

L'AAB `-PnormalPlayRelease=true` esclude SEND_SMS, ricevitore SMS e invio automatico.
È la distribuzione scelta per Play: consenso e permesso runtime non bastano ad
ottenere un'eccezione alla policy Google. Non viene simulato il ruolo di app SMS.
L'APK debug non è destinato al caricamento su Google Play.

## Limiti dichiarati

- Le 24 ore vincolano il flusso interno, non la disattivazione dell'amministratore,
  della VPN o la disinstallazione dalle impostazioni Android. Cancellare dati,
  disinstallare, modificare il sistema o possedere privilegi root può aggirarle.
- Entro lo stesso avvio i timer usano l'orologio monotono. Dopo un riavvio usano un
  fallback limitato sull'orologio civile; cambi manuali dell'ora possono influenzarlo.
- Lockdown scelto dall'utente interrompe Internet senza VPN, anche se il motore
  Normal mantiene il precedente comportamento fail-open. L'emergenza spiega come
  disattivarlo: non viene nascosto questo rischio.
- Job persistenti, riavvio del supervisore e monitoraggio foreground aiutano ma
  non garantiscono puntualità. Doze, OEM/Samsung, permessi notifiche negati e limiti
  di avvio FGS possono ritardare ripristino e SMS. Non si richiede l'accesso agli
  allarmi esatti né si forza un'esenzione dalla batteria.
- Arresto forzato e disinstallazione possono impedire ogni avviso. Una prenotazione
  seguita da crash prima dell'invio privilegia evitare duplicati: l'avviso può non
  partire. SMS affidato all'operatore non significa ricevuto dal destinatario e non
  può essere richiamato quando la VPN ritorna attiva. Possibili costi multipart.
- La persona fidata sblocca mediante codice locale, non tramite SMS ricevuti o un
  servizio remoto. Non esiste provisioning Device Owner nella Normal. Quel modello
  richiede gestione dedicata e viene valutato separatamente, senza modificare Strong.

## Verifica e build

`testNormalDebugUnitTest`, `testStrongDebugUnitTest`, `lintNormalDebug`,
`assembleNormalDebug`, `assembleNormalDebugAndroidTest`; test Python e confronto
Strong prima/dopo. La nuova workflow `normal-resilience.yml` esegue su Android 35
il tunnel vero, HTTPS consentito, recupero autorizzato, rilascio volontario, UI,
ricreazione, PIN e gate di 24 ore. La prova delle 24 ore complete usa test di timer
e checkpoint preparati per gli scenari UI successivi; non pretende 24 ore reali
di attesa. Nessun SMS verso persone reali è inviato dai test automatici.

Per Play: `testNormalReleaseUnitTest lintNormalRelease bundleNormalRelease` con
`-PnormalPlayRelease=true`. La firma richiede la chiave upload originale descritta
in `NORMAL_PLAY_RELEASE.md`; senza configurazione il bundle è unsigned.

Riferimenti: [VPN Android](https://developer.android.com/develop/connectivity/vpn),
[Device Administrator](https://developer.android.com/work/device-admin),
[limiti FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
[SmsManager](https://developer.android.com/reference/android/telephony/SmsManager),
[policy SMS Play](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en-GB).
