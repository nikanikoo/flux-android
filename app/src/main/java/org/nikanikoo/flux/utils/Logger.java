package org.nikanikoo.flux.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.nikanikoo.flux.BuildConfig;
import org.nikanikoo.flux.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Logger {
    private static final boolean DEBUG_ENABLED = BuildConfig.DEBUG;
    private static final String APP_TAG = "Flux";

    private static Context appContext;
    private static Thread.UncaughtExceptionHandler defaultHandler;
    private static File logDir;
    private static File activeLogFile;
    private static File oldLogFile;
    private static File crashFile;

    private static final String PREF_NAME = "log_prefs";
    private static final String KEY_LOG_LIMIT = "log_size_limit";
    public static final long DEFAULT_LIMIT = 5 * 1024 * 1024; // 5 MB

    private static long currentLimitBytes = DEFAULT_LIMIT;

    public static final int REQUEST_CODE_SAVE_CRASH = 1099;
    public static String pendingCrashReport;

    private static final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    public interface LogOperationCallback {
        void onComplete();
    }

    public static void init(Context context) {
        if (appContext != null) return;
        appContext = context.getApplicationContext();
        logDir = new File(appContext.getFilesDir(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        activeLogFile = new File(logDir, "app_log.txt");
        oldLogFile = new File(logDir, "app_log.old");
        crashFile = new File(logDir, "crash_log.txt");

        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentLimitBytes = prefs.getLong(KEY_LOG_LIMIT, DEFAULT_LIMIT);

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> handleCrash(thread, throwable));
    }

    private static void handleCrash(Thread thread, Throwable throwable) {
        try {
            if (logDir == null && appContext != null) {
                logDir = new File(appContext.getFilesDir(), "logs");
                logDir.mkdirs();
                crashFile = new File(logDir, "crash_log.txt");
            }
            
            if (crashFile != null) {
                FileWriter writer = new FileWriter(crashFile, false);
                PrintWriter printWriter = new PrintWriter(writer);

                printWriter.println("=== CRASH REPORT ===");
                printWriter.println("Time: " + new Date().toString());
                printWriter.println("Thread: " + thread.getName() + " (ID: " + thread.getId() + ")");
                printWriter.println("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                printWriter.println("Android Version: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")");
                printWriter.println("App Version: " + (appContext != null ? getAppVersion(appContext) : "Unknown"));
                printWriter.println("\n--- STACK TRACE ---");
                throwable.printStackTrace(printWriter);

                if (activeLogFile != null && activeLogFile.exists()) {
                    printWriter.println("\n--- RECENT LOGS ---");
                    appendRecentLogs(printWriter);
                }

                printWriter.flush();
                printWriter.close();
            }
        } catch (Exception e) {
            Log.e("Logger", "Failed to write crash report", e);
        } finally {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }

    private static String getAppVersion(Context context) {
        try {
            android.content.pm.PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName + " (" + pInfo.versionCode + ")";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static void appendRecentLogs(PrintWriter printWriter) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(activeLogFile));
            LinkedList<String> lastLines = new LinkedList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lastLines.add(line);
                if (lastLines.size() > 50) {
                    lastLines.removeFirst();
                }
            }
            reader.close();
            for (String logLine : lastLines) {
                printWriter.println(logLine);
            }
        } catch (Exception e) {
            printWriter.println("Could not read recent logs: " + e.getMessage());
        }
    }

    private static void writeLogToFile(String level, String tag, String message, Throwable throwable) {
        if (appContext == null || activeLogFile == null) return;

        writeExecutor.submit(() -> {
            try {
                checkAndRollLogFile();

                FileWriter writer = new FileWriter(activeLogFile, true);
                PrintWriter printWriter = new PrintWriter(writer);

                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
                printWriter.println(String.format("%s [%s] %s: %s", timestamp, level, tag, message));
                
                if (throwable != null) {
                    throwable.printStackTrace(printWriter);
                }
                
                printWriter.flush();
                printWriter.close();
            } catch (Exception e) {
                Log.e("Logger", "Failed to write log to file", e);
            }
        });
    }

    private static void checkAndRollLogFile() {
        if (currentLimitBytes <= 0) return;

        if (activeLogFile.exists() && activeLogFile.length() >= currentLimitBytes) {
            try {
                if (oldLogFile.exists()) {
                    oldLogFile.delete();
                }
                activeLogFile.renameTo(oldLogFile);
                activeLogFile.createNewFile();
            } catch (Exception e) {
                Log.e("Logger", "Failed to roll log file", e);
            }
        }
    }

    public static void setLogLimit(long limitBytes) {
        currentLimitBytes = limitBytes;
        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LOG_LIMIT, limitBytes).apply();
        }
    }

    public static long getLogLimit() {
        return currentLimitBytes;
    }

    public static long getLogsSize() {
        long size = 0;
        if (activeLogFile != null && activeLogFile.exists()) {
            size += activeLogFile.length();
        }
        if (oldLogFile != null && oldLogFile.exists()) {
            size += oldLogFile.length();
        }
        return size;
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static void clearLogs(LogOperationCallback callback) {
        writeExecutor.submit(() -> {
            try {
                if (activeLogFile != null && activeLogFile.exists()) {
                    activeLogFile.delete();
                }
                if (oldLogFile != null && oldLogFile.exists()) {
                    oldLogFile.delete();
                }
                if (crashFile != null && crashFile.exists()) {
                    crashFile.delete();
                }
                if (activeLogFile != null) {
                    activeLogFile.createNewFile();
                }
            } catch (Exception e) {
                Log.e("Logger", "Failed to clear log files", e);
            }
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onComplete);
            }
        });
    }

    public static void saveLogsToFile(Context context, android.net.Uri uri, LogOperationCallback callback) {
        writeExecutor.submit(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("=== DEVICE INFO ===\n");
                sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
                sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
                sb.append("App Version: ").append(getAppVersion(context)).append("\n\n");
                sb.append("=== APP LOGS ===\n");

                if (oldLogFile != null && oldLogFile.exists()) {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(oldLogFile));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();
                    } catch (Exception e) {
                        sb.append("Error reading old logs: ").append(e.getMessage()).append("\n");
                    }
                }

                if (activeLogFile != null && activeLogFile.exists()) {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(activeLogFile));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();
                    } catch (Exception e) {
                        sb.append("Error reading active logs: ").append(e.getMessage()).append("\n");
                    }
                }

                java.io.OutputStream os = context.getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(sb.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();
                }

                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onComplete);
                }
            } catch (Exception e) {
                Log.e("Logger", "Failed to save logs to URI", e);
            }
        });
    }

    public static void checkAndShowCrashReport(Activity activity) {
        if (appContext == null) {
            init(activity.getApplicationContext());
        }

        if (crashFile != null && crashFile.exists() && crashFile.length() > 0) {
            StringBuilder sb = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(crashFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
            } catch (Exception e) {
                Log.e("Logger", "Failed to read crash log", e);
            }

            String crashReport = sb.toString();
            if (crashReport.isEmpty()) {
                crashFile.delete();
                return;
            }

            pendingCrashReport = crashReport;

            new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.crash_dialog_title))
                .setMessage(activity.getString(R.string.crash_dialog_message))
                .setPositiveButton(activity.getString(R.string.crash_option_save), (dialog, which) -> {
                    saveToDevice(activity);
                })
                .setNeutralButton(activity.getString(R.string.crash_option_copy), (dialog, which) -> {
                    copyToClipboard(activity, pendingCrashReport);
                    deleteCrashFile();
                    pendingCrashReport = null;
                })
                .setNegativeButton(activity.getString(R.string.crash_option_ignore), (dialog, which) -> {
                    deleteCrashFile();
                    pendingCrashReport = null;
                })
                .setOnCancelListener(dialog -> {
                    deleteCrashFile();
                    pendingCrashReport = null;
                })
                .show();
        }
    }

    private static void copyToClipboard(Activity activity, String text) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Flux Crash Report", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(activity, activity.getString(R.string.crash_copied_success), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("Logger", "Failed to copy crash report to clipboard", e);
        }
    }

    private static void saveToDevice(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "flux_crash_report.txt");
            activity.startActivityForResult(intent, REQUEST_CODE_SAVE_CRASH);
        } catch (Exception e) {
            Log.e("Logger", "Failed to start save crash intent", e);
            Toast.makeText(activity, activity.getString(R.string.crash_saved_error), Toast.LENGTH_SHORT).show();
            deleteCrashFile();
            pendingCrashReport = null;
        }
    }

    public static void handleSaveCrashResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SAVE_CRASH) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                if (pendingCrashReport != null) {
                    try {
                        android.net.Uri uri = data.getData();
                        java.io.OutputStream os = activity.getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            os.write(pendingCrashReport.getBytes("UTF-8"));
                            os.flush();
                            os.close();
                            Toast.makeText(activity, activity.getString(R.string.crash_saved_success), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e("Logger", "Failed to save crash log to file", e);
                        Toast.makeText(activity, activity.getString(R.string.crash_saved_error), Toast.LENGTH_SHORT).show();
                    } finally {
                        deleteCrashFile();
                        pendingCrashReport = null;
                    }
                }
            } else {
                deleteCrashFile();
                pendingCrashReport = null;
            }
        }
    }

    private static void deleteCrashFile() {
        if (crashFile != null && crashFile.exists()) {
            crashFile.delete();
        }
    }



    public static void d(String tag, String message) {
        if (DEBUG_ENABLED) {
            Log.d(APP_TAG + ":" + tag, sanitizeMessage(message));
        }
        writeLogToFile("D", tag, sanitizeMessage(message), null);
    }

    public static void i(String tag, String message) {
        Log.i(APP_TAG + ":" + tag, sanitizeMessage(message));
        writeLogToFile("I", tag, sanitizeMessage(message), null);
    }

    public static void w(String tag, String message) {
        Log.w(APP_TAG + ":" + tag, sanitizeMessage(message));
        writeLogToFile("W", tag, sanitizeMessage(message), null);
    }

    public static void w(String tag, String message, Throwable throwable) {
        Log.w(APP_TAG + ":" + tag, sanitizeMessage(message), throwable);
        writeLogToFile("W", tag, sanitizeMessage(message), throwable);
    }

    public static void e(String tag, String message) {
        Log.e(APP_TAG + ":" + tag, sanitizeMessage(message));
        writeLogToFile("E", tag, sanitizeMessage(message), null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(APP_TAG + ":" + tag, sanitizeMessage(message), throwable);
        writeLogToFile("E", tag, sanitizeMessage(message), throwable);
    }

    public static void apiResponse(String tag, String response) {
        if (DEBUG_ENABLED) {
            String sanitized = sanitizeApiResponse(response);
            Log.d(APP_TAG + ":API:" + tag, sanitized);
            writeLogToFile("D", "API:" + tag, sanitized, null);
        }
    }

    private static String sanitizeMessage(String message) {
        if (message == null) return "null";

        // Handle access_token in various formats
        message = message.replaceAll("access_token=[^&\\s]+", "access_token=***");
        message = message.replaceAll("\"access_token\"\\s*:\\s*\"[^\"]+\"", "\"access_token\":\"***\"");
        message = message.replaceAll("'access_token'\\s*:\\s*'[^']+'", "'access_token':'***'");
        message = message.replaceAll("access_token\\s*=\\s*[^&\\s,;]+", "access_token=***");

        // Handle password in various formats
        message = message.replaceAll("password=[^&\\s]+", "password=***");
        message = message.replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"***\"");
        message = message.replaceAll("'password'\\s*:\\s*'[^']+'", "'password':'***'");
        message = message.replaceAll("password\\s*=\\s*[^&\\s,;]+", "password=***");

        // Handle token in various formats
        message = message.replaceAll("token=[^&\\s]+", "token=***");
        message = message.replaceAll("\"token\"\\s*:\\s*\"[^\"]+\"", "\"token\":\"***\"");
        message = message.replaceAll("'token'\\s*:\\s*'[^']+'", "'token':'***'");

        return message;
    }

    private static String sanitizeApiResponse(String response) {
        if (response == null) return "null";

        if (response.length() > 1000) {
            response = response.substring(0, 1000) + "... [truncated]";
        }

        return sanitizeMessage(response);
    }
}