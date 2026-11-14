package org.nikanikoo.flux.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.StickersManager;
import org.nikanikoo.flux.data.models.Sticker;

import java.util.List;

public final class StickerPickerDialog {
    public interface OnStickerSelectedListener {
        void onStickerSelected(Sticker sticker);
    }

    private StickerPickerDialog() {}

    public static void show(Context context, OnStickerSelectedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 24));

        TextView title = new TextView(context);
        title.setText(R.string.stickers_picker_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurface));
        title.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 48)));

        MaterialButton storeButton = new MaterialButton(context);
        storeButton.setText(R.string.sticker_store_open);
        LinearLayout.LayoutParams storeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        storeParams.bottomMargin = dp(context, 8);
        content.addView(storeButton, storeParams);
        storeButton.setOnClickListener(v -> {
            dialog.dismiss();
            StickerStoreDialog.show(context, () -> show(context, listener));
        });

        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setFillViewport(true);
        scroll.setNestedScrollingEnabled(true);
        LinearLayout packsContainer = new LinearLayout(context);
        packsContainer.setOrientation(LinearLayout.VERTICAL);
        addMessage(context, packsContainer, R.string.stickers_loading);
        scroll.addView(packsContainer);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 360)));
        dialog.setContentView(content);
        dialog.show();

        StickersManager.getInstance(context).getOwnedStickers(new StickersManager.StickersCallback() {
            @Override
            public void onSuccess(List<Sticker> stickers) {
                if (!dialog.isShowing()) return;
                packsContainer.removeAllViews();
                if (stickers.isEmpty()) {
                    addMessage(context, packsContainer, R.string.stickers_empty);
                    return;
                }

                String currentPack = null;
                LinearLayout row = null;
                int inRow = 0;
                for (Sticker sticker : stickers) {
                    String packName = sticker.getPackName() == null ? "" : sticker.getPackName();
                    if (!packName.equals(currentPack)) {
                        currentPack = packName;
                        TextView packTitle = new TextView(context);
                        packTitle.setText(packName);
                        packTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        packTitle.setTextColor(resolveColor(context,
                                com.google.android.material.R.attr.colorOnSurfaceVariant));
                        packTitle.setPadding(0, dp(context, 14), 0, dp(context, 4));
                        packsContainer.addView(packTitle);
                        row = null;
                        inRow = 0;
                    }
                    if (row == null || inRow == 4) {
                        row = new LinearLayout(context);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        packsContainer.addView(row, new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 84)));
                        inRow = 0;
                    }

                    ImageView image = new ImageView(context);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
                    image.setContentDescription(sticker.getEmoji().isEmpty()
                            ? context.getString(R.string.message_sticker_image) : sticker.getEmoji());
                    image.setBackgroundResource(android.R.drawable.list_selector_background);
                    image.setClickable(true);
                    image.setFocusable(true);
                    Picasso.get().load(sticker.getImageUrl()).into(image);
                    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                    row.addView(image, imageParams);
                    image.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (listener != null) listener.onStickerSelected(sticker);
                    });
                    inRow++;
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

    private static void addMessage(Context context, LinearLayout parent, int stringId) {
        TextView message = new TextView(context);
        message.setText(stringId);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        message.setGravity(Gravity.CENTER);
        parent.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 120)));
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
}
