package com.cometinhastudios.arnis;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;
public class MainActivity extends Activity {
 EditText bbox; TextView log; Spinner format; Button run;
 public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(24,24,24,24);
 TextView title=new TextView(this);title.setText("Arnis Android 0.1 (experimental)");title.setTextSize(22);l.addView(title);
 TextView help=new TextView(this);help.setText("Coordenadas: min_lat,min_lon,max_lat,max_lon. Comece com uma area pequena.");l.addView(help);
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
