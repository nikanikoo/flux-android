package org.nikanikoo.flux.ui.fragments.settings;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.services.LongPollService;
import org.nikanikoo.flux.services.MessageNotificationManager;
import org.nikanikoo.flux.ui.activities.AboutAppActivity;
import org.nikanikoo.flux.ui.activities.AboutInstanceActivity;
import org.nikanikoo.flux.ui.activities.AppearanceSettingsActivity;
import org.nikanikoo.flux.ui.activities.DataSettingsActivity;
import org.nikanikoo.flux.ui.activities.LanguageSettingsActivity;
import org.nikanikoo.flux.ui.activities.LoginActivity;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.activities.NotificationsSettingsActivity;
import org.nikanikoo.flux.ui.activities.AccountManagerActivity;
import org.nikanikoo.flux.security.AccountManager;
import org.nikanikoo.flux.utils.BuildInfo;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;

public class SettingsFragment extends Fragment {

    private ThemeManager themeManager;
    private LocaleManager localeManager;

    // Settings value views
    private TextView appVersionValue;
    private TextView instanceUrlValue;
    private TextView languageValue;
    private TextView balanceValue;

    // Clickable items
    private View settingsAboutApp;
    private View settingsAboutInstance;
    private View cardLanguage;
    private View cardBalance;

    private MaterialButton btnLogout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        themeManager = ThemeManager.getInstance(requireContext());
        localeManager = LocaleManager.getInstance(requireContext());

        initViews(view);
        setupThemeSettings(view);
        setupNotificationSettings(view);
        setupDataSettings(view);
        setupLanguageSettings(view);
        setupAccountsSettings(view);
        setupBalanceSettings(view);
        setupAboutSettings();
        setupLogout();

        return view;
    }

    private void initViews(View view) {
        // About
        appVersionValue = view.findViewById(R.id.app_version_value);
        instanceUrlValue = view.findViewById(R.id.instance_url_value);
        languageValue = view.findViewById(R.id.language_value);
        balanceValue = view.findViewById(R.id.balance_value);

        // Clickable items
        settingsAboutApp = view.findViewById(R.id.settings_about_app);
        settingsAboutInstance = view.findViewById(R.id.settings_about_instance);
        cardLanguage = view.findViewById(R.id.card_language);
        cardBalance = view.findViewById(R.id.card_balance);

        btnLogout = view.findViewById(R.id.btn_logout);
    }
    
    private void setupAccountsSettings(View view) {
        View accountsCard = view.findViewById(R.id.card_accounts);
        if (accountsCard != null) {
            accountsCard.setOnClickListener(v -> navigateToAccountManager());
        }
    }
    
    private void navigateToAccountManager() {
        Intent intent = new Intent(requireContext(), AccountManagerActivity.class);
        startActivity(intent);
    }
    
    private void setupThemeSettings(View view) {
        // Navigate to appearance settings
        View appearanceCard = view.findViewById(R.id.card_appearance);
        if (appearanceCard != null) {
            appearanceCard.setOnClickListener(v -> navigateToAppearanceSettings());
        }
    }
    
    private void navigateToAppearanceSettings() {
        Intent intent = new Intent(requireContext(), AppearanceSettingsActivity.class);
        startActivity(intent);
    }
    
    private void setupNotificationSettings(View view) {
        View notificationsCard = view.findViewById(R.id.card_notifications);
        if (notificationsCard != null) {
            notificationsCard.setOnClickListener(v -> navigateToNotificationsSettings());
        }
    }
    
    private void navigateToNotificationsSettings() {
        Intent intent = new Intent(requireContext(), NotificationsSettingsActivity.class);
        startActivity(intent);
    }
    
    private void setupDataSettings(View view) {
        View dataCard = view.findViewById(R.id.card_data);
        if (dataCard != null) {
            dataCard.setOnClickListener(v -> navigateToDataSettings());
        }
    }
    
    private void navigateToDataSettings() {
        Intent intent = new Intent(requireContext(), DataSettingsActivity.class);
        startActivity(intent);
    }

    private void setupLanguageSettings(View view) {
        updateLanguageValue();
        if (cardLanguage != null) {
            cardLanguage.setOnClickListener(v -> navigateToLanguageSettings());
        }
    }

    private void navigateToLanguageSettings() {
        Intent intent = new Intent(requireContext(), LanguageSettingsActivity.class);
        startActivity(intent);
    }

    private void updateLanguageValue() {
        if (languageValue != null) {
            languageValue.setText(localeManager.getLanguageName(localeManager.getLanguage()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLanguageValue();
        loadBalance();
        if (getActivity() != null) {
            androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(getString(R.string.settings_title));
            }
        }
    }

    private void setupBalanceSettings(View view) {
        if (cardBalance != null) {
            cardBalance.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), org.nikanikoo.flux.ui.activities.BalanceActivity.class);
                startActivity(intent);
            });
        }
    }

    private void loadBalance() {
        if (balanceValue == null) return;
        
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("balance_cache", android.content.Context.MODE_PRIVATE);
        int cachedBalance = prefs.getInt("cached_balance", -1);
        if (cachedBalance != -1) {
            balanceValue.setText(formatVotes(cachedBalance));
        } else {
            balanceValue.setText(getString(R.string.loading));
        }
        
        OpenVKApi.getInstance(requireContext()).callMethod("account.getBalance", null, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                if (isAdded() && getActivity() != null) {
                    try {
                        org.json.JSONObject responseObj = response.getJSONObject("response");
                        int votes = responseObj.optInt("votes", 0);
                        
                        prefs.edit().putInt("cached_balance", votes).apply();
                        
                        balanceValue.setText(formatVotes(votes));
                    } catch (Exception e) {
                        org.nikanikoo.flux.utils.Logger.e("SettingsFragment", "Failed to parse balance response", e);
                        balanceValue.setText(getString(R.string.settings_balance_desc));
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded() && getActivity() != null) {
                    org.nikanikoo.flux.utils.Logger.e("SettingsFragment", "Failed to load balance: " + error);
                    if (cachedBalance == -1) {
                        balanceValue.setText(getString(R.string.settings_balance_desc));
                    }
                }
            }
        });
    }

    private String formatVotes(int count) {
        LocaleManager localeMgr = LocaleManager.getInstance(requireContext());
        java.util.Locale locale;
        int langInt = localeMgr.getLanguage();
        if (langInt == LocaleManager.LANGUAGE_SYSTEM) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                locale = android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0);
            } else {
                locale = android.content.res.Resources.getSystem().getConfiguration().locale;
            }
        } else {
            switch (langInt) {
                case LocaleManager.LANGUAGE_RUSSIAN:
                    locale = new java.util.Locale("ru", "RU");
                    break;
                case LocaleManager.LANGUAGE_UKRAINIAN:
                    locale = new java.util.Locale("uk", "UA");
                    break;
                case LocaleManager.LANGUAGE_ENGLISH:
                    locale = new java.util.Locale("en", "US");
                    break;
                default:
                    locale = new java.util.Locale("ru", "RU");
                    break;
            }
        }
        String lang = locale.getLanguage();
        if ("uk".equals(lang)) {
            int lastDigit = count % 10;
            int lastTwoDigits = count % 100;
            if (lastTwoDigits >= 11 && lastTwoDigits <= 19) {
                return count + " голосів";
            } else if (lastDigit == 1) {
                return count + " голос";
            } else if (lastDigit >= 2 && lastDigit <= 4) {
                return count + " голоси";
            } else {
                return count + " голосів";
            }
        } else if ("en".equals(lang)) {
            return count == 1 ? "1 vote" : count + " votes";
        } else {
            int lastDigit = count % 10;
            int lastTwoDigits = count % 100;
            if (lastTwoDigits >= 11 && lastTwoDigits <= 19) {
                return count + " голосов";
            } else if (lastDigit == 1) {
                return count + " голос";
            } else if (lastDigit >= 2 && lastDigit <= 4) {
                return count + " голоса";
            } else {
                return count + " голосов";
            }
        }
    }

    private void setupAboutSettings() {
        // Get app version
        if (getActivity() != null) {
            String version = BuildInfo.getAppVersion(getActivity());
            if (appVersionValue != null) {
                appVersionValue.setText(version);
            }
        }

        // Get instance URL
        String currentInstance = OpenVKApi.getInstance(requireContext()).getBaseUrl();
        if (currentInstance != null && !currentInstance.isEmpty()) {
            String displayInstance = currentInstance.replace("https://", "").replace("http://", "");
            instanceUrlValue.setText(displayInstance);
        } else {
            instanceUrlValue.setText("N/A");
        }
        
        // About app click
        if (settingsAboutApp != null) {
            settingsAboutApp.setOnClickListener(v -> navigateToAboutApp());
        }
        
        // About instance click
        if (settingsAboutInstance != null) {
            settingsAboutInstance.setOnClickListener(v -> navigateToAboutInstance());
        }
    }
    
    private void navigateToAboutApp() {
        Intent intent = new Intent(requireContext(), AboutAppActivity.class);
        startActivity(intent);
    }
    
    private void navigateToAboutInstance() {
        Intent intent = new Intent(requireContext(), AboutInstanceActivity.class);
        startActivity(intent);
    }
    
    private void setupLogout() {
        btnLogout.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_logout2))
                .setMessage(getString(R.string.settings_logout_message))
                .setPositiveButton(getString(R.string.settings_logout), (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        });
    }
    
    private void performLogout() {
        // Remove current account from AccountManager
        AccountManager accountManager = AccountManager.getInstance(requireContext());
        String currentAccountId = accountManager.getCurrentAccountId();
        if (currentAccountId != null) {
            accountManager.removeAccount(currentAccountId);
        }
        
        // Check if there are other accounts
        if (accountManager.getAccountCount() > 0) {
            // Switch to first available account
            AccountManager.Account nextAccount = accountManager.getAccounts().get(0);
            accountManager.switchToAccount(nextAccount.id);
            
            // Update TokenManager
            try {
                org.nikanikoo.flux.security.TokenManager tokenManager =
                    new org.nikanikoo.flux.security.TokenManager(requireContext());
                tokenManager.saveToken(nextAccount.token);
                tokenManager.saveInstance(nextAccount.instance);
            } catch (org.nikanikoo.flux.security.TokenManager.EncryptionException e) {
                Toast.makeText(requireContext(), getString(R.string.account_encryption_error), Toast.LENGTH_LONG).show();
                return;
            }
            
            // Clear cache and reset API instance
            ProfileManager.getInstance(requireContext()).clearCache();
            MessageNotificationManager.getInstance(requireContext()).clearCache();
            org.nikanikoo.flux.ui.fragments.news.NewsFragment.clearCache();

            // Reset OpenVKApi to use new token
            org.nikanikoo.flux.data.managers.api.OpenVKApi.resetInstance();

            android.util.Log.d("SettingsFragment", "Switched to account: " + nextAccount.fullName + " (id: " + nextAccount.id + ")");

            Toast.makeText(requireContext(), getString(R.string.account_switched, nextAccount.fullName), Toast.LENGTH_SHORT).show();
            
            // Restart MainActivity
            Intent intent = new Intent(requireActivity(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
            return;
        }
        
        // Clear token
        OpenVKApi.getInstance(requireContext()).logout();
        
        // Clear profile cache
        ProfileManager.getInstance(requireContext()).clearCache();
        org.nikanikoo.flux.ui.fragments.news.NewsFragment.clearCache();
        
        // Clear notifications cache and cancel all notifications
        MessageNotificationManager.getInstance(requireContext()).clearCache();
        MessageNotificationManager.getInstance(requireContext()).cancelAllNotifications();
        
        // Stop LongPoll service
        LongPollService.stop(requireContext());
        
        Toast.makeText(requireContext(), getString(R.string.settings_logout_success), Toast.LENGTH_SHORT).show();
        
        // Navigate to login screen
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
