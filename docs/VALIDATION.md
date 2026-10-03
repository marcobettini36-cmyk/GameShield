# GameShield — verifica locale

Verificato il 2026-10-04 00:42 +0200 (Europe/Rome).

- Build Android Normal debug/release e Strong debug/release: riuscite, AGP 8.10.1, Gradle 8.11.1, JDK 17, SDK 35, NDK 28.2.13676358.
- 62 test JVM superati (31 per variante): domini, parsing completo della lista inclusa, pacchetti DNS/CNAME, codice custode, TLS SNI e connessioni reali SOCKS TCP/UDP. Zero errori/fallimenti.
- 5 test Python superati per la normalizzazione e l'estrazione della directory ADM.
- Lint di entrambe le varianti superato, zero errori. Avvisi non bloccanti per versioni fissate degli strumenti, scritture sincrone di sicurezza e localizzazione italiana.
- APK debug firmati con chiave locale di test e verificati con apksigner. APK release non firmati, da firmare con la chiave stabile del proprietario prima dell'installazione.
- Tre ABI: arm64-v8a, armeabi-v7a, x86_64. Verificati allineamento ELF a 16 KB e allineamento ZIP APK a 16 KB.
- Asset di ciascuno dei quattro APK verificato byte per byte rispetto al feed pubblicabile.
- Blacklist: 586,662 domini unici. Fonti: HaGeZi (578,451), ADM inibiti (12,072), ADM autorizzati (52) e regole curate. Nessun errore di acquisizione. Non garantisce ogni nuovo dominio/mirror.
- Nessun dispositivo o emulatore collegato: avvio Android, traffico del tunnel sul dispositivo e applicazione/rilascio delle policy Device Owner NON verificati. Seguire la checklist prima di impiegare Strong su un dispositivo reale.
- GitHub: scrittura tramite plugin rifiutata con HTTP 403 Resource not accessible by integration. Accesso Git locale non ancora autenticato. I workflow sono inclusi nel progetto, ma nessuna esecuzione Actions è stata verificata sul repository remoto.

## Contenuto

GameShield-source.zip include codice app, Gradle wrapper, workflow Actions, snapshot delle liste, licenze e sorgenti nativi completi con dipendenze. Esclude cache, SDK, chiavi e credenziali. Normal richiede Android 10+, Strong Android 11+ e provisioning Device Owner.

I limiti comprendono domini non censiti, IP diretti, DoH personalizzato, ECH, TLS frammentato e tunnel applicativi. Device Owner non impedisce root, recovery/reflash o bootloader sbloccato. Una sola VPN può essere attiva.
