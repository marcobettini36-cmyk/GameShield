package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class GuardianCryptoTest {
    @Test public void verifiesOnlyCorrectCode() throws Exception { String h = GuardianCrypto.create("guardian-passphrase".toCharArray()); assertTrue(GuardianCrypto.verify("guardian-passphrase".toCharArray(), h)); assertFalse(GuardianCrypto.verify("wrong-passphrase".toCharArray(), h)); }
    @Test public void saltsAreRandom() throws Exception { assertNotEquals(GuardianCrypto.create("guardian-passphrase".toCharArray()), GuardianCrypto.create("guardian-passphrase".toCharArray())); }
    @Test(expected = IllegalArgumentException.class) public void rejectsWeakCode() throws Exception { GuardianCrypto.create("1234".toCharArray()); }
    @Test public void malformedHashFailsClosed() throws Exception { assertFalse(GuardianCrypto.verify("12345678".toCharArray(), "garbage")); assertFalse(GuardianCrypto.verify("12345678".toCharArray(), "a:b:c")); }
}
