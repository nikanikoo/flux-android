package org.nikanikoo.flux.ui.adapters;

import android.text.Html;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.Note;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    private final List<Note> notes = new ArrayList<>();
    private OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public void setOnNoteClickListener(OnNoteClickListener listener) {
        this.listener = listener;
    }

    public void setNotes(List<Note> newNotes) {
        notes.clear();
        notes.addAll(newNotes);
        notifyDataSetChanged();
    }

    public void addNotes(List<Note> moreNotes) {
        int startPos = notes.size();
        notes.addAll(moreNotes);
        notifyItemRangeInserted(startPos, moreNotes.size());
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.bind(note, listener);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDate;
        TextView tvText;
        TextView tvCommentsCount;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.note_title);
            tvDate = itemView.findViewById(R.id.note_date);
            tvText = itemView.findViewById(R.id.note_text);
            tvCommentsCount = itemView.findViewById(R.id.note_comments_count);
        }

        public void bind(Note note, OnNoteClickListener listener) {
            tvTitle.setText(note.getTitle());
            
            long timestamp = note.getDate() * 1000L;
            tvDate.setText(DateFormat.format("dd MMM yyyy, HH:mm", new Date(timestamp)));
            
            if (note.getText() != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    tvText.setText(Html.fromHtml(note.getText(), Html.FROM_HTML_MODE_COMPACT).toString().trim());
                } else {
                    tvText.setText(Html.fromHtml(note.getText()).toString().trim());
                }
            } else {
                tvText.setText("");
            }

            tvCommentsCount.setText(String.valueOf(note.getComments()));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNoteClick(note);
                }
            });
        }
    }
}
