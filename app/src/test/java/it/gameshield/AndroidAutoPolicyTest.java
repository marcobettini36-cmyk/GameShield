package it.gameshield;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;
public class AndroidAutoPolicyTest {
    @Test public void onlyVerifiedHostCanBeExcluded() {
        assertEquals(Collections.singleton(AndroidAutoPolicy.HOST),AndroidAutoPolicy.exclusions(true,true));
        assertTrue(AndroidAutoPolicy.exclusions(false,true).isEmpty());
        assertTrue(AndroidAutoPolicy.exclusions(true,false).isEmpty());
        assertFalse(AndroidAutoPolicy.exclusions(true,true).contains("com.google.android.gms"));
        assertFalse(AndroidAutoPolicy.exclusions(true,true).contains("com.android.chrome"));
    }
    @Test public void sharedUidIsRejected() {
        assertTrue(AndroidAutoPolicy.isolatedHostUid(new String[]{AndroidAutoPolicy.HOST}));
        assertFalse(AndroidAutoPolicy.isolatedHostUid(null));
        assertFalse(AndroidAutoPolicy.isolatedHostUid(new String[]{}));
        assertFalse(AndroidAutoPolicy.isolatedHostUid(new String[]{"com.android.chrome"}));
        assertFalse(AndroidAutoPolicy.isolatedHostUid(new String[]{AndroidAutoPolicy.HOST,"other.package"}));
    }
    @Test public void projectionRequiresTrustedProviderAndHost() {
        assertTrue(AndroidAutoPolicy.projection(2,true,true));
        assertFalse(AndroidAutoPolicy.projection(0,true,true));
        assertFalse(AndroidAutoPolicy.projection(1,true,true));
        assertFalse(AndroidAutoPolicy.projection(2,false,true));
        assertFalse(AndroidAutoPolicy.projection(2,true,false));
    }
    @Test public void productionCertificatesOnly() {
        assertEquals(2,AndroidAutoPolicy.RELEASE_CERTS.length);
        for(String cert:AndroidAutoPolicy.RELEASE_CERTS) {
            assertTrue(AndroidAutoPolicy.releaseCertificate(cert));
            assertEquals(32,AndroidAutoPolicy.digest(cert).length);
        }
        assertFalse(AndroidAutoPolicy.releaseCertificate("fake"));
        assertFalse(AndroidAutoPolicy.releaseCertificate(null));
        assertFalse(AndroidAutoPolicy.releaseCertificate("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    }
}
