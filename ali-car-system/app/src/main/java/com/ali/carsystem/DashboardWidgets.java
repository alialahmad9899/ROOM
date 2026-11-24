package com.ali.carsystem;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

final class DashboardBackdropView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    DashboardBackdropView(Context context){super(context);setWillNotDraw(false);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        DashboardTheme.Palette theme=DashboardTheme.from(getContext());
        paint.setShader(new LinearGradient(0,0,w,h,theme.background,theme.backgroundEnd,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setShader(new RadialGradient(w*.52f,h*.43f,Math.max(w,h)*.68f,
                new int[]{withAlpha(theme.accent,34),withAlpha(theme.accent,8),0x00000000},
                null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1f,w*.0012f));
        paint.setColor(withAlpha(theme.accent,22));
        Path road=new Path();road.moveTo(w*.42f,h*.43f);road.lineTo(w*.06f,h*1.02f);
        road.moveTo(w*.58f,h*.43f);road.lineTo(w*.94f,h*1.02f);
        canvas.drawPath(road,paint);
        for(int i=1;i<=4;i++){
            float y=h*(.62f+i*.085f);
            canvas.drawLine(w*.08f,y,w*.92f,y,paint);
        }
        paint.setStrokeWidth(Math.max(1f,w*.0008f));
        canvas.drawArc(w*.02f,h*.04f,w*.45f,h*.72f,205,74,false,paint);
        canvas.drawArc(w*.55f,h*.04f,w*.98f,h*.72f,-99,74,false,paint);
        paint.setStyle(Paint.Style.FILL);
    }
    private int withAlpha(int color,int alpha){return InstrumentDialStyle.withAlpha(color,alpha);}
}

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
            case 6:{
                RectF radio=new RectF(cx-r*.70f,cy-r*.45f,cx+r*.70f,cy+r*.48f);
                p.setStyle(Paint.Style.FILL);p.setColor(0xFFF3FAFF);
                canvas.drawRoundRect(radio,r*.12f,r*.12f,p);
                p.setColor(color);canvas.drawRoundRect(cx-r*.52f,cy-r*.26f,cx+r*.27f,cy+r*.13f,r*.05f,r*.05f,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.09f);
                canvas.drawCircle(cx+r*.45f,cy+r*.02f,r*.12f,p);
                canvas.drawLine(cx-r*.30f,cy-r*.45f,cx+r*.35f,cy-r*.78f,p);
                break;
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
    static void drawFace(Canvas canvas,Paint paint,float cx,float cy,float radius,
                         DashboardTheme.Palette theme){
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx-radius*.24f,cy-radius*.30f,radius*1.65f,
                new int[]{withAlpha(theme.accent,55),theme.surface,theme.background},null,Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,radius,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2,radius*.045f));
        paint.setShader(new LinearGradient(cx-radius,cy-radius,cx+radius,cy+radius,
                0xFFEFF8FF,theme.border,Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,radius,paint);paint.setShader(null);
        paint.setStrokeWidth(Math.max(1,radius*.012f));paint.setColor(withAlpha(theme.accent,100));
        canvas.drawCircle(cx,cy,radius*.91f,paint);
        paint.setStrokeWidth(Math.max(1.5f,radius*.018f));paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(withAlpha(theme.accent,185));
        canvas.drawArc(cx-radius*.98f,cy-radius*.98f,cx+radius*.98f,cy+radius*.98f,205,112,false,paint);
        paint.setColor(withAlpha(theme.secondary,175));
        canvas.drawArc(cx-radius*.98f,cy-radius*.98f,cx+radius*.98f,cy+radius*.98f,25,42,false,paint);
    }
    static int withAlpha(int color,int alpha){return (color&0x00FFFFFF)|((alpha&255)<<24);}
}

final class SpeedometerView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private float speed;
    SpeedometerView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setSpeed(float kmh){speed=Math.max(0,Math.min(240,kmh));invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
        DashboardTheme.Palette theme=DashboardTheme.from(getContext());
        InstrumentDialStyle.drawFace(c,p,cx,cy,r,theme);

        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(6,r*.065f));p.setColor(theme.track);
        c.drawArc(cx-r*.83f,cy-r*.83f,cx+r*.83f,cy+r*.83f,135,270,false,p);
        p.setShader(new SweepGradient(cx,cy,new int[]{theme.accent,theme.secondary,theme.needle},null));
        c.drawArc(cx-r*.83f,cy-r*.83f,cx+r*.83f,cy+r*.83f,135,270*speed/240f,false,p);p.setShader(null);

        p.setStrokeWidth(Math.max(1.5f,r*.014f));
        for(int i=0;i<=48;i++){
            double angle=Math.toRadians(135+i*5.625);
            boolean major=i%4==0;
            float inner=r*(major?.69f:.75f),outer=r*.82f;
            p.setColor(major?theme.secondary:theme.textSecondary);
            c.drawLine(cx+(float)Math.cos(angle)*inner,cy+(float)Math.sin(angle)*inner,
                    cx+(float)Math.cos(angle)*outer,cy+(float)Math.sin(angle)*outer,p);
        }
        double needleAngle=Math.toRadians(135+270.0*speed/240.0);
        float nx=cx+(float)Math.cos(needleAngle)*r*.63f;
        float ny=cy+(float)Math.sin(needleAngle)*r*.63f;
        p.setStrokeWidth(Math.max(2.8f,r*.026f));p.setColor(theme.needle);
        p.setShadowLayer(7,0,0,theme.needle);c.drawLine(cx,cy,nx,ny,p);p.clearShadowLayer();

        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        p.setTextSize(r*.105f);p.setColor(theme.textSecondary);
        for(int i=0;i<=12;i++){
            double angle=Math.toRadians(135+i*22.5);
            float x=cx+(float)Math.cos(angle)*r*.59f;
            float y=cy+(float)Math.sin(angle)*r*.59f+r*.035f;
            c.drawText(String.valueOf(i*20),x,y,p);
        }
        p.setTextSize(r*.60f);p.setColor(theme.textPrimary);
        c.drawText(String.format(java.util.Locale.US,"%.0f",speed),cx,cy+r*.18f,p);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        p.setTextSize(r*.135f);p.setColor(theme.secondary);c.drawText("km/h",cx,cy+r*.42f,p);
        p.setColor(theme.accent);c.drawCircle(cx,cy,r*.055f,p);
        if(speed>120){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,r*.025f));
            p.setColor(withAlpha(theme.needle,210));p.setShadowLayer(12,0,0,theme.needle);
            c.drawCircle(cx,cy,r*1.035f,p);p.clearShadowLayer();
        }
    }
    private int withAlpha(int color,int alpha){return InstrumentDialStyle.withAlpha(color,alpha);}
}

final class TripDistanceGaugeView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private double distanceKm;
    TripDistanceGaugeView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setDistanceKm(double value){distanceKm=Math.max(0,value);invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
        if(r<=0)return;
        DashboardTheme.Palette theme=DashboardTheme.from(getContext());
        InstrumentDialStyle.drawFace(canvas,p,cx,cy,r,theme);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(5,r*.075f));p.setColor(theme.track);
        canvas.drawArc(cx-r*.76f,cy-r*.76f,cx+r*.76f,cy+r*.76f,135,270,false,p);
        double scale=Math.max(100d,Math.ceil(Math.max(1d,distanceKm)/100d)*100d);
        float sweep=(float)(270d*Math.min(1d,distanceKm/scale));
        p.setColor(theme.accent);p.setShadowLayer(6,0,0,theme.glow);
        canvas.drawArc(cx-r*.76f,cy-r*.76f,cx+r*.76f,cy+r*.76f,135,sweep,false,p);p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        p.setTextSize(r*.33f);p.setColor(theme.textPrimary);
        canvas.drawText(String.format(java.util.Locale.US,"%.1f",distanceKm),cx,cy+r*.12f,p);
        p.setTextSize(r*.13f);p.setColor(theme.secondary);
        canvas.drawText("TRIP  KM",cx,cy+r*.39f,p);
        p.setTextSize(r*.105f);p.setColor(theme.textSecondary);
        canvas.drawText(String.format(java.util.Locale.US,"MAX %.0f",scale),cx,cy+r*.60f,p);
        canvas.drawCircle(cx,cy-r*.69f,r*.035f,p);
    }
}

final class AnalogClockView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.Calendar cal=java.util.Calendar.getInstance();
    AnalogClockView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);cal.setTimeInMillis(System.currentTimeMillis());
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
        DashboardTheme.Palette theme=DashboardTheme.from(getContext());
        InstrumentDialStyle.drawFace(c,p,cx,cy,r,theme);
        for(int i=0;i<60;i++){
            double a=Math.PI*i/30;boolean major=i%5==0;
            float inner=r*(major?.77f:.86f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(major?Math.max(2.2f,r*.022f):Math.max(1,r*.009f));
            p.setColor(major?theme.secondary:theme.textSecondary);
            c.drawLine(cx+(float)Math.sin(a)*inner,cy-(float)Math.cos(a)*inner,
                    cx+(float)Math.sin(a)*r*.92f,cy-(float)Math.cos(a)*r*.92f,p);
        }
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        p.setTextSize(r*.135f);p.setColor(theme.secondary);
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
        hand(c,cx,cy,r*.45f,hr/12f*360,Math.max(4,r*.040f),theme.textPrimary);
        hand(c,cx,cy,r*.68f,min/60f*360,Math.max(2.8f,r*.026f),theme.accent);
        hand(c,cx,cy,r*.77f,sec/60f*360,Math.max(1.5f,r*.013f),theme.needle);
        p.setStyle(Paint.Style.FILL);p.setColor(theme.secondary);
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
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds=new RectF();
    private final Typeface display=Typeface.create("sans-serif-black",Typeface.BOLD);
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL);
    private final Typeface medium=Typeface.create("sans-serif-medium",Typeface.BOLD);

    NeonSplashView(Context c){
        super(c);
        setTag("welcome_splash_overlay");
        setContentDescription("ALI CAR welcome screen");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        // The Intel SoFIA graphics stack should not be driven by an animated overlay.
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }

    private void centeredText(Canvas c,String value,float x,float y,float size,int color,Typeface face){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(face);p.setTextSize(size);p.setColor(color);p.clearShadowLayer();
        c.drawText(value,x,y,p);
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        final float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        final float s=Math.min(w/900f,h/520f);
        final float cx=w*.5f,centerY=h*.425f;
        final float logoY=centerY-18f*s;
        final float radius=91f*s;

        // Single low-cost static frame; no timer, shader, particles or progress animation.
        c.drawColor(0xFF030B16);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f,s));
        p.setColor(0x183E7892);
        for(int i=1;i<=5;i++){
            float y=h*(.12f+i*.105f);
            c.drawLine(w*.09f,y,w*.91f,y,p);
        }
        p.setColor(0x193E7892);
        c.drawLine(cx-w*.38f,0,cx-w*.12f,h,p);
        c.drawLine(cx+w*.38f,0,cx+w*.12f,h,p);

        ringBounds.set(cx-radius,logoY-radius,cx+radius,logoY+radius);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(1.8f*s);p.setColor(0x704D819A);
        c.drawCircle(cx,logoY,radius,p);
        p.setStrokeWidth(4.1f*s);p.setColor(0xFF63E6FF);
        c.drawArc(ringBounds,205f,112f,false,p);
        p.setStrokeWidth(2.5f*s);p.setColor(0xFFFFD27C);
        c.drawArc(ringBounds,27f,54f,false,p);
        p.setStrokeWidth(1.5f*s);p.setColor(0xFF6EC8E4);
        c.drawArc(ringBounds,150f,32f,false,p);

        p.setStrokeWidth(3.2f*s);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xFFE8FAFF);
        c.drawCircle(cx,logoY,27f*s,p);
        c.drawArc(cx-19f*s,logoY-13f*s,cx+19f*s,logoY+18f*s,22f,136f,false,p);
        c.drawLine(cx,logoY-25f*s,cx,logoY+5f*s,p);
        c.drawLine(cx,logoY+5f*s,cx-17f*s,logoY+18f*s,p);
        c.drawLine(cx,logoY+5f*s,cx+17f*s,logoY+18f*s,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xFF66E7FF);
        c.drawCircle(cx,logoY+5f*s,3.5f*s,p);

        centeredText(c,"WELCOME, ALI",cx,logoY-radius-22f*s,13f*s,0xFF9EC5D8,medium);
        centeredText(c,"ALI CAR",cx,centerY+105f*s,47f*s,0xFFF4FBFF,display);
        centeredText(c,"YOUR DRIVE  •  YOUR WORLD",
                cx,centerY+132f*s,11.5f*s,0xFF9DCCDE,medium);
        centeredText(c,"أهلاً بك، رحلتك أصبحت جاهزة",
                cx,centerY+166f*s,17f*s,0xFFE3F5FA,regular);
    }
}
