package org.nikanikoo.flux.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.WindowInsetsController;
import android.view.View;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import org.nikanikoo.flux.R;

public class ThemeManager {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_THEME_STYLE = "theme_style";
    private static final String KEY_CONTRAST_MODE = "contrast_mode";
    private static final String KEY_CUSTOM_COLOR = "custom_theme_color";

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_SYSTEM = 2;
    public static final int THEME_AMOLED = 3;

    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_MATERIAL_YOU = 1;
    public static final int STYLE_GREEN = 2;
    public static final int STYLE_PURPLE = 3;
    public static final int STYLE_RED = 4;
    public static final int STYLE_CUSTOM_COLOR = 5;

    public static final int CONTRAST_NORMAL = 0;
    public static final int CONTRAST_MEDIUM = 1;
    public static final int CONTRAST_HIGH = 2;
    
    private static ThemeManager instance;
    private final SharedPreferences prefs;
    private final Context context;
    
    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        forceDynamicColorsSupport();
    }
    
    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    private void forceDynamicColorsSupport() {
        try {
            java.lang.reflect.Field condField = com.google.android.material.color.DynamicColors.class.getDeclaredField("DEFAULT_DEVICE_SUPPORT_CONDITION");
            condField.setAccessible(true);
            Object defaultCondition = condField.get(null);

            String[] mapNames = {"DYNAMIC_COLOR_SUPPORTED_BRANDS", "DYNAMIC_COLOR_SUPPORTED_MANUFACTURERS"};
            for (String mapName : mapNames) {
                try {
                    java.lang.reflect.Field field = com.google.android.material.color.DynamicColors.class.getDeclaredField(mapName);
                    field.setAccessible(true);
                    Object mapObj = field.get(null);
                    if (mapObj instanceof java.util.Map) {
                        java.util.Map<String, Object> originalMap = (java.util.Map<String, Object>) mapObj;

                        java.util.Map<String, Object> mutableMap = new java.util.HashMap<>(originalMap);

                        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
                        String brand = android.os.Build.BRAND.toLowerCase(java.util.Locale.ROOT);
                        mutableMap.put(manufacturer, defaultCondition);
                        mutableMap.put(brand, defaultCondition);
                        mutableMap.put("unknown", defaultCondition);

                        try {
                            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                            modifiersField.setAccessible(true);
                            modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                            field.set(null, mutableMap);
                        } catch (Exception ex) {
                            field.set(null, mutableMap);
                        }
                    }
                } catch (Exception e) {
                    // Ignore: reflection failed for this map
                }
            }
        } catch (Exception e) {
            // Ignore: reflection failed
        }
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyTheme(mode);
    }
    
    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public boolean isDarkMode() {
        int mode = getThemeMode();
        if (mode == THEME_DARK || mode == THEME_AMOLED) {
            return true;
        } else if (mode == THEME_LIGHT) {
            return false;
        } else {
            int nightMode = AppCompatDelegate.getDefaultNightMode();
            return nightMode == AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    public static void applySystemBarsAppearance(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController windowInsetsController = activity.getWindow().getInsetsController();
            if (windowInsetsController != null) {
                ThemeManager themeManager = ThemeManager.getInstance(activity);
                boolean isLightTheme = !themeManager.isDarkMode();
                windowInsetsController.setSystemBarsAppearance(
                        isLightTheme ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        }
    }

    public void setThemeStyle(int style) {
        prefs.edit().putInt(KEY_THEME_STYLE, style).apply();
    }
    
    public int getThemeStyle() {
        int style = prefs.getInt(KEY_THEME_STYLE, STYLE_DEFAULT);
        if (style != STYLE_DEFAULT && style != STYLE_MATERIAL_YOU && style != STYLE_CUSTOM_COLOR) {
            style = STYLE_DEFAULT;
            setThemeStyle(STYLE_DEFAULT);
        }
        return style;
    }

    public void setCustomColor(int color) {
        prefs.edit().putInt(KEY_CUSTOM_COLOR, color).apply();
    }

    public int getCustomColor() {
        return prefs.getInt(KEY_CUSTOM_COLOR, 0xFF2196F3);
    }

    public void setContrastMode(int contrast) {
        prefs.edit().putInt(KEY_CONTRAST_MODE, contrast).apply();
    }
    
    public int getContrastMode() {
        return prefs.getInt(KEY_CONTRAST_MODE, CONTRAST_NORMAL);
    }

    public boolean isDynamicColorsAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }
    
    private void applyTheme(int mode) {
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
            case THEME_AMOLED:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
    
    public void applySavedTheme() {
        applyTheme(getThemeMode());
    }

    public void applyThemeToActivity(Activity activity) {
        forceDynamicColorsSupport();
        int themeResId = getThemeResourceId();
        activity.setTheme(themeResId);

        int style = getThemeStyle();
        if (style == STYLE_MATERIAL_YOU && isDynamicColorsAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity);
        } else if (style == STYLE_CUSTOM_COLOR && isDynamicColorsAvailable()) {
            int customColor = getCustomColor();
            try {
                int overlay = isDarkMode()
                        ? com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark
                        : com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light;
                DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                        .setContentBasedSource(customColor)
                        .setThemeOverlay(overlay)
                        .build();
                DynamicColors.applyToActivityIfAvailable(activity, options);
            } catch (Exception e) {
                Logger.e("ThemeManager", "Error applying custom dynamic color", e);
            }
        }
    }

    public int getThemeResourceId() {
        int style = getThemeStyle();
        int contrast = getContrastMode();
        int mode = getThemeMode();
        if (mode == THEME_AMOLED) {
            switch (style) {
                case STYLE_GREEN:
                    return getContrastTheme(R.style.Theme_Flux_Green_Amoled, contrast);
                case STYLE_PURPLE:
                    return getContrastTheme(R.style.Theme_Flux_Purple_Amoled, contrast);
                case STYLE_RED:
                    return getContrastTheme(R.style.Theme_Flux_Red_Amoled, contrast);
                case STYLE_CUSTOM_COLOR:
                    if (isDynamicColorsAvailable()) {
                        return getContrastTheme(R.style.Theme_Flux_CustomColor_Amoled, contrast);
                    }
                case STYLE_DEFAULT:
                default:
                    return getContrastTheme(R.style.Theme_Flux_Amoled, contrast);
            }
        }

        if (style == STYLE_MATERIAL_YOU && isDynamicColorsAvailable()) {
            return getContrastTheme(R.style.Theme_Flux_DynamicColors, contrast);
        }

        if (style == STYLE_CUSTOM_COLOR && isDynamicColorsAvailable()) {
            return getContrastTheme(R.style.Theme_Flux_CustomColor, contrast);
        }

        switch (style) {
            case STYLE_GREEN:
                return getContrastTheme(R.style.Theme_Flux_Green, contrast);
            case STYLE_PURPLE:
                return getContrastTheme(R.style.Theme_Flux_Purple, contrast);
            case STYLE_RED:
                return getContrastTheme(R.style.Theme_Flux_Red, contrast);
            case STYLE_CUSTOM_COLOR:
            case STYLE_DEFAULT:
            default:
                Logger.d("ThemeManager", "Returning Default (Flux) theme");
                return getContrastTheme(R.style.Theme_Flux, contrast);
        }
    }

    private int getContrastTheme(int baseTheme, int contrast) {
        switch (contrast) {
            case CONTRAST_HIGH:
                if (baseTheme == R.style.Theme_Flux) {
                    return R.style.Theme_Flux_HighContrast;
                }
                break;
            case CONTRAST_MEDIUM:
                if (baseTheme == R.style.Theme_Flux) {
                    return R.style.Theme_Flux_MediumContrast;
                }
                break;
        }
        return baseTheme;
    }
    
    public String getThemeName(int mode) {
        switch (mode) {
            case THEME_LIGHT:
                return context.getString(R.string.appearance_theme_light);
            case THEME_DARK:
                return context.getString(R.string.appearance_theme_dark);
            case THEME_AMOLED:
                return context.getString(R.string.appearance_theme_amoled);
            case THEME_SYSTEM:
            default:
                return context.getString(R.string.appearance_theme_system);
        }
    }
    
    public String getStyleName(int style) {
        switch (style) {
            case STYLE_MATERIAL_YOU:
                return context.getString(R.string.appearance_color_material_you);
            case STYLE_GREEN:
                return context.getString(R.string.appearance_color_green);
            case STYLE_PURPLE:
                return context.getString(R.string.appearance_color_purple);
            case STYLE_RED:
                return context.getString(R.string.appearance_color_red);
            case STYLE_CUSTOM_COLOR:
                return context.getString(R.string.appearance_color_custom);
            case STYLE_DEFAULT:
            default:
                return context.getString(R.string.appearance_color_blue);
        }
    }
    
    public String getContrastName(int contrast) {
        switch (contrast) {
            case CONTRAST_HIGH:
                return context.getString(R.string.contrast_high);
            case CONTRAST_MEDIUM:
                return context.getString(R.string.contrast_medium);
            case CONTRAST_NORMAL:
            default:
                return context.getString(R.string.contrast_normal);
        }
    }
}
