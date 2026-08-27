package com.wudcompress.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;

import com.wudcompress.android.core.WudEngine;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service responsible for WUD/WUX conversion.
 *
 * The conversion keeps running when MainActivity is backgrounded or removed
 * from Recents. A low-priority progress notification keeps Android aware of
 * the long-running work.
 */
public final class WudProcessService extends Service {
    public static final String ACTION_START = "com.wudcompress.android.action.START";
    public static final String ACTION_STATE = "com.wudcompress.android.action.STATE";

    public static final String EXTRA_INPUT_URI = "input_uri";
    public static final String EXTRA_OUTPUT_URI = "output_uri";
    public static final String EXTRA_VERIFY = "verify";
    public static final String EXTRA_RUNNING = "running";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_STAGE = "stage";
    public static final String EXTRA_RESULT = "result";

    public static final int RESULT_NONE = Integer.MIN_VALUE;

    private static final String CHANNEL_ID = "wudcompress_processing";
    private static final int NOTIFICATION_ID = 100;

    private static volatile boolean sRunning;
    private static volatile int sProgress;
    private static volatile String sStage = "Pronto";
    private static volatile int sResult = RESULT_NONE;
    private static volatile boolean sVerify;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean taskStarted;
    private int lastNotificationProgress = -100;
    private WudEngine.Stage lastNotificationStage;
    private PowerManager.WakeLock wakeLock;

    public static final class Snapshot {
        public final boolean running;
        public final int progress;
        public final String stage;
        public final int result;
        public final boolean verify;

        private Snapshot(boolean running, int progress, String stage, int result, boolean verify) {
            this.running = running;
            this.progress = progress;
            this.stage = stage;
            this.result = result;
            this.verify = verify;
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(sRunning, sProgress, sStage, sResult, sVerify);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            return START_NOT_STICKY;
        }
        if (taskStarted || sRunning) {
            return START_REDELIVER_INTENT;
        }

        String input = intent.getStringExtra(EXTRA_INPUT_URI);
        String output = intent.getStringExtra(EXTRA_OUTPUT_URI);
        boolean verify = intent.getBooleanExtra(EXTRA_VERIFY, true);
        if (input == null || output == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        taskStarted = true;
        sRunning = true;
        sProgress = 0;
        sStage = "Preparando";
        sResult = RESULT_NONE;
        sVerify = verify;

        startAsForeground(buildNotification("Preparando", 0, true, false));
        sendStateBroadcast();
        acquireWakeLock();

        final Uri inputUri = Uri.parse(input);
        final Uri outputUri = Uri.parse(output);
        worker.execute(() -> runConversion(inputUri, outputUri, verify, startId));
        return START_REDELIVER_INTENT;
    }

    private void runConversion(Uri inputUri, Uri outputUri, boolean verify, int startId) {
        int result = WudEngine.ERR_IO;
        try (ParcelFileDescriptor inPfd = getContentResolver().openFileDescriptor(inputUri, "r");
             ParcelFileDescriptor outPfd = getContentResolver().openFileDescriptor(outputUri, "rw")) {
            if (inPfd != null && outPfd != null) {
                FileDescriptorRandomAccess input = FileDescriptorRandomAccess.forRead(inPfd.getFileDescriptor());
                FileDescriptorRandomAccess output = FileDescriptorRandomAccess.forReadWrite(outPfd.getFileDescriptor());
                result = WudEngine.process(input, output, verify, this::onEngineProgress);
            }
        } catch (OutOfMemoryError e) {
            result = WudEngine.ERR_MEMORY;
        } catch (Throwable e) {
            result = WudEngine.ERR_IO;
        }

        if (result != WudEngine.OK) {
            try {
                getContentResolver().delete(outputUri, null, null);
            } catch (Exception ignored) {
            }
        }

        sRunning = false;
        sResult = result;
        sProgress = result == WudEngine.OK ? 1000 : sProgress;
        sStage = result == WudEngine.OK ? "Concluído" : "Falha";
        sendStateBroadcast();

        releaseWakeLock();
        showFinishedNotification(result, verify);
        taskStarted = false;
        stopSelf(startId);
    }

    private void onEngineProgress(WudEngine.Stage stage, int perMille) {
        int safe = Math.max(0, Math.min(1000, perMille));
        sProgress = safe;
        sStage = stageLabel(stage);
        sResult = RESULT_NONE;
        sendStateBroadcast();

        // Do not spam NotificationManager for every 0.1% update.
        boolean stageChanged = stage != lastNotificationStage;
        if (stageChanged || safe - lastNotificationProgress >= 10 || safe == 1000) {
            lastNotificationStage = stage;
            lastNotificationProgress = safe;
            notificationManager().notify(
                    NOTIFICATION_ID,
                    buildNotification(sStage, safe, true, false));
        }
    }

    private void showFinishedNotification(int result, boolean verify) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            // minSdk is 26, kept for completeness.
            stopForeground(false);
        }

        String title = result == WudEngine.OK ? "Conversão concluída" : "Falha na conversão";
        String text = result == WudEngine.OK
                ? (verify ? "Arquivo convertido e verificado." : "Arquivo convertido.")
                : errorMessage(result);
        notificationManager().notify(
                NOTIFICATION_ID,
                buildNotification(title + " • " + text, result == WudEngine.OK ? 1000 : sProgress, false, result != WudEngine.OK));
    }

    private Notification buildNotification(String stage, int progress, boolean ongoing, boolean error) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String percent = String.format(Locale.US, "%.1f%%", progress / 10.0);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(ongoing ? "WudCompressMobile • " + stage : stage)
                .setContentText(ongoing ? percent + " • pode usar outros apps" : percent)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(1000, progress, false);
        if (error) {
            builder.setCategory(Notification.CATEGORY_ERROR);
        }
        return builder.build();
    }

    private void startAsForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Conversões WUD/WUX",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mostra o progresso enquanto a conversão roda em segundo plano.");
        channel.setSound(null, null);
        notificationManager().createNotificationChannel(channel);
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private void sendStateBroadcast() {
        Intent state = new Intent(ACTION_STATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_RUNNING, sRunning)
                .putExtra(EXTRA_PROGRESS, sProgress)
                .putExtra(EXTRA_STAGE, sStage)
                .putExtra(EXTRA_RESULT, sResult)
                .putExtra(EXTRA_VERIFY, sVerify);
        sendBroadcast(state);
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WudCompressMobile:Conversion");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Throwable ignored) {
            wakeLock = null;
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        wakeLock = null;
    }

    private static String stageLabel(WudEngine.Stage stage) {
        switch (stage) {
            case COMPRESSING:
                return "Compactando WUD → WUX";
            case DECOMPRESSING:
                return "Descompactando WUX → WUD";
            case VERIFYING:
                return "Verificando byte por byte";
            case DONE:
                return "Concluído";
            default:
                return "Processando";
        }
    }

    public static String errorMessage(int code) {
        switch (code) {
            case WudEngine.ERR_INPUT:
                return "Falha ao abrir a imagem de entrada.";
            case WudEngine.ERR_NOT_SEEKABLE:
                return "O provedor não permite acesso aleatório. Mova o arquivo para o armazenamento local.";
            case WudEngine.ERR_OUTPUT:
                return "Falha ao preparar o arquivo de saída.";
            case WudEngine.ERR_VERIFY:
                return "A verificação encontrou diferenças; a saída foi removida.";
            case WudEngine.ERR_MEMORY:
                return "Memória insuficiente para compactar esta imagem.";
            case WudEngine.ERR_FORMAT:
                return "WUX inválido ou corrompido.";
            default:
                return "Erro de leitura/gravação (" + code + ").";
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Intentionally do not stop: the foreground service should continue
        // even if the user removes the Activity from Recents.
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
