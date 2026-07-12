package org.nikanikoo.flux.ui.fragments.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.coordinators.CacheCoordinator;
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.utils.Logger;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class DataSettingsFragment extends Fragment {

    private TextView cacheSizeValue;
    private View settingsClearCache;
    private CacheCoordinator cacheCoordinator;

    private static final int REQUEST_CODE_SAVE_LOGS = 1100;

    private View settingsLogLimit;
    private TextView logLimitValue;
    private View settingsSaveLogs;
    private View settingsClearLogs;
    private TextView logsSizeValue;

    private AudioCacheManager audioCacheManager;
    private View settingsSaveOnListeningContainer;
    private SwitchMaterial switchSaveOnListening;
    private View settingsMusicCacheLimit;
    private TextView musicCacheLimitValue;

    private final android.content.BroadcastReceiver audioCacheReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            updateMusicCacheText();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data_settings, container, false);

        cacheCoordinator = new CacheCoordinator(requireContext());
        audioCacheManager = AudioCacheManager.getInstance(requireContext());

        initViews(view);
        calculateCacheSize();
        updateLogsSize();
        updateLogLimitText();
        setupClickListeners();

        switchSaveOnListening.setChecked(audioCacheManager.isSaveOnListeningEnabled());
        updateMusicCacheText();

        return view;
    }
    
    private void initViews(View view) {
        cacheSizeValue = view.findViewById(R.id.cache_size_value);
        settingsClearCache = view.findViewById(R.id.settings_clear_cache);

        settingsLogLimit = view.findViewById(R.id.settings_log_limit);
        logLimitValue = view.findViewById(R.id.log_limit_value);
        settingsSaveLogs = view.findViewById(R.id.settings_save_logs);
        settingsClearLogs = view.findViewById(R.id.settings_clear_logs);
        logsSizeValue = view.findViewById(R.id.logs_size_value);

        settingsSaveOnListeningContainer = view.findViewById(R.id.settings_save_on_listening_container);
        switchSaveOnListening = view.findViewById(R.id.switch_save_on_listening);
        settingsMusicCacheLimit = view.findViewById(R.id.settings_music_cache_limit);
        musicCacheLimitValue = view.findViewById(R.id.music_cache_limit_value);
    }
    
    private void setupClickListeners() {
        settingsClearCache.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.data_clear_cache))
                .setMessage(getString(R.string.data_clear_cache_message))
                .setPositiveButton(getString(R.string.data_clear_btn_clear), (dialog, which) -> clearCache())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        });

        settingsLogLimit.setOnClickListener(v -> {
            String cancelStr = getString(R.string.cancel);
            String unlimitedStr = "Unlimited";
            if ("Відміна".equals(cancelStr)) {
                unlimitedStr = "Без обмежень";
            } else if ("Отмена".equals(cancelStr)) {
                unlimitedStr = "Без ограничений";
            }

            String[] options = {"512 KB", "1 MB", "5 MB", "10 MB", unlimitedStr};
            long[] values = {
                512 * 1024L,
                1024 * 1024L,
                5 * 1024 * 1024L,
                10 * 1024 * 1024L,
                -1L
            };

            long currentLimit = Logger.getLogLimit();
            int selectedIndex = 4;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == currentLimit) {
                    selectedIndex = i;
                    break;
                }
            }

            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_log_limit))
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    Logger.setLogLimit(values[which]);
                    logLimitValue.setText(options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        });

        settingsSaveLogs.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "flux_app_logs.txt");
                startActivityForResult(intent, REQUEST_CODE_SAVE_LOGS);
            } catch (Exception e) {
                Toast.makeText(requireContext(), getString(R.string.crash_saved_error), Toast.LENGTH_SHORT).show();
            }
        });

        settingsSaveLogs.setOnLongClickListener(v -> {
            throw new RuntimeException("Симуляция падения для проверки краш-репортера Flux!");
        });

        settingsClearLogs.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_clear_logs_confirm))
                .setMessage(getString(R.string.settings_clear_logs_message))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    Logger.clearLogs(() -> {
                        Toast.makeText(requireContext(), getString(R.string.settings_logs_cleared), Toast.LENGTH_SHORT).show();
                        updateLogsSize();
                    });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        });

        settingsSaveOnListeningContainer.setOnClickListener(v -> {
            boolean isChecked = !switchSaveOnListening.isChecked();
            switchSaveOnListening.setChecked(isChecked);
            audioCacheManager.setSaveOnListeningEnabled(isChecked);
        });

        settingsMusicCacheLimit.setOnClickListener(v -> {
            String cancelStr = getString(R.string.cancel);
            String unlimitedStr = getString(R.string.settings_music_cache_unlimited);

            String[] options = {"100 MB", "250 MB", "500 MB", "1 GB", "2 GB", "5 GB", unlimitedStr};
            long[] values = {
                100 * 1024 * 1024L,
                250 * 1024 * 1024L,
                500 * 1024 * 1024L,
                1024 * 1024 * 1024L,
                2L * 1024 * 1024 * 1024L,
                5L * 1024 * 1024 * 1024L,
                -1L
            };

            long currentLimit = audioCacheManager.getCacheLimit();
            int selectedIndex = 6;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == currentLimit) {
                    selectedIndex = i;
                    break;
                }
            }

            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_music_cache_limit))
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    audioCacheManager.setCacheLimit(values[which]);
                    updateMusicCacheText();
                    dialog.dismiss();
                })
                .setNegativeButton(cancelStr, null)
                .show();
        });
    }
    
    private void calculateCacheSize() {
        new Thread(() -> {
            try {
                CacheCoordinator.CacheStats stats = cacheCoordinator.getCacheStats();
                String sizeStr = CacheCoordinator.CacheStats.formatSize(stats.totalCacheDirSizeBytes);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> cacheSizeValue.setText(sizeStr));
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> cacheSizeValue.setText("N/A"));
                }
            }
        }).start();
    }

    private void updateLogsSize() {
        long size = Logger.getLogsSize();
        logsSizeValue.setText(Logger.formatSize(size));
    }
    
    private void updateLogLimitText() {
        long limit = Logger.getLogLimit();
        String cancelStr = getString(R.string.cancel);
        String unlimitedStr = "Unlimited";
        if ("Відміна".equals(cancelStr)) {
            unlimitedStr = "Без обмежень";
        } else if ("Отмена".equals(cancelStr)) {
            unlimitedStr = "Без ограничений";
        }
        
        if (limit == 512 * 1024L) {
            logLimitValue.setText("512 KB");
        } else if (limit == 1024 * 1024L) {
            logLimitValue.setText("1 MB");
        } else if (limit == 5 * 1024 * 1024L) {
            logLimitValue.setText("5 MB");
        } else if (limit == 10 * 1024 * 1024L) {
            logLimitValue.setText("10 MB");
        } else {
            logLimitValue.setText(unlimitedStr);
        }
    }

    private void clearCache() {
        new Thread(() -> {
            try {
                cacheCoordinator.clearAllCaches();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.data_cache_cleared), Toast.LENGTH_SHORT).show();
                        calculateCacheSize();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.data_cache_clear_error), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAVE_LOGS) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
                android.net.Uri uri = data.getData();
                Logger.saveLogsToFile(requireContext(), uri, () -> {
                    Toast.makeText(requireContext(), getString(R.string.crash_saved_success), Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        android.content.IntentFilter filter = new android.content.IntentFilter(AudioCacheManager.ACTION_AUDIO_CACHE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(audioCacheReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(audioCacheReceiver, filter);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            requireContext().unregisterReceiver(audioCacheReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void updateMusicCacheText() {
        new Thread(() -> {
            long sizeBytes = audioCacheManager.getCacheDirSize();
            long limitBytes = audioCacheManager.getCacheLimit();
            
            String sizeStr = formatBytesToMB(sizeBytes) + " MB";
            
            String statusText;
            if (limitBytes == -1L) {
                statusText = getString(R.string.settings_music_cache_occupied_unlimited, sizeStr);
            } else {
                String limitStr = (limitBytes / (1024 * 1024)) + " MB";
                statusText = getString(R.string.settings_music_cache_occupied, sizeStr, limitStr);
            }
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> musicCacheLimitValue.setText(statusText));
            }
        }).start();
    }

    private String formatBytesToMB(long bytes) {
        double megabytes = bytes / (1024.0 * 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f", megabytes);
    }
}
