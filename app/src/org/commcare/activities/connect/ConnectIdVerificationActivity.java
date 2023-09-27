package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import androidx.biometric.BiometricManager;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.BiometricsHelper;
import org.commcare.views.dialogs.CustomProgressDialog;

/**
 * Shows the page for configuring biometrics (fingerprint and/or PIN)
 *
 * @author dviggiano
 */
public class ConnectIdVerificationActivity extends CommCareActivity<ConnectIdVerificationActivity>
        implements WithUIController {

    private ConnectIdVerificationActivityUiController uiController;
    private BiometricManager biometricManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_verify_title));
        biometricManager = BiometricManager.from(this);

        uiController.setupUI();

        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        if (fingerprint == BiometricsHelper.ConfigurationStatus.NotAvailable &&
                pin == BiometricsHelper.ConfigurationStatus.NotAvailable) {
            //Skip to password-only workflow
            finish(true, true, false);
        } else {
            updateState(fingerprint, pin);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        updateState(fingerprint, pin);
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
        uiController = new ConnectIdVerificationActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    public void updateState(BiometricsHelper.ConfigurationStatus fingerprintStatus,
                            BiometricsHelper.ConfigurationStatus pinStatus) {
        String titleText = getString(R.string.connect_verify_title);
        String messageText = getString(R.string.connect_verify_message);
        String fingerprintButtonText = null;
        String pinButtonText = null;
        if (fingerprintStatus == BiometricsHelper.ConfigurationStatus.Configured) {
            //Show fingerprint but not PIN
            titleText = getString(R.string.connect_verify_use_fingerprint_long);
            messageText = getString(R.string.connect_verify_fingerprint_configured);
            fingerprintButtonText = getString(R.string.connect_verify_agree);
        } else if (pinStatus == BiometricsHelper.ConfigurationStatus.Configured) {
            //Show PIN, and fingerprint if configurable
            titleText = getString(R.string.connect_verify_use_pin_long);
            messageText = getString(R.string.connect_verify_pin_configured);
            pinButtonText = getString(R.string.connect_verify_agree);
            fingerprintButtonText = fingerprintStatus == BiometricsHelper.ConfigurationStatus.NotConfigured ?
                    getString(R.string.connect_verify_configure_fingerprint) : null;
        } else {
            //Show anything configurable
            if (fingerprintStatus == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                fingerprintButtonText = getString(R.string.connect_verify_configure_fingerprint);
            }
            if (pinStatus == BiometricsHelper.ConfigurationStatus.NotConfigured) {
                pinButtonText = getString(R.string.connect_verify_configure_pin);
            }
        }

        uiController.setTitleText(titleText);
        uiController.setMessageText(messageText);

        boolean showFingerprint = fingerprintButtonText != null;
        boolean showPin = pinButtonText != null;

        uiController.updateFingerprint(fingerprintButtonText);
        uiController.updatePin(pinButtonText);

        uiController.setOrVisibility(showFingerprint && showPin);
    }

    public void handleFingerprintButton() {
        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        if (fingerprint == BiometricsHelper.ConfigurationStatus.Configured) {
            finish(true, false, false);
        } else if (!BiometricsHelper.configureFingerprint(this)) {
            finish(true, false, true);
        }
    }

    public void handlePinButton() {
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        if (pin == BiometricsHelper.ConfigurationStatus.Configured) {
            finish(true, false, false);
        } else if (!BiometricsHelper.configurePin(this)) {
            finish(true, false, true);
        }
    }

    public void handlePasswordButton() {
        finish(true, true, false);
    }

    public void finish(boolean success, boolean passwordOnly, boolean failedEnrollment) {
        Intent intent = new Intent(getIntent());

        BiometricsHelper.ConfigurationStatus fingerprint = BiometricsHelper.checkFingerprintStatus(this,
                biometricManager);
        BiometricsHelper.ConfigurationStatus pin = BiometricsHelper.checkPinStatus(this, biometricManager);
        boolean configured = fingerprint == BiometricsHelper.ConfigurationStatus.Configured ||
                pin == BiometricsHelper.ConfigurationStatus.Configured;
        intent.putExtra(ConnectIdConstants.CONFIGURED, configured);

        intent.putExtra(ConnectIdConstants.PASSWORD, passwordOnly);
        intent.putExtra(ConnectIdConstants.ENROLL_FAIL, failedEnrollment);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }
}
