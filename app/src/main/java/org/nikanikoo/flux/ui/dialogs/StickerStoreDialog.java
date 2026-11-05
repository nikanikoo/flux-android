package org.nikanikoo.flux.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.StickersManager;
import org.nikanikoo.flux.data.models.StickerPack;

import java.util.List;

public final class StickerStoreDialog {
    private StickerStoreDialog() {}

    public static void show(Context context, Runnable onPurchased) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 24));

        TextView title = new TextView(context);
        title.setText(R.string.sticker_store_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurface));
        title.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 48)));

        ScrollView scroll = new ScrollView(context);
        LinearLayout packsList = new LinearLayout(context);
        packsList.setOrientation(LinearLayout.VERTICAL);
        TextView loading = new TextView(context);
        loading.setText(R.string.stickers_loading_catalog);
        loading.setGravity(Gravity.CENTER);
        packsList.addView(loading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 120)));
        scroll.addView(packsList);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 420)));
        dialog.setContentView(content);
        dialog.show();

        StickersManager manager = StickersManager.getInstance(context);
        manager.getAllPacks(new StickersManager.StickerPacksCallback() {
            @Override
            public void onSuccess(List<StickerPack> packs) {
                if (!dialog.isShowing()) return;
                packsList.removeAllViews();
                if (packs.isEmpty()) {
                    addStatus(context, packsList, R.string.sticker_store_empty);
                    return;
                }
                for (StickerPack pack : packs) addPack(context, packsList, pack, manager, dialog, onPurchased);
            }

            @Override
            public void onError(String error) {
                if (dialog.isShowing()) dialog.dismiss();
                Toast.makeText(context, context.getString(R.string.stickers_load_error, error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void addPack(Context context, LinearLayout parent, StickerPack pack,
                                StickersManager manager, BottomSheetDialog dialog, Runnable onPurchased) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 10), dp(context, 8), dp(context, 10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(resolveColor(context, com.google.android.material.R.attr.colorSurfaceContainer));
        background.setCornerRadius(dp(context, 16));
        card.setBackground(background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(context, 10);
        parent.addView(card, cardParams);

        ImageView preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (pack.getPreviewUrl() != null && !pack.getPreviewUrl().isEmpty()) {
            Picasso.get().load(pack.getPreviewUrl()).fit().centerInside().into(preview);
        }
        card.addView(preview, new LinearLayout.LayoutParams(dp(context, 72), dp(context, 72)));

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(context, 12), 0, dp(context, 6), 0);
        card.addView(details, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(context);
        name.setText(pack.getName());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurface));
        name.setMaxLines(2);
        details.addView(name);

        if (pack.getDescription() != null && !pack.getDescription().isEmpty()) {
            TextView description = new TextView(context);
            description.setText(pack.getDescription());
            description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            description.setTextColor(resolveColor(context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            description.setMaxLines(2);
            details.addView(description);
        }

        TextView price = new TextView(context);
        if (pack.isPurchased()) {
            price.setText(R.string.sticker_pack_owned);
        } else if (!pack.isAvailable()) {
            price.setText(R.string.sticker_pack_unavailable);
        } else if (pack.getPrice() <= 0) {
            price.setText(R.string.sticker_pack_free);
        } else {
            price.setText(context.getString(R.string.sticker_pack_price,
                    context.getResources().getQuantityString(R.plurals.sticker_voice_count,
                            pack.getPrice(), pack.getPrice())));
        }
        price.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        price.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        details.addView(price);

        MaterialButton action = new MaterialButton(context);
        action.setText(pack.isPurchased() ? R.string.sticker_pack_owned
                : pack.isAvailable() ? R.string.sticker_pack_buy : R.string.sticker_pack_unavailable);
        action.setEnabled(!pack.isPurchased() && pack.isAvailable());
        card.addView(action);
        if (!pack.isPurchased() && pack.isAvailable()) {
            action.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.sticker_pack_buy_title)
                    .setMessage(pack.getPrice() <= 0
                            ? context.getString(R.string.sticker_pack_confirm_free, pack.getName())
                            : context.getString(R.string.sticker_pack_confirm_price,
                                    pack.getName(), context.getResources().getQuantityString(
                                            R.plurals.sticker_voice_count,
                                            pack.getPrice(), pack.getPrice())))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.sticker_pack_buy, (d, which) -> {
                        action.setEnabled(false);
                        manager.buyPack(pack.getId(), new StickersManager.PurchaseCallback() {
                            @Override
                            public void onSuccess() {
                                if (!dialog.isShowing()) return;
                                Toast.makeText(context, R.string.sticker_pack_purchase_success,
                                        Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                if (onPurchased != null) onPurchased.run();
                            }

                            @Override
                            public void onError(String error) {
                                action.setEnabled(true);
                                Toast.makeText(context, context.getString(
                                        R.string.sticker_pack_purchase_error, error), Toast.LENGTH_LONG).show();
                            }
                        });
                    }).show());
        }
    }

    private static void addStatus(Context context, LinearLayout parent, int stringId) {
        TextView status = new TextView(context);
        status.setText(stringId);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        parent.addView(status, new LinearLayout.LayoutParams(
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
