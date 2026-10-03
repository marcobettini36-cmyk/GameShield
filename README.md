# GameShield

App Android per ridurre l'accesso al gioco d'azzardo online, con due APK: **Normal** (Android 10+) e **Strong** (Android 11+, dispositivo dedicato provisionato come Device Owner).

## Funzioni

- VPN locale completa IPv4/IPv6 con motore tun2socks HEV, TCP e UDP; nessun server VPN remoto.
- DNS locale: domini e sottodomini bloccati con NXDOMAIN, verifica CNAME/DNAME, DNS UDP e TCP. Prima usa i DNS della rete fisica, poi Cloudflare/Quad9; fallback TCP anche su timeout UDP. I socket sono protetti e vincolati alla rete fisica prima della connessione.
- Filtro HTTP Host, TLS SNI su 443/853 e SNI del ClientHello QUIC Initial v1/v2 quando completo. QUIC, DoT/DoQ e provider DoH consentiti: nessun blocco globale per porta. Non vengono decifrati i contenuti HTTPS.
- Self-test con socket esplicitamente vincolati alla rete VPN: DNS Android, TCP 443 e HTTPS con certificato verificato per google.com, wikipedia.org e github.com, dopo avvio e ogni minuto. Esito visibile nella dashboard. Normal chiude il TUN e disattiva il filtro entro 30 secondi dal fallimento del controllo; Strong conserva il TUN e segnala l’errore. Normal non supporta always-on/lockdown e non riparte dopo un errore di inoltro.
- Blacklist GPLv3 HaGeZi Gambling, domini italiani curati e importazione dei PDF ADM quando disponibili. Snapshot inclusa nell'APK; aggiornamenti HTTPS ogni 12 ore mentre la VPN è attiva; sostituzione atomica e mantenimento dell'ultima lista valida in caso di errore.
- Acquisizione della directory paginata ADM dei siti autorizzati e dell'elenco TXT/PDF dei siti inibiti; snapshot e stato di ogni fonte nei metadati.
- Aggiunta locale di domini/mirror e verifica della lista. Nessuna eccezione che permetta al normale utente di rimuovere blocchi Strong.
- Notifica persistente, ripresa dopo riavvio, statistiche aggregate per sessione, collegamento all'autoesclusione ADM.
- Strong: anti-disinstallazione Device Owner, always-on/lockdown, restrizioni debug/modifica VPN/safe boot/ripristino dalle impostazioni/nuovi utenti; codice custode PBKDF2-HMAC-SHA256 (210000 iterazioni, sale casuale), confronto costante, attese crescenti dopo errori e rilascio offline.

## Build riproducibile

```sh
git clone --recurse-submodules https://github.com/marcobettini36-cmyk/GameShield.git
cd GameShield
# JDK 17, SDK Android 35, NDK 28.2.13676358; local.properties con sdk.dir se necessario
./gradlew testNormalDebugUnitTest testStrongDebugUnitTest lintNormalDebug lintStrongDebug assembleNormalDebug assembleStrongDebug
```

Su Windows usare `gradlew.bat`. Gradle materializza gli header linkati del submodule su Windows. Il wrapper è Gradle 8.11.1, AGP 8.10.1; il motore è fissato al commit `2cdc169a248ced7097a7931aea5bf81540dc7759` e le sue dipendenze ai gitlink di quel commit. ABI arm64-v8a, armeabi-v7a e x86_64; allineamento nativo a 16 KB.

Gli APK debug sono in `app/build/outputs/apk/{normal,strong}/debug/`. GitHub Actions esegue test, lint e build di entrambe le varianti su ogni push/PR e offre APK come artifact. Un secondo job avvia Android 11 API 30 e verifica il TUN nativo, navigazione consentita, domini bloccati, Private DNS Off/Automatico e guasti del relay per entrambe le edizioni; i log sono nell’artifact native-tunnel-device-reports. Gli APK release sono **non firmati**: prima della distribuzione firmarli con una chiave stabile custodita dal proprietario. Le chiavi debug servono ai test e possono cambiare tra runner CI: non usarle per una flotta Strong.

## Blacklist e nuovi mirror

```sh
python -m pip install -r scripts/requirements.txt
python -m unittest discover -s scripts -p 'test_*.py' -v
python scripts/update_lists.py
```

`feeds/metadata.json` riporta conteggio, hash e avvisi su fonti non disponibili. ADM può rifiutare lo scraping o cambiare struttura: l'ultimo snapshot viene conservato; non si dichiara un aggiornamento ADM riuscito quando non lo è. La lista curata include operatori italiani e internazionali senza classificare il loro status di licenza. HaGeZi copre ulteriori domini e mirror, ma **non esiste una garanzia di copertura di tutti i domini nuovi**.

Il workflow giornaliero pubblica gli snapshot validati sul branch principale e avvia nuove build tramite `workflow_run`. Una riduzione superiore al 30% della fonte comunitaria interrompe l'aggiornamento; i guasti ADM conservano lo snapshot precedente e sono registrati nei metadati. Le protezioni del branch devono consentire il commit dell'automazione; in caso contrario il job fallisce senza perdere la lista pubblicata. Si può aggiornare manualmente `feeds/curated.txt` e rigenerare con `--offline`. `feeds/bypass.txt` contiene esclusioni di migrazione: i provider DNS vengono rimossi dalla lista gambling e dalle vecchie cache, non bloccati.

## Provisioning Strong (custode)

Usare un dispositivo dedicato, senza account, appena ripristinato. Effettuare prima prove con un emulatore/dispositivo di test e mantenere il codice custode fuori dal dispositivo protetto. Non installare Normal e Strong insieme per usarle simultaneamente: Android consente una sola VPN attiva.

```sh
adb install app-strong-debug.apk
adb shell dpm set-device-owner it.gameshield.strong/it.gameshield.AdminReceiver
```

Il custode imposta e conferma un codice di almeno 8 caratteri, attiva la VPN e verifica navigazione consentita e blocco di domini. Solo allora seleziona **Applica protezioni Device Owner**. Il consenso VPN e l'attivazione Strong sono espliciti. Strong non può trasformare un normale dispositivo già configurato in Device Owner senza provisioning.

Il **Rilascio del custode** con codice corretto elimina restrizioni, always-on, anti-disinstallazione e Device Owner, poi ferma la VPN. Il rilascio funziona offline ed è intenzionalmente definitivo; per riattivare Device Owner servirà nuovo provisioning. Un codice perso non dispone di backdoor di recupero. Lockdown può interrompere Internet in caso di guasto del tunnel; l'app e il rilascio restano accessibili.

## Limiti e verifiche su dispositivo

DNS cifrato combinato con ECH/SNI non disponibile, ClientHello QUIC frammentati tra datagrammi, SNI diviso tra record TLS, tunnel applicativi, IP diretti e nuovi domini non censiti possono aggirare il filtro. QUIC consentito non richiede fallback TCP. Private DNS Automatico può ripiegare sul DNS locale; la modalità stretta e Secure DNS dei browser non sono interrotti deliberatamente, ma possono ridurre la visibilità del filtro DNS. Non è implementata ispezione contenuti HTTPS, classificazione AI o scoperta attiva di domini. VPN concorrenti non sono supportate. Non promette anti-disinstallazione assoluta contro root, bootloader sbloccato, recovery o reflash; restrizioni OEM vanno provate.

La compilazione e i test JVM non certificano il comportamento sul dispositivo. Prima dell'uso reale seguire `docs/DEVICE_TESTS.md`, inclusi cambio rete, riavvio, DNS cifrato, lockdown, codice errato e rilascio. Il proxy ha limiti di concorrenza (64 sessioni native, massimo 192 worker e 4 worker DNS con coda limitata); non è un gateway general purpose ad alte prestazioni.

## Privacy e licenze

Traffico inoltrato dal dispositivo direttamente alle destinazioni; nessuna cronologia di domini persistente, telemetria o account. I resolver DNS vedono le richieste consentite e il feed HTTPS contatta GitHub. Codice custode salvato solo come hash salato nello storage privato; backup Android disabilitato. Nessun certificato CA installato.

Codice GameShield e lista HaGeZi: GPL-3.0, vedi `LICENSE` e `THIRD_PARTY_NOTICES.md`. La disponibilità dei blocchi non sostituisce autoesclusione e supporto professionale.
