package org.commcare.activities.connect;

/**
 * Enum representing the tasks (and associated activities) involved in various ConnectId workflows
 *
 * @author dviggiano
 */
public enum ConnectTask {
    CONNECT_NO_ACTIVITY(ConnectConstants.ConnectIdTaskIdOffset,
            null),
    CONNECT_REGISTER_OR_RECOVER_DECISION(ConnectConstants.ConnectIdTaskIdOffset + 1,
            ConnectIdRecoveryDecisionActivity.class),
    CONNECT_REGISTRATION_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 2,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_CONSENT(ConnectConstants.ConnectIdTaskIdOffset + 3,
            ConnectIdConsentActivity.class),
    CONNECT_REGISTRATION_MAIN(ConnectConstants.ConnectIdTaskIdOffset + 4,
            ConnectIdRegistrationActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS(ConnectConstants.ConnectIdTaskIdOffset + 5,
            ConnectIdVerificationActivity.class),
    CONNECT_REGISTRATION_UNLOCK_BIOMETRIC(ConnectConstants.ConnectIdTaskIdOffset + 6,
            ConnectIdLoginActivity.class),
    CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 7,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 8,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_PASSWORD(ConnectConstants.ConnectIdTaskIdOffset + 9,
            ConnectIdPasswordActivity.class),
    CONNECT_REGISTRATION_ALTERNATE_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 10,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_SUCCESS(ConnectConstants.ConnectIdTaskIdOffset + 11,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 12,
            ConnectIdPhoneActivity.class),
    CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 13,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_VERIFY_PASSWORD(ConnectConstants.ConnectIdTaskIdOffset + 14,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_RECOVERY_ALT_PHONE_MESSAGE(ConnectConstants.ConnectIdTaskIdOffset + 15,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_VERIFY_ALT_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 16,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_CHANGE_PASSWORD(ConnectConstants.ConnectIdTaskIdOffset + 17,
            ConnectIdPasswordActivity.class),
    CONNECT_RECOVERY_SUCCESS(ConnectConstants.ConnectIdTaskIdOffset + 18,
            ConnectIdMessageActivity.class),
    CONNECT_UNLOCK_BIOMETRIC(ConnectConstants.ConnectIdTaskIdOffset + 19,
            ConnectIdLoginActivity.class),
    CONNECT_UNLOCK_PASSWORD(ConnectConstants.ConnectIdTaskIdOffset + 20,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_UNLOCK_PIN(ConnectConstants.ConnectIdTaskIdOffset + 21,
            null),
    CONNECT_BIOMETRIC_ENROLL_FAIL(ConnectConstants.ConnectIdTaskIdOffset + 22,
            ConnectIdMessageActivity.class),
    CONNECT_MAIN(ConnectConstants.ConnectIdTaskIdOffset + 23,
                 ConnectActivity.class);

    private final int requestCode;
    private final Class<?> nextActivity;

    ConnectTask(int requestCode, Class<?> nextActivity) {
        this.requestCode = requestCode;
        this.nextActivity = nextActivity;
    }

    public int getRequestCode() {
        return requestCode;
    }

    public Class<?> getNextActivity() {
        return nextActivity;
    }

    public static ConnectTask fromRequestCode(int code) {
        for (ConnectTask task : ConnectTask.values()) {
            if (task.requestCode == code) {
                return task;
            }
        }

        return ConnectTask.CONNECT_NO_ACTIVITY;
    }
}
