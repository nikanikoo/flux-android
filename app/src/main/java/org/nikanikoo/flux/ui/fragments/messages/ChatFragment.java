package org.nikanikoo.flux.ui.fragments.messages;

import android.os.Bundle;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.widget.CheckBox;
import com.squareup.picasso.Picasso;

import com.google.android.material.card.MaterialCardView;

import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.models.Message;
import org.nikanikoo.flux.services.LongPollManager;
import org.nikanikoo.flux.services.MessageNotificationManager;
import org.nikanikoo.flux.ui.adapters.messages.MessagesAdapter;
import org.nikanikoo.flux.data.managers.MessagesManager;
import org.nikanikoo.flux.data.managers.PhotoUploadManager;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.managers.VideoManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.data.models.Video;
import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.ChatActivity;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatFragment extends Fragment implements MessagesAdapter.OnMessageClickListener,
        LongPollManager.OnMessageEventListener, LongPollManager.OnTypingEventListener {

    private static class SelectableMedia {
        final String attachmentId;
        final String title;
        final String subtitle;
        final String previewUrl;

        SelectableMedia(String attachmentId, String title, String subtitle, String previewUrl) {
            this.attachmentId = attachmentId;
            this.title = title;
            this.subtitle = subtitle;
            this.previewUrl = previewUrl;
        }
    }
    
    private static final String ARG_PEER_ID = "peer_id";
    private static final String ARG_TITLE = "title";
    
    private int peerId;
    private String title;
    private RecyclerView recyclerView;
    private MessagesAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText messageInput;
    private ImageView sendButton;
    private ImageView attachButton;
    private View pendingAttachmentPreview;
    private android.widget.LinearLayout pendingAttachmentList;
    private ActivityResultLauncher<String> photoPicker;
    private final List<String> pendingPhotoAttachments = new ArrayList<>();
    private final List<Uri> pendingPhotoUris = new ArrayList<>();
    private final List<String> pendingMediaAttachments = new ArrayList<>();
    private final List<String> pendingMediaTitles = new ArrayList<>();
    private final List<String> pendingMediaPreviews = new ArrayList<>();
    private boolean photoUploadInProgress;
    private int recyclerBaseBottomPadding;
    private int pendingReplyMessageId;
    private MessagesManager messagesManager;
    private List<Message> messages;

    // Для плашки даты
    private MaterialCardView dateHeaderCard;
    private TextView dateHeaderText;
    private Handler hideDateHeaderHandler;
    private Runnable hideDateHeaderRunnable;
    private static final long DATE_HEADER_HIDE_DELAY = 2000; // 2 секунды

    // Для бесконечного скролла
    private LinearLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;

    // Для тайпинга и LongPoll
    private LongPollManager longPollManager;
    private Handler typingTimeoutHandler;
    private Runnable typingTimeoutRunnable;
    private long lastTypingSendTime = 0;

    public static ChatFragment newInstance(int peerId, String title) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PEER_ID, peerId);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }
    
    public static ChatFragment newInstance(int peerId, String title, int fromId) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PEER_ID, peerId);
        args.putString(ARG_TITLE, title);
        args.putInt("from_id", fromId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), this::onPhotosPicked);
        if (getArguments() != null) {
            peerId = getArguments().getInt(ARG_PEER_ID);
            title = getArguments().getString(ARG_TITLE);
        }
        if (savedInstanceState != null) {
            pendingPhotoAttachments.addAll(savedInstanceState.getStringArrayList("pending_photo_attachments") != null
                    ? savedInstanceState.getStringArrayList("pending_photo_attachments") : new ArrayList<>());
            ArrayList<String> uris = savedInstanceState.getStringArrayList("pending_photo_uris");
            if (uris != null) for (String uri : uris) pendingPhotoUris.add(Uri.parse(uri));
            ArrayList<String> mediaAttachments = savedInstanceState.getStringArrayList("pending_media_attachments");
            if (mediaAttachments != null) pendingMediaAttachments.addAll(mediaAttachments);
            ArrayList<String> mediaTitles = savedInstanceState.getStringArrayList("pending_media_titles");
            if (mediaTitles != null) pendingMediaTitles.addAll(mediaTitles);
            ArrayList<String> mediaPreviews = savedInstanceState.getStringArrayList("pending_media_previews");
            if (mediaPreviews != null) pendingMediaPreviews.addAll(mediaPreviews);
            while (pendingMediaPreviews.size() < pendingMediaAttachments.size()) pendingMediaPreviews.add("");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("pending_photo_attachments", new ArrayList<>(pendingPhotoAttachments));
        ArrayList<String> uris = new ArrayList<>();
        for (Uri uri : pendingPhotoUris) uris.add(uri.toString());
        outState.putStringArrayList("pending_photo_uris", uris);
        outState.putStringArrayList("pending_media_attachments", new ArrayList<>(pendingMediaAttachments));
        outState.putStringArrayList("pending_media_titles", new ArrayList<>(pendingMediaTitles));
        outState.putStringArrayList("pending_media_previews", new ArrayList<>(pendingMediaPreviews));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        
        initViews(view);
        setupRecyclerView();
        setupEndlessScroll();
        setupSendButton();
        loadMessages(true);
        
        longPollManager = LongPollManager.getInstance(requireContext());
        typingTimeoutHandler = new Handler(Looper.getMainLooper());
        typingTimeoutRunnable = () -> setActionBarSubtitle(null);

        // Установить заголовок
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(title);
        }
        
        // Отменяем уведомления для этого чата
        // Нужно получить fromId из аргументов или использовать peerId
        int fromId = getArguments() != null ? getArguments().getInt("from_id", peerId) : peerId;
        MessageNotificationManager.getInstance(requireContext()).cancelNotification(fromId);
        
        // Устанавливаем текущий открытый чат
        MessageNotificationManager.setCurrentOpenChat(peerId);
        
        Log.d("ChatFragment", "Opened chat with peerId: " + peerId + ", cancelling notifications for fromId: " + fromId);
        
        return view;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (hideDateHeaderHandler != null) {
            hideDateHeaderHandler.removeCallbacksAndMessages(null);
        }

        // Обновляем список чатов при выходе из чата (на фоне)
        refreshConversationsListBackground();
        
        // Очищаем текущий открытый чат при закрытии фрагмента
        if (MessageNotificationManager.getCurrentOpenChat() == peerId) {
            MessageNotificationManager.clearCurrentOpenChat();
            Log.d("ChatFragment", "Cleared current open chat for peerId: " + peerId);
        }
        
        // Очищаем все scroll listeners
        if (recyclerView != null) {
            recyclerView.clearOnScrollListeners();
        }
        
        // Восстанавливаем заголовок
        if (getActivity() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerBaseBottomPadding = recyclerView.getPaddingBottom();
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        messageInput = view.findViewById(R.id.message_input);
        sendButton = view.findViewById(R.id.send_button);
        attachButton = view.findViewById(R.id.attach_button);
        pendingAttachmentPreview = view.findViewById(R.id.pending_attachment_preview);
        pendingAttachmentList = view.findViewById(R.id.pending_attachment_list);
        dateHeaderCard = view.findViewById(R.id.date_header_card);
        dateHeaderText = view.findViewById(R.id.date_header_text);
        messagesManager = MessagesManager.getInstance(requireContext());
        messages = new ArrayList<>();
        updatePendingAttachmentPreview();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            scrollListener.resetState();
            loadMessages(true);
        });
    }

    private void setupRecyclerView() {
        adapter = new MessagesAdapter(requireContext(), messages);
        adapter.setOnMessageClickListener(this);
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        hideDateHeaderHandler = new Handler(Looper.getMainLooper());
        hideDateHeaderRunnable = this::hideDateHeader;

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();

                if (firstVisibleItem >= 0 && lastVisibleItem >= 0) {
                    updateDateHeader(firstVisibleItem, lastVisibleItem);
                }
            }
        });
    }
    
    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(Constants.Api.MESSAGES_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                System.out.println("ChatFragment: Loading more messages, offset: " + offset);
                loadMessages(false);
            }
        };
        
        // Добавляем дополнительный слушатель для загрузки истории при скролле вверх
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private long lastLoadTime = 0;
            
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Загружаем историю при скролле вверх
                if (dy < 0 && paginationHelper.canLoadMore()) {
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    int totalItemCount = layoutManager.getItemCount();
                    
                    // Проверяем, что мы близко к началу списка
                    if (firstVisibleItemPosition <= 5 && totalItemCount > 0) {
                        long currentTime = System.currentTimeMillis();
                        
                        // Предотвращаем частые запросы
                        if (currentTime - lastLoadTime > 1000) {
                            System.out.println("ChatFragment: Loading history at top, first visible: " + firstVisibleItemPosition);
                            loadMessages(false);
                            lastLoadTime = currentTime;
                        }
                    }
                }
            }
        });
        
        recyclerView.addOnScrollListener(scrollListener);
    }

    /**
     * Обновление плашки с датой
     */
    private void updateDateHeader(int firstVisibleItem, int lastVisibleItem) {
        String dateText = adapter.getCurrentDateHeader(firstVisibleItem, lastVisibleItem);
        if (dateText != null) {
            showDateHeader(dateText);
        }
    }

    private void showDateHeader(String text) {
        if (dateHeaderCard == null || dateHeaderText == null) return;

        hideDateHeaderHandler.removeCallbacks(hideDateHeaderRunnable);

        if (!text.equals(dateHeaderText.getText().toString())) {
            dateHeaderText.setText(text);
        }

        if (dateHeaderCard.getVisibility() != View.VISIBLE) {
            dateHeaderCard.setVisibility(View.VISIBLE);
            dateHeaderCard.setAlpha(1.0f);
        }

        hideDateHeaderHandler.postDelayed(hideDateHeaderRunnable, DATE_HEADER_HIDE_DELAY);
    }

    private void hideDateHeader() {
        if (dateHeaderCard == null) return;

        dateHeaderCard.animate()
                .alpha(0.0f)
                .setDuration(200)
                .withEndAction(() -> {
                    dateHeaderCard.setVisibility(View.GONE);
                    dateHeaderCard.setAlpha(1.0f);
                });
    }

    private void setupSendButton() {
        sendButton.setOnClickListener(v -> sendMessage());
        attachButton.setOnClickListener(v -> {
            showAttachmentMenu();
        });
        
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTypingSendTime > 5000) {
                    lastTypingSendTime = currentTime;
                    sendTypingStatus();
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void showAttachmentMenu() {
        PopupMenu menu = new PopupMenu(requireContext(), attachButton);
        menu.getMenu().add(0, 1, 0, R.string.attach_photo_action);
        menu.getMenu().add(0, 2, 1, R.string.attach_video_action);
        menu.getMenu().add(0, 3, 2, R.string.attach_audio_action);
        menu.getMenu().add(0, 4, 3, R.string.attach_document_action);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    photoPicker.launch("image/*");
                    return true;
                case 2:
                    loadVideosForAttachment();
                    return true;
                case 3:
                    loadAudiosForAttachment();
                    return true;
                case 4:
                    loadDocumentsForAttachment();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void loadVideosForAttachment() {
        VideoManager.getInstance(requireContext()).getVideos(0, 0, 100, new VideoManager.VideoCallback() {
            @Override
            public void onSuccess(List<Video> videos, int totalCount) {
                List<SelectableMedia> media = new ArrayList<>();
                for (Video video : videos) {
                    String id = "video" + video.getOwnerId() + "_" + video.getId()
                            + (video.getAccessKey() == null || video.getAccessKey().isEmpty()
                            ? "" : "_" + video.getAccessKey());
                    media.add(new SelectableMedia(id, video.getTitle(), video.getFormattedDuration(),
                            video.getImage() != null && !video.getImage().isEmpty() ? video.getImage() : video.getFirstFrame()));
                }
                showMediaChoices(R.string.attach_video_action, media);
            }

            @Override
            public void onError(String error) {
                showAttachmentError(error);
            }
        });
    }

    private void loadAudiosForAttachment() {
        AudioManager.getInstance(requireContext()).getAudio(0, 0, 100, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> audios, int totalCount) {
                List<SelectableMedia> media = new ArrayList<>();
                for (Audio audio : audios) {
                    media.add(new SelectableMedia("audio" + audio.getOwnerId() + "_" + audio.getId(),
                            audio.getFullTitle(), audio.getFormattedDuration(), audio.getCoverUrl()));
                }
                showMediaChoices(R.string.attach_audio_action, media);
            }

            @Override
            public void onError(String error) {
                showAttachmentError(error);
            }
        });
    }

    private void loadDocumentsForAttachment() {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("count", "100");
        OpenVKApi.getInstance(requireContext()).callMethod("docs.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                try {
                    org.json.JSONObject result = response.getJSONObject("response");
                    org.json.JSONArray items = result.optJSONArray("items");
                    List<SelectableMedia> media = new ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            org.json.JSONObject document = items.optJSONObject(i);
                            if (document == null) continue;
                            String id = "doc" + document.optInt("owner_id") + "_" + document.optInt("id");
                            String accessKey = document.optString("access_key", "");
                            if (!accessKey.isEmpty()) id += "_" + accessKey;
                            String previewUrl = "";
                            org.json.JSONObject preview = document.optJSONObject("preview");
                            if (preview != null) {
                                previewUrl = org.nikanikoo.flux.utils.ImageUtils.extractOptimalImageUrl(
                                        preview.optJSONObject("photo"));
                            }
                            String extension = document.optString("ext", "").toUpperCase(java.util.Locale.ROOT);
                            String size = android.text.format.Formatter.formatShortFileSize(
                                    requireContext(), document.optLong("size", 0));
                            String subtitle = extension.isEmpty() ? size : extension + " · " + size;
                            media.add(new SelectableMedia(id,
                                    document.optString("title", getString(R.string.document)), subtitle, previewUrl));
                        }
                    }
                    showMediaChoices(R.string.attach_document_action, media);
                } catch (Exception e) {
                    showAttachmentError(e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                showAttachmentError(error);
            }
        });
    }

    private void showMediaChoices(int titleRes, List<SelectableMedia> media) {
        if (!isAdded()) return;
        if (media.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_media_to_attach, Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        android.widget.LinearLayout rows = new android.widget.LinearLayout(requireContext());
        rows.setOrientation(android.widget.LinearLayout.VERTICAL);
        rows.setPadding(dp(8), dp(4), dp(8), dp(4));
        scrollView.addView(rows);
        boolean[] checked = new boolean[media.size()];

        for (int i = 0; i < media.size(); i++) {
            final int index = i;
            SelectableMedia item = media.get(i);
            MaterialCardView card = new MaterialCardView(requireContext());
            android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
            cardParams.setMargins(dp(3), 0, dp(3), dp(7));
            card.setLayoutParams(cardParams);
            card.setRadius(dp(12));
            card.setCardElevation(dp(1));
            card.setClickable(true);
            card.setFocusable(true);

            android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(6), dp(12), dp(6));
            CheckBox checkBox = new CheckBox(requireContext());
            row.addView(checkBox, new android.widget.LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView preview = new ImageView(requireContext());
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackgroundResource(R.drawable.avatar_circle);
            android.widget.LinearLayout.LayoutParams previewParams = new android.widget.LinearLayout.LayoutParams(dp(76), dp(60));
            previewParams.setMargins(0, 0, dp(12), 0);
            row.addView(preview, previewParams);
            if (org.nikanikoo.flux.utils.ImageUtils.isValidImageUrl(item.previewUrl)) {
                Picasso.get().load(item.previewUrl).fit().centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder).into(preview);
            } else {
                preview.setImageResource(R.drawable.ic_image_placeholder);
            }

            android.widget.LinearLayout textColumn = new android.widget.LinearLayout(requireContext());
            textColumn.setOrientation(android.widget.LinearLayout.VERTICAL);
            textColumn.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView title = new TextView(requireContext());
            title.setText(item.title);
            title.setTextSize(14);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            textColumn.addView(title);
            TextView subtitle = new TextView(requireContext());
            subtitle.setText(item.subtitle);
            subtitle.setTextSize(12);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            subtitle.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    subtitle, com.google.android.material.R.attr.colorOnSurfaceVariant));
            textColumn.addView(subtitle);
            row.addView(textColumn, new android.widget.LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
            card.addView(row);

            View.OnClickListener toggle = v -> {
                checked[index] = !checked[index];
                checkBox.setChecked(checked[index]);
            };
            checkBox.setOnCheckedChangeListener((button, isChecked) -> checked[index] = isChecked);
            card.setOnClickListener(toggle);
            title.setOnClickListener(toggle);
            subtitle.setOnClickListener(toggle);
            rows.addView(card);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setView(scrollView)
                .setPositiveButton(R.string.repost_attach, (dialog, which) -> {
                    for (int i = 0; i < checked.length; i++) {
                        SelectableMedia item = media.get(i);
                        if (checked[i] && !pendingMediaAttachments.contains(item.attachmentId)) {
                            pendingMediaAttachments.add(item.attachmentId);
                            pendingMediaTitles.add(item.title);
                            pendingMediaPreviews.add(item.previewUrl != null ? item.previewUrl : "");
                        }
                    }
                    updatePendingAttachmentPreview();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showAttachmentError(String error) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), getString(R.string.error) + ": " + error,
                Toast.LENGTH_LONG).show();
    }

    private void loadMessages(boolean isRefresh) {
        if (!paginationHelper.canLoadMore() && !isRefresh) {
            System.out.println("ChatFragment: Cannot load more, skipping");
            return;
        }
        
        if (isRefresh) {
            swipeRefreshLayout.setRefreshing(true);
        }
        
        int offset = isRefresh ? 0 : paginationHelper.getCurrentOffset();
        System.out.println("ChatFragment: Loading messages, offset: " + offset + ", isRefresh: " + isRefresh);
        
        messagesManager.getHistory(peerId, Constants.Api.MESSAGES_PER_PAGE, offset, new MessagesManager.MessagesCallback() {
            @Override
            public void onSuccess(List<Message> loadedMessages) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        System.out.println("ChatFragment: Loaded " + loadedMessages.size() + " messages");
                        
                        paginationHelper.onDataLoaded(loadedMessages.size());
                        
                        // Сообщения приходят в обратном порядке, переворачиваем
                        Collections.reverse(loadedMessages);
                        
                        boolean shouldScrollToBottom = false;
                        
                        if (isRefresh) {
                            adapter.setMessages(loadedMessages);
                            shouldScrollToBottom = true;
                        } else {
                            // Для истории добавляем в начало списка
                            adapter.addMessagesToTop(loadedMessages);
                        }

                        tryScrollToPendingReply();
                        
                        swipeRefreshLayout.setRefreshing(false);

                        if (shouldScrollToBottom && adapter.getItemCount() > 0) {
                            recyclerView.post(() ->
                                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1)
                            );
                        }

                        // Помечаем сообщения как прочитанные только при первой загрузке
                        if (isRefresh) {
                            messagesManager.markAsRead(peerId);
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        paginationHelper.stopLoading();
                        pendingReplyMessageId = 0;
                        Toast.makeText(requireContext(), getString(R.string.chat_loading_error) + error, Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (photoUploadInProgress) return;
        sendMessage(messageText, getPendingAttachmentString());
    }

    private String getPendingAttachmentString() {
        List<String> all = new ArrayList<>(pendingPhotoAttachments);
        all.addAll(pendingMediaAttachments);
        return android.text.TextUtils.join(",", all);
    }

    private void onPhotosPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        photoUploadInProgress = true;
        attachButton.setEnabled(false);
        sendButton.setEnabled(false);
        uploadNextPhoto(uris, 0);
    }

    private void uploadNextPhoto(List<Uri> uris, int index) {
        if (index >= uris.size()) {
            photoUploadInProgress = false;
            attachButton.setEnabled(true);
            sendButton.setEnabled(true);
            updatePendingAttachmentPreview();
            return;
        }
        Uri uri = uris.get(index);
        PhotoUploadManager.getInstance(requireContext()).uploadMessagePhoto(uri, peerId,
                new PhotoUploadManager.PhotoUploadCallback() {
                    @Override
                    public void onSuccess(String attachment) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            pendingPhotoAttachments.add(attachment);
                            pendingPhotoUris.add(uri);
                            updatePendingAttachmentPreview();
                            uploadNextPhoto(uris, index + 1);
                        });
                    }
                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            photoUploadInProgress = false;
                            attachButton.setEnabled(true);
                            sendButton.setEnabled(true);
                            updatePendingAttachmentPreview();
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void sendMessage(String messageText, String attachment) {
        if (messageText.isEmpty() && (attachment == null || attachment.isEmpty())) {
            return;
        }
        
        // Отключаем кнопку отправки
        sendButton.setEnabled(false);
        messageInput.setEnabled(false);
        
        messagesManager.sendMessage(peerId, messageText, attachment, new MessagesManager.SendMessageCallback() {
            @Override
            public void onSuccess(int messageId) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        messageInput.setText("");
                        sendButton.setEnabled(true);
                        messageInput.setEnabled(true);
                        if (attachment != null && attachment.equals(getPendingAttachmentString())) {
                            clearPendingAttachment();
                        }
                        
                        // Перезагружаем сообщения чтобы показать отправленное
                        loadMessages(true);
                        
                        // Обновляем список чатов
                        refreshConversationsList();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.chat_send_error, error), Toast.LENGTH_LONG).show();
                        sendButton.setEnabled(true);
                        messageInput.setEnabled(true);
                    });
                }
            }
        });
    }

    private void updatePendingAttachmentPreview() {
        if (pendingAttachmentPreview == null || pendingAttachmentList == null) return;
        pendingAttachmentList.removeAllViews();
        boolean hasAttachments = !pendingPhotoAttachments.isEmpty() || !pendingMediaAttachments.isEmpty();
        if (!hasAttachments) {
            pendingAttachmentPreview.setVisibility(View.GONE);
            attachButton.setEnabled(true);
            updateRecyclerBottomPadding(false);
            return;
        }
        for (int i = 0; i < pendingPhotoUris.size(); i++) {
            final int index = i;
            MaterialCardView item = new MaterialCardView(requireContext());
            android.widget.LinearLayout.LayoutParams itemParams = new android.widget.LinearLayout.LayoutParams(dp(88), dp(88));
            itemParams.setMargins(0, 0, dp(8), 0);
            item.setRadius(dp(12));
            item.setCardElevation(dp(1));
            ImageView image = new ImageView(requireContext());
            image.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Picasso.get().load(pendingPhotoUris.get(i)).fit().centerCrop().into(image);
            item.addView(image);
            ImageView remove = new ImageView(requireContext());
            android.widget.FrameLayout.LayoutParams removeParams = new android.widget.FrameLayout.LayoutParams(
                    dp(24), dp(24), android.view.Gravity.TOP | android.view.Gravity.END);
            remove.setLayoutParams(removeParams);
            remove.setPadding(dp(4), dp(4), dp(4), dp(4));
            remove.setBackgroundResource(R.drawable.avatar_circle);
            remove.setImageResource(R.drawable.ic_close);
            remove.setColorFilter(android.graphics.Color.WHITE);
            remove.setOnClickListener(v -> {
                pendingPhotoUris.remove(index);
                pendingPhotoAttachments.remove(index);
                updatePendingAttachmentPreview();
            });
            item.addView(remove);
            pendingAttachmentList.addView(item, itemParams);
        }
        for (int i = 0; i < pendingMediaAttachments.size(); i++) {
            final int index = i;
            MaterialCardView item = new MaterialCardView(requireContext());
            android.widget.LinearLayout.LayoutParams itemParams = new android.widget.LinearLayout.LayoutParams(dp(104), dp(92));
            itemParams.setMargins(0, 0, dp(8), 0);
            item.setRadius(dp(12));
            item.setCardElevation(dp(1));
            android.widget.LinearLayout content = new android.widget.LinearLayout(requireContext());
            content.setOrientation(android.widget.LinearLayout.VERTICAL);
            content.setPadding(dp(4), dp(4), dp(4), dp(3));
            ImageView preview = new ImageView(requireContext());
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            content.addView(preview, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
            String previewUrl = i < pendingMediaPreviews.size() ? pendingMediaPreviews.get(i) : "";
            if (org.nikanikoo.flux.utils.ImageUtils.isValidImageUrl(previewUrl)) {
                Picasso.get().load(previewUrl).fit().centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder).into(preview);
            } else {
                preview.setImageResource(R.drawable.ic_image_placeholder);
            }
            TextView title = new TextView(requireContext());
            title.setGravity(android.view.Gravity.CENTER_VERTICAL);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setText(pendingMediaTitles.get(i));
            title.setTextSize(10);
            content.addView(title, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            item.addView(content);
            ImageView remove = new ImageView(requireContext());
            android.widget.FrameLayout.LayoutParams removeParams = new android.widget.FrameLayout.LayoutParams(
                    dp(24), dp(24), android.view.Gravity.TOP | android.view.Gravity.END);
            remove.setLayoutParams(removeParams);
            remove.setPadding(dp(4), dp(4), dp(4), dp(4));
            remove.setBackgroundResource(R.drawable.avatar_circle);
            remove.setImageResource(R.drawable.ic_close);
            remove.setColorFilter(android.graphics.Color.WHITE);
            remove.setOnClickListener(v -> {
                pendingMediaAttachments.remove(index);
                pendingMediaTitles.remove(index);
                if (index < pendingMediaPreviews.size()) pendingMediaPreviews.remove(index);
                updatePendingAttachmentPreview();
            });
            item.addView(remove);
            pendingAttachmentList.addView(item, itemParams);
        }
        pendingAttachmentPreview.setVisibility(View.VISIBLE);
        updateRecyclerBottomPadding(true);
    }

    private void updateRecyclerBottomPadding(boolean hasAttachments) {
        if (recyclerView == null) return;
        int extra = hasAttachments ? dp(112) : 0;
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(), recyclerBaseBottomPadding + extra);
    }

    private void clearPendingAttachment() {
        pendingPhotoAttachments.clear();
        pendingPhotoUris.clear();
        pendingMediaAttachments.clear();
        pendingMediaTitles.clear();
        pendingMediaPreviews.clear();
        if (pendingAttachmentPreview != null) pendingAttachmentPreview.setVisibility(View.GONE);
        if (pendingAttachmentList != null) pendingAttachmentList.removeAllViews();
        updateRecyclerBottomPadding(false);
        if (attachButton != null) attachButton.setEnabled(true);
    }
    
    /**
     * Обновить список диалогов в фоне (без UI)
     */
    private void refreshConversationsListBackground() {
        // Обновляем список чатов в фоне
        new Thread(() -> {
            try {
                MessagesManager messagesManager = MessagesManager.getInstance(requireContext());
                messagesManager.getConversations(20, 0, new MessagesManager.ConversationsCallback() {
                    @Override
                    public void onSuccess(List<Conversation> conversations) {
                        Log.d("ChatFragment", "Background refresh: updated " + conversations.size() + " conversations");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("ChatFragment", "Background refresh failed: " + error);
                    }
                });
            } catch (Exception e) {
                Log.e("ChatFragment", "Background refresh error", e);
            }
        }).start();
    }
    
    /**
     * Обновить список диалогов после отправки/получения сообщения
     */
    private void refreshConversationsList() {
        // Получаем родительский фрагмент (MessagesListFragment)
        androidx.fragment.app.Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof org.nikanikoo.flux.ui.fragments.messages.MessagesListFragment) {
            ((org.nikanikoo.flux.ui.fragments.messages.MessagesListFragment) parentFragment).refreshConversations();
        }
    }
    
    @Override
    public void onAvatarClick(int userId, String userName) {
        if (getActivity() == null) {
            return;
        }

        int containerId;
        if (getActivity() instanceof ChatActivity) {
            containerId = R.id.chat_container;
        } else {
            containerId = R.id.fragment_container;
        }

        if (userId > 0) {
            // Пользователь
            ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(userId, userName);
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(containerId, profileFragment)
                    .addToBackStack("profile_" + userId)
                    .commit();
        } else if (userId < 0) {
            // Группа (отрицательный ID)
            GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(-userId, userName);
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(containerId, groupProfileFragment)
                    .addToBackStack("group_" + (-userId))
                    .commit();
        }
    }

    @Override
    public void onReplyClick(int messageId) {
        pendingReplyMessageId = messageId;
        tryScrollToPendingReply();
    }

    private void tryScrollToPendingReply() {
        if (pendingReplyMessageId == 0 || adapter == null || recyclerView == null) return;

        int position = adapter.findMessagePosition(pendingReplyMessageId);
        if (position != RecyclerView.NO_POSITION) {
            pendingReplyMessageId = 0;
            recyclerView.post(() -> recyclerView.smoothScrollToPosition(position));
        } else if (paginationHelper != null && paginationHelper.canLoadMore()) {
            loadMessages(false);
        } else if (paginationHelper != null) {
            pendingReplyMessageId = 0;
        }
    }
    
    // Методы для обновления из LongPoll
    public void refreshMessagesIfSamePeer(int targetPeerId) {
        Log.d("ChatFragment", "refreshMessagesIfSamePeer called: targetPeerId=" + targetPeerId + ", currentPeerId=" + peerId);
        
        if (peerId == targetPeerId && getActivity() != null) {
            Log.d("ChatFragment", "Refreshing messages for matching peer");
            getActivity().runOnUiThread(() -> {
                // Загружаем только новые сообщения, не сбрасывая список
                loadMessages(true);
            });
        }
    }
    
    // Метод для получения текущего peerId
    public int getPeerId() {
        return peerId;
    }
    
    // Метод для добавления нового сообщения в реальном времени
    public void addNewMessageFromLongPoll(int messageId, int messagePeerId, int fromId, String text, long timestamp, boolean isOut) {
        Log.d("ChatFragment", "addNewMessageFromLongPoll called: messageId=" + messageId + ", messagePeerId=" + messagePeerId + ", fromId=" + fromId + ", text=" + text + ", currentPeerId=" + peerId);
        
        // Проверяем, что сообщение предназначено для этого чата
        if (peerId != messagePeerId) {
            Log.d("ChatFragment", "Message peerId (" + messagePeerId + ") doesn't match current chat peerId (" + peerId + "), ignoring");
            return;
        }
        
        if (getActivity() != null) {
            Log.d("ChatFragment", "Activity is not null, proceeding with UI update");
            
            getActivity().runOnUiThread(() -> {
                Log.d("ChatFragment", "Running on UI thread");
                
                // Создаем новое сообщение
                Message newMessage = new Message(messageId, messagePeerId, fromId, text, timestamp, isOut, true);
                Log.d("ChatFragment", "Created new message object");
                
                if (adapter != null) {
                    Log.d("ChatFragment", "Adapter is not null, adding message via adapter");
                    adapter.addMessage(newMessage);
                    
                    // Загружаем информацию о пользователе для входящих сообщений
                    if (!isOut && fromId > 0) {
                        loadUserInfoForMessage(newMessage, fromId);
                    }
                    
                    // Прокручиваем к последнему сообщению
                    if (recyclerView != null && adapter.getItemCount() > 0) {
                        Log.d("ChatFragment", "Scrolling to position: " + (adapter.getItemCount() - 1));
                        recyclerView.post(() ->
                            recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1)
                        );
                    } else {
                        Log.w("ChatFragment", "RecyclerView is null or no items to scroll");
                    }
                } else {
                    Log.w("ChatFragment", "Adapter is null!");
                }
            });
        } else {
            Log.w("ChatFragment", "Activity is null, cannot update UI");
        }
    }
    
    // Метод для загрузки информации о пользователе для сообщения
    private void loadUserInfoForMessage(Message message, int userId) {
        Log.d("ChatFragment", "Loading user info for message from user " + userId);
        
        // Используем MessagesManager для получения информации о пользователе
        messagesManager.getUserInfo(userId, new MessagesManager.UserInfoCallback() {
            @Override
            public void onSuccess(String userName, String userPhoto) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Log.d("ChatFragment", "User info loaded: " + userName + ", photo: " + userPhoto);
                        message.setUserName(userName);
                        message.setUserPhoto(userPhoto);
                        
                        // Обновляем конкретный элемент в адаптере
                        if (adapter != null) {
                            adapter.notifyMessageChanged(message);
                            Log.d("ChatFragment", "Updated message with user info via notifyMessageChanged");
                        }
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                Log.w("ChatFragment", "Failed to load user info for user " + userId + ": " + error);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (longPollManager != null) {
            longPollManager.addMessageEventListener(this);
            longPollManager.setTypingEventListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (longPollManager != null) {
            longPollManager.removeMessageEventListener(this);
            longPollManager.setTypingEventListener(null);
        }
        if (typingTimeoutHandler != null && typingTimeoutRunnable != null) {
            typingTimeoutHandler.removeCallbacks(typingTimeoutRunnable);
        }
        setActionBarSubtitle(null);
    }

    @Override
    public void onNewMessage(int messageId, int peerId, long timestamp, String text, int fromId, boolean isOut) {
        refreshMessagesIfSamePeer(peerId);
    }

    @Override
    public void onMessageRead(int peerId, int localId) {
        refreshMessagesIfSamePeer(peerId);
    }

    @Override
    public void onMessageEdit(int messageId, int peerId, String newText) {
        refreshMessagesIfSamePeer(peerId);
    }

    @Override
    public void onUserTyping(int peerId, int userId) {
        Log.d("ChatFragment", "onUserTyping received: peerId=" + peerId + ", userId=" + userId + ", currentPeerId=" + this.peerId);
        if (this.peerId == peerId && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                setActionBarSubtitle(getString(R.string.chat_typing));
                
                if (typingTimeoutHandler != null && typingTimeoutRunnable != null) {
                    typingTimeoutHandler.removeCallbacks(typingTimeoutRunnable);
                    typingTimeoutHandler.postDelayed(typingTimeoutRunnable, 6000);
                }
            });
        }
    }

    private void sendTypingStatus() {
        messagesManager.sendTypingActivity(peerId, new org.nikanikoo.flux.data.managers.api.OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                Log.d("ChatFragment", "Typing status sent successfully");
            }

            @Override
            public void onError(String error) {
                Log.w("ChatFragment", "Failed to send typing status: " + error);
            }
        });
    }

    private void setActionBarSubtitle(String subtitleText) {
        if (getActivity() instanceof MainActivity) {
            androidx.appcompat.app.ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(subtitleText);
            }
        } else if (getActivity() instanceof ChatActivity) {
            androidx.appcompat.app.ActionBar actionBar = ((ChatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(subtitleText);
            }
        }
    }
}
