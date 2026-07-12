package org.nikanikoo.flux.ui.adapters.posts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;

import java.util.List;

public class PostImagesCollage extends FrameLayout {
    
    private LinearLayout collage1Image;
    private LinearLayout collage2Images;
    private LinearLayout collage3Images;
    private LinearLayout collage4Images;
    
    private ImageView image1Of1;
    private ImageView image1Of2, image2Of2;
    private ImageView image1Of3, image2Of3, image3Of3;
    private ImageView image1Of4, image2Of4, image3Of4, image4Of4;
    
    private View moreImagesOverlay;
    private TextView moreImagesCount;
    
    private OnImageClickListener imageClickListener;
    
    public interface OnImageClickListener {
        void onImageClick(int position, List<String> imageUrls);
    }
    
    public PostImagesCollage(Context context) {
        super(context);
        init();
    }
    
    public PostImagesCollage(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public PostImagesCollage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.post_images_collage, this, true);
        
        collage1Image = findViewById(R.id.collage_1_image);
        collage2Images = findViewById(R.id.collage_2_images);
        collage3Images = findViewById(R.id.collage_3_images);
        collage4Images = findViewById(R.id.collage_4_images);
        
        image1Of1 = findViewById(R.id.image_1_of_1);
        image1Of2 = findViewById(R.id.image_1_of_2);
        image2Of2 = findViewById(R.id.image_2_of_2);
        
        image1Of3 = findViewById(R.id.image_1_of_3);
        image2Of3 = findViewById(R.id.image_2_of_3);
        image3Of3 = findViewById(R.id.image_3_of_3);
        
        image1Of4 = findViewById(R.id.image_1_of_4);
        image2Of4 = findViewById(R.id.image_2_of_4);
        image3Of4 = findViewById(R.id.image_3_of_4);
        image4Of4 = findViewById(R.id.image_4_of_4);
        
        moreImagesOverlay = findViewById(R.id.more_images_overlay);
        moreImagesCount = findViewById(R.id.more_images_count);
    }
    
    public void setOnImageClickListener(OnImageClickListener listener) {
        this.imageClickListener = listener;
    }
    
    public void setImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        
        setVisibility(VISIBLE);
        hideAllCollages();
        
        int imageCount = imageUrls.size();
        
        if (imageCount == 1) {
            setup1Image(imageUrls);
        } else if (imageCount == 2) {
            setup2Images(imageUrls);
        } else if (imageCount == 3) {
            setup3Images(imageUrls);
        } else if (imageCount >= 4) {
            setup4Images(imageUrls);
        }
    }
    
    private void hideAllCollages() {
        if (collage1Image != null) collage1Image.setVisibility(GONE);
        collage2Images.setVisibility(GONE);
        collage3Images.setVisibility(GONE);
        collage4Images.setVisibility(GONE);
        moreImagesOverlay.setVisibility(GONE);
        moreImagesCount.setVisibility(GONE);
    }
    
    private void setup1Image(List<String> imageUrls) {
        if (collage1Image != null) {
            collage1Image.setVisibility(VISIBLE);
            loadImageIntoView(image1Of1, imageUrls.get(0));
            setupImageClickListener(image1Of1, 0, imageUrls);
        }
    }

    private void setup2Images(List<String> imageUrls) {
        collage2Images.setVisibility(VISIBLE);
        
        loadImageIntoView(image1Of2, imageUrls.get(0));
        loadImageIntoView(image2Of2, imageUrls.get(1));
        
        setupImageClickListener(image1Of2, 0, imageUrls);
        setupImageClickListener(image2Of2, 1, imageUrls);
    }
    
    private void setup3Images(List<String> imageUrls) {
        collage3Images.setVisibility(VISIBLE);
        
        loadImageIntoView(image1Of3, imageUrls.get(0));
        loadImageIntoView(image2Of3, imageUrls.get(1));
        loadImageIntoView(image3Of3, imageUrls.get(2));
        
        setupImageClickListener(image1Of3, 0, imageUrls);
        setupImageClickListener(image2Of3, 1, imageUrls);
        setupImageClickListener(image3Of3, 2, imageUrls);
    }
    
    private void setup4Images(List<String> imageUrls) {
        collage4Images.setVisibility(VISIBLE);
        
        loadImageIntoView(image1Of4, imageUrls.get(0));
        loadImageIntoView(image2Of4, imageUrls.get(1));
        loadImageIntoView(image3Of4, imageUrls.get(2));
        loadImageIntoView(image4Of4, imageUrls.get(3));
        
        setupImageClickListener(image1Of4, 0, imageUrls);
        setupImageClickListener(image2Of4, 1, imageUrls);
        setupImageClickListener(image3Of4, 2, imageUrls);
        setupImageClickListener(image4Of4, 3, imageUrls);
        
        // Показываем overlay с количеством дополнительных изображений, если их больше 4
        if (imageUrls.size() > 4) {
            int moreCount = imageUrls.size() - 4;
            moreImagesOverlay.setVisibility(VISIBLE);
            moreImagesCount.setVisibility(VISIBLE);
            moreImagesCount.setText("+" + moreCount);
            
            // Клик на последнее изображение с overlay должен открывать галерею
            setupImageClickListener(image4Of4, 3, imageUrls);
        }
    }
    
    private void loadImageIntoView(ImageView imageView, String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            com.squareup.picasso.RequestCreator requestCreator = Picasso.get()
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .fit();
            
            if (imageView == image1Of1) {
                requestCreator.centerInside();
            } else {
                requestCreator.centerCrop();
            }
            
            requestCreator.into(imageView);
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }
    
    private void setupImageClickListener(ImageView imageView, int position, List<String> imageUrls) {
        imageView.setOnClickListener(v -> {
            if (imageClickListener != null) {
                imageClickListener.onImageClick(position, imageUrls);
            }
        });
    }
}