package com.example.ds4vibration;
import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.widget.*;
import android.graphics.Color;
public class MainActivity extends Activity {
 TextView status; Button toggle; SeekBar strength; boolean running=false; Vibrator motor; android.os.Handler handler=new android.os.Handler();
 final Runnable stopTask=()->stop();
 @Override public void onCreate(Bundle b){super.onCreate(b); LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(36,64,36,30);l.setBackgroundColor(Color.rgb(20,23,32));
 TextView title=new TextView(this);title.setText("DS4 Vibration Control");title.setTextSize(26);title.setTextColor(-1);l.addView(title);
 status=new TextView(this);status.setTextColor(-1);status.setTextSize(16);l.addView(status);
 strength=new SeekBar(this);strength.setMax(255);strength.setProgress(160);l.addView(strength);
 toggle=new Button(this);toggle.setText("LIGAR VIBRAÇÃO");l.addView(toggle);toggle.setOnClickListener(v->{if(running)stop();else start();});
 Button refresh=new Button(this);refresh.setText("PROCURAR CONTROLE");l.addView(refresh);refresh.setOnClickListener(v->scan());
 TextView note=new TextView(this);note.setText("Teste de até 30 segundos. Depende do suporte do Android à vibração do controle. Não vibra o celular.");note.setTextColor(Color.LTGRAY);l.addView(note);setContentView(l);scan(); }
 void scan(){motor=null;InputManager im=(InputManager)getSystemService(INPUT_SERVICE);for(int id:im.getInputDeviceIds()){InputDevice d=im.getInputDevice(id);if(d!=null && (d.getSources()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD){Vibrator v=d.getVibrator();if(v!=null && v.hasVibrator()){motor=v;status.setText("Controle detectado: "+d.getName());return;}}}status.setText("Nenhum controle com vibração acessível.");}
 void start(){scan();if(motor==null)return;int amp=Math.max(1,strength.getProgress());try{motor.vibrate(VibrationEffect.createWaveform(new long[]{0,500,100},new int[]{0,amp,0},0));running=true;toggle.setText("DESLIGAR VIBRAÇÃO");handler.postDelayed(stopTask,30000);}catch(Exception e){status.setText("Vibração indisponível: "+e.getMessage());}}
 void stop(){handler.removeCallbacks(stopTask);if(motor!=null)motor.cancel();running=false;if(toggle!=null)toggle.setText("LIGAR VIBRAÇÃO");}
 @Override protected void onPause(){super.onPause();stop();}
 @Override protected void onDestroy(){stop();super.onDestroy();}
}