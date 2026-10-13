package it.gameshield;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Internal commitment only. Never constrains Android settings or removal. */
final class NormalAutonomy {
    static long duration(int days) {
        if(days!=7 && days!=30 && days!=90)throw new IllegalArgumentException("Periodo non valido");
        return days*NormalSafety.DAY;
    }
    static long remaining(Context c) {
        SharedPreferences p=NormalSafety.prefs(c);long duration=p.getLong("autonomyDuration",0);
        if(duration!=7*NormalSafety.DAY && duration!=30*NormalSafety.DAY && duration!=90*NormalSafety.DAY)return 0;
        NormalDeadline d=NormalSafety.deadline(p,"autonomy",duration);
        return d==null?0:d.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),NormalSafety.boot(c));
    }
    static boolean locked(Context c){return remaining(c)>0 && !NormalSafety.granted(c);}
    static boolean start(Context c,int days) {
        synchronized(NormalSafety.LOCK) {
            if(remaining(c)>0)return false;
            SharedPreferences.Editor e=NormalSafety.prefs(c).edit().putLong("autonomyDuration",duration(days)).remove("grantUntil");
            NormalSafety.putDeadline(e,"autonomy",new NormalDeadline(System.currentTimeMillis(),SystemClock.elapsedRealtime(),NormalSafety.boot(c),duration(days)));
            return e.commit();
        }
    }
    static void clear(SharedPreferences.Editor e){NormalSafety.putDeadline(e,"autonomy",null);e.remove("autonomyDuration");}
    static String status(Context c){long n=remaining(c);return n==0?"Protezione Autonoma: non attiva / periodo concluso":"Protezione Autonoma: "+((n+59_999)/60_000/1440)+" giorni, "+(((n+59_999)/60_000/60)%24)+" ore rimanenti. Vincolo interno; Android resta accessibile.";}
}
