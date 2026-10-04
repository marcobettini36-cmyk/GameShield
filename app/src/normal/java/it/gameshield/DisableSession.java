package it.gameshield;
import java.security.SecureRandom;
import java.util.Locale;
/** UI-only gate. It never controls the tunnel or stores a PIN. Monotonic milliseconds. */
final class DisableSession {
    static final long WAIT_MS = 60_000;
    final long deadline;
    final String code;
    private boolean verified, cancelled;
    DisableSession(long now, String previous) {
        deadline = now + WAIT_MS;
        SecureRandom random = new SecureRandom();
        String next;
        do { next = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); } while (next.equals(previous));
        code = next;
    }
    DisableSession(long deadline, String code, boolean restored) { this.deadline = deadline; this.code = code; }
    int remaining(long now) { return (int)Math.max(0, (deadline - now + 999)/1000); }
    boolean verify(long now, String entered, boolean pinRequired, boolean pinCorrect) {
        verified = !cancelled && remaining(now)==0 && code.equals(entered) && (!pinRequired || pinCorrect);
        return verified;
    }
    boolean confirm(boolean yes) { if(!verified || cancelled)return false; boolean result=yes; verified=false; cancelled=true; return result; }
    void cancel() { verified=false; cancelled=true; }
}
