package org.nikanikoo.flux.ui.adapters.friends;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.card.MaterialCardView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.Friend;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConversationFriendPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnFriendToggleListener { void onFriendToggled(Friend friend); }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_FRIEND = 1;
    private final Context context;
    private final Set<Integer> selectedIds;
    private final OnFriendToggleListener listener;
    private final List<Object> rows = new ArrayList<>();

    public ConversationFriendPickerAdapter(Context context, Set<Integer> selectedIds,
                                            OnFriendToggleListener listener) {
        this.context = context;
        this.selectedIds = selectedIds;
        this.listener = listener;
    }

    public void setFriends(List<Friend> friends) {
        rows.clear();
        String previousLetter = null;
        for (Friend friend : friends) {
            String name = friend.getFullName() == null ? "" : friend.getFullName().trim();
            String letter = name.isEmpty() ? "#" : name.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
            if (!letter.equals(previousLetter)) {
                rows.add(letter);
                previousLetter = letter;
            }
            rows.add(friend);
        }
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        return rows.get(position) instanceof Friend ? TYPE_FRIEND : TYPE_HEADER;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        if (type == TYPE_HEADER) {
            TextView header = new TextView(context);
            header.setTextColor(themeColor(androidx.appcompat.R.attr.colorPrimary,
                    android.R.color.holo_blue_dark));
            header.setTextSize(14);
            header.setTypeface(null, Typeface.BOLD);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(16), dp(8), dp(16), dp(4));
            return new HeaderHolder(header);
        }

        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(14));
        card.setCardElevation(0);
        card.setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer,
                android.R.color.white));
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        ShapeableImageView avatar = new ShapeableImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setShapeAppearanceModel(com.google.android.material.shape.ShapeAppearanceModel.builder()
                .setAllCornerSizes(dp(28)).build());
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        labelsParams.leftMargin = dp(12);
        row.addView(labels, labelsParams);
        TextView name = new TextView(context);
        name.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface,
                android.R.color.black));
        name.setTextSize(16);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView status = new TextView(context);
        status.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.R.color.darker_gray));
        status.setTextSize(13);
        status.setMaxLines(1);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(status);
        MaterialCheckBox check = new MaterialCheckBox(context);
        check.setClickable(false);
        check.setFocusable(false);
        row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(row);
        card.setClickable(true);
        card.setFocusable(true);
        card.setForeground(ContextCompat.getDrawable(context, android.R.drawable.list_selector_background));
        return new FriendHolder(card, avatar, name, status, check);
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).text.setText((String) row);
            return;
        }
        Friend friend = (Friend) row;
        FriendHolder friendHolder = (FriendHolder) holder;
        friendHolder.name.setText(friend.getFullName().trim());
        String subtitle = friend.getStatus() == null || friend.getStatus().trim().isEmpty()
                ? (friend.isOnline() ? context.getString(R.string.profile_status_online)
                : context.getString(R.string.profile_status_offline)) : friend.getStatus();
        friendHolder.status.setText(subtitle);
        friendHolder.check.setChecked(selectedIds.contains(friend.getId()));
        if (friend.getPhoto50() == null || friend.getPhoto50().isEmpty()) {
            friendHolder.avatar.setImageResource(R.drawable.camera_200);
        } else {
            Picasso.get().load(friend.getPhoto50()).placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200).resize(dp(96), dp(96)).centerCrop()
                    .into(friendHolder.avatar);
        }
        friendHolder.itemView.setOnClickListener(v -> listener.onFriendToggled(friend));
    }

    @Override public int getItemCount() { return rows.size(); }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private int themeColor(int attribute, int fallbackResource) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) return value.data;
        return ContextCompat.getColor(context, fallbackResource);
    }

    private static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;
        HeaderHolder(TextView text) { super(text); this.text = text; }
    }

    private static class FriendHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView avatar;
        final TextView name;
        final TextView status;
        final MaterialCheckBox check;
        FriendHolder(View itemView, ShapeableImageView avatar, TextView name,
                     TextView status, MaterialCheckBox check) {
            super(itemView);
            this.avatar = avatar;
            this.name = name;
            this.status = status;
            this.check = check;
        }
    }
}
