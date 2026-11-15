package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.MenuItem;
import android.view.Menu;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import com.squareup.picasso.Picasso;
import org.nikanikoo.flux.ui.custom.CircularImageTransformation;
import org.nikanikoo.flux.ui.fragments.settings.AppearanceSettingsFragment;
import org.nikanikoo.flux.data.managers.MessagesManager;
import org.nikanikoo.flux.data.managers.PhotoUploadManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.services.LongPollManager;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.fragments.messages.ChatFragment;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_PEER_ID = "peer_id";
    public static final String EXTRA_PEER_NAME = "peer_name";
    public static final String EXTRA_FROM_ID = "from_id";

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = LocaleManager.getInstance(newBase);
        Context context = localeManager.updateContext(newBase);
        super.attachBaseContext(context);
    }

    private Toolbar toolbar;
    private android.view.View toolbarHeader;
    private int peerId;
    private int chatId;
    private String peerName;
    private String toolbarTitle;
    private boolean toolbarOnline;
    private boolean toolbarTyping;
    private ImageView toolbarAvatar;
    private TextView toolbarTitleView;
    private TextView toolbarSubtitleView;
    private boolean chatNotificationsMuted;
    private boolean toolbarHeaderActive;
    private int toolbarProfileUserId;
    private final Handler notificationStateHandler = new Handler(Looper.getMainLooper());
    private Runnable notificationExpiryRunnable;
    private ActivityResultLauncher<String> chatPhotoPicker;
    private MiniPlayerController miniPlayerController;
    private final MessagesManager.PushSettingsChangeListener pushSettingsChangeListener = (changedPeerId, settings) -> {
        if (changedPeerId != peerId) return;
        runOnUiThread(() -> updateNotificationMuteIndicator(settings));
    };
    private final LongPollManager.OnOnlineEventListener chatOnlineListener = (userId, online) -> {
        if (userId == peerId) {
            runOnUiThread(() -> {
                toolbarOnline = online;
                updateToolbarSubtitle();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applySavedTheme();
        themeManager.applyThemeToActivity(this);

        super.onCreate(savedInstanceState);
        peerId = getIntent().getIntExtra(EXTRA_PEER_ID, 0);
        peerName = getIntent().getStringExtra(EXTRA_PEER_NAME);
        chatId = peerId > 2000000000 ? peerId - 2000000000 : 0;
        chatPhotoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onChatPhotoPicked);
        setContentView(R.layout.activity_chat);
        miniPlayerController = new MiniPlayerController(this);
        miniPlayerController.initViews(findViewById(R.id.chat_mini_player));

        ThemeManager.applySystemBarsAppearance(this);

        int fromId = getIntent().getIntExtra(EXTRA_FROM_ID, peerId);

        setupToolbar(peerName);

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            androidx.fragment.app.Fragment currentFragment =
                    getSupportFragmentManager().findFragmentById(R.id.chat_container);
            if (currentFragment instanceof ProfileFragment) {
                Bundle args = currentFragment.getArguments();
                int profileUserId = args != null ? args.getInt("user_id", 0) : 0;
                String profileUserName = args != null ? args.getString("user_name") : null;
                if (profileUserId > 0) showProfileToolbar(profileUserId, profileUserName);
            } else if (isChatScreenVisible()) {
                toolbarHeaderActive = false;
                toolbarProfileUserId = 0;
                toolbarTitle = peerName != null ? peerName : getString(R.string.chat_title);
                toolbarTyping = false;
                if (toolbarHeader != null) toolbarHeader.setOnClickListener(v -> openChatHeaderOptions());
                updateToolbarTitle();
                loadToolbarHeader();
                MessagesManager.getInstance(this).refreshPushSettings(peerId,
                        new MessagesManager.PushSettingsCallback() {
                            @Override public void onSuccess(MessagesManager.PushSettings settings) {
                                updateNotificationMuteIndicator(settings);
                            }
                            @Override public void onError(String error) { }
                        });
            }
            invalidateOptionsMenu();
        });

        if (savedInstanceState == null) {
            ChatFragment chatFragment = ChatFragment.newInstance(peerId, peerName, fromId);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.chat_container, chatFragment)
                .commit();
        }
        loadToolbarHeader();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (miniPlayerController != null) miniPlayerController.bindService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MessagesManager messagesManager = MessagesManager.getInstance(this);
        messagesManager.addPushSettingsChangeListener(pushSettingsChangeListener);
        LongPollManager.getInstance(this).addOnlineEventListener(chatOnlineListener);
        messagesManager.refreshPushSettings(peerId, new MessagesManager.PushSettingsCallback() {
            @Override
            public void onSuccess(MessagesManager.PushSettings settings) {
                updateNotificationMuteIndicator(settings);
            }

            @Override
            public void onError(String error) {
                // Keep the current indicator when a refresh fails.
            }
        });
    }

    @Override
    protected void onStop() {
        MessagesManager.getInstance(this).removePushSettingsChangeListener(pushSettingsChangeListener);
        LongPollManager.getInstance(this).removeOnlineEventListener(chatOnlineListener);
        if (notificationExpiryRunnable != null) {
            notificationStateHandler.removeCallbacks(notificationExpiryRunnable);
            notificationExpiryRunnable = null;
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        if (miniPlayerController != null) miniPlayerController.unbindService();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isChatScreenVisible()) {
            getMenuInflater().inflate(R.menu.menu_chat, menu);
        }
        MenuItem groupOptions = menu.findItem(R.id.action_chat_options);
        if (groupOptions != null) groupOptions.setVisible(chatId > 0);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean showChatMenu = isChatScreenVisible();
        MenuItem notifications = menu.findItem(R.id.action_chat_notifications);
        if (notifications != null) notifications.setVisible(showChatMenu);
        MenuItem groupOptions = menu.findItem(R.id.action_chat_options);
        if (groupOptions != null) groupOptions.setVisible(showChatMenu && chatId > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean isChatScreenVisible() {
        return getSupportFragmentManager().findFragmentById(R.id.chat_container) instanceof ChatFragment;
    }

    private void openGroupOptions() {
        String[] options = {
                getString(R.string.chat_members_action),
                getString(R.string.chat_add_member_action),
                getString(R.string.chat_rename_action),
                getString(R.string.chat_change_photo_action),
                getString(R.string.chat_notifications_menu)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(peerName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) loadChatMembers();
                    else if (which == 1) promptAddMember();
                    else if (which == 2) promptRenameChat();
                    else if (which == 3) chatPhotoPicker.launch("image/*");
                    else openNotificationOptions();
                })
                .show();
    }

    private void openChatHeaderOptions() {
        if (chatId > 0) {
            openGroupOptions();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(peerName)
                .setItems(new String[] { getString(R.string.chat_notifications_menu) },
                        (dialog, which) -> openNotificationOptions())
                .show();
    }

    private void openNotificationOptions() {
        MessagesManager.getInstance(this).refreshPushSettings(peerId, new MessagesManager.PushSettingsCallback() {
            @Override
            public void onSuccess(MessagesManager.PushSettings settings) {
                boolean enabled = settings.areNotificationsEnabled();
                String[] options;
                int[] durations;
                if (enabled) {
                    options = new String[] {
                            getString(R.string.chat_notifications_mute_1_hour),
                            getString(R.string.chat_notifications_mute_8_hours),
                            getString(R.string.chat_notifications_mute_forever)
                    };
                    durations = new int[] { 3600, 28800, -1 };
                } else {
                    options = new String[] {
                            getString(R.string.chat_notifications_enable),
                            getString(R.string.chat_notifications_mute_1_hour),
                            getString(R.string.chat_notifications_mute_8_hours),
                            getString(R.string.chat_notifications_mute_forever)
                    };
                    durations = new int[] { 0, 3600, 28800, -1 };
                }

                new MaterialAlertDialogBuilder(ChatActivity.this)
                        .setTitle(enabled ? R.string.chat_notifications_enabled : R.string.chat_notifications_muted)
                        .setItems(options, (dialog, which) -> applySilenceMode(durations[which]))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }

            @Override
            public void onError(String error) {
                showChatActionError(getString(R.string.chat_notifications_update_error, error));
            }
        });
    }

    private void applySilenceMode(int durationSeconds) {
        MessagesManager.getInstance(this).setSilenceMode(peerId, durationSeconds,
                new MessagesManager.SilenceModeCallback() {
                    @Override
                    public void onSuccess() {
                        int message = durationSeconds == 0
                                ? R.string.chat_notifications_enabled
                                : R.string.chat_notifications_muted;
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        showChatActionError(getString(R.string.chat_notifications_update_error, error));
                    }
                });
    }

    private void loadChatMembers() {
        MessagesManager.getInstance(this).getConversationMembers(peerId, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject data = response.getJSONObject("response");
                    JSONArray items = data.optJSONArray("items");
                    JSONArray profiles = data.optJSONArray("profiles");
                    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
                    java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject member = items.optJSONObject(i);
                            if (member == null) continue;
                            int id = member.optInt("member_id", member.optInt("id", 0));
                            if (id <= 0) continue;
                            String name = "id" + id;
                            if (profiles != null) {
                                for (int j = 0; j < profiles.length(); j++) {
                                    JSONObject profile = profiles.optJSONObject(j);
                                    if (profile != null && profile.optInt("id") == id) {
                                        name = (profile.optString("first_name", "") + " " + profile.optString("last_name", "")).trim();
                                        break;
                                    }
                                }
                            }
                            ids.add(id);
                            labels.add(name);
                        }
                    }
                    showChatMembers(ids, labels);
                } catch (Exception e) {
                    showChatActionError();
                }
            }

            @Override
            public void onError(String error) { showChatActionError(error); }
        });
    }

    private void showChatMembers(java.util.ArrayList<Integer> ids, java.util.ArrayList<String> labels) {
        if (labels.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.chat_members_action)
                    .setMessage(R.string.group_members_none).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.chat_members_action)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int userId = ids.get(which);
                    String name = labels.get(which);
                    new MaterialAlertDialogBuilder(this)
                            .setMessage(getString(R.string.chat_member_remove_confirm, name))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.chat_remove_member_action, (d, w) -> MessagesManager.getInstance(this)
                                    .removeChatUser(peerId, userId, chatActionCallback(null)))
                            .show();
                }).show();
    }

    private void promptAddMember() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.chat_enter_user_id);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.chat_add_member_action).setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        int userId = Integer.parseInt(input.getText().toString().trim());
                        MessagesManager.getInstance(this).addChatUser(peerId, userId, chatActionCallback(null));
                    } catch (NumberFormatException e) { showChatActionError(); }
                }).show();
    }

    private void promptRenameChat() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.chat_rename_hint);
        input.setText(peerName);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.chat_rename_action).setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String title = input.getText().toString().trim();
                    if (!title.isEmpty()) MessagesManager.getInstance(this).editChat(chatId, title, chatActionCallback(title));
                }).show();
    }

    private OpenVKApi.ApiCallback chatActionCallback(String newTitle) {
        return new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (newTitle != null) {
                    peerName = newTitle;
                    setToolbarTitle(newTitle);
                }
                Toast.makeText(ChatActivity.this, R.string.chat_action_success, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(String error) { showChatActionError(error); }
        };
    }

    private void onChatPhotoPicked(Uri uri) {
        if (uri == null) return;
        PhotoUploadManager.getInstance(this).uploadChatPhoto(uri, chatId, chatActionCallback(null));
    }

    private void showChatActionError() { showChatActionError(getString(R.string.chat_group_options_error)); }

    private void showChatActionError(String error) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    private void setupToolbar(String title) {
        toolbarTitle = title != null ? title : getString(R.string.chat_title);
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                getSupportActionBar().setDisplayShowCustomEnabled(true);
                toolbar.setContentInsetsRelative((int) (16 * getResources().getDisplayMetrics().density), 0);
                toolbarHeader = getLayoutInflater().inflate(R.layout.chat_toolbar_header, toolbar, false);
                getSupportActionBar().setCustomView(toolbarHeader,
                        new androidx.appcompat.app.ActionBar.LayoutParams(
                                androidx.appcompat.app.ActionBar.LayoutParams.WRAP_CONTENT,
                                androidx.appcompat.app.ActionBar.LayoutParams.MATCH_PARENT,
                                android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL));
                toolbarAvatar = toolbarHeader.findViewById(R.id.chat_toolbar_avatar);
                toolbarTitleView = toolbarHeader.findViewById(R.id.chat_toolbar_title);
                toolbarSubtitleView = toolbarHeader.findViewById(R.id.chat_toolbar_subtitle);
                toolbarHeader.setOnClickListener(v -> openChatHeaderOptions());
                updateToolbarTitle();
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    private void loadToolbarHeader() {
        if (peerId > 2000000000) {
            java.util.Map<String, String> params = new java.util.HashMap<>();
            params.put("peer_ids", String.valueOf(peerId));
            params.put("extended", "1");
            OpenVKApi.getInstance(this).callMethod("messages.getConversationsById", params, new OpenVKApi.ApiCallback() {
                @Override public void onSuccess(JSONObject response) {
                    JSONObject data = response.optJSONObject("response");
                    org.json.JSONArray items = data != null ? data.optJSONArray("items") : null;
                    JSONObject item = items != null && items.length() > 0 ? items.optJSONObject(0) : null;
                    JSONObject conversation = item != null ? item.optJSONObject("conversation") : null;
                    JSONObject settings = conversation != null ? conversation.optJSONObject("chat_settings") : null;
                    JSONObject photo = settings != null ? settings.optJSONObject("photo") : null;
                    String url = photo != null ? photo.optString("photo_50", photo.optString("photo_200", "")) : "";
                    runOnUiThread(() -> {
                        if (!toolbarHeaderActive) showToolbarAvatar(url);
                    });
                }
                @Override public void onError(String error) { }
            });
            return;
        }

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("user_ids", String.valueOf(peerId));
        params.put("fields", "photo_50,online");
        OpenVKApi.getInstance(this).callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                org.json.JSONArray users = response.optJSONArray("response");
                JSONObject user = users != null && users.length() > 0 ? users.optJSONObject(0) : null;
                if (user == null) return;
                String url = user.optString("photo_50", "");
                runOnUiThread(() -> {
                    if (toolbarHeaderActive) return;
                    toolbarOnline = user.optInt("online", 0) == 1;
                    updateToolbarSubtitle();
                    showToolbarAvatar(url);
                });
            }
            @Override public void onError(String error) { }
        });
    }

    private void showToolbarAvatar(String url) {
        if (toolbarAvatar == null || url == null || url.isEmpty()) return;
        com.squareup.picasso.RequestCreator request = Picasso.get().load(url).placeholder(R.drawable.camera_200);
        if (getSharedPreferences("appearance_settings", MODE_PRIVATE)
                .getBoolean(AppearanceSettingsFragment.KEY_ROUND_AVATARS, true)) {
            request.transform(new CircularImageTransformation());
        }
        request.into(toolbarAvatar);
    }

    public void setChatTyping(boolean typing) {
        toolbarTyping = typing;
        updateToolbarSubtitle();
    }

    private void updateToolbarSubtitle() {
        if (toolbarSubtitleView == null) return;
        String status = toolbarTyping ? getString(R.string.chat_typing)
                : toolbarOnline ? getString(R.string.profile_status_online) : "";
        toolbarSubtitleView.setText(status);
        toolbarSubtitleView.setVisibility(status.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    public void setToolbarTitle(String title) {
        if (title != null) toolbarTitle = title;
        updateToolbarTitle();
    }

    public void showProfileToolbar(int userId, String displayName) {
        toolbarHeaderActive = true;
        toolbarProfileUserId = userId;
        toolbarTitle = displayName != null && !displayName.trim().isEmpty()
                ? displayName : getString(R.string.profile_title);
        toolbarOnline = false;
        toolbarTyping = false;
        if (toolbarHeader != null) toolbarHeader.setOnClickListener(null);
        if (toolbarAvatar != null) toolbarAvatar.setImageResource(R.drawable.camera_200);
        updateToolbarTitle();

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("user_ids", String.valueOf(userId));
        params.put("fields", "photo_50,online");
        OpenVKApi.getInstance(this).callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                org.json.JSONArray users = response.optJSONArray("response");
                JSONObject user = users != null && users.length() > 0 ? users.optJSONObject(0) : null;
                if (user == null) return;
                String avatarUrl = user.optString("photo_50", "");
                boolean online = user.optInt("online", 0) == 1;
                runOnUiThread(() -> {
                    if (!toolbarHeaderActive || toolbarProfileUserId != userId) return;
                    toolbarOnline = online;
                    updateToolbarSubtitle();
                    showToolbarAvatar(avatarUrl);
                });
            }
            @Override public void onError(String error) { }
        });
    }

    private void updateNotificationMuteIndicator(MessagesManager.PushSettings settings) {
        chatNotificationsMuted = !settings.areNotificationsEnabled();
        if (notificationExpiryRunnable != null) {
            notificationStateHandler.removeCallbacks(notificationExpiryRunnable);
            notificationExpiryRunnable = null;
        }

        long disabledUntil = settings.getDisabledUntil();
        long remainingSeconds = disabledUntil - System.currentTimeMillis() / 1000L;
        if (chatNotificationsMuted && disabledUntil > 0 && remainingSeconds > 0) {
            notificationExpiryRunnable = () -> {
                chatNotificationsMuted = false;
                updateToolbarTitle();
            };
            notificationStateHandler.postDelayed(notificationExpiryRunnable, remainingSeconds * 1000L);
        }
        updateToolbarTitle();
    }

    private void updateToolbarTitle() {
        if (toolbarTitle == null) return;
        if (toolbarTitleView != null) {
            toolbarTitleView.setText(toolbarTitle);
            Drawable muteIcon = chatNotificationsMuted && isChatScreenVisible()
                    ? ContextCompat.getDrawable(this, R.drawable.ic_notifications_off) : null;
            if (muteIcon != null) {
                muteIcon = DrawableCompat.wrap(muteIcon.mutate());
                int size = (int) (18 * getResources().getDisplayMetrics().density + 0.5f);
                muteIcon.setBounds(0, 0, size, size);
            }
            toolbarTitleView.setCompoundDrawablesRelative(null, null, muteIcon, null);
            toolbarTitleView.setCompoundDrawablePadding((int) (6 * getResources().getDisplayMetrics().density));
            updateToolbarSubtitle();
            return;
        }
        CharSequence displayTitle = toolbarTitle;
        if (chatNotificationsMuted && isChatScreenVisible()) {
            Drawable icon = ContextCompat.getDrawable(this, R.drawable.ic_notifications_off);
            if (icon != null) {
                icon = DrawableCompat.wrap(icon.mutate());
                int size = (int) (18 * getResources().getDisplayMetrics().density + 0.5f);
                icon.setBounds(0, 0, size, size);
                SpannableStringBuilder decoratedTitle = new SpannableStringBuilder(toolbarTitle).append("  ");
                int iconStart = decoratedTitle.length();
                decoratedTitle.append("\uFFFC");
                decoratedTitle.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), iconStart,
                        decoratedTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                displayTitle = decoratedTitle;
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(displayTitle);
        } else if (toolbar != null) {
            toolbar.setTitle(displayTitle);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (isChatScreenVisible() && item.getItemId() == R.id.action_chat_notifications) {
            openNotificationOptions();
            return true;
        }
        if (isChatScreenVisible() && item.getItemId() == R.id.action_chat_options && chatId > 0) {
            openGroupOptions();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
