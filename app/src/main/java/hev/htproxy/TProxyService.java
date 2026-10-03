package hev.htproxy;

/** JNI class name is part of the upstream native ABI. */
public final class TProxyService {
    private TProxyService() {}
    static { System.loadLibrary("hev-socks5-tunnel"); }
    public static native boolean TProxyStartService(String configPath, int fd);
    public static native boolean TProxyStopService();
    public static native boolean TProxyIsRunning();
    public static native long[] TProxyGetStats();
}
