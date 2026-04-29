package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.security.AccountManager;
import org.nikanikoo.flux.ui.adapters.InstancesAdapter;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailLayout;
    private TextInputLayout inputPassword;
    private TextInputEditText editLogin;
    private TextInputEditText editPassword;
    private MaterialButton btnLogin;
    private CoordinatorLayout coordinatorLayout;
    private View dimOverlay;
    private LinearLayout bottomSheet;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private RecyclerView recyclerViewInstances;
    private InstancesAdapter instancesAdapter;
    private TextView selectedInstanceName;
    private TextView selectedInstanceUrl;
    private TextView selectedInstancePing;
    private TextView textAddInstance;

    private static final String[] INSTANCE_URLS = {
        "https://api.openvk.org",
        "http://openvk.xyz",
        "https://api.vepurovk.fun",
        "https://vepurovk.xyz"
    };
    
    private static final String[] INSTANCE_DISPLAY_NAMES = {
        "api.openvk.org",
        "openvk.xyz",
        "api.vepurovk.fun",
        "vepurovk.xyz"
    };
    
    private static final String[] INSTANCE_PINGS = {
        "?ms",
        "?ms",
        "?ms",
        "?ms"
    };
    
    private final List<String> instanceUrlsList = new ArrayList<>();
    private final List<String> instanceDisplayNamesList = new ArrayList<>();
    private final List<String> instancePingsList = new ArrayList<>();
    
    private int selectedInstanceIndex = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = LocaleManager.getInstance(newBase);
        Context context = localeManager.updateContext(newBase);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applyThemeToActivity(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);
        
        ThemeManager.applySystemBarsAppearance(this);

        if (themeManager.getThemeStyle() == ThemeManager.STYLE_MATERIAL_YOU && 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        dimOverlay = findViewById(R.id.dimOverlay);
        bottomSheet = findViewById(R.id.bottomSheet);
        emailLayout = findViewById(R.id.emailLayout);
        inputPassword = findViewById(R.id.input_password);
        editLogin = findViewById(R.id.edit_login);
        editPassword = findViewById(R.id.edit_password);
        btnLogin = findViewById(R.id.btn_login);
        selectedInstanceName = findViewById(R.id.selectedInstanceName);
        selectedInstanceUrl = findViewById(R.id.selectedInstanceUrl);
        selectedInstancePing = findViewById(R.id.selectedInstancePing);
        textAddInstance = findViewById(R.id.textAddInstance);
        recyclerViewInstances = findViewById(R.id.recyclerViewInstances);

        // Инициализируем списки из статических массивов
        for (int i = 0; i < INSTANCE_URLS.length; i++) {
            instanceUrlsList.add(INSTANCE_URLS[i]);
            instanceDisplayNamesList.add(INSTANCE_DISPLAY_NAMES[i]);
            instancePingsList.add(generateRandomPing());
        }
        
        setupBottomSheet();
        setupInstancesList();
        updateSelectedInstanceDisplay();

        btnLogin.setOnClickListener(v -> performLogin());

        findViewById(R.id.btnRegister).setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.closed), Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.textAddInstance).setOnClickListener(v -> {
            showAddInstanceDialog();
        });
    }
    
    private void setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setPeekHeight(90);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    dimOverlay.setVisibility(View.VISIBLE);
                    dimOverlay.setAlpha(1f);
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    dimOverlay.setVisibility(View.GONE);
                }
            }

            @Override
            public void onSlide(View bottomSheet, float slideOffset) {
                if (slideOffset > 0) {
                    dimOverlay.setVisibility(View.VISIBLE);
                    dimOverlay.setAlpha(slideOffset);
                } else {
                    dimOverlay.setVisibility(View.GONE);
                }
            }
        });

        findViewById(R.id.selectedInstanceContainer).setOnClickListener(v -> {
            if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
    }
    
    private void setupInstancesList() {
        recyclerViewInstances.setLayoutManager(new LinearLayoutManager(this));
        List<InstancesAdapter.InstanceItem> items = new ArrayList<>();
        for (int i = 0; i < instanceDisplayNamesList.size(); i++) {
            items.add(new InstancesAdapter.InstanceItem(
                instanceDisplayNamesList.get(i),
                instanceUrlsList.get(i),
                instancePingsList.get(i),
                i == selectedInstanceIndex
            ));
        }
        instancesAdapter = new InstancesAdapter(items, this::onInstanceSelected);
        recyclerViewInstances.setAdapter(instancesAdapter);
    }
    
    private String generateRandomPing() {
        // Генерируем случайный пинг от 30 до 350 мс
        int pingMs = 30 + (int)(Math.random() * 320);
        return pingMs + "ms";
    }
    
    private void onInstanceSelected(int position) {
        selectedInstanceIndex = position;
        updateSelectedInstanceDisplay();
        instancesAdapter.setSelectedPosition(position);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }
    
    private void updateSelectedInstanceDisplay() {
        if (selectedInstanceIndex >= 0 && selectedInstanceIndex < instanceDisplayNamesList.size()) {
            selectedInstanceName.setText(instanceDisplayNamesList.get(selectedInstanceIndex));
            selectedInstanceUrl.setText(instanceUrlsList.get(selectedInstanceIndex));
            selectedInstancePing.setText(instancePingsList.get(selectedInstanceIndex));
        }
    }
    
    private void performLogin() {
        String instance = getSelectedInstance();
        String login = editLogin.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (login.isEmpty()) {
            emailLayout.setError(getString(R.string.login_enter_email));
            editLogin.requestFocus();
            return;
        } else {
            emailLayout.setError(null);
        }
        if (password.isEmpty()) {
            inputPassword.setError(getString(R.string.login_enter_password));
            editPassword.requestFocus();
            return;
        } else {
            inputPassword.setError(null);
        }

        final String formattedInstance = formatInstanceUrl(instance);

        if (isInsecureConnection(formattedInstance)) {
            showInsecureConnectionDialog(formattedInstance, login, password);
            return;
        }

        proceedWithLogin(formattedInstance, login, password);
    }
    
    private String getSelectedInstance() {
        if (selectedInstanceIndex >= 0 && selectedInstanceIndex < instanceUrlsList.size()) {
            return instanceUrlsList.get(selectedInstanceIndex);
        }
        return "https://api.openvk.org";
    }

    private String formatInstanceUrl(String instance) {
        if (instance == null || instance.trim().isEmpty()) {
            return "https://api.openvk.org";
        }
        
        instance = instance.trim();

        if (instance.startsWith("http://") || instance.startsWith("https://")) {
            return instance;
        }

        return "https://" + instance;
    }

    private boolean isInsecureConnection(String instanceUrl) {
        return instanceUrl.startsWith("http://");
    }

    private void showInsecureConnectionDialog(final String instance, final String login, final String password) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_insecure_connection, null);

        TextView urlText = new TextView(this);
        urlText.setText("\n" + getString(R.string.login_adress) + instance);
        urlText.setTextSize(14);
        urlText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        
        new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_login_confirm), (dialog, which) -> {
                proceedWithLogin(instance, login, password);
            })
            .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                dialog.dismiss();
                btnLogin.setEnabled(true);
                btnLogin.setText(getString(R.string.btn_login));
            })
            .setCancelable(false)
            .show();
    }

    private void proceedWithLogin(String instance, String login, String password) {
        OpenVKApi.resetInstance();
        
        OpenVKApi.getInstance(this).saveInstance(instance);
        
        btnLogin.setEnabled(false);
        btnLogin.setText(getString(R.string.btn_login_loading));

        OpenVKApi.getInstance(this).login(login, password, new OpenVKApi.LoginCallback() {
            @Override
            public void onSuccess(String token) {
                runOnUiThread(() -> {
                    OpenVKApi.getInstance(LoginActivity.this).saveToken(token);
                    ProfileManager.getInstance(LoginActivity.this).clearCache();

                    ProfileManager.getInstance(LoginActivity.this).loadProfile(false, false, new ProfileManager.ProfileCallback() {
                        @Override
                        public void onSuccess(UserProfile profile) {
                            String instance = OpenVKApi.getInstance(LoginActivity.this).getBaseUrl();
                            AccountManager.getInstance(LoginActivity.this).addAccount(token, instance, profile);
                            
                            Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                            
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            if (getIntent().getBooleanExtra("add_account", false)) {
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            }
                            startActivity(intent);
                            finish();
                        }
                        
                        @Override
                        public void onError(String error) {
                            String instance = OpenVKApi.getInstance(LoginActivity.this).getBaseUrl();
                            UserProfile dummyProfile = new UserProfile();
                            dummyProfile.setId(0);
                            dummyProfile.setFirstName(getString(R.string.loading));
                            dummyProfile.setLastName("");
                            dummyProfile.setScreenName(login);
                            AccountManager.getInstance(LoginActivity.this).addAccount(token, instance, dummyProfile);
                            
                            Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                            
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            if (getIntent().getBooleanExtra("add_account", false)) {
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            }
                            startActivity(intent);
                            finish();
                        }
                    });
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    handleLoginError(error, login);
                });
            }
        });
    }

    private void handleLoginError(String error, String login) {
        System.out.println("Ошибка авторизации: " + error);
        
        try {
            JSONObject errorJson = new JSONObject(error);
            
            System.out.println(errorJson.toString());

            boolean needs2FA = false;
            String errorMsg = "";
            
            if (errorJson.has("error_code")) {
                int errorCode = errorJson.getInt("error_code");
                System.out.println("Код ошибки: " + errorCode);
                
                if (errorJson.has("error_msg")) {
                    errorMsg = errorJson.getString("error_msg");
                    System.out.println("Ошибка: " + errorMsg);

                    if (errorCode == 28 && (errorMsg.contains("Invalid 2FA") || errorMsg.contains("2FA"))) {
                        needs2FA = true;
                    } else if (errorCode == 28 && (errorMsg.contains("Invalid username") || errorMsg.contains("Invalid password") || errorMsg.contains("invalid_grant"))) {
                        needs2FA = false;
                    } else if (errorCode == 28) {
                        needs2FA = true;
                    }
                }
            }
            
            if (needs2FA) {
                System.out.println("Двухфакторка включена, переход на другой активити");

                Intent intent = new Intent(LoginActivity.this, TwoFactorActivity.class);
                intent.putExtra("username", login);
                intent.putExtra("password", editPassword.getText().toString().trim());
                intent.putExtra("instance", OpenVKApi.getInstance(LoginActivity.this).getBaseUrl());
                startActivity(intent);
                finish();
                return;
            }
            
            // Показываем понятное сообщение об ошибке
            String message = "";
            
            if (!errorMsg.isEmpty()) {
                if (errorMsg.contains("Invalid username") || errorMsg.contains("Invalid password") || errorMsg.contains("invalid_grant")) {
                    message = getString(R.string.login_error1);
                    showCustomError();
                } else if (errorMsg.contains("Invalid 2FA")) {
                    message = getString(R.string.login_error2);
                } else {
                    message = errorMsg;
                }
            } else if (errorJson.has("error")) {
                String errorType = errorJson.optString("error", "");
                String errorDescription = errorJson.optString("error_description", "");
                
                if (errorType.equals("invalid_grant") || errorDescription.contains("Invalid username or password")) {
                    message = getString(R.string.login_error1);
                    showCustomError();
                } else if (errorType.equals("invalid_client")) {
                    message = getString(R.string.login_error3);
                } else if (!errorDescription.isEmpty()) {
                    message = errorDescription;
                } else if (!errorType.isEmpty()) {
                    message = getString(R.string.error_loading) + errorType;
                } else {
                    message = getString(R.string.error_unknown);
                }
            } else {
                message = getString(R.string.error_unknown);
            }
            
            if (!message.isEmpty() && !message.equals(getString(R.string.login_error1))) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            System.out.println("Ошибка парсинга JSON: " + e.getMessage());
            Toast.makeText(LoginActivity.this, getString(R.string.error_loading) + error, Toast.LENGTH_LONG).show();
        }
        
        btnLogin.setEnabled(true);
        btnLogin.setText(getString(R.string.btn_login));
    }
    
    private void showAddInstanceDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_instance, null);
        TextView title = dialogView.findViewById(R.id.dialog_title);
        TextInputLayout urlLayout = dialogView.findViewById(R.id.url_input_layout);
        TextInputEditText urlEdit = dialogView.findViewById(R.id.url_input);
        
        new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add), (dialog, which) -> {
                String url = urlEdit != null ? urlEdit.getText().toString().trim() : "";
                if (url.isEmpty()) {
                    Toast.makeText(this, getString(R.string.instance_url_empty), Toast.LENGTH_SHORT).show();
                    return;
                }
                // Добавляем новый инстанс
                String displayName = url.replaceFirst("^(https?://)?(www\\.)?", "");
                if (displayName.endsWith("/")) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                String ping = generateRandomPing();
                instanceUrlsList.add(url);
                instanceDisplayNamesList.add(displayName);
                instancePingsList.add(ping);
                
                // Обновляем адаптер
                setupInstancesList();
                // Выбираем новый инстанс
                selectedInstanceIndex = instanceUrlsList.size() - 1;
                updateSelectedInstanceDisplay();
                instancesAdapter.setSelectedPosition(selectedInstanceIndex);
                
                Toast.makeText(this, getString(R.string.instance_added), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }
    
    private void showCustomError() {
        // Подсветка полей ошибкой
        emailLayout.setErrorIconDrawable(R.drawable.ic_error_custom);
        emailLayout.setError(" ");
        inputPassword.setErrorIconDrawable(R.drawable.ic_error_custom);
        inputPassword.setError(" ");
        
        // Показываем кастомный snackbar
        Snackbar snackbar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_LONG);
        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();
        layout.setBackgroundColor(Color.TRANSPARENT);
        layout.setPadding(0, 0, 0, 0);
        
        View customView = getLayoutInflater().inflate(R.layout.custom_error_snackbar, null);
        layout.addView(customView, 0);
        
        snackbar.setAnchorView(R.id.bottomSheet);
        snackbar.show();
    }
}
