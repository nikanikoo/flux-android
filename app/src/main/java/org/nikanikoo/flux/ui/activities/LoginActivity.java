package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.security.AccountManager;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;
import org.nikanikoo.flux.utils.Logger;

public class LoginActivity extends AppCompatActivity {

    private AutoCompleteTextView spinnerInstance;
    private TextInputLayout layoutCustomInstance;
    private TextInputLayout inputPassword;
    private TextInputEditText editCustomInstance;
    private TextInputEditText editLogin;
    private TextInputEditText editPassword;
    private MaterialButton btnLogin;

    private static final String[] INSTANCE_URLS = {
        "https://api.openvk.org",
        "http://openvk.xyz",
        "https://api.vepurovk.fun",
        "https://vepurovk.xyz"
    };
    
    private String[] INSTANCE_DISPLAY_NAMES;
    
    private String CUSTOM_OPTION;
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

        INSTANCE_DISPLAY_NAMES = new String[] {
                "api.openvk.org",
                "openvk.xyz",
                "api.vepurovk.fun",
                "vepurovk.xyz",
                getString(R.string.instance_display_names_custom)
        };

        CUSTOM_OPTION = getString(R.string.instance_display_names_custom);
        
        setContentView(R.layout.activity_login);
        
        Logger.checkAndShowCrashReport(this);
        
        ThemeManager.applySystemBarsAppearance(this);

        if (themeManager.getThemeStyle() == ThemeManager.STYLE_MATERIAL_YOU && 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        spinnerInstance = findViewById(R.id.spinner_instance);
        layoutCustomInstance = findViewById(R.id.layout_custom_instance);
        editCustomInstance = findViewById(R.id.edit_custom_instance);
        editLogin = findViewById(R.id.edit_login);
        editPassword = findViewById(R.id.edit_password);
        btnLogin = findViewById(R.id.btn_login);
        inputPassword = findViewById(R.id.input_password);

        setupInstanceSpinner();

        editPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputPassword.setPasswordVisibilityToggleEnabled(true);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin();
            }
        });

        findViewById(R.id.text_forgot_password).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(LoginActivity.this, getString(R.string.closed), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.text_register).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(LoginActivity.this, getString(R.string.closed), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.text_login_with_token).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTokenLoginDialog();
            }
        });
    }
    
    private void setupInstanceSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            INSTANCE_DISPLAY_NAMES
        );
        
        spinnerInstance.setAdapter(adapter);

        spinnerInstance.setText(INSTANCE_DISPLAY_NAMES[0], false);
        selectedInstanceIndex = 0;
        
        spinnerInstance.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedInstanceIndex = position;

                if (INSTANCE_DISPLAY_NAMES[position].equals(CUSTOM_OPTION)) {
                    layoutCustomInstance.setVisibility(View.VISIBLE);
                    editCustomInstance.requestFocus();
                } else {
                    layoutCustomInstance.setVisibility(View.GONE);
                }
            }
        });
    }

    private void performLogin() {
        String instance = getSelectedInstance();
        String login = editLogin.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (instance.isEmpty()) {
            Toast.makeText(this, getString(R.string.select_instance), Toast.LENGTH_SHORT).show();
            return;
        }
        if (login.isEmpty()) {
            editLogin.setError(getString(R.string.login_enter_email));
            editLogin.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            editPassword.setError(getString(R.string.login_enter_password));
            editPassword.requestFocus();
            return;
        }

        final String formattedInstance = formatInstanceUrl(instance);

        if (isInsecureConnection(formattedInstance)) {
            showInsecureConnectionDialog(formattedInstance, login, password);
            return;
        }

        proceedWithLogin(formattedInstance, login, password);
    }
    
    private String getSelectedInstance() {
        if (INSTANCE_DISPLAY_NAMES[selectedInstanceIndex].equals(CUSTOM_OPTION)) {
            String customInstance = editCustomInstance.getText().toString().trim();
            if (customInstance.isEmpty()) {
                editCustomInstance.setError(getString(R.string.instance_address_hint));
                return "";
            }
            return customInstance;
        } else {
            return INSTANCE_URLS[selectedInstanceIndex];
        }
    }

    private String formatInstanceUrl(String instance) {
        if (instance == null || instance.trim().isEmpty()) {
            return "https://api.openvk.org"; // По умолчанию
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

            if ("need_validation".equals(errorJson.optString("error"))) {
                needs2FA = true;
            }
            
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
            
            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            System.out.println("Ошибка парсинга JSON: " + e.getMessage());
            Toast.makeText(LoginActivity.this, getString(R.string.error_loading) + error, Toast.LENGTH_LONG).show();
        }
        
        btnLogin.setEnabled(true);
        btnLogin.setText(getString(R.string.btn_login));
    }

    private void showTokenLoginDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login_token, null);
        
        AutoCompleteTextView dialogSpinnerInstance = dialogView.findViewById(R.id.dialog_spinner_instance);
        TextInputLayout dialogLayoutCustomInstance = dialogView.findViewById(R.id.dialog_layout_custom_instance);
        TextInputEditText dialogEditCustomInstance = dialogView.findViewById(R.id.dialog_edit_custom_instance);
        TextInputEditText dialogEditToken = dialogView.findViewById(R.id.dialog_edit_token);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            INSTANCE_DISPLAY_NAMES
        );
        dialogSpinnerInstance.setAdapter(adapter);
        
        dialogSpinnerInstance.setText(INSTANCE_DISPLAY_NAMES[selectedInstanceIndex], false);
        final int[] dialogSelectedInstanceIndex = {selectedInstanceIndex};
        
        if (INSTANCE_DISPLAY_NAMES[selectedInstanceIndex].equals(CUSTOM_OPTION)) {
            dialogLayoutCustomInstance.setVisibility(View.VISIBLE);
            dialogEditCustomInstance.setText(editCustomInstance.getText());
        }
        
        dialogSpinnerInstance.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialogSelectedInstanceIndex[0] = position;
                if (INSTANCE_DISPLAY_NAMES[position].equals(CUSTOM_OPTION)) {
                    dialogLayoutCustomInstance.setVisibility(View.VISIBLE);
                    dialogEditCustomInstance.requestFocus();
                } else {
                    dialogLayoutCustomInstance.setVisibility(View.GONE);
                }
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.login_with_token))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_login), null)
            .setNegativeButton(getString(R.string.cancel), (dialogInterface, which) -> dialogInterface.dismiss())
            .create();
            
        dialog.show();
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String instance = "";
            if (INSTANCE_DISPLAY_NAMES[dialogSelectedInstanceIndex[0]].equals(CUSTOM_OPTION)) {
                String customInstance = dialogEditCustomInstance.getText().toString().trim();
                if (customInstance.isEmpty()) {
                    dialogEditCustomInstance.setError(getString(R.string.instance_address_hint));
                    return;
                }
                instance = customInstance;
            } else {
                instance = INSTANCE_URLS[dialogSelectedInstanceIndex[0]];
            }

            String token = dialogEditToken.getText().toString().trim();
            if (token.isEmpty()) {
                dialogEditToken.setError(getString(R.string.login_token_invalid));
                dialogEditToken.requestFocus();
                return;
            }

            dialog.dismiss();
            performTokenLogin(instance, token);
        });
    }

    private void performTokenLogin(String instance, String token) {
        OpenVKApi.resetInstance();
        final String formattedInstance = formatInstanceUrl(instance);
        OpenVKApi.getInstance(this).saveInstance(formattedInstance);
        OpenVKApi.getInstance(this).saveToken(token);
        
        btnLogin.setEnabled(false);
        btnLogin.setText(getString(R.string.btn_login_loading));
        
        ProfileManager.getInstance(this).clearCache();
        ProfileManager.getInstance(this).loadProfile(false, false, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                runOnUiThread(() -> {
                    AccountManager.getInstance(LoginActivity.this).addAccount(token, formattedInstance, profile);
                    
                    Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                    
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    if (getIntent().getBooleanExtra("add_account", false)) {
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    }
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    UserProfile dummyProfile = new UserProfile();
                    dummyProfile.setId(0);
                    dummyProfile.setFirstName(getString(R.string.loading));
                    dummyProfile.setLastName("");
                    dummyProfile.setScreenName("Token User");
                    AccountManager.getInstance(LoginActivity.this).addAccount(token, formattedInstance, dummyProfile);
                    
                    Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                    
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    if (getIntent().getBooleanExtra("add_account", false)) {
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    }
                    startActivity(intent);
                    finish();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.handleSaveCrashResult(this, requestCode, resultCode, data);
    }
}
