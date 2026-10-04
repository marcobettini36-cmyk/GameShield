package it.gameshield;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private TextView status, detail, counters;
    private Button start, stop, strong;
    private boolean consentPending;
    private android.content.SharedPreferences prefs;
    private final Runnable refresh = new Runnable() { public void run() { renderStatus(); handler.postDelayed(this, 2000); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs = getSharedPreferences("shield", 0);
        getWindow().setStatusBarColor(Color.rgb(7, 45, 54)); getWindow().setNavigationBarColor(Color.rgb(7, 45, 54));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(28), dp(22), dp(32)); root.setBackgroundColor(Color.rgb(241, 247, 247)); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> { root.setPadding(dp(22), dp(28) + insets.getSystemWindowInsetTop(), dp(22), dp(32) + insets.getSystemWindowInsetBottom()); return insets; });
        TextView brand = text("GAMESHIELD" + (BuildConfig.STRONG ? "  /  STRONG" : ""), 14, 0xff087f8c); brand.setLetterSpacing(.12f); root.addView(brand);
        root.addView(text("Più spazio alla tua vita.", 30, 0xff072d36));
        root.addView(text("Un filtro locale per ridurre l’accesso al gioco d’azzardo online.", 16, 0xff47636b));
        LinearLayout card = card(root);
        status = text("Protezione disattivata", 23, 0xff072d36); card.addView(status);
        detail = text("", 14, 0xff47636b); card.addView(detail);
        counters = text("", 15, 0xff087f8c); card.addView(counters);
        start = button(card, "Attiva protezione", this::startProtection);
        stop = button(card, "Disattiva protezione", () -> {
            if (new StrongPolicy(this).locked()) { toast("Serve il rilascio del custode"); return; }
            if (!BuildConfig.STRONG) { startActivity(new Intent().setClassName(this, "it.gameshield.NormalDisableActivity")); return; }
            prefs.edit().putBoolean("wanted", false).apply(); startService(new Intent(this, ShieldVpnService.class).setAction("STOP")); renderStatus();
        });
        button(card, "Aggiorna blacklist", () -> {
            if (!ShieldVpnService.running) { toast("Attiva prima la VPN"); return; }
            startForegroundService(new Intent(this, ShieldVpnService.class).setAction("UPDATE")); toast("Aggiornamento avviato");
        });
        if (BuildConfig.STRONG) {
            LinearLayout owner = card(root); owner.addView(text("Protezione Strong", 21, 0xff072d36));
            owner.addView(text("Richiede Device Owner su un dispositivo dedicato. Il custode imposta il codice prima di bloccare disinstallazione e modifica della VPN. Conserva il codice: non esiste un recupero automatico.", 14, 0xff47636b));
            button(owner, "Imposta codice custode", this::setupCode);
            strong = button(owner, "Applica protezioni Device Owner", () -> {
                if (!ShieldVpnService.running) { toast("Attiva e verifica prima la VPN"); return; }
                new AlertDialog.Builder(this).setTitle("Affida il dispositivo al custode")
                    .setMessage("Verranno bloccati disinstallazione, modifica VPN, debug USB, modalità sicura e ripristino dalle impostazioni. La VPN sarà always-on con lockdown. Un guasto VPN può interrompere Internet: il custode potrà rilasciare il dispositivo usando il codice, anche offline.")
                    .setNegativeButton("Annulla", null).setPositiveButton("Applica", (d, w) -> {
                        try { new StrongPolicy(this).enable(); renderStatus(); toast("Protezioni Strong applicate"); }
                        catch (Exception e) { toast("Configurazione non completata: " + e.getMessage()); }
                    }).show();
            });
            button(owner, "Rilascio del custode", this::release);
            button(owner, "Istruzioni Device Owner", () -> new AlertDialog.Builder(this).setTitle("Provisioning Strong")
                .setMessage("Su dispositivo di test appena ripristinato, senza account, installa solo Strong e usa ADB:\n\nadb shell dpm set-device-owner it.gameshield.strong/it.gameshield.AdminReceiver\n\nPoi il custode imposta il codice, verifica che la VPN navighi e applica le protezioni. Le restrizioni possono dipendere dal produttore. Non applicarle al tuo unico dispositivo senza aver provato il rilascio.")
                .setPositiveButton("Chiudi", null).show());
        }
        if (!BuildConfig.STRONG) button(root, "Impostazioni Normal", () -> startActivity(new Intent().setClassName(this, "it.gameshield.NormalSettingsActivity")));
        LinearLayout tools = card(root); tools.addView(text("Le tue regole", 21, 0xff072d36));
        button(tools, "Aggiungi dominio o mirror", () -> input("Blocca un dominio", false, value -> {
            String domain = DomainRules.normalize(value); if (domain == null) { toast("Inserisci un dominio, senza https o percorso"); return; }
            prefs.edit().putString("custom", prefs.getString("custom", "") + domain + "\n").apply();
            if (ShieldVpnService.running) startForegroundService(new Intent(this, ShieldVpnService.class).setAction("RELOAD"));
            toast("Dominio e sottodomini aggiunti");
        }));
        button(tools, "Verifica un dominio", () -> input("Verifica blacklist", false, domain -> background.execute(() -> {
            try { RuleStore store = new RuleStore(this); boolean blocked = store.rules().blocks(domain); runOnUiThread(() -> toast(blocked ? "Dominio presente nella blacklist" : "Dominio non presente: puoi aggiungerlo")); }
            catch (Exception e) { runOnUiThread(() -> toast("Verifica fallita: " + e.getMessage())); }
        })));
        button(tools, "Impostazioni VPN Android", () -> startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)));
        LinearLayout help = card(root); help.addView(text("Un aiuto in più", 21, 0xff072d36));
        help.addView(text("GameShield blocca domini conosciuti e sottodomini. Nuovi mirror, IP diretti, DNS cifrato personalizzato ed ECH possono superare il filtro. Una sola VPN può essere attiva. DNS della rete, con fallback Cloudflare/Quad9; nessuna intercettazione HTTPS e nessuna cronologia di navigazione salvata.", 14, 0xff47636b));
        button(help, "Autoesclusione ADM", () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://arserviziam.adm.gov.it/static/pudm_rua/index.html"))));
        help.addView(text("Se il gioco ti mette in difficoltà, rivolgiti al SerD della tua zona o a una persona di fiducia.", 14, 0xff47636b));
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
    }
    private void startProtection() {
        new AlertDialog.Builder(this).setTitle("Consenso alla VPN locale")
            .setMessage("GameShield instrada il traffico nel dispositivo per filtrare i domini. Non invia il traffico a un server VPN. Le richieste DNS consentite usano i resolver della rete, con fallback Cloudflare o Quad9. La Normal disattiva il filtro se il tunnel impedisce la connettività. Questo sostituisce eventuali altre VPN attive.")
            .setNegativeButton("Annulla", null).setPositiveButton("Continua", (d, w) -> {
                Intent consent = android.net.VpnService.prepare(this);
                if (consent == null) launch(); else { consentPending = true; startActivityForResult(consent, 1); }
            }).show();
    }
    private void launch() { startForegroundService(new Intent(this, ShieldVpnService.class)); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1 && consentPending) { consentPending = false; if (result == RESULT_OK) launch(); }
    }
    private void setupCode() {
        if (new Guardian(this).configured()) { toast("Il codice è già configurato"); return; }
        input("Il custode inserisce un codice di almeno 8 caratteri", true, first -> input("Ripeti il codice custode", true, second -> {
            if (!first.equals(second)) { toast("I codici non coincidono"); return; }
            background.execute(() -> { try { new Guardian(this).setup(first.toCharArray()); runOnUiThread(() -> toast("Codice custode salvato")); } catch (Exception e) { runOnUiThread(() -> toast(e.getMessage())); } });
        }));
    }
    private void release() {
        input("Codice custode: rimuove Device Owner e ferma la VPN", true, code -> background.execute(() -> {
            try { new StrongPolicy(this).release(code.toCharArray()); runOnUiThread(() -> { toast("Dispositivo rilasciato"); renderStatus(); }); }
            catch (Exception e) { runOnUiThread(() -> toast(e.getMessage())); }
        }));
    }
    private void renderStatus() {
        if (status == null) return;
        boolean running = ShieldVpnService.running, locked = new StrongPolicy(this).locked();
        boolean connected = prefs.getBoolean("connectivityOk", false);
        status.setText(running ? (connected ? "Protezione attiva" : "Verifica connettività VPN") : "Protezione disattivata");
        String message = locked ? "Strong: disinstallazione bloccata e VPN vincolata" : "VPN locale • filtro DNS, HTTP Host e TLS SNI";
        if (prefs.contains("error")) message += "\n" + prefs.getString("error", "");
        if (prefs.contains("updateError")) message += "\n" + prefs.getString("updateError", "");
        if (prefs.contains("connectivity")) message += "\n" + prefs.getString("connectivity", "");
        detail.setText(message);
        long updated = prefs.getLong("updated", 0);
        counters.setText(prefs.getInt("rules", 0) + " domini • " + prefs.getLong("blocked", 0) + " blocchi in questa sessione\n" + (updated == 0 ? "Lista inclusa nell’app" : "Aggiornata: " + DateFormat.getDateTimeInstance().format(new Date(updated))));
        start.setEnabled(!running); stop.setEnabled(running && !locked);
        if (strong != null) strong.setEnabled(running && connected && new StrongPolicy(this).owner() && new Guardian(this).configured() && !locked);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { background.shutdownNow(); super.onDestroy(); }
    private TextView text(String content, int size, int color) { TextView v = new TextView(this); v.setText(content); v.setTextSize(size); v.setTextColor(color); v.setPadding(0, dp(5), 0, dp(10)); if (size >= 20) v.setTypeface(null, Typeface.BOLD); return v; }
    private LinearLayout card(LinearLayout root) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(18), dp(14), dp(18), dp(14)); GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18)); v.setBackground(bg); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(18); root.addView(v, p); return v; }
    private Button button(LinearLayout root, String label, Runnable action) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(0xff087f8c); b.setOnClickListener(v -> action.run()); root.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b; }
    private void input(String title, boolean secret, java.util.function.Consumer<String> action) { EditText edit = new EditText(this); edit.setSingleLine(true); edit.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); new AlertDialog.Builder(this).setTitle(title).setView(edit).setNegativeButton("Annulla", null).setPositiveButton("Conferma", (d, w) -> { String value = edit.getText().toString(); edit.setText(""); action.accept(value); }).show(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
