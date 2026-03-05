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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_INCOMING = 0;
    private static final int TYPE_OUTGOING = 1;
    private static final int TYPE_DATE_HEADER = 2;

    private List<Object> items;
    private Context context;
    private OnMessageClickListener listener;

    public interface OnMessageClickListener {
        void onAvatarClick(int userId, String userName);
    }

    public MessagesAdapter(Context context, List<Message> messages) {
        this.context = context;
        this.items = new ArrayList<>();
        if (messages != null) {
            this.items.addAll(messages);
        }
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof Message) {
            return ((Message) item).isOut() ? TYPE_OUTGOING : TYPE_INCOMING;
        } else if (item instanceof DateHeader) {
            return TYPE_DATE_HEADER;
        }
        return TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_OUTGOING) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_outgoing, parent, false);
            return new OutgoingMessageViewHolder(view);
        } else if (viewType == TYPE_INCOMING) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_incoming, parent, false);
            return new IncomingMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_date_header, parent, false);
            return new DateHeaderViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);

        if (holder instanceof OutgoingMessageViewHolder) {
            bindOutgoingMessage((OutgoingMessageViewHolder) holder, (Message) item);
        } else if (holder instanceof IncomingMessageViewHolder) {
            bindIncomingMessage((IncomingMessageViewHolder) holder, (Message) item);
        } else if (holder instanceof DateHeaderViewHolder) {
            bindDateHeader((DateHeaderViewHolder) holder, (DateHeader) item);
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

    private void bindDateHeader(DateHeaderViewHolder holder, DateHeader dateHeader) {
        holder.dateText.setText(dateHeader.getText());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setMessages(List<Message> newMessages) {
        this.items.clear();
        this.items.addAll(groupMessagesByDate(newMessages));
        notifyDataSetChanged();
        System.out.println("MessagesAdapter: Set " + newMessages.size() + " messages");
    }

    public void addMessagesToTop(List<Message> newMessages) {
        if (newMessages != null && !newMessages.isEmpty()) {
            // Дедупликация по ID сообщения
            List<Message> uniqueMessages = new ArrayList<>();
            for (Message newMsg : newMessages) {
                boolean isDuplicate = false;
                for (Object existingMsg : this.items) {
                    if (existingMsg instanceof Message && ((Message) existingMsg).getId() == newMsg.getId()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    uniqueMessages.add(newMsg);
                }
            }

            if (!uniqueMessages.isEmpty()) {
                List<Object> itemsToInsert = groupMessagesByDate(uniqueMessages);
                
                // Если первый элемент - заголовок даты, и он совпадает с существующим, убираем дубликат
                if (!itemsToInsert.isEmpty() && itemsToInsert.get(0) instanceof DateHeader) {
                    if (!items.isEmpty() && items.get(0) instanceof DateHeader) {
                        DateHeader newHeader = (DateHeader) itemsToInsert.get(0);
                        DateHeader existingHeader = (DateHeader) items.get(0);
                        if (newHeader.getText().equals(existingHeader.getText())) {
                            itemsToInsert.remove(0);
                        }
                    }
                }
                
                if (!itemsToInsert.isEmpty()) {
                    this.items.addAll(0, itemsToInsert);
                    notifyItemRangeInserted(0, itemsToInsert.size());
                    System.out.println("MessagesAdapter: Added " + itemsToInsert.size() + " items to top");
                }
            }
        }
    }
    
    // Метод для добавления одного сообщения в конец списка
    public void addMessage(Message message) {
        if (message != null) {
            // Проверяем, что сообщение еще не добавлено
            boolean messageExists = false;
            for (Object existingMessage : this.items) {
                if (existingMessage instanceof Message && ((Message) existingMessage).getId() == message.getId()) {
                    messageExists = true;
                    break;
                }
            }

            if (!messageExists) {
                int position = items.size();

                if (position > 0) {
                    Object lastItem = items.get(position - 1);
                    if (lastItem instanceof Message) {
                        Message lastMessage = (Message) lastItem;
                        String currentDate = getDateKey(message.getDate());
                        String lastDate = getDateKey(lastMessage.getDate());

                        if (!currentDate.equals(lastDate)) {
                            DateHeader header = new DateHeader(getDateHeaderText(message.getDate()));
                            items.add(header);
                            notifyItemInserted(position);
                            position++;
                        }
                    }
                } else {
                    DateHeader header = new DateHeader(getDateHeaderText(message.getDate()));
                    items.add(header);
                    notifyItemInserted(0);
                    position = 1;
                }

                this.items.add(message);
                notifyItemInserted(position);
                System.out.println("MessagesAdapter: Added single message with ID " + message.getId());
            } else {
                System.out.println("MessagesAdapter: Message with ID " + message.getId() + " already exists, skipping");
            }
        }
    }

    private List<Object> groupMessagesByDate(List<Message> messages) {
        List<Object> result = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return result;
        }

        String lastDateKey = null;
        for (Message message : messages) {
            String currentDateKey = getDateKey(message.getDate());
            if (!currentDateKey.equals(lastDateKey)) {
                result.add(new DateHeader(getDateHeaderText(message.getDate())));
                lastDateKey = currentDateKey;
            }
            result.add(message);
        }

        return result;
    }

    private String getDateKey(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp * 1000);

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    private String getDateHeaderText(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp * 1000);

        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    public String getCurrentDateHeader(int firstVisiblePosition, int lastVisiblePosition) {
        if (firstVisiblePosition < 0 || lastVisiblePosition >= items.size() || firstVisiblePosition > lastVisiblePosition) {
            return null;
        }

        for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
            if (items.get(i) instanceof Message) {
                for (int j = i; j >= 0; j--) {
                    if (items.get(j) instanceof DateHeader) {
                        return ((DateHeader) items.get(j)).getText();
                    }
                }
                break;
            }
        }

        return null;
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

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;

        DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.date_text);
        }
    }

    /**
     * Класс для заголовка даты
     */
    private static class DateHeader {
        private final String text;

        DateHeader(String text) {
            this.text = text;
        }

        String getText() {
            return text;
        }
    }
}
