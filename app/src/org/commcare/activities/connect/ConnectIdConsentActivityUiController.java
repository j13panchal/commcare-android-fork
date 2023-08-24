package org.commcare.activities.connect;

import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * UI Controller, handles UI interaction with the owning Activity
 *
 * @author dviggiano
 */
@ManagedUi(R.layout.screen_connect_consent)
public class ConnectIdConsentActivityUiController implements CommCareActivityUIController {

    @UiElement(value = R.id.connect_consent_message_1)
    private TextView messageText;
    @UiElement(value = R.id.connect_consent_check)
    private CheckBox checkbox;
    @UiElement(value = R.id.connect_consent_button)
    private Button button;

    protected final ConnectIdConsentActivity activity;

    public ConnectIdConsentActivityUiController(ConnectIdConsentActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        //This makes the links work in the consent message
        messageText.setMovementMethod(LinkMovementMethod.getInstance());

        checkbox.setOnClickListener(v -> updateState());

        button.setOnClickListener(v -> activity.handleButtonPress());

        updateState();
    }

    @Override
    public void refreshView() {
        updateState();
    }

    public void updateState() {
        button.setEnabled(checkbox.isChecked());
    }
}
