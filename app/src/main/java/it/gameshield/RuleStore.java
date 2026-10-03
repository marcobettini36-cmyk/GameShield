package it.gameshield;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RuleStore {
    public static final String FEED_HOST = "raw.githubusercontent.com";
    public static final String FEED_PATH = "/marcobettini36-cmyk/GameShield/main/feeds/gambling.txt";
    private final Context context;
    private final AtomicFile cache;
    private volatile DomainRules rules;
    public RuleStore(Context c) throws IOException {
        context = c; cache = new AtomicFile(new File(c.getFilesDir(), "gambling.txt")); reload();
    }
    private Set<String> baseline() throws IOException {
        Set<String> result = new HashSet<>();
        try (InputStream in = context.getAssets().open("gambling.txt")) {
            DomainRules.readInto(new InputStreamReader(in, StandardCharsets.UTF_8), result);
        }
        return result;
    }
    public synchronized void reload() throws IOException {
        Set<String> merged = new HashSet<>();
        try (InputStream in = cache.openRead()) { DomainRules.readInto(new InputStreamReader(in, StandardCharsets.UTF_8), merged); }
        catch (IOException | IllegalArgumentException ignored) { merged = baseline(); }
        applyCustom(merged);
    }
    private void applyCustom(Set<String> merged) {
        String custom = context.getSharedPreferences("shield", 0).getString("custom", "");
        if (!custom.trim().isEmpty()) merged.addAll(DomainRules.parse(custom));
        rules = new DomainRules(merged);
    }
    public DomainRules rules() { return rules; }
    public synchronized void install(byte[] content) throws IOException {
        if (content.length > 32 * 1024 * 1024) throw new IOException("Lista troppo grande");
        Set<String> parsed = new HashSet<>();
        DomainRules.readInto(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8), parsed);
        if (parsed.size() < 20) throw new IOException("Lista incompleta");
        if (rules != null && parsed.size() < rules.size() * 0.7) throw new IOException("Riduzione della lista superiore al 30%; aggiornamento rifiutato");
        FileOutputStream stream = null;
        try { stream = cache.startWrite(); stream.write(content); cache.finishWrite(stream); }
        catch (IOException e) { if (stream != null) cache.failWrite(stream); throw e; }
        applyCustom(parsed);
        context.getSharedPreferences("shield", 0).edit().putLong("updated", System.currentTimeMillis()).putInt("rules", rules.size()).apply();
    }
}
