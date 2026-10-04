package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class DisableSessionTest {
    @Test public void fullSixtySecondsRequired(){DisableSession s=new DisableSession(1000,null);assertEquals(60,s.remaining(1000));assertEquals(1,s.remaining(60999));assertFalse(s.verify(60999,s.code,false,true));assertFalse(s.confirm(true));}
    @Test public void correctCodeThenPositiveConfirmation(){DisableSession s=new DisableSession(0,null);assertTrue(s.verify(60000,s.code,false,false));assertTrue(s.confirm(true));assertFalse(s.confirm(true));}
    @Test public void wrongCodeNeverStops(){DisableSession s=new DisableSession(0,null);assertFalse(s.verify(60000,"wrong",false,true));assertFalse(s.confirm(true));}
    @Test public void finalNegativeKeepsProtection(){DisableSession s=new DisableSession(0,null);assertTrue(s.verify(60000,s.code,false,true));assertFalse(s.confirm(false));assertFalse(s.confirm(true));}
    @Test public void cancellationInvalidatesEverything(){DisableSession s=new DisableSession(0,null);s.cancel();assertFalse(s.verify(60000,s.code,false,true));assertFalse(s.confirm(true));}
    @Test public void pinRequiredCannotBeBypassed(){DisableSession s=new DisableSession(0,null);assertFalse(s.verify(60000,s.code,true,false));assertFalse(s.confirm(true));assertTrue(s.verify(60000,s.code,true,true));assertTrue(s.confirm(true));}
    @Test public void recreatedSessionKeepsDeadlineButDropsAuthorization(){DisableSession s=new DisableSession(0,null);assertTrue(s.verify(60000,s.code,true,true));DisableSession restored=new DisableSession(s.deadline,s.code,true);assertFalse(restored.confirm(true));assertEquals(30,restored.remaining(30000));}
    @Test public void freshRequestRestartsAndChangesCode(){DisableSession first=new DisableSession(0,null);for(int i=0;i<100;i++){DisableSession next=new DisableSession(70000,first.code);assertNotEquals(first.code,next.code);assertTrue(next.code.matches("[0-9]{6}"));assertEquals(60,next.remaining(70000));first=next;}}
}
