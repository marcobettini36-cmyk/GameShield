package it.gameshield;

/** One durable outage, one SMS claim. Reconnection cancels the entire outage. */
final class NormalEpisode {
    static final long SMS_DELAY=15*60_000L;
    long id;
    boolean seenProtection,off,claimed;
    NormalDeadline deadline;
    void observe(boolean protectedNow,long wall,long elapsed,int boot) {
        if(protectedNow) {seenProtection=true;off=false;claimed=false;deadline=null;}
        else if(seenProtection && !off) {
            id++;off=true;claimed=false;deadline=new NormalDeadline(wall,elapsed,boot,SMS_DELAY);
        }
    }
    boolean due(long wall,long elapsed,int boot) {
        return off && !claimed && deadline!=null && deadline.remaining(wall,elapsed,boot)==0;
    }
    boolean claim(long wall,long elapsed,int boot) {
        if(!due(wall,elapsed,boot))return false;
        claimed=true;return true;
    }
}
