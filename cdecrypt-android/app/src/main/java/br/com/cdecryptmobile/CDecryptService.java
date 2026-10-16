package br.com.cdecryptmobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class CDecryptService extends Service {
    static { System.loadLibrary("cdecrypt_jni"); }
    public static final String ACTION_START = "br.com.cdecryptmobile.START";
    public static final String ACTION_STATE = "br.com.cdecryptmobile.STATE";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_DEST = "dest";
    public static final String EXTRA_RUNNING = "running";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_STAGE = "stage";
    public static final String EXTRA_RESULT = "result";
    public static final String EXTRA_REPORT = "report";
    public static final String EXTRA_OUTPUT = "output";
    public static final int NO_RESULT = -9999;

    private static final String CHANNEL = "cdecrypt_progress";
    private static final int NOTIFICATION = 304;
    private static volatile boolean running = false;
    private static volatile int progress = 0;
    private static volatile int result = NO_RESULT;
    private static volatile String stage = "Pronto";
    private static volatile String report = "";
    private static volatile String output = "";
    private static volatile CDecryptService active;

    private PowerManager.WakeLock lock;
    private boolean taskStarted = false;
    private int lastNotified = -20;
    private String lastNotifiedStage = "";
    private static native int nativeDecrypt(String input, String output, String logFile);

    static final class Snapshot {
        final boolean running;
        final int progress;
        final int result;
        final String stage;
        final String report;
        final String output;
        Snapshot() {
            this.running = CDecryptService.running;
            this.progress = CDecryptService.progress;
            this.result = CDecryptService.result;
            this.stage = CDecryptService.stage;
            this.report = CDecryptService.report;
            this.output = CDecryptService.output;
        }
    }

    static Snapshot snapshot(Context context) {
        if (!running && result == NO_RESULT) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("last_run", MODE_PRIVATE);
            result = prefs.getInt("result", NO_RESULT);
            stage = prefs.getString("stage", "Pronto");
            output = prefs.getString("output", "");
            report = prefs.getString("report", "");
            progress = prefs.getInt("progress", 0);
        }
        return new Snapshot();
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Extração Wii U", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Extração em segundo plano e progresso real.");
        channel.setSound(null, null);
        manager().createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        if (taskStarted || running) return START_NOT_STICKY;
        String sourcePath = intent.getStringExtra(EXTRA_SOURCE);
        String destPath = intent.getStringExtra(EXTRA_DEST);
        if (sourcePath == null || destPath == null) { stopSelf(startId); return START_NOT_STICKY; }

        taskStarted = true;
        active = this;
        running = true;
        progress = 0;
        stage = "Preparando arquivos";
        result = NO_RESULT;
        report = "";
        output = destPath;
        notifyForeground(notification(stage, 0, true));
        broadcast();
        acquireLock();
        new Thread(() -> work(sourcePath, destPath, startId), "wiiu-decryption").start();
        return START_NOT_STICKY;
    }

    private void work(String sourcePath, String destPath, int startId) {
        int code;
        File dir = new File(destPath);
        File source = new File(sourcePath);
        File log = new File(getFilesDir(), "last_decrypt_report.txt");
        try {
            if (!source.isDirectory() || !new File(source, "title.tmd").isFile()
                    || !new File(source, "title.tik").isFile()) {
                code = -10;
            } else if (dir.exists() || !dir.mkdirs()) {
                code = -11;
            } else {
                code = nativeDecrypt(source.getAbsolutePath(), dir.getAbsolutePath(), log.getAbsolutePath());
            }
        } catch (Throwable ex) {
            code = -100;
            report = "Exceção Android: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }

        if (code == -10) {
            report = "A pasta de entrada está sem title.tmd ou title.tik.";
        } else if (code == -11) {
            report = "A pasta de saída já existe ou não pôde ser criada.";
        } else if (code != -100) {
            report = readTail(log);
        }

        result = code;
        running = false;
        progress = code == 0 ? 1000 : progress;
        stage = code == 0 ? "Concluído" : "Falha • código " + code;
        getSharedPreferences("last_run", MODE_PRIVATE).edit()
                .putInt("result", result).putInt("progress", progress)
                .putString("stage", stage).putString("report", report)
                .putString("output", output).apply();
        broadcast();
        releaseLock();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH);
        manager().notify(NOTIFICATION, notification(
                code == 0 ? "Extração concluída" : "Falha: veja o relatório", progress, false));
        active = null;
        taskStarted = false;
        stopSelf(startId);
    }

    private static String readTail(File file) {
        if (!file.isFile()) return "Relatório não gerado.";
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            int read = (int) Math.min(len, 18000);
            byte[] bytes = new byte[read];
            raf.seek(len - read);
            raf.readFully(bytes);
            return (len > read ? "[Últimos 18 KB]\n" : "") + new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) { return "Falha ao ler relatório: " + ex.getMessage(); }
    }

    /* Called by C after actual output bytes have been written. */
    @SuppressWarnings("unused")
    private static void nativeProgress(int perMille, String fileName) {
        CDecryptService service = active;
        if (service == null || !running) return;
        int safe = Math.max(0, Math.min(999, perMille));
        boolean changed = safe != progress || (fileName != null && !fileName.equals(stage));
        if (!changed) return;
        progress = safe;
        stage = (fileName == null || fileName.isEmpty()) ? "Extraindo arquivos" : "Extraindo: " + fileName;
        service.broadcast();
        if (safe - service.lastNotified >= 10
                || !service.lastNotifiedStage.equals(stage) && safe == 0) {
            service.lastNotified = safe;
            service.lastNotifiedStage = stage;
            service.manager().notify(NOTIFICATION, service.notification(stage, safe, true));
        }
    }

    private void broadcast() {
        Intent intent = new Intent(ACTION_STATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_RUNNING, running)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_STAGE, stage)
                .putExtra(EXTRA_RESULT, result)
                .putExtra(EXTRA_REPORT, report)
                .putExtra(EXTRA_OUTPUT, output);
        sendBroadcast(intent);
    }

    private Notification notification(String message, int perMille, boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String label = String.format(Locale.US, "%.1f%%", perMille / 10.0);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setContentTitle(ongoing ? "CDecrypt Android" : message)
                .setContentText(ongoing ? label + " • " + message : "Toque para ver os detalhes")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setProgress(1000, perMille, false);
        return b.build();
    }

    private NotificationManager manager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
    private void notifyForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION, n);
    }
    private void acquireLock() {
        try {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CDecryptAndroid:Extraction");
            lock.setReferenceCounted(false);
            lock.acquire();
        } catch (Throwable ignored) { lock = null; }
    }
    private void releaseLock() {
        if (lock != null && lock.isHeld()) lock.release();
        lock = null;
    }
    @Override public void onTaskRemoved(Intent root) {
        // Keep foreground extraction running after the activity is dismissed.
        super.onTaskRemoved(root);
    }
    @Override public void onDestroy() {
        releaseLock();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
