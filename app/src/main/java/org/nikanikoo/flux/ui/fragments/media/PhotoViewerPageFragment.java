package org.nikanikoo.flux.ui.fragments.media;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.github.chrisbanes.photoview.PhotoView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.PhotoViewerActivity;
import org.nikanikoo.flux.utils.Logger;

public class PhotoViewerPageFragment extends Fragment {
    
    private static final String TAG = "PhotoViewerPageFragment";
    private static final String ARG_IMAGE_URL = "image_url";
    
    private String imageUrl;
    private PhotoView photoView;
    
    public static PhotoViewerPageFragment newInstance(String imageUrl) {
        PhotoViewerPageFragment fragment = new PhotoViewerPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IMAGE_URL, imageUrl);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            imageUrl = getArguments().getString(ARG_IMAGE_URL);
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_photo_viewer_page, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            photoView = view.findViewById(R.id.photoView);
            setupPhotoView();
            
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Logger.d(TAG, "Loading image: " + imageUrl);
                loadImage();
            }
        } catch (Exception e) {
            Logger.d(TAG, "Error initializing PhotoView: " + e.getMessage());
            // Fallback - если PhotoView не работает, показываем ошибку
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }
    
    private void setupPhotoView() {
        try {
            // Настраиваем PhotoView для плавной работы
            photoView.setMaximumScale(4.0f);
            photoView.setMediumScale(2.0f);
            photoView.setMinimumScale(1.0f);
            
            // Включаем зум и панорамирование
            photoView.setZoomable(true);
            
            
            // Настраиваем обработчик изменения масштаба для плавности
            photoView.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
                Logger.d(TAG, "Scale changed: " + scaleFactor);
            });
            
            // Настраиваем обработчик матрицы для предотвращения застревания
            photoView.setOnMatrixChangeListener(rect -> {
                // Проверяем, находится ли изображение в исходном масштабе
                boolean isMinScale = photoView.getScale() <= photoView.getMinimumScale() + 0.01f;
                
                if (isMinScale) {
                    // При минимальном масштабе разрешаем родительскому ViewPager обрабатывать жесты
                    photoView.setAllowParentInterceptOnEdge(true);
                    // Разрешаем swipe-to-close
                    if (getActivity() instanceof PhotoViewerActivity) {
                        ((PhotoViewerActivity) getActivity()).setSwipeToCloseEnabled(true);
                    }
                } else {
                    // При увеличенном масштабе PhotoView обрабатывает все жесты
                    photoView.setAllowParentInterceptOnEdge(false);
                    // Запрещаем swipe-to-close чтобы не мешать панорамированию
                    if (getActivity() instanceof PhotoViewerActivity) {
                        ((PhotoViewerActivity) getActivity()).setSwipeToCloseEnabled(false);
                    }
                }
            });
            
            // Изначально разрешаем родительскому ViewPager перехватывать события на краях
            photoView.setAllowParentInterceptOnEdge(true);
            
            // Устанавливаем длительность анимации зума
            photoView.setZoomTransitionDuration(Constants.UI.ANIMATION_DURATION_MEDIUM);
            
        } catch (Exception e) {
            Logger.d(TAG, "Error setting up PhotoView: " + e.getMessage());
            // Fallback - если PhotoView не работает, используем базовые настройки
        }
    }
    
    private void loadImage() {
        Picasso.get()
                .load(imageUrl)
                .fit()
                .centerInside()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(photoView);
    }
}