# GameShield 0.3.0 — verifica forwarding VPN

Sorgente verificato: `7faf35be094a615b7ce8ebdfe076a52f3e3f00aa`. [Build e test Android riusciti](https://github.com/marcobettini36-cmyk/GameShield/actions/runs/37193692116).

- Quattro APK Normal/Strong debug/release compilati; lint entrambe le edizioni superato.
- 82 test JVM superati (41 per edizione), 5 test Python superati.
- Confronto del relay reale: 0.2.0 fallisce 3 regressioni (server-first, upload dopo FIN, nona destinazione UDP); 0.3.0 passa tutti i 14 test SOCKS TCP/UDP. Log allegati.
- Android 15 API 35 Google Play: preflight nativo e suite completa in entrambe le edizioni con Private DNS Off/Automatico. Report strumentazione e logcat disponibili negli Artifacts.
- Verificati DNS/TCP 443/HTTPS google.it, youtube.com, wikipedia.org, github.com; navigazione WebView e Chrome su Google; DNS e TLS con IP cached Playzilla/Excitewin; bet365/stake; dominio utente example.com via RELOAD.
- Tre minuti di HTTPS concorrente per combinazione edizione/Private DNS, senza rimozione o sostituzione della VPN; più cicli di self-test. Peer fisico controllato verifica FIN, download e upload completi, senza reset.
- Google Play: endpoint HTTPS e avvio app; screenshot allegati. Nessun account configurato, quindi acquisti/download autenticati non verificati.
- Guasto relay: un fallimento mantiene Normal; tre fallimenti consecutivi con HTTPS fisico funzionante rimuovono Normal e ripristinano navigazione. Strong mantiene TUN dopo lo stesso guasto confermato.
- APK debug firmati con la chiave locale di test; release non firmati. Tre ABI e allineamento ELF/ZIP a 16 KB verificati. Lista invariata in questa correzione, verificata byte per byte negli APK.

Il Samsung S23 Ultra non è collegato: non dichiaro verificata la soluzione sul suo firmware. Samsung Internet, Chrome Secure DNS, rete fisica solo IPv6, cambi rete OEM e provisioning/lockdown Device Owner restano verifiche specifiche del dispositivo.

Difetti individuati nel percorso reale: IPv6 annunciato dal TUN su una rete fisica senza connettivita IPv6, con handshake sintetico scelto dal browser e successivo reset; chiusura completa del relay su FIN unidirezionale; abort/RST nativo anche dopo trasferimenti conclusi; perdita di byte durante classificazione incompleta e limite UDP troppo basso. Corrette anche la chiusura esplicita del TUN, la distinzione tra registrazione iniziale e percorso pacchetti pronto, il rilascio del descrittore solo dopo join JNI, la pulizia dei PCB al riavvio e i timer SYN/FIN/TIME_WAIT. Normal OFF viene verificata con DNS e socket ordinari non protetti e non legati a una rete secondaria.

La correzione interviene nel relay Java, nel routing delle famiglie IP e nella chiusura TCP nativa. Non usa MITM e non disattiva subito la VPN per nascondere un errore. Dettagli e limiti in FORWARDING_03.md. Log domini/trasporto temporanei nei debug APK: 15 minuti, senza payload o codici custode.
