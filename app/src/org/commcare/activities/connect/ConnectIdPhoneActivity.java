package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.PhoneNumberHelper;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Shows the page that prompts the user to enter a phone number (during registration or recovery)
 *
 * @author dviggiano
 */
public class ConnectIdPhoneActivity extends CommCareActivity<ConnectIdPhoneActivity>
        implements WithUIController {

    private String method;
    private String existingPhone;
    private ConnectIdPhoneActivityUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_phone_page_title));

        uiController.setupUI();

        method = getIntent().getStringExtra(ConnectConstants.METHOD);
        //Special case for initial reg. screen. Remembering phone number before account has been created
        existingPhone = getIntent().getStringExtra(ConnectConstants.PHONE);

        ConnectUserRecord user = ConnectManager.getUser(this);
        String title = getString(R.string.connect_phone_title_primary);
        String message = getString(R.string.connect_phone_message_primary);
        String existing;
        if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
            title = getString(R.string.connect_phone_title_alternate);
            message = getString(R.string.connect_phone_message_alternate);

            existing = user != null ? user.getAlternatePhone() : null;
        } else {
            existing = user != null ? user.getPrimaryPhone() : existingPhone;
        }

        uiController.setTitle(title);
        uiController.setMessage(message);

        int code = PhoneNumberHelper.getCountryCode(this);
        if (existing != null && existing.length() > 0) {
            code = PhoneNumberHelper.getCountryCode(this, existing);
        }

        String codeText = "";
        if (code > 0) {
            codeText = String.format(Locale.getDefault(), "%d", code);
        }

        if (existing != null && existing.startsWith("+" + codeText)) {
            existing = existing.substring(codeText.length() + 1);
        }

        uiController.setPhoneNumber(existing);
        uiController.setCountryCode(codeText);
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPhoneNumber();

        uiController.requestInputFocus();
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
        uiController = new ConnectIdPhoneActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    public void finish(boolean success, String phone) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectConstants.PHONE, phone);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void handleButtonPress() {
        String phone = PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(),
                uiController.getPhoneNumber());
        ConnectUserRecord user = ConnectManager.getUser(this);
        String existing = user != null ? user.getPrimaryPhone() : existingPhone;
        if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
            existing = user != null ? user.getAlternatePhone() : null;
        }
        if (user != null && existing != null && !existing.equals(phone)) {
            IApiCallback callback = new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    finish(true, phone);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    Toast.makeText(getApplicationContext(), getString(R.string.connect_phone_change_error),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void processNetworkFailure() {
                    ConnectNetworkHelper.showNetworkError(getApplicationContext());
                }

                @Override
                public void processOldApiError() {
                    ConnectNetworkHelper.showOutdatedApiError(getApplicationContext());
                }
            };

            //Update the phone number with the server
            boolean isBusy;
            if (method.equals(ConnectConstants.METHOD_CHANGE_ALTERNATE)) {
                isBusy = !ApiConnectId.updateUserProfile(this, user.getUserId(), user.getPassword(),
                        null, phone, callback);
            } else {
                isBusy = !ApiConnectId.changePhone(this, user.getUserId(), user.getPassword(),
                        existing, phone, callback);
            }

            if (isBusy) {
                Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
            }
        } else {
            finish(true, phone);
        }
    }

    public void checkPhoneNumber() {
        String phone = PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(),
                uiController.getPhoneNumber());

        boolean valid = PhoneNumberHelper.isValidPhoneNumber(this, phone);
        ConnectUserRecord user = ConnectManager.getUser(this);

        if (valid) {
            String existingPrimary = user != null ? user.getPrimaryPhone() : existingPhone;
            String existingAlternate = user != null ? user.getAlternatePhone() : null;
            switch (method) {
                case ConnectConstants.METHOD_REGISTER_PRIMARY,
                        ConnectConstants.METHOD_CHANGE_PRIMARY -> {
                    if (existingPrimary != null && existingPrimary.equals(phone)) {
                        uiController.setAvailabilityText("");
                        uiController.setOkButtonEnabled(true);
                    } else if (existingAlternate != null && existingAlternate.equals(phone)) {
                        uiController.setAvailabilityText(getString(R.string.connect_phone_not_alt));
                        uiController.setOkButtonEnabled(false);
                    } else {
                        //Make sure the number isn't already in use
                        phone = phone.replaceAll("\\+", "%2b");
                        uiController.setAvailabilityText(getString(R.string.connect_phone_checking));
                        uiController.setOkButtonEnabled(false);

                        boolean isBusy = !ApiConnectId.checkPhoneAvailable(this, phone,
                                new IApiCallback() {
                                    @Override
                                    public void processSuccess(int responseCode, InputStream responseData) {
                                        uiController.setAvailabilityText(
                                                getString(R.string.connect_phone_available));
                                        uiController.setOkButtonEnabled(true);
                                    }

                                    @Override
                                    public void processFailure(int responseCode, IOException e) {
                                        String text = getString(R.string.connect_phone_unavailable);
                                        uiController.setOkButtonEnabled(false);

                                        if (e != null) {
                                            Logger.exception("Checking phone number", e);
                                        }

                                        uiController.setAvailabilityText(text);
                                    }

                                    @Override
                                    public void processNetworkFailure() {
                                        uiController.setAvailabilityText(getString(
                                                R.string.recovery_network_unavailable));
                                    }

                                    @Override
                                    public void processOldApiError() {
                                        uiController.setAvailabilityText(getString(
                                                R.string.recovery_network_outdated));
                                    }
                                });

                        if (isBusy) {
                            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                case ConnectConstants.METHOD_CHANGE_ALTERNATE -> {
                    if (existingPrimary != null && existingPrimary.equals(phone)) {
                        uiController.setAvailabilityText(getString(R.string.connect_phone_not_primary));
                        uiController.setOkButtonEnabled(false);
                    } else {
                        uiController.setAvailabilityText("");
                        uiController.setOkButtonEnabled(true);
                    }
                }
                case ConnectConstants.METHOD_RECOVER_PRIMARY -> {
                    uiController.setAvailabilityText("");
                    uiController.setOkButtonEnabled(true);
                }
            }
        } else {
            uiController.setAvailabilityText(getString(R.string.connect_phone_invalid));
            uiController.setOkButtonEnabled(false);
        }
    }
}
