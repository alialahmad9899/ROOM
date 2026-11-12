package com.ali.carsystem;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public final class CarAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context c, Intent i) {
        Toast.makeText(c,"تم تفعيل مسؤول الجهاز",Toast.LENGTH_LONG).show();
    }
    @Override public void onDisabled(Context c, Intent i) {
        Toast.makeText(c,"تم إلغاء مسؤول الجهاز",Toast.LENGTH_LONG).show();
    }
}
