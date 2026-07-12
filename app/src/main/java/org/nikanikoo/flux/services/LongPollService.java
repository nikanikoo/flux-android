package org.nikanikoo.flux.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;

import androidx.core.app.NotificationCompat;

public class LongPollService extends Service {
    private static final String TAG = "LongPollService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "longpoll_channel";
    
    private LongPollManager longPollManager;
    private MessageNotificationManager messageNotificationManager;
    private LongPollManager.OnMessageEventListener messageEventListener;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        createNotificationChannel();
        longPollManager = LongPollManager.getInstance(this);
        messageNotificationManager = MessageNotificationManager.getInstance(this);
        
        // Настраиваем обработчик новых сообщений (уведомления обрабатываются в MainActivity)
        messageEventListener = new LongPollManager.OnMessageEventListener() {
            @Override
            public void onNewMessage(int messageId, int peerId, long timestamp, String text, int fromId, boolean isOut) {
                Log.d(TAG, "LongPoll event - messageId: " + messageId + ", peerId: " + peerId + 
                          ", fromId: " + fromId + ", isOut: " + isOut + ", timestamp: " + timestamp + ", text: " + text);
                
                // Уведомления теперь обрабатываются в MainActivity, здесь только логируем
                Log.d(TAG, "Message event processed by service (notifications handled by MainActivity)");
            }

            @Override
            public void onMessageRead(int peerId, int localId) {
                Log.d(TAG, "Message read event - peerId: " + peerId + ", localId: " + localId);
                // Обработка чтения сообщений теперь в MainActivity
            }

            @Override
            public void onMessageEdit(int messageId, int peerId, String newText) {
                Log.d(TAG, "Message edit event - messageId: " + messageId + ", peerId: " + peerId);
                // Не обрабатываем в сервисе
            }
        };
        longPollManager.addMessageEventListener(messageEventListener);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        try {
            // Создаем уведомление для foreground service
            Notification notification = createForegroundNotification();
            startForeground(NOTIFICATION_ID, notification);
            
            // Запускаем LongPoll
            if (!longPollManager.isRunning()) {
                longPollManager.start();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            // Останавливаем сервис, если не можем запустить foreground
            stopSelf();
            return START_NOT_STICKY;
        }
        
        return START_STICKY; // Перезапускать сервис при убийстве системой
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        // Remove message event listener to prevent memory leak
        if (longPollManager != null && messageEventListener != null) {
            longPollManager.removeMessageEventListener(messageEventListener);
            messageEventListener = null;
        }
        
        if (longPollManager != null) {
            longPollManager.stop();
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Не поддерживаем binding
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.w(TAG, "Service timed out (fgsType=" + fgsType + "), stopping to prevent crash");
            stopSelf();
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LongPoll Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the app connected to receive messages");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OpenVK Flux")
                .setContentText("Connected to receive messages")
                .setSmallIcon(R.drawable.ic_chat_bubble)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    
    // Статические методы для управления сервисом
    public static void start(Context context) {
        // Проверяем разрешения для Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (context.checkSelfPermission("android.permission.FOREGROUND_SERVICE_SPECIAL_USE") 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "FOREGROUND_SERVICE_SPECIAL_USE permission not granted");
                return;
            }
        }
        
        Intent intent = new Intent(context, LongPollService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
        }
    }
    
    public static void stop(Context context) {
        Intent intent = new Intent(context, LongPollService.class);
        context.stopService(intent);
    }
}