package it.gameshield;
import android.app.job.*;
import android.content.*;
import android.os.SystemClock;

final class NormalJobs {
    static final int PERIODIC=47101,EVENT=47102;
    static void schedule(Context c) {
        try{scheduleInternal(c);}catch(RuntimeException e){try{NormalSafety.prefs(c).edit().putString("monitorIssue","Android non ha accettato il lavoro in background: apri GameShield.").apply();}catch(RuntimeException ignored){}}
    }
    private static void scheduleInternal(Context c) {
        JobScheduler js=c.getSystemService(JobScheduler.class);
        if(!NormalSafety.enabled(c)){js.cancel(PERIODIC);js.cancel(EVENT);return;}
        ComponentName component=new ComponentName(c,NormalCheckJob.class);
        if(js.getPendingJob(PERIODIC)==null)js.schedule(new JobInfo.Builder(PERIODIC,component).setPeriodic(15*60_000L).setPersisted(true).build());
        NormalEpisode e=NormalSafety.episode(c);
        if(e.off && !e.claimed && BuildConfig.AUTOMATIC_SMS_ENABLED && NormalSafety.prefs(c).getBoolean("smsConsent",false)) {
            long remaining=e.deadline==null?NormalEpisode.SMS_DELAY:e.deadline.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),NormalSafety.boot(c));
            long retry=Math.max(0,NormalSafety.prefs(c).getLong("smsRetry",0)-System.currentTimeMillis());
            long delay=Math.max(1000,Math.max(remaining,retry));
            // Do not postpone an existing job every time the supervisor ticks.
            if(js.getPendingJob(EVENT)==null)js.schedule(new JobInfo.Builder(EVENT,component).setMinimumLatency(delay).setPersisted(true).build());
        }else js.cancel(EVENT);
    }
}
