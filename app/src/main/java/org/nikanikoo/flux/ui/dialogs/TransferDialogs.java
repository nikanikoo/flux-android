package org.nikanikoo.flux.ui.dialogs;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.FriendsManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Friend;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransferDialogs {

    private static final String TAG = "TransferDialogs";

    public interface OnTransferListener {
        void onTransferSuccess();
    }

    public static void showSelectUserDialog(Context context, OnTransferListener listener) {
        BottomSheetDialog selectDialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_transfer_select, null);

        TextInputEditText editSearch = view.findViewById(R.id.edit_search);
        MaterialButton btnSort = view.findViewById(R.id.btn_sort);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        RecyclerView rvUsers = view.findViewById(R.id.rv_users);
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        TextView tvEmpty = view.findViewById(R.id.tv_empty);

        final int[] activeSort = {4};

        List<Friend> friendsList = new ArrayList<>();

        TransferUserAdapter adapter = new TransferUserAdapter(context, new ArrayList<>(), user -> {
            selectDialog.dismiss();
            showConfirmDialog(context, user, listener);
        });
        rvUsers.setLayoutManager(new LinearLayoutManager(context));
        rvUsers.setAdapter(adapter);

        tabLayout.addTab(tabLayout.newTab().setText(context.getString(R.string.transfer_tab_friends)));
        tabLayout.addTab(tabLayout.newTab().setText(context.getString(R.string.transfer_tab_search)));

        Runnable loadFriends = () -> {
            progressBar.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            FriendsManager.getInstance(context).getFriends(100, 0, new FriendsManager.FriendsCallback() {
                @Override
                public void onSuccess(List<Friend> friends) {
                    progressBar.setVisibility(View.GONE);
                    friendsList.clear();
                    friendsList.addAll(friends);
                    if (tabLayout.getSelectedTabPosition() == 0) {
                        adapter.updateUsers(friendsList);
                        tvEmpty.setVisibility(friendsList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void onError(String error) {
                    progressBar.setVisibility(View.GONE);
                    if (tabLayout.getSelectedTabPosition() == 0) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(context.getString(R.string.transfer_friends_load_error));
                    }
                }
            });
        };

        loadFriends.run();

        Runnable performSearch = () -> {
            String query = editSearch.getText() != null ? editSearch.getText().toString().trim() : "";
            int activeTab = tabLayout.getSelectedTabPosition();

            btnSort.setVisibility(activeTab == 0 ? View.GONE : View.VISIBLE);

            if (activeTab == 0) {
                if (query.isEmpty()) {
                    adapter.updateUsers(friendsList);
                    tvEmpty.setVisibility(friendsList.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);
                    FriendsManager.getInstance(context).searchFriends(query, 100, new FriendsManager.FriendsCallback() {
                        @Override
                        public void onSuccess(List<Friend> friends) {
                            progressBar.setVisibility(View.GONE);
                            adapter.updateUsers(friends);
                            tvEmpty.setVisibility(friends.isEmpty() ? View.VISIBLE : View.GONE);
                        }

                        @Override
                        public void onError(String error) {
                            progressBar.setVisibility(View.GONE);
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText(context.getString(R.string.transfer_no_results));
                        }
                    });
                }
            } else {
                progressBar.setVisibility(View.VISIBLE);
                tvEmpty.setVisibility(View.GONE);

                if (!query.isEmpty() && query.matches("^\\d+$")) {
                    Map<String, String> getParams = new HashMap<>();
                    getParams.put("user_ids", query);
                    getParams.put("fields", "photo_50,photo_100,online,screen_name,status,verified");

                    OpenVKApi.getInstance(context).callMethod("users.get", getParams, new OpenVKApi.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            try {
                                JSONArray responseArray = response.getJSONArray("response");
                                List<Friend> idResults = new ArrayList<>();
                                Friend exactUserFinal = null;
                                if (responseArray.length() > 0) {
                                    JSONObject userJson = responseArray.getJSONObject(0);
                                    exactUserFinal = Friend.fromJson(userJson);
                                    idResults.add(exactUserFinal);
                                }
                                
                                final Friend exactUser = exactUserFinal;
                                
                                runGlobalSearch(context, query, activeSort[0], results -> {
                                    progressBar.setVisibility(View.GONE);
                                    List<Friend> merged = new ArrayList<>(idResults);
                                    for (Friend u : results) {
                                        if (exactUser == null || u.getId() != exactUser.getId()) {
                                            merged.add(u);
                                        }
                                    }
                                    adapter.updateUsers(merged);
                                    tvEmpty.setVisibility(merged.isEmpty() ? View.VISIBLE : View.GONE);
                                }, error -> {
                                    progressBar.setVisibility(View.GONE);
                                    adapter.updateUsers(idResults);
                                    tvEmpty.setVisibility(idResults.isEmpty() ? View.VISIBLE : View.GONE);
                                });

                            } catch (Exception e) {
                                runGlobalSearch(context, query, activeSort[0], results -> {
                                    progressBar.setVisibility(View.GONE);
                                    adapter.updateUsers(results);
                                    tvEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
                                }, error -> {
                                    progressBar.setVisibility(View.GONE);
                                    tvEmpty.setVisibility(View.VISIBLE);
                                    tvEmpty.setText(context.getString(R.string.transfer_search_error));
                                });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            runGlobalSearch(context, query, activeSort[0], results -> {
                                progressBar.setVisibility(View.GONE);
                                adapter.updateUsers(results);
                                tvEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
                            }, err -> {
                                progressBar.setVisibility(View.GONE);
                                tvEmpty.setVisibility(View.VISIBLE);
                                tvEmpty.setText(context.getString(R.string.transfer_search_error));
                            });
                        }
                    });
                } else {
                    runGlobalSearch(context, query, activeSort[0], results -> {
                        progressBar.setVisibility(View.GONE);
                        adapter.updateUsers(results);
                        tvEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
                    }, error -> {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(context.getString(R.string.transfer_search_error));
                    });
                }
            }
        };

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                performSearch.run();
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                performSearch.run();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnSort.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, btnSort);
            popup.getMenu().add(0, 4, 0, context.getString(R.string.transfer_sort_rating));
            popup.getMenu().add(0, 0, 1, context.getString(R.string.transfer_sort_new));
            popup.getMenu().add(0, 1, 2, context.getString(R.string.transfer_sort_old));

            popup.getMenu().findItem(activeSort[0]).setCheckable(true).setChecked(true);

            popup.setOnMenuItemClickListener(item -> {
                activeSort[0] = item.getItemId();
                performSearch.run();
                return true;
            });
            popup.show();
        });

        selectDialog.setContentView(view);
        selectDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        selectDialog.show();
    }

    private static void runGlobalSearch(Context context, String query, int sort, 
                                        OnSearchSuccess success, OnSearchError error) {
        Map<String, String> params = new HashMap<>();
        params.put("q", query);
        params.put("count", "100");
        params.put("sort", String.valueOf(sort));
        params.put("fields", "photo_50,photo_100,online,screen_name,status,verified");

        OpenVKApi.getInstance(context).callMethod("users.search", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Friend> results = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        results.add(Friend.fromJson(items.getJSONObject(i)));
                    }
                    success.onSearch(results);
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to parse users.search response", e);
                    error.onError(e.getMessage());
                }
            }

            @Override
            public void onError(String err) {
                Logger.e(TAG, "users.search error: " + err);
                error.onError(err);
            }
        });
    }

    interface OnSearchSuccess {
        void onSearch(List<Friend> results);
    }

    interface OnSearchError {
        void onError(String err);
    }

    private static void showConfirmDialog(Context context, Friend user, OnTransferListener listener) {
        BottomSheetDialog confirmDialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_transfer_confirm, null);

        com.google.android.material.imageview.ShapeableImageView recipientAvatar = view.findViewById(R.id.recipient_avatar);
        TextView recipientName = view.findViewById(R.id.recipient_name);
        TextView recipientUsername = view.findViewById(R.id.recipient_username);
        TextInputEditText editVotesAmount = view.findViewById(R.id.edit_votes_amount);
        TextInputEditText editVotesMessage = view.findViewById(R.id.edit_votes_message);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel_transfer);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm_transfer);

        recipientName.setText(user.getFullName());
        if (user.getScreenName() != null && !user.getScreenName().isEmpty()) {
            recipientUsername.setText("@" + user.getScreenName());
        } else {
            recipientUsername.setText("ID: " + user.getId());
        }

        if (user.getPhoto50() != null && !user.getPhoto50().isEmpty()) {
            Picasso.get()
                    .load(user.getPhoto50())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .resize(Constants.UI.THUMBNAIL_SIZE, Constants.UI.THUMBNAIL_SIZE)
                    .centerCrop()
                    .into(recipientAvatar);
        } else {
            recipientAvatar.setImageResource(R.drawable.camera_200);
        }

        btnCancel.setOnClickListener(v -> confirmDialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String amountStr = editVotesAmount.getText() != null ? editVotesAmount.getText().toString().trim() : "";
            if (amountStr.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.transfer_err_empty_amount), Toast.LENGTH_SHORT).show();
                return;
            }

            int amount;
            try {
                amount = Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(context, context.getString(R.string.transfer_err_invalid_format), Toast.LENGTH_SHORT).show();
                return;
            }

            if (amount <= 0) {
                Toast.makeText(context, context.getString(R.string.transfer_err_greater_than_zero), Toast.LENGTH_SHORT).show();
                return;
            }

            String message = editVotesMessage.getText() != null ? editVotesMessage.getText().toString().trim() : "";

            btnConfirm.setEnabled(false);
            btnCancel.setEnabled(false);

            Map<String, String> params = new HashMap<>();
            params.put("reciever", String.valueOf(user.getId()));
            params.put("receiver", String.valueOf(user.getId()));
            params.put("value", String.valueOf(amount));
            if (!message.isEmpty()) {
                params.put("message", message);
            }

            OpenVKApi.getInstance(context).callMethod("account.sendVotes", params, new OpenVKApi.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    confirmDialog.dismiss();
                    Toast.makeText(context, context.getString(R.string.transfer_success), Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onTransferSuccess();
                    }
                }

                @Override
                public void onError(String error) {
                    btnConfirm.setEnabled(true);
                    btnCancel.setEnabled(true);

                    String errorText = context.getString(R.string.error) + ": " + error;
                    if (error.contains("-105") || error.contains("Commerce is disabled")) {
                        errorText = context.getString(R.string.transfer_err_commerce_disabled);
                    } else if (error.contains("-252") || error.contains("Not enough votes")) {
                        errorText = context.getString(R.string.transfer_err_not_enough_votes);
                    } else if (error.contains("-251") || error.contains("yourself")) {
                        errorText = context.getString(R.string.transfer_err_self_transfer);
                    } else if (error.contains("-248") || error.contains("-250")) {
                        errorText = context.getString(R.string.transfer_err_invalid_recipient);
                    } else if (error.contains("-249")) {
                        errorText = context.getString(R.string.transfer_err_message_too_long);
                    }
                    Toast.makeText(context, errorText, Toast.LENGTH_LONG).show();
                }
            });
        });

        confirmDialog.setContentView(view);
        confirmDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        confirmDialog.show();
    }

    static class TransferUserAdapter extends RecyclerView.Adapter<TransferUserAdapter.ViewHolder> {
        private final List<Friend> users;
        private final Context context;
        private final OnUserClickListener listener;

        interface OnUserClickListener {
            void onUserClick(Friend user);
        }

        public TransferUserAdapter(Context context, List<Friend> users, OnUserClickListener listener) {
            this.context = context;
            this.users = users;
            this.listener = listener;
        }

        public void updateUsers(List<Friend> newUsers) {
            this.users.clear();
            this.users.addAll(newUsers);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_transfer_user, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Friend user = users.get(position);
            holder.userName.setText(user.getFullName());
            
            if (user.getScreenName() != null && !user.getScreenName().isEmpty()) {
                holder.userInfo.setText("@" + user.getScreenName());
            } else {
                holder.userInfo.setText("ID: " + user.getId());
            }

            if (user.getPhoto50() != null && !user.getPhoto50().isEmpty()) {
                Picasso.get()
                        .load(user.getPhoto50())
                        .placeholder(R.drawable.camera_200)
                        .error(R.drawable.camera_200)
                        .resize(Constants.UI.THUMBNAIL_SIZE, Constants.UI.THUMBNAIL_SIZE)
                        .centerCrop()
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.camera_200);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserClick(user);
                }
            });
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            com.google.android.material.imageview.ShapeableImageView avatar;
            TextView userName;
            TextView userInfo;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.avatar_image);
                userName = itemView.findViewById(R.id.user_name);
                userInfo = itemView.findViewById(R.id.user_info);
            }
        }
    }
}
