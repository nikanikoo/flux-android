package org.nikanikoo.flux.ui.fragments.media;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.PhotosManager;
import org.nikanikoo.flux.data.models.Photo;
import org.nikanikoo.flux.ui.activities.PhotoViewerActivity;
import org.nikanikoo.flux.ui.adapters.photos.AlbumPhotosAdapter;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class AlbumPhotosFragment extends BaseFragment implements AlbumPhotosAdapter.OnPhotoClickListener {

    private static final String TAG = "AlbumPhotosFragment";
    private static final String ARG_ALBUM_ID = "album_id";
    private static final String ARG_OWNER_ID = "owner_id";
    private static final String ARG_ALBUM_TITLE = "album_title";
    private static final int PHOTOS_PER_PAGE = 60;
    private static final int GRID_COLUMNS = 3;

    private int albumId;
    private int ownerId;
    private String albumTitle;

    private RecyclerView recyclerPhotos;
    private AlbumPhotosAdapter photosAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private GridLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;

    private PhotosManager photosManager;
    private final List<Photo> photos = new ArrayList<>();

    public static AlbumPhotosFragment newInstance(int albumId, int ownerId, String albumTitle) {
        AlbumPhotosFragment fragment = new AlbumPhotosFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ALBUM_ID, albumId);
        args.putInt(ARG_OWNER_ID, ownerId);
        args.putString(ARG_ALBUM_TITLE, albumTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            albumId = getArguments().getInt(ARG_ALBUM_ID, 0);
            ownerId = getArguments().getInt(ARG_OWNER_ID, 0);
            albumTitle = getArguments().getString(ARG_ALBUM_TITLE, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_album_photos, container, false);

        photosManager = PhotosManager.getInstance(requireContext());

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupEndlessScroll();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(() -> loadPhotos(true));

        loadPhotos(true);

        return view;
    }

    private void initViews(View view) {
        recyclerPhotos = view.findViewById(R.id.recycler_photos);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    private void setupRecyclerView() {
        layoutManager = new GridLayoutManager(requireContext(), GRID_COLUMNS);
        recyclerPhotos.setLayoutManager(layoutManager);
        photosAdapter = new AlbumPhotosAdapter(photos, this);
        recyclerPhotos.setAdapter(photosAdapter);
        recyclerPhotos.addItemDecoration(new GridSpacingItemDecoration(GRID_COLUMNS, 2, true));
        swipeRefresh.setColorSchemeColors(
                androidx.core.content.ContextCompat.getColor(requireContext(),
                        com.google.android.material.R.color.m3_ref_palette_dynamic_primary40));
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            scrollListener.resetState();
            photos.clear();
            photosAdapter.notifyDataSetChanged();
            loadPhotos(true);
        });
    }

    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(PHOTOS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                loadPhotos(false);
            }
        };
        recyclerPhotos.addOnScrollListener(scrollListener);
    }

    private void loadPhotos(boolean isRefresh) {
        if (!paginationHelper.canLoadMore() && !isRefresh) return;

        if (isRefresh) {
            progressBar.setVisibility(View.VISIBLE);
            paginationHelper.reset();
        }

        int offset = isRefresh ? 0 : paginationHelper.getCurrentOffset();
        String albumIdStr = String.valueOf(albumId);

        photosManager.getPhotos(ownerId, albumIdStr, offset, PHOTOS_PER_PAGE,
                new PhotosManager.PhotosCallback() {
                    @Override
                    public void onSuccess(List<Photo> loaded, int totalCount) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            paginationHelper.onDataLoaded(loaded.size());
                            progressBar.setVisibility(View.GONE);
                            swipeRefresh.setRefreshing(false);
                            hideError();

                            if (isRefresh) photos.clear();
                            photos.addAll(loaded);
                            photosAdapter.notifyDataSetChanged();

                            if (photos.isEmpty()) {
                                if (getErrorViewHandler() != null) {
                                    getErrorViewHandler().setErrorImage(R.drawable.ic_photo);
                                }
                                showError(R.string.photos_empty_title, R.string.photos_empty_message);
                            }
                            Logger.d(TAG, "Loaded " + loaded.size() + " photos");
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            paginationHelper.stopLoading();
                            progressBar.setVisibility(View.GONE);
                            swipeRefresh.setRefreshing(false);
                            if (getErrorViewHandler() != null) {
                                getErrorViewHandler().setErrorImage(R.drawable.veselcraft);
                            }
                            showErrorAuto(error);
                            Logger.e(TAG, "Photos error: " + error);
                        });
                    }
                });
    }

    @Override
    public void onPhotoClick(Photo photo, int position) {
        List<String> urls = new ArrayList<>();
        for (Photo p : photos) {
            urls.add(p.getBestUrl());
        }
        PhotoViewerActivity.start(requireContext(), urls, position, albumTitle);
    }

    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int spacing;
        private final boolean includeEdge;

        GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;
                if (position < spanCount) outRect.top = spacing;
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) outRect.top = spacing;
            }
        }
    }
}
