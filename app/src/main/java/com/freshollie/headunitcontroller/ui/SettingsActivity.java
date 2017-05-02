package com.freshollie.headunitcontroller.ui;


import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.freshollie.headunitcontroller.R;
import com.freshollie.headunitcontroller.input.DeviceInputManager;
import com.freshollie.headunitcontroller.input.DeviceKeyMapper;
import com.freshollie.headunitcontroller.service.MainService;
import com.freshollie.headunitcontroller.utils.DummyPreference;
import com.freshollie.headunitcontroller.utils.PowerUtil;
import com.freshollie.headunitcontroller.utils.StatusUtil;
import com.freshollie.shuttlexpressdriver.ShuttleXpressDevice;

import java.util.List;

import static com.freshollie.headunitcontroller.service.MainService.ACTION_START_INPUT_SERVICE;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity implements StatusUtil.OnStatusChangeListener {

    private String TAG = SettingsActivity.class.getSimpleName();

    private SharedPreferences sharedPreferences;

    private StatusUtil statusUtil;

    private static boolean isLargeScreen(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static void startMainService(Context context, String action) {
        context.startService(new Intent(context, MainService.class).setAction(action));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        statusUtil = StatusUtil.getInstance();

        if (!sharedPreferences.getBoolean(getString(R.string.pref_debug_enabled_key), false)) {
            if (PowerUtil.isConnected(getApplicationContext())) {
                startMainService(this, Intent.ACTION_POWER_CONNECTED);
            }
        }
    }

    @Override
    public void onResume() {
        statusUtil.addOnStatusChangeListener(this);
        onStatusChange(statusUtil.getStatus());

        super.onResume();
    }

    @Override
    public void onPause() {
        statusUtil.removeOnStatusChangeListener(this);

        super.onPause();
    }

    @Override
    public void onStatusChange(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    String titleString = getString(R.string.title_activity_settings) + " - " +
                            newStatus;

                    if (newStatus == null) {
                        titleString = getString(R.string.title_activity_settings);
                    }

                    actionBar.setTitle(titleString);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isLargeScreen(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || WakeUpPreferencesFragment.class.getName().equals(fragmentName)
                || SuspendPreferencesFragment.class.getName().equals(fragmentName)
                || DebuggingFragment.class.getName().equals(fragmentName)
                || InputPreferencesFragment.class.getName().equals(fragmentName);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class WakeUpPreferencesFragment extends PreferenceFragment {

        ListPreference maxDevicesPreference;
        EditTextPreference routineDelayPreference;
        SwitchPreference setVolumeSwitchPreference;
        ListPreference volumeLevelPreference;
        Preference shellCommandsPreference;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_wakeup);

            setHasOptionsMenu(true);

            maxDevicesPreference = (ListPreference)
                    findPreference(getString(R.string.pref_num_devices_key));

            routineDelayPreference = (EditTextPreference)
                    findPreference(getString(R.string.pref_wake_up_delay_key));

            setVolumeSwitchPreference = (SwitchPreference)
                    findPreference(getString(R.string.pref_set_volume_key));

            volumeLevelPreference = (ListPreference)
                    findPreference(getString(R.string.pref_volume_level_key));

            shellCommandsPreference = findPreference(getString(R.string.pref_shell_commands_key));

            setupPreferences();

        }

        private void setupPreferences() {

            routineDelayPreference.setSummary(getString(R.string.pref_wake_up_delay_summary,
                    routineDelayPreference.getText()));

            routineDelayPreference.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object o) {
                            preference.setSummary(getString(R.string.pref_wake_up_delay_summary, o));

                            View view = getActivity().getCurrentFocus();
                            if (view != null) {
                                InputMethodManager inputManager = (InputMethodManager)
                                        getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                                inputManager.hideSoftInputFromWindow(view.getWindowToken(),
                                        InputMethodManager.HIDE_NOT_ALWAYS);
                            }

                            return true;
                        }
                    }
            );

            String[] numDevicesValues = new String[101];

            for (int i = 0; i < 101; i++) {
                numDevicesValues[i] = String.valueOf(i);
            }

            maxDevicesPreference.setEntries(numDevicesValues);
            maxDevicesPreference.setEntryValues(numDevicesValues);

            setVolumeSwitchPreference.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object o) {
                            boolean newValue = (boolean) o;
                            volumeLevelPreference.setEnabled(newValue);

                            return true;
                        }
                    }
            );

            String[] volumes = new String[17];
            for (int i = 0; i < 17; i++) {
                volumes[i] = String.valueOf(i);
            }

            volumeLevelPreference.setEnabled(setVolumeSwitchPreference.isChecked());
            volumeLevelPreference.setEntries(volumes);
            volumeLevelPreference.setEntryValues(volumes);

            volumeLevelPreference.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object o) {
                            preference.setSummary(getString(R.string.pref_volume_level_summary, o));
                            return true;
                        }
                    }
            );



        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SuspendPreferencesFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_suspend);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }


    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class InputPreferencesFragment extends PreferenceFragment {

        DeviceKeyMapper keyMapper;

        Preference[] deviceKeyPreferences = new Preference[ShuttleXpressDevice.KeyCodes.NUM_KEYS];

        Preference defaultsPreference;
        Preference startInputPreference;

        PreferenceCategory buttonCategory;
        PreferenceCategory wheelCategory;
        PreferenceCategory ringCategory;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);

            keyMapper = new DeviceKeyMapper(getActivity());

            setupPage();
        }

        private String getSummaryForKey(int key, boolean hold) {
            String summary = "";

            String[] actionAndExtra;

            if (!hold) {
                actionAndExtra = keyMapper.getKeyPressAction(key);
            } else {
                actionAndExtra = keyMapper.getKeyHoldAction(key);
            }

            if (actionAndExtra[0] != null) {
                if (hold) {
                    summary += "Hold " + keyMapper.getKeyHoldDelay(key) + "ms: " +
                            DeviceInputManager.getStringForAction(getActivity(), actionAndExtra[0]);
                } else {
                    summary += "Press: " +
                            DeviceInputManager.getStringForAction(getActivity(), actionAndExtra[0]);
                }

                if (actionAndExtra[0].equals(DeviceInputManager.ACTION_SEND_KEYEVENT)) {
                    int keyCode = Integer.parseInt(actionAndExtra[1]);
                    summary += " -> " + KeyEvent.keyCodeToString(keyCode);

                } else if (actionAndExtra[0].equals(DeviceInputManager.ACTION_LAUNCH_APP)) {
                    summary += " -> " + actionAndExtra[1];

                }
            } else {
                if (hold) {
                    summary += "Hold: None";
                } else {
                    summary += "Press: None";
                }
            }

            if (!hold) {
                summary += "\n";
                summary += getSummaryForKey(key, true);
            }

            return summary;
        }

        public String getSummaryForKey(int key) {
            return getSummaryForKey(key, false);
        }

        public void launchKeySetDialog(int key) {
            KeySetDialog dialog = new KeySetDialog();
            dialog.setKey(key);
            dialog.show(getFragmentManager(), "KeysetDialog");
        }

        private void setupPage() {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());

            DummyPreference d = new DummyPreference(getActivity(), null);
            screen.addPreference(d);

            SwitchPreference inputEnabledPreference = new SwitchPreference(getActivity());
            inputEnabledPreference.setTitle(R.string.pref_input_service_enabled_title);
            inputEnabledPreference.setKey(getString(R.string.pref_input_service_enabled_key));
            inputEnabledPreference.setSummaryOn(R.string.pref_input_service_enabled_summary_on);
            inputEnabledPreference.setSummaryOff(R.string.pref_input_service_enabled_summary_off);
            inputEnabledPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean value = (boolean) o;

                    for (Preference keyPreference: deviceKeyPreferences) {
                        keyPreference.setEnabled(value);
                    }

                    startInputPreference.setEnabled(value);
                    defaultsPreference.setEnabled(value);

                    buttonCategory.setEnabled(false);
                    wheelCategory.setEnabled(false);
                    ringCategory.setEnabled(false);


                    return true;
                }
            });

            screen.addPreference(inputEnabledPreference);

            startInputPreference = new Preference(getActivity());
            startInputPreference.setTitle(R.string.pref_launch_input_title);
            startInputPreference.setSummary(R.string.pref_launch_input_summary);
            startInputPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startMainService(getActivity(), ACTION_START_INPUT_SERVICE);
                    return true;
                }
            });
            startInputPreference.setEnabled(inputEnabledPreference.isChecked());
            screen.addPreference(startInputPreference);

            defaultsPreference = new Preference(getActivity());
            defaultsPreference.setTitle("Reset to default");
            defaultsPreference.setSummary("Reset all input settings to their original values");
            defaultsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setMessage(
                                    "Are you sure you want to set the input preferences to default?")
                            .setPositiveButton(android.R.string.no, null)
                            .setNegativeButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    keyMapper.setDefaults();
                                    setupPage();
                                }
                            })
                            .show();
                    return true;
                }
            });
            defaultsPreference.setEnabled(inputEnabledPreference.isChecked());
            screen.addPreference(defaultsPreference);

            for (int i = 0; i < deviceKeyPreferences.length; i++) {
                if (i == 0) {
                    PreferenceCategory category = new PreferenceCategory(getActivity());
                    category.setTitle("Buttons");
                    screen.addPreference(category);
                    buttonCategory = category;
                    category.setEnabled(inputEnabledPreference.isChecked());

                } else if (i == ShuttleXpressDevice.KeyCodes.ALL_BUTTONS.size()) {
                    PreferenceCategory category = new PreferenceCategory(getActivity());
                    category.setTitle("Ring");
                    screen.addPreference(category);
                    ringCategory = category;
                    category.setEnabled(inputEnabledPreference.isChecked());

                } else if (i == ShuttleXpressDevice.KeyCodes.ALL_KEYS.size() - 2) {
                    PreferenceCategory category = new PreferenceCategory(getActivity());
                    category.setTitle("Wheel");
                    screen.addPreference(category);
                    wheelCategory = category;
                    category.setEnabled(inputEnabledPreference.isChecked());
                }

                final int key = ShuttleXpressDevice.KeyCodes.ALL_KEYS.get(i);

                Preference keyPreference = new Preference(getActivity());
                keyPreference.setTitle(DeviceInputManager.getNameForDeviceKey(getActivity(), key));
                keyPreference.setSummary(getSummaryForKey(key));
                keyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        launchKeySetDialog(key);
                        return true;
                    }
                });

                keyPreference.setEnabled(inputEnabledPreference.isChecked());

                screen.addPreference(keyPreference);

                deviceKeyPreferences[i] = keyPreference;

                setPreferenceScreen(screen);
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DebuggingFragment extends PreferenceFragment {

        SwitchPreference debugEnabledToggle;
        SwitchPreference debugPowerToggle;
        private String TAG = DebuggingFragment.class.getSimpleName();

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_debug);
            setHasOptionsMenu(false);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.

            debugEnabledToggle = (SwitchPreference) findPreference(getString(R.string.pref_debug_enabled_key));
            debugPowerToggle = (SwitchPreference) findPreference(getString(R.string.pref_power_on_debug_key));

            setupToggles();
        }

        public void setupToggles() {
            final Boolean debugEnabled = debugEnabledToggle.isChecked();

            debugPowerToggle.setEnabled(debugEnabled);

            debugEnabledToggle.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean switchValue = (boolean) newValue;

                    Log.v(TAG, "Debug enabled: " + String.valueOf(switchValue));

                    if (switchValue) {
                        if (debugPowerToggle.isChecked()) {
                            startMainService(getActivity(), Intent.ACTION_POWER_CONNECTED);
                        } else {
                            startMainService(getActivity(), Intent.ACTION_POWER_DISCONNECTED);
                        }
                    }

                    debugPowerToggle.setEnabled(switchValue);

                    return true;
                }
            });


            debugPowerToggle.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean switchValue = (boolean) newValue;

                    Log.v(TAG, "Debug power: " + String.valueOf(switchValue));

                    if (debugEnabledToggle.isChecked()){
                        if (switchValue) {
                            startMainService(getActivity(), Intent.ACTION_POWER_CONNECTED);
                        } else {
                            startMainService(getActivity(), Intent.ACTION_POWER_DISCONNECTED);
                        }
                    }

                    return true;
                }
            });
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
