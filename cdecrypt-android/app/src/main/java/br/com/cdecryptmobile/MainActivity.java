package br.com.cdecryptmobile;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.content.ActivityNotFoundException;
import android.view.View;
import android.content.res.Configuration;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_SOURCE = 101;
    private static final int PICK_DEST = 102;
    private static final int REQ_NOTIFICATION = 103;
    private static final int REQ_ACCESS = 104;
    private TextView inputText, destText, statusText, stageText, percentText, reportText;
    private ProgressBar progressBar;
    private Button sourceButton, destButton, startButton, permissionButton, copyButton;
    private File source, dest;
    private boolean running;
    private boolean registered;
    private String lastReport = "";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!CDecryptService.ACTION_STATE.equals(intent.getAction())) return;
            showState(intent.getBooleanExtra(CDecryptService.EXTRA_RUNNING, false),
                    intent.getIntExtra(CDecryptService.EXTRA_PROGRESS, 0),
                    intent.getStringExtra(CDecryptService.EXTRA_STAGE),
                    intent.getIntExtra(CDecryptService.EXTRA_RESULT, CDecryptService.NO_RESULT),
                    intent.getStringExtra(CDecryptService.EXTRA_REPORT),
                    intent.getStringExtra(CDecryptService.EXTRA_OUTPUT));
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sourceButton = findViewById(R.id.selectInputButton);
        destButton = findViewById(R.id.selectOutputButton);
        startButton = findViewById(R.id.startButton);
        permissionButton = findViewById(R.id.permissionButton);
        copyButton = findViewById(R.id.copyButton);
        inputText = findViewById(R.id.inputNameText);
        destText = findViewById(R.id.outputText);
        statusText = findViewById(R.id.statusText);
        stageText = findViewById(R.id.stageText);
        percentText = findViewById(R.id.progressText);
        reportText = findViewById(R.id.reportText);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(1000);
        sourceButton.setOnClickListener(v -> chooseFolder(PICK_SOURCE));
        destButton.setOnClickListener(v -> chooseFolder(PICK_DEST));
        permissionButton.setOnClickListener(v -> requestFilePermission());
        startButton.setOnClickListener(v -> startExtraction());
        copyButton.setOnClickListener(v -> {
            if (lastReport == null || lastReport.isEmpty()) {
                Toast.makeText(this, "Ainda não há relatório.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("CDecrypt log", lastReport));
            Toast.makeText(this, "Relatório copiado", Toast.LENGTH_SHORT).show();
        });

        android.content.SharedPreferences prefs = getSharedPreferences("folders", MODE_PRIVATE);
        String in = prefs.getString("source", null);
        String out = prefs.getString("dest", null);
        if (in != null) source = new File(in);
        if (out != null) dest = new File(out);
        updateFolders();
        applyInsets();
        updateSnapshot();
    }

    @Override public void onStart() {
        super.onStart();
        if (!registered) {
            IntentFilter filter = new IntentFilter(CDecryptService.ACTION_STATE);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
            registered = true;
        }
        updateSnapshot();
    }

    @Override public void onStop() {
        if (registered) {
            unregisterReceiver(receiver);
            registered = false;
        }
        super.onStop();
    }

    private void applyInsets() {
        View root = findViewById(R.id.rootScroll);
        int l = root.getPaddingLeft(), t = root.getPaddingTop();
        int r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(l + insets.getSystemWindowInsetLeft(),
                    t + insets.getSystemWindowInsetTop(),
                    r + insets.getSystemWindowInsetRight(),
                    b + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        boolean dark = (getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (dark) flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void updateSnapshot() {
        CDecryptService.Snapshot s = CDecryptService.snapshot(this);
        showState(s.running, s.progress, s.stage, s.result, s.report, s.output);
    }

    private void showState(boolean active, int progress, String stage, int result, String report, String output) {
        running = active;
        sourceButton.setEnabled(!running);
        destButton.setEnabled(!running);
        permissionButton.setEnabled(!running);
        startButton.setEnabled(!running && source != null && dest != null && hasAccess());
        progressBar.setProgress(Math.max(0, Math.min(1000, progress)));
        percentText.setText(String.format(Locale.US, "%.1f%%", Math.max(0, Math.min(1000, progress)) / 10.0));
        if (stage != null) stageText.setText(stage);
        if (running) statusText.setText("Extraindo em segundo plano. Pode minimizar o aplicativo ou desligar a tela.");
        else if (result == 0) statusText.setText("Extração concluída! Arquivos em: " + output);
        else if (result != CDecryptService.NO_RESULT) statusText.setText("Falha na extração (código " + result + "). Copie o relatório para identificar a causa.");
        else statusText.setText(hasAccess() ? "Selecione as pastas de origem e destino." :
                "Primeiro conceda acesso aos arquivos.");
        lastReport = report == null ? "" : report;
        reportText.setText(lastReport.isEmpty() ? "Os detalhes aparecerão aqui após a extração." : lastReport);
        copyButton.setEnabled(!lastReport.isEmpty());
    }

    private boolean hasAccess() { return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(); }

    private void requestFilePermission() {
        if (hasAccess()) {
            Toast.makeText(this, "Acesso já autorizado.", Toast.LENGTH_SHORT).show();
            updateSnapshot();
            return;
        }
        try {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQ_ACCESS);
        } catch (ActivityNotFoundException ex) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_ACCESS);
        }
    }

    private void chooseFolder(int type) {
        if (running) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, type);
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ACCESS) { updateSnapshot(); return; }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        File folder = resolveDocumentTree(data.getData());
        if (folder == null || !folder.isDirectory()) {
            Toast.makeText(this, "Escolha uma pasta local da memória interna ou cartão SD.", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == PICK_SOURCE) {
            source = folder;
            if (!new File(folder, "title.tmd").isFile() || !new File(folder, "title.tik").isFile()) {
                Toast.makeText(this, "A pasta selecionada não tem title.tmd e title.tik.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == PICK_DEST) {
            dest = folder;
        }
        getSharedPreferences("folders", MODE_PRIVATE).edit()
                .putString("source", source == null ? null : source.getAbsolutePath())
                .putString("dest", dest == null ? null : dest.getAbsolutePath()).apply();
        updateFolders();
        updateSnapshot();
    }

    private void updateFolders() {
        inputText.setText(source == null ? "Nenhuma pasta selecionada" : source.getAbsolutePath());
        destText.setText(dest == null ? "Nenhuma pasta selecionada" : dest.getAbsolutePath());
    }

    private File resolveDocumentTree(Uri uri) {
        try {
            if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
            String id = DocumentsContract.getTreeDocumentId(uri);
            int colon = id.indexOf(':');
            if (colon < 0) return null;
            String volume = id.substring(0, colon);
            String relative = id.substring(colon + 1);
            if (relative.startsWith("/") || relative.contains("..") || relative.contains("\\")) return null;
            File root = volume.equalsIgnoreCase("primary") ? Environment.getExternalStorageDirectory()
                    : new File("/storage", volume);
            File parent = root.getCanonicalFile();
            File resolved = new File(root, relative).getCanonicalFile();
            if (!resolved.equals(parent) && !resolved.getPath().startsWith(parent.getPath() + "/")) return null;
            return resolved;
        } catch (Exception ex) { return null; }
    }

    private void startExtraction() {
        if (running) return;
        if (!hasAccess()) { requestFilePermission(); return; }
        if (source == null || dest == null || !source.isDirectory() || !dest.isDirectory()) {
            statusText.setText("Selecione uma origem e uma pasta de destino válidas.");
            return;
        }
        if (!new File(source, "title.tmd").isFile() || !new File(source, "title.tik").isFile()) {
            statusText.setText("A pasta de origem precisa conter title.tmd e title.tik.");
            return;
        }
        try {
            long free = new StatFs(dest.getAbsolutePath()).getAvailableBytes();
            if (free < 512L * 1024 * 1024) {
                statusText.setText("Pouco espaço livre no destino (menos de 512 MB).");
                return;
            }
        } catch (Exception ex) {
            statusText.setText("Não foi possível consultar o espaço livre do destino.");
            return;
        }

        String base = source.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        if (base.length() > 36) base = base.substring(0, 36);
        if (base.isEmpty()) base = "WiiU";
        File target = new File(dest, "CDecrypt_" + base);
        for (int n = 2; target.exists() && n < 1000; n++) target = new File(dest, "CDecrypt_" + base + "_" + n);
        if (target.exists()) {
            statusText.setText("Não foi possível criar outra pasta de saída.");
            return;
        }
        maybeRequestNotificationPermission();
        Intent task = new Intent(this, CDecryptService.class)
                .setAction(CDecryptService.ACTION_START)
                .putExtra(CDecryptService.EXTRA_SOURCE, source.getAbsolutePath())
                .putExtra(CDecryptService.EXTRA_DEST, target.getAbsolutePath());
        startButton.setEnabled(false);
        statusText.setText("Preparando extração em segundo plano...");
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(task);
            else startService(task);
        } catch (Throwable e) {
            startButton.setEnabled(true);
            statusText.setText("Não foi possível iniciar o serviço: " + e.getMessage());
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }
}
