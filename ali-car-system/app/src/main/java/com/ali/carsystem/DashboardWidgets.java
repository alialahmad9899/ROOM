package com.ali.carsystem;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

final class NeonIconView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int type,color;
    private float pressed=1f;
    NeonIconView(Context c,int type,int color){super(c);this.type=type;this.color=color;setFocusable(true);}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){animateScale(.94f);return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){animateScale(1f);performClick();return true;}
        if(e.getAction()==MotionEvent.ACTION_CANCEL){animateScale(1f);return true;}return true;
    }
    private void animateScale(float value){
        ValueAnimator animator=ValueAnimator.ofFloat(pressed,value);animator.setDuration(120);
        animator.addUpdateListener(a->{pressed=(Float)a.getAnimatedValue();invalidate();});animator.start();
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private int blend(int a,int b,float t){
        int aa=(a>>>24)&255,ar=(a>>>16)&255,ag=(a>>>8)&255,ab=a&255;
        int ba=(b>>>24)&255,br=(b>>>16)&255,bg=(b>>>8)&255,bb=b&255;
        return ((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)|
               ((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.save();canvas.scale(pressed,pressed,getWidth()/2f,getHeight()/2f);
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.43f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx-r,cy-r,cx+r,cy+r,blend(color,Color.WHITE,.27f),
                blend(color,0xFF07111D,.30f),Shader.TileMode.CLAMP));
        canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,r*.48f,r*.48f,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.2f,r*.055f));p.setColor(0x88FFFFFF);
        canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,r*.48f,r*.48f,p);
        p.setStrokeWidth(Math.max(2.2f,r*.11f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(Color.WHITE);p.setStyle(Paint.Style.FILL);
        switch(type){
            case 0:{
                RectF box=new RectF(cx-r*.70f,cy-r*.48f,cx+r*.70f,cy+r*.48f);
                p.setColor(0xFFF5F8FF);canvas.drawRoundRect(box,r*.20f,r*.20f,p);
                Path triangle=new Path();triangle.moveTo(cx-r*.18f,cy-r*.27f);
                triangle.lineTo(cx+r*.33f,cy);triangle.lineTo(cx-r*.18f,cy+r*.27f);triangle.close();
                p.setColor(color);canvas.drawPath(triangle,p);break;
            }
            case 1:{
                Path pin=new Path();pin.moveTo(cx,cy+r*.76f);pin.cubicTo(cx-r*.20f,cy+r*.35f,cx-r*.68f,cy-r*.06f,cx-r*.68f,cy-r*.36f);
                pin.cubicTo(cx-r*.68f,cy-r*.79f,cx+r*.68f,cy-r*.79f,cx+r*.68f,cy-r*.36f);
                pin.cubicTo(cx+r*.68f,cy-r*.06f,cx+r*.20f,cy+r*.35f,cx,cy+r*.76f);pin.close();
                canvas.drawPath(pin,p);p.setColor(color);canvas.drawCircle(cx,cy-r*.36f,r*.20f,p);break;
            }
            case 2:{
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.17f);
                Path note=new Path();note.moveTo(cx+r*.12f,cy-r*.62f);note.lineTo(cx+r*.12f,cy+r*.30f);
                note.lineTo(cx+r*.62f,cy+r*.18f);note.lineTo(cx+r*.62f,cy-r*.77f);
                note.lineTo(cx+r*.12f,cy-r*.62f);canvas.drawPath(note,p);
                p.setStyle(Paint.Style.FILL);canvas.drawCircle(cx-r*.08f,cy+r*.40f,r*.24f,p);
                canvas.drawCircle(cx+r*.42f,cy+r*.28f,r*.24f,p);break;
            }
            case 3:{
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.15f);
                Path bt=new Path();bt.moveTo(cx,cy-r*.76f);bt.lineTo(cx+r*.48f,cy-r*.34f);
                bt.lineTo(cx-r*.46f,cy+r*.34f);bt.lineTo(cx,cy+r*.76f);bt.lineTo(cx,cy-r*.76f);
                bt.lineTo(cx-r*.46f,cy-r*.34f);bt.lineTo(cx+r*.48f,cy+r*.34f);
                canvas.drawPath(bt,p);break;
            }
            case 4:{
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.14f);
                for(int i=0;i<3;i++){RectF arc=new RectF(cx-r*(.74f-i*.22f),cy-r*(.72f-i*.18f),cx+r*(.74f-i*.22f),cy+r*(.72f-i*.18f));canvas.drawArc(arc,225,90,false,p);}
                p.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy+r*.42f,r*.12f,p);break;
            }
            default:{
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.105f);
                canvas.drawCircle(cx,cy,r*.34f,p);
                for(int i=0;i<8;i++){double a=Math.PI*i/4;float x1=cx+(float)Math.cos(a)*r*.53f,y1=cy+(float)Math.sin(a)*r*.53f,x2=cx+(float)Math.cos(a)*r*.78f,y2=cy+(float)Math.sin(a)*r*.78f;canvas.drawLine(x1,y1,x2,y2,p);}
                p.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy,r*.10f,p);
            }
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.045f);p.setColor(0x55FFFFFF);
        canvas.drawArc(cx-r*.80f,cy-r*.80f,cx+r*.80f,cy+r*.80f,205,130,false,p);
        canvas.restore();
    }
}
final class InstrumentDialStyle {
    private InstrumentDialStyle() {}
    static void drawFace(Canvas canvas,Paint paint,float cx,float cy,float radius){
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx-radius*.25f,cy-radius*.35f,radius*1.7f,
                new int[]{0xAAFFFFFF,0x553E9AB5,0xCC071321},null,Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,radius,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2,radius*.045f));
        paint.setShader(new LinearGradient(cx-radius,cy-radius,cx+radius,cy+radius,
                0xFFF8FFFF,0xFF718B9C,Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,radius,paint);paint.setShader(null);
        paint.setStrokeWidth(Math.max(1,radius*.012f));paint.setColor(0x6677D5EA);
        canvas.drawCircle(cx,cy,radius*.91f,paint);
        paint.setStrokeWidth(Math.max(1.5f,radius*.018f));paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x8856CFEF);
        canvas.drawArc(cx-radius*.98f,cy-radius*.98f,cx+radius*.98f,cy+radius*.98f,205,130,false,paint);
        paint.setColor(0x55FFD36B);
        canvas.drawArc(cx-radius*.98f,cy-radius*.98f,cx+radius*.98f,cy+radius*.98f,25,42,false,paint);
    }
}

final class SpeedometerView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private float speed;
    SpeedometerView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setSpeed(float kmh){speed=Math.max(0,Math.min(240,kmh));invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
        InstrumentDialStyle.drawFace(c,p,cx,cy,r);

        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(6,r*.065f));p.setColor(0x334B90AE);
        c.drawArc(cx-r*.83f,cy-r*.83f,cx+r*.83f,cy+r*.83f,135,270,false,p);
        p.setShader(new SweepGradient(cx,cy,new int[]{0xFF00DFFF,0xFF49F3D2,0xFFFFC45B,0xFFFF405F},null));
        c.drawArc(cx-r*.83f,cy-r*.83f,cx+r*.83f,cy+r*.83f,135,270*speed/240f,false,p);p.setShader(null);

        p.setStrokeWidth(Math.max(1.5f,r*.014f));
        for(int i=0;i<=48;i++){
            double angle=Math.toRadians(135+i*5.625);
            boolean major=i%4==0;
            float inner=r*(major?.69f:.75f),outer=r*.82f;
            p.setColor(major?0xFFFFD36B:0xFF78AFC6);
            c.drawLine(cx+(float)Math.cos(angle)*inner,cy+(float)Math.sin(angle)*inner,
                    cx+(float)Math.cos(angle)*outer,cy+(float)Math.sin(angle)*outer,p);
        }
        double needleAngle=Math.toRadians(135+270.0*speed/240.0);
        float nx=cx+(float)Math.cos(needleAngle)*r*.63f;
        float ny=cy+(float)Math.sin(needleAngle)*r*.63f;
        p.setStrokeWidth(Math.max(2.8f,r*.026f));p.setColor(0xFFFF5B74);
        p.setShadowLayer(7,0,0,0xFFFF405F);c.drawLine(cx,cy,nx,ny,p);p.clearShadowLayer();

        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        p.setTextSize(r*.105f);p.setColor(0xFFBDEFFF);
        for(int i=0;i<=12;i++){
            double angle=Math.toRadians(135+i*22.5);
            float x=cx+(float)Math.cos(angle)*r*.59f;
            float y=cy+(float)Math.sin(angle)*r*.59f+r*.035f;
            c.drawText(String.valueOf(i*20),x,y,p);
        }
        p.setTextSize(r*.60f);p.setColor(Color.WHITE);
        c.drawText(String.format(java.util.Locale.US,"%.0f",speed),cx,cy+r*.18f,p);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        p.setTextSize(r*.135f);p.setColor(0xFFFFD36B);c.drawText("km/h",cx,cy+r*.42f,p);
        p.setColor(0xFFFFD36B);c.drawCircle(cx,cy,r*.055f,p);
        if(speed>120){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,r*.025f));
            p.setColor(0xCCFF365E);p.setShadowLayer(12,0,0,0xFFFF365E);
            c.drawCircle(cx,cy,r*1.035f,p);p.clearShadowLayer();
        }
    }
}

final class AnalogClockView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.Calendar cal=java.util.Calendar.getInstance();
    AnalogClockView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);cal.setTimeInMillis(System.currentTimeMillis());
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
        InstrumentDialStyle.drawFace(c,p,cx,cy,r);
        for(int i=0;i<60;i++){
            double a=Math.PI*i/30;boolean major=i%5==0;
            float inner=r*(major?.77f:.86f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(major?Math.max(2.2f,r*.022f):Math.max(1,r*.009f));
            p.setColor(major?0xFFFFD36B:0xFF9BB8C9);
            c.drawLine(cx+(float)Math.sin(a)*inner,cy-(float)Math.cos(a)*inner,
                    cx+(float)Math.sin(a)*r*.92f,cy-(float)Math.cos(a)*r*.92f,p);
        }
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        p.setTextSize(r*.135f);p.setColor(0xFFFFD36B);
        String[] marks={"12","3","6","9"};
        int[] positions={0,15,30,45};
        for(int i=0;i<4;i++){
            double a=Math.PI*positions[i]/30;
            float x=cx+(float)Math.sin(a)*r*.66f;
            float y=cy-(float)Math.cos(a)*r*.66f+r*.045f;
            c.drawText(marks[i],x,y,p);
        }
        float sec=cal.get(java.util.Calendar.SECOND);
        float min=cal.get(java.util.Calendar.MINUTE)+sec/60f;
        float hr=cal.get(java.util.Calendar.HOUR)+min/60f;
        hand(c,cx,cy,r*.45f,hr/12f*360,Math.max(4,r*.040f),0xFFFFFFFF);
        hand(c,cx,cy,r*.68f,min/60f*360,Math.max(2.8f,r*.026f),0xFF89EFFF);
        hand(c,cx,cy,r*.77f,sec/60f*360,Math.max(1.5f,r*.013f),0xFFFF5574);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFFFFD36B);
        c.drawCircle(cx,cy,r*.055f,p);postInvalidateDelayed(1000);
    }
    private void hand(Canvas c,float x,float y,float len,float deg,float width,int color){
        c.save();c.rotate(deg,x,y);p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(width);p.setColor(color);
        p.setShadowLayer(5,0,0,color);c.drawLine(x,y+len*.18f,x,y-len,p);
        p.clearShadowLayer();c.restore();
    }
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
