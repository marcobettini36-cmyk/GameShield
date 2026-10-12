package it.gameshield;
import android.content.*;
public final class NormalSmsSentReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){NormalSms.result(c,i,getResultCode());}
}
