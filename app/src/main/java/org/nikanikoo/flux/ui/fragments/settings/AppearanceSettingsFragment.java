package org.nikanikoo.flux.ui.fragments.settings;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.utils.ThemeManager;

public class AppearanceSettingsFragment extends Fragment {

    private ThemeManager themeManager;
    private TextView themeModeValue;
    private TextView colorSchemeValue;
    private TextView contrastValue;
    private View settingsThemeMode;
    private View settingsColorScheme;
    private View settingsContrast;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_appearance_settings, container, false);
        
        themeManager = ThemeManager.getInstance(requireContext());
        
        initViews(view);
        updateThemeValues();
        setupClickListeners();
        
        return view;
    }
    
    private void initViews(View view) {
        themeModeValue = view.findViewById(R.id.theme_mode_value);
        colorSchemeValue = view.findViewById(R.id.color_scheme_value);
        contrastValue = view.findViewById(R.id.contrast_value);
        
        settingsThemeMode = view.findViewById(R.id.settings_theme_mode);
        settingsColorScheme = view.findViewById(R.id.settings_color_scheme);
        settingsContrast = view.findViewById(R.id.settings_contrast);
    }
    
    private void setupClickListeners() {
        settingsThemeMode.setOnClickListener(v -> showThemeModeDialog());
        settingsColorScheme.setOnClickListener(v -> showColorSchemeDialog());
        settingsContrast.setOnClickListener(v -> showContrastDialog());
    }
    
    private void updateThemeValues() {
        themeModeValue.setText(themeManager.getThemeName(themeManager.getThemeMode()));
        colorSchemeValue.setText(themeManager.getStyleName(themeManager.getThemeStyle()));
        contrastValue.setText(themeManager.getContrastName(themeManager.getContrastMode()));
    }
    
    private void showThemeModeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_theme_mode, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.theme_mode_radio_group);
        MaterialRadioButton radioLight = dialogView.findViewById(R.id.radio_theme_light);
        MaterialRadioButton radioDark = dialogView.findViewById(R.id.radio_theme_dark);
        MaterialRadioButton radioAmoled = dialogView.findViewById(R.id.radio_theme_amoled);
        MaterialRadioButton radioSystem = dialogView.findViewById(R.id.radio_theme_system);
        
        int currentTheme = themeManager.getThemeMode();
        switch (currentTheme) {
            case ThemeManager.THEME_LIGHT:
                radioLight.setChecked(true);
                break;
            case ThemeManager.THEME_DARK:
                radioDark.setChecked(true);
                break;
            case ThemeManager.THEME_AMOLED:
                radioAmoled.setChecked(true);
                break;
            case ThemeManager.THEME_SYSTEM:
                radioSystem.setChecked(true);
                break;
        }
        
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apply), (d, which) -> {
                int newTheme;
                int checkedId = radioGroup.getCheckedRadioButtonId();
                
                if (checkedId == R.id.radio_theme_light) {
                    newTheme = ThemeManager.THEME_LIGHT;
                } else if (checkedId == R.id.radio_theme_dark) {
                    newTheme = ThemeManager.THEME_DARK;
                } else if (checkedId == R.id.radio_theme_amoled) {
                    newTheme = ThemeManager.THEME_AMOLED;
                } else {
                    newTheme = ThemeManager.THEME_SYSTEM;
                }
                
                themeManager.setThemeMode(newTheme);
                updateThemeValues();
                restartMainActivity();
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .create();
        
        dialog.show();
    }
    
    private void showColorSchemeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_color_scheme, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.color_scheme_radio_group);
        MaterialRadioButton radioDefault = dialogView.findViewById(R.id.radio_style_default);
        MaterialRadioButton radioMaterialYou = dialogView.findViewById(R.id.radio_style_material_you);
        MaterialRadioButton radioCustom = dialogView.findViewById(R.id.radio_style_custom);
        TextView materialYouInfo = dialogView.findViewById(R.id.text_material_you_info);
        
        View customColorContainer = dialogView.findViewById(R.id.custom_color_container);
        MaterialCardView previewView = dialogView.findViewById(R.id.custom_color_preview);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.custom_color_input_layout);
        TextInputEditText editHex = dialogView.findViewById(R.id.edit_custom_color_hex);
        
        boolean materialYouAvailable = themeManager.isDynamicColorsAvailable();
        if (!materialYouAvailable) {
            radioMaterialYou.setEnabled(false);
            materialYouInfo.setVisibility(View.VISIBLE);
        }
        
        int currentStyle = themeManager.getThemeStyle();
        switch (currentStyle) {
            case ThemeManager.STYLE_DEFAULT:
                radioDefault.setChecked(true);
                break;
            case ThemeManager.STYLE_MATERIAL_YOU:
                if (materialYouAvailable) {
                    radioMaterialYou.setChecked(true);
                } else {
                    radioDefault.setChecked(true);
                }
                break;
            case ThemeManager.STYLE_CUSTOM_COLOR:
                radioCustom.setChecked(true);
                customColorContainer.setVisibility(View.VISIBLE);
                break;
        }
        
        org.nikanikoo.flux.ui.views.HsvGradientView hsvGradientView = dialogView.findViewById(R.id.hsv_gradient_view);
        org.nikanikoo.flux.ui.views.HueSliderView hueSliderView = dialogView.findViewById(R.id.hue_slider_view);
        
        int customColor = themeManager.getCustomColor();
        String hexString = String.format("%06X", (0xFFFFFF & customColor));
        editHex.setText(hexString);
        previewView.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(customColor));
        
        if (hsvGradientView != null && hueSliderView != null) {
            float[] hsv = new float[3];
            Color.colorToHSV(customColor, hsv);
            hsvGradientView.setColor(hsv[0], hsv[1], hsv[2]);
            hueSliderView.setHue(hsv[0]);
            
            hsvGradientView.setOnColorChangedListener((h, s, v) -> {
                int selectedColor = Color.HSVToColor(new float[]{h, s, v});
                previewView.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(selectedColor));
                if (!editHex.hasFocus()) {
                    String hex = String.format("%06X", (0xFFFFFF & selectedColor));
                    editHex.setText(hex);
                    inputLayout.setError(null);
                }
            });

            hueSliderView.setOnHueChangedListener(hue -> {
                hsvGradientView.setHue(hue);
                int selectedColor = Color.HSVToColor(new float[]{hue, hsvGradientView.getSaturation(), hsvGradientView.getValue()});
                previewView.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(selectedColor));
                if (!editHex.hasFocus()) {
                    String hex = String.format("%06X", (0xFFFFFF & selectedColor));
                    editHex.setText(hex);
                    inputLayout.setError(null);
                }
            });
        }
        
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_style_custom) {
                customColorContainer.setVisibility(View.VISIBLE);
            } else {
                customColorContainer.setVisibility(View.GONE);
            }
        });
        
        editHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String hex = s.toString().trim();
                if (hex.length() == 6) {
                    try {
                        int parsedColor = Color.parseColor("#" + hex);
                        previewView.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(parsedColor));
                        
                        if (editHex.hasFocus() && hsvGradientView != null && hueSliderView != null) {
                            float[] hsvVals = new float[3];
                            Color.colorToHSV(parsedColor, hsvVals);
                            hsvGradientView.setColor(hsvVals[0], hsvVals[1], hsvVals[2]);
                            hueSliderView.setHue(hsvVals[0]);
                        }
                        inputLayout.setError(null);
                    } catch (IllegalArgumentException e) {
                        inputLayout.setError(getString(R.string.custom_color_hex_error));
                    }
                } else if (hex.length() > 0) {
                    inputLayout.setError(getString(R.string.custom_color_hex_error));
                } else {
                    inputLayout.setError(null);
                }
            }
        });
        
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apply), (d, which) -> {
                int newStyle;
                int checkedId = radioGroup.getCheckedRadioButtonId();
                
                if (checkedId == R.id.radio_style_material_you) {
                    newStyle = ThemeManager.STYLE_MATERIAL_YOU;
                } else if (checkedId == R.id.radio_style_custom) {
                    newStyle = ThemeManager.STYLE_CUSTOM_COLOR;
                    String hex = editHex.getText().toString().trim();
                    int parsedColor = 0xFF2196F3;
                    try {
                        if (hex.length() == 6) {
                            parsedColor = Color.parseColor("#" + hex);
                        } else {
                            throw new IllegalArgumentException();
                        }
                    } catch (Exception e) {
                        Toast.makeText(getContext(), getString(R.string.custom_color_hex_error), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    themeManager.setCustomColor(parsedColor);
                } else {
                    newStyle = ThemeManager.STYLE_DEFAULT;
                }
                
                themeManager.setThemeStyle(newStyle);
                updateThemeValues();
                restartMainActivity();
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .create();
        
        dialog.show();
    }
    
    private void showContrastDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_contrast, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.contrast_radio_group);
        MaterialRadioButton radioNormal = dialogView.findViewById(R.id.radio_contrast_normal);
        MaterialRadioButton radioMedium = dialogView.findViewById(R.id.radio_contrast_medium);
        MaterialRadioButton radioHigh = dialogView.findViewById(R.id.radio_contrast_high);
        
        int currentContrast = themeManager.getContrastMode();
        switch (currentContrast) {
            case ThemeManager.CONTRAST_NORMAL:
                radioNormal.setChecked(true);
                break;
            case ThemeManager.CONTRAST_MEDIUM:
                radioMedium.setChecked(true);
                break;
            case ThemeManager.CONTRAST_HIGH:
                radioHigh.setChecked(true);
                break;
        }
        
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apply), (d, which) -> {
                int newContrast;
                int checkedId = radioGroup.getCheckedRadioButtonId();
                
                if (checkedId == R.id.radio_contrast_high) {
                    newContrast = ThemeManager.CONTRAST_HIGH;
                } else if (checkedId == R.id.radio_contrast_medium) {
                    newContrast = ThemeManager.CONTRAST_MEDIUM;
                } else {
                    newContrast = ThemeManager.CONTRAST_NORMAL;
                }
                
                themeManager.setContrastMode(newContrast);
                updateThemeValues();
                restartMainActivity();
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .create();
        
        dialog.show();
    }
    
    private void restartMainActivity() {
        if (getActivity() != null) {
            getActivity().recreate();
        }
    }
}
