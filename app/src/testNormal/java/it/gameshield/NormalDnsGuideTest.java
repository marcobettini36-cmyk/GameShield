package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class NormalDnsGuideTest {
    @Test public void corruptedStepIsClamped(){assertEquals(0,NormalDnsGuide.step(-1));assertEquals(5,NormalDnsGuide.step(Integer.MAX_VALUE));for(int n=0;n<6;n++)assertEquals(n,NormalDnsGuide.step(n));}
}
