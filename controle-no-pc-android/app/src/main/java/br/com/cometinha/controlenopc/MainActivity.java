package br.com.cometinha.controlenopc;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.widget.*;
import android.view.View;
import android.view.Gravity;
import java.util.Locale;

public class MainActivity extends Activity implements InputManager.InputDeviceListener {
    private LinearLayout root, deviceList, eventList;
    private TextView summary, eventStatus;
    private InputManager inputManager;
    private int eventCount = 0;
    private boolean listening = false;
    private int white = Color.WHITE, muted = Color.rgb(160,170,185);
    private int bg = Color.rgb(13,17,25), panel = Color.rgb(25,32,44), accent = Color.rgb(70,140,255);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        inputManager = (InputManager)getSystemService(Context.INPUT_SERVICE);
        buildUi();
        inputManager.registerInputDeviceListener(this, null);
        scanDevices();
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(1); return l; }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root = column(); root.setPadding(22,34,22,26); root.setBackgroundColor(bg); scroll.addView(root);
        TextView brand = text("CONTROLE NO PC", 13, accent, true); root.addView(brand);
        TextView title = text("Central de dispositivos", 27, white, true); title.setPadding(0,5,0,4); root.addView(title);
        root.addView(text("Reconhecimento e teste de controles USB e Bluetooth", 14, muted, false));
        LinearLayout status = column(); status.setPadding(16,16,16,16); status.setBackground(shape(panel,18));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=22; root.addView(status,sp);
        summary = text("Procurando dispositivos…",16,white,true); status.addView(summary);
        Button refresh = new Button(this); refresh.setText("ATUALIZAR DISPOSITIVOS"); status.addView(refresh);
        refresh.setOnClickListener(v -> scanDevices());
        root.addView(sectionTitle("DISPOSITIVOS CONECTADOS"));
        deviceList = column(); root.addView(deviceList);
        root.addView(sectionTitle("TESTE DE BOTÕES E EIXOS"));
        TextView help = text("Toque em INICIAR TESTE e pressione qualquer botão ou mova os analógicos. Os eventos recebidos aparecem abaixo.",13,muted,false);
        help.setPadding(0,0,0,10); root.addView(help);
        Button test = new Button(this); test.setText("INICIAR TESTE"); root.addView(test);
        eventStatus = text("Teste desligado",13,muted,false); eventStatus.setPadding(0,8,0,8); root.addView(eventStatus);
        eventList = column(); root.addView(eventList);
        test.setOnClickListener(v -> { listening=!listening; test.setText(listening?"PARAR TESTE":"INICIAR TESTE"); eventStatus.setText(listening?"Aguardando entradas…":"Teste desligado"); });
        setContentView(scroll);
    }
    private TextView sectionTitle(String s) { TextView t=text(s,12,accent,true); t.setPadding(0,24,0,10); return t; }
    private void scanDevices() {
        deviceList.removeAllViews(); int[] ids=inputManager.getInputDeviceIds(); int count=0;
        for(int id:ids) { InputDevice d=inputManager.getInputDevice(id); if(d==null) continue;
            int src=d.getSources(); boolean relevant=(src&(InputDevice.SOURCE_GAMEPAD|InputDevice.SOURCE_JOYSTICK|InputDevice.SOURCE_DPAD|InputDevice.SOURCE_KEYBOARD))!=0;
            if(!relevant) continue; count++;
            LinearLayout card=column(); card.setPadding(15,14,15,14); card.setBackground(shape(panel,16));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=9; deviceList.addView(card,cp);
            card.addView(text(d.getName(),17,white,true));
            card.addView(text("ID "+id+"  •  "+sourceNames(src),12,muted,false));
            String descriptor=d.getDescriptor(); if(descriptor!=null&&!descriptor.isEmpty()) card.addView(text(descriptor,10,muted,false));
            StringBuilder axes=new StringBuilder(); for(InputDevice.MotionRange range:d.getMotionRanges()) { if(axes.length()>0) axes.append("  "); axes.append("Eixo ").append(range.getAxis()).append(" (").append(String.format(Locale.US,"%.1f",range.getMin())).append(" a ").append(String.format(Locale.US,"%.1f",range.getMax())).append(")"); }
            if(axes.length()>0) card.addView(text(axes.toString(),11,muted,false));
        }
        summary.setText(count==0?"Nenhum controle ou teclado detectado":"Dispositivos encontrados: "+count);
    }
    private String sourceNames(int s) {
        StringBuilder b=new StringBuilder();
        if((s&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD)b.append("Gamepad ");
        if((s&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK)b.append("Joystick ");
        if((s&InputDevice.SOURCE_DPAD)==InputDevice.SOURCE_DPAD)b.append("D-pad ");
        if((s&InputDevice.SOURCE_KEYBOARD)==InputDevice.SOURCE_KEYBOARD)b.append("Teclado ");
        if(b.length()==0)b.append("Entrada genérica"); return b.toString().trim();
    }
    private void logEvent(String s) {
        if(!listening)return; eventCount++; eventStatus.setText("Eventos recebidos: "+eventCount);
        TextView line=text(s,12,white,false); line.setPadding(10,8,10,8); line.setBackground(shape(panel,10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=5; eventList.addView(line,0,p);
        if(eventList.getChildCount()>30)eventList.removeViewAt(30);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(listening && e.getAction()==KeyEvent.ACTION_DOWN) logEvent("BOTÃO  •  "+KeyEvent.keyCodeToString(e.getKeyCode())+"  •  "+deviceName(e.getDeviceId()));
        return super.dispatchKeyEvent(e);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if(listening && (e.getSource()&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK && e.getAction()==MotionEvent.ACTION_MOVE) {
            StringBuilder b=new StringBuilder("EIXOS  •  ");
            for(int i=0;i<e.getDevice().getMotionRanges().size();i++) { InputDevice.MotionRange r=e.getDevice().getMotionRanges().get(i); float v=e.getAxisValue(r.getAxis()); if(Math.abs(v)>0.12f)b.append("A").append(r.getAxis()).append(":").append(String.format(Locale.US,"%.2f",v)).append("  "); }
            if(b.length()>10)logEvent(b.toString());
        }
        return super.dispatchGenericMotionEvent(e);
    }
    private String deviceName(int id) { InputDevice d=InputDevice.getDevice(id); return d==null?"dispositivo":d.getName(); }
    @Override public void onInputDeviceAdded(int id){scanDevices();}
    @Override public void onInputDeviceRemoved(int id){scanDevices();}
    @Override public void onInputDeviceChanged(int id){scanDevices();}
    @Override protected void onDestroy(){if(inputManager!=null)inputManager.unregisterInputDeviceListener(this);super.onDestroy();}
}
