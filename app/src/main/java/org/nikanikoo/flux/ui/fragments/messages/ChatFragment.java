package org.nikanikoo.flux.ui.fragments.messages;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.models.Message;
import org.nikanikoo.flux.services.MessageNotificationManager;
import org.nikanikoo.flux.ui.adapters.messages.MessagesAdapter;
import org.nikanikoo.flux.data.managers.MessagesManager;
import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatFragment extends Fragment implements MessagesAdapter.OnMessageClickListener {
    
    private static final String ARG_PEER_ID = "peer_id";
    private static final String ARG_TITLE = "title";
    
    private int peerId;
    private String title;
    private RecyclerView recyclerView;
    private MessagesAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EditText messageInput;
    private ImageButton sendButton;
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
        if (getArguments() != null) {
            peerId = getArguments().getInt(ARG_PEER_ID);
            title = getArguments().getString(ARG_TITLE);
        }
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
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        messageInput = view.findViewById(R.id.message_input);
        sendButton = view.findViewById(R.id.send_button);
        dateHeaderCard = view.findViewById(R.id.date_header_card);
        dateHeaderText = view.findViewById(R.id.date_header_text);
        messagesManager = MessagesManager.getInstance(requireContext());
        messages = new ArrayList<>();

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
        
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
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
                        
                        swipeRefreshLayout.setRefreshing(false);
                        
                        // Прокручиваем к последнему сообщению только при первой загрузке
                        if (shouldScrollToBottom && !messages.isEmpty()) {
                            recyclerView.scrollToPosition(messages.size() - 1);
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
                        Toast.makeText(requireContext(), getString(R.string.chat_loading_error) + error, Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }
        
        // Отключаем кнопку отправки
        sendButton.setEnabled(false);
        messageInput.setEnabled(false);
        
        messagesManager.sendMessage(peerId, messageText, new MessagesManager.SendMessageCallback() {
            @Override
            public void onSuccess(int messageId) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        messageInput.setText("");
                        sendButton.setEnabled(true);
                        messageInput.setEnabled(true);
                        
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
                        Toast.makeText(requireContext(), getString(R.string.chat_send_error) + error, Toast.LENGTH_SHORT).show();
                        sendButton.setEnabled(true);
                        messageInput.setEnabled(true);
                    });
                }
            }
        });
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
        if (getActivity() != null) {
            if (userId > 0) {
                // Пользователь
                ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(userId, userName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, profileFragment)
                        .addToBackStack("profile_" + userId)
                        .commit();
            } else if (userId < 0) {
                // Группа (отрицательный ID)
                GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(-userId, userName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, groupProfileFragment)
                        .addToBackStack("group_" + (-userId))
                        .commit();
            }
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
                    if (recyclerView != null) {
                        Log.d("ChatFragment", "Scrolling to position: " + (messages.size() - 1));
                        recyclerView.scrollToPosition(messages.size() - 1);
                    } else {
                        Log.w("ChatFragment", "RecyclerView is null!");
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
                            int position = messages.indexOf(message);
                            if (position >= 0) {
                                adapter.notifyItemChanged(position);
                                Log.d("ChatFragment", "Updated message at position " + position + " with user info");
                            }
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
}