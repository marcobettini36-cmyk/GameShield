package it.gameshield;
import android.content.Context;
import android.net.*;
import android.os.CancellationSignal;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;

/** System-resolver measurement only. Never starts/stops VPN or changes DNS. */
final class NormalDnsProbe {
    static boolean hasVpn(ConnectivityManager cm){for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return true;}return false;}
    static List<InetAddress> resolve(Network n,String host)throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<List<InetAddress>> answer=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();CancellationSignal cancel=new CancellationSignal();
        DnsResolver.getInstance().query(n,host,DnsResolver.FLAG_NO_CACHE_LOOKUP,Runnable::run,cancel,new DnsResolver.Callback<List<InetAddress>>(){
            public void onAnswer(List<InetAddress> value,int rcode){if(rcode==0||rcode==3)answer.set(value);else error.set(new java.io.IOException("DNS rcode "+rcode));done.countDown();}
            public void onError(DnsResolver.DnsException e){error.set(e);done.countDown();}
        });
        if(!done.await(10,TimeUnit.SECONDS)){cancel.cancel();throw new java.io.IOException("DNS timeout");}
        if(error.get()!=null)throw error.get();return answer.get()==null?Collections.emptyList():answer.get();
    }
    static boolean blocked(List<InetAddress> values){return values.isEmpty()||values.stream().allMatch(a->a.isAnyLocalAddress()||a.isLoopbackAddress());}
    static String run(Context context,String sample)throws Exception {
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
        if(hasVpn(cm))return "NON VERIFICATO: una VPN è attiva. Non viene disattivata automaticamente. Riprova soltanto quando scegli volontariamente di spegnerla.";
        Network n=cm.getActiveNetwork();if(n==null)return "NON VERIFICATO: nessuna rete attiva.";
        LinkProperties lp=cm.getLinkProperties(n);String hostname=lp==null?null:lp.getPrivateDnsServerName();
        if(lp==null || !lp.isPrivateDnsActive() || hostname==null)return "NON VERIFICATO: Private DNS con hostname non attivo/validato. Automatico non garantisce un filtro gambling.";
        StringBuilder out=new StringBuilder("Private DNS attivo: ").append(hostname).append('\n');
        boolean good=true;
        for(String host:new String[]{"google.com","wikipedia.org","github.com"}){boolean ok=!blocked(resolve(n,host));out.append(host).append(" DNS: ").append(ok?"OK":"FALLITO").append('\n');good&=ok;}
        HttpsURLConnection h=(HttpsURLConnection)n.openConnection(new URL("https://www.wikipedia.org/"));h.setConnectTimeout(10_000);h.setReadTimeout(10_000);
        boolean https;try{https=h.getResponseCode()>0;}finally{h.disconnect();}
        out.append("HTTPS innocuo: ").append(https?"OK":"FALLITO").append('\n');
        boolean sampleBlocked=blocked(resolve(n,sample));out.append(sample).append(" DNS: ").append(sampleBlocked?"risposta bloccante osservata":"RISOLTO: non bloccato da questo DNS").append('\n');
        LinkProperties end=cm.getLinkProperties(n);
        if(hasVpn(cm)||!n.equals(cm.getActiveNetwork())||end==null||!end.isPrivateDnsActive()||!Objects.equals(hostname,end.getPrivateDnsServerName()))return "NON VERIFICATO: rete, VPN o DNS cambiati durante la prova.";
        return out+(good&&https&&sampleBlocked?"Campione filtrato senza VPN; non certifica l'intera blacklist o tutti i browser.":"Protezione DNS non dimostrata per questo campione.")+" Una risposta NXDOMAIN può dipendere anche dal sito: ripeti su domini attivi. Nessuna richiesta HTTPS al sito gambling.";
    }
}
