package org.nikanikoo.flux.ui.adapters.messages;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder> {
    private List<Conversation> conversations;
    private Context context;
    private OnConversationClickListener listener;

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
        void onAvatarClick(int userId, String userName);
    }

    public ConversationsAdapter(Context context, List<Conversation> conversations) {
        this.context = context;
        this.conversations = conversations;
    }

    public void setOnConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);
        
        holder.titleText.setText(conversation.getTitle());
        holder.lastMessageText.setText(ValidationUtils.SanitizeText(conversation.getLastMessage()));
        
        // Форматирование времени
        if (conversation.getLastMessageDate() > 0) {
            Date date = new Date(conversation.getLastMessageDate() * 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            holder.timeText.setText(sdf.format(date));
        }
        
        // Показать количество непрочитанных
        if (conversation.getUnreadCount() > 0) {
            holder.unreadBadge.setVisibility(View.VISIBLE);
            holder.unreadCount.setText(String.valueOf(conversation.getUnreadCount()));
        } else {
            holder.unreadBadge.setVisibility(View.GONE);
        }
        
        // Показать статус онлайн
        holder.onlineIndicator.setVisibility(conversation.isOnline() ? View.VISIBLE : View.GONE);
        
        // Показать галочку верификации
        android.util.Log.d("ConversationsAdapter", "Conversation " + conversation.getTitle() + " verified: " + conversation.isPeerVerified());
        if (holder.peerVerified != null) {
            holder.peerVerified.setVisibility(conversation.isPeerVerified() ? View.VISIBLE : View.GONE);
        }
        
        // Загрузить аватар
        if (conversation.getPeerPhoto() != null && !conversation.getPeerPhoto().isEmpty()) {
            Picasso.get()
                    .load(conversation.getPeerPhoto())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(holder.avatarImage);
        } else {
            holder.avatarImage.setImageResource(R.drawable.camera_200);
        }
        
        // Обработка клика на аватарку
        holder.avatarImage.setOnClickListener(v -> {
            if (listener != null && conversation.getPeerId() != 0) {
                listener.onAvatarClick(conversation.getPeerId(), conversation.getTitle());
            }
        });
        
        // Обработка клика на диалог
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversationClick(conversation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void updateConversations(List<Conversation> newConversations) {
        this.conversations = newConversations;
        notifyDataSetChanged();
    }
    
    public void setConversations(List<Conversation> newConversations) {
        this.conversations.clear();
        this.conversations.addAll(newConversations);
        notifyDataSetChanged();
        System.out.println("ConversationsAdapter: Set " + newConversations.size() + " conversations");
    }
    
    public void addConversations(List<Conversation> newConversations) {
        if (newConversations != null && !newConversations.isEmpty()) {
            int startPosition = this.conversations.size();
            
            // Дедупликация по ID диалога
            for (Conversation newConv : newConversations) {
                boolean isDuplicate = false;
                for (Conversation existingConv : this.conversations) {
                    if (existingConv.getPeerId() == newConv.getPeerId()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    this.conversations.add(newConv);
                }
            }
            
            int addedCount = this.conversations.size() - startPosition;
            if (addedCount > 0) {
                notifyItemRangeInserted(startPosition, addedCount);
                System.out.println("ConversationsAdapter: Added " + addedCount + " new conversations");
            }
        }
    }
    
    // Обновление онлайн статуса пользователя
    public void updateUserOnlineStatus(int userId, boolean isOnline) {
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conversation = conversations.get(i);
            if (conversation.getPeerId() == userId) {
                conversation.setOnline(isOnline);
                notifyItemChanged(i);
                break;
            }
        }
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        TextView titleText;
        TextView lastMessageText;
        TextView timeText;
        View unreadBadge;
        TextView unreadCount;
        View onlineIndicator;
        ImageView peerVerified;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.avatar_image);
            titleText = itemView.findViewById(R.id.title_text);
            lastMessageText = itemView.findViewById(R.id.last_message_text);
            timeText = itemView.findViewById(R.id.time_text);
            unreadBadge = itemView.findViewById(R.id.unread_badge);
            unreadCount = itemView.findViewById(R.id.unread_count);
            onlineIndicator = itemView.findViewById(R.id.online_indicator);
            peerVerified = itemView.findViewById(R.id.peer_verified);
        }
    }
}