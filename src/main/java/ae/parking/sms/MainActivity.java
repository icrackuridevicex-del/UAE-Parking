package ae.parking.sms;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  SharedPreferences prefs; boolean arabic=true; String emirate="";
  LinearLayout root; TextView locationText, warning; EditText plateCode, plateNumber, plateSource, zone;
  Spinner duration, parkingType; ExecutorService worker=Executors.newSingleThreadExecutor();
  String t(String ar,String en){return arabic?ar:en;}
  @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("parking",MODE_PRIVATE);arabic=prefs.getBoolean("arabic",true);show();}
  TextView label(String s){TextView v=new TextView(this);v.setText(s);v.setTextSize(16);v.setPadding(0,14,0,5);root.addView(v);return v;}
  EditText field(String hint,String key){EditText e=new EditText(this);e.setSingleLine(true);e.setText(prefs.getString(key,""));e.setHint(hint);root.addView(e);return e;}
  Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);root.addView(b);b.setOnClickListener(v->action.run());return b;}
  void show(){
    ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,28,28,40);root.setLayoutDirection(arabic?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);scroll.addView(root);setContentView(scroll);
    label("UAE Parking • SMS").setTextSize(25);
    button(arabic?"English":"العربية",()->{save();arabic=!arabic;prefs.edit().putBoolean("arabic",arabic).apply();show();});
    label(t("الإمارة حسب GPS","Emirate via GPS"));locationText=label(emirate.isEmpty()?t("لم يتم تحديد الإمارة","Emirate not detected"):emirate);
    button(t("تحديد موقعي","Detect my location"),this::locate);
    label(t("مصدر اللوحة (مثال DXB أو SHJ)","Plate registration emirate (e.g. DXB, SHJ)"));plateSource=field("DXB / SHJ / AJM / UAQ...","source");
    label(t("رمز اللوحة (مثال A)","Plate category/code (e.g. A)"));plateCode=field("A","code");
    label(t("رقم اللوحة","Plate number"));plateNumber=field("12345","number");
    label(t("رمز منطقة المواقف في دبي","Dubai parking zone code"));zone=field("123A","zone");
    label(t("نوع الموقف في أبوظبي","Abu Dhabi parking type"));parkingType=new Spinner(this);root.addView(parkingType);parkingType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"S - Standard", "P - Premium"}));
    label(t("مدة الوقوف","Parking duration"));duration=new Spinner(this);root.addView(duration);
    warning=label(t("تحقق من لوحة الموقف، فالـGPS يحدد الإمارة وليس بالضرورة رمز المنطقة أو كون الموقف مدفوعاً.","Check the parking sign: GPS identifies the emirate, not necessarily the zone or whether parking is paid."));
    button(t("فتح رسالة الدفع","Open payment SMS"),this::pay);
    label(t("لن تُرسل أي رسالة تلقائياً. انتظر رسالة تأكيد الجهة قبل اعتبار الموقف مدفوعاً.","No SMS is sent automatically. Wait for official payment confirmation."));
    updateDuration();
  }
  void updateDuration(){String[] options=emirate.equals("Ajman")?new String[]{"1"}:emirate.equals("Sharjah")?new String[]{"1","2","3","5"}:new String[]{"1","2","3","4"};duration.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));}
  void save(){if(plateSource!=null)prefs.edit().putString("source",plateSource.getText().toString().trim()).putString("code",plateCode.getText().toString().trim()).putString("number",plateNumber.getText().toString().trim()).putString("zone",zone.getText().toString().trim()).apply();}
  void locate(){if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},1);return;}try{
    LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location loc=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(loc==null)loc=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    if(loc==null){locationText.setText(t("لا يوجد موقع متاح. فعّل GPS وحاول مجدداً.","Location unavailable. Enable GPS and retry."));return;}
    final double lat=loc.getLatitude(),lon=loc.getLongitude();locationText.setText(t("جارٍ التحقق من الإمارة...","Checking emirate..."));
    worker.execute(()->{String detected="";try{Geocoder geo=new Geocoder(this,Locale.ENGLISH);List<Address> list=geo.getFromLocation(lat,lon,1);if(list!=null&&!list.isEmpty()){Address a=list.get(0);String s=((a.getAdminArea()==null?"":a.getAdminArea())+" "+(a.getLocality()==null?"":a.getLocality())).toLowerCase(Locale.ROOT);if(s.contains("dubai"))detected="Dubai";else if(s.contains("sharjah")||s.contains("khor fakkan")||s.contains("kalba"))detected="Sharjah";else if(s.contains("ajman"))detected="Ajman";else if(s.contains("abu dhabi"))detected="Abu Dhabi";else if(s.contains("umm al")||s.contains("umm al-q")||s.contains("umm al quwain")||s.contains("um al quwain"))detected="Umm Al Quwain";else if(s.contains("ras al"))detected="Ras Al Khaimah";else if(s.contains("fujairah"))detected="Fujairah";}}catch(Exception ignored){}final String result=detected;runOnUiThread(()->{emirate=result;locationText.setText(result.isEmpty()?t("تعذر التحقق من الإمارة. لا يمكن تجهيز SMS.","Could not verify emirate; SMS disabled."):result);updateDuration();});});
  }catch(SecurityException ex){locationText.setText(t("يلزم إذن الموقع","Location permission required"));}catch(Exception ex){locationText.setText(t("تعذر تحديد الموقع","Location unavailable"));}}
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==1&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)locate();}
  void pay(){save();String src=plateSource.getText().toString().trim().toUpperCase(Locale.ROOT),code=plateCode.getText().toString().trim().toUpperCase(Locale.ROOT),num=plateNumber.getText().toString().trim(),z=zone.getText().toString().trim().toUpperCase(Locale.ROOT);
    if(!src.matches("[A-Z]{2,4}")||!code.matches("[A-Z0-9]{1,3}")||!num.matches("[0-9]{1,7}")){toast(t("راجع بيانات اللوحة","Check plate details"));return;}
    String dest,body;String d=duration.getSelectedItem().toString();if(emirate.equals("Dubai")){if(z.isEmpty()){toast(t("أدخل رمز منطقة دبي من لوحة المواقف","Enter Dubai zone code from parking sign"));return;}dest="7275";body=code+num+" "+z+" "+d;}
    else if(emirate.equals("Sharjah")){dest="5566";body=src+" "+num+" "+d;}
    else if(emirate.equals("Ajman")){dest="5155";body=src+" "+code+" "+num;}
    else if(emirate.equals("Abu Dhabi")){dest="3009";String kind=parkingType.getSelectedItemPosition()==1?"P":"S";body=src+code+" "+num+" "+kind+" "+d;}
    else{toast(t("تم تحديد الإمارة، لكن لا توجد صيغة SMS رسمية مؤكدة لهذا المكان. راجع لوحة المواقف.","Emirate detected, but no verified local SMS payment format. Check the parking sign."));return;}
    new android.app.AlertDialog.Builder(this).setTitle(t("راجع بيانات الدفع","Review payment SMS"))
    .setMessage(t("الإمارة: ","Emirate: ")+emirate+"\n"+t("الرقم: ","To: ")+dest+"\nSMS: "+body+"\n\n"+t("تأكد من اللوحة ورمز المنطقة والمدة. لا يعني فتح الرسالة أن الموقف مدفوع.","Verify plate, zone and duration. Opening SMS does not mean parking is paid."))
    .setNegativeButton(t("إلغاء","Cancel"),null).setPositiveButton(t("فتح الرسائل","Open SMS"),(dialog,which)->{try{Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+dest));i.putExtra("sms_body",body);startActivity(i);}catch(Exception ex){toast(t("لا يوجد تطبيق رسائل متوافق","No compatible SMS app"));}}).show();
  }
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
