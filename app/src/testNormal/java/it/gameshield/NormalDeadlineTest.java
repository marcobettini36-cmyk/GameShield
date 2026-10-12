package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class NormalDeadlineTest {
    @Test public void fullDayRequired(){NormalDeadline d=new NormalDeadline(1000,2000,1,86_400_000);assertEquals(1,d.remaining(86_400_999,86_401_999,1));assertEquals(0,d.remaining(86_401_000,86_402_000,1));}
    @Test public void wallClockForwardDoesNotSkipSameBoot(){NormalDeadline d=new NormalDeadline(1000,2000,1,86_400_000);assertEquals(86_399_000,d.remaining(999_999_999,3000,1));}
    @Test public void wallClockBackDoesNotLengthenSameBoot(){NormalDeadline d=new NormalDeadline(1000,2000,1,86_400_000);assertEquals(86_399_000,d.remaining(0,3000,1));}
    @Test public void rebootRetainsElapsedWallTime(){NormalDeadline d=new NormalDeadline(1000,2000,1,86_400_000);assertEquals(86_340_000,d.remaining(61_000,100,2));}
    @Test public void rollbackAcrossBootIsBounded(){NormalDeadline d=new NormalDeadline(1000,2000,1,86_400_000);assertEquals(86_400_000,d.remaining(0,100,2));}
    @Test public void exhaustedDeadlineCannotBecomeNegative(){NormalDeadline d=new NormalDeadline(1000,2000,1,60_000);assertEquals(0,d.remaining(999_000,999_000,1));}
}
