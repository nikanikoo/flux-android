package org.nikanikoo.flux.ui.fragments.notes;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.CommentsManager;
import org.nikanikoo.flux.data.managers.NotesManager;
import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.data.models.Note;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.adapters.comments.CommentsAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NoteViewerFragment extends Fragment implements CommentsAdapter.OnCommentClickListener {

    private static final String ARG_NOTE = "note";
    private Note note;
    
    private TextView tvTitle;
    private TextView tvDate;
    private TextView tvContent;
    private RecyclerView recyclerComments;
    private SwipeRefreshLayout swipeRefresh;
    private EditText editComment;
    private ImageView btnAttachImage;
    private ImageView btnSendComment;
    
    private CommentsAdapter commentsAdapter;
    private List<Comment> commentsList = new ArrayList<>();
    private NotesManager notesManager;

    public static NoteViewerFragment newInstance(Note note) {
        NoteViewerFragment fragment = new NoteViewerFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_NOTE, note);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            note = (Note) getArguments().getSerializable(ARG_NOTE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setToolbarTitle(getString(R.string.note_viewer_title));
        }

        notesManager = NotesManager.getInstance(requireContext());

        tvTitle = view.findViewById(R.id.note_title);
        tvDate = view.findViewById(R.id.note_date);
        tvContent = view.findViewById(R.id.note_content);
        recyclerComments = view.findViewById(R.id.recycler_comments);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_note);
        editComment = view.findViewById(R.id.edit_comment);
        btnAttachImage = view.findViewById(R.id.btn_attach_image);
        btnAttachImage.setVisibility(View.GONE);
        btnSendComment = view.findViewById(R.id.btn_send_comment);
        FloatingActionButton fabEditNote = view.findViewById(R.id.fab_edit_note);

        recyclerComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        commentsAdapter = new CommentsAdapter(requireContext(), commentsList);
        commentsAdapter.setOnCommentClickListener(this);
        recyclerComments.setAdapter(commentsAdapter);

        if (note != null) {
            updateNoteUI();
            loadComments();
        }

        swipeRefresh.setOnRefreshListener(this::loadComments);

        fabEditNote.setOnClickListener(v -> showEditNoteDialog());

        btnSendComment.setOnClickListener(v -> {
            String text = editComment.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), R.string.comments_add_error, Toast.LENGTH_SHORT).show();
                return;
            }
            sendComment(text);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.setToolbarTitle(note.getTitle());
        }
    }

    private void updateNoteUI() {
        tvTitle.setText(note.getTitle());
        long timestamp = note.getDate() * 1000L;
        tvDate.setText(DateFormat.format("dd MMM yyyy, HH:mm", new Date(timestamp)));

        if (note.getText() != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                tvContent.setText(Html.fromHtml(note.getText(), Html.FROM_HTML_MODE_COMPACT));
            } else {
                tvContent.setText(Html.fromHtml(note.getText()));
            }
        }
    }

    private void loadComments() {
        swipeRefresh.setRefreshing(true);
        notesManager.getComments(note.getOwnerId(), note.getId(), 0, 100, new CommentsManager.CommentsCallback() {
            @Override
            public void onSuccess(List<Comment> comments) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
                commentsList.clear();
                commentsList.addAll(comments);
                commentsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendComment(String text) {
        btnSendComment.setEnabled(false);
        notesManager.createComment(note.getOwnerId(), note.getId(), text, null, new CommentsManager.CreateCommentCallback() {
            @Override
            public void onSuccess(Comment comment) {
                if (!isAdded()) return;
                btnSendComment.setEnabled(true);
                editComment.setText("");
                loadComments();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                btnSendComment.setEnabled(true);
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditNoteDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.note_edit_title);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.note_title_hint);
        titleInput.setText(note.getTitle());
        layout.addView(titleInput);

        final EditText textInput = new EditText(requireContext());
        textInput.setHint(R.string.note_text_hint);
        textInput.setText(note.getText());
        textInput.setMinLines(3);
        layout.addView(textInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            String title = titleInput.getText().toString().trim();
            String text = textInput.getText().toString().trim();

            if (!title.isEmpty() && !text.isEmpty()) {
                notesManager.editNote(note.getId(), title, text, new NotesManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        note.setTitle(title);
                        note.setText(text);
                        updateNoteUI();
                        Toast.makeText(requireContext(), R.string.note_updated_success, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(requireContext(), R.string.note_fill_fields_error, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        MenuItem deleteItem = menu.add(Menu.NONE, 1001, Menu.NONE, R.string.delete);
        deleteItem.setIcon(R.drawable.ic_delete);
        deleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1001) {
            showDeleteConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteConfirmationDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(R.string.note_delete_confirm)
                .setPositiveButton(R.string.yes, (dialog, which) -> deleteNote())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void deleteNote() {
        notesManager.deleteNote(note.getId(), new NotesManager.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.note_delete_success, Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAuthorClick(int authorId, String authorName, boolean isGroup) {
        if (getActivity() != null) {
            if (isGroup) {
                int groupId = authorId < 0 ? -authorId : authorId;
                org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment groupProfileFragment =
                    org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment.newInstance(groupId, authorName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, groupProfileFragment)
                        .addToBackStack("group_" + groupId)
                        .commit();
            } else {
                org.nikanikoo.flux.ui.fragments.profile.ProfileFragment profileFragment =
                    org.nikanikoo.flux.ui.fragments.profile.ProfileFragment.newInstanceWithId(authorId, authorName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, profileFragment)
                        .addToBackStack("profile_" + authorId)
                        .commit();
            }
        }
    }

    @Override
    public void onLikeClick(Comment comment) {
        final boolean originalLikedState = comment.isLiked();
        final int originalLikeCount = comment.getLikesCount();
        
        final boolean newLikedState = !originalLikedState;
        final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;
        
        comment.setLiked(newLikedState);
        comment.setLikesCount(newLikeCount);
        
        int index = commentsList.indexOf(comment);
        if (index >= 0) {
            commentsAdapter.notifyItemChanged(index, "LIKE_UPDATE");
        }
        
        CommentsManager.getInstance(requireContext()).toggleCommentLikeWithOriginalState(comment, note.getOwnerId(), note.getId(), 
                originalLikedState, new CommentsManager.LikeCommentCallback() {
            @Override
            public void onSuccess(int newLikesCount, boolean isLiked) {
                if (!isAdded()) return;
                comment.setLikesCount(newLikesCount);
                comment.setLiked(isLiked);
                int successIndex = commentsList.indexOf(comment);
                if (successIndex >= 0) {
                    commentsAdapter.notifyItemChanged(successIndex, "LIKE_UPDATE");
                }
            }
            
            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                comment.setLiked(originalLikedState);
                comment.setLikesCount(originalLikeCount);
                int errorIndex = commentsList.indexOf(comment);
                if (errorIndex >= 0) {
                    commentsAdapter.notifyItemChanged(errorIndex, "LIKE_UPDATE");
                }
                Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onReplyClick(Comment comment) {
        String replyText = "[id" + comment.getFromId() + "|" + comment.getAuthorName() + "] ";
        editComment.setText(replyText);
        editComment.setSelection(replyText.length());
        editComment.requestFocus();
    }

    @Override
    public void onImageClick(String imageUrl) {
    }
}
