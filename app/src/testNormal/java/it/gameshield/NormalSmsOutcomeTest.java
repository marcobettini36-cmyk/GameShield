package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class NormalSmsOutcomeTest {
    @Test public void duplicateCallbackRejected(){assertFalse(NormalSmsOutcome.acceptPart(0,2,1));assertTrue(NormalSmsOutcome.acceptPart(1,2,1));}
    @Test public void invalidPartRejected(){assertFalse(NormalSmsOutcome.acceptPart(-1,2,0));assertFalse(NormalSmsOutcome.acceptPart(2,2,0));assertFalse(NormalSmsOutcome.acceptPart(0,9,0));}
    @Test public void noServiceEveryPartCanRetry(){assertTrue(NormalSmsOutcome.retry(2,3,0,3));}
    @Test public void partialSuccessNeverAutoRetries(){assertFalse(NormalSmsOutcome.retry(2,3,1,2));}
    @Test public void missingCallbackNeverAutoRetries(){assertFalse(NormalSmsOutcome.retry(2,1,0,1));}
    @Test public void unknownFailureNeverAutoRetries(){assertFalse(NormalSmsOutcome.retry(2,3,0,0));}
}
