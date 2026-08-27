package com.wudcompress.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.wudcompress.android.core.WudEngine;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_INPUT = 1001;
    private static final int REQ_OUTPUT = 1002;

    private Button selectInputButton;
    private Button startButton;
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

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectInputButton = findViewById(R.id.selectInputButton);
        startButton = findViewById(R.id.startButton);
        inputNameText = findViewById(R.id.inputNameText);
        modeText = findViewById(R.id.modeText);
        verifySwitch = findViewById(R.id.verifySwitch);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        stageText = findViewById(R.id.stageText);
        statusText = findViewById(R.id.statusText);

        selectInputButton.setOnClickListener(v -> chooseInput());
        startButton.setOnClickListener(v -> chooseOutput());
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
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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

    private void startProcessing(Uri outputUri) {
        Uri inUri = inputUri;
        if (inUri == null) return;

        setRunning(true);
        progressBar.setProgress(0);
        progressText.setText("0.0%");
        stageText.setText(detectedMode == WudEngine.MODE_WUX_TO_WUD ? "Descompactando" : "Compactando");
        statusText.setText("Não feche o aplicativo durante o processamento.");

        boolean verify = verifySwitch.isChecked();
        worker.execute(() -> {
            int result = WudEngine.ERR_IO;
            try (ParcelFileDescriptor inPfd = getContentResolver().openFileDescriptor(inUri, "r");
                 ParcelFileDescriptor outPfd = getContentResolver().openFileDescriptor(outputUri, "rw")) {
                if (inPfd != null && outPfd != null) {
                    FileDescriptorRandomAccess input = FileDescriptorRandomAccess.forRead(inPfd.getFileDescriptor());
                    FileDescriptorRandomAccess output = FileDescriptorRandomAccess.forReadWrite(outPfd.getFileDescriptor());
                    result = WudEngine.process(input, output, verify, (stage, perMille) ->
                            mainHandler.post(() -> updateProgress(stage, perMille)));
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

            int finalResult = result;
            mainHandler.post(() -> finishProcessing(finalResult, verify));
        });
    }

    private void updateProgress(WudEngine.Stage stage, int perMille) {
        progressBar.setProgress(Math.max(0, Math.min(1000, perMille)));
        progressText.setText(String.format(Locale.US, "%.1f%%", perMille / 10.0));
        switch (stage) {
            case COMPRESSING:
                stageText.setText("Compactando WUD → WUX");
                break;
            case DECOMPRESSING:
                stageText.setText("Descompactando WUX → WUD");
                break;
            case VERIFYING:
                stageText.setText("Verificando byte por byte");
                break;
            case DONE:
                stageText.setText("Concluído");
                break;
            default:
                break;
        }
    }

    private void finishProcessing(int result, boolean verify) {
        setRunning(false);
        if (result == WudEngine.OK) {
            progressBar.setProgress(1000);
            progressText.setText("100.0%");
            stageText.setText("Concluído");
            statusText.setText(verify
                    ? "Conversão concluída e verificada sem diferenças."
                    : "Conversão concluída. Verificação desativada.");
            toast("Concluído!");
        } else {
            stageText.setText("Falha");
            statusText.setText(errorMessage(result));
            toast(errorMessage(result));
        }
    }

    private void setRunning(boolean value) {
        running = value;
        selectInputButton.setEnabled(!value);
        startButton.setEnabled(!value && inputUri != null && detectedMode >= 0);
        verifySwitch.setEnabled(!value);
        if (value) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private String errorMessage(int code) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}
