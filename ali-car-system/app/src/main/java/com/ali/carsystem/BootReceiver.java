package com.ali.carsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean powerConnected = Intent.ACTION_POWER_CONNECTED.equals(action);
        if (!boot && !powerConnected) return;

        if (powerConnected) {
            try {
                PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (manager != null) {
                    PowerManager.WakeLock wakeLock = manager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                        "ALICarSystem:PowerConnected"
                    );
                    wakeLock.acquire(7000L);
                }
            } catch (RuntimeException ignored) {
                // If the firmware blocks screen wake-up, still try to open the dashboard.
            }
        }

        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra(MainActivity.EXTRA_SHOW_WELCOME, true);
        launch.putExtra(MainActivity.EXTRA_BOOT_GREETING, boot);
        try {
            context.startActivity(launch);
        } catch (RuntimeException ignored) {
            // Some firmware builds restrict activity launches from background receivers.
        }
    }
}
