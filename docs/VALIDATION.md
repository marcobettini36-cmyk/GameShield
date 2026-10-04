# GameShield 0.2.0 — verifica della correzione VPN

Verificato il 2026-10-04 02:36 +0200 (Europe/Rome).

- Causa primaria riprodotta su Android: nuovo Socket TCP senza fd nativo valido, protect(Socket) restituiva false e il relay resettava ogni connessione. Corretto bind locale effimero prima di protect e connect. DNS UDP era già inizializzato.
- Build Android Normal debug/release e Strong debug/release: riuscite, AGP 8.10.1, Gradle 8.11.1, JDK 17, SDK 35, NDK 28.2.13676358.
- 74 test JVM superati (37 per variante): domini, parsing completo della lista inclusa, pacchetti DNS/CNAME, codice custode, TLS SNI e connessioni reali SOCKS TCP/UDP. Zero errori/fallimenti.
- 5 test Python superati per la normalizzazione e l'estrazione della directory ADM.
- Lint di entrambe le varianti superato, zero errori. Avvisi non bloccanti per versioni fissate degli strumenti, scritture sincrone di sicurezza e localizzazione italiana.
- APK debug firmati con chiave locale di test e verificati con apksigner. APK release non firmati, da firmare con la chiave stabile del proprietario prima dell'installazione.
- Tre ABI: arm64-v8a, armeabi-v7a, x86_64. Verificati allineamento ELF a 16 KB e allineamento ZIP APK a 16 KB.
- Asset di ciascuno dei quattro APK verificato byte per byte rispetto al feed pubblicabile.
- Blacklist: 586,653 domini unici. Fonti: HaGeZi (578,451), ADM inibiti (12,072), ADM autorizzati (52) e regole curate. Nessun errore di acquisizione. Non garantisce ogni nuovo dominio/mirror.
- Android 11 API 30 su runner GitHub KVM: 12 test di integrazione superati (3 test × 2 edizioni × Private DNS Off/Automatico). Verificati TUN HEV nativo, DNS Android/UDP, TCP 443, HTTPS Google/Wikipedia/GitHub e provider DoH, DNS Playzilla/Excitewin/bet365/stake, TLS con IP cached, WebView Chromium, fail-open Normal e TUN mantenuto Strong su guasto del relay. Chrome, Samsung Internet e Samsung S23 Ultra NON testati direttamente. Applicazione/rilascio Device Owner e lockdown OEM ancora da collaudare.
- GitHub: progetto pubblicato su [marcobettini36-cmyk/GameShield](https://github.com/marcobettini36-cmyk/GameShield). [Build Actions riuscita](https://github.com/marcobettini36-cmyk/GameShield/actions/runs/37164797794): test, lint e quattro APK, con artifact Normal, Strong, release non firmati e report disponibili.
- [Aggiornamento automatico delle blacklist verificato](https://github.com/marcobettini36-cmyk/GameShield/actions/runs/37160598896): acquisizione, test e pubblicazione sul branch main riusciti; esecuzione giornaliera configurata alle 04:21 UTC. La versione 0.2.0 rimuove i nove provider DNS erroneamente inclusi nella lista; la generazione esclude quei provider anche nei futuri aggiornamenti.

## Contenuto

GameShield-source.zip include codice app, Gradle wrapper, workflow Actions, snapshot delle liste, licenze e sorgenti nativi completi con dipendenze. Esclude cache, SDK, chiavi e credenziali. Normal richiede Android 10+, Strong Android 11+ e provisioning Device Owner.

I limiti comprendono domini non censiti, IP diretti, DoH combinato con ECH/SNI non visibile, TLS/QUIC frammentato e tunnel applicativi. Nessun blocco indiscriminato di UDP/443 o 853. Self-test di connettività post-avvio e periodico; Normal rilascia VPN su errore, Strong mantiene TUN. Device Owner non impedisce root, recovery/reflash o bootloader sbloccato. Una sola VPN può essere attiva.
