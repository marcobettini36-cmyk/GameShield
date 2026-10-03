package it.gameshield;

import android.app.*;
import android.content.*;
import android.net.VpnService;
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
    private volatile boolean active;
    public static volatile boolean running;
    @Override public void onCreate() {
        super.onCreate(); worker = Executors.newSingleThreadScheduledExecutor();
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
            if (intent != null && "RELOAD".equals(intent.getAction())) worker.execute(() -> { try { store.reload(); getSharedPreferences("shield", 0).edit().putInt("rules", store.rules().size()).apply(); } catch (Exception e) { getSharedPreferences("shield", 0).edit().putString("error", e.getMessage()).apply(); } });
            else if (intent != null && "UPDATE".equals(intent.getAction())) worker.execute(this::update);
            else foreground("Filtro VPN attivo");
            return START_STICKY;
        }
        active = true;
        worker.execute(() -> {
            try {
                store = new RuleStore(this); network = new ProtectedNetwork(this, store);
                byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
                String password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                proxy = new LocalProxy(network, "gameshield", password);
                Builder builder = new Builder().setSession("GameShield").setMtu(1500)
                    .addAddress("198.18.0.1", 32).addAddress("fd42:4753::1", 128)
                    .addDnsServer("198.18.0.2").addRoute("0.0.0.0", 0).addRoute("::", 0).setBlocking(false);
                tunnel = builder.establish(); if (tunnel == null) throw new IOException("Consenso VPN mancante");
                String config = "tunnel:\n  mtu: 1500\n  ipv4: 198.18.0.1\n  ipv6: 'fd42:4753::1'\nsocks5:\n  address: 127.0.0.1\n  port: " + proxy.port() + "\n  udp: 'udp'\n  username: 'gameshield'\n  password: '" + password + "'\nmisc:\n  max-session-count: 64\n  tcp-read-write-timeout: 300000\n  log-level: error\n";
                File file = new File(getFilesDir(), "tunnel.yml");
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(config.getBytes(StandardCharsets.UTF_8)); }
                if (!TProxyService.TProxyStartService(file.getAbsolutePath(), tunnel.getFd())) throw new IOException("Motore tunnel non avviato");
                running = true;
                getSharedPreferences("shield", 0).edit().putBoolean("wanted", true).remove("error").putInt("rules", store.rules().size()).apply();
                foreground("Filtro VPN attivo");
                worker.scheduleWithFixedDelay(this::health, 2, 2, TimeUnit.SECONDS);
                worker.scheduleWithFixedDelay(this::update, 1, 12 * 60 * 60, TimeUnit.SECONDS);
            } catch (Exception | LinkageError e) {
                getSharedPreferences("shield", 0).edit().putString("error", "Avvio fallito: " + e.getMessage()).apply(); stopSelf();
            }
        });
        return START_STICKY;
    }
    private void health() {
        if (!active) return;
        if (!TProxyService.TProxyIsRunning()) {
            running = false; getSharedPreferences("shield", 0).edit().putString("error", "Tunnel interrotto; riattiva la protezione").apply(); stopSelf(); return;
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
        try { TProxyService.TProxyStopService(); } catch (LinkageError ignored) { }
        if (proxy != null) proxy.close();
        if (tunnel != null) try { tunnel.close(); } catch (IOException ignored) { }
        super.onDestroy();
    }
}
