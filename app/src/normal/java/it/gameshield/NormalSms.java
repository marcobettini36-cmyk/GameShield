package it.gameshield;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.telephony.*;
import java.util.*;

final class NormalSms {
    static final String BODY="Avviso GameShield: la protezione contro il gioco d'azzardo risulta disattivata da almeno 15 minuti. Ti consigliamo di contattare la persona che ti ha indicato come riferimento.";
    static boolean validNumber(String n){return n!=null && n.matches("\\+?[0-9]{7,15}");}
    static void maybeSend(Context c) {
        if(!BuildConfig.AUTOMATIC_SMS_ENABLED || !NormalSafety.prefs(c).getBoolean("smsConsent",false))return;
        NormalEpisode e=NormalSafety.episode(c);
        if(!e.due(System.currentTimeMillis(),SystemClock.elapsedRealtime(),NormalSafety.boot(c)) || System.currentTimeMillis()<NormalSafety.prefs(c).getLong("smsRetry",0))return;
        send(c,false);
    }
    static boolean send(Context c,boolean test) {
        synchronized(NormalSafety.LOCK) {
            android.content.SharedPreferences p=NormalSafety.prefs(c);String key=test?"test":"alert";
            if(!BuildConfig.AUTOMATIC_SMS_ENABLED || !p.getBoolean("smsConsent",false))return issue(c,test,"SMS automatici non autorizzati.",false);
            if(c.checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)return issue(c,test,"Permesso SEND_SMS negato: nessun SMS inviato. Apri le impostazioni Normal.",true);
            String number=p.getString("number","");if(!validNumber(number))return issue(c,test,"Numero della persona fidata non configurato.",true);
            if(!c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))return issue(c,test,"Dispositivo senza telefonia SMS.",true);
            int sid=p.getInt("sim",SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if(!SubscriptionManager.isValidSubscriptionId(sid) || sid!=SubscriptionManager.getDefaultSmsSubscriptionId())return issue(c,test,"Seleziona e riconferma una SIM SMS predefinita nelle impostazioni Android. Nessun SMS inviato.",true);
            try {
                TelephonyManager phone=c.getSystemService(TelephonyManager.class).createForSubscriptionId(sid);
                if(phone.getSimState()!=TelephonyManager.SIM_STATE_READY)return issue(c,test,"SIM assente, bloccata o non pronta: avviso sospeso.",true);
            }catch(RuntimeException ex){return issue(c,test,"Stato SIM non verificabile: nessun SMS inviato.",true);}
            NormalEpisode state=NormalSafety.episode(c);
            if(!test) {
                if(NormalSafety.protectedNow(c)){NormalSafety.check(c);return false;}
                if(!state.claim(System.currentTimeMillis(),SystemClock.elapsedRealtime(),NormalSafety.boot(c)))return false;
            }else if(System.currentTimeMillis()<p.getLong("nextTest",0))return issue(c,true,"Attendi un minuto prima di un altro invio di prova.",false);
            SmsManager manager=Build.VERSION.SDK_INT>=31?c.getSystemService(SmsManager.class).createForSubscriptionId(sid):SmsManager.getSmsManagerForSubscriptionId(sid);
            ArrayList<String> parts=manager.divideMessage(test?"PROVA GameShield: questo è un messaggio di test, non un avviso di disattivazione reale.":BODY);
            if(parts.isEmpty() || parts.size()>8)return issue(c,test,"Messaggio non supportato: nessun SMS inviato.",false);
            long id=test?p.getLong("testId",0)+1:state.id,attempt=p.getLong(key+"Attempt",0)+1;
            android.content.SharedPreferences.Editor edit=p.edit().putLong(key+"Id",id).putLong(key+"Attempt",attempt)
                .putInt(key+"Parts",parts.size()).putInt(key+"Done",0).putInt(key+"Success",0).putInt(key+"Transient",0);
            if(test)edit.putLong("nextTest",System.currentTimeMillis()+60_000);
            else edit.putBoolean("claimed",true);
            // Durable claim BEFORE SmsManager: a crash must not send the same alert twice.
            if(!edit.commit())return false;
            ArrayList<PendingIntent> sent=new ArrayList<>();
            for(int i=0;i<parts.size();i++) {
                Intent result=new Intent(c,NormalSmsSentReceiver.class).setData(Uri.parse("gameshield://sms/"+key+"/"+id+"/"+attempt+"/"+i))
                    .putExtra("test",test).putExtra("id",id).putExtra("attempt",attempt).putExtra("part",i);
                sent.add(PendingIntent.getBroadcast(c,0,result,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
            }
            try {
                manager.sendMultipartTextMessage(number,null,parts,sent,null);
                p.edit().putString(test?"testIssue":"smsIssue","SMS affidato al sistema; in attesa dell'esito dell'operatore.").apply();return true;
            }catch(RuntimeException ex) {
                // Submission may be ambiguous. Never automatically duplicate it.
                return issue(c,test,"Invio non confermato. Nessun reinvio automatico per evitare duplicati; verifica permessi e SIM.",false);
            }
        }
    }
    private static boolean issue(Context c,boolean test,String message,boolean retry) {
        android.content.SharedPreferences.Editor e=NormalSafety.prefs(c).edit().putString(test?"testIssue":"smsIssue",message);
        if(!test && retry)e.putLong("smsRetry",System.currentTimeMillis()+5*60_000L);
        e.apply();return false;
    }
    static void result(Context c,Intent i,int result) {
        synchronized(NormalSafety.LOCK) {
            boolean test=i.getBooleanExtra("test",false);String key=test?"test":"alert";
            android.content.SharedPreferences p=NormalSafety.prefs(c);
            long id=i.getLongExtra("id",-1),attempt=i.getLongExtra("attempt",-1);int part=i.getIntExtra("part",-1),total=p.getInt(key+"Parts",0);
            if(id!=p.getLong(key+"Id",-2)||attempt!=p.getLong(key+"Attempt",-2))return;
            NormalEpisode state=NormalSafety.episode(c);if(!test && (!state.off || id!=state.id))return;
            int bit=1<<part,done=p.getInt(key+"Done",0);if(!NormalSmsOutcome.acceptPart(part,total,done))return;
            int success=p.getInt(key+"Success",0),transientMask=p.getInt(key+"Transient",0);done|=bit;
            if(result==Activity.RESULT_OK)success|=bit;
            if(result==SmsManager.RESULT_ERROR_NO_SERVICE || result==SmsManager.RESULT_ERROR_RADIO_OFF)transientMask|=bit;
            android.content.SharedPreferences.Editor edit=p.edit().putInt(key+"Done",done).putInt(key+"Success",success).putInt(key+"Transient",transientMask);
            int all=(1<<total)-1;
            if(done==all) {
                String message=success==all?"SMS inviato al servizio telefonico; ricezione dalla persona fidata non garantita.":"Invio fallito o parziale. Nessun reinvio automatico di messaggi potenzialmente già inviati.";
                if(!test && NormalSmsOutcome.retry(total,done,success,transientMask)) {
                    edit.putBoolean("claimed",false).putLong("smsRetry",System.currentTimeMillis()+5*60_000L);
                    message="Radio spenta o copertura assente: nessuna parte inviata; nuovo tentativo fra almeno 5 minuti.";
                }
                edit.putString(test?"testIssue":"smsIssue",message);
            }
            edit.commit();NormalJobs.schedule(c);
        }
    }
}
