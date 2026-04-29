package org.nikanikoo.flux.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.nikanikoo.flux.R;
import java.util.List;

public class InstancesAdapter extends RecyclerView.Adapter<InstancesAdapter.ViewHolder> {

    public static class InstanceItem {
        public final String displayName;
        public final String url;
        public final String ping;
        public boolean selected;

        public InstanceItem(String displayName, String url, String ping, boolean selected) {
            this.displayName = displayName;
            this.url = url;
            this.ping = ping;
            this.selected = selected;
        }
    }

    public interface OnInstanceClickListener {
        void onInstanceClick(int position);
    }

    private final List<InstanceItem> items;
    private final OnInstanceClickListener listener;
    private int selectedPosition = -1;

    public InstancesAdapter(List<InstanceItem> items, OnInstanceClickListener listener) {
        this.items = items;
        this.listener = listener;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).selected) {
                selectedPosition = i;
                break;
            }
        }
    }

    public void setSelectedPosition(int position) {
        int oldSelected = selectedPosition;
        selectedPosition = position;
        if (oldSelected >= 0 && oldSelected < items.size()) {
            items.get(oldSelected).selected = false;
            notifyItemChanged(oldSelected);
        }
        if (position >= 0 && position < items.size()) {
            items.get(position).selected = true;
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_instance_link, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InstanceItem item = items.get(position);
        holder.name.setText(item.displayName);
        holder.url.setText(item.url);
        holder.ping.setText(item.ping);
        holder.itemView.setSelected(item.selected);

        int pingDrawable = getPingDrawable(item.ping);
        holder.pingIndicator.setBackgroundResource(pingDrawable);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onInstanceClick(position);
            }
        });
    }
    
    private int getPingDrawable(String pingText) {
        if (pingText == null || pingText.isEmpty()) {
            return R.drawable.shape_circle_red;
        }
        try {
            String numeric = pingText.replaceAll("[^0-9]", "");
            if (numeric.isEmpty()) {
                return R.drawable.shape_circle_red;
            }
            int pingMs = Integer.parseInt(numeric);
            if (pingMs <= 100) {
                return R.drawable.shape_circle_online;
            } else if (pingMs <= 300) {
                return R.drawable.shape_circle_yellow;
            } else {
                return R.drawable.shape_circle_red;
            }
        } catch (NumberFormatException e) {
            return R.drawable.shape_circle_red;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView url;
        TextView ping;
        View pingIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.link_title);
            url = itemView.findViewById(R.id.link_url);
            ping = itemView.findViewById(R.id.instance_ping);
            pingIndicator = itemView.findViewById(R.id.ping_indicator);
        }
    }
}