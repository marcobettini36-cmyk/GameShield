# Analisi Data Safety — richiede verifica del titolare

Solo Normal Play 0.6.1. Considerare anche tutte le versioni ancora distribuite nelle tracce Play.

| Dato | Uso | Destinazione/conservazione |
|---|---|---|
| DNS, IP, porte, HTTP Host/TLS SNI visibili | Decisione filtro e inoltro | Locale; richieste consentite a siti/resolver configurati |
| Package foreground e barra indirizzo | Accessibility consensuale | Locale, nessun archivio cronologia |
| Domini manuali, hostname DNS, stati e periodi | Configurazione/diagnosi | Preferenze e file privati, backup escluso |
| PIN/codice di rilascio | Verifica locale | Hash salato, nessun PIN nei log |
| Download blacklist | Aggiornamento richiesto | HTTPS GitHub; servizio vede normale richiesta/IP |
| Probe DNS/HTTPS | Verifica rete | Resolver, Google/Wikipedia/GitHub e provider DNS ricevono normali richieste |

Nessun SDK pubblicitario/analytics identificato: AndroidX Car App e transitive AndroidX; runner/JUnit solo test. Nessun account GameShield, upload della cronologia, contatti, foto, posizione o SMS automatico. Log Android di stato/risultati tecnici; logging dettagliato trasporti solo DEBUG. Nessun invio crash log a GameShield.

Non selezionare automaticamente "nessun dato raccolto": la definizione Play comprende trasmissione fuori dispositivo. Valutare IP e metadati verso GitHub/provider e loro conservazione. L'elaborazione effimera esterna va indicata nel modulo e non è dimostrata per tutti i provider dal codice Android. Applicare le eccezioni ufficiali a inoltro/navigazione caso per caso. Non dichiarare tutto il traffico cifrato: HTTPS resta cifrato, HTTP/DNS tradizionale possono non esserlo. La risposta cifratura riguarda i dati dichiarati e va verificata.

NextDNS è opzionale, configurato fuori app: account/log dipendono dal provider. La disinstallazione elimina dati locali, la revoca DNS è separata. Completare titolare/contatti e pratiche dei provider prima di approvare le risposte. Nessun modulo Console inviato.

[Istruzioni ufficiali Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
