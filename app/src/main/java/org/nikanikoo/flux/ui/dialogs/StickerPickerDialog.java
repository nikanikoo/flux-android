package org.nikanikoo.flux.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.StickersManager;
import org.nikanikoo.flux.data.models.Sticker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StickerPickerDialog {
    public interface OnStickerSelectedListener {
        void onStickerSelected(Sticker sticker);
    }

    private StickerPickerDialog() {}

    public static void show(Context context, OnStickerSelectedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(context, 16);
        content.setPadding(horizontalPadding, dp(context, 20), horizontalPadding, dp(context, 12));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(context);
        title.setText(R.string.stickers_picker_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurface));
        header.addView(title, new LinearLayout.LayoutParams(0, dp(context, 56), 1f));

        MaterialButton storeButton = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        storeButton.setText(R.string.sticker_store_open);
        storeButton.setMinHeight(dp(context, 40));
        header.addView(storeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 40)));
        content.addView(header);
        storeButton.setOnClickListener(v -> {
            dialog.dismiss();
            StickerStoreDialog.show(context, () -> show(context, listener));
        });

        HorizontalScrollView packScroll = new HorizontalScrollView(context);
        packScroll.setHorizontalScrollBarEnabled(false);
        packScroll.setFillViewport(false);
        packScroll.setHorizontalScrollBarEnabled(false);
        ChipGroup packTabs = new ChipGroup(context);
        packTabs.setSingleSelection(true);
        packTabs.setSelectionRequired(true);
        packTabs.setSingleLine(true);
        packScroll.addView(packTabs, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(packScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)));

        FrameLayout contentFrame = new FrameLayout(context);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 360));
        frameParams.topMargin = dp(context, 4);
        content.addView(contentFrame, frameParams);

        RecyclerView grid = new RecyclerView(context);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(context, 4), 0, dp(context, 8));
        int screenWidthDp = Math.round(context.getResources().getDisplayMetrics().widthPixels
                / context.getResources().getDisplayMetrics().density);
        int columns = Math.min(5, Math.max(3, screenWidthDp / 76));
        grid.setLayoutManager(new GridLayoutManager(context, columns));
        StickerGridAdapter stickerAdapter = new StickerGridAdapter(context, sticker -> {
            dialog.dismiss();
            if (listener != null) listener.onStickerSelected(sticker);
        });
        grid.setAdapter(stickerAdapter);
        contentFrame.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout loading = new LinearLayout(context);
        loading.setGravity(Gravity.CENTER);
        loading.setOrientation(LinearLayout.VERTICAL);
        ProgressBar progress = new ProgressBar(context);
        loading.addView(progress, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
        TextView loadingText = new TextView(context);
        loadingText.setText(R.string.stickers_loading);
        loadingText.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        loadingText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadingTextParams.topMargin = dp(context, 8);
        loading.addView(loadingText, loadingTextParams);
        contentFrame.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dialog.setContentView(content);
        dialog.show();

        StickersManager.getInstance(context).getOwnedStickers(new StickersManager.StickersCallback() {
            @Override
            public void onSuccess(List<Sticker> stickers) {
                if (!dialog.isShowing()) return;
                loading.setVisibility(View.GONE);
                packTabs.removeAllViews();
                for (int i = contentFrame.getChildCount() - 1; i >= 0; i--) {
                    View child = contentFrame.getChildAt(i);
                    if (child != grid && child != loading) contentFrame.removeViewAt(i);
                }
                if (stickers.isEmpty()) {
                    addEmptyState(context, contentFrame);
                    return;
                }

                Map<String, List<Sticker>> packs = new LinkedHashMap<>();
                for (Sticker sticker : stickers) {
                    String packName = sticker.getPackName() == null || sticker.getPackName().trim().isEmpty()
                            ? context.getString(R.string.stickers_default_pack) : sticker.getPackName().trim();
                    packs.computeIfAbsent(packName, ignored -> new ArrayList<>()).add(sticker);
                }

                boolean[] firstTab = {true};
                for (Map.Entry<String, List<Sticker>> pack : packs.entrySet()) {
                    Chip chip = new Chip(context);
                    chip.setText(pack.getKey());
                    chip.setCheckable(true);
                    chip.setClickable(true);
                    chip.setEnsureMinTouchTargetSize(true);
                    packTabs.addView(chip, new ChipGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    chip.setOnClickListener(v -> {
                        if (!chip.isChecked()) chip.setChecked(true);
                        stickerAdapter.setStickers(pack.getValue());
                        grid.scrollToPosition(0);
                    });
                    if (firstTab[0]) {
                        firstTab[0] = false;
                        chip.setChecked(true);
                        stickerAdapter.setStickers(pack.getValue());
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (dialog.isShowing()) dialog.dismiss();
                Toast.makeText(context, context.getString(R.string.stickers_load_error, error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void addEmptyState(Context context, FrameLayout parent) {
        TextView message = new TextView(context);
        message.setText(R.string.stickers_empty);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        message.setGravity(Gravity.CENTER);
        parent.addView(message, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static int resolveColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) return ContextCompat.getColor(context, value.resourceId);
            return value.data;
        }
        return Color.BLACK;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static class StickerGridAdapter extends RecyclerView.Adapter<StickerGridAdapter.StickerHolder> {
        interface OnClickListener { void onClick(Sticker sticker); }
        private final Context context;
        private final OnClickListener listener;
        private final List<Sticker> stickers = new ArrayList<>();

        StickerGridAdapter(Context context, OnClickListener listener) {
            this.context = context;
            this.listener = listener;
        }

        void setStickers(List<Sticker> stickers) {
            this.stickers.clear();
            this.stickers.addAll(stickers);
            notifyDataSetChanged();
        }

        @NonNull @Override public StickerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ShapeableImageView image = new ShapeableImageView(context);
            image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            image.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
            image.setShapeAppearanceModel(com.google.android.material.shape.ShapeAppearanceModel.builder()
                    .setAllCornerSizes(dp(context, 14)).build());
            image.setForeground(ContextCompat.getDrawable(context, android.R.drawable.list_selector_background));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 80));
            params.setMargins(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
            image.setLayoutParams(params);
            image.setClickable(true);
            image.setFocusable(true);
            return new StickerHolder(image);
        }

        @Override public void onBindViewHolder(@NonNull StickerHolder holder, int position) {
            Sticker sticker = stickers.get(position);
            holder.image.setContentDescription(sticker.getEmoji().isEmpty()
                    ? context.getString(R.string.message_sticker_image) : sticker.getEmoji());
            Picasso.get().load(sticker.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .fit().centerInside().into(holder.image);
            holder.image.setOnClickListener(v -> listener.onClick(sticker));
        }

        @Override public int getItemCount() { return stickers.size(); }

        private static class StickerHolder extends RecyclerView.ViewHolder {
            final ShapeableImageView image;
            StickerHolder(ShapeableImageView image) { super(image); this.image = image; }
        }
    }
}
