package com.ali.carsystem;
import android.app.*;import android.app.admin.DevicePolicyManager;import android.content.*;import android.content.pm.PackageManager;
import android.graphics.Color;import android.os.Bundle;import android.view.*;import android.view.WindowManager;import android.widget.*;import java.util.*;
public final class AdminToolsActivity extends Activity{
private static final String[] SAFE={"com.google.android.youtube","com.google.android.apps.photos","com.google.android.music","com.google.android.videos","com.google.android.feedback","com.android.printspooler","com.google.android.apps.docs","com.asus.webstorage","com.asus.livedemo"};
private DevicePolicyManager dpm;private ComponentName admin;private final List<CheckBox> checks=new ArrayList<>();private TextView statusView;
private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
private void immersive(){getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|4096|256);}
@Override protected void onCreate(Bundle b){super.onCreate(b);immersive();dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);admin=new ComponentName(this,CarAdminReceiver.class);
LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(0xFF06101B);
TextView title=text("إدارة الجهاز ووضع القيادة",22,Color.WHITE,true);root.addView(title,new LinearLayout.LayoutParams(-1,dp(42)));
TextView info=text("إخفاء التطبيقات أو قفل الواجهة يحتاج إلى تسجيل هذا التطبيق مالكاً للجهاز. صلاحية المشرف العادية لا تكفي.",12,0xFFFFD98A,false);root.addView(info);
Button adminBtn=btn("طلب صلاحية مشرف الجهاز");adminBtn.setOnClickListener(v->requestAdmin());root.addView(adminBtn,new LinearLayout.LayoutParams(-1,dp(42)));
Button hide=btn("إخفاء التطبيقات الاختيارية المحددة");hide.setOnClickListener(v->hideSelected());root.addView(hide,new LinearLayout.LayoutParams(-1,dp(42)));
Button restore=btn("استعادة كل التطبيقات الاختيارية");restore.setOnClickListener(v->restoreOptionalApps());root.addView(restore,new LinearLayout.LayoutParams(-1,dp(42)));
Button kiosk=btn("تفعيل وضع القيادة المقفل");kiosk.setOnClickListener(v->startKiosk());root.addView(kiosk,new LinearLayout.LayoutParams(-1,dp(42)));
Button stop=btn("إيقاف وضع القيادة المقفل");stop.setOnClickListener(v->{try{stopLockTask();}catch(Exception ignored){}});root.addView(stop,new LinearLayout.LayoutParams(-1,dp(42)));
statusView=text("",12,0xFF75E6FF,true);
root.addView(statusView,new LinearLayout.LayoutParams(-1,dp(44)));

root.addView(text("يتم تسجيل مالك الجهاز خلال إعداد الجهاز الأولي، عادةً بمساعدة الحاسوب وبعد إعادة ضبط المصنع.",11,0xFFB9CFDD,false));
root.addView(text("حزم اختيارية فقط. لا تتضمن الهاتف أو البلوتوث أو إعدادات النظام أو خدمات الموقع.",11,Color.WHITE,true));
ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);
String[] names={"تطبيق يوتيوب","تطبيق الصور","مشغل الموسيقى","مشغل الفيديو","خدمات الملاحظات","خدمة الطباعة","المستندات","التخزين السحابي","العرض التجريبي"};int index=0;for(String pkg:SAFE){CheckBox c=new CheckBox(this);c.setText(names[index++]);c.setTextColor(Color.WHITE);c.setTextSize(12);c.setTag(pkg);try{c.setChecked(dpm.isApplicationHidden(admin,pkg));}catch(Exception ignored){}checks.add(c);list.addView(c);}
root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));Button back=btn("رجوع");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(40)));setContentView(root);}
private String ownerStatus(){
boolean adminActive=dpm!=null&&admin!=null&&dpm.isAdminActive(admin);
boolean owner=dpm!=null&&dpm.isDeviceOwnerApp(getPackageName());
return "مشرف الجهاز: "+(adminActive?"مفعّل":"غير مفعّل")+"  •  مالك الجهاز: "+(owner?"مفعّل":"غير مفعّل")
        +"  •  Root: غير متوفر حسب فحص المستخدم";
}
private void refreshAdminStatus(){if(statusView!=null)statusView.setText(ownerStatus());}
private void restoreOptionalApps(){
if(!dpm.isDeviceOwnerApp(getPackageName())){
new AlertDialog.Builder(this).setTitle("تحتاج صلاحية مالك الجهاز")
.setMessage("إعادة إتاحة التطبيقات تتطلب Device Owner، وليست متاحة بصلاحية مشرف الجهاز العادية.")
.setPositiveButton("حسناً",null).show();return;}
int done=0,failed=0;
for(String pkg:SAFE){
try{if(getPackageManager().getApplicationInfo(pkg,0)!=null&&dpm.setApplicationHidden(admin,pkg,false))done++;}
catch(Exception error){failed++;}
}
for(CheckBox check:checks)check.setChecked(false);
Toast.makeText(this,"تمت استعادة: "+done+" • تعذرت استعادة: "+failed,Toast.LENGTH_LONG).show();
refreshAdminStatus();
}
private void requestAdmin(){Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin);i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"إدارة وضع السيارة المقفل");startActivityForResult(i,41);}
private void hideSelected(){if(!dpm.isDeviceOwnerApp(getPackageName())){new AlertDialog.Builder(this).setTitle("يجب تفعيل مالك الجهاز").setMessage("لا يمكن لتطبيق عادي إخفاء تطبيقات النظام. يجب تسجيله مالكاً للجهاز أثناء الإعداد الأولي.").setPositiveButton("حسناً",null).show();return;}
int done=0,failed=0;for(CheckBox c:checks)if(c.isChecked()){String pkg=(String)c.getTag();try{getPackageManager().getApplicationInfo(pkg,0);if(dpm.setApplicationHidden(admin,pkg,true))done++;else failed++;}catch(Exception e){failed++;}}
Toast.makeText(this,"تم الإخفاء: "+done+" • تعذر: "+failed,Toast.LENGTH_LONG).show();refreshAdminStatus();}
private void startKiosk(){if(!dpm.isDeviceOwnerApp(getPackageName())){Toast.makeText(this,"يجب تسجيل التطبيق مالكاً للجهاز لتفعيل وضع القيادة المقفل.",Toast.LENGTH_LONG).show();return;}
try{dpm.setLockTaskPackages(admin,new String[]{getPackageName()});startLockTask();}catch(Exception e){Toast.makeText(this,"تعذر تفعيل وضع القيادة المقفل.",Toast.LENGTH_LONG).show();}}
private TextView text(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(c);t.setTextSize(z);t.setGravity(Gravity.CENTER_VERTICAL);t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?1:0));return t;}
private Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
@Override protected void onResume(){super.onResume();immersive();refreshAdminStatus();}
@Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
super.onActivityResult(requestCode,resultCode,data);refreshAdminStatus();
}
}