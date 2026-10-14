# Dichiarazioni da adattare ai moduli Console

## VPN

Filtro/firewall locale gambling: VpnService intercetta DNS e traffico, confronta hostname visibili e inoltra le connessioni consentite. Nessun endpoint VPN remoto GameShield, MITM TLS o monetizzazione del traffico. Il tunnel termina nel processo locale: non dichiarare una VPN remota cifrata che il codice non implementa. Giustificare il caso d'uso filtro/firewall alla revisione; la sua accettazione non è garantita. La disclosure compare nei due percorsi di avvio prima del consenso Android.

Preparare video <=90 secondi: apertura, disclosure completa, annullamento, consenso Android, connessione consentita e dominio bloccato. Non presentare il prodotto come enterprise o parental control senza queste funzionalità.

## Accessibility

isAccessibilityTool=false: non è uno strumento dedicato alle disabilità. Consenso separato per nome package foreground e testo del solo controllo indirizzo dei browser supportati. Regole deterministiche di catalogo/blacklist, una singola azione HOME per candidato gambling. Nessun agente AI, enumerazione completa delle app, lettura messaggi/password, screenshot, archivio o trasmissione cronologia. I package sistema sono esclusi. Non ostacola disinstallazione, Impostazioni o revoca del servizio. Allegare video disclosure, attivazione manuale, intervento e revoca Android.

La policy vieta impedire disattivazione/disinstallazione tramite Accessibility in un'app autonoma come questa. Non dichiarare il livello come protezione anti-rimozione.

## Device Administrator

Legacy volontario, uses-policies vuoto: niente wipe, blocco schermo, Device Owner o restrizioni amministrative di rete. Android richiede revoca prima della rimozione. L'app permette questo percorso senza imporre l'attesa alle impostazioni Android.

## Foreground specialUse

ShieldVpnService: filtro locale continuo richiesto dall'utente, notifica di stato. NormalMonitorService: supervisione facoltativa richiesta dall'utente, notifica visibile e ripristino limitato con consenso VPN valido. Sospenderli può interrompere filtro/ripristino. Presentare entrambi gli usi specialUse con video e motivazione; accettazione soggetta a revisione.

## Permessi

INTERNET: traffico consentito, DNS/probe, feed GitHub. ACCESS_NETWORK_STATE: rete/diagnosi. FOREGROUND_SERVICE e FOREGROUND_SERVICE_SPECIAL_USE: servizi visibili. POST_NOTIFICATIONS: notifica protezione, richiesta runtime Android 13+. RECEIVE_BOOT_COMPLETED: ripristino consentito nei limiti Android. BIND_VPN_SERVICE, BIND_ACCESSIBILITY_SERVICE, BIND_DEVICE_ADMIN e BIND_JOB_SERVICE proteggono componenti, non sono richieste runtime. Queries limitate a Android Auto. Nessun QUERY_ALL_PACKAGES, READ_SMS, RECEIVE_SMS o SEND_SMS. Verificare anche permessi transitivi nel Manifest finale.

## Fonti ufficiali verificate il 14 ottobre 2026

- [VpnService](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en)
- [Accessibility e API sensibili](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)
- [Accessibility: automazioni deterministiche e disclosure](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- [Disclosure e dati](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Foreground services](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
- [Target API 36](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Pagine native 16 KB](https://developer.android.com/guide/practices/page-sizes)
