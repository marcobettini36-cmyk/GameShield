package it.gameshield;
/** Retry only a complete, unambiguously unsent multipart attempt. */
final class NormalSmsOutcome {
    static boolean acceptPart(int part,int total,int done){return total>0 && total<=8 && part>=0 && part<total && (done&(1<<part))==0;}
    static boolean retry(int total,int done,int success,int transientMask) {
        if(total<1 || total>8)return false;
        int all=(1<<total)-1;return done==all && success==0 && transientMask==all;
    }
}
