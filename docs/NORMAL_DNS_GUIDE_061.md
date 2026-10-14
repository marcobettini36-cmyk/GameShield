# Normal 0.6.1 / code 10 — guida DNS

Base: main 7f66c9d (Normal 0.6.0/code 9). Solo UI DNS e test Normal; motore VPN,
probe DNS, manifest/permessi, servizi, regole, Strong e impostazioni di protezione invariati.

Sei passaggi persistenti: account esterno, categorie, nome host personale,
salvataggio/copia/incolla, impostazioni Android, verifica. Bozza e passaggio
restano dopo uscita/ricreazione. I primi tre indicatori sono dichiarazioni
dell'utente: non verifiche dell'account. Android è osservato tramite LinkProperties;
la prova effettiva rimane il probe esistente, senza interrompere VPN o cambiare DNS.
Nessuna credenziale NextDNS richiesta. Schemi vettoriali originali indicativi,
esplicitamente distinti da schermate autentiche del sito esterno.

La pagina con le istruzioni Android è Configurazione / Setup, non la scheda
Settings che contiene altre impostazioni del profilo. Interfaccia web e
traduzioni possono variare. Non pretendiamo di aver ispezionato un account privato.

Il piano gratuito permette 300.000 query al mese, poi risponde senza filtrare:
https://nextdns.io/pricing. Portale: https://my.nextdns.io/.

Il nuovo logo non è incluso: l'allegato contiene solo testo e nel repository
non è presente il file originale dello scudo descritto. Nessuna immagine generica
è stata creata. Le icone attuali restano finché viene fornito il riferimento.

Release Play senza SEND_SMS. APK debug conserva SMS opzionali. AAB unsigned,
in attesa della chiave upload originale e della review Play; nessuna nuova chiave
o pubblicazione. Il logo richiesto e il relativo controllo adaptive/safe zone
restano da eseguire quando sarà disponibile l'immagine.
