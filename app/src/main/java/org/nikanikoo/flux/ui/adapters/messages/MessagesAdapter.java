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

import org.nikanikoo.flux.data.models.Message;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int TYPE_INCOMING = 0;
    private static final int TYPE_OUTGOING = 1;
    
    private List<Message> messages;
    private Context context;
    private OnMessageClickListener listener;

    public interface OnMessageClickListener {
        void onAvatarClick(int userId, String userName);
    }

    public MessagesAdapter(Context context, List<Message> messages) {
        this.context = context;
        this.messages = messages;
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isOut() ? TYPE_OUTGOING : TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_OUTGOING) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_outgoing, parent, false);
            return new OutgoingMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_incoming, parent, false);
            return new IncomingMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        
        if (holder instanceof OutgoingMessageViewHolder) {
            bindOutgoingMessage((OutgoingMessageViewHolder) holder, message);
        } else if (holder instanceof IncomingMessageViewHolder) {
            bindIncomingMessage((IncomingMessageViewHolder) holder, message);
        }
    }

    private void bindOutgoingMessage(OutgoingMessageViewHolder holder, Message message) {
        holder.messageText.setText(ValidationUtils.SanitizeText(message.getText()));
        
        // Форматирование времени
        Date date = new Date(message.getDate() * 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(date));
        
        // Показать статус прочтения
        if (message.isReadState()) {
            holder.readStatus.setVisibility(View.VISIBLE);
        } else {
            holder.readStatus.setVisibility(View.GONE);
        }
    }

    private void bindIncomingMessage(IncomingMessageViewHolder holder, Message message) {
        holder.messageText.setText(ValidationUtils.SanitizeText(message.getText()));
        
        // Форматирование времени
        Date date = new Date(message.getDate() * 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(date));
        
        // Загрузить аватар отправителя
        if (message.getUserPhoto() != null && !message.getUserPhoto().isEmpty()) {
            Picasso.get()
                    .load(message.getUserPhoto())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(holder.avatarImage);
        } else {
            holder.avatarImage.setImageResource(R.drawable.camera_200);
        }
        
        // Обработчик клика на аватарку
        holder.avatarImage.setOnClickListener(v -> {
            if (listener != null && message.getFromId() != 0) {
                listener.onAvatarClick(message.getFromId(), message.getUserName());
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<Message> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }
    
    public void setMessages(List<Message> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        notifyDataSetChanged();
        System.out.println("MessagesAdapter: Set " + newMessages.size() + " messages");
    }
    
    public void addMessagesToTop(List<Message> newMessages) {
        if (newMessages != null && !newMessages.isEmpty()) {
            // Дедупликация по ID сообщения
            List<Message> uniqueMessages = new ArrayList<>();
            for (Message newMsg : newMessages) {
                boolean isDuplicate = false;
                for (Message existingMsg : this.messages) {
                    if (existingMsg.getId() == newMsg.getId()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    uniqueMessages.add(newMsg);
                }
            }
            
            if (!uniqueMessages.isEmpty()) {
                this.messages.addAll(0, uniqueMessages);
                notifyItemRangeInserted(0, uniqueMessages.size());
                System.out.println("MessagesAdapter: Added " + uniqueMessages.size() + " messages to top");
            }
        }
    }
    
    // Метод для добавления одного сообщения в конец списка
    public void addMessage(Message message) {
        if (message != null) {
            // Проверяем, что сообщение еще не добавлено
            boolean messageExists = false;
            for (Message existingMessage : this.messages) {
                if (existingMessage.getId() == message.getId()) {
                    messageExists = true;
                    break;
                }
            }
            
            if (!messageExists) {
                this.messages.add(message);
                notifyItemInserted(this.messages.size() - 1);
                System.out.println("MessagesAdapter: Added single message with ID " + message.getId());
            } else {
                System.out.println("MessagesAdapter: Message with ID " + message.getId() + " already exists, skipping");
            }
        }
    }

    static class OutgoingMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timeText;
        ImageView readStatus;

        OutgoingMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            timeText = itemView.findViewById(R.id.time_text);
            readStatus = itemView.findViewById(R.id.read_status);
        }
    }

    static class IncomingMessageViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        TextView messageText;
        TextView timeText;

        IncomingMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.avatar_image);
            messageText = itemView.findViewById(R.id.message_text);
            timeText = itemView.findViewById(R.id.time_text);
        }
    }
}