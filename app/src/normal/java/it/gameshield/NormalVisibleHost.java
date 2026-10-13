package it.gameshield;

import java.net.URI;

/** Only the browser address control is passed here; never arbitrary page text. */
final class NormalVisibleHost {
    static String parse(String text) {
        if(text==null)return null;
        String s=text.trim();if(s.isEmpty())return null;
        int start=s.regionMatches(true,0,"https://",0,8)?8:s.regionMatches(true,0,"http://",0,7)?7:0;
        int end=s.length();for(char delimiter:new char[]{'/','?','#'}){int p=s.indexOf(delimiter,start);if(p>=0)end=Math.min(end,p);}
        // Query strings (including @, spaces and long/partially typed escapes) cannot hide the host.
        String authority=s.substring(start,end);if(authority.length()>2048 || authority.matches(".*\\s.*"))return null;
        int colon=authority.indexOf(':');if(start==0 && colon>=0 && !authority.substring(colon+1).matches("[0-9]+"))return null;
        try {
            URI u=new URI("https://"+authority);String host=u.getHost();
            if(host==null){host=u.getRawAuthority();if(host==null)return null;int at=host.lastIndexOf('@');if(at>=0)host=host.substring(at+1);int colon=host.lastIndexOf(':');if(colon>=0&&host.substring(colon+1).matches("[0-9]+"))host=host.substring(0,colon);}
            return DomainRules.normalize(host);
        }catch(java.net.URISyntaxException e){return null;}
    }
}
