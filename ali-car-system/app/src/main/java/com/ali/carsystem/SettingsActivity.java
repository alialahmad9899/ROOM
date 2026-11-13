package com.ali.carsystem;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class SettingsActivity extends BaseActivity {
    private LinearLayout box;private DevicePolicyManager dpm;private ComponentName admin;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);admin=new ComponentName(this,CarAdminReceiver.class);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFF07121E);box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(14),dp(20),dp(14));scroll.addView(box);setContentView(scroll);render();}
    private void render(){
        box.removeAllViews();TextView title=label("إدارة الجهاز",22);title.setGravity(Gravity.CENTER);box.addView(title);
        add("الملاحة الصوتية العربية");
        SharedPreferences voicePrefs=getSharedPreferences("ali_car_navigation",MODE_PRIVATE);
        CheckBox voiceEnabled=new CheckBox(this);voiceEnabled.setText("تفعيل الإرشادات الصوتية");voiceEnabled.setTextColor(-1);
        voiceEnabled.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF55D8F0));
        voiceEnabled.setChecked(voicePrefs.getBoolean("voice_enabled",true));
        voiceEnabled.setOnCheckedChangeListener((button,checked)->voicePrefs.edit().putBoolean("voice_enabled",checked).apply());
        box.addView(voiceEnabled);
        TextView voiceLevel=label("مستوى صوت الإرشادات: "+Math.round(voicePrefs.getFloat("voice_volume",0.85f)*100)+"%",14);box.addView(voiceLevel);
        SeekBar voiceVolume=new SeekBar(this);voiceVolume.setMax(100);
        voiceVolume.setProgress(Math.round(voicePrefs.getFloat("voice_volume",0.85f)*100));
        voiceVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seek,int value,boolean fromUser){
                float level=Math.max(10,value)/100f;voiceLevel.setText("مستوى صوت الإرشادات: "+Math.round(level*100)+"%");
                if(fromUser)voicePrefs.edit().putFloat("voice_volume",level).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seek){}
            @Override public void onStopTrackingTouch(SeekBar seek){}
        });
        box.addView(voiceVolume,new LinearLayout.LayoutParams(-1,dp(42)));

        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
        long total=info.totalMem/(1024*1024),avail=info.availMem/(1024*1024),pss=android.os.Debug.getPss()/1024;
        add("الذاكرة الكلية: "+total+" MB");add("الذاكرة المتاحة: "+avail+" MB");add("ذاكرة التطبيق الحالية PSS: "+pss+" MB");
        boolean owner=dpm.isDeviceOwnerApp(getPackageName());boolean active=dpm.isAdminActive(admin);
        add("صلاحية مسؤول الجهاز: "+(active?"مفعّلة":"غير مفعّلة"));add("مالك الجهاز Device Owner: "+(owner?"نعم":"لا"));
        button(active?"صلاحية مسؤول الجهاز مفعّلة":"تفعيل صلاحية مسؤول الجهاز",()->{Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin);i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"إدارة وضع السيارة فقط.");startActivity(i);});
        button("إعدادات النظام",()->startActivity(new Intent(Settings.ACTION_SETTINGS)));
        button("إعدادات التطبيقات",()->startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        button("تفعيل وضع السيارة المقفل",()->{if(owner){try{dpm.setLockTaskPackages(admin,new String[]{getPackageName()});startLockTask();}catch(Exception e){add("تعذر تشغيل وضع القفل: "+e.getMessage());}}else add("يلزم تعيين التطبيق Device Owner أولاً عبر إعداد الجهاز.");});
        button("إيقاف وضع القفل",()->{try{stopLockTask();}catch(Exception ignored){}});
        button("إذن تعديل سطوع النظام",()->{if(Build.VERSION.SDK_INT>=23)startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));else Toast.makeText(this,"إذن تعديل السطوع متاح ضمن صلاحيات النظام في هذا الإصدار",Toast.LENGTH_LONG).show();});
        button("إدارة التطبيقات الاختيارية",()->showHideDialog(owner));
        add("اسم كلاس الإدارة الكامل: com.ali.carsystem.CarAdminReceiver");
        add("لا يمكن إخفاء خدمات النظام إلا عندما يكون التطبيق Device Owner؛ لن نخفي خدمات Google أو الهاتف أو الموقع تلقائياً.");
    }
    private void showHideDialog(boolean owner){
        if(!owner){new AlertDialog.Builder(this).setMessage("هذه الوظيفة تحتاج Device Owner. تفعيل Device Admin وحده لا يكفي.").setPositiveButton("حسناً",null).show();return;}
        String[] names={"Google Maps","YouTube","Google Play Music"};String[] pkgs={"com.google.android.apps.maps","com.google.android.youtube","com.google.android.music"};
        boolean[] checked=new boolean[pkgs.length];
        new AlertDialog.Builder(this).setTitle("إخفاء تطبيقات اختيارية").setMultiChoiceItems(names,checked,(d,w,v)->{
            try{dpm.setApplicationHidden(admin,pkgs[w],v);}catch(Exception e){Toast.makeText(this,"تعذر تغيير التطبيق: "+pkgs[w],Toast.LENGTH_SHORT).show();}
        }).setPositiveButton("تم",null).show();
    }
    private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(-1);t.setPadding(0,dp(8),0,dp(8));return t;}
    private void add(String s){box.addView(label(s,14));}
    private void button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());box.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));}
}
