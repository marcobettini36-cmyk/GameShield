# Compatibilità Android Auto — 0.3.2



## Analisi del codice precedente



`ShieldVpnService.establishTransport()` configurava indirizzo 198.18.0.1/32,

DNS sintetico 198.18.0.2 e route 0.0.0.0/0; sulle reti con IPv6 aggiungeva

fd42:4753::1/128 e ::/0. Non configurava allowed/disallowed applications.

Il traffico Android Auto, comprese le comunicazioni locali, entrava quindi

nel TUN/relay del resto del telefono. In Strong, `StrongPolicy.enable()`

abilitava always-on/lockdown senza eccezioni: escludere il pacchetto dal solo

Builder non sarebbe sufficiente, perché Android bloccherebbe il suo UID fuori

dalla VPN. Questa è la condizione tecnica trovata; senza log e collegamento

all'auto non è possibile attribuire con certezza ogni Errore 21 a tale causa.



Avvio/arresto, consenso VPN, rilascio custode, self-test, BootReceiver e

riavvio dopo aggiornamento sono stati esaminati. Le loro politiche restano

invariate. Il relay, i socket protetti, i parser e la blacklist non cambiano.



## Strategia



Il Builder usa `addDisallowedApplication()` esclusivamente per

`com.google.android.projection.gearhead`, quando installato, abilitato,

firmato con uno dei due certificati di produzione pubblicati da Android

UAMP e con UID non condiviso. Package assente/rimosso/falso: nessuna eccezione.

L'eccezione copre traffico IPv4, IPv6 e LAN di quel solo UID.



Normal abilita la compatibilità per impostazione predefinita. Strong richiede

opt-in e codice custode; se lockdown è già attivo, ne aggiorna soltanto

l'allowlist con il medesimo pacchetto verificato. Non disabilita lockdown.

Nessuna esclusione Google Play Services: l'UID ospita molti servizi estranei

alla proiezione. Browser, Maps e app musicali conservano il normale filtro.



Sono stati valutati `allowBypass()` ed `excludeRoute()` (API 33): non usati.

Il primo consentirebbe a qualsiasi app di selezionare una rete fisica;

esclusioni globali RFC1918, link-local o Wi-Fi Direct permetterebbero anche

a browser/proxy locali di eludere il filtro. L'esenzione del solo UID Android

Auto risolve il routing locale di quel componente senza queste eccezioni.

Non è stata osservata una necessità di eccezioni per altri componenti Google:

le eventuali esigenze di uno specifico telefono vanno misurate sull'hardware.



`AndroidAutoCompatibilityManager` usa AndroidX `CarConnection` 1.7.0.

Prima dell'osservazione verifica che il provider ufficiale sia posseduto

dal pacchetto Android Auto autenticato. Il broadcast di aggiornamento è

soltanto un invito a interrogare il provider; i suoi extra non sono prova

di proiezione. Solo CONNECTION_TYPE_PROJECTION produce lo stato collegato.

Nessuna Accessibility, osservazione app in primo piano o pulsante pausa.



L'impostazione rispetta il PIN opzionale Normal e il codice custode Strong.

Autorizzazioni temporanee e dialoghi non sopravvivono alla chiusura della

schermata. La modifica aggiorna le regole VPN senza cambiare liste/configurazione.

Le riconfigurazioni e il controllo del motore sono serializzati sul lock

di lifecycle; i callback prima della disponibilità del TUN sono ignorati.
RECONFIGURE_AUTO aggiorna soltanto una VPN già attiva: un messaggio
consegnato dopo STOP non riavvia la protezione volontariamente disattivata.
Il manager segue anche abilitazione/disabilitazione del pacchetto host.

Log nuovi: solo stato connessione/routing, assenza host o query indisponibile.



## Fallback e limiti



Nessun fallback di sospensione automatica: non esiste una prova hardware

della sua necessità, né viene interpretato un broadcast come Errore 21.

La VPN resta attiva collegando/scollegando l'auto. Un dispositivo che rifiuta

qualsiasi VPN attiva potrebbe ancora mostrare Errore 21; non viene promessa

la risoluzione prima del collaudo fisico.



MinSDK Normal 29 / Strong 30, target/compile 35 invariati.

Versione 0.3.2, versionCode 5 per entrambe le varianti.



## Verifica



Test JVM aggiunti: UID isolato, produzione vs firma falsa/debug,

stato projection autenticato, nessuna esclusione GMS/browser.

Test strumentali aggiunti: falso broadcast/host assente, ricreazione Activity,

PIN/custode errato e corretto, annullamento/conferma impostazione, VPN e

risposta DNS BLOCK mantenute. Un passaggio dedicato rimuove Android Auto

solo dall’utente dell’emulatore e verifica anche il caso realmente assente,

poi ripristina il pacchetto prima dei test browser. Eseguiti tramite GitHub Actions insieme ai

test esistenti di traffico reale, Chrome, Play Store e disattivazione Normal,

con Private DNS Off/Automatico su emulatore Android 35.

La matrice DNS Off comprende un vero riavvio dell’emulatore e controllo

del ripristino VPN/self-test. Strong viene provisionato Device Owner sul

solo emulatore usa-e-getta, verificando la allowlist stretta e lockdown

durante attivazione/rimozione dell’eccezione; il rilascio custode rimuove

l’owner a fine prova. Le restrizioni debug non vengono applicate al runner.

Gli esiti effettivi e gli APK sono registrati nel report consegnato.



Collaudi fisici ancora necessari, non simulati dagli emulatori:

Android Auto cablato e wireless, assenza Error 21, Maps/musica/telefonia,

Chrome filtrato durante la proiezione, scollegamento e riavvio con host reale,

Strong Device Owner/lockdown con Android Auto ufficiale. Nessun telefono o

veicolo è collegato a questa sessione. Gli emulatori non certificano tali casi.



## Fonti ufficiali



- [VpnService.Builder](https://developer.android.com/reference/android/net/VpnService.Builder)

- [CarConnection](https://developer.android.com/training/cars/apps/library/connection-api)

- [AndroidX car-app](https://developer.android.com/jetpack/androidx/releases/car-app)

- [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)

- [VPN Android 15: lockdown UID ranges](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/connectivity/Vpn.java)

- [Certificati Android Auto pubblicati da Android UAMP](https://github.com/android/uamp/blob/main/common/src/main/res/xml/allowed_media_browser_callers.xml)

