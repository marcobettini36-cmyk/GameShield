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
    private List<String> baseline() throws IOException {
        try (InputStream in = context.getAssets().open("gambling.txt")) {
            return DomainRules.parse(new String(LocalProxy.readAll(in, 32 * 1024 * 1024), StandardCharsets.UTF_8));
        }
    }
    public synchronized void reload() throws IOException {
        List<String> merged = baseline();
        try (InputStream in = cache.openRead()) { merged.addAll(DomainRules.parse(new String(LocalProxy.readAll(in, 32 * 1024 * 1024), StandardCharsets.UTF_8))); }
        catch (IOException | IllegalArgumentException ignored) { }
        String custom = context.getSharedPreferences("shield", 0).getString("custom", "");
        if (!custom.trim().isEmpty()) merged.addAll(DomainRules.parse(custom));
        rules = new DomainRules(merged);
    }
    public DomainRules rules() { return rules; }
    public synchronized void install(byte[] content) throws IOException {
        if (content.length > 32 * 1024 * 1024) throw new IOException("Lista troppo grande");
        List<String> parsed;
        try { parsed = DomainRules.parse(new String(content, StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
        if (parsed.size() < 20) throw new IOException("Lista incompleta");
        FileOutputStream stream = null;
        try { stream = cache.startWrite(); stream.write(content); cache.finishWrite(stream); }
        catch (IOException e) { if (stream != null) cache.failWrite(stream); throw e; }
        reload();
        context.getSharedPreferences("shield", 0).edit().putLong("updated", System.currentTimeMillis()).putInt("rules", rules.size()).apply();
    }
}
