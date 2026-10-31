package org.nikanikoo.flux.ui.adapters.messages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.FluxApplication;
import org.nikanikoo.flux.data.models.Message;
import org.nikanikoo.flux.data.models.MessageAttachment;
import org.nikanikoo.flux.data.models.MessageReference;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.utils.MentionUtils;
import org.nikanikoo.flux.utils.SafeLinkMovementMethod;
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
        void onReplyClick(int messageId);
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
        String sanitizedText = ValidationUtils.SanitizeText(message.getText());
        holder.messageText.setText(MentionUtils.formatMentions(sanitizedText, (id, name, isGroup) -> {
            if (listener != null) {
                listener.onAvatarClick(isGroup ? -id : id, name);
            }
        },true));
        Linkify.addLinks(holder.messageText, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        holder.messageText.setMovementMethod(SafeLinkMovementMethod.getInstance());
        
        // Устанавливаем цвет ссылок таким же, как у обычного текста
        holder.messageText.setLinkTextColor(holder.messageText.getCurrentTextColor());
        bindReferences(holder.replyPreview, message);
        bindAttachments(holder.attachments, message.getAttachments());
        
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
        String sanitizedText = ValidationUtils.SanitizeText(message.getText());
        holder.messageText.setText(MentionUtils.formatMentions(sanitizedText, (id, name, isGroup) -> {
            if (listener != null) {
                listener.onAvatarClick(isGroup ? -id : id, name);
            }
        }, true));
        Linkify.addLinks(holder.messageText, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        holder.messageText.setMovementMethod(SafeLinkMovementMethod.getInstance());
        
        // Устанавливаем цвет ссылок таким же, как у обычного текста
        holder.messageText.setLinkTextColor(holder.messageText.getCurrentTextColor());
        bindReferences(holder.replyPreview, message);
        bindAttachments(holder.attachments, message.getAttachments());
        
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

    private void bindAttachments(LinearLayout container, List<MessageAttachment> attachments) {
        container.removeAllViews();
        if (attachments == null) return;
        for (MessageAttachment attachment : attachments) {
            if ("wall".equals(attachment.getType())) {
                addWallCard(container, attachment);
            } else if ("doc".equals(attachment.getType())) {
                addDocumentCard(container, attachment);
            } else if (attachment.getImageUrl() != null && !attachment.getImageUrl().isEmpty()) {
                ImageView image = new ImageView(context);
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(220), dp(160));
                imageParams.topMargin = dp(8);
                image.setLayoutParams(imageParams);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setContentDescription(attachment.getTitle());
                Picasso.get().load(attachment.getImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .fit().centerInside().into(image);
                image.setOnClickListener(v -> {
                    if ("photo".equals(attachment.getType())) {
                        org.nikanikoo.flux.ui.activities.PhotoViewerActivity.start(
                                context, java.util.Collections.singletonList(attachment.getImageUrl()), 0, "");
                    } else if ("video".equals(attachment.getType()) && attachment.getVideo() != null
                            && attachment.getVideo().getPlayer() != null
                            && !attachment.getVideo().getPlayer().isEmpty()) {
                        org.nikanikoo.flux.ui.activities.VideoPlayerActivity.start(context, attachment.getVideo());
                    }
                });
                container.addView(image);
            } else {
                TextView label = new TextView(context);
                String text = attachment.getTitle();
                if (attachment.getSubtitle() != null && !attachment.getSubtitle().isEmpty()) {
                    text += "\n" + attachment.getSubtitle();
                }
                label.setText(text);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                label.setPadding(dp(10), dp(8), dp(10), dp(8));
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                labelParams.topMargin = dp(8);
                label.setLayoutParams(labelParams);
                label.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
                container.addView(label);
            }
        }
    }

    private void addDocumentCard(LinearLayout container, MessageAttachment attachment) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(10);
        card.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x1F000000);
        background.setCornerRadius(dp(10));
        card.setBackground(background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(8);
        card.setLayoutParams(cardParams);

        TextView name = new TextView(context);
        name.setText(attachment.getTitle().isEmpty() ? "Документ" : attachment.getTitle());
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        card.addView(name);

        if (attachment.getImageUrl() != null && !attachment.getImageUrl().isEmpty()) {
            ImageView preview = new ImageView(context);
            preview.setLayoutParams(new LinearLayout.LayoutParams(dp(220), dp(130)));
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Picasso.get().load(attachment.getImageUrl()).fit().centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery).into(preview);
            LinearLayout.LayoutParams previewParams = (LinearLayout.LayoutParams) preview.getLayoutParams();
            previewParams.topMargin = dp(6);
            card.addView(preview, previewParams);
        }

        TextView details = new TextView(context);
        details.setText(attachment.getSubtitle().isEmpty() ? "Открыть документ" : attachment.getSubtitle().toUpperCase(Locale.getDefault()) + " · Открыть документ");
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailsParams.topMargin = dp(4);
        card.addView(details, detailsParams);
        card.setOnClickListener(v -> {
            if (attachment.isGifDocument()) {
                org.nikanikoo.flux.ui.activities.PhotoViewerActivity.startGif(
                        context, attachment.getUrl(), attachment.getTitle(),
                        attachment.getOwnerId(), attachment.getId(), attachment.getAccessKey());
            } else {
                openUrl(attachment.getUrl());
            }
        });
        container.addView(card);
    }

    private void bindReferences(TextView view, Message message) {
        StringBuilder text = new StringBuilder();
        MessageReference reply = message.getReply();
        if (reply != null) {
            appendReference(text, "Ответ", reply);
        }
        for (MessageReference forwarded : message.getForwardedMessages()) {
            appendReference(text, "Переслано", forwarded);
        }
        if (text.length() == 0) {
            view.setVisibility(View.GONE);
            view.setOnClickListener(null);
        } else {
            view.setText(text.toString().trim());
            view.setVisibility(View.VISIBLE);
            if (reply != null && reply.getId() > 0) {
                view.setOnClickListener(v -> {
                    if (listener != null) listener.onReplyClick(reply.getId());
                });
            } else {
                view.setOnClickListener(null);
            }
        }
    }

    private void appendReference(StringBuilder text, String kind, MessageReference reference) {
        text.append(kind);
        if (reference.getSender() != null && !reference.getSender().isEmpty()) {
            text.append(" · ").append(reference.getSender());
        }
        text.append('\n');
        String snippet = reference.getText();
        text.append(snippet == null || snippet.isEmpty() ? "Сообщение" : snippet).append('\n');
    }

    private void addWallCard(LinearLayout container, MessageAttachment attachment) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(10);
        card.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x1F000000);
        background.setCornerRadius(dp(10));
        card.setBackground(background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(8);
        card.setLayoutParams(cardParams);

        TextView author = new TextView(context);
        author.setText(attachment.getTitle().isEmpty() ? "Запись" : attachment.getTitle());
        author.setTypeface(null, android.graphics.Typeface.BOLD);
        author.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        card.addView(author);

        if (attachment.getSubtitle() != null && !attachment.getSubtitle().isEmpty()) {
            TextView body = new TextView(context);
            body.setText(attachment.getSubtitle());
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setMaxLines(5);
            body.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyParams.topMargin = dp(4);
            card.addView(body, bodyParams);
        }

        if (attachment.getImageUrl() != null && !attachment.getImageUrl().isEmpty()) {
            ImageView preview = new ImageView(context);
            preview.setLayoutParams(new LinearLayout.LayoutParams(dp(220), dp(130)));
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Picasso.get().load(attachment.getImageUrl()).fit().centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery).into(preview);
            LinearLayout.LayoutParams previewParams = (LinearLayout.LayoutParams) preview.getLayoutParams();
            previewParams.topMargin = dp(6);
            card.addView(preview, previewParams);
        }
        card.setOnClickListener(v -> openUrl(attachment.getTargetUrl()));
        container.addView(card);
    }

    private void openUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) return;
        String url = pathOrUrl;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            String baseUrl = OpenVKApi.getInstance(context).getBaseUrl();
            url = baseUrl + (url.startsWith("/") ? url : "/" + url);
        }
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
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
        TextView replyPreview;
        LinearLayout attachments;
        TextView timeText;
        ImageView readStatus;

        OutgoingMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            replyPreview = itemView.findViewById(R.id.reply_preview);
            attachments = itemView.findViewById(R.id.message_attachments);
            timeText = itemView.findViewById(R.id.time_text);
            readStatus = itemView.findViewById(R.id.read_status);
        }
    }

    static class IncomingMessageViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        TextView messageText;
        TextView replyPreview;
        LinearLayout attachments;
        TextView timeText;

        IncomingMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.avatar_image);
            messageText = itemView.findViewById(R.id.message_text);
            replyPreview = itemView.findViewById(R.id.reply_preview);
            attachments = itemView.findViewById(R.id.message_attachments);
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

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof OutgoingMessageViewHolder) {
            OutgoingMessageViewHolder outHolder = (OutgoingMessageViewHolder) holder;
            if (outHolder.messageText != null) {
                outHolder.messageText.clearFocus();
            }
        } else if (holder instanceof IncomingMessageViewHolder) {
            IncomingMessageViewHolder inHolder = (IncomingMessageViewHolder) holder;
            if (inHolder.messageText != null) {
                inHolder.messageText.clearFocus();
            }
        }
    }

    public void notifyMessageChanged(Message message) {
        int position = items.indexOf(message);
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    public int findMessagePosition(int messageId) {
        for (int position = 0; position < items.size(); position++) {
            Object item = items.get(position);
            if (item instanceof Message && ((Message) item).getId() == messageId) {
                return position;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public int markOutgoingMessagesReadThrough(int conversationMessageId) {
        if (conversationMessageId <= 0) return 0;
        int updated = 0;
        for (int position = 0; position < items.size(); position++) {
            Object item = items.get(position);
            if (item instanceof Message) {
                Message message = (Message) item;
                int localId = message.getConversationMessageId();
                if (message.isOut() && localId > 0 && localId <= conversationMessageId && !message.isReadState()) {
                    message.setReadState(true);
                    notifyItemChanged(position);
                    updated++;
                }
            }
        }
        return updated;
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof OutgoingMessageViewHolder) {
            OutgoingMessageViewHolder outHolder = (OutgoingMessageViewHolder) holder;
            if (outHolder.messageText != null) {
                outHolder.messageText.clearFocus();
            }
        } else if (holder instanceof IncomingMessageViewHolder) {
            IncomingMessageViewHolder inHolder = (IncomingMessageViewHolder) holder;
            if (inHolder.messageText != null) {
                inHolder.messageText.clearFocus();
            }
        }
    }
}
