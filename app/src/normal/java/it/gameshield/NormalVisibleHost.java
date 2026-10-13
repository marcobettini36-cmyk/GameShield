package it.gameshield;

import java.net.URI;

/** Only the browser address control is passed here; never arbitrary page text. */
final class NormalVisibleHost {
    static String parse(String text) {
        if(text==null || text.length()>2048)return null;
        String s=text.trim();if(s.isEmpty() || s.matches(".*\\s.*") || s.contains("@"))return null;
        try {
            URI u=new URI(s.contains("://")?s:"https://"+s);
            if(!"https".equalsIgnoreCase(u.getScheme())&&!"http".equalsIgnoreCase(u.getScheme()))return null;
            return DomainRules.normalize(u.getHost());
        }catch(java.net.URISyntaxException e){return null;}
    }
}
