package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class NormalDnsConfigTest {
    @Test public void personalAndCustom(){assertEquals("abcdef.dns.nextdns.io",NormalDnsConfig.hostname(" ABCDEF.dns.nextdns.io "));assertEquals("dns.provider.example",NormalDnsConfig.hostname("dns.provider.example"));assertEquals("phone-abcdef.dns.nextdns.io",NormalDnsConfig.hostname("phone-abcdef.dns.nextdns.io"));}
    @Test public void rejectsGenericAndSecrets(){for(String s:new String[]{"dns.nextdns.io","id.dns.nextdns.io","https://abcdef.dns.nextdns.io","user:pass@dns.example","1.1.1.1","a..example","-a.example","a-.example","a.example/path","a.example?token=x",""})assertNull(s,NormalDnsConfig.hostname(s));}
    @Test public void incompleteLevelsNeverActive(){assertEquals("DISATTIVATA",NormalProtectionState.overall(false,false,false,false));assertEquals("PARZIALE",NormalProtectionState.overall(true,true,false,false));assertEquals("PARZIALE",NormalProtectionState.overall(true,false,true,true));assertEquals("ATTIVA",NormalProtectionState.overall(true,true,true,true));}
}
