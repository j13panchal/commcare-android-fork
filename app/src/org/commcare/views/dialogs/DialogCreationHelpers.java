package org.commcare.views.dialogs;

import android.support.v7.app.AppCompatActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.commcare.AppUtils;
import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.utils.MarkupUtil;
import org.javarosa.core.services.locale.Localization;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DialogCreationHelpers {

    public static CommCareAlertDialog buildAboutCommCareDialog(AppCompatActivity activity) {

        LayoutInflater li = LayoutInflater.from(activity);
        View view = li.inflate(R.layout.scrolling_info_dialog, null);
        TextView titleView = (TextView) view.findViewById(R.id.dialog_title_text);
        titleView.setText(activity.getString(R.string.about_cc));
        Spannable markdownText = buildAboutMessage(activity);
        TextView aboutText = (TextView)view.findViewById(R.id.dialog_text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            aboutText.setText(markdownText);
        } else {
            aboutText.setText(markdownText.toString());
        }

        CustomViewAlertDialog dialog = new CustomViewAlertDialog(activity, view);
        dialog.setPositiveButton(Localization.get("dialog.ok"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        return dialog;
    }

    private static Spannable buildAboutMessage(Context context) {
        String commcareVersion = AppUtils.getCurrentVersionString();
        String customAcknowledgment = Localization.getWithDefault("custom.acknowledgement", "");
        String message = context.getString(R.string.about_dialog, commcareVersion, customAcknowledgment);
        return MarkupUtil.returnMarkdown(context, message);
    }

    /**
     * Build dialog that tells user why they should authorize a given
     * permission. Pressing positive button launches the system's permission
     * request dialgo
     *
     * @param permRequester interface for launching system permission request
     *                      dialog
     */
    public static CommCareAlertDialog buildPermissionRequestDialog(AppCompatActivity activity,
                                                           final RuntimePermissionRequester permRequester,
                                                           final int requestCode,
                                                           String title,
                                                           String body) {

        View view = LayoutInflater.from(activity).inflate(R.layout.scrolling_info_dialog, null);
        TextView bodyText = (TextView)view.findViewById(R.id.dialog_text);
        bodyText.setText(body);
        TextView titleText = (TextView) view.findViewById(R.id.dialog_title_text);
        titleText.setText(title);

        CustomViewAlertDialog dialog = new CustomViewAlertDialog(activity, view);
        dialog.setPositiveButton(Localization.get("dialog.ok"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                permRequester.requestNeededPermissions(requestCode);
                dialog.dismiss();
            }
        });

        return dialog;
    }
}
