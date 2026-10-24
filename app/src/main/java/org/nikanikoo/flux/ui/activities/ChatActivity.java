package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.Menu;
import android.widget.EditText;
import android.widget.Toast;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.MessagesManager;
import org.nikanikoo.flux.data.managers.PhotoUploadManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.fragments.messages.ChatFragment;
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
    private int peerId;
    private int chatId;
    private String peerName;
    private ActivityResultLauncher<String> chatPhotoPicker;

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

        ThemeManager.applySystemBarsAppearance(this);

        int fromId = getIntent().getIntExtra(EXTRA_FROM_ID, peerId);

        setupToolbar(peerName);

        if (savedInstanceState == null) {
            ChatFragment chatFragment = ChatFragment.newInstance(peerId, peerName, fromId);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.chat_container, chatFragment)
                .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (chatId > 0) {
            getMenuInflater().inflate(R.menu.menu_chat, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void openGroupOptions() {
        String[] options = {
                getString(R.string.chat_members_action),
                getString(R.string.chat_add_member_action),
                getString(R.string.chat_rename_action),
                getString(R.string.chat_change_photo_action)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(peerName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) loadChatMembers();
                    else if (which == 1) promptAddMember();
                    else if (which == 2) promptRenameChat();
                    else chatPhotoPicker.launch("image/*");
                })
                .show();
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
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(title != null ? title : getString(R.string.chat_title));
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    public void setToolbarTitle(String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        } else if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_chat_options && chatId > 0) {
            openGroupOptions();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
