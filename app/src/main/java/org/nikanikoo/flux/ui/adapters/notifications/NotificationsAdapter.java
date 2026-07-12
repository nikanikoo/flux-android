package org.nikanikoo.flux.ui.adapters.notifications;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.nikanikoo.flux.data.models.Notification;
import org.nikanikoo.flux.utils.ImageLoaderUtils;
import org.nikanikoo.flux.data.managers.NotificationsManager;
import org.nikanikoo.flux.R;

import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {
    private List<Notification> notifications;
    private Context context;
    private OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        void onAvatarClick(int userId, String userName);
    }

    public NotificationsAdapter(Context context, List<Notification> notifications) {
        this.context = context;
        this.notifications = notifications;
    }

    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        // Используем getBindingAdapterPosition() вместо position для корректной работы с кликами
        final int adapterPosition = holder.getBindingAdapterPosition();
        if (adapterPosition == RecyclerView.NO_POSITION) {
            return;
        }
        
        Notification notification = notifications.get(adapterPosition);
        
        System.out.println("NotificationsAdapter: Binding notification at position " + adapterPosition + 
                          " - type: " + notification.getType() + 
                          ", fromId: " + notification.getFromId() + 
                          ", fromName: " + notification.getFromName() + 
                          ", commentDataLoaded: " + notification.isCommentDataLoaded());
        
        // Установить иконку типа уведомления
        holder.typeIcon.setImageResource(notification.getTypeIcon());
        
        // Для comment_post и comment_photo загружаем данные комментария, если они еще не загружены
        if (("comment_post".equals(notification.getType()) || "comment_photo".equals(notification.getType())) && !notification.isCommentDataLoaded()) {
            NotificationsManager notificationsManager = NotificationsManager.getInstance(context);
            notificationsManager.loadCommentDataForNotification(notification, () -> {
                // Используем безопасный метод обновления с актуальной позицией
                safeNotifyItemChanged(adapterPosition);
            });
        }
        
        // Установить аватар отправителя
        ImageLoaderUtils.loadAvatar(notification.getFromPhoto(), holder.avatarImage);
        
        // Установить текст уведомления
        String notificationText = "";
        if (notification.getFromName() != null && !notification.getFromName().isEmpty()) {
            notificationText = notification.getFromName() + "\n" + notification.getReadableType();
        } else {
            notificationText = notification.getReadableType();
        }
        
        // Специальная обработка для mention, comment_post и comment_photo
        if ("mention".equals(notification.getType()) || "comment_post".equals(notification.getType()) || "comment_photo".equals(notification.getType())) {
            if (!notification.getText().isEmpty()) {
                // Для mention, comment_post и comment_photo показываем текст комментария/упоминания
                String displayText = notification.getText();
                if (displayText.length() > 100) {
                    displayText = displayText.substring(0, 100) + "...";
                }
                notificationText += "\n\"" + displayText + "\"";
            }
        } else if ("sent_gift".equals(notification.getType())) {
            // Для sent_gift не добавляем дополнительный текст, только "Имя отправил(а) вам подарок"
            // Текст уже сформирован выше
        } else {
            // Для других типов уведомлений показываем текст как раньше
            if (!notification.getText().isEmpty()) {
                notificationText += ": " + notification.getText();
            }
        }
        
        holder.notificationText.setText(notificationText);
        
        // Установить время
        holder.timeText.setText(notification.getDate());
        
        // Показать индикатор непрочитанного
        // Архивные уведомления всегда считаются прочитанными
        boolean shouldShowAsRead = notification.isRead() || notification.isArchived();
        holder.unreadIndicator.setVisibility(shouldShowAsRead ? View.GONE : View.VISIBLE);
        
        // Убираем полупрозрачность - все уведомления отображаются с полной непрозрачностью
        holder.itemView.setAlpha(1.0f);
        holder.notificationText.setAlpha(1.0f);
        holder.timeText.setAlpha(1.0f);
        
        // Отладочная информация для всех уведомлений comment_post
        if ("comment_post".equals(notification.getType())) {
            System.out.println("NotificationsAdapter: comment_post notification at position " + position + 
                             " - isRead: " + notification.isRead() + 
                             ", isArchived: " + notification.isArchived() +
                             ", shouldShowAsRead: " + shouldShowAsRead +
                             ", commentDataLoaded: " + notification.isCommentDataLoaded() +
                             ", id: " + notification.getId() +
                             ", fromName: " + notification.getFromName());
        }
        
        // Отладочная информация для архивных уведомлений
        if (notification.getId().startsWith("archived_")) {
            System.out.println("NotificationsAdapter: Archived notification '" + notification.getType() + 
                             "' isRead: " + notification.isRead() + 
                             ", isArchived: " + notification.isArchived() +
                             ", shouldShowAsRead: " + shouldShowAsRead +
                             ", indicator visibility: " + (shouldShowAsRead ? "GONE" : "VISIBLE") +
                             ", actual indicator visibility: " + holder.unreadIndicator.getVisibility());
        }
        
        // Обработка клика на аватарку - используем актуальную позицию
        holder.avatarImage.setOnClickListener(v -> {
            if (listener != null) {
                int clickPosition = holder.getBindingAdapterPosition();
                if (clickPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                
                Notification clickedNotification = notifications.get(clickPosition);
                int userId = clickedNotification.getFromId();
                String userName = clickedNotification.getFromName();
                
                System.out.println("NotificationsAdapter: Avatar clicked at position " + clickPosition + 
                                 " - userId: " + userId + ", userName: " + userName + ", type: " + clickedNotification.getType());
                System.out.println("NotificationsAdapter: Notification ID: " + clickedNotification.getId() + 
                                 ", commentDataLoaded: " + clickedNotification.isCommentDataLoaded());
                
                if (userId != 0 && userName != null && !userName.isEmpty() && !"Пользователь".equals(userName)) {
                    listener.onAvatarClick(userId, userName);
                } else {
                    System.out.println("NotificationsAdapter: Invalid user data for avatar click - userId: " + userId + ", userName: '" + userName + "'");
                }
            }
        });
        
        // Обработка клика на уведомление - используем актуальную позицию
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int clickPosition = holder.getBindingAdapterPosition();
                if (clickPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                
                Notification clickedNotification = notifications.get(clickPosition);
                System.out.println("NotificationsAdapter: Item clicked at position " + clickPosition + 
                                 " - type: " + clickedNotification.getType() + ", id: " + clickedNotification.getId());
                listener.onNotificationClick(clickedNotification);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void updateNotifications(List<Notification> newNotifications) {
        this.notifications = newNotifications;
        notifyDataSetChanged();
    }
    
    // Безопасное обновление конкретного элемента
    public void safeNotifyItemChanged(int position) {
        System.out.println("NotificationsAdapter: safeNotifyItemChanged called for position " + position);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (position >= 0 && position < notifications.size()) {
                Notification notification = notifications.get(position);
                System.out.println("NotificationsAdapter: Updating item at position " + position + 
                                 " - type: " + notification.getType() + 
                                 ", isRead: " + notification.isRead() + 
                                 ", commentDataLoaded: " + notification.isCommentDataLoaded() +
                                 ", id: " + notification.getId());
                notifyItemChanged(position);
            } else {
                System.out.println("NotificationsAdapter: Invalid position " + position + ", list size: " + notifications.size());
            }
        });
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        ImageView typeIcon;
        ImageView avatarImage;
        TextView notificationText;
        TextView timeText;
        View unreadIndicator;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.type_icon);
            avatarImage = itemView.findViewById(R.id.avatar_image);
            notificationText = itemView.findViewById(R.id.notification_text);
            timeText = itemView.findViewById(R.id.time_text);
            unreadIndicator = itemView.findViewById(R.id.unread_indicator);
        }
    }
}