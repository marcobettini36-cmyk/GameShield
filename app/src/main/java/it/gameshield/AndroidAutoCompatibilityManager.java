package it.gameshield;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.util.Log;
import androidx.car.app.connection.CarConnection;
import androidx.lifecycle.Observer;
import java.util.*;
/** Official connection query, authenticated host, per-app routing; never stops the VPN. */
final class AndroidAutoCompatibilityManager implements AutoCloseable {
    static volatile boolean projectionConnected;
    static volatile boolean routingApplied;
    private final Context context;
    private final Runnable changed;
    private CarConnection connection;
    private boolean observing, closed;
    private final Observer<Integer> observer=type->update(type==null?0:type);
    private final BroadcastReceiver packages=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i.getData()!=null && AndroidAutoPolicy.HOST.equals(i.getData().getSchemeSpecificPart())){
                detach();setConnected(false);attach();changed.run();
            }
        }
    };
    AndroidAutoCompatibilityManager(Context context,Runnable changed){this.context=context;this.changed=changed;}
    static boolean enabled(Context c){return c.getSharedPreferences("android_auto",0).getBoolean("enabled",!BuildConfig.STRONG);}
    static boolean trustedHost(Context c){return trustedHostUid(c)>=0;}
    static int trustedHostUid(Context c){
        PackageManager pm=c.getPackageManager();
        try{
            ApplicationInfo app=pm.getApplicationInfo(AndroidAutoPolicy.HOST,0);
            if(!app.enabled || !AndroidAutoPolicy.isolatedHostUid(pm.getPackagesForUid(app.uid)))return -1;
            for(String cert:AndroidAutoPolicy.RELEASE_CERTS)
                if(pm.hasSigningCertificate(AndroidAutoPolicy.HOST,AndroidAutoPolicy.digest(cert),PackageManager.CERT_INPUT_SHA256))return app.uid;
        }catch(PackageManager.NameNotFoundException|SecurityException ignored){/* Missing/untrusted host is not an error. */}
        return -1;
    }
    static boolean trustedProvider(Context c){
        ProviderInfo p=c.getPackageManager().resolveContentProvider(AndroidAutoPolicy.AUTHORITY,0);
        return p!=null && p.enabled && AndroidAutoPolicy.HOST.equals(p.packageName) && trustedHost(c);
    }
    static Map<String,Integer> requestedRoutes(Context c){return AndroidAutoPolicy.routing(enabled(c),trustedHostUid(c));}
    static Set<String> requestedExclusions(Context c){return requestedRoutes(c).keySet();}
    void start(){
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_PACKAGE_ADDED);f.addAction(Intent.ACTION_PACKAGE_REMOVED);f.addAction(Intent.ACTION_PACKAGE_REPLACED);f.addAction(Intent.ACTION_PACKAGE_CHANGED);f.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(packages,f,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(packages,f);
        projectionConnected=false;attach();
    }
    private void attach(){
        if(closed || !trustedProvider(context))return;
        try{
            connection=new CarConnection(context);observing=true;connection.getType().observeForever(observer);
        }catch(RuntimeException error){detach();setConnected(false);Log.w("AndroidAuto","connection query unavailable");}
    }
    private void detach(){if(observing&&connection!=null)connection.getType().removeObserver(observer);observing=false;connection=null;}
    private void update(int type){if(!closed)setConnected(AndroidAutoPolicy.projection(type,trustedHost(context),trustedProvider(context)));}
    private void setConnected(boolean value){
        if(value==projectionConnected)return;
        projectionConnected=value;Log.i("AndroidAuto",value?"projection connected":"disconnected");changed.run();
    }
    Map<String,Integer> apply(android.net.VpnService.Builder builder){
        Map<String,Integer> applied=new HashMap<>();
        for(Map.Entry<String,Integer> entry:requestedRoutes(context).entrySet()){
            String pkg=entry.getKey();
            try{builder.addDisallowedApplication(pkg);applied.put(pkg,entry.getValue());}
            catch(PackageManager.NameNotFoundException gone){Log.i("AndroidAuto","host unavailable; full filtering retained");}
        }
        return Collections.unmodifiableMap(applied);
    }
    static void recordRouting(Set<String> applied){
        boolean value=applied.contains(AndroidAutoPolicy.HOST);
        if(value!=routingApplied)Log.i("AndroidAuto",value?"compatibility routing enabled":"compatibility routing disabled");
        routingApplied=value;
    }
    @Override public void close(){closed=true;detach();context.unregisterReceiver(packages);projectionConnected=false;routingApplied=false;}
}
