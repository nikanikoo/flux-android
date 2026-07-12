package org.nikanikoo.flux.ui.fragments.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.security.AccountManager;
import org.nikanikoo.flux.utils.Logger;

public class BalanceFragment extends Fragment {

    private static final String TAG = "BalanceFragment";

    private TextView tvBalanceAmount;
    private View btnTopUp;
    private View btnVoucher;
    private View btnTransfer;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_balance, container, false);

        initViews(view);
        loadBalance();
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        tvBalanceAmount = view.findViewById(R.id.tv_balance_amount);
        btnTopUp = view.findViewById(R.id.btn_action_topup);
        btnVoucher = view.findViewById(R.id.btn_action_voucher);
        btnTransfer = view.findViewById(R.id.btn_action_transfer);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::loadBalance);
        }
    }

    private void loadBalance() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("balance_cache", android.content.Context.MODE_PRIVATE);
        int cachedBalance = prefs.getInt("cached_balance", 0);
        tvBalanceAmount.setText(String.valueOf(cachedBalance));

        OpenVKApi.getInstance(requireContext()).callMethod("account.getBalance", null, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (isAdded() && getActivity() != null) {
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    try {
                        JSONObject responseObj = response.getJSONObject("response");
                        int votes = responseObj.optInt("votes", 0);
                        
                        // Cache balance
                        prefs.edit().putInt("cached_balance", votes).apply();
                        
                        tvBalanceAmount.setText(String.valueOf(votes));
                    } catch (Exception e) {
                        Logger.e(TAG, "Failed to parse balance response", e);
                        Toast.makeText(requireContext(), getString(R.string.request_server_error), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded() && getActivity() != null) {
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    Logger.e(TAG, "Failed to load balance: " + error);
                    Toast.makeText(requireContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupClickListeners() {
        View.OnClickListener webRedirectListener = v -> {
            String baseUrl = OpenVKApi.getInstance(requireContext()).getBaseUrl();
            String url = baseUrl + "/settings?act=finance";
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), getString(R.string.link_error), Toast.LENGTH_SHORT).show();
            }
        };

        btnTopUp.setOnClickListener(webRedirectListener);
        btnVoucher.setOnClickListener(webRedirectListener);

        btnTransfer.setOnClickListener(v -> {
            org.nikanikoo.flux.ui.dialogs.TransferDialogs.showSelectUserDialog(requireContext(), this::loadBalance);
        });
    }
}
