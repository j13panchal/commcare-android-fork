package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.connect.ConnectIdManager;
import org.commcare.activities.connect.ConnectIdNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJob;
import org.commcare.android.database.connect.models.ConnectJobLearningModule;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.dalvik.R;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.fragment.app.Fragment;

/**
 * Fragment for showing learning progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectLearningProgressFragment extends Fragment {
    public ConnectLearningProgressFragment() {
        // Required empty public constructor
    }

    public static ConnectLearningProgressFragment newInstance() {
        return new ConnectLearningProgressFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectJob job = ConnectLearningProgressFragmentArgs.fromBundle(getArguments()).getJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_learning_progress, container, false);

        //NOTE: Leaving old logic here in case we go back to array
        int completed = job.getCompletedLearningModules();// 0;
//        for (ConnectJobLearningModule module: job.getLearningModules()) {
//            if(module.getCompletedDate() != null) {
//                completed++;
//            }
//        }

        int numModules = job.getNumLearningModules();// job.getLearningModules().length;
        int percent = numModules > 0 ? (100 * completed / numModules) : 100;
        boolean learningFinished = percent >= 100;

        String status;
        String buttonText;
        if (learningFinished) {
            status = getString(R.string.connect_learn_finished);
            buttonText = getString(R.string.connect_learn_start_claim);
        } else if(percent > 0) {
            status = getString(R.string.connect_learn_status, completed, numModules);
            buttonText = getString(R.string.connect_learn_continue);
        } else {
            status = getString(R.string.connect_learn_not_started);
            buttonText = getString(R.string.connect_learn_start);
        }

        TextView progressText = view.findViewById(R.id.connect_learning_progress_text);
        progressText.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        ProgressBar progressBar = view.findViewById(R.id.connect_learning_progress_bar);
        progressBar.setVisibility(learningFinished ? View.GONE : View.VISIBLE);
        if(!learningFinished) {
            progressBar.setProgress(percent);
            progressBar.setMax(100);

            progressText.setText(String.format(Locale.getDefault(), "%d%%", percent));
        }

        LinearLayout certContainer = view.findViewById(R.id.connect_learning_certificate_container);
        certContainer.setVisibility(learningFinished ? View.VISIBLE : View.GONE);

        TextView textView = view.findViewById(R.id.connect_learn_progress_title);
        textView.setText(getString(learningFinished ? R.string.connect_learn_complete_title :
                R.string.connect_learn_progress_title));

        textView = view.findViewById(R.id.connect_learning_claim_label);
        textView.setVisibility(learningFinished ? View.VISIBLE : View.GONE);

        textView = view.findViewById(R.id.connect_learning_status_text);
        textView.setText(status);

        TextView completeByText = view.findViewById(R.id.connect_learning_complete_by_text);
        completeByText.setVisibility(learningFinished ? View.GONE : View.VISIBLE);

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        if(learningFinished) {
            textView = view.findViewById(R.id.connect_learn_cert_subject);
            textView.setText(job.getTitle());

            textView = view.findViewById(R.id.connect_learn_cert_person);
            textView.setText(ConnectIdManager.getUser(getContext()).getName());

            //TODO: get from server somehow
            Date latestDate = new Date();//null;
//            for (ConnectJobLearningModule module : job.getLearningModules()) {
//                if(latestDate == null || latestDate.before(module.getCompletedDate())) {
//                    latestDate = module.getCompletedDate();
//                }
//            }

            textView = view.findViewById(R.id.connect_learn_cert_date);
            textView.setText(getString(R.string.connect_learn_completed, df.format(latestDate)));
        } else {
            completeByText.setText(getString(R.string.connect_learn_complete_by, df.format(job.getProjectEndDate())));
        }

        Button button = view.findViewById(R.id.connect_learning_button);
        button.setText(buttonText);
        button.setOnClickListener(v -> {
            //First, need to tell Connect we're starting learning so it can create a user on HQ
            ConnectIdNetworkHelper.startLearnApp(getContext(), job.getJobId(), new ConnectIdNetworkHelper.INetworkResultHandler() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    CommCareLauncher.launchCommCareForAppId(getContext(), job.getLearnAppInfo().getAppId());
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    Toast.makeText(getContext(), "Connect: error starting learning", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void processNetworkFailure() {
                    Toast.makeText(getContext(), getString(R.string.recovery_network_unavailable),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });

        return view;
    }
}
