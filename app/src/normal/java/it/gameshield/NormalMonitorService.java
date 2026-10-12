package it.gameshield;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;

/** Status-only VpnService instance: no TUN, no VPN intent filter, no data path. */
public final class NormalMonitorService extends VpnService {
    private static volatile NormalMonitorService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){
        if(!NormalSafety.enabled(NormalMonitorService.this)){stopSelf();return;}
        NormalSafety.check(NormalMonitorService.this);try{show();}catch(RuntimeException ignored){}handler.postDelayed(this,15_000);
    }};
    static Boolean alwaysOn(){try{return instance==null?null:instance.isAlwaysOn();}catch(RuntimeException e){return null;}}
    static Boolean lockdown(){try{return instance==null?null:instance.isLockdownEnabled();}catch(RuntimeException e){return null;}}
    @Override public void onCreate(){super.onCreate();instance=this;show();}
    private void show() {
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("normal_monitor","Supervisione Normal",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,40,new Intent(this,NormalResilienceActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification note=new Notification.Builder(this,"normal_monitor").setSmallIcon(R.drawable.shield).setContentTitle("GameShield Normal")
            .setContentText(NormalSafety.protectedNow(this)?"Filtro VPN attivo / supervisione attiva":"Filtro disattivato / supervisione e avvisi attivi")
            .setStyle(new Notification.BigTextStyle().bigText(NormalSafety.status(this))).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(40,note,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(40,note);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){handler.removeCallbacks(tick);handler.post(tick);return START_STICKY;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(instance==this)instance=null;super.onDestroy();}
}
