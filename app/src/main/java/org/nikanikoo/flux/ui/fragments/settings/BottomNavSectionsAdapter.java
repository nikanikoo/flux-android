package org.nikanikoo.flux.ui.fragments.settings;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.fragments.menu.MenuDashboardFragment;

import java.util.Collections;
import java.util.List;

public class BottomNavSectionsAdapter extends RecyclerView.Adapter<BottomNavSectionsAdapter.ViewHolder> {

    public static class SectionItem {
        public final String tag;
        public final int nameResId;
        public final int iconResId;
        public boolean isSelected;
        public final boolean isFixed;

        public SectionItem(String tag, int nameResId, int iconResId, boolean isSelected) {
            this(tag, nameResId, iconResId, isSelected, false);
        }

        public SectionItem(String tag, int nameResId, int iconResId, boolean isSelected, boolean isFixed) {
            this.tag = tag;
            this.nameResId = nameResId;
            this.iconResId = iconResId;
            this.isSelected = isSelected;
            this.isFixed = isFixed;
        }
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private final List<SectionItem> items;
    private final int maxSelectableItems;
    private final OnStartDragListener dragListener;
    private final OnSelectionChangedListener selectionListener;

    public BottomNavSectionsAdapter(List<SectionItem> items,
                                    OnStartDragListener dragListener,
                                    OnSelectionChangedListener selectionListener) {
        this(items, MenuDashboardFragment.MAX_BOTTOM_NAV_ITEMS, dragListener, selectionListener);
    }

    public BottomNavSectionsAdapter(List<SectionItem> items,
                                    int maxSelectableItems,
                                    OnStartDragListener dragListener,
                                    OnSelectionChangedListener selectionListener) {
        this.items = items;
        this.maxSelectableItems = maxSelectableItems;
        this.dragListener = dragListener;
        this.selectionListener = selectionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nav_section_manage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SectionItem item = items.get(position);
        holder.titleView.setText(item.nameResId);
        holder.iconView.setImageResource(item.iconResId);

        holder.switchView.setOnCheckedChangeListener(null);

        if (item.isFixed) {
            holder.switchView.setChecked(true);
            holder.switchView.setEnabled(false);
            holder.itemView.setOnClickListener(null);
        } else {
            holder.switchView.setEnabled(true);
            holder.switchView.setChecked(item.isSelected);

            holder.switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (getSelectedCount() >= maxSelectableItems) {
                        holder.switchView.setChecked(false);
                        Toast.makeText(holder.itemView.getContext(),
                                R.string.navigation_max_items, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.isSelected = true;
                } else {
                    item.isSelected = false;
                }

                if (selectionListener != null) {
                    selectionListener.onSelectionChanged(getSelectedCount());
                }
            });

            holder.itemView.setOnClickListener(v -> {
                holder.switchView.toggle();
            });
        }

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragListener != null) {
                dragListener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(items, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public int getSelectedCount() {
        int count = 0;
        for (SectionItem item : items) {
            if (item.isSelected) count++;
        }
        return count;
    }

    public String getSelectedTagsString() {
        StringBuilder sb = new StringBuilder();
        for (SectionItem item : items) {
            if (item.isSelected) {
                if (sb.length() > 0) sb.append(",");
                sb.append(item.tag);
            }
        }
        return sb.toString();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView dragHandle;
        final ImageView iconView;
        final TextView titleView;
        final SwitchMaterial switchView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            iconView = itemView.findViewById(R.id.section_icon);
            titleView = itemView.findViewById(R.id.section_title);
            switchView = itemView.findViewById(R.id.section_switch);
        }
    }
}
