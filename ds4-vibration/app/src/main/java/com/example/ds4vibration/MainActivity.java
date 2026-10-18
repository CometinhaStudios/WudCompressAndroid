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
 TextView status,level; Button toggle; SeekBar strength; boolean running=false; Vibrator motor; android.os.Handler handler=new android.os.Handler();

 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(36,56,36,24);l.setBackgroundColor(Color.rgb(20,23,32));
 TextView title=new TextView(this);title.setText("DS4 Vibration Control");title.setTextSize(25);title.setTextColor(-1);l.addView(title);
 status=new TextView(this);status.setTextColor(-1);status.setTextSize(16);l.addView(status);
 level=new TextView(this);level.setTextColor(-1);l.addView(level);
 strength=new SeekBar(this);strength.setMax(254);strength.setProgress(159);l.addView(strength);

 strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){level.setText("Força: "+Math.round((p+1)*100f/255)+"%");if(u&&running)apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});

 level.setText("Força: 63%");
 toggle=new Button(this);toggle.setText("LIGAR VIBRAÇÃO");l.addView(toggle);toggle.setOnClickListener(v->{if(running)stop();else start();});
 Button refresh=new Button(this);refresh.setText("PROCURAR CONTROLE");l.addView(refresh);refresh.setOnClickListener(v->scan());
 TextView note=new TextView(this);note.setText("Vibração contínua até você desligar ou sair do aplicativo. O Android pode limitar a força ou não reconhecer os motores.");note.setTextColor(Color.LTGRAY);l.addView(note);setContentView(l);scan();}
 void scan(){if(running)return;motor=null;InputManager im=(InputManager)getSystemService(INPUT_SERVICE);for(int id:im.getInputDeviceIds()){InputDevice d=im.getInputDevice(id);if(d!=null&&(d.getSources()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD){Vibrator v=d.getVibrator();if(v!=null&&v.hasVibrator()){motor=v;status.setText("Controle: "+d.getName());return;}}}status.setText("Nenhum controle com vibração acessível.");}
 void apply(){if(motor==null)return;try{int amp=Math.max(1,strength.getProgress()+1);motor.vibrate(VibrationEffect.createWaveform(new long[]{0,1000},new int[]{0,amp},1));}catch(Exception e){stop();status.setText("Vibração indisponível: "+e.getMessage());}}
 void start(){scan();if(motor==null)return;running=true;toggle.setText("DESLIGAR VIBRAÇÃO");apply();}
 void stop(){if(motor!=null)motor.cancel();running=false;if(toggle!=null)toggle.setText("LIGAR VIBRAÇÃO");}
 @Override protected void onPause(){super.onPause();stop();}
 @Override protected void onDestroy(){stop();super.onDestroy();}
}