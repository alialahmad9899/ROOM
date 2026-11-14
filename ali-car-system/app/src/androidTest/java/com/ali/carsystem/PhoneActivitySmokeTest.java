package com.ali.carsystem;

import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class PhoneActivitySmokeTest {
    @Rule public final ActivityTestRule<PhoneActivity> activity =
            new ActivityTestRule<>(PhoneActivity.class);

    @Test public void phoneAndBluetoothControlsAreAvailableWithoutDirectCallPermission() {
        PhoneActivity screen = activity.getActivity();
        assertNotNull(screen);
        View root = screen.findViewById(android.R.id.content);
        assertNotNull(root.findViewWithTag("phone_dial_button"));
        assertNotNull(root.findViewWithTag("phone_contacts_button"));
        assertNotNull(root.findViewWithTag("phone_bluetooth_button"));
        assertNotNull(root.findViewWithTag("phone_bluetooth_status"));
        assertNotNull(root.findViewWithTag("phone_back_button"));
    }
}
