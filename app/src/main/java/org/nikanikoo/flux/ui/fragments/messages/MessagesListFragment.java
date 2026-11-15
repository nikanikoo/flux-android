package org.nikanikoo.flux.ui.fragments.messages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.data.models.Message;
import org.nikanikoo.flux.ui.adapters.messages.ConversationsAdapter;
import org.nikanikoo.flux.data.managers.MessagesManager;
import org.nikanikoo.flux.data.managers.FriendsManager;
import org.nikanikoo.flux.data.models.Friend;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.ChatActivity;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;

import org.nikanikoo.flux.services.LongPollManager;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MessagesListFragment extends BaseFragment implements ConversationsAdapter.OnConversationClickListener, LongPollManager.OnTypingEventListener {
    
    private RecyclerView recyclerView;
    private ConversationsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private MessagesManager messagesManager;
    private List<Conversation> conversations;
    private boolean isConversationsLoaded = false;
    private boolean isViewCreated = false;

    private static List<Conversation> sCachedConversations = null;
    private static boolean sIsConversationsLoaded = false;
    private int activePeerId = -1;
    private final Map<Integer, String> typingNamesByPeerId = new HashMap<>();
    private final Set<Integer> typingNameRequests = new HashSet<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Инициализируем список и менеджер один раз при создании фрагмента
        if (conversations == null) {
            conversations = new ArrayList<>();
        }
        if (messagesManager == null) {
            messagesManager = MessagesManager.getInstance(requireContext());
        }
        
        if (conversations.isEmpty() && sIsConversationsLoaded && sCachedConversations != null) {
            conversations.addAll(sCachedConversations);
            isConversationsLoaded = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_messages_list, container, false);
        
        initViews(view);
        setupRecyclerView();
        setupToolbarTitle();
        setupErrorView(view, R.id.messages_swipe_refresh);
        setRetryCallback(() -> refreshConversations());

        isViewCreated = true;
        
        // Загружаем диалоги
        if (!isConversationsLoaded || conversations.isEmpty()) {
            refreshConversations();
        } else {
            // Если список уже загружен, просто обновляем адаптер
            adapter.notifyDataSetChanged();
        }
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Восстанавливаем заголовок при возврате
        setupToolbarTitle();
        if (LongPollManager.getInstance(requireContext()) != null) {
            LongPollManager.getInstance(requireContext()).setTypingEventListener(this);
        }
        
        if (activePeerId != -1) {
            activePeerId = -1;
            // Reload the ordered conversation list so a chat that received a new
            // message while open returns to its correct position as well as preview.
            refreshConversations();
        } else {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (LongPollManager.getInstance(requireContext()) != null) {
            LongPollManager.getInstance(requireContext()).setTypingEventListener(null);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewCreated = false;
        if (adapter != null) {
            adapter.cleanup();
        }
    }
    
    @Override
    public void onUserTyping(int peerId, int userId) {
        if (getActivity() != null && isViewCreated && adapter != null) {
            getActivity().runOnUiThread(() -> {
                if (peerId <= 2000000000) {
                    adapter.setUserTyping(peerId, getString(R.string.chat_typing));
                    return;
                }

                String cachedName = typingNamesByPeerId.get(peerId);
                if (cachedName != null) {
                    adapter.setUserTyping(peerId, getString(R.string.chat_typing_with_name, cachedName));
                } else {
                    adapter.setUserTyping(peerId, getString(R.string.chat_typing));
                    if (typingNameRequests.add(peerId)) {
                        messagesManager.getUserInfo(userId, new MessagesManager.UserInfoCallback() {
                            @Override
                            public void onSuccess(String userName, String userPhoto) {
                                typingNameRequests.remove(peerId);
                                typingNamesByPeerId.put(peerId, userName);
                                if (isAdded() && adapter != null) {
                                    adapter.setUserTyping(peerId, getString(R.string.chat_typing_with_name, userName));
                                }
                            }

                            @Override
                            public void onError(String error) {
                                typingNameRequests.remove(peerId);
                            }
                        });
                    }
                }
            });
        }
    }

    public void onNewMessageReceived(int messageId, int peerId, String text, long timestamp, boolean isOut) {
        if (getActivity() != null && isViewCreated) {
            if (messageId <= 0) {
                handleNewMessageLocally(peerId, text, timestamp, isOut);
                return;
            }

            messagesManager.getMessageById(messageId, peerId, new MessagesManager.MessagesCallback() {
                @Override
                public void onSuccess(List<Message> messages) {
                    if (getActivity() == null || !isViewCreated) return;
                    if (messages == null || messages.isEmpty()) {
                        handleNewMessageLocally(peerId, text, timestamp, isOut);
                        return;
                    }

                    Message message = messages.get(0);
                    handleNewMessageLocally(peerId, messagesManager.createConversationPreview(message),
                            message.getDate(), message.isOut());
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null && isViewCreated) {
                        handleNewMessageLocally(peerId, text, timestamp, isOut);
                    }
                }
            });
        }
    }

    public void onMessageReadLocally(int peerId) {
        if (getActivity() != null && isViewCreated) {
            getActivity().runOnUiThread(() -> {
                for (int i = 0; i < conversations.size(); i++) {
                    Conversation conversation = conversations.get(i);
                    if (conversation.getPeerId() == peerId) {
                        conversation.setUnreadCount(0);
                        if (adapter != null) {
                            adapter.notifyItemChanged(i);
                        }
                        break;
                    }
                }
            });
        }
    }

    public void onMessageEditLocally(int peerId, String newText) {
        if (getActivity() != null && isViewCreated) {
            getActivity().runOnUiThread(() -> {
                for (int i = 0; i < conversations.size(); i++) {
                    Conversation conversation = conversations.get(i);
                    if (conversation.getPeerId() == peerId) {
                        conversation.setLastMessage(newText);
                        if (adapter != null) {
                            adapter.notifyItemChanged(i);
                        }
                        break;
                    }
                }
            });
        }
    }

    private void handleNewMessageLocally(int peerId, String text, long timestamp, boolean isOut) {
        int foundIndex = -1;
        Conversation targetConversation = null;
        
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).getPeerId() == peerId) {
                foundIndex = i;
                targetConversation = conversations.get(i);
                break;
            }
        }
        
        if (targetConversation != null) {
            if (timestamp < targetConversation.getLastMessageDate()) return;
            targetConversation.setLastMessage(text);
            targetConversation.setLastMessageDate(timestamp);
            if (!isOut) {
                targetConversation.setUnreadCount(targetConversation.getUnreadCount() + 1);
            } else {
                targetConversation.setUnreadCount(0);
            }
            
            org.nikanikoo.flux.data.managers.ProfileManager profileManager =
                org.nikanikoo.flux.data.managers.ProfileManager.getInstance(requireContext());
            org.nikanikoo.flux.data.models.UserProfile myProfile = profileManager.getCachedProfileSync();
            int myId = myProfile != null ? myProfile.getId() : 0;
            
            if (peerId == myId) {
                cacheConversations();
                if (adapter != null) {
                    adapter.notifyItemChanged(foundIndex);
                }
                return;
            }
            
            conversations.remove(foundIndex);
            
            int insertIndex = 0;
            if (!conversations.isEmpty()) {
                Conversation first = conversations.get(0);
                if (first.getPeerId() == myId) {
                    insertIndex = 1;
                }
            }
            
            conversations.add(insertIndex, targetConversation);
            
            if (adapter != null) {
                if (foundIndex == insertIndex) {
                    adapter.notifyItemChanged(insertIndex);
                } else {
                    adapter.notifyItemChanged(foundIndex);
                    adapter.notifyItemMoved(foundIndex, insertIndex);
                    adapter.notifyItemChanged(insertIndex);
                }
            }
            cacheConversations();
        } else {
            refreshConversations();
        }
    }

    private void cacheConversations() {
        sCachedConversations = new ArrayList<>(conversations);
        sIsConversationsLoaded = true;
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(getString(R.string.messages_title));
        }
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.messages_swipe_refresh);
        FloatingActionButton newConversationButton = view.findViewById(R.id.fab_new_conversation);
        newConversationButton.setOnClickListener(v -> loadFriendsForNewConversation());
        
        swipeRefreshLayout.setOnRefreshListener(this::refreshConversations);
    }

    private void setupRecyclerView() {
        adapter = new ConversationsAdapter(requireContext(), conversations);
        adapter.setOnConversationClickListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    /**
     * Обновить список диалогов
     */
    public void refreshConversations() {
        if (!isViewCreated) {
            return;
        }

        sCachedConversations = null;
        sIsConversationsLoaded = false;
        
        swipeRefreshLayout.setRefreshing(true);
        
        messagesManager.getConversations(20, 0, new MessagesManager.ConversationsCallback() {
            @Override
            public void onSuccess(List<Conversation> loadedConversations) {
                if (getActivity() != null && isViewCreated) {
                    getActivity().runOnUiThread(() -> {
                        hideError();
                        conversations.clear();
                        
                        org.nikanikoo.flux.data.managers.ProfileManager profileManager = 
                            org.nikanikoo.flux.data.managers.ProfileManager.getInstance(requireContext());
                        org.nikanikoo.flux.data.models.UserProfile myProfile = profileManager.getCachedProfileSync();
                        
                        Conversation selfConv = null;
                        if (myProfile != null) {
                            for (Conversation c : loadedConversations) {
                                if (c.getPeerId() == myProfile.getId()) {
                                    selfConv = c;
                                    break;
                                }
                            }
                            
                            if (selfConv != null) {
                                loadedConversations.remove(selfConv);
                                Conversation updatedSelf = new Conversation(
                                    selfConv.getId(),
                                    selfConv.getPeerId(),
                                    getString(R.string.chat_favorites),
                                    selfConv.getLastMessage() == null || selfConv.getLastMessage().isEmpty() ? getString(R.string.chat_favorites_subtitle) : selfConv.getLastMessage(),
                                    selfConv.getLastMessageDate(),
                                    selfConv.getUnreadCount()
                                );
                                updatedSelf.setPeerPhoto(myProfile.getPhoto200());
                                updatedSelf.setOnline(true);
                                updatedSelf.setPeerVerified(myProfile.isVerified());
                                selfConv = updatedSelf;
                            } else {
                                selfConv = new Conversation(
                                    myProfile.getId(),
                                    myProfile.getId(),
                                    getString(R.string.chat_favorites),
                                    getString(R.string.chat_favorites_subtitle),
                                    0,
                                    0
                                );
                                selfConv.setPeerPhoto(myProfile.getPhoto200());
                                selfConv.setOnline(true);
                                selfConv.setPeerVerified(myProfile.isVerified());
                            }
                        }
                        
                        if (selfConv != null) {
                            conversations.add(selfConv);
                        }
                        conversations.addAll(loadedConversations);
                        
                        if (adapter != null) {
                            adapter.updateConversations(conversations);
                        }
                        isConversationsLoaded = true;
                        
                        sCachedConversations = new ArrayList<>(conversations);
                        sIsConversationsLoaded = true;
                        
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null && isViewCreated) {
                    getActivity().runOnUiThread(() -> {
                        showErrorAuto(error);
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }
 
    @Override
    public void onConversationClick(Conversation conversation) {
        activePeerId = conversation.getPeerId();
        
        // Открываем чат с выбранным пользователем
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_ID, conversation.getPeerId());
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, conversation.getTitle());
        intent.putExtra(ChatActivity.EXTRA_FROM_ID, conversation.getPeerId());
        startActivity(intent);
    }

    @Override
    public void onConversationLongClick(Conversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(conversation.getTitle())
                .setItems(new CharSequence[]{getString(R.string.chat_notifications_menu),
                        getString(R.string.chat_delete_conversation)}, (dialog, which) -> {
                    if (which == 0) showConversationNotificationOptions(conversation);
                    else confirmDeleteConversation(conversation);
                })
                .show();
    }

    private void showConversationNotificationOptions(Conversation conversation) {
        messagesManager.refreshPushSettings(conversation.getPeerId(), new MessagesManager.PushSettingsCallback() {
            @Override
            public void onSuccess(MessagesManager.PushSettings settings) {
                if (!isAdded()) return;
                List<String> options = new ArrayList<>();
                List<Integer> durations = new ArrayList<>();
                if (!settings.areNotificationsEnabled()) {
                    options.add(getString(R.string.chat_notifications_enable));
                    durations.add(0);
                }
                options.add(getString(R.string.chat_notifications_mute_1_hour));
                durations.add(3600);
                options.add(getString(R.string.chat_notifications_mute_8_hours));
                durations.add(28800);
                options.add(getString(R.string.chat_notifications_mute_forever));
                durations.add(-1);

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.chat_notifications_menu)
                        .setItems(options.toArray(new String[0]), (dialog, which) ->
                                messagesManager.setSilenceMode(conversation.getPeerId(), durations.get(which),
                                        new MessagesManager.SilenceModeCallback() {
                                            @Override public void onSuccess() {
                                                if (isAdded()) Toast.makeText(requireContext(),
                                                        durations.get(which) == 0 ? R.string.chat_notifications_enabled
                                                                : R.string.chat_notifications_muted,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                            @Override public void onError(String error) {
                                                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                                            }
                                        }))
                        .show();
            }

            @Override
            public void onError(String error) {
                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDeleteConversation(Conversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.chat_delete_conversation_confirm, conversation.getTitle()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> messagesManager.deleteConversation(
                        conversation.getPeerId(), new org.nikanikoo.flux.data.managers.api.OpenVKApi.ApiCallback() {
                            @Override public void onSuccess(org.json.JSONObject response) {
                                if (!isAdded()) return;
                                conversations.remove(conversation);
                                cacheConversations();
                                if (adapter != null) adapter.updateConversations(conversations);
                            }
                            @Override public void onError(String error) {
                                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    private void loadFriendsForNewConversation() {
        FriendsManager.getInstance(requireContext()).getFriends(1000, 0, new FriendsManager.FriendsCallback() {
            @Override
            public void onSuccess(List<Friend> friends) {
                if (!isAdded()) return;
                if (friends.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.no_friends_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                showFriendPicker(friends);
            }

            @Override
            public void onError(String error) {
                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showFriendPicker(List<Friend> friends) {
        String[] names = new String[friends.size()];
        boolean[] selected = new boolean[friends.size()];
        for (int i = 0; i < friends.size(); i++) names[i] = friends.get(i).getFullName().trim();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.new_conversation)
                .setMultiChoiceItems(names, selected, (dialog, which, checked) -> selected[which] = checked)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_action, (dialog, which) -> {
                    List<Friend> chosen = new ArrayList<>();
                    for (int i = 0; i < selected.length; i++) if (selected[i]) chosen.add(friends.get(i));
                    if (chosen.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.select_at_least_one_friend, Toast.LENGTH_SHORT).show();
                    } else if (chosen.size() == 1) {
                        openConversation(chosen.get(0).getId(), chosen.get(0).getFullName());
                    } else {
                        createGroupConversation(chosen);
                    }
                })
                .show();
    }

    private void createGroupConversation(List<Friend> friends) {
        StringBuilder userIds = new StringBuilder();
        for (Friend friend : friends) {
            if (userIds.length() > 0) userIds.append(',');
            userIds.append(friend.getId());
        }
        messagesManager.createChat(userIds.toString(), new org.nikanikoo.flux.data.managers.api.OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                if (!isAdded()) return;
                int chatId = response.optInt("response", 0);
                if (chatId <= 0) {
                    Toast.makeText(requireContext(), R.string.chat_create_error, Toast.LENGTH_LONG).show();
                    return;
                }
                openConversation(2000000000 + chatId, getString(R.string.chat_title_default));
            }

            @Override
            public void onError(String error) {
                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openConversation(int peerId, String title) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_ID, peerId);
        intent.putExtra(ChatActivity.EXTRA_PEER_NAME, title);
        intent.putExtra(ChatActivity.EXTRA_FROM_ID, peerId);
        startActivity(intent);
    }
    
    @Override
    public void onAvatarClick(int userId, String userName) {
        // Переход в профиль пользователя или группы
        if (userId > 0) {
            // Пользователь
            ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(userId, userName);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, profileFragment)
                    .addToBackStack("profile_" + userId)
                    .commit();
        } else if (userId < 0) {
            // Группа (отрицательный ID)
            GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(-userId, userName);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, groupProfileFragment)
                    .addToBackStack("group_" + (-userId))
                    .commit();
        }
    }
    
    public void updateUserOnlineStatus(int userId, boolean isOnline) {
        if (getActivity() != null && isViewCreated) {
            getActivity().runOnUiThread(() -> {
                // Обновляем статус пользователя в адаптере
                if (adapter != null) {
                    adapter.updateUserOnlineStatus(userId, isOnline);
                }
            });
        }
    }
}
