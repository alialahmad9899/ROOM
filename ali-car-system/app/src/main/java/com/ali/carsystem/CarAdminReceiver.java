package com.ali.carsystem;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public final class CarAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "تم تفعيل صلاحية إدارة الجهاز", Toast.LENGTH_LONG).show();
    }
    @Override public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "تم إلغاء صلاحية إدارة الجهاز", Toast.LENGTH_LONG).show();
    }
}
