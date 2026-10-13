package it.gameshield;
import android.content.Context;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.net.*;
import android.os.SystemClock;

/** Presentation only; never changes protection or grants a permission. */
final class NormalProtectionState {
    static boolean apps(Context c){return NormalContentService.consent(c)&&NormalContentService.enabledInSettings(c)&&NormalContentService.connected()&&NormalContentService.rulesReady();}
    static boolean admin(Context c){try{DevicePolicyManager d=c.getSystemService(DevicePolicyManager.class);return d!=null&&d.isAdminActive(new ComponentName(c,NormalAdminReceiver.class));}catch(RuntimeException e){return false;}}
    static String actualDns(Context c){
        try{ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);Network n=dnsNetwork(c);LinkProperties lp=n==null?null:cm.getLinkProperties(n);return lp!=null&&lp.isPrivateDnsActive()?lp.getPrivateDnsServerName():null;}catch(RuntimeException e){return null;}
    }
    static Network dnsNetwork(Context c){try{ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);Network active=cm.getActiveNetwork();NetworkCapabilities caps=active==null?null:cm.getNetworkCapabilities(active);if(caps!=null&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return active;for(Network n:cm.getAllNetworks()){caps=cm.getNetworkCapabilities(n);if(caps!=null&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return n;}}catch(RuntimeException ignored){}return null;}
    static boolean dnsVerified(Context c){
        android.content.SharedPreferences p=NormalSafety.prefs(c);
        long elapsed=p.getLong("dnsVerifiedElapsed",-1), now=SystemClock.elapsedRealtime();
        return elapsed>=0&&now>=elapsed&&now-elapsed<15*60_000L&&p.getInt("dnsVerifiedBoot",-1)==NormalSafety.boot(c)
            &&java.util.Objects.equals(p.getString("dnsVerifiedHost",null),actualDns(c))&&actualDns(c)!=null
            &&dnsNetwork(c)!=null&&dnsNetwork(c).getNetworkHandle()==p.getLong("dnsVerifiedNetwork",-1);
    }
    static String overall(boolean vpn,boolean internet,boolean apps,boolean dns){
        if(vpn&&internet&&apps&&dns)return "ATTIVA";
        return vpn||apps||dns?"PARZIALE":"DISATTIVATA";
    }
    static String overall(Context c){return overall(NormalSafety.protectedNow(c),NormalSafety.physicalAvailable(c)&&c.getSharedPreferences("shield",0).getBoolean("connectivityOk",false),apps(c),dnsVerified(c));}
}
