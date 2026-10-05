package it.gameshield;
import android.content.Context;
final class AndroidAutoAuthorization {

    static boolean available(Context c){return new Guardian(c).configured();}
    static boolean required(Context c){return true;}
    static String title(){return "Codice custode";}
    static boolean authenticate(Context c,char[] secret)throws Exception{return new Guardian(c).authenticate(secret);}
}
