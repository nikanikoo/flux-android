package org.nikanikoo.flux.ui.fragments.notes;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.NotesManager;
import org.nikanikoo.flux.data.models.Note;
import org.nikanikoo.flux.ui.adapters.NotesAdapter;

import java.util.List;

public class NotesFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fabAddNote;
    private ProgressBar progressBar;
    private View emptyState;

    private NotesAdapter adapter;
    private NotesManager notesManager;

    private int currentOffset = 0;
    private static final int COUNT = 20;
    private boolean isLoading = false;
    private boolean isLastPage = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view_notes);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        fabAddNote = view.findViewById(R.id.fab_add_note);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyState = view.findViewById(R.id.empty_state);

        notesManager = NotesManager.getInstance(requireContext());
        adapter = new NotesAdapter();

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        adapter.setOnNoteClickListener(note -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, NoteViewerFragment.newInstance(note))
                    .addToBackStack("note_viewer")
                    .commit();
        });

        swipeRefreshLayout.setOnRefreshListener(this::refreshNotes);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0) {
                        loadMoreNotes();
                    }
                }
            }
        });

        fabAddNote.setOnClickListener(v -> showAddNoteDialog());

        refreshNotes();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof org.nikanikoo.flux.ui.activities.MainActivity) {
            org.nikanikoo.flux.ui.activities.MainActivity activity = (org.nikanikoo.flux.ui.activities.MainActivity) getActivity();
            activity.setToolbarTitle(getString(R.string.nav_notes));
        }
    }

    private void refreshNotes() {
        currentOffset = 0;
        isLastPage = false;
        loadNotes(true);
    }

    private void loadMoreNotes() {
        loadNotes(false);
    }

    private void loadNotes(boolean isRefresh) {
        if (isLoading) return;
        isLoading = true;

        if (isRefresh) {
            swipeRefreshLayout.setRefreshing(true);
        } else {
            progressBar.setVisibility(View.VISIBLE);
        }

        notesManager.getNotes(0, currentOffset, COUNT, new NotesManager.NotesCallback() {
            @Override
            public void onSuccess(List<Note> notes, int count) {
                if (!isAdded()) return;

                isLoading = false;
                swipeRefreshLayout.setRefreshing(false);
                progressBar.setVisibility(View.GONE);

                if (isRefresh) {
                    adapter.setNotes(notes);
                    if (notes.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                    } else {
                        emptyState.setVisibility(View.GONE);
                    }
                } else {
                    adapter.addNotes(notes);
                }

                currentOffset += notes.size();
                if (notes.size() < COUNT) {
                    isLastPage = true;
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;

                isLoading = false;
                swipeRefreshLayout.setRefreshing(false);
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();

                if (adapter.getItemCount() == 0) {
                    emptyState.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void showAddNoteDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.note_create_title);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.note_title_hint);
        layout.addView(titleInput);

        final EditText textInput = new EditText(requireContext());
        textInput.setHint(R.string.note_text_hint);
        textInput.setMinLines(3);
        layout.addView(textInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.note_btn_create, (dialog, which) -> {
            String title = titleInput.getText().toString().trim();
            String text = textInput.getText().toString().trim();

            if (!title.isEmpty() && !text.isEmpty()) {
                createNote(title, text);
            } else {
                Toast.makeText(requireContext(), R.string.note_fill_fields_error, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void createNote(String title, String text) {
        progressBar.setVisibility(View.VISIBLE);
        notesManager.addNote(title, text, new NotesManager.CreateCallback() {
            @Override
            public void onSuccess(int noteId) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.note_created_success, Toast.LENGTH_SHORT).show();
                refreshNotes();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}