package org.nikanikoo.flux.ui.adapters.audio;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.nikanikoo.flux.R;

import java.util.ArrayList;
import java.util.List;

public class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.ViewHolder> {

    public static class GenreItem {
        public final int id;
        public final String name;
        public final int color;
        public final int iconResId;

        public GenreItem(int id, String name, int color, int iconResId) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.iconResId = iconResId;
        }
    }

    private final List<GenreItem> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(GenreItem item);
    }

    public GenreAdapter(OnItemClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
        setupDefaultGenres();
    }

    private void setupDefaultGenres() {
        items.add(new GenreItem(2, "Поп", 0xFFE91E63, R.drawable.ic_favorite));
        items.add(new GenreItem(1, "Рок", 0xFF9C27B0, R.drawable.ic_interests));
        items.add(new GenreItem(3, "Рэп и Хип-хоп", 0xFF2196F3, R.drawable.ic_chat_bubble));
        items.add(new GenreItem(5, "Хаус и Дэнс", 0xFFFF9800, R.drawable.ic_palette));
        items.add(new GenreItem(7, "Метал", 0xFFF44336, R.drawable.ic_warning));
        items.add(new GenreItem(8, "Дабстеп", 0xFF673AB7, R.drawable.ic_colors));
        items.add(new GenreItem(1001, "Джаз и Блюз", 0xFF4CAF50, R.drawable.ic_contrast));
        items.add(new GenreItem(11, "Транс", 0xFF009688, R.drawable.ic_sunny));
        items.add(new GenreItem(12, "Шансон", 0xFF3F51B5, R.drawable.ic_library_music));
        items.add(new GenreItem(16, "Классика", 0xFF795548, R.drawable.ic_policy));
        items.add(new GenreItem(17, "Инди-поп", 0xFF8BC34A, R.drawable.ic_palette));
        items.add(new GenreItem(22, "Диско", 0xFFE040FB, R.drawable.ic_colors));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_genre_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView icon;
        TextView title;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.genre_icon);
            title = itemView.findViewById(R.id.genre_title);
        }

        void bind(GenreItem item) {
            title.setText(item.name);
            card.setCardBackgroundColor(ColorStateList.valueOf(item.color));
            
            if (item.iconResId != 0) {
                icon.setImageResource(item.iconResId);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }

            card.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
