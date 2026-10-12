package it.gameshield;
import android.app.job.*;
import java.util.concurrent.*;

public final class NormalCheckJob extends JobService {
    private final ConcurrentHashMap<Integer,ExecutorService> workers=new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters p) {
        final ExecutorService running=Executors.newSingleThreadExecutor();workers.put(p.getJobId(),running);
        running.execute(()->{try{NormalSafety.check(this);NormalSafety.startMonitor(this);}finally{workers.remove(p.getJobId(),running);jobFinished(p,false);new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->NormalJobs.schedule(this),1000);running.shutdown();}});return true;
    }
    @Override public boolean onStopJob(JobParameters p){ExecutorService running=workers.remove(p.getJobId());if(running!=null)running.shutdownNow();if(p.getJobId()==NormalJobs.EVENT){NormalEpisode e=NormalSafety.episode(this);return NormalSafety.enabled(this) && e.off && !e.claimed;}return NormalSafety.enabled(this);}
}
