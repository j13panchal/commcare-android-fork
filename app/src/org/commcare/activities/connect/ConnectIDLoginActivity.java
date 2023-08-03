package org.commcare.activities.connect;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.dalvik.R;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.BiometricsHelper;
import org.commcare.activities.CommCareActivity;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;

public class ConnectIDLoginActivity extends CommCareActivity<ConnectIDLoginActivity>
implements WithUIController {
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo fingerprintPromptInfo;
    private BiometricPrompt.PromptInfo pinPromptInfo;
    private boolean attemptingFingerprint = false;
    private boolean allowPassword = false;

    private ConnectIDLoginActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_unlock_title));

        uiController.setupUI();

        allowPassword = getIntent().getStringExtra(ConnectIDConstants.ALLOW_PASSWORD).equals("true");

        biometricPrompt = preparePrompt();

        BiometricManager biometricManager = BiometricManager.from(this);
        configureFingerprintUnlock(biometricManager);
        configurePinUnlock(biometricManager);

        if(fingerprintPromptInfo != null) {
            //Automatically try fingerprint first
            performFingerprintUnlock();
        }
        else if(pinPromptInfo == null) {
            //Automatically try password, it's the only option
            performPasswordUnlock();
        }
        else {
            if(allowPassword) {
                //Show options for PIN or password
                uiController.showAdditionalOptions();
            }
            else {
                //PIN is the only option
                performPinUnlock();
            }
        }
    }

    @Override
    protected  void onResume() {
        super.onResume();

        uiController.refreshView();
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIDLoginActivityUIController(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        ConnectIDManager.handleFinishedActivity(requestCode, resultCode, intent);

        super.onActivityResult(requestCode, resultCode, intent);
    }
    public void startAccountRecoveryWorkflow() {
        finish(false, false, true);
    }

    public void performFingerprintUnlock() {
        attemptingFingerprint = true;
        biometricPrompt.authenticate(fingerprintPromptInfo);
    }

    public void performPasswordUnlock() {
        finish(false, true, false);
    }
    public void performPinUnlock() {
        biometricPrompt.authenticate(pinPromptInfo);
    }

    private BiometricPrompt preparePrompt() {
        return new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if(attemptingFingerprint) {
                    attemptingFingerprint = false;
                    if(pinPromptInfo == null) {
                        //Automatically try password, it's the only option
                        performPasswordUnlock();
                    }
                    else {
                        //Show options for PIN or password
                        uiController.showAdditionalOptions();
                    }
                }
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                logSuccess();
                finish(true, false, false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                //TODO: Change path before Android takes action
                Toast.makeText(getApplicationContext(), "Authentication failed",
                                Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void logSuccess() {
        String method = attemptingFingerprint ? AnalyticsParamValue.CCC_SIGN_IN_METHOD_FINGERPRINT
                : AnalyticsParamValue.CCC_SIGN_IN_METHOD_PIN;
        FirebaseAnalyticsUtil.reportCccSignIn(method);
    }

    private void finish(boolean success, boolean password, boolean recover) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectIDConstants.PASSWORD, password);
        intent.putExtra(ConnectIDConstants.RECOVER, recover);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    private void configureFingerprintUnlock(BiometricManager biometricManager) {
        fingerprintPromptInfo = null;

        if(BiometricsHelper.isFingerprintConfigured(biometricManager)) {
            fingerprintPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.connect_unlock_fingerprint_title))
                    .setSubtitle(getString(R.string.connect_unlock_fingerprint_message))
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setNegativeButtonText(getString(R.string.connect_unlock_other_options))
                    .build();
        }
    }

    private void configurePinUnlock(BiometricManager biometricManager) {
        pinPromptInfo = null;

        if(BiometricsHelper.isPinConfigured(biometricManager)) {
            pinPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.connect_unlock_pin_title))
                    .setSubtitle(getString(R.string.connect_unlock_pin_message))
                    .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                    .build();
        }
    }
}
