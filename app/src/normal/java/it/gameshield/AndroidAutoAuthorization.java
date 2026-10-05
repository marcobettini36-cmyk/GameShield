package it.gameshield;
import android.content.Context;
final class AndroidAutoAuthorization {

    static boolean available(Context c){return true;}
    static boolean required(Context c){return new NormalPinStore(c).configured();}
    static String title(){return "PIN per disattivazione";}
    static boolean authenticate(Context c,char[] secret)throws Exception{try{return new NormalPinStore(c).verify(secret);}finally{java.util.Arrays.fill(secret,'\0');}}
}
