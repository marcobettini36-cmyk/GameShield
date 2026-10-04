package it.gameshield;
import org.junit.Test;
import static org.junit.Assert.*;
public class NormalPinCryptoTest {
    @Test public void saltedHashesVerifyAndRejectWrongPin()throws Exception{char[] pin="012345".toCharArray();String a=NormalPinCrypto.create(pin),b=NormalPinCrypto.create(pin);assertNotEquals(a,b);assertFalse(a.contains("012345"));assertTrue(NormalPinCrypto.verify(pin,a));assertFalse(NormalPinCrypto.verify("012346".toCharArray(),a));}
    @Test public void invalidPinsRejected(){assertFalse(NormalPinCrypto.valid("12345".toCharArray()));assertFalse(NormalPinCrypto.valid("1234567890123".toCharArray()));assertFalse(NormalPinCrypto.valid("12345x".toCharArray()));assertTrue(NormalPinCrypto.valid("000000".toCharArray()));}
    @Test public void malformedStorageFailsClosed()throws Exception{for(String s:new String[]{"","1:invalid:x","2:invalid:x","1:a:b:c"})assertFalse(NormalPinCrypto.verify("123456".toCharArray(),s));assertFalse(NormalPinCrypto.verify("123456".toCharArray(),null));}
}
