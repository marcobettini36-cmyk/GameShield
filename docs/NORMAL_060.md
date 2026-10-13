# Normal 0.6.0 / codice 9

Base verificata: main c14d1ba, identica a origin/main al fetch del 14 ottobre
2026. Intervento confinato al flavor Normal, alle sue prove, alla sua
configurazione di build e documentazione. Nessun cambiamento a Strong o al
motore VPN condiviso, alle liste o alle patch native.

## Interfaccia e guida

Nuovo launcher NormalHomeActivity nel solo manifest Normal. I collegamenti
legacy alla MainActivity (per esempio notifiche del servizio condiviso) vengono
inoltrati al nuovo launcher dal provider Normal. Il launcher Strong è invariato.
Tema scuro antracite, smeraldo, testo ad alto contrasto, pulsanti arrotondati con
ripple, dimensioni in sp/dp, ScrollView e inset del sistema. Le schermate Normal
esistenti ricevono il tema comune senza modificare i controlli di sicurezza.

Configurazione iniziale e richiamabile: otto passaggi, avanzamento persistente,
salto e ripresa; nessun consenso assegnato automaticamente. Ritorno dalle
impostazioni: controlli del tunnel effettivo, servizio contenuti e regole,
amministratore Android, periodo e codice fidato. Always-on e blocco senza VPN
sono verificati solo se l'API del servizio restituisce un risultato; altrimenti
restano Da verificare. La persona fidata non è obbligatoria. Le notifiche del
supervisore si richiedono tramite un pulsante esplicito.

ATTIVA richiede VPN reale, connettività verificata dal motore esistente,
Accessibility con consenso/servizio/regole e un test DNS recente positivo.
PARZIALE indica che uno o più livelli operano ma gli altri non sono dimostrati;
DISATTIVATA indica che nessuno dei tre livelli operativi è dimostrato.
Amministratore, periodo e persona fidata sono facoltativi e separati dallo stato.
Non viene usata la dicitura Protezione completa. ATTIVA non equivale a copertura
assoluta. La verifica DNS vale per un solo campione, 15 minuti, rete e hostname
invariati e stesso boot; non verifica le preferenze o la quota di un account.

## DNS selettivo

La UI non raccomanda più clean.dnsforge.de. Guida volontaria NextDNS:
creare/selezionare configurazione, Parental Control → Gambling, Porn disattivata;
controllare anche categorie, Privacy/blocklist, SafeSearch e regole personali.
Nessuna lettura delle impostazioni NextDNS e nessuna certificazione di assenza
di altri filtri. Campo hostname DNS-over-TLS personale, validazione locale,
salvataggio dichiarato, copia e impostazioni Android. Rifiuto di URL, credenziali,
indirizzi IP e resolver NextDNS generici. Nessuna credenziale conservata.

Provider personalizzati restano possibili. Nessun account NextDNS creato dal
programma e nessun acquisto. Prove reali NextDNS selettive richiedono un profilo
personale; le prove del resolver pubblico nelle fixture non certificano quel
profilo. La fixture dnsforge esistente è mantenuta esclusivamente per verificare
il test del DNS personalizzato esterno e non costituisce una raccomandazione UI.

Test esistente indipendente dalla VPN: rifiuta qualsiasi VPN attiva, osserva
Private DNS Android validato, risolve Google/Wikipedia/GitHub, connette HTTPS
a Wikipedia e misura un dominio bloccato dalle regole GameShield. Nessun HTTPS
al sito gambling, nessun arresto automatico VPN o scrittura di impostazioni.
NXDOMAIN/sinkhole non dimostrano da soli la copertura totale o la categoria
configurata: il dominio può essere inesistente e va riprovato su campioni attivi.

Fonti ufficiali consultate il 14 ottobre 2026:

- [NextDNS prezzi](https://nextdns.io/pricing): 300.000 query/mese gratuite;
  superamento quota → risoluzione senza filtro. Nessuna promessa illimitata.
- [NextDNS oltre la quota](https://help.nextdns.io/t/p8hmvaw/what-happens-after-300k-queries).
- [Configurazione personale](https://my.nextdns.io/).

## Conservazione e limiti

Restano VPN, blacklist/manuali, intervento contenuti, periodo 7/30/90,
attesa interna, PIN, emergenza, recupero, SMS facoltativi nell'APK e assenza
SEND_SMS nel Play. Android può revocare i permessi. Accessibility non blocca
Impostazioni/disinstallazione. Nessuna nuova API Knox, Device Owner o VPN.
Arresto forzato/disinstallazione, Doze, OEM, browser/DoH/IP/ECH e nuove app
possono limitare la protezione. Strong resta congelata alla baseline.

## Verifica e distribuzione

Prove JVM hostname/status, isolamento Python di tutti i sorgenti condivisi e
Strong, regressione Android 15/16 con tunnel effettivo e riavvio; nuove prove
di navigazione, ripresa/ricreazione wizard, hostname dichiarato non verificato,
schermo piccolo/font 150%, screenshot reali da UiAutomation. I report Actions
distinguono fallimenti e skip. Non si dichiara una prova su Samsung fisico.

APK debug con chiave debug locale già esistente. AAB Play release senza SMS,
unsigned in assenza della chiave upload originale; nessuna chiave sostitutiva.
Per Play occorrono firma originale, controllo versionCode Console e revisione
Accessibility/FGS. Nessuna pubblicazione automatica.
