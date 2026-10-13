package it.gameshield;

import java.io.*;
import java.util.*;

/** Exact verified package identities; no name substrings or finance categories. */
final class NormalAppCatalog {
    final Set<String> packages;
    NormalAppCatalog(Reader in)throws IOException {
        Set<String> values=new HashSet<>();BufferedReader r=new BufferedReader(in);String s;
        while((s=r.readLine())!=null){s=s.split("#",2)[0].trim();if(s.isEmpty())continue;
            if(!s.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || safe(s))throw new IOException("Package non valido");
            if(values.size()>=10000)throw new IOException("Catalogo troppo grande");values.add(s);
        }
        packages=Collections.unmodifiableSet(values);
    }
    boolean blocks(String pkg){return pkg!=null&&!safe(pkg)&&packages.contains(pkg);}
    static boolean safe(String p){return p==null||p.equals("android")||p.startsWith("it.gameshield")||p.startsWith("com.android.")||p.startsWith("com.google.android.")||p.startsWith("com.samsung.")||p.startsWith("com.sec.");}
}
