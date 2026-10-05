package it.gameshield;
import java.util.*;
/** No global route exception, GMS exemption, allowBypass or VPN-suspension decision. */
final class AndroidAutoPolicy {
    static final String HOST="com.google.android.projection.gearhead";
    static final String AUTHORITY="androidx.car.app.connection";
    // Android's official UAMP allowed_media_browser_callers.xml: production keys only.
    static final String[] RELEASE_CERTS={
        "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
        "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295"};
    static boolean isolatedHostUid(String[] packages) {
        return packages!=null && packages.length==1 && HOST.equals(packages[0]);
    }
    static boolean projection(int type,boolean hostTrusted,boolean providerTrusted) {
        return type==2 && hostTrusted && providerTrusted;
    }
    static Set<String> exclusions(boolean enabled,boolean hostTrusted) {
        return enabled && hostTrusted ? Collections.singleton(HOST) : Collections.emptySet();
    }
    static boolean releaseCertificate(String digest) {
        return Arrays.asList(RELEASE_CERTS).contains(digest);
    }
    static byte[] digest(String hex) {
        byte[] b=new byte[hex.length()/2];
        for(int i=0;i<b.length;i++)b[i]=(byte)((Character.digit(hex.charAt(i*2),16)<<4)|Character.digit(hex.charAt(i*2+1),16));
        return b;
    }
}
