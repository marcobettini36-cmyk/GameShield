# Procedura chiave upload — nuova chiave solo dopo autorizzazione

Cercare prima chiave originale e backup. Non usare debug.keystore. Per app già in Play senza chiave seguire recupero/reset upload ufficiale, non sostituirla silenziosamente. Confrontare certificato upload in Integrità app / Firma app Console.

Solo per prima pubblicazione DOPO autorizzazione: Android Studio → Generate Signed Bundle / APK → Android App Bundle → Create new. Percorso privato esterno al repository, alias upload, password lunga unica in password manager. Alternativamente keytool con password interattive, mai riga comando/chat/log. RSA >=2048 e validità lunga secondo guida ufficiale. Nessuna chiave generata da questa preparazione.

Due backup cifrati in luoghi distinti, almeno uno offline. Password separata, alias e certificato pubblico SHA-256; verificare apertura backup. Mai repository, Artifacts o cartelle condivise. La perdita può impedire aggiornamenti; Play dispone di reset upload, non recupero del file privato.

Creare release.properties privato FUORI repository e outputs:

```properties
storeFile=C:/percorso/privato/gameshield-upload.jks
keyAlias=ALIAS_REALE
storePassword=DA_COMPILARE_LOCALMENTE
keyPassword=DA_COMPILARE_LOCALMENTE
```

Esempio non utilizzabile: compilare localmente, non inviare in chat. Impostare GAMESHIELD_UPLOAD_PROPERTIES al percorso o passare uploadPropertiesFile. Eseguire bundleNormalRelease con normalPlayRelease=true. Verificare jarsigner e SHA-256 keytool, confrontare Console ed escludere certificato debug.

[Firma Android ufficiale](https://developer.android.com/studio/publish/app-signing)
