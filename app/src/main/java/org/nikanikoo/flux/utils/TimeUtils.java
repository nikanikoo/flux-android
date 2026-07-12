package org.nikanikoo.flux.utils;

import android.content.Context;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.FluxApplication;

public class TimeUtils {

    public static String formatTimeAgo(long timestamp) {
        if (timestamp <= 0) {
            Context context = FluxApplication.getInstance();
            if (context != null) {
                return context.getString(R.string.time_just_now);
            }
            return "just now";
        }

        Context context = FluxApplication.getInstance();
        if (context == null) {
            return formatTimeAgoFallback(timestamp);
        }

        long timeMs = timestamp * 1000;
        long nowMs = System.currentTimeMillis();
        long diffSeconds = (nowMs - timeMs) / 1000;

        if (diffSeconds < 0) {
            return context.getString(R.string.time_just_now);
        }

        if (diffSeconds < 60) {
            return context.getString(R.string.time_just_now);
        }

        if (diffSeconds < 3600) {
            int minutes = (int) (diffSeconds / 60);
            return context.getString(R.string.time_minutes_ago, minutes);
        }

        Calendar timeCal = Calendar.getInstance();
        timeCal.setTimeInMillis(timeMs);

        Calendar nowCal = Calendar.getInstance();
        nowCal.setTimeInMillis(nowMs);

        boolean isToday = nowCal.get(Calendar.YEAR) == timeCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == timeCal.get(Calendar.DAY_OF_YEAR);

        Calendar yesterdayCal = (Calendar) nowCal.clone();
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
        boolean isYesterday = yesterdayCal.get(Calendar.YEAR) == timeCal.get(Calendar.YEAR) &&
                yesterdayCal.get(Calendar.DAY_OF_YEAR) == timeCal.get(Calendar.DAY_OF_YEAR);

        Locale locale = getAppLocale(context);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
        String formattedTime = timeFormat.format(new Date(timeMs));

        if (isToday) {
            return context.getString(R.string.time_today_at, formattedTime);
        }

        if (isYesterday) {
            return context.getString(R.string.time_yesterday_at, formattedTime);
        }

        boolean isCurrentYear = nowCal.get(Calendar.YEAR) == timeCal.get(Calendar.YEAR);
        SimpleDateFormat dateFormat = new SimpleDateFormat("d MMMM", locale);
        String formattedDate = dateFormat.format(new Date(timeMs));

        if (isCurrentYear) {
            return context.getString(R.string.time_full_format, formattedDate, formattedTime);
        } else {
            return context.getString(R.string.time_full_format_year, formattedDate, timeCal.get(Calendar.YEAR), formattedTime);
        }
    }

    private static Locale getAppLocale(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales().get(0);
        } else {
            return context.getResources().getConfiguration().locale;
        }
    }

    private static String formatTimeAgoFallback(long timestamp) {
        long currentTime = System.currentTimeMillis() / 1000;
        long diff = currentTime - timestamp;

        if (diff < 0 || diff < 60) {
            return "just now";
        }

        if (diff < 3600) {
            long minutes = diff / 60;
            return minutes + " min ago";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM HH:mm", Locale.US);
        return sdf.format(new Date(timestamp * 1000));
    }
}
