# Normal 0.5.0: protezione autonoma multilivello

Analisi e implementazione: 13 ottobre 2026. Base stabile Normal 0.4.0 `d1d495e`,
aggiornata al feed upstream `49b7ed7` prima delle modifiche. Versione Normal 0.5.0,
code 8. Strong conserva versione 0.3.2/code5 e tutti i sorgenti condivisi/Strong.

## Analisi prima della modifica

Normal 0.4 ha un TUN con relay nativo TCP/UDP, socket outbound protetti, filtro
DNS/HTTP Host/TLS SNI/QUIC, RuleStore atomico e regole manuali. Controllo di
connettività e recovery limitato non vanno sostituiti. Il flusso interno ha attesa
24h, countdown60s, codice casuale, PIN opzionale, conferma, persona fidata e
emergenza. Supervisor/jobs/boot e SMS opt-in esistono già. Il problema residuo è
che il filtro di rete non opera quando il TUN non è attivo.

L'analisi pubblica dei concorrenti non è un audit dei loro binari:
- GamBlock dichiara assenza VPN, browser/euristiche e protezioni Samsung. Divergence
  non Samsung richiede reset, escluso qui. API proprietarie non dimostrate:
  https://www.gamblock.com/questions/gamblock-mobile-for-android-phones/can-gamblock-be-disabled-by-removing-vpns.html
  https://www.gamblock.com/gamblock-android-divergence-featues.html
- Gamban documenta VPN, Accessibility, admin e background; niente deduzioni sui
  suoi privilegi da una promessa anti-rimozione:
  https://kb.gamban.com/en-gb/8-installing-gamban/26-installing-gamban-on-an-android-device
- BetBlocker dichiara VPN/Accessibility/admin. Il tutorial storico sul polling ogni
  millisecondo non viene copiato né attribuito alla release attuale:
  https://play.google.com/store/apps/details?id=mobile.betblocker
  https://betblocker.org/android-vpn/

UsageStatsManager rileva eventi/package, non interrompe da solo un'app o un URL;
polling continuo e un ulteriore accesso speciale non offrono qui un vantaggio:
non implementato e nessun PACKAGE_USAGE_STATS richiesto. Accessibility è usato
solo per una regola deterministica a consenso esplicito: app/hostname gambling
supportato → avviso e Home. Non controlla impostazioni, revoca o disinstallazione.
Non dichiariamo isAccessibilityTool; la pubblicazione necessita dichiarazione e
review Google Play. La presenza di Gamban/BetBlocker nello Store non è una
certificazione per GameShield.

Fonti tecniche/policy:
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
https://developer.android.com/reference/android/app/usage/UsageStatsManager
https://support.google.com/googleplay/android-developer/answer/10964491?hl=en
https://support.google.com/googleplay/android-developer/answer/18258653?hl=en

## Intervento indipendente dalla VPN

`NormalContentService` opera sul servizio Accessibility Android, non crea TUN,
non modifica routing/socket, non richiede overlay generale SYSTEM_ALERT_WINDOW.
Eventi finestra/contenuto/testo debounced; niente polling di Usage Access.
Catalogo esatto di sei package bet365/Betfair/William Hill, con URL Play verificati in
`src/normal/assets/gambling-apps.txt`. Non si bloccano nomi simili, categorie
finanziarie, autenticatore bet365, banche o pagamenti. Aggiornare questo catalogo
tramite una nuova versione dell'app; non viene scaricato codice/regole da terzi.

Per Chrome stabile/beta e Samsung Internet stabile/beta controlla esclusivamente
ID del campo indirizzo supportato. Riutilizza `RuleStore`/`DomainRules`, cache
aggiornata e regole manuali; ricarica dopo `custom`/`updated`. Non classifica testo
della pagina, non ispeziona credenziali o screenshot, non fa MITM. I nuovi mirror
dipendono dagli aggiornamenti della blacklist, non da euristiche inventate.

L'intervento è reattivo, può avvenire dopo caricamento/transazione e non sospende
il package o il suo traffico background. URL non visibile/ID differente, UI
sensibile, browser non supportati o permesso revocato riducono la copertura.
Ogni candidato produce un solo intervento Home; nessun loop di degradazione.
Una schermata identifica chiaramente GameShield e mantiene accesso all'emergenza.
Nessun host visitato/package viene registrato nei log o trasmesso. Un contatore
di interventi riusciti esiste solo in memoria per verifica.

Le impostazioni mostrano separatamente VPN, consenso, abilitazione Accessibility,
collegamento del servizio e disponibilità delle regole: "attivo" non significa
copertura completa. Revocare il servizio non comporta una concessione silenziosa.
Un rilascio volontario completato sospende anche il consenso al livello contenuti.

## Protezione Autonoma

Opt-in 7/30/90 giorni, solo dopo verifica VPN attiva. Non riattiva permessi o
Accessibility. Scadenza persistente, monotona nello stesso boot; fallback wall
clock limitato dopo reboot come i timer 0.4. Nessuna persona fidata obbligatoria.
La scadenza non viene anticipata da una richiesta né prolungata automaticamente.
Il normale rilascio attende il massimo fra periodo restante e attesa24h della
richiesta; quindi continua con countdown/code/PIN/conferma0.4. Annullamento cancella
la richiesta, non il periodo. PIN e codice fidato non modificabili impulsivamente
durante il periodo. Il consenso SMS resta revocabile per il controllo privacy.
Emergenza/identità del proprietario e rilascio fidato restano possibili.

Il gate non vincola le impostazioni Android, la disinstallazione, l'orologio dopo
riavvio o la cancellazione dati. Può essere aggirato dal proprietario con tali
azioni. Una chiave custode obbligatoria non è stata introdotta. Dopo un rilascio
completato non si riaccende di nascosto la VPN.

## DNS privato: guida più misurazione, nessun provider imposto

Nessuna seconda VPN né WRITE_SECURE_SETTINGS. Android configura manualmente il
provider; l'app non spegne la VPN per eseguire il test e non cambia Private DNS.
Il test rifiuta qualsiasi VPN attiva, richiede LinkProperties con Private DNS
hostname validato, usa DnsResolver del sistema senza cache per google.com,
wikipedia.org, github.com e un campione della blacklist scelto dall'utente.
Verifica anche HTTPS innocuo con certificati standard. NXDOMAIN/risposte sinkhole
sono annotate come campione bloccante, non prova universale di classificazione.
La rete/hostname devono restare uguali fino alla fine. Nessun HTTPS a un sito
gambling. Un guasto/timeout è NON VERIFICATO, non protezione riuscita.

Nessun provider garantisce copertura provata di tutta la blacklist. Il wizard
propone opzionalmente `clean.dnsforge.de`, gratuito senza account secondo il
provider, con filtri gambling ma anche adulti/pubblicità/tracker/malware e SafeSearch.
Consenso prima di copiare il solo hostname; nessuna configurazione silenziosa.
Server in Germania/no logging sono dichiarazioni del provider, non audit.
Mullvad, che documenta gambling nel DNS pubblico family,
annuncia cessazione 2 novembre2026: non viene hardcoded o consigliato. Il wizard
è utilizzabile con provider scelto dall'utente e fornisce una prova locale reale;
non è una nuova blacklist remota gestita da GameShield. Liste/privacy/gratuità
appartengono al provider; hostname Automatico non significa filtro gambling.
DoH del browser, IP diretti e resolver autonomi possono bypassare il DNS.
Una risposta NXDOMAIN per un dominio già inesistente non prova un filtro.

https://mullvad.net/en/help/dns-over-https-and-dns-over-tls
https://dnsforge.de/
https://support.google.com/fi/answer/2819583?hl=en
https://developer.android.com/reference/android/net/DnsResolver

## Knox: precisazione rispetto alla ricerca generale

`AdvancedRestrictionPolicy.allowUserSetAlwaysOn(false)` esiste da API Knox30 /
Knox3.4.1, Global Scope, permission `KNOX_ADVANCED_RESTRICTION` di tipo signature.
Impedisce modifiche del menu Always-on; non prova da solo blocco di ogni percorso
di arresto VPN. La documentazione pubblica corrente NON elenca questo metodo
nella lista DA restricted da Android15: sarebbe scorretto dedurre che richieda
DO semplicemente perché firewall/anti-rimozione lo richiedono.

Knox contempla licenze KPE Premium gratuite, ma occorrono ottenimento/attivazione,
autorizzazioni Samsung e dispositivo Knox supportato. Dichiarare il permesso nel
manifest ordinario non basta. Non è disponibile una licenza/autorizzazione
GameShield consumer né una verifica S23 Ultra/Android16. Non è dimostrato che il
caso richiesto possa imporre quel vincolo senza tali integrazioni. Non affermiamo
che ogni integrazione autorizzata di terzi sia impossibile; non implementiamo
un'API non autorizzata o un finto pulsante. Nessuna dipendenza Knox aggiunta.
Firewall/domain filter e setAdminRemovable(false) restano DO/PO da Android15.

https://docs.samsungknox.com/devref/knox-sdk/reference/com/samsung/android/knox/restriction/AdvancedRestrictionPolicy.html
https://docs.samsungknox.com/dev/knox-sdk/api-reference/restricted-api-methods/
https://docs.samsungknox.com/dev/knox-sdk/faq/licensing/
https://docs.samsungknox.com/admin/knox-platform-for-enterprise/before-you-begin/knox-platform-for-enterprise-licenses/

## Android15/16, test e bypass da dichiarare

Android15 force-stop cancella PendingIntent e mantiene lo stato stopped fino a
interazione dell'utente. Android16 impone quote jobs anche con FGS; viste sensibili
possono essere inaccessibili ai servizi non accessibility tool. Samsung battery
sleep può interrompere componenti. Nessuna garanzia dopo force-stop/disinstallazione.

https://developer.android.com/about/versions/15/behavior-changes-all
https://developer.android.com/about/versions/16/behavior-changes-all
https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data

Test aggiunti: matching package esatto/esclusione sistemi e pagamenti, hostname
vs testo/query, periodi/date; test strumentali reali del servizio su fixture APK
separato, senza VPN, consenso revocato, Settings accessibili, gate/annullamento/
emergenza/scadenza e rifiuto del test DNS su Automatico. La fixture non è una app
gambling reale; la regola fixture è DEBUG-only, rimossa dalla Release con R8.
CI usa emulatori Android35/36; non è un test su Samsung fisico, SIM reale,
Knox o scadenza dopo90giorni reali. Gli esiti effettivamente ottenuti sono riportati
nel rapporto di consegna, non presunti da questo piano.

Bypass residui: revoca Accessibility/consenso VPN, force-stop, disinstallazione
dopo rimozione admin, cancellazione dati, altri utenti/profili, package regionali
non catalogati, browser/UI non supportati, vista sensibile, mirror non in lista,
resolver DNS autonomo/DoH, IP diretti, ripristino e orologio cambiato dopo reboot.
Emergenza è deliberatamente accessibile, non una porta nascosta. Non c'è
prevenzione universale della transazione o del traffico background.

APK debug diretto: SMS opt-in0.4 invariati. AAB Play: SEND_SMS/receiver/send APIs
esclusi dal confezionamento esistente. Nessuna pubblicazione Play automatica.
La release richiede la chiave upload originale; nessuna nuova chiave creata.

Comandi: gradle -PnormalLayerFixtures=true testNormalDebugUnitTest
testStrongDebugUnitTest lintNormalDebug assembleNormalDebug
assembleNormalDebugAndroidTest :normalFixture:assembleDebug; poi profilo
-PnormalPlayRelease=true testNormalReleaseUnitTest lintNormalRelease bundleNormalRelease.
Il module fixture non viene incluso senza la property esplicita.
`scripts/test_normal_isolation.py` continua a proteggere tutti i sorgenti common/
Strong e i native-patches con hash precedenti. Non si aggiorna la baseline.
