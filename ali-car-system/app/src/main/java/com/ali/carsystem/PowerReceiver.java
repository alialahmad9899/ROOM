package com.ali.carsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restores persisted navigation after boot only.
 *
 * Plugging in a charger must never launch MainActivity: the user may be in
 * YouTube, Music, Navigation, or another foreground screen. Charging state
 * is observed by the foreground activities themselves.
 */
public final class PowerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (NavigationGuidanceService.isActive(context)) {
            try {
                context.startService(new Intent(context, NavigationGuidanceService.class));
            } catch (Exception ignored) {
                // The service records its own startup state when it can be recreated.
            }
        }
    }
}