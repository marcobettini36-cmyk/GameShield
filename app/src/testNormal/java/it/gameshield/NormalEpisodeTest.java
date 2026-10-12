package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class NormalEpisodeTest {
    @Test public void noAlertBeforeFirstProtection(){NormalEpisode e=new NormalEpisode();e.observe(false,0,0,1);assertFalse(e.off);assertFalse(e.due(999_999,999_999,1));}
    @Test public void fifteenMinutesAreMinimum(){NormalEpisode e=outage();assertFalse(e.due(900_009,900_009,1));assertTrue(e.due(900_010,900_010,1));}
    @Test public void reconnectCancels(){NormalEpisode e=outage();e.observe(true,800_000,800_000,1);assertFalse(e.off);assertNull(e.deadline);assertFalse(e.claim(900_010,900_010,1));}
    @Test public void durableClaimPreventsRepeat(){NormalEpisode e=outage();assertTrue(e.claim(900_010,900_010,1));NormalEpisode copy=new NormalEpisode();copy.id=e.id;copy.off=e.off;copy.claimed=e.claimed;copy.seenProtection=e.seenProtection;copy.deadline=e.deadline;assertFalse(copy.claim(1_800_000,1_800_000,2));}
    @Test public void separateOutagesHaveSeparateIdentities(){NormalEpisode e=outage();long first=e.id;e.observe(true,20,20,1);e.observe(false,30,30,1);assertEquals(first+1,e.id);assertFalse(e.claimed);assertEquals(900_000,e.deadline.remaining(30,30,1));}
    private NormalEpisode outage(){NormalEpisode e=new NormalEpisode();e.observe(true,0,0,1);e.observe(false,10,10,1);return e;}
}
