package org.nikanikoo.flux.ui.fragments.messages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.ui.adapters.messages.ConversationsAdapter;
import org.nikanikoo.flux.data.managers.MessagesManager;
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
import java.util.ArrayList;
import java.util.List;

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
        setupErrorView(view, R.id.swipe_refresh);
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
            final int peerIdToUpdate = activePeerId;
            activePeerId = -1;
            
            messagesManager.getHistory(peerIdToUpdate, 1, 0, new MessagesManager.MessagesCallback() {
                @Override
                public void onSuccess(List<org.nikanikoo.flux.data.models.Message> messagesList) {
                    if (getActivity() != null && isViewCreated && messagesList != null && !messagesList.isEmpty()) {
                        getActivity().runOnUiThread(() -> {
                            org.nikanikoo.flux.data.models.Message lastMsg = messagesList.get(0);
                            
                            handleNewMessageLocally(peerIdToUpdate, lastMsg.getText(), lastMsg.getDate(), lastMsg.isOut());
                            
                            for (int i = 0; i < conversations.size(); i++) {
                                Conversation c = conversations.get(i);
                                if (c.getPeerId() == peerIdToUpdate) {
                                    c.setUnreadCount(0);
                                    if (adapter != null) {
                                        adapter.notifyItemChanged(i);
                                    }
                                    break;
                                }
                            }
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            });
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
                adapter.setUserTyping(peerId, true);
            });
        }
    }

    public void onNewMessageReceived(int peerId, String text, long timestamp, boolean isOut) {
        if (getActivity() != null && isViewCreated) {
            getActivity().runOnUiThread(() -> {
                handleNewMessageLocally(peerId, text, timestamp, isOut);
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
        } else {
            refreshConversations();
        }
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(getString(R.string.messages_title));
        }
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        
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