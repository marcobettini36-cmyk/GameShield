package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class NormalLayersTest {
    @Test public void exactPackageMatchingAvoidsBankAndSubstringBlocks()throws Exception {
        NormalAppCatalog c=new NormalAppCatalog(new StringReader("com.bet365Wrapper.Bet365_Application\n"));
        assertTrue(c.blocks("com.bet365Wrapper.Bet365_Application"));
        assertFalse(c.blocks("com.bank.bet365Wrapper.Bet365_Application"));assertFalse(c.blocks("com.paypal.android.p2pmobile"));
        assertFalse(c.blocks("com.revolut.revolut"));assertFalse(c.blocks("com.android.settings"));assertFalse(c.blocks(null));
    }
    @Test public void systemAndOwnPackagesCannotEnterCatalog() {
        for(String p:new String[]{"android","it.gameshield","it.gameshield.strong","com.android.settings","com.google.android.permissioncontroller","com.sec.android.app.sbrowser"})
            try{new NormalAppCatalog(new StringReader(p));fail(p);}catch(IOException expected){}
    }
    @Test public void visibleHostRequiresAddressNotPageText(){
        assertEquals("bet365.com",NormalVisibleHost.parse("https://bet365.com/path?q=1"));assertEquals("bet365.com",NormalVisibleHost.parse("bet365.com"));
        assertNull(NormalVisibleHost.parse("Article about bet365.com"));assertNull(NormalVisibleHost.parse("https://bank.com@bet365.com"));assertNull(NormalVisibleHost.parse("javascript:bet365.com"));
        assertNull(NormalVisibleHost.parse("127.0.0.1"));assertEquals("google.com",NormalVisibleHost.parse("https://google.com/search?q=bet365.com"));
    }
    @Test public void periodsOnlyAcceptExplicitChoicesAndNeverExpireOnClockChangeInSameBoot(){
        for(int days:new int[]{7,30,90}){long period=NormalAutonomy.duration(days);assertEquals(days*86_400_000L,period);
            NormalDeadline d=new NormalDeadline(1000,500,1,period);assertEquals(period-100,d.remaining(Long.MAX_VALUE,600,1));assertEquals(0,d.remaining(0,500+period,1));assertEquals(period,d.remaining(0,0,2));}
        try{NormalAutonomy.duration(1);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void browserMatchingKeepsLegitimateUrlWhoseQueryMentionsGambling(){
        DomainRules r=new DomainRules(java.util.List.of("bet365.com"));assertFalse(r.blocks(NormalVisibleHost.parse("https://google.com/search?q=bet365.com")));
        assertTrue(r.blocks(NormalVisibleHost.parse("https://sports.bet365.com/")));assertFalse(r.blocks(NormalVisibleHost.parse("https://notbet365.com/")));
    }
}
