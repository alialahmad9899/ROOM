package com.ali.carsystem;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class PhoneActivity extends BaseActivity {
    private static final int REQUEST_PHONE_CONTACT = 7301;
    private TextView bluetoothStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 18, 28, 18);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF142D43, 0xFF030912, 0xFF071B2C}));

        TextView title = label("مركز الهاتف والبلوتوث", 27, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 64));

        TextView note = label("اتصال آمن عبر تطبيق الهاتف في النظام", 15, 0xFF9CC9DC);
        note.setGravity(Gravity.CENTER);
        root.addView(note, new LinearLayout.LayoutParams(-1, 42));

        TextView dial = action("فتح لوحة الاتصال", "phone_dial_button");
        root.addView(dial, buttonLayout());
        dial.setOnClickListener(v -> openDialer(null));

        TextView contacts = action("اختيار جهة اتصال", "phone_contacts_button");
        root.addView(contacts, buttonLayout());
        contacts.setOnClickListener(v -> pickContact());

        bluetoothStatus = label("", 15, 0xFF91E9FF);
        bluetoothStatus.setTag("phone_bluetooth_status");
        bluetoothStatus.setGravity(Gravity.CENTER);
        root.addView(bluetoothStatus, new LinearLayout.LayoutParams(-1, 46));

        TextView bluetooth = action("إعدادات البلوتوث والأجهزة المقترنة", "phone_bluetooth_button");
        root.addView(bluetooth, buttonLayout());
        bluetooth.setOnClickListener(v -> openBluetoothSettings());

        TextView back = action("العودة إلى لوحة السيارة", "phone_back_button");
        root.addView(back, buttonLayout());
        back.setOnClickListener(v -> finish());

        setContentView(root);
        refreshBluetoothStatus();
    }

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 64);
        lp.topMargin = 12;
        return lp;
    }

    private TextView action(String value, String tag) {
        TextView view = label(value, 18, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setTag(tag);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xCC123D56, 0xCC145C70});
        bg.setCornerRadius(18);
        bg.setStroke(1, 0x995CD9F4);
        view.setBackground(bg);
        return view;
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setIncludeFontPadding(false);
        return view;
    }

    private void openDialer(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            if (number != null && !number.trim().isEmpty()) {
                intent.setData(Uri.fromParts("tel", number, null));
            }
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "لا يوجد تطبيق اتصال متاح على الجهاز", Toast.LENGTH_LONG).show();
        }
    }

    private void pickContact() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(intent, REQUEST_PHONE_CONTACT);
        } catch (Exception error) {
            Toast.makeText(this, "تعذر فتح جهات الاتصال", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PHONE_CONTACT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        String number = null;
        String name = null;
        try (Cursor cursor = getContentResolver().query(data.getData(),
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
                number = cursor.getString(1);
            }
        } catch (Exception error) {
            Toast.makeText(this, "تعذر قراءة رقم جهة الاتصال", Toast.LENGTH_LONG).show();
            return;
        }
        if (number == null || number.trim().isEmpty()) {
            Toast.makeText(this, "جهة الاتصال لا تحتوي على رقم هاتف", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "فتح الاتصال مع " + (name == null ? number : name),
                Toast.LENGTH_SHORT).show();
        openDialer(number);
    }

    private void refreshBluetoothStatus() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            bluetoothStatus.setText("البلوتوث غير مدعوم على هذا الجهاز");
        } else {
            bluetoothStatus.setText(adapter.isEnabled()
                    ? "البلوتوث مفعّل • افتح الإعدادات لاختيار جهاز مقترن"
                    : "البلوتوث متوقف • يمكنك تفعيله من الإعدادات");
        }
    }

    private void openBluetoothSettings() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "البلوتوث غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (!adapter.isEnabled()) startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            else startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception error) {
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (bluetoothStatus != null) refreshBluetoothStatus();
    }
}
