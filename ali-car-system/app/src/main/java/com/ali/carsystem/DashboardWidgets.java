package com.ali.carsystem;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

final class NeonIconView extends View {
    private final Paint p=new Paint(3);
    private final int type,color;
    private float pressed=1f;
    NeonIconView(Context c,int type,int color){super(c);this.type=type;this.color=color;setLayerType(View.LAYER_TYPE_SOFTWARE,null);setFocusable(true);}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){animateScale(.92f);return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){animateScale(1f);performClick();return true;}
        if(e.getAction()==MotionEvent.ACTION_CANCEL){animateScale(1f);return true;}return true;
    }
    private void animateScale(float v){ValueAnimator a=ValueAnimator.ofFloat(pressed,v);a.setDuration(130);a.addUpdateListener(x->{pressed=(Float)x.getAnimatedValue();invalidate();});a.start();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();c.scale(pressed,pressed,getWidth()/2f,getHeight()/2f);
        float cx=getWidth()/2f,cy=getHeight()/2f,s=Math.min(getWidth(),getHeight())*.34f;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(Math.max(3,s*.12f));p.setColor(color);p.setShadowLayer(s*.28f,0,0,color);
        switch(type){
            case 0: // YouTube play
                p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(cx-s,cy-s,cx+s,cy+s,0xFFFF365E,0xFFFF9A70,Shader.TileMode.CLAMP));
                c.drawRoundRect(cx-s,cy-s*.66f,cx+s,cy+s*.66f,s*.36f,s*.36f,p);p.setShader(null);
                p.setColor(Color.WHITE);Path tri=new Path();tri.moveTo(cx-s*.22f,cy-s*.36f);tri.lineTo(cx+s*.42f,cy);tri.lineTo(cx-s*.22f,cy+s*.36f);tri.close();c.drawPath(tri,p);break;
            case 1: // compass pointer
                p.setStyle(Paint.Style.STROKE);c.drawCircle(cx,cy,s*.88f,p);Path nav=new Path();nav.moveTo(cx+s*.62f,cy-s*.72f);nav.lineTo(cx-s*.52f,cy+s*.72f);nav.lineTo(cx+s*.02f,cy+s*.16f);nav.close();p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(cx,cy-s,cx,cy+s,0xFFB5FFFF,color,Shader.TileMode.CLAMP));c.drawPath(nav,p);p.setShader(null);break;
            case 2: // music note
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.17f);Path note=new Path();note.moveTo(cx+s*.20f,cy-s*.64f);note.lineTo(cx+s*.20f,cy+s*.32f);note.lineTo(cx+s*.74f,cy+s*.18f);note.lineTo(cx+s*.74f,cy-s*.76f);note.lineTo(cx+s*.20f,cy-s*.64f);c.drawPath(note,p);p.setStyle(Paint.Style.FILL);c.drawCircle(cx-s*.03f,cy+s*.45f,s*.30f,p);c.drawCircle(cx+s*.50f,cy+s*.30f,s*.30f,p);break;
            case 3: // bluetooth
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.17f);Path bt=new Path();bt.moveTo(cx,cy-s);bt.lineTo(cx+s*.55f,cy-s*.48f);bt.lineTo(cx-s*.52f,cy+s*.48f);bt.lineTo(cx,cy+s);bt.lineTo(cx,cy-s);bt.lineTo(cx-s*.52f,cy-s*.48f);bt.lineTo(cx+s*.55f,cy+s*.48f);c.drawPath(bt,p);break;
            case 4: // wifi
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.16f);RectF r=new RectF(cx-s,cy-s*.95f,cx+s,cy+s*.88f);c.drawArc(r,225,90,false,p);r.inset(s*.28f,s*.28f);c.drawArc(r,225,90,false,p);r.inset(s*.28f,s*.28f);c.drawArc(r,225,90,false,p);p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy+s*.60f,s*.14f,p);break;
            default: // sun
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.14f);c.drawCircle(cx,cy,s*.40f,p);for(int i=0;i<8;i++){double a=Math.PI*i/4;float x1=cx+(float)Math.cos(a)*s*.62f,y1=cy+(float)Math.sin(a)*s*.62f,x2=cx+(float)Math.cos(a)*s*.90f,y2=cy+(float)Math.sin(a)*s*.90f;c.drawLine(x1,y1,x2,y2,p);}
        }
        p.clearShadowLayer();c.restore();
    }
}

final class SpeedometerView extends View {
    private final Paint p=new Paint(3);private float speed;
    SpeedometerView(Context c){super(c);}
    void setSpeed(float kmh){speed=Math.max(0,Math.min(240,kmh));invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.43f;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(Math.max(7,r*.075f));
        p.setColor(0x334B90AE);c.drawArc(cx-r,cy-r,cx+r,cy+r,135,270,false,p);
        p.setShader(new SweepGradient(cx,cy,new int[]{0xFF00DFFF,0xFF49F3D2,0xFFFFC45B,0xFFFF405F},null));
        c.drawArc(cx-r,cy-r,cx+r,cy+r,135,270*speed/240f,false,p);p.setShader(null);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-light",0));p.setTextSize(r*.66f);c.drawText(String.format(java.util.Locale.US,"%.0f",speed),cx,cy+r*.20f,p);
        p.setTextSize(r*.15f);p.setColor(0xFFFFD36B);c.drawText("km/h",cx,cy+r*.48f,p);
        p.setTextSize(r*.105f);p.setColor(0xFFBDEFFF);
        for(int i=0;i<=12;i++){double a=Math.toRadians(135+i*22.5);float x=cx+(float)Math.cos(a)*r*.79f,y=cy+(float)Math.sin(a)*r*.79f;c.drawText(String.valueOf(i*20),x,y,p);}
        if(speed>120){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xFFFF365E);p.setShadowLayer(18,0,0,0xFFFF365E);c.drawCircle(cx,cy,r*1.12f,p);p.clearShadowLayer();}
    }
}

final class AnalogClockView extends View {
    private final Paint p=new Paint(3);private final java.util.Calendar cal=java.util.Calendar.getInstance();
    AnalogClockView(Context c){super(c);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);cal.setTimeInMillis(System.currentTimeMillis());float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
        p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx-r*.25f,cy-r*.35f,r*1.7f,new int[]{0xAAFFFFFF,0x553E9AB5,0xCC071321},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,r*.045f));p.setShader(new LinearGradient(cx-r,cy-r,cx+r,cy+r,0xFFF8FFFF,0xFF718B9C,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r,p);p.setShader(null);
        for(int i=0;i<60;i++){double a=Math.PI*i/30;float inner=r*(i%5==0?.82f:.90f);p.setStrokeWidth(i%5==0?2.6f:1f);p.setColor(i%5==0?Color.WHITE:0xFF9BB8C9);c.drawLine(cx+(float)Math.sin(a)*inner,cy-(float)Math.cos(a)*inner,cx+(float)Math.sin(a)*r*.94f,cy-(float)Math.cos(a)*r*.94f,p);}
        float sec=cal.get(java.util.Calendar.SECOND),min=cal.get(java.util.Calendar.MINUTE)+sec/60f,hr=cal.get(java.util.Calendar.HOUR)+min/60f;
        hand(c,cx,cy,r*.50f,hr/12f*360,5,0xFFFFFFFF);hand(c,cx,cy,r*.72f,min/60f*360,3,0xFF89EFFF);hand(c,cx,cy,r*.82f,sec/60f*360,1.5f,0xFFFF5574);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFD36B);c.drawCircle(cx,cy,r*.055f,p);postInvalidateDelayed(1000);
    }
    private void hand(Canvas c,float x,float y,float len,float deg,float width,int color){c.save();c.rotate(deg,x,y);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(width);p.setColor(color);p.setShadowLayer(5,0,0,color);c.drawLine(x,y+len*.18f,x,y-len,p);p.clearShadowLayer();c.restore();}
}

final class NeonSplashView extends View {
    private final Paint p=new Paint(3);
    private final long start=android.os.SystemClock.uptimeMillis();
    NeonSplashView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float elapsed=android.os.SystemClock.uptimeMillis()-start;
        float f=Math.min(1f,elapsed/4300f),cx=getWidth()/2f,cy=getHeight()/2f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx,cy,Math.max(getWidth(),getHeight())*.78f,new int[]{0xFF153E59,0xFF071522,0xFF02060C},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,getWidth(),getHeight(),p);p.setShader(null);
        for(int i=0;i<28;i++){float phase=(elapsed*.035f+i*137f)%(getWidth()+100);float x=phase-50,y=(i*83f)%Math.max(1,getHeight());p.setColor(i%3==0?0x8858E8FF:0x44669CB8);p.setStrokeWidth(i%4==0?2.2f:1f);c.drawCircle(x,y,i%4==0?2.5f:1.2f,p);}
        float pulse=.5f+.5f*(float)Math.sin(elapsed*.003);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.5f);p.setColor(0xFF58DFFF);p.setShadowLayer(22,0,0,0xFF169EFF);
        c.drawRoundRect(cx-130,cy-112,cx+130,cy+112,38,38,p);p.clearShadowLayer();
        p.setStrokeWidth(7);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xFFB4F4FF);c.drawArc(cx-105,cy-87,cx+105,cy+87,200,140*f,false,p);
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        p.setTextSize(46);p.setColor(Color.WHITE);p.setShadowLayer(14,0,0,0xFF24BAFF);c.drawText("ALI CAR",cx,cy-5,p);p.clearShadowLayer();
        p.setTextSize(15);p.setColor(0xFF9CD8EF);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));c.drawText("SMART COCKPIT",cx,cy+31,p);
        p.setColor(0xFF314D61);c.drawRoundRect(cx-140,cy+142,cx+140,cy+148,4,4,p);
        p.setColor(0xFF56E4FF);c.drawRoundRect(cx-140,cy+142,cx-140+280*f,cy+148,4,4,p);
        p.setTextSize(12);p.setColor(0xFF8CAFC2);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));c.drawText("WELCOME  •  ENJOY THE DRIVE",cx,cy+182,p);
        p.setAlpha((int)(255*(.65f+.35f*pulse)));if(f<1)postInvalidateDelayed(16);
    }
}
