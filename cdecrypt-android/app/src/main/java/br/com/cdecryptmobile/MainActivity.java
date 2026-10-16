package br.com.cdecryptmobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.Locale;

public final class MainActivity extends Activity {
    static { System.loadLibrary("cdecrypt_jni"); }
    private static final int REQ_TREE = 101;
    private static final int REQ_PERMISSION = 102;
    private TextView chosen, status;
    private Button extract;
    private File gameFolder;
    private volatile boolean busy = false;

    private static native int nativeDecrypt(String input, String output);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(20), dp(25), dp(20), dp(25));
        main.setBackgroundColor(0xff101520);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(main);
        setContentView(scroll);
        TextView title = text("CDecrypt Android", 25, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        main.addView(title);
        main.addView(text("Wii U  •  Descriptografar .app / .h3", 14, 0xffbac9df));
        gap(main, 24);

        Button permissions = button("1. Permitir acesso aos arquivos");
        main.addView(permissions);
        permissions.setOnClickListener(v -> askPermission());
        gap(main, 8);

        Button pick = button("2. Selecionar pasta do jogo");
        main.addView(pick);
        pick.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(it, REQ_TREE);
        });
        gap(main, 12);
        chosen = text("Nenhuma pasta selecionada", 13, 0xffbac9df);
        main.addView(chosen);
        gap(main, 18);

        extract = button("3. Descriptografar e extrair");
        main.addView(extract);
        extract.setOnClickListener(v -> decrypt());
        gap(main, 15);
        status = text("Selecione uma pasta com title.tik, title.tmd e arquivos .app.", 14, Color.WHITE);
        main.addView(status);
        gap(main, 20);
        main.addView(text("Os arquivos originais não serão apagados. A saída será salva na pasta CDecrypt_Extraido dentro da pasta selecionada. Mantenha o aplicativo aberto durante a extração.", 13, 0xff9ba9bd));
        gap(main, 20);
        main.addView(text("Código nativo: CDecrypt (VitaSmith/crediar), GPLv3. Projeto experimental.", 11, 0xff95a7c2));
    }

    private TextView text(String s, int size, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }
    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }
    private int dp(int px) { return Math.round(px * getResources().getDisplayMetrics().density); }
    private void gap(LinearLayout layout, int h) {
        layout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h)));
    }
    private boolean canReadFiles() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }
    private void askPermission() {
        if (canReadFiles()) {
            Toast.makeText(this, "Permissão já autorizada", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent it = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(it, REQ_PERMISSION);
        } catch (ActivityNotFoundException ex) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_PERMISSION);
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PERMISSION) {
            status.setText(canReadFiles() ? "Permissão concedida." : "Permissão ainda não concedida.");
            return;
        }
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null) return;
        File folder = resolveFolder(data.getData());
        if (folder == null || !folder.isDirectory()) {
            gameFolder = null;
            chosen.setText("Pasta não acessível. Selecione um diretório na memória interna.");
            return;
        }
        gameFolder = folder;
        chosen.setText("Pasta: " + folder.getAbsolutePath());
        status.setText(filesPresent(folder)
                ? "Arquivos principais encontrados. Pronto para extrair."
                : "Atenção: title.tmd ou title.tik está ausente desta pasta.");
    }
    private File resolveFolder(Uri uri) {
        if (uri == null) return null;
        try {
            if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
            String id = DocumentsContract.getTreeDocumentId(uri);
            int index = id.indexOf(':');
            if (index < 0) return null;
            String volume = id.substring(0, index);
            String relative = id.substring(index + 1);
            if (relative.startsWith("/") || relative.contains("..") || relative.contains("\\")) return null;
            File base = "primary".equalsIgnoreCase(volume)
                    ? Environment.getExternalStorageDirectory() : new File("/storage", volume);
            File canonicalBase = base.getCanonicalFile();
            File folder = new File(base, relative).getCanonicalFile();
            if (!folder.equals(canonicalBase) && !folder.getPath().startsWith(canonicalBase.getPath() + "/")) return null;
            return folder;
        } catch (Exception ex) { return null; }
    }
    private boolean filesPresent(File directory) {
        return new File(directory, "title.tmd").isFile() && new File(directory, "title.tik").isFile();
    }
    private void decrypt() {
        if (busy) return;
        if (!canReadFiles()) {
            status.setText("Primeiro autorize o acesso aos arquivos.");
            askPermission();
            return;
        }
        if (gameFolder == null || !filesPresent(gameFolder)) {
            status.setText("Selecione uma pasta com title.tmd e title.tik.");
            return;
        }
        final File folder = gameFolder;
        final File output = new File(folder, "CDecrypt_Extraido");
        if (output.exists()) {
            status.setText("CDecrypt_Extraido já existe. Renomeie a pasta existente para evitar sobrescrita.");
            return;
        }
        long available = new StatFs(folder.getAbsolutePath()).getAvailableBytes();
        if (available < 1024L * 1024 * 1024) {
            status.setText("Menos de 1 GB livre. Libere espaço antes da extração.");
            return;
        }
        if (!output.mkdirs()) {
            status.setText("Não consegui criar a pasta de saída.");
            return;
        }
        busy = true;
        extract.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText(String.format(Locale.ROOT, "Extraindo... %.1f GB livres. Não feche o app.", available / 1073741824.0));
        new Thread(() -> {
            int result;
            try { result = nativeDecrypt(folder.getAbsolutePath(), output.getAbsolutePath()); }
            catch (Throwable error) { result = -100; }
            final int rc = result;
            runOnUiThread(() -> {
                busy = false;
                extract.setEnabled(true);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                status.setText(rc == 0 ?
                    "Extração concluída! Confira a pasta CDecrypt_Extraido e adicione o jogo no Cemu." :
                    "Erro de extração: código " + rc + ". Confira integridade dos arquivos, ticket e espaço livre. Logs: CDecrypt.");
            });
        }, "cdecrypt-worker").start();
    }
}
