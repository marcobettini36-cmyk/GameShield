package it.gameshield;
import android.content.*;
import android.database.Cursor;
import android.net.*;
import android.os.*;

public final class NormalInitializer extends ContentProvider {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable check=()->{Context c=getContext();NormalSafety.check(c);NormalSafety.startMonitor(c);};
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private ConnectivityManager.NetworkCallback callback;
    @Override public boolean onCreate() {
        Context c=getContext();
        ((android.app.Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(android.app.Activity a,Bundle b){if(a instanceof MainActivity){a.startActivity(new Intent(a,NormalHomeActivity.class));a.finish();}}
            public void onActivityStarted(android.app.Activity a){} public void onActivityResumed(android.app.Activity a){}
            public void onActivityPaused(android.app.Activity a){} public void onActivityStopped(android.app.Activity a){}
            public void onActivitySaveInstanceState(android.app.Activity a,Bundle b){} public void onActivityDestroyed(android.app.Activity a){}
        });
        listener=(p,key)->{if("wanted".equals(key)||"connectivityOk".equals(key)||"error".equals(key)){handler.removeCallbacks(check);handler.postDelayed(check,500);}};
        c.getSharedPreferences("shield",0).registerOnSharedPreferenceChangeListener(listener);
        callback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network n){changed();}
            @Override public void onLost(Network n){changed();}
            private void changed(){handler.removeCallbacks(check);handler.postDelayed(check,500);}
        };
        try{c.getSystemService(ConnectivityManager.class).registerNetworkCallback(new NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addTransportType(NetworkCapabilities.TRANSPORT_VPN).build(),callback);}catch(RuntimeException ignored){}
        handler.post(check);return true;
    }
    @Override public Cursor query(Uri u,String[]p,String s,String[]a,String sort){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
}
