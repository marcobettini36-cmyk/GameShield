package it.gameshield;

/** Monotonic within a boot; bounded wall-clock fallback across reboot. */
final class NormalDeadline {
    final long wall, elapsed, duration;
    final int boot;
    NormalDeadline(long wall,long elapsed,int boot,long duration) {
        this.wall=wall;this.elapsed=elapsed;this.boot=boot;this.duration=duration;
    }
    long remaining(long nowWall,long nowElapsed,int nowBoot) {
        long passed=boot==nowBoot && nowElapsed>=elapsed ? nowElapsed-elapsed : nowWall-wall;
        return Math.max(0,duration-Math.max(0,Math.min(duration,passed)));
    }
}
