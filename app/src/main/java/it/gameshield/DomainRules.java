package it.gameshield;

import java.net.IDN;
import java.util.*;

/** Immutable suffix matcher; no substring matching or arbitrary regex from feeds. */
public final class DomainRules {
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
        try { d = IDN.toASCII(d, IDN.USE_STD3_ASCII_RULES); } catch (IllegalArgumentException e) { return null; }
        if (d.length() > 253 || !d.contains(".") || d.matches("[0-9.]+")) return null;
        for (String label : d.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-") || !label.matches("[a-z0-9-]+")) return null;
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
}
