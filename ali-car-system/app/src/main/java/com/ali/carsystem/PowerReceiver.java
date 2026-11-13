package com.ali.carsystem;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Enables Bluetooth when vehicle power is connected. Android's bonded-profile
 * manager owns the actual A2DP reconnection; this receiver deliberately does
 * not use hidden APIs or reflective profile.connect() calls.
 */
public final class PowerReceiver extends BroadcastReceiver {
    private static final String PREFS = "ali_car_power";
    private static final String KEY_LAST_DEVICE_ADDRESS = "last_audio_device_address";
    private static final String KEY_LAST_DEVICE_NAME = "last_audio_device_name";
    private static final String KEY_ENABLED_BY_CHARGER = "enabled_by_charger";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String action = intent.getAction();

        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            enableBluetoothForVehicle(preferences);
            return;
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            rememberConnectedAudioDevice(intent, preferences);
        }
    }

    private void enableBluetoothForVehicle(SharedPreferences preferences) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            preferences.edit().putString("last_power_event", "bluetooth_not_supported").apply();
            return;
        }
        try {
            boolean alreadyEnabled = adapter.isEnabled();
            if (!alreadyEnabled) {
                boolean requestAccepted = adapter.enable();
                preferences.edit()
                        .putBoolean(KEY_ENABLED_BY_CHARGER, requestAccepted)
                        .putString("last_power_event", requestAccepted
                                ? "bluetooth_enable_requested" : "bluetooth_enable_rejected")
                        .apply();
            } else {
                preferences.edit()
                        .putBoolean(KEY_ENABLED_BY_CHARGER, false)
                        .putString("last_power_event", "bluetooth_already_enabled")
                        .apply();
            }
            // For an already bonded vehicle, Android's Bluetooth profile service
            // is responsible for reconnecting A2DP/HFP after the adapter turns on.
        } catch (SecurityException | RuntimeException error) {
            preferences.edit().putString("last_power_event", "bluetooth_permission_or_state_error").apply();
        }
    }

    private void rememberConnectedAudioDevice(Intent intent, SharedPreferences preferences) {
        try {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) return;
            BluetoothClass deviceClass = device.getBluetoothClass();
            if (deviceClass == null
                    || deviceClass.getMajorDeviceClass() != BluetoothClass.Device.Major.AUDIO_VIDEO) {
                return;
            }
            String address = device.getAddress();
            String name = device.getName();
            if (address == null || address.trim().isEmpty()) return;
            preferences.edit()
                    .putString(KEY_LAST_DEVICE_ADDRESS, address)
                    .putString(KEY_LAST_DEVICE_NAME, name == null ? "جهاز صوتي مقترن" : name)
                    .apply();
        } catch (SecurityException ignored) {
            // Keep the previously known paired device if the platform withholds device metadata.
        }
    }
}
