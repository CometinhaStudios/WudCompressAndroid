package com.cometinhastudios.arnis;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import android.webkit.*;
public class MainActivity extends Activity {
 EditText bbox; TextView log; Spinner format; Button run; WebView map; EditText search;
 public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(24,24,24,24);
 TextView title=new TextView(this);title.setText("Arnis Android 0.1 (experimental)");title.setTextSize(22);l.addView(title);
 search=new EditText(this);search.setSingleLine(true);search.setHint("Buscar cidade ou endereco");l.addView(search);
 Button find=new Button(this);find.setText("Buscar no mapa");l.addView(find);
 map=new WebView(this);map.getSettings().setJavaScriptEnabled(true);map.getSettings().setDomStorageEnabled(true);map.addJavascriptInterface(new Object(){@JavascriptInterface public void onBounds(String b){runOnUiThread(()->bbox.setText(b));}},"Android");
 l.addView(map,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*.43)));
 map.loadUrl("file:///android_asset/map.html");
 find.setOnClickListener(v->map.evaluateJavascript("search("+org.json.JSONObject.quote(search.getText().toString())+")",null));
 Button select=new Button(this);select.setText("Selecionar area no mapa");l.addView(select);
 select.setOnClickListener(v->map.evaluateJavascript("selectArea()",null));
 TextView help=new TextView(this);help.setText("Area selecionada (coordenadas automaticas):");l.addView(help);
 bbox=new EditText(this);bbox.setSingleLine(true);bbox.setText("-1.1970,-47.1820,-1.1965,-47.1815");l.addView(bbox);
 format=new Spinner(this);format.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Bedrock (.mcworld)","Java (pasta)"}));l.addView(format);
 run=new Button(this);run.setText("Gerar mundo");l.addView(run);
 log=new TextView(this);log.setTextIsSelectable(true);log.setText("Os mundos serao salvos em Android/data/com.cometinhastudios.arnis/files");l.addView(log);
 ScrollView s=new ScrollView(this);s.addView(l);setContentView(s);
 run.setOnClickListener(v->start());
 }
 void start(){String bb=bbox.getText().toString().trim();
 if(!bb.matches("-?[0-9.]+,-?[0-9.]+,-?[0-9.]+,-?[0-9.]+")){log.setText("Formato de coordenadas invalido");return;}
 run.setEnabled(false);log.setText("Iniciando motor ARM64...\n");
 new Thread(()->{try{
 File out=new File(getExternalFilesDir(null),"world-"+System.currentTimeMillis());out.mkdirs();
 String bin=getApplicationInfo().nativeLibraryDir+"/libarnis.so";
 java.util.ArrayList<String> args=new java.util.ArrayList<>();args.add(bin);args.add("--bbox");args.add(bb);args.add("--output-dir");args.add(out.getAbsolutePath());args.add("--mode");args.add("geo-only");args.add("--overture");args.add("false");args.add("--canopy-height");args.add("false");
 if(format.getSelectedItemPosition()==0)args.add("--bedrock");
 ProcessBuilder pb=new ProcessBuilder(args);pb.redirectErrorStream(true);pb.directory(getFilesDir());java.lang.Process p=pb.start();
 try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){String line;while((line=r.readLine())!=null){final String msg=line;runOnUiThread(()->log.append(msg+"\n"));}}
 int code=p.waitFor();runOnUiThread(()->{log.append("Finalizado: codigo "+code+"\nSaida: "+out);run.setEnabled(true);});
 }catch(Exception e){runOnUiThread(()->{log.append("Erro: "+e.toString());run.setEnabled(true);});}}).start();
 }
}
