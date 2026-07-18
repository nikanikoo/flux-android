package org.nikanikoo.flux.ui.fragments.messages;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.adapters.messages.AttachmentPhotoAdapter;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttachmentBottomSheet extends BottomSheetDialogFragment {

    public interface OnAttachmentSelectedListener {
        void onPhotoSelected(Uri photoUri);
        void onCameraPhotoTaken(Uri photoUri);
    }

    private OnAttachmentSelectedListener listener;

    private Uri cameraOutputUri;

    private ActivityResultLauncher<String>  permissionLauncher;
    private ActivityResultLauncher<Uri>     cameraLauncher;

    public static AttachmentBottomSheet newInstance() {
        return new AttachmentBottomSheet();
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheet_Attachment;
    }

    public void setOnAttachmentSelectedListener(OnAttachmentSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraOutputUri != null) {
                        if (listener != null) {
                            listener.onCameraPhotoTaken(cameraOutputUri);
                        }
                        dismiss();
                    } else {
                        deleteCameraOutputUri();
                    }
                });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        loadPhotos();
                    } else {
                        Toast.makeText(requireContext(),
                                R.string.attachment_permission_denied,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_attachment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View sectionPhotos = view.findViewById(R.id.section_photos);
        if (sectionPhotos != null) {
            sectionPhotos.setOnClickListener(v -> expandToFullScreen());
        }

        RecyclerView grid = view.findViewById(R.id.photos_grid);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        grid.setLayoutManager(layoutManager);

        AttachmentPhotoAdapter adapter = new AttachmentPhotoAdapter(
                requireContext(), new ArrayList<>());

        adapter.setOnItemClickListener(new AttachmentPhotoAdapter.OnItemClickListener() {
            @Override
            public void onCameraClick() {
                openCamera();
            }

            @Override
            public void onPhotoClick(Uri photoUri) {
                if (listener != null) listener.onPhotoSelected(photoUri);
                dismiss();
            }
        });
        grid.setAdapter(adapter);

        grid.addItemDecoration(new SquareCellDecoration(2));
        checkPermissionAndLoad(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        android.view.View bottomSheet = requireDialog()
                .findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;

        BottomSheetBehavior<android.view.View> behavior =
                BottomSheetBehavior.from(bottomSheet);

        DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
        int peekHeight = (int) (dm.heightPixels * 0.60f);
        behavior.setPeekHeight(peekHeight, true);

        behavior.setFitToContents(false);
        behavior.setHalfExpandedRatio(0.60f);
        behavior.setSkipCollapsed(false);

        behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);

        bottomSheet.getLayoutParams().height =
                android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        bottomSheet.requestLayout();
    }

    private void expandToFullScreen() {
        android.view.View bottomSheet = requireDialog()
                .findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            BottomSheetBehavior.from(bottomSheet)
                    .setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void openCamera() {
        try {
            cameraOutputUri = createCameraOutputUri();
            if (cameraOutputUri != null) {
                cameraLauncher.launch(cameraOutputUri);
            } else {
                Toast.makeText(requireContext(),
                        "Не удалось подготовить файл для камеры",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(),
                    "Ошибка при открытии камеры",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createCameraOutputUri() throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "FLUX_" + timestamp + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES);
            return requireContext().getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } else {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            File storageDir = requireContext().getExternalCacheDir();
            File imageFile = File.createTempFile(
                    "FLUX_" + timestamp + "_", ".jpg", storageDir);
            return FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    imageFile);
        }
    }

    private void deleteCameraOutputUri() {
        if (cameraOutputUri == null) return;
        try {
            requireContext().getContentResolver().delete(
                    cameraOutputUri, null, null);
        } catch (Exception ignored) {}
        cameraOutputUri = null;
    }

    private void checkPermissionAndLoad(AttachmentPhotoAdapter adapter) {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED) {
            loadPhotosInto(adapter);
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void loadPhotos() {
        if (getView() == null) return;
        RecyclerView grid = getView().findViewById(R.id.photos_grid);
        if (grid == null || !(grid.getAdapter() instanceof AttachmentPhotoAdapter)) return;
        loadPhotosInto((AttachmentPhotoAdapter) grid.getAdapter());
    }

    private void loadPhotosInto(AttachmentPhotoAdapter adapter) {
        new Thread(() -> {
            List<Uri> uris = queryDevicePhotos();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.getPhotoUris().clear();
                    adapter.getPhotoUris().addAll(uris);
                    adapter.notifyDataSetChanged();
                });
            }
        }).start();
    }

    private List<Uri> queryDevicePhotos() {
        List<Uri> result = new ArrayList<>();

        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder    = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        try (Cursor cursor = requireContext().getContentResolver()
                .query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int count = 0;
                while (cursor.moveToNext() && count < 60) {
                    long id = cursor.getLong(idCol);
                    result.add(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                    count++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static class SquareCellDecoration extends RecyclerView.ItemDecoration {
        private static final int SPAN = 3;
        private final int gap;

        SquareCellDecoration(int gapPx) {
            this.gap = gapPx;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            outRect.set(gap, gap, gap, gap);
        }

        @Override
        public void onDraw(@NonNull android.graphics.Canvas c,
                           @NonNull RecyclerView parent,
                           @NonNull RecyclerView.State state) {
            int totalWidth = parent.getWidth()
                    - parent.getPaddingStart()
                    - parent.getPaddingEnd();
            int cellSize = (totalWidth - gap * 2 * SPAN) / SPAN;

            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp.height != cellSize) {
                    lp.height = cellSize;
                    child.setLayoutParams(lp);
                }
            }
        }
    }
}
