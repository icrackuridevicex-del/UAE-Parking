package ae.parking.sms;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private boolean arabic = true;
    private String themeMode = "system", emirate = "";
    private LinearLayout root;
    private TextView locationText, zoneStatus;
    private EditText plateCode, plateNumber, plateSource, zone;
    private Spinner duration, parkingType;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private String t(String ar, String en) { return arabic ? ar : en; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("parking", MODE_PRIVATE);
        arabic = prefs.getBoolean("arabic", true);
        themeMode = prefs.getString("theme", "system");
        applyTheme();
        show();
        askLocationOnLaunch();
    }

    private void applyTheme() {
        boolean dark = "dark".equals(themeMode);
        if ("system".equals(themeMode)) {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = mode == Configuration.UI_MODE_NIGHT_YES;
        }
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        getWindow().setStatusBarColor(dark ? 0xff101010 : 0xfff7f7f7);
        getWindow().setNavigationBarColor(dark ? 0xff101010 : 0xfff7f7f7);
    }

    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(16); v.setPadding(0, 14, 0, 5);
        root.addView(v); return v;
    }
    private EditText field(String hint, String key) {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setText(prefs.getString(key, "")); e.setHint(hint);
        root.addView(e); return e;
    }
    private void button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); root.addView(b);
        b.setOnClickListener(v -> action.run());
    }
    private void show() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 40);
        root.setLayoutDirection(arabic ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scroll.addView(root); setContentView(scroll);
        label("UAE Parking • SMS").setTextSize(25);
        button(arabic ? "English" : "العربية", () -> {
            save(); arabic = !arabic; prefs.edit().putBoolean("arabic", arabic).apply(); show();
        });
        button(t("المظهر: فاتح / داكن / حسب الهاتف", "Appearance: Light / Dark / System"), this::showThemeDialog);
        label(t("الإمارة حسب GPS", "Emirate via GPS"));
        locationText = label(emirate.isEmpty() ? t("جارٍ تحديد الإمارة...", "Detecting emirate...") : emirate);
        button(t("تحديث موقعي", "Refresh location"), this::askLocationConsent);
        label(t("رمز الموقف", "Parking zone"));
        zoneStatus = label(t("رمز المنطقة غير مؤكد. أدخله من لوحة الموقف.", "Zone not verified. Enter it from the parking sign."));
        label(t("مصدر اللوحة (DXB / SHJ / AJM...) ", "Plate registration emirate (DXB / SHJ / AJM...)"));
        plateSource = field("DXB / SHJ / AJM / UAQ", "source");
        label(t("رمز اللوحة", "Plate code")); plateCode = field("A", "code");
        label(t("رقم اللوحة", "Plate number")); plateNumber = field("12345", "number");
        label(t("رمز منطقة المواقف في دبي (من اللوحة)", "Dubai parking zone (from sign)"));
        zone = field("123A", "zone");
        label(t("نوع موقف أبوظبي", "Abu Dhabi parking type"));
        parkingType = new Spinner(this); root.addView(parkingType);
        parkingType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"S - Standard", "P - Premium"}));
        label(t("مدة الوقوف (ساعات)", "Parking duration (hours)"));
        duration = new Spinner(this); root.addView(duration); updateDuration();
        label(t("GPS يحدد الإمارة، لكنه لا يثبت رمز الموقف أو أنه مدفوع. راجع اللوحة قبل الإرسال.",
                "GPS detects the emirate, not a verified paid zone. Check the parking sign before sending."));
        button(t("فتح رسالة الدفع", "Open payment SMS"), this::pay);
        label(t("لن تُرسل رسالة تلقائياً. انتظر تأكيد الجهة الرسمية.",
                "No message is sent automatically. Wait for official payment confirmation."));
    }
    private void showThemeDialog() {
        String[] options = arabic ? new String[]{"فاتح", "داكن", "حسب الهاتف"}
                : new String[]{"Light", "Dark", "System"};
        String[] values = {"light", "dark", "system"};
        int selected = "light".equals(themeMode) ? 0 : "dark".equals(themeMode) ? 1 : 2;
        new AlertDialog.Builder(this).setTitle(t("اختر المظهر", "Choose appearance"))
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    save(); prefs.edit().putString("theme", values[which]).apply();
                    dialog.dismiss(); recreate();
                }).setNegativeButton(t("إلغاء", "Cancel"), null).show();
    }
    private void updateDuration() {
        if (duration == null) return;
        String[] options = emirate.equals("Ajman") ? new String[]{"1"}
                : emirate.equals("Sharjah") ? new String[]{"1", "2", "3", "5"}
                : new String[]{"1", "2", "3", "4"};
        duration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
    }
    private void save() {
        if (plateSource == null) return;
        prefs.edit().putString("source", plateSource.getText().toString().trim())
                .putString("code", plateCode.getText().toString().trim())
                .putString("number", plateNumber.getText().toString().trim())
                .putString("zone", zone.getText().toString().trim()).apply();
    }
    private void askLocationOnLaunch() {
        if (!prefs.getBoolean("location_prompt_seen", false)) {
            askLocationConsent();
        } else if (hasLocationPermission()) {
            locate();
        } else {
            locationText.setText(t("الموقع غير مفعل. اضغط تحديث موقعي للسماح.",
                    "Location disabled. Tap Refresh location to allow."));
        }
    }
    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void askLocationConsent() {
        if (hasLocationPermission()) { locate(); return; }
        new AlertDialog.Builder(this)
                .setTitle(t("السماح بالوصول إلى الموقع؟", "Allow location access?"))
                .setMessage(t("نستخدم موقعك لتحديد الإمارة. الموقع الدقيق يساعد على تحديد الموقف، لكن رمز المنطقة يحتاج تأكيداً من اللوحة. يمكنك الرفض أو تغيير الإذن من إعدادات الهاتف.",
                        "We use your location to detect the emirate. Precise location helps with parking detection, but the zone must be checked against the sign. You can decline or change permission in phone settings."))
                .setNegativeButton(t("ليس الآن", "Not now"), (d, w) -> {
                    prefs.edit().putBoolean("location_prompt_seen", true).apply();
                    locationText.setText(t("لم يتم السماح بالموقع", "Location not allowed"));
                })
                .setPositiveButton(t("متابعة", "Continue"), (d, w) -> {
                    prefs.edit().putBoolean("location_prompt_seen", true).apply();
                    if (Build.VERSION.SDK_INT >= 23) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                    } else locate();
                }).show();
    }
    private void locate() {
        if (locationText == null) return;
        if (!hasLocationPermission()) { askLocationConsent(); return; }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationText.setText(t("جارٍ الحصول على موقع حديث...", "Getting a fresh location..."));
            if (Build.VERSION.SDK_INT >= 30) {
                boolean fine = Build.VERSION.SDK_INT < 23 ||
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                String provider = fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
                lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> handleLocation(loc));
            } else {
                Location loc = null;
                if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                handleLocation(loc);
            }
        } catch (Exception ex) {
            locationText.setText(t("تعذر الحصول على الموقع", "Location unavailable"));
        }
    }
    private void handleLocation(Location loc) {
        if (loc == null) {
            locationText.setText(t("لم يتوفر موقع. فعّل GPS وأعد المحاولة.",
                    "Location unavailable. Enable GPS and retry.")); return;
        }
        double lat = loc.getLatitude(), lon = loc.getLongitude();
        locationText.setText(t("جارٍ التحقق من الإمارة...", "Checking emirate..."));
        worker.execute(() -> {
            String detected = "";
            try {
                Geocoder geo = new Geocoder(this, Locale.ENGLISH);
                List<Address> addresses = geo.getFromLocation(lat, lon, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);
                    String s = ((a.getAdminArea() == null ? "" : a.getAdminArea()) + " " +
                            (a.getLocality() == null ? "" : a.getLocality()) + " " +
                            (a.getSubAdminArea() == null ? "" : a.getSubAdminArea())).toLowerCase(Locale.ROOT);
                    if (s.contains("dubai")) detected = "Dubai";
                    else if (s.contains("sharjah") || s.contains("khor fakkan") || s.contains("kalba")) detected = "Sharjah";
                    else if (s.contains("ajman")) detected = "Ajman";
                    else if (s.contains("abu dhabi") || s.contains("al ain")) detected = "Abu Dhabi";
                    else if (s.contains("umm al") || s.contains("um al quwain")) detected = "Umm Al Quwain";
                    else if (s.contains("ras al") || s.contains("ras al khaimah")) detected = "Ras Al Khaimah";
                    else if (s.contains("fujairah")) detected = "Fujairah";
                }
            } catch (Exception ignored) { }
            final String result = detected;
            runOnUiThread(() -> {
                emirate = result;
                locationText.setText(result.isEmpty() ?
                        t("تعذر تأكيد الإمارة. الدفع غير متاح.", "Emirate not verified. Payment unavailable.") : result);
                zoneStatus.setText(t("الموقع: ", "Location: ") + String.format(Locale.US, "%.5f, %.5f", lat, lon)
                        + "\n" + t("رمز الموقف غير مؤكد؛ استخدم لوحة المواقف.",
                        "Parking zone unverified; use the parking sign."));
                updateDuration();
            });
        });
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 1) {
            if (hasLocationPermission()) locate();
            else locationText.setText(t("تم رفض إذن الموقع. يمكنك تفعيله من إعدادات التطبيق.",
                    "Location permission denied. You can enable it in app settings."));
        }
    }
    private void pay() {
        save();
        String src = plateSource.getText().toString().trim().toUpperCase(Locale.ROOT);
        String code = plateCode.getText().toString().trim().toUpperCase(Locale.ROOT);
        String num = plateNumber.getText().toString().trim();
        String z = zone.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (!src.matches("[A-Z]{2,4}") || !code.matches("[A-Z0-9]{1,3}") || !num.matches("[0-9]{1,7}")) {
            toast(t("راجع بيانات اللوحة", "Check plate details")); return;
        }
        String dest, body, d = duration.getSelectedItem().toString();
        if (emirate.equals("Dubai")) {
            if (!z.matches("[0-9]{1,5}[A-Z]{1,2}")) {
                toast(t("أدخل رمز دبي الصحيح من لوحة الموقف", "Enter the Dubai zone from the parking sign")); return;
            }
            dest = "7275"; body = code + num + " " + z + " " + d;
        } else if (emirate.equals("Sharjah")) {
            dest = "5566"; body = src + " " + num + " " + d;
        } else if (emirate.equals("Ajman")) {
            dest = "5155"; body = src + " " + code + " " + num;
        } else if (emirate.equals("Abu Dhabi")) {
            dest = "3009";
            String kind = parkingType.getSelectedItemPosition() == 1 ? "P" : "S";
            body = src + code + " " + num + " " + kind + " " + d;
        } else {
            toast(t("لا توجد صيغة SMS موثقة لهذه الإمارة في النسخة الحالية.",
                    "No verified SMS format for this emirate in this version.")); return;
        }
        new AlertDialog.Builder(this).setTitle(t("راجع رسالة الدفع", "Review payment SMS"))
                .setMessage(t("الإمارة: ", "Emirate: ") + emirate + "\n" +
                        t("الرقم: ", "To: ") + dest + "\nSMS: " + body + "\n\n" +
                        t("تأكد من رقم اللوحة ورمز المنطقة والمدة قبل الإرسال. فتح الرسالة لا يعني الدفع.",
                        "Verify plate, zone and duration. Opening SMS does not mean payment."))
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .setPositiveButton(t("فتح الرسائل", "Open SMS"), (dialog, which) -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + dest));
                        i.putExtra("sms_body", body); startActivity(i);
                    } catch (Exception ex) { toast(t("لا يوجد تطبيق رسائل متوافق", "No compatible SMS app")); }
                }).show();
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
