package org.nikanikoo.flux.ui.fragments.media;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.PhotosManager;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.Album;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.adapters.photos.AlbumsAdapter;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class PhotosFragment extends BaseFragment implements AlbumsAdapter.OnAlbumClickListener,
        AlbumsAdapter.OnAlbumLongClickListener {

    private static final String TAG = "PhotosFragment";
    private static final int ALBUMS_PER_PAGE = 40;
    private static final int GRID_COLUMNS = 2;

    private RecyclerView recyclerAlbums;
    private AlbumsAdapter albumsAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private GridLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;

    private PhotosManager photosManager;
    private ProfileManager profileManager;
    private final List<Album> albums = new ArrayList<>();
    private int currentUserId = 0;
    private int targetOwnerId = 0;
    private String ownerTitle = null;

    private static final String ARG_OWNER_ID = "owner_id";
    private static final String ARG_OWNER_TITLE = "owner_title";

    public static PhotosFragment newInstance(int ownerId, String ownerTitle) {
        PhotosFragment fragment = new PhotosFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_OWNER_ID, ownerId);
        args.putString(ARG_OWNER_TITLE, ownerTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetOwnerId = getArguments().getInt(ARG_OWNER_ID, 0);
            ownerTitle = getArguments().getString(ARG_OWNER_TITLE, null);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photos, container, false);

        photosManager = PhotosManager.getInstance(requireContext());
        profileManager = ProfileManager.getInstance(requireContext());

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupEndlessScroll();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(() -> loadAlbums(true));

        setupFab(view);

        if (targetOwnerId != 0) {
            currentUserId = targetOwnerId;
            loadAlbums(true);
        } else {
            loadUserProfile();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            if (ownerTitle != null && !ownerTitle.isEmpty()) {
                ((MainActivity) getActivity()).setToolbarTitle(ownerTitle);
            } else {
                ((MainActivity) getActivity()).setToolbarTitle(getString(R.string.nav_photos));
            }
        }
    }

    private void initViews(View view) {
        recyclerAlbums = view.findViewById(R.id.recycler_albums);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    private void setupRecyclerView() {
        layoutManager = new GridLayoutManager(requireContext(), GRID_COLUMNS);
        recyclerAlbums.setLayoutManager(layoutManager);
        albumsAdapter = new AlbumsAdapter(albums, this);
        albumsAdapter.setOnAlbumLongClickListener(this);
        recyclerAlbums.setAdapter(albumsAdapter);
        swipeRefresh.setColorSchemeColors(
                androidx.core.content.ContextCompat.getColor(requireContext(),
                        com.google.android.material.R.color.m3_ref_palette_dynamic_primary40));
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            scrollListener.resetState();
            albums.clear();
            albumsAdapter.notifyDataSetChanged();
            loadAlbums(true);
        });
    }

    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(ALBUMS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                loadAlbums(false);
            }
        };
        recyclerAlbums.addOnScrollListener(scrollListener);
    }

    private void setupFab(View view) {
        FloatingActionButton fab = view.findViewById(R.id.fab_add_album);
        fab.setOnClickListener(v -> showCreateAlbumDialog());
    }

    private void showCreateAlbumDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.album_create_title);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.album_title_hint);
        layout.addView(titleInput);

        EditText descInput = new EditText(requireContext());
        descInput.setHint(R.string.album_description_hint);
        layout.addView(descInput);

        builder.setView(layout);
        builder.setPositiveButton(R.string.album_btn_create, null);
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String desc = descInput.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), R.string.album_fill_title_error, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            createAlbum(title, desc);
        });
    }

    private void showEditAlbumDialog(Album album) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.album_edit_title);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.album_title_hint);
        titleInput.setText(album.getTitle());
        layout.addView(titleInput);

        EditText descInput = new EditText(requireContext());
        descInput.setHint(R.string.album_description_hint);
        descInput.setText(album.getDescription());
        layout.addView(descInput);

        builder.setView(layout);
        builder.setPositiveButton(R.string.album_btn_save, null);
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String desc = descInput.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), R.string.album_fill_title_error, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            editAlbum(album, title, desc);
        });
    }

    private void createAlbum(String title, String description) {
        progressBar.setVisibility(View.VISIBLE);
        int groupId = currentUserId < 0 ? -currentUserId : 0;
        photosManager.createAlbum(title, description, groupId, new PhotosManager.CreateAlbumCallback() {
            @Override
            public void onSuccess(Album album) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.album_created_success, Toast.LENGTH_SHORT).show();
                    loadAlbums(true);
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void editAlbum(Album album, String title, String description) {
        progressBar.setVisibility(View.VISIBLE);
        String descParam = description.isEmpty() ? album.getDescription() : description;
        photosManager.editAlbum(album.getId(), album.getOwnerId(), title, descParam,
                new PhotosManager.EditAlbumCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), R.string.album_updated_success, Toast.LENGTH_SHORT).show();
                            loadAlbums(true);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    @Override
    public void onAlbumLongClick(Album album) {
        String[] options = new String[]{
                getString(R.string.photo_action_edit),
                getString(R.string.photo_action_delete)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(album.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditAlbumDialog(album);
                    } else if (which == 1) {
                        showDeleteAlbumDialog(album);
                    }
                })
                .show();
    }

    private void showDeleteAlbumDialog(Album album) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.album_delete_title)
                .setMessage(getString(R.string.album_delete_confirm, album.getTitle()))
                .setPositiveButton(R.string.remove, (dialog, which) -> deleteAlbum(album))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteAlbum(Album album) {
        progressBar.setVisibility(View.VISIBLE);
        int groupId = album.getOwnerId() < 0 ? -album.getOwnerId() : 0;
        photosManager.deleteAlbum(album.getId(), groupId, new PhotosManager.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.album_deleted_success, Toast.LENGTH_SHORT).show();
                    loadAlbums(true);
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadUserProfile() {
        profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                currentUserId = profile.getId();
                Logger.d(TAG, "User ID: " + currentUserId);
                loadAlbums(true);
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Profile error: " + error);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        swipeRefresh.setRefreshing(false);
                        showErrorAuto(error);
                    });
                }
            }
        });
    }

    private void loadAlbums(boolean isRefresh) {
        if (!paginationHelper.canLoadMore() && !isRefresh) return;
        if (currentUserId == 0) return;

        if (isRefresh) {
            progressBar.setVisibility(View.VISIBLE);
            paginationHelper.reset();
        }

        int offset = isRefresh ? 0 : paginationHelper.getCurrentOffset();

        photosManager.getAlbums(currentUserId, offset, ALBUMS_PER_PAGE,
                new PhotosManager.AlbumsCallback() {
                    @Override
                    public void onSuccess(List<Album> loaded, int totalCount) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            paginationHelper.onDataLoaded(loaded.size());
                            progressBar.setVisibility(View.GONE);
                            swipeRefresh.setRefreshing(false);
                            hideError();

                            if (isRefresh) albums.clear();
                            albums.addAll(loaded);
                            albumsAdapter.notifyDataSetChanged();

                            if (albums.isEmpty()) {
                                showError(R.string.albums_empty_title, R.string.albums_empty_message);
                            }
                            Logger.d(TAG, "Loaded " + loaded.size() + " albums");
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            paginationHelper.stopLoading();
                            progressBar.setVisibility(View.GONE);
                            swipeRefresh.setRefreshing(false);
                            showErrorAuto(error);
                            Logger.e(TAG, "Albums error: " + error);
                        });
                    }
                });
    }

    @Override
    public void onAlbumClick(Album album) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            AlbumPhotosFragment fragment = AlbumPhotosFragment.newInstance(
                    album.getId(),
                    album.getOwnerId(),
                    album.getTitle(),
                    album.canUpload()
            );
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack("album_photos")
                    .commit();
            activity.setToolbarTitle(album.getTitle());
        }
    }
}