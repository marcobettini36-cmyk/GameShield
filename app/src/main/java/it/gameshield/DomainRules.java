package it.gameshield;

import java.net.IDN;
import java.util.*;
import java.io.*;
import java.util.regex.Pattern;

/** Immutable suffix matcher; no substring matching or arbitrary regex from feeds. */
public final class DomainRules {
    private static final Pattern LABEL = Pattern.compile("[a-z0-9-]+");
    private static final Pattern IP = Pattern.compile("[0-9.]+");
    private final Set<String> domains;
    public DomainRules(Collection<String> values) {
        Set<String> out = new HashSet<>();
        for (String value : values) { String d = normalize(value); if (d != null) out.add(d); }
        domains = Collections.unmodifiableSet(out);
    }
    public int size() { return domains.size(); }
    public boolean blocks(String input) {
        String d = normalize(input);
        if (d == null) return false;
        while (true) {
            if (domains.contains(d)) return true;
            int dot = d.indexOf('.'); if (dot < 0) return false; d = d.substring(dot + 1);
        }
    }
    public static String normalize(String value) {
        if (value == null) return null;
        String d = value.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("*.")) d = d.substring(2);
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        if (!d.chars().allMatch(c -> c < 128)) {
            try { d = IDN.toASCII(d, IDN.USE_STD3_ASCII_RULES); } catch (IllegalArgumentException e) { return null; }
        }
        if (d.length() > 253 || !d.contains(".") || IP.matcher(d).matches()) return null;
        for (String label : d.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-") || !LABEL.matcher(label).matches()) return null;
        }
        return d;
    }
    public static List<String> parse(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            line = line.split("#", 2)[0].trim();
            if (line.isEmpty()) continue;
            String d = normalize(line);
            if (d == null) throw new IllegalArgumentException("Formato lista non valido");
            out.add(d);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Lista vuota");
        return out;
    }
    public static int readInto(Reader reader, Set<String> result) throws IOException {
        BufferedReader in = new BufferedReader(reader); int count = 0; String line;
        while ((line = in.readLine()) != null) {
            line = line.split("#", 2)[0].trim(); if (line.isEmpty()) continue;
            String domain = normalize(line); if (domain == null) throw new IOException("Formato lista non valido");
            result.add(domain); if (++count > 1000000) throw new IOException("Troppe regole");
        }
        if (count == 0) throw new IOException("Lista vuota"); return count;
    }
}
