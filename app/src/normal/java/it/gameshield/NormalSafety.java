package it.gameshield;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import hev.htproxy.TProxyService;

/** Normal-only supervisor. Never changes routes, sockets, DNS or the VPN engine. */
final class NormalSafety {
    static final Object LOCK=new Object();
    static final long DAY=24*60*60_000L;
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("normal_safety",0);}
    static int boot(Context c){try{return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,0);}catch(SecurityException e){return -1;}}
    static boolean protectedNow(Context c) {
        if(!ShieldVpnService.running)return false;
        try {
            if(!TProxyService.TProxyIsRunning())return false;
            ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
            for(Network n:cm.getAllNetworks()) {
                NetworkCapabilities caps=cm.getNetworkCapabilities(n);
                if(caps!=null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return true;
            }
        }catch(RuntimeException|LinkageError ignored){}
        return false;
    }
    static boolean physicalAvailable(Context c) {
        try {
        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
        for(Network n:cm.getAllNetworks()) {
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps!=null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return true;
        }
        }catch(RuntimeException ignored){}
        return false;
    }
    static NormalDeadline deadline(android.content.SharedPreferences p,String key,long duration) {
        if(!p.contains(key+"Wall"))return null;
        return new NormalDeadline(p.getLong(key+"Wall",0),p.getLong(key+"Elapsed",0),p.getInt(key+"Boot",-1),duration);
    }
    static void putDeadline(android.content.SharedPreferences.Editor e,String key,NormalDeadline d) {
        if(d==null)e.remove(key+"Wall").remove(key+"Elapsed").remove(key+"Boot");
        else e.putLong(key+"Wall",d.wall).putLong(key+"Elapsed",d.elapsed).putInt(key+"Boot",d.boot);
    }
    static NormalEpisode episode(Context c) {
        android.content.SharedPreferences p=prefs(c);NormalEpisode e=new NormalEpisode();
        e.id=p.getLong("episode",0);e.seenProtection=p.getBoolean("seen",false);
        e.off=p.getBoolean("off",false);e.claimed=p.getBoolean("claimed",false);
        e.deadline=deadline(p,"off",NormalEpisode.SMS_DELAY);return e;
    }
    static boolean saveEpisode(Context c,NormalEpisode state) {
        android.content.SharedPreferences.Editor e=prefs(c).edit().putLong("episode",state.id)
            .putBoolean("seen",state.seenProtection).putBoolean("off",state.off).putBoolean("claimed",state.claimed);
        putDeadline(e,"off",state.deadline);return e.commit();
    }
    static boolean enabled(Context c){return prefs(c).getBoolean("recovery",false) || (BuildConfig.AUTOMATIC_SMS_ENABLED && prefs(c).getBoolean("smsConsent",false));}
    static void check(Context c) {
        try{checkInternal(c);}catch(RuntimeException e){try{prefs(c).edit().putString("monitorIssue","Verifica della supervisione non completata; il motore VPN non è stato arrestato.").apply();}catch(RuntimeException ignored){}}
    }
    private static void checkInternal(Context c) {
        c=c.getApplicationContext();
        synchronized(LOCK) {
            boolean active=protectedNow(c);NormalEpisode e=episode(c);
            boolean oldOff=e.off,oldSeen=e.seenProtection;long oldId=e.id;
            e.observe(active,System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(c));
            if(oldOff!=e.off || oldSeen!=e.seenProtection || oldId!=e.id) {
                if(!saveEpisode(c,e))return;
                prefs(c).edit().putLong("smsRetry",0).remove("smsIssue").apply();
            }
            if(active) {
                prefs(c).edit().putInt("retries",0).putBoolean("recoveryBlocked",false).remove("recoveryIssue").apply();
            } else if(prefs(c).getBoolean("recovery",false) && e.seenProtection
                && !prefs(c).getBoolean("paused",false) && !prefs(c).getBoolean("recoveryBlocked",false)) {
                recover(c);
            }
            NormalSms.maybeSend(c);
            NormalJobs.schedule(c);
        }
    }
    private static void recover(Context c) {
        android.content.SharedPreferences p=prefs(c),shield=c.getSharedPreferences("shield",0);
        if(shield.getInt("consecutiveOutages",0)>=3) {
            p.edit().putBoolean("recoveryBlocked",true).putString("recoveryIssue","Ripristino sospeso: il filtro ha rilevato un guasto reale. Apri GameShield; evita cicli che interrompono Internet.").apply();return;
        }
        if(android.net.VpnService.prepare(c)!=null) {
            p.edit().putString("recoveryIssue","Consenso VPN revocato o altra VPN attiva: riattiva da GameShield.").apply();return;
        }
        if(!physicalAvailable(c)) {
            p.edit().putString("recoveryIssue","In attesa di una rete con accesso Internet verificato.").apply();return;
        }
        long now=System.currentTimeMillis();if(now<p.getLong("recoverAfter",0))return;
        int attempts=p.getInt("retries",0);
        if(attempts>=3){p.edit().putBoolean("recoveryBlocked",true).putString("recoveryIssue","Tre tentativi falliti: apri GameShield per riprovare.").apply();return;}
        long delay=new long[]{60_000L,5*60_000L,15*60_000L}[attempts];
        if(!p.edit().putInt("retries",attempts+1).putLong("recoverAfter",now+delay).commit())return;
        try {
            c.startForegroundService(new Intent(c,ShieldVpnService.class));
            p.edit().putString("recoveryIssue","Ripristino richiesto ad Android (tentativo "+(attempts+1)+"/3).").apply();
        }catch(IllegalStateException|SecurityException ex) {
            p.edit().putString("recoveryIssue","Android richiede l'apertura dell'app per riavviare la VPN.").apply();
        }
    }
    static void startMonitor(Context c) {
        NormalJobs.schedule(c);
        if(!enabled(c))return;
        try{c.startForegroundService(new Intent(c,NormalMonitorService.class));}
        catch(IllegalStateException|SecurityException e){prefs(c).edit().putString("monitorIssue","Supervisione in background limitata da Android: apri GameShield.").apply();}
    }
    static void requestRelease(Context c) {
        synchronized(LOCK){if(deadline(prefs(c),"release",DAY)==null){android.content.SharedPreferences.Editor e=prefs(c).edit();putDeadline(e,"release",new NormalDeadline(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(c),DAY));e.commit();}}
    }
    static long releaseRemaining(Context c) {
        if(granted(c))return 0;
        NormalDeadline d=deadline(prefs(c),"release",DAY);
        long ordinary=d==null?DAY:d.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(c));
        return Math.max(ordinary,NormalAutonomy.remaining(c));
    }
    static boolean granted(Context c) {return prefs(c).getInt("grantBoot",-2)==boot(c) && SystemClock.elapsedRealtime()<prefs(c).getLong("grantUntil",0);}
    static boolean grant(Context c) {return prefs(c).edit().putInt("grantBoot",boot(c)).putLong("grantUntil",SystemClock.elapsedRealtime()+10*60_000L).commit();}
    static void cancelRelease(Context c){synchronized(LOCK){android.content.SharedPreferences.Editor e=prefs(c).edit().remove("grantUntil");putDeadline(e,"release",null);e.commit();}}
    static boolean voluntaryStop(Context c) {
        if(NormalAutonomy.locked(c) && releaseRemaining(c)>0)return false;
        // A successful voluntary release must never trigger app-driven reactivation.
        android.content.SharedPreferences.Editor release=prefs(c).edit().putBoolean("paused",true).putBoolean("recovery",false).putBoolean("contentConsent",false);
        NormalAutonomy.clear(release);
        if(!release.commit())return false;
        c.getSharedPreferences("shield",0).edit().putBoolean("wanted",false).commit();
        DevicePolicyManager manager=c.getSystemService(DevicePolicyManager.class);
        ComponentName admin=new ComponentName(c,NormalAdminReceiver.class);
        try{if(manager!=null && manager.isAdminActive(admin))manager.removeActiveAdmin(admin);}catch(RuntimeException e){prefs(c).edit().putString("recoveryIssue","Rimuovi l'amministratore Normal dalle impostazioni Android.").apply();}
        try{if(ShieldVpnService.running)c.startService(new Intent(c,ShieldVpnService.class).setAction("STOP"));else c.stopService(new Intent(c,ShieldVpnService.class));cancelRelease(c);return true;}catch(RuntimeException e){prefs(c).edit().putString("recoveryIssue","Arresto non eseguito: usa le impostazioni VPN Android.").apply();return false;}
    }
    static String status(Context c) {
        boolean active=protectedNow(c);String text=active?"VPN locale attiva":"VPN locale disattivata: filtro di rete non attivo";
        text+="\n"+NormalAutonomy.status(c);
        text+="\nLivello contenuti: "+(!NormalContentService.consent(c)?"non consentito":!NormalContentService.enabledInSettings(c)?"permesso revocato / disattivato":!NormalContentService.connected()?"consentito; servizio non collegato":NormalContentService.rulesReady()?"servizio e regole caricati; copertura limitata alle app e URL supportati":"servizio collegato; regole non ancora disponibili");
        if(active)text+=physicalAvailable(c)&&c.getSharedPreferences("shield",0).getBoolean("connectivityOk",false)?" / Internet verificato":" / rete assente o verifica Internet incompleta";
        android.content.SharedPreferences p=prefs(c);
        if(p.contains("contentIssue"))text+="\n"+p.getString("contentIssue","");
        Boolean always=NormalMonitorService.alwaysOn(),lock=NormalMonitorService.lockdown();
        text+="\nAlways-on: "+(always==null?"verifica nelle impostazioni Android":always?"attivo":"disattivato");
        text+="\nBlocca connessioni senza VPN: "+(lock==null?"verifica nelle impostazioni Android":lock?"attivo":"disattivato");
        text+="\n"+p.getString("recoveryIssue",p.getBoolean("paused",false)?"Ripristino automatico sospeso dopo rilascio volontario.":"Ripristino automatico: "+(p.getBoolean("recovery",false)?"abilitato":"non abilitato"));
        if(p.contains("monitorIssue"))text+="\n"+p.getString("monitorIssue","");
        if(BuildConfig.AUTOMATIC_SMS_ENABLED && p.getBoolean("smsConsent",false)) {
            NormalEpisode e=episode(c);
            if(e.off && e.deadline!=null && !e.claimed)text+="\nAvviso SMS fra almeno "+((e.deadline.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(c))+999)/1000)+" secondi (Android può ritardare).";
            text+="\n"+p.getString("smsIssue","SMS automatici consentiti; possibile costo dell'operatore.");
        }
        return text;
    }
}
