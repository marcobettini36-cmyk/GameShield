package it.gameshield;
import android.content.*;
public final class NormalBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())){
            NormalJobs.schedule(c);NormalSafety.startMonitor(c);
        }
    }
}
