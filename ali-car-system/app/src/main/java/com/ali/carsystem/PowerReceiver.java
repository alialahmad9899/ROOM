package com.ali.carsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PowerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) return;
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { context.startActivity(launch); } catch (Exception ignored) {}
    }
}
