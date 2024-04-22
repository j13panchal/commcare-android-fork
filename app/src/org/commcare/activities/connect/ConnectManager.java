package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.AppUtils;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.activities.SettingsHelper;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.connect.workers.ConnectHeartbeatWorker;
import org.commcare.core.encryption.CryptUtil;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.CrashUtil;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.util.PropertyUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import androidx.annotation.Nullable;

/**
 * Manager class for ConnectID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class ConnectManager {
    private static final String CONNECT_WORKER = "connect_worker";
    private static final long PERIODICITY_FOR_HEARTBEAT_IN_HOURS = 4;
    private static final long BACKOFF_DELAY_FOR_HEARTBEAT_RETRY = 5 * 60 * 1000L; // 5 mins
    private static final String CONNECT_HEARTBEAT_REQUEST_NAME = "connect_hearbeat_periodic_request";
    private static final int APP_DOWNLOAD_TASK_ID = 4;

    /**
     * Enum representing the current state of ConnectID
     */
    public enum ConnectIdStatus {
        NotIntroduced,
        Registering,
        LoggedIn
    }

    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;
    private ConnectTask phase = ConnectTask.CONNECT_NO_ACTIVITY;

    //Only used for remembering the phone number between the first and second registration screens
    private String primaryPhone = null;
    private String recoveryPhone = null;
    private String recoverySecret = null;
    private boolean forgotPassword = false;
    private boolean passwordOnlyWorkflow = false;
    private String primedAppIdForAutoLogin = null;

    //Singleton, private constructor
    private ConnectManager() {
    }

    private static ConnectManager getInstance() {
        if (manager == null) {
            manager = new ConnectManager();
        }

        return manager;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;
        ConnectDatabaseHelper.init(parent);

        if(manager.connectStatus == ConnectIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectTask.CONNECT_NO_ACTIVITY;
                manager.connectStatus = registering ? ConnectIdStatus.Registering : ConnectIdStatus.LoggedIn;
            }
        }
    }

    private static void scheduleHearbeat() {
        if (AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();

            PeriodicWorkRequest heartbeatRequest =
                    new PeriodicWorkRequest.Builder(ConnectHeartbeatWorker.class,
                            PERIODICITY_FOR_HEARTBEAT_IN_HOURS,
                            TimeUnit.HOURS)
                            .addTag(CONNECT_WORKER)
                            .setConstraints(constraints)
                            .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL,
                                    BACKOFF_DELAY_FOR_HEARTBEAT_RETRY,
                                    TimeUnit.MILLISECONDS)
                            .build();

            WorkManager.getInstance(CommCareApplication.instance()).enqueueUniquePeriodicWork(
                    CONNECT_HEARTBEAT_REQUEST_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    heartbeatRequest
            );
        }
    }

    public static void setParent(CommCareActivity<?> parent) {
        getInstance().parentActivity = parent;
    }

    public static boolean isConnectIdIntroduced() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean isUnlocked() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    private static DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }

    public static String formatDateTime(Date date) {
        return SimpleDateFormat.getDateTimeInstance().format(date);
    }

    public static boolean shouldShowSignInMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus != ConnectIdStatus.LoggedIn;
    }

    public static boolean shouldShowSignOutMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static String getConnectButtonText(Context context) {
        return switch (getInstance().connectStatus) {
            case Registering, NotIntroduced ->
                    context.getString(R.string.connect_button_logged_out);
            case LoggedIn -> context.getString(R.string.connect_button_logged_in);
        };
    }

    public static boolean shouldShowConnectButton() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    private static void completeSignin() {
        ConnectManager instance = getInstance();
        instance.connectStatus = ConnectIdStatus.LoggedIn;

        scheduleHearbeat();
        CrashUtil.registerConnectUser();

        if(instance.loginListener != null) {
            instance.loginListener.connectActivityComplete(true);
        }
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectDatabaseHelper.getUser(context);
    }

    public static void forgetUser() {
        ConnectManager manager = getInstance();

        ConnectDatabaseHelper.forgetUser(manager.parentActivity);

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
        manager.loginListener = null;
        manager.phase = ConnectTask.CONNECT_NO_ACTIVITY;
        manager.primaryPhone = null;
        manager.recoveryPhone = null;
        manager.recoverySecret = null;
        manager.forgotPassword = false;
    }

    public static void filterConnectManagedApps(Context context, ArrayList<ApplicationRecord> readyApps, String presetAppId) {
        if(ConnectManager.isConnectIdIntroduced()) {
            //We need to remove any apps that are managed by Connect
            String username = ConnectManager.getUser(context).getUserId().toLowerCase(Locale.getDefault());
            for(int i= readyApps.size()-1; i>=0; i--) {
                String appId = readyApps.get(i).getUniqueId();
                //Preset app needs to remain in the list if set
                if(!appId.equals(presetAppId)) {
                    if (isLoginManagedByConnectId(appId, username)) {
                        //Creds stored for the CID username indicates this app is managed by Connect
                        readyApps.remove(i);
                    }
                }
            }
        }
    }

    public static void unlockConnect(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        if(manager.connectStatus == ConnectIdStatus.LoggedIn) {
            manager.parentActivity = parent;
            manager.loginListener = listener;
            manager.forgotPassword = false;

            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            manager.phase = user.shouldForcePassword() ?
                    ConnectTask.CONNECT_UNLOCK_PASSWORD :
                    ConnectTask.CONNECT_UNLOCK_BIOMETRIC;

            manager.continueWorkflow();
        }
    }

    public static void registerUser(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;
        manager.loginListener = listener;
        manager.forgotPassword = false;

        ConnectTask requestCode = ConnectTask.CONNECT_NO_ACTIVITY;
        switch (manager.connectStatus) {
            case NotIntroduced -> {
                requestCode = ConnectTask.CONNECT_REGISTER_OR_RECOVER_DECISION;
            }
            case Registering -> {
                ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                ConnectTask phase = user.getRegistrationPhase();
                if (phase != ConnectTask.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                } else {
                    requestCode = user.shouldForcePassword() ?
                            ConnectTask.CONNECT_UNLOCK_PASSWORD :
                            ConnectTask.CONNECT_UNLOCK_BIOMETRIC;
                }
            }
            default -> {
                //Error, should never get here
            }
        }

        if (requestCode != ConnectTask.CONNECT_NO_ACTIVITY) {
            manager.phase = requestCode;
            manager.continueWorkflow();
        }
    }

    public static void handleConnectButtonPress(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;
        manager.loginListener = listener;
        manager.forgotPassword = false;

        ConnectTask requestCode = ConnectTask.CONNECT_NO_ACTIVITY;
        switch (manager.connectStatus) {
            case NotIntroduced -> {
                requestCode = ConnectTask.CONNECT_REGISTER_OR_RECOVER_DECISION;
            }
            case Registering -> {
                ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                ConnectTask phase = user.getRegistrationPhase();
                if (phase != ConnectTask.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                } else {
                    requestCode = user.shouldForcePassword() ?
                            ConnectTask.CONNECT_UNLOCK_PASSWORD :
                            ConnectTask.CONNECT_UNLOCK_BIOMETRIC;
                }
            }
            case LoggedIn -> {
                goToConnectJobsList();
            }
        }

        if (requestCode != ConnectTask.CONNECT_NO_ACTIVITY) {
            manager.phase = requestCode;
            manager.continueWorkflow();
        }
    }

    public static void goToConnectJobsList() {
        ConnectTask task = ConnectTask.CONNECT_MAIN;
        Intent i = new Intent(manager.parentActivity, task.getNextActivity());
        manager.parentActivity.startActivityForResult(i, task.getRequestCode());
    }

    private void continueWorkflow() {
        //Determine activity to launch for next phase
        Class<?> nextActivity = phase.getNextActivity();
        Map<String, Serializable> params = new HashMap<>();
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);

        switch (phase) {
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_REGISTER_PRIMARY);
                params.put(ConnectConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_MAIN -> {
                params.put(ConnectConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRegistrationPrimary));
                params.put(ConnectConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectConstants.CHANGE, "true");
                params.put(ConnectConstants.USERNAME, user.getUserId());
                params.put(ConnectConstants.PASSWORD, user.getPassword());
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_CHANGE_PRIMARY);
            }
            case CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                params.put(ConnectConstants.USERNAME, user.getUserId());
                params.put(ConnectConstants.PASSWORD, user.getPassword());
                params.put(ConnectConstants.METHOD, passwordOnlyWorkflow ? "true" : "false");
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_CHANGE_ALTERNATE);
            }
            case CONNECT_REGISTRATION_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectConstants.TITLE, R.string.connect_register_success_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_register_success_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_register_success_button);
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_RECOVER_PRIMARY);
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRecoveryPrimary));
                params.put(ConnectConstants.PHONE, recoveryPhone);
                params.put(ConnectConstants.CHANGE, "false");
                params.put(ConnectConstants.USERNAME, recoveryPhone);
                params.put(ConnectConstants.PASSWORD, "");
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD,
                    CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                params.put(ConnectConstants.PHONE, recoveryPhone);
                params.put(ConnectConstants.SECRET, recoverySecret);
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                //Show message screen indicating plan to use alt phone
                params.put(ConnectConstants.TITLE, R.string.connect_recovery_alt_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_recovery_alt_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_recovery_alt_button);
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                params.put(ConnectConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRecoveryAlternate));
                params.put(ConnectConstants.PHONE, null);
                params.put(ConnectConstants.CHANGE, "false");
                params.put(ConnectConstants.USERNAME, recoveryPhone);
                params.put(ConnectConstants.PASSWORD, recoverySecret);
            }
            case CONNECT_RECOVERY_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectConstants.TITLE, R.string.connect_recovery_success_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_recovery_success_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_recovery_success_button);
            }
            case CONNECT_UNLOCK_BIOMETRIC -> {
                params.put(ConnectConstants.ALLOW_PASSWORD, "true");
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                params.put(ConnectConstants.ALLOW_PASSWORD, "false");
            }
            case CONNECT_BIOMETRIC_ENROLL_FAIL -> {
                params.put(ConnectConstants.TITLE, R.string.connect_biometric_enroll_fail_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_biometric_enroll_fail_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_biometric_enroll_fail_button);
            }
        }

        if (nextActivity != null) {
            Intent i = new Intent(parentActivity, nextActivity);

            for (Map.Entry<String, Serializable> pair : params.entrySet()) {
                i.putExtra(pair.getKey(), pair.getValue());
            }

            parentActivity.startActivityForResult(i, phase.getRequestCode());
        }
    }

    public static void handleFinishedActivity(int requestCode, int resultCode, Intent intent) {
        ConnectManager manager = getInstance();
        boolean success = resultCode == Activity.RESULT_OK;
        ConnectTask nextRequestCode = ConnectTask.CONNECT_NO_ACTIVITY;
        boolean rememberPhase = false;
        boolean launchSecuritySettings = false;

        ConnectTask task = ConnectTask.fromRequestCode(requestCode);
        switch (task) {
            case CONNECT_REGISTER_OR_RECOVER_DECISION -> {
                if (success) {
                    boolean createNew = intent.getBooleanExtra(ConnectConstants.CREATE, false);

                    if (createNew) {
                        nextRequestCode = ConnectTask.CONNECT_REGISTRATION_CONSENT;
                    } else {
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                        manager.recoveryPhone = intent.getStringExtra(ConnectConstants.PHONE);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONSENT -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_PRIMARY_PHONE :
                        ConnectTask.CONNECT_NO_ACTIVITY;
            }
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_MAIN :
                        ConnectTask.CONNECT_REGISTRATION_CONSENT;
                if (success) {
                    manager.primaryPhone = intent.getStringExtra(ConnectConstants.PHONE);

                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(manager.primaryPhone);
                        ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_MAIN -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS :
                        ConnectTask.CONNECT_REGISTRATION_PRIMARY_PHONE;
                if (success) {
                    ConnectUserRecord user = ConnectUserRecord.getUserFromIntent(intent);
                    ConnectUserRecord dbUser = ConnectDatabaseHelper.getUser(manager.parentActivity);
                    if (dbUser != null) {
                        dbUser.setName(user.getName());
                        dbUser.setAlternatePhone(user.getAlternatePhone());
                        user = dbUser;
                    } else {
                        manager.connectStatus = ConnectIdStatus.Registering;
                    }
                    ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    rememberPhase = true;
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS -> {
                //Backing up here is problematic, we just created a new account...
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_MAIN;
                if (success) {
                    //If no biometric configured, proceed with password only
                    boolean configured = intent.getBooleanExtra(ConnectConstants.CONFIGURED, false);
                    manager.passwordOnlyWorkflow = intent.getBooleanExtra(ConnectConstants.PASSWORD, false);
                    boolean failedEnrollment = intent.getBooleanExtra(ConnectConstants.ENROLL_FAIL, false);
                    if (failedEnrollment) {
                        nextRequestCode = ConnectTask.CONNECT_BIOMETRIC_ENROLL_FAIL;
                    } else {
                        nextRequestCode = configured && !manager.passwordOnlyWorkflow ?
                                ConnectTask.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC :
                                ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                    }
                }
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE :
                        ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                rememberPhase = success;
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                nextRequestCode = manager.passwordOnlyWorkflow ? ConnectTask.CONNECT_REGISTRATION_MAIN :
                        ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if (success) {
                    boolean changeNumber = intent != null && intent.getBooleanExtra(ConnectConstants.CHANGE,
                            false);
                    nextRequestCode = changeNumber ? ConnectTask.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE :
                            ConnectTask.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                    rememberPhase = !changeNumber;
                }
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_SUCCESS :
                        ConnectTask.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                if (success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setAlternatePhone(intent.getStringExtra(ConnectConstants.PHONE));
                        ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                //Note that we return to primary phone verification
                // (whether they did or didn't change the phone number)
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(intent.getStringExtra(ConnectConstants.PHONE));
                        ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_ALTERNATE_PHONE :
                        ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;

                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPassword(intent.getStringExtra(ConnectConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if (success) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                    manager.recoveryPhone = intent.getStringExtra(ConnectConstants.PHONE);
                }
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    //If the user forgot their password, proceed directly to alt OTP
                    nextRequestCode = manager.forgotPassword ? ConnectTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE :
                            ConnectTask.CONNECT_RECOVERY_VERIFY_PASSWORD;

                    //Remember the secret key for use through the rest of the recovery process
                    manager.recoverySecret = intent.getStringExtra(ConnectConstants.SECRET);
                }
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD -> {
                nextRequestCode = success ? ConnectTask.CONNECT_RECOVERY_SUCCESS :
                        ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    manager.forgotPassword = intent.getBooleanExtra(ConnectConstants.FORGOT, false);
                    if (manager.forgotPassword) {
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE;
                    } else {
                        String username = intent.getStringExtra(ConnectConstants.USERNAME);
                        String name = intent.getStringExtra(ConnectConstants.NAME);
                        String password = intent.getStringExtra(ConnectConstants.PASSWORD);

                        if (username != null && name != null && password != null) {
                            //TODO: Need to get secondary phone from server
                            ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username,
                                    password, name, "");
                            user.setLastPasswordDate(new Date());
                            ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                        }
                    }
                }
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                if (success) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_ALT_PHONE;
                }
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                nextRequestCode = success ? ConnectTask.CONNECT_RECOVERY_CHANGE_PASSWORD :
                        ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;

                if (success) {
                    String username = intent.getStringExtra(ConnectConstants.USERNAME);
                    String name = intent.getStringExtra(ConnectConstants.NAME);
                    String altPhone = intent.getStringExtra(ConnectConstants.ALT_PHONE);

                    if (username != null && name != null) {
                        //NOTE: They'll choose a new password next
                        ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username,
                                "", name, altPhone);
                        ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                nextRequestCode = success ? ConnectTask.CONNECT_RECOVERY_SUCCESS :
                        ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPassword(intent.getStringExtra(ConnectConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_SUCCESS,
                    CONNECT_REGISTRATION_SUCCESS -> {
                //Finish workflow, user registered/recovered and logged in
                rememberPhase = true;
                completeSignin();
            }
            case CONNECT_UNLOCK_BIOMETRIC -> {
                if (success) {
                    completeSignin();
                } else if (intent != null && intent.getBooleanExtra(ConnectConstants.PASSWORD, false)) {
                    nextRequestCode = ConnectTask.CONNECT_UNLOCK_PASSWORD;
                } else if (intent != null && intent.getBooleanExtra(ConnectConstants.RECOVER, false)) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                }
            }
            case CONNECT_UNLOCK_PASSWORD -> {
                if (success) {
                    boolean forgot = intent.getBooleanExtra(ConnectConstants.FORGOT, false);
                    if (forgot) {
                        //Begin the recovery workflow
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                        manager.forgotPassword = true;
                    } else {
                        manager.forgotPassword = false;
                        completeSignin();
                        FirebaseAnalyticsUtil.reportCccSignIn(AnalyticsParamValue.CCC_SIGN_IN_METHOD_PASSWORD);

                        ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
                        user.setLastPasswordDate(new Date());
                        ConnectDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_BIOMETRIC_ENROLL_FAIL -> {
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if (success) {
                    //Go to settings
                    launchSecuritySettings = true;
                }
            }
            default -> {
                return;
            }
        }

        manager.phase = nextRequestCode;

        if (rememberPhase) {
            ConnectDatabaseHelper.setRegistrationPhase(manager.parentActivity, manager.phase);
        }

        manager.continueWorkflow();

        if (launchSecuritySettings) {
            //Launch after continuing workflow so previous activity is still there when user returns
            SettingsHelper.launchSecuritySettings(manager.parentActivity);
        }
    }

    public static void forgetAppCredentials(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
        if (record != null) {
            ConnectDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static void checkConnectIdLink(CommCareActivity<?> activity, boolean autoLoggedIn, String appId, String username, String password, ConnectActivityCompleteListener callback) {
        if(isLoginManagedByConnectId(appId, username)) {
            //ConnectID is configured
            if(!autoLoggedIn) {
                //See if user wishes to permanently sever the connection
                StandardAlertDialog d = new StandardAlertDialog(activity,
                        activity.getString(R.string.login_unlink_connectid_title),
                        activity.getString(R.string.login_unlink_connectid_message));

                d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    unlockConnect(activity, success -> {
                        if(success) {
                            ConnectLinkedAppRecord linkedApp = ConnectDatabaseHelper.getAppData(activity, appId, username);
                            if(linkedApp != null) {
                                linkedApp.severConnectIdLink();
                                ConnectDatabaseHelper.storeApp(activity, linkedApp);
                            }
                        }

                        callback.connectActivityComplete(success);
                    });
                });

                d.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    callback.connectActivityComplete(false);
                });

                activity.showAlertDialog(d);
                return;
            }
        } else {
            //ConnectID is NOT configured
            boolean offerToLink = true;
            boolean isSecondOffer = false;

            ConnectLinkedAppRecord linkedApp = ConnectDatabaseHelper.getAppData(activity, appId, username);
            //See if we've offered to link already
            Date firstOffer = linkedApp != null ? linkedApp.getLinkOfferDate1() : null;
            if(firstOffer != null) {
                isSecondOffer = true;
                //See if we've done the second offer
                Date secondOffer = linkedApp.getLinkOfferDate2();
                if(secondOffer != null) {
                    //They've declined twice, we won't bug them again
                    offerToLink = false;
                } else {
                    //Determine whether to do second offer
                    int daysToSecondOffer = 30;
                    long millis = (new Date()).getTime() - firstOffer.getTime();
                    long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
                    offerToLink = days >= daysToSecondOffer;
                }
            }

            if(offerToLink) {
                if(linkedApp == null) {
                    //Create the linked app record (even if just to remember that we offered
                    linkedApp = ConnectDatabaseHelper.storeApp(activity, appId, username, false, "", false);
                }

                //Update that we offered
                if(isSecondOffer) {
                    linkedApp.setLinkOfferDate2(new Date());
                } else {
                    linkedApp.setLinkOfferDate1(new Date());
                }

                final ConnectLinkedAppRecord appRecordFinal = linkedApp;
                StandardAlertDialog d = new StandardAlertDialog(activity,
                        activity.getString(R.string.login_link_connectid_title),
                        activity.getString(R.string.login_link_connectid_message));

                d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    unlockConnect(activity, success -> {
                        if(success) {
                            appRecordFinal.linkToConnectId(password);
                            ConnectDatabaseHelper.storeApp(activity, appRecordFinal);

                            //Link the HQ user by aqcuiring the SSO token for the first time
                            ConnectSsoHelper.retrieveHqSsoTokenAsync(activity, username, true, auth -> {
                                if(auth == null) {
                                    Toast.makeText(activity, "Failed to acquire SSO token", Toast.LENGTH_SHORT).show();
                                    //TODO: Re-enable when token working again
                                    //ConnectManager.forgetAppCredentials(appId, username);
                                }

                                callback.connectActivityComplete(true);
                            });
                        } else {
                            callback.connectActivityComplete(false);
                        }
                    });
                });

                d.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    //Save updated record indicating that we offered
                    ConnectDatabaseHelper.storeApp(activity, appRecordFinal);

                    callback.connectActivityComplete(false);
                });

                activity.showAlertDialog(d);
                return;
            }
        }

        callback.connectActivityComplete(false);
    }

    public static boolean isLoginManagedByConnectId(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null;
    }

    public static String getStoredPasswordForApp(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null ? auth.password : null;
    }

    @Nullable
    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId,
                userId);
        if (record != null && record.getConnectIdLinked() && record.getPassword().length() > 0) {
            return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
        }

        return null;
    }

    public static AuthInfo.TokenAuth getConnectToken() {
        if (isUnlocked()) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null && (new Date()).compareTo(user.getConnectTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(user.getConnectToken());
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isUnlocked()) {
            ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId,
                    userId);
            if (record != null && (new Date()).compareTo(record.getHqTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHqToken());
            }
        }

        return null;
    }

    public static boolean isAppInstalled(String appId) {
        boolean installed = false;
        ArrayList<ApplicationRecord> apps = AppUtils.
                getInstalledAppRecords();
        for (ApplicationRecord app : apps) {
            if (appId.equals(app.getUniqueId())) {
                installed = true;
                break;
            }
        }
        return installed;
    }

    private boolean downloading = false;
    private ResourceEngineListener downloadListener = null;
    public static void downloadAppOrResumeUpdates(String installUrl, ResourceEngineListener listener) {
        ConnectManager instance = getInstance();
        instance.downloadListener = listener;
        if(!instance.downloading) {
            instance.downloading = true;
            //Start a new download
            ResourceInstallUtils.startAppInstallAsync(false, APP_DOWNLOAD_TASK_ID, new CommCareTaskConnector<ResourceEngineListener>() {
                @Override
                public void connectTask(CommCareTask task) {

                }

                @Override
                public void startBlockingForTask(int id) {

                }

                @Override
                public void stopBlockingForTask(int id) {
                    instance.downloading = false;
                }

                @Override
                public void taskCancelled() {

                }

                @Override
                public ResourceEngineListener getReceiver() {
                    return instance.downloadListener;
                }

                @Override
                public void startTaskTransition() {

                }

                @Override
                public void stopTaskTransition(int taskId) {

                }

                @Override
                public void hideTaskCancelButton() {

                }
            }, installUrl);
        }
    }

    public static void launchApp(Context context, boolean isLearning, String appId) {
        CommCareApplication.instance().closeUserSession();

        String appType = isLearning ? "Learn" : "Deliver";
        FirebaseAnalyticsUtil.reportCccAppLaunch(appType, appId);

        getInstance().primedAppIdForAutoLogin = appId;

        CommCareLauncher.launchCommCareForAppId(context, appId);
    }

    public static boolean wasAppLaunchedFromConnect(String appId) {
        String primed = getInstance().primedAppIdForAutoLogin;
        return primed != null && primed.equals(appId);
    }

    public static String checkAutoLoginAndOverridePassword(Context context, String appId, String username,
                                                    String passwordOrPin, boolean appLaunchedFromConnect, boolean uiInAutoLogin) {
        if (isUnlocked()) {
            if(appLaunchedFromConnect) {
                //Configure some things if we haven't already
                ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(context,
                        appId, username);
                if (record == null) {
                    record = prepareConnectManagedApp(context, appId, username);
                }

                passwordOrPin = record.getPassword();
            } else if(uiInAutoLogin) {
                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                passwordOrPin = ConnectManager.getStoredPasswordForApp(seatedAppId, username);
            }
        }

        return passwordOrPin;
    }

    public static ConnectLinkedAppRecord prepareConnectManagedApp(Context context, String appId, String username) {
        //Create app password
        String password = generatePassword();

        //Store ConnectLinkedAppRecord (note worker already linked)
        ConnectLinkedAppRecord appRecord = ConnectDatabaseHelper.storeApp(context, appId, username, true, password, true);

        //Store UKR
        SecretKey newKey = CryptUtil.generateSemiRandomKey();
        String sandboxId = PropertyUtils.genUUID().replace("-", "");
        Date now = new Date();

        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.YEAR, -10); //Begin ten years ago
        Date fromDate = cal.getTime();

        cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.YEAR, 10); //Expire in ten years
        Date toDate = cal.getTime();

        UserKeyRecord ukr = new UserKeyRecord(username, UserKeyRecord.generatePwdHash(password),
                ByteEncrypter.wrapByteArrayWithString(newKey.getEncoded(), password),
                fromDate, toDate, sandboxId);

        CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).write(ukr);

        return appRecord;
    }

    public static void updatePaymentConfirmed(Context context, final ConnectJobPaymentRecord payment, boolean confirmed, ConnectActivityCompleteListener listener) {
        ConnectNetworkHelper.setPaymentConfirmed(context, payment.getPaymentId(), confirmed, new ConnectNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                payment.setConfirmed(confirmed);
                ConnectDatabaseHelper.storePayment(context, payment);

                //No need to report to user
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                listener.connectActivityComplete(false);
            }
        });
    }

    public static String generatePassword() {
        int passwordLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return password.toString();
    }
}