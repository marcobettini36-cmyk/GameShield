package it.gameshield;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.net.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import hev.htproxy.TProxyService;
public final class ShieldVpnService extends VpnService {
    private ParcelFileDescriptor tunnel;
    private LocalProxy proxy;
    private RuleStore store;
    private ProtectedNetwork network;
    private ScheduledExecutorService worker;
    private final ExecutorService probes = Executors.newSingleThreadExecutor();
    private volatile boolean active;
    private volatile Network lastUnderlying;
    private int consecutiveOutages;
    private final java.util.concurrent.atomic.AtomicBoolean checking = new java.util.concurrent.atomic.AtomicBoolean();
    private final Object lifecycle = new Object();
    public static volatile boolean running;
    @Override public void onCreate() {
        super.onCreate(); worker = Executors.newScheduledThreadPool(3);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("vpn", "Protezione GameShield", NotificationManager.IMPORTANCE_LOW));
    }
    private void foreground(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note = new Notification.Builder(this, "vpn").setSmallIcon(R.drawable.shield).setContentTitle(getString(R.string.app_name)).setContentText(text).setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, note, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, note);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        foreground("Avvio della protezione…");
        if (active) {
            if (BuildConfig.DEBUG && intent != null && "TEST_BREAK_PROXY".equals(intent.getAction())) {
                proxy.close(); worker.execute(this::connectivityTest);
                return BuildConfig.STRONG ? START_STICKY : START_NOT_STICKY;
            }
            if (intent != null && "RELOAD".equals(intent.getAction())) worker.execute(() -> { try { store.reload(); getSharedPreferences("shield", 0).edit().putInt("rules", store.rules().size()).apply(); } catch (Exception e) { getSharedPreferences("shield", 0).edit().putString("error", e.getMessage()).apply(); } });
            else if (intent != null && "UPDATE".equals(intent.getAction())) worker.execute(this::update);
            else foreground("Filtro VPN attivo");
            return BuildConfig.STRONG ? START_STICKY : START_NOT_STICKY;
        }
        active = true;
        worker.execute(() -> {
            try {
                store = new RuleStore(this); network = new ProtectedNetwork(this, store);
                synchronized (lifecycle) {
                if (!active) return;
                byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
                String password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                proxy = new LocalProxy(network, "gameshield", password);
                Builder builder = new Builder().setSession("GameShield").setMtu(1500)
                    .addAddress("198.18.0.1", 32).addAddress("fd42:4753::1", 128)
                    .addDnsServer("198.18.0.2").addRoute("0.0.0.0", 0).addRoute("::", 0).setBlocking(false);
                Network physical = network.underlying();
                lastUnderlying = physical;
                if (physical != null) builder.setUnderlyingNetworks(new Network[]{physical});
                tunnel = builder.establish(); if (tunnel == null) throw new IOException("Consenso VPN mancante");
                String config = "tunnel:\n  mtu: 1500\n  ipv4: 198.18.0.1\n  ipv6: 'fd42:4753::1'\nsocks5:\n  address: 127.0.0.1\n  port: " + proxy.port() + "\n  udp: 'udp'\n  username: 'gameshield'\n  password: '" + password + "'\nmisc:\n  max-session-count: 128\n  tcp-read-write-timeout: 1800000\n  log-level: error\n";
                File file = new File(getFilesDir(), "tunnel.yml");
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(config.getBytes(StandardCharsets.UTF_8)); }
                if (!TProxyService.TProxyStartService(file.getAbsolutePath(), tunnel.getFd())) throw new IOException("Motore tunnel non avviato");
                running = true;
                }
                getSharedPreferences("shield", 0).edit().putBoolean("wanted", true).putBoolean("connectivityOk", false)
                    .putString("connectivity", "Verifica DNS, TCP 443 e HTTPS in corso…").remove("error").putInt("rules", store.rules().size()).apply();
                foreground("Verifica connettività nel tunnel…");
                worker.scheduleWithFixedDelay(this::health, 2, 2, TimeUnit.SECONDS);
                worker.scheduleWithFixedDelay(this::connectivityTest, 2, 15, TimeUnit.SECONDS);
                worker.scheduleWithFixedDelay(this::update, 1, 12 * 60 * 60, TimeUnit.SECONDS);
            } catch (Exception | LinkageError e) {
                failure("Avvio fallito: " + e.getMessage());
            }
        });
        return BuildConfig.STRONG ? START_STICKY : START_NOT_STICKY;
    }
    private void connectivityTest() {
        if (!active || !running || !checking.compareAndSet(false, true)) return;
        Network initialPhysical = network.underlying();
        Future<ConnectivityProbe.Result> check = probes.submit(() -> {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            Network vpn = null;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { vpn = n; break; }
            }
            return ConnectivityProbe.check(vpn);
        });
        try {
            ConnectivityProbe.Result result = check.get(120, TimeUnit.SECONDS);
            if (!active) return;
            String report = result.toString();
            boolean physicalWorks = false;
            if (!result.usable() && initialPhysical != null) {
                Future<ConnectivityProbe.Result> baseline = probes.submit(() -> ConnectivityProbe.check(initialPhysical, false));
                try { physicalWorks = baseline.get(120, TimeUnit.SECONDS).usable(); }
                catch (Exception error) { baseline.cancel(true); }
            }
            // A host outage, offline phone or network handover is not evidence of a broken tunnel.
            boolean confirmedOutage = !result.usable() && physicalWorks
                && Objects.equals(initialPhysical, network.underlying());
            consecutiveOutages = confirmedOutage ? consecutiveOutages + 1 : 0;
            report += confirmedOutage ? "\nTunnel inutilizzabile, rete fisica OK: tentativo " + consecutiveOutages + "/3"
                : (!result.usable() ? "\nRete fisica non verificata o in transizione: VPN mantenuta" : "");
            android.util.Log.i("GameShieldConnectivity", report);
            getSharedPreferences("shield", 0).edit().putBoolean("connectivityOk", result.complete())
                .putString("connectivity", report).putInt("consecutiveOutages", consecutiveOutages).remove("error").apply();
            if (consecutiveOutages >= 3) { failure("Tre verifiche consecutive: nessun HTTPS nel tunnel, HTTPS fisico funzionante. " + report); return; }
            foreground(result.complete() ? "Filtro VPN attivo  /  Internet verificato" : "VPN attiva  /  verifica connettivita parziale");
        } catch (Exception e) {
            check.cancel(true);
            // A timeout or interrupted diagnostic does not prove a data-path outage.
            consecutiveOutages = 0;
            if (active) getSharedPreferences("shield", 0).edit().putBoolean("connectivityOk", false)
                .putString("connectivity", "Self-test incompleto; VPN mantenuta: " + e).apply();
        } finally { checking.set(false); }
    }
    private void failure(String reason) {
        getSharedPreferences("shield", 0).edit().putBoolean("connectivityOk", false)
            .putString("error", reason + (BuildConfig.STRONG ? "; Strong mantiene la protezione" : "; VPN rimossa per ripristinare Internet. Filtro disattivato."))
            .putString("connectivity", reason).putBoolean("wanted", BuildConfig.STRONG).apply();
        if (BuildConfig.STRONG) { foreground("Errore connettività • serve verifica del custode"); return; }
        // Close TUN immediately, before joining native threads: restore Android's physical routes.
        active = false; running = false;
        synchronized (lifecycle) {
            if (tunnel != null) try { tunnel.close(); } catch (IOException ignored) { }
            tunnel = null;
        }
        stopSelf();
    }
    private void health() {
        if (!active) return;
        Network physical = network.underlying();
        if (!Objects.equals(physical, lastUnderlying)) {
            setUnderlyingNetworks(physical == null ? null : new Network[]{physical}); lastUnderlying = physical;
        }
        if (!TProxyService.TProxyIsRunning()) {
            running = false;
            failure("Motore tunnel interrotto"); return;
        }
        getSharedPreferences("shield", 0).edit().putLong("blocked", network.blocked.get()).apply();
    }
    private void update() {
        if (store == null || network == null) return;
        try { store.install(network.downloadFeed()); getSharedPreferences("shield", 0).edit().remove("updateError").apply(); }
        catch (Exception e) { getSharedPreferences("shield", 0).edit().putString("updateError", "Aggiornamento non riuscito; resta valida la lista precedente: " + e.getMessage()).apply(); }
    }
    @Override public void onRevoke() { getSharedPreferences("shield", 0).edit().putBoolean("wanted", false).apply(); stopSelf(); }
    @Override public void onDestroy() {
        active = false; running = false;
        if (worker != null) worker.shutdownNow();
        probes.shutdownNow();
        synchronized (lifecycle) {
            // Release VPN routes before a potentially slow native shutdown.
            if (tunnel != null) try { tunnel.close(); } catch (IOException ignored) { }
            tunnel = null;
            try { TProxyService.TProxyStopService(); } catch (LinkageError ignored) { }
            if (proxy != null) proxy.close();
        }
        super.onDestroy();
    }
}
