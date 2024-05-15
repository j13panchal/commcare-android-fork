package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import org.commcare.activities.connect.ConnectDatabaseHelper;
import org.commcare.activities.connect.ConnectManager;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Fragment for showing delivery progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectDeliveryProgressFragment extends Fragment {
    private ConnectDeliveryProgressFragment.ViewStateAdapter viewStateAdapter;
    private TextView updateText;

    private ConstraintLayout paymentAlertTile;
    private TextView paymentAlertText;
    private ConnectJobPaymentRecord paymentToConfirm = null;

    public ConnectDeliveryProgressFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressFragment newInstance() {
        return new ConnectDeliveryProgressFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_delivery_progress, container, false);

        updateText = view.findViewById(R.id.connect_delivery_last_update);
        updateUpdatedDate(job.getLastDeliveryUpdate());

        ImageView refreshButton = view.findViewById(R.id.connect_delivery_refresh);
        refreshButton.setOnClickListener(v -> refreshData());

        paymentAlertTile = view.findViewById(R.id.connect_delivery_progress_alert_tile);
        paymentAlertText = view.findViewById(R.id.connect_payment_confirm_label);
        TextView paymentAlertNoButton = view.findViewById(R.id.connect_payment_confirm_no_button);
        paymentAlertNoButton.setOnClickListener(v -> {
            updatePaymentConfirmationTile(getContext(), true);
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(false);
        });

        TextView paymentAlertYesButton = view.findViewById(R.id.connect_payment_confirm_yes_button);
        paymentAlertYesButton.setOnClickListener(v -> {
            final ConnectJobPaymentRecord payment = paymentToConfirm;
            //Dismiss the tile
            updatePaymentConfirmationTile(getContext(), true);

            if(payment != null) {
                FirebaseAnalyticsUtil.reportCccPaymentConfirmationInteraction(true);

                ConnectManager.updatePaymentConfirmed(getContext(), payment, true, success -> {
                    //Nothing to do
                });
            }
        });

        final ViewPager2 pager = view.findViewById(R.id.connect_delivery_progress_view_pager);
        viewStateAdapter = new ConnectDeliveryProgressFragment.ViewStateAdapter(getChildFragmentManager(), getLifecycle());
        pager.setAdapter(viewStateAdapter);

        final TabLayout tabLayout = view.findViewById(R.id.connect_delivery_progress_tabs);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress_delivery));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_progress_delivery_verification));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                TabLayout.Tab tab = tabLayout.getTabAt(position);
                tabLayout.selectTab(tab);

                FirebaseAnalyticsUtil.reportConnectTabChange(tab.getText().toString());
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                pager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        updatePaymentConfirmationTile(getContext(), false);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(ConnectManager.isUnlocked()) {
            refreshData();
        }
    }

    public void refreshData() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectManager.updateDeliveryProgress(getContext(), job, success -> {
            if(success) {
                try {
                    updateUpdatedDate(new Date());
                    updatePaymentConfirmationTile(getContext(), false);
                    viewStateAdapter.refresh();
                }
                catch(Exception e) {
                    //Ignore exception, happens if we leave the page before API call finishes
                }
            }
        });
    }

    private void updatePaymentConfirmationTile(Context context, boolean forceHide) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        paymentToConfirm = null;
        if(!forceHide) {
            //Look for at least one payment that needs to be confirmed
            for (ConnectJobPaymentRecord payment : job.getPayments()) {
                if(payment.allowConfirm()) {
                    paymentToConfirm = payment;
                    break;
                }
            }
        }

        //NOTE: Checking for network connectivity here
        boolean show = paymentToConfirm != null;
        if(show) {
            show = ConnectNetworkHelper.isOnline(context);
            FirebaseAnalyticsUtil.reportCccPaymentConfirmationOnlineCheck(show);
        }

        paymentAlertTile.setVisibility(show ? View.VISIBLE : View.GONE);
        if(show) {
            String date = ConnectManager.formatDate(paymentToConfirm.getDate());
            paymentAlertText.setText(getString(R.string.connect_payment_confirm_text, paymentToConfirm.getAmount(), job.getCurrency(), date));

            FirebaseAnalyticsUtil.reportCccPaymentConfirmationDisplayed();
        }
    }

    private void updateUpdatedDate(Date lastUpdate) {
        updateText.setText(getString(R.string.connect_last_update, ConnectManager.formatDateTime(lastUpdate)));
    }

    private static class ViewStateAdapter extends FragmentStateAdapter {
        private static ConnectDeliveryProgressDeliveryFragment deliveryFragment = null;
        private static ConnectResultsSummaryListFragment verificationFragment = null;
        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                deliveryFragment = ConnectDeliveryProgressDeliveryFragment.newInstance();
                return deliveryFragment;
            }

            verificationFragment = ConnectResultsSummaryListFragment.newInstance();
            return verificationFragment;
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        public void refresh() {
            if(deliveryFragment != null) {
                deliveryFragment.updateView();
            }

            if(verificationFragment != null) {
                verificationFragment.updateView();
            }
        }
    }
}
