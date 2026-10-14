package it.gameshield;

/** Hostnames only. No account, token, IP, URL, generic NextDNS resolver or credentials. */
final class NormalDnsConfig {
    static String hostname(String value) {
        if(value==null)return null;
        String s=value.trim().toLowerCase(java.util.Locale.ROOT);
        if(s.length()>253 || !s.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") || !s.contains("."))return null;
        for(String label:s.split("\\.",-1))if(label.isEmpty()||label.length()>63||label.startsWith("-")||label.endsWith("-"))return null;
        if(s.matches("[0-9.]+") || s.equals("dns.nextdns.io") || s.equals("my.nextdns.io") || s.equals("nextdns.io") || s.equals("www.nextdns.io"))return null;
        if(s.endsWith(".dns.nextdns.io")&&!s.matches("(?:[a-z0-9-]+-)?[a-f0-9]{6}\\.dns\\.nextdns\\.io"))return null;
        return s;
    }
}
