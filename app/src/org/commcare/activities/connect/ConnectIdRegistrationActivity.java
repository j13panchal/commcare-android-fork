package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.views.dialogs.CustomProgressDialog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Random;

/**
 * Shows the page that prompts the user to enter their name
 *
 * @author dviggiano
 */
public class ConnectIdRegistrationActivity extends CommCareActivity<ConnectIdRegistrationActivity>
        implements WithUIController {
    private ConnectIdRegistrationActivityUiController uiController;

    private ConnectUserRecord user;
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_register_title));

        phone = getIntent().getStringExtra(ConnectConstants.PHONE);

        uiController.setupUI();

        ConnectUserRecord user = ConnectManager.getUser(this);
        if (user != null) {
            uiController.setNameText(user.getName());
        }

        updateStatus();
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
        uiController = new ConnectIdRegistrationActivityUiController(this);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    private String generateUserId() {
        int idLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder userId = new StringBuilder();
        for (int i = 0; i < idLength; i++) {
            userId.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return userId.toString();
    }

    public void updateStatus() {
        String error = uiController.getNameText().length() == 0 ?
                getString(R.string.connect_register_error_name) : null;

        uiController.setErrorText(error);
        uiController.setButtonEnabled(error == null);
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        user.putUserInIntent(intent);
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void continuePressed() {
        user = ConnectManager.getUser(this);
        if (user == null) {
            createAccount();
        } else {
            updateAccount();
        }
    }

    public void createAccount() {
        uiController.setErrorText(null);

        ConnectUserRecord tempUser = new ConnectUserRecord(phone, generateUserId(), ConnectManager.generatePassword(),
                uiController.getNameText(), "");

        boolean isBusy = !ApiConnectId.registerUser(this, tempUser.getUserId(), tempUser.getPassword(),
                tempUser.getName(), phone, new ConnectNetworkHelper.INetworkResultHandler() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        user = tempUser;
                        finish(true);
                    }

                    @Override
                    public void processFailure(int responseCode, IOException e) {
                        uiController.setErrorText(String.format(Locale.getDefault(), "Registration error: %d",
                                responseCode));
                    }

                    @Override
                    public void processNetworkFailure() {
                        uiController.setErrorText(getString(R.string.recovery_network_unavailable));
                    }

                    @Override
                    public void processOldApiError() {
                        uiController.setErrorText(getString(R.string.recovery_network_outdated));
                    }
                });

        if (isBusy) {
            Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void updateAccount() {
        uiController.setErrorText(null);

        String newName = uiController.getNameText();

        if (newName.equals(user.getName())) {
            finish(true);
        } else {
            boolean isBusy = !ApiConnectId.updateUserProfile(this, user.getUserId(),
                    user.getPassword(), newName, null, new ConnectNetworkHelper.INetworkResultHandler() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            user.setName(newName);
                            finish(true);
                        }

                        @Override
                        public void processFailure(int responseCode, IOException e) {
                            uiController.setErrorText(String.format(Locale.getDefault(), "Error: %d",
                                    responseCode));
                        }

                        @Override
                        public void processNetworkFailure() {
                            uiController.setErrorText(getString(R.string.recovery_network_unavailable));
                        }

                        @Override
                        public void processOldApiError() {
                            uiController.setErrorText(getString(R.string.recovery_network_outdated));
                        }
                    });

            if (isBusy) {
                Toast.makeText(this, R.string.busy_message, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
