package org.commcare.preferences;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.GlobalPrivilegeClaimingActivity;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

public class AppManagerDeveloperPreferences extends CommCarePreferenceFragment {

    private final static String ENABLE_PRIVILEGE = "enable-mobile-privilege";
    private final static String ENABLE_CONNECT_ID = "enable-connect-id";
    private static final String DEVELOPER_PREFERENCES_ENABLED = "developer-preferences-enabled";
    private static final String CONNECT_ID_ENABLED = "connect_id-enabled";

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(ENABLE_PRIVILEGE, "menu.enable.privileges");
        keyToTitleMap.put(ENABLE_CONNECT_ID, "menu.enable.connect.id");
    }

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("app.manager.developer.options.title");
    }

    @Nullable
    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return keyToTitleMap;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.app_manager_developer_preferences;
    }

    @Override
    protected void setupPrefClickListeners() {
        Preference enablePrivilegesButton = findPreference(ENABLE_PRIVILEGE);
        enablePrivilegesButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.ENABLE_PRIVILEGES);
            launchPrivilegeClaimActivity();
            return true;
        });

        Preference enableConectIDButton = findPreference(ENABLE_CONNECT_ID);
        enableConectIDButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.ENABLE_CONNECT_ID);
            toggleConnectIDEnabled();
            return true;
        });
    }

    private void toggleConnectIDEnabled() {
        AppManagerDeveloperPreferences.setConnectIDEnabled(true);
        Toast.makeText(getContext(), Localization.get("connect.id.enabled"), Toast.LENGTH_SHORT).show();
    }

    private void launchPrivilegeClaimActivity() {
        Intent i = new Intent(getActivity(), GlobalPrivilegeClaimingActivity.class);
        startActivity(i);
    }

    public static void setDeveloperPreferencesEnabled(boolean enabled) {
        GlobalPrivilegesManager.getGlobalPrefsRecord()
                .edit()
                .putBoolean(DEVELOPER_PREFERENCES_ENABLED, enabled)
                .apply();
    }

    public static boolean isDeveloperPreferencesEnabled() {
        return GlobalPrivilegesManager.getGlobalPrefsRecord().getBoolean(DEVELOPER_PREFERENCES_ENABLED, false);
    }

    public static void setConnectIDEnabled(boolean enabled) {
        GlobalPrivilegesManager.getGlobalPrefsRecord()
                .edit()
                .putBoolean(CONNECT_ID_ENABLED, enabled)
                .apply();
    }

    public static boolean isConnectIDEnabled() {
        return GlobalPrivilegesManager.getGlobalPrefsRecord().getBoolean(CONNECT_ID_ENABLED, false);
    }
}
