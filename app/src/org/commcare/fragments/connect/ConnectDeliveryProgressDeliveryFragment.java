package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class ConnectDeliveryProgressDeliveryFragment extends Fragment {
    private ConnectJobRecord job;
    private View view;
    public ConnectDeliveryProgressDeliveryFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressDeliveryFragment newInstance(ConnectJobRecord job) {
        ConnectDeliveryProgressDeliveryFragment fragment = new ConnectDeliveryProgressDeliveryFragment();
        fragment.job = job;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_connect_progress_delivery, container, false);
        updateView();

//        //NOTE: The commented code attempts to warn the user when max visits has been exceeded
//        //But there's a bug where the buttons don't appear so the user gets stuck
//        //Just proceeding into the app instead.
//        boolean expired = job.getDaysRemaining() <= 0;
        Button button = view.findViewById(R.id.connect_progress_button);
        button.setOnClickListener(v -> {
//            String title = null;
//            String message = null;
//            if(expired) {
//                title = getString(R.string.connect_progress_expired_dialog_title);
//                message = getString(R.string.connect_progress_expired);
//            }
//            else if(job.getCompletedVisits() >= job.getMaxVisits()) {
//                title = getString(R.string.connect_progress_max_visits_dialog_title);
//                message = getString(R.string.connect_progress_visits_completed);
//            }
//
//            if(title != null) {
//                new AlertDialog.Builder(getContext())
//                        .setTitle(title)
//                        .setMessage(message)
//                        .setPositiveButton(R.string.proceed, (dialog, which) -> {
//                            launchDeliveryApp(button);
//                        })
//                        .setNegativeButton(R.string.cancel, null)
//                        .show();
//            }
//            else {
                launchDeliveryApp(button);
//            }
        });

        Button reviewButton = view.findViewById(R.id.connect_progress_review_button);
        reviewButton.setOnClickListener(v -> {
            launchLearningApp(reviewButton);
        });

        return view;
    }

    private void launchLearningApp(Button button) {
        if(ConnectManager.isAppInstalled(job.getLearnAppInfo().getAppId())) {
            ConnectManager.launchApp(getContext(), job.getLearnAppInfo().getAppId());
        }
        else {
            String title = getString(R.string.connect_downloading_learn);
            Navigation.findNavController(button).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectDownloadingFragment(title, true, true, job));
        }
    }

    private void launchDeliveryApp(Button button) {
        if(ConnectManager.isAppInstalled(job.getDeliveryAppInfo().getAppId())) {
            ConnectManager.launchApp(getContext(), job.getDeliveryAppInfo().getAppId());
        }
        else {
            String title = getString(R.string.connect_downloading_delivery);
            Navigation.findNavController(button).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectDownloadingFragment(title, false, true, job));
        }
    }

    public void updateView() {
        if(job == null || view == null) {
            return;
        }

        int completed = job.getCompletedVisits();
        int total = job.getMaxVisits();
        int percent = total > 0 ? (100 * completed / total) : 100;

        ProgressBar progress = view.findViewById(R.id.connect_progress_progress_bar);
        progress.setProgress(percent);
        progress.setMax(100);

        TextView textView = view.findViewById(R.id.connect_progress_progress_text);
        textView.setText(String.format(Locale.getDefault(), "%d%%", percent));

        textView = view.findViewById(R.id.connect_progress_status_text);
        textView.setText(getString(R.string.connect_progress_status, completed, total));

        int totalVisitCount = 0;
        int dailyVisitCount = 0;
        Date today = new Date();
        for (ConnectJobDeliveryRecord record : job.getDeliveries()) {
            totalVisitCount++;
            if(sameDay(today, record.getDate())) {
                dailyVisitCount++;
            }
        }

        boolean finished = job.isFinished();

        int warningTextId = -1;
        if(finished) {
            warningTextId = R.string.connect_progress_warning_ended;
        } else if(totalVisitCount >= job.getMaxVisits()) {
            warningTextId = R.string.connect_progress_warning_max_reached;
        } else if(dailyVisitCount >= job.getMaxDailyVisits()) {
            warningTextId = R.string.connect_progress_warning_daily_max_reached;
        }

        textView = view.findViewById(R.id.connect_progress_delivery_warning_text);
        textView.setVisibility(warningTextId >= 0 ? View.VISIBLE : View.GONE);
        if(warningTextId >= 0) {
            textView.setText(warningTextId);
        }

        textView = view.findViewById(R.id.connect_progress_complete_by_text);
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        int textId = finished ? R.string.connect_progress_ended : R.string.connect_progress_complete_by;
        textView.setText(getString(textId, df.format(job.getProjectEndDate())));

        textView = view.findViewById(R.id.connect_progress_warning_learn_text);
        textView.setOnClickListener(v -> {
                StandardAlertDialog dialog = new StandardAlertDialog(
                        getContext(),
                        getString(R.string.connect_progress_warning),
                        getString(R.string.connect_progress_warning_full));
                dialog.setPositiveButton(Localization.get("dialog.ok"), (dialog1, which) -> {
                    dialog1.dismiss();
                });
            ((CommCareActivity<?>)getActivity()).showAlertDialog(dialog);
        });
    }

    private static boolean sameDay(Date date1, Date date2) {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return date1.toInstant().truncatedTo(ChronoUnit.DAYS).equals(date2.toInstant());
        }

        Calendar calendar1 = new GregorianCalendar();
        calendar1.setTime(date1);
        int year1 = calendar1.get(Calendar.YEAR);
        int day1 = calendar1.get(Calendar.DAY_OF_YEAR);

        Calendar calendar2 = new GregorianCalendar();
        calendar2.setTime(date2);
        int year2 = calendar2.get(Calendar.YEAR);
        int day2 = calendar2.get(Calendar.DAY_OF_YEAR);

        return year1 == year2 && day1 == day2;
    }
}
