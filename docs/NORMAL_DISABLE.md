# Disattivazione Normal — 0.3.1

Modifica limitata alla UI Normal: MainActivity apre NormalDisableActivity al posto dello STOP immediato. Nessuna modifica a ShieldVpnService, proxy, routing, DNS, blacklist o policy Strong. Strong mantiene versione 0.3.0 e il flusso precedente. Le schermate e il PIN sono compilati soltanto nel flavor Normal.

## Flusso

1. Avviso esplicito e countdown reale di 60 secondi, su clock monotono Android. Continua e input disabilitati durante l’attesa; Annulla e Back sempre disponibili.
2. Codice casuale a 6 cifre, generato con SecureRandom, differente dalla richiesta precedente. Il codice diventa visibile dopo l’attesa.
3. PIN opzionale, se configurato, richiesto insieme al codice dopo l’attesa.
4. Conferma finale Mantieni protezione / Disattiva protezione. Solo il secondo pulsante, dopo tutte le verifiche, invia lo STOP già esistente e azzera wanted. Nessuna riattivazione aggiunta.

Annullamento, Back e uscita in background terminano la richiesta, senza fermare la VPN. La richiesta successiva ricomincia da 60 secondi. La rotazione conserva deadline/codice, ma scarta input e autorizzazioni: se avvenuta sulla conferma finale, codice e PIN devono essere verificati nuovamente. Un identificatore solo in memoria impedisce di ripristinare un countdown precedente dopo la morte del processo.

## PIN

Impostazioni Normal permette creazione, modifica e rimozione; modifica/rimozione verificano il PIN corrente. Da 6 a 12 cifre; PBKDF2-HMAC-SHA256, 210.000 iterazioni, salt casuale di 16 byte, hash di 256 bit versionato, confronto constant-time. Solo salt/hash in SharedPreferences private; backup escluso dal manifest esistente. Nessun PIN nei log, savedInstanceState, autofill o screenshot; char[] temporanei azzerati. La derivazione avviene fuori dal thread UI. Annullare una configurazione prima della conferma del risultato non salva il PIN.

Il PIN non blocca Android, disinstallazione o gestione della VPN dalle Impostazioni. Queste vie sono spiegate anche in caso di PIN dimenticato. Nessun Device Admin o Accessibility aggiunto.

## VPN sempre attiva

Voce informativa e Intent ACTION_VPN_SETTINGS. Normal attualmente dichiara SUPPORTS_ALWAYS_ON=false; questa modifica non altera il contratto del servizio e la schermata lo dichiara chiaramente. Android consente questo opt-out: https://developer.android.com/develop/connectivity/vpn . Nessuna attivazione nascosta.

Il flusso mantiene disattivazione trasparente e controllo dell’utente. Non è una certificazione Play Console: restano le dichiarazioni e il consenso VpnService previsti dalla policy ufficiale https://support.google.com/googleplay/android-developer/answer/12564964 .

## Verifiche automatiche

11 nuovi test JVM Normal: 8 per countdown/codice/cancellazione/conferma/PIN/ricreazione, 3 per validazione/hash/salt/storage malformato. Tutte le regressioni esistenti sono mantenute.

Due test Android Normal con vero servizio VPN e attese di 60 secondi: Internet durante attesa, pulsante MainActivity, codice errato/corretto, annullamento, conferma negativa/positiva, background e riapertura, ricreazione durante countdown e conferma finale, PIN errato/corretto, creazione/modifica/rimozione PIN, nessuna disattivazione accidentale. Eseguiti in CI dopo le cinque regressioni tunnel, con Private DNS Off e Automatico. Strong usa esclusivamente la suite tunnel precedente.
