package com.wudcompress.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.content.res.Configuration;
import android.view.View;
import android.view.WindowInsets;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.wudcompress.android.core.WudEngine;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_INPUT = 1001;
    private static final int REQ_OUTPUT = 1002;
    private static final int REQ_NOTIFICATIONS = 1003;

    private Button selectInputButton;
    private Button startButton;
    private Button aboutButton;
    private TextView inputNameText;
    private TextView modeText;
    private Switch verifySwitch;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView stageText;
    private TextView statusText;

    private Uri inputUri;
    private String inputName = "game.wud";
    private int detectedMode = -1;
    private boolean running;
    private boolean receiverRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!WudProcessService.ACTION_STATE.equals(intent.getAction())) return;
            applyServiceState(
                    intent.getBooleanExtra(WudProcessService.EXTRA_RUNNING, false),
                    intent.getIntExtra(WudProcessService.EXTRA_PROGRESS, 0),
                    intent.getStringExtra(WudProcessService.EXTRA_STAGE),
                    intent.getIntExtra(WudProcessService.EXTRA_RESULT, WudProcessService.RESULT_NONE),
                    intent.getBooleanExtra(WudProcessService.EXTRA_VERIFY, true),
                    true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectInputButton = findViewById(R.id.selectInputButton);
        startButton = findViewById(R.id.startButton);
        aboutButton = findViewById(R.id.aboutButton);
        inputNameText = findViewById(R.id.inputNameText);
        modeText = findViewById(R.id.modeText);
        verifySwitch = findViewById(R.id.verifySwitch);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        stageText = findViewById(R.id.stageText);
        statusText = findViewById(R.id.statusText);

        selectInputButton.setOnClickListener(v -> chooseInput());
        startButton.setOnClickListener(v -> chooseOutput());
        aboutButton.setOnClickListener(v -> showAbout());

        configureSystemBarsAndInsets();

        WudProcessService.Snapshot snapshot = WudProcessService.snapshot();
        applyServiceState(snapshot.running, snapshot.progress, snapshot.stage, snapshot.result, snapshot.verify, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerStateReceiver();
        WudProcessService.Snapshot snapshot = WudProcessService.snapshot();
        applyServiceState(snapshot.running, snapshot.progress, snapshot.stage, snapshot.result, snapshot.verify, false);
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void registerStateReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(WudProcessService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void configureSystemBarsAndInsets() {
        final View root = findViewById(R.id.rootScroll);
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            // These APIs exist on every Android version supported by this app
            // and include the safe area used by status/navigation bars.
            int left = windowInsets.getSystemWindowInsetLeft();
            int top = windowInsets.getSystemWindowInsetTop();
            int right = windowInsets.getSystemWindowInsetRight();
            int bottom = windowInsets.getSystemWindowInsetBottom();
            view.setPadding(
                    baseLeft + left,
                    baseTop + top,
                    baseRight + right,
                    baseBottom + bottom);
            return windowInsets;
        });
        root.requestApplyInsets();

        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_text)
                .setPositiveButton("OK", null)
                .show();
    }

    private void chooseInput() {
        if (running) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_INPUT);
    }

    private void chooseOutput() {
        if (running || inputUri == null || detectedMode < 0) return;
        String extension = detectedMode == WudEngine.MODE_WUX_TO_WUD ? ".wud" : ".wux";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, oppositeExtension(inputName, extension));
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_OUTPUT);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_INPUT) {
            loadInput(uri);
        } else if (requestCode == REQ_OUTPUT) {
            persistOutputPermission(uri);
            startProcessing(uri);
        }
    }

    private void loadInput(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        String name = displayName(uri);
        if (name == null) name = "game.wud";
        int mode = WudEngine.ERR_INPUT;

        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                FileDescriptorRandomAccess input = FileDescriptorRandomAccess.forRead(pfd.getFileDescriptor());
                mode = WudEngine.detect(input);
            }
        } catch (Throwable ignored) {
            mode = WudEngine.ERR_INPUT;
        }

        inputNameText.setText(name);
        if (mode != WudEngine.MODE_WUD_TO_WUX && mode != WudEngine.MODE_WUX_TO_WUD) {
            inputUri = null;
            detectedMode = -1;
            modeText.setText("Modo: arquivo inválido");
            statusText.setText("Não consegui acessar o arquivo como WUD/WUX.");
            startButton.setEnabled(false);
            toast("Arquivo inválido ou armazenamento sem acesso aleatório.");
            return;
        }

        inputUri = uri;
        inputName = name;
        detectedMode = mode;
        modeText.setText(mode == WudEngine.MODE_WUX_TO_WUD
                ? "Modo: Descompactar WUX → WUD"
                : "Modo: Compactar WUD → WUX");
        statusText.setText("Arquivo pronto para processar.");
        stageText.setText("Pronto");
        progressBar.setProgress(0);
        progressText.setText("0.0%");
        startButton.setEnabled(true);
    }

    private void persistOutputPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private void startProcessing(Uri outputUri) {
        Uri inUri = inputUri;
        if (inUri == null) return;

        maybeRequestNotificationPermission();
        setRunning(true);
        progressBar.setProgress(0);
        progressText.setText("0.0%");
        stageText.setText(detectedMode == WudEngine.MODE_WUX_TO_WUD ? "Descompactando" : "Compactando");
        statusText.setText("Rodando em segundo plano. Pode minimizar ou remover o app dos recentes.");

        Intent service = new Intent(this, WudProcessService.class)
                .setAction(WudProcessService.ACTION_START)
                .putExtra(WudProcessService.EXTRA_INPUT_URI, inUri.toString())
                .putExtra(WudProcessService.EXTRA_OUTPUT_URI, outputUri.toString())
                .putExtra(WudProcessService.EXTRA_VERIFY, verifySwitch.isChecked());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } catch (Throwable e) {
            setRunning(false);
            stageText.setText("Falha");
            statusText.setText("Não foi possível iniciar o processamento em segundo plano.");
            toast("Falha ao iniciar serviço.");
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void applyServiceState(
            boolean isRunning,
            int perMille,
            String stage,
            int result,
            boolean verify,
            boolean allowToast) {
        setRunning(isRunning);
        if (stage != null && !stage.isEmpty()) stageText.setText(stage);
        int safe = Math.max(0, Math.min(1000, perMille));
        progressBar.setProgress(safe);
        progressText.setText(String.format(Locale.US, "%.1f%%", safe / 10.0));

        if (isRunning) {
            statusText.setText("Rodando em segundo plano. Pode usar outros apps normalmente.");
            return;
        }

        if (result == WudProcessService.RESULT_NONE) return;
        if (result == WudEngine.OK) {
            progressBar.setProgress(1000);
            progressText.setText("100.0%");
            stageText.setText("Concluído");
            statusText.setText(verify
                    ? "Conversão concluída e verificada sem diferenças."
                    : "Conversão concluída. Verificação desativada.");
            if (allowToast) toast("Concluído!");
        } else {
            stageText.setText("Falha");
            statusText.setText(WudProcessService.errorMessage(result));
            if (allowToast) toast(WudProcessService.errorMessage(result));
        }
    }

    private void setRunning(boolean value) {
        running = value;
        selectInputButton.setEnabled(!value);
        startButton.setEnabled(!value && inputUri != null && detectedMode >= 0);
        verifySwitch.setEnabled(!value);
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String oppositeExtension(String name, String extension) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) + extension : name + extension;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
