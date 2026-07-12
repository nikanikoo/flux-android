package org.nikanikoo.flux.ui.adapters.posts;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.ImageSpan;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.PhotoViewerActivity;
import org.nikanikoo.flux.ui.views.AudioAttachmentView;
import org.nikanikoo.flux.utils.ImageUtils;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.SafeLinkMovementMethod;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "PostAdapter";
    private static final int VIEW_TYPE_POST = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    private final List<Post> posts = Collections.synchronizedList(new ArrayList<>());
    private final Object postsLock = new Object();
    private Context context;
    private OnPostClickListener clickListener;
    private volatile boolean isLoading = false;
    private boolean isProfileWall = false;

    public void setProfileWall(boolean isProfileWall) {
        this.isProfileWall = isProfileWall;
    }
    
    // Кеш для предотвращения дублирования постов (thread-safe)
    private final Set<String> postIds = Collections.synchronizedSet(new HashSet<>());

    public interface OnPostClickListener {
        void onAuthorClick(int authorId, String authorName, boolean isGroup);
        void onLikeClick(Post post);
        void onCommentClick(Post post);
        void onShareClick(Post post);
        void onPostLongClick(Post post, View view);
    }

    public PostAdapter(Context context, List<Post> initialPosts) {
        this.context = context;
        if (initialPosts != null) {
            this.posts.addAll(initialPosts);
            updatePostIds();
        }
    }

    /**
     * Обновление кеша ID постов для быстрой проверки дубликатов
     */
    private void updatePostIds() {
        postIds.clear();
        synchronized (postsLock) {
            for (Post post : posts) {
                if (post != null) {
                    String postKey = generatePostKey(post);
                    if (postKey != null) {
                        postIds.add(postKey);
                    }
                }
            }
        }
        Logger.d(TAG, "Updated post IDs cache with " + postIds.size() + " entries");
    }

    /**
     * Генерация уникального ключа для поста
     */
    private String generatePostKey(Post post) {
        if (post == null) return null;
        
        if (ValidationUtils.isValidPostOwnerId(post.getPostId()) && ValidationUtils.isValidPostOwnerId(post.getOwnerId())) {
            return post.getPostId() + "_" + post.getOwnerId();
        } else if (post.getAuthorName() != null && post.getContent() != null && post.getTimestamp() != null) {
            return post.getAuthorName() + "_" + post.getContent().hashCode() + "_" + post.getTimestamp();
        }
        
        return null;
    }

    public void setOnPostClickListener(OnPostClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_loading, parent, false);
            return new LoadingViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
            return new PostViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder) {
            bindPostViewHolder((PostViewHolder) holder, position);
        }
        // LoadingViewHolder не требует привязки данных
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof PostViewHolder) {
            PostViewHolder postHolder = (PostViewHolder) holder;
            Post post;
            synchronized (postsLock) {
                post = posts.get(position);
            }
            if (post != null) {
                boolean likeUpdated = false;
                for (Object payload : payloads) {
                    if ("LIKE_UPDATE".equals(payload)) {
                        likeUpdated = true;
                        break;
                    }
                }
                if (likeUpdated) {
                    updateLikeState(postHolder, post);
                    return;
                }
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindPostViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post;
        synchronized (postsLock) {
            post = posts.get(position);
        }
        
        // Валидация данных поста
        if (post == null) {
            Logger.w(TAG, "Post at position " + position + " is null");
            return;
        }

        if (holder.nsfwSpoiler != null && holder.postBodyContainer != null) {
            if (post.isExplicit() && !post.isNsfwRevealed()) {
                holder.nsfwSpoiler.setVisibility(View.VISIBLE);
                holder.nsfwSpoiler.setAlpha(1f);
                holder.postBodyContainer.setVisibility(View.INVISIBLE);
                
                holder.nsfwSpoiler.setOnClickListener(v -> {
                    v.getRootView().clearFocus();
                    
                    post.setNsfwRevealed(true);
                    
                    holder.postBodyContainer.setVisibility(View.VISIBLE);
                    holder.nsfwSpoiler.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            holder.nsfwSpoiler.setVisibility(View.GONE);
                            holder.nsfwSpoiler.setAlpha(1f);
                        })
                        .start();
                });
            } else {
                holder.nsfwSpoiler.setVisibility(View.GONE);
                holder.nsfwSpoiler.setOnClickListener(null);
                holder.postBodyContainer.setVisibility(View.VISIBLE);
                holder.postBodyContainer.setAlpha(1f);
            }
        }
        
        // Безопасная установка текстовых данных
        boolean isWallPostOnOtherWall = !isProfileWall && post.getOwnerId() != 0 && post.getAuthorId() != 0 && 
                !(Math.abs(post.getOwnerId()) == Math.abs(post.getAuthorId()) && post.isGroup() == post.isOwnerGroup());
        
        if (isWallPostOnOtherWall) {
            holder.authorName.setTypeface(null, Typeface.NORMAL);
            holder.authorName.setMaxLines(2);
            holder.authorName.setMovementMethod(SafeLinkMovementMethod.getInstance());
            if (holder.authorVerified != null) {
                holder.authorVerified.setVisibility(View.GONE);
            }
            
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            
            // 1. Author Clickable Span
            android.text.style.ClickableSpan authorClickSpan = new android.text.style.ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (clickListener != null && post.getAuthorId() != 0) {
                        clickListener.onAuthorClick(post.getAuthorId(), post.getAuthorName(), post.isGroup());
                    }
                }

                @Override
                public void updateDrawState(@NonNull android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                    ds.setColor(holder.authorName.getCurrentTextColor());
                    ds.setTypeface(Typeface.create(ds.getTypeface(), Typeface.BOLD));
                }
            };
            
            String authorName = ValidationUtils.sanitizeUserInput(post.getAuthorName());
            int authorStart = ssb.length();
            ssb.append(authorName);
            int authorEnd = ssb.length();
            ssb.setSpan(authorClickSpan, authorStart, authorEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            if (post.isAuthorVerified()) {
                ssb.append(" ");
                int badgeStart = ssb.length();
                ssb.append("￼");
                int badgeEnd = ssb.length();
                
                Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_verified);
                if (drawable != null) {
                    TypedValue typedValue = new TypedValue();
                    context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
                    int color = typedValue.data;
                    DrawableCompat.setTint(drawable, color);
                    
                    drawable.setBounds(0, 0, (int) (14 * context.getResources().getDisplayMetrics().density), (int) (14 * context.getResources().getDisplayMetrics().density));
                    ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
                    ssb.setSpan(imageSpan, badgeStart, badgeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            
            ssb.append(" ");
            String verb = post.getAuthorSex() == 1 ? "написала на стене" : "написал на стене";
            ssb.append(verb);
            ssb.append(" ");
            
            android.text.style.ClickableSpan ownerClickSpan = new android.text.style.ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (clickListener != null && post.getOwnerId() != 0) {
                        int ownerAbsId = Math.abs(post.getOwnerId());
                        clickListener.onAuthorClick(ownerAbsId, post.getOwnerName(), post.isOwnerGroup());
                    }
                }

                @Override
                public void updateDrawState(@NonNull android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                    ds.setColor(holder.authorName.getCurrentTextColor());
                    ds.setTypeface(Typeface.create(ds.getTypeface(), Typeface.BOLD));
                }
            };
            
            String ownerName = ValidationUtils.sanitizeUserInput(post.getOwnerName() != null ? post.getOwnerName() : "User");
            int ownerStart = ssb.length();
            ssb.append(ownerName);
            int ownerEnd = ssb.length();
            ssb.setSpan(ownerClickSpan, ownerStart, ownerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            holder.authorName.setText(ssb);
        } else {
            holder.authorName.setTypeface(null, Typeface.BOLD);
            holder.authorName.setMaxLines(1);
            holder.authorName.setMovementMethod(null);
            holder.authorName.setText(ValidationUtils.sanitizeUserInput(post.getAuthorName()));
            if (holder.authorVerified != null) {
                holder.authorVerified.setVisibility(post.isAuthorVerified() ? View.VISIBLE : View.GONE);
            }
        }
        
        String timestampText = post.getTimestamp();
        if (post.isPinned()) {
            timestampText += ", " + context.getString(R.string.pinned);
        }
        holder.timestamp.setText(timestampText);
        setDeviceIcon(holder.deviceIcon, post.getPlatform());
        holder.likeCount.setText(String.valueOf(Math.max(0, post.getLikeCount())));
        holder.commentCount.setText(String.valueOf(Math.max(0, post.getCommentCount())));
        
        // Оптимизированная загрузка аватара
        if (ImageUtils.isValidImageUrl(post.getAuthorAvatarUrl())) {
            ImageUtils.loadAvatar(post.getAuthorAvatarUrl(), holder.avatar);
        } else {
            holder.avatar.setImageResource(R.drawable.camera_200);
        }
        
        // Обработка репостов
        if (post.isRepost() && post.getOriginalPost() != null) {
            handleRepost(holder, post);
        } else {
            handleRegularPost(holder, post);
        }
        
        // Отображение неподдерживаемых элементов
        handleUnsupportedElements(holder, post);

        handleCopyright(holder.copyrightContainer, holder.copyrightLink, post);
        
        // Обновление состояния лайка
        updateLikeState(holder, post);
        
        // Установка обработчиков кликов
        setupClickListeners(holder, post);
    }
    
    /**
     * Обработка репоста
     */
    private void handleRepost(@NonNull PostViewHolder holder, Post post) {
        Post originalPost = post.getOriginalPost();
        
        // Показываем текст репоста (если есть)
        String repostText = post.getRepostText();
        if (ValidationUtils.isValidPostText(repostText)) {
            String sanitizedText = ValidationUtils.SanitizeText(repostText);
            holder.content.setText(sanitizedText);
            Linkify.addLinks(holder.content, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            holder.content.setMovementMethod(SafeLinkMovementMethod.getInstance());
            holder.content.setVisibility(View.VISIBLE);
        } else {
            holder.content.setVisibility(View.GONE);
        }
        
        // Скрываем изображения основного поста для репостов
        holder.imagesCollage.setVisibility(View.GONE);
        AudioAttachmentView.clearAudioAttachments(holder.audioContainer);
        clearVideoAttachments(holder.videoContainer);
        org.nikanikoo.flux.ui.views.PollAttachmentView.clearPollAttachments(holder.pollContainer);
        
        // Показываем контейнер оригинального поста
        holder.originalPostContainer.setVisibility(View.VISIBLE);
        
        if (holder.originalPostNsfwSpoiler != null && holder.originalPostPostBodyContainer != null) {
            if (originalPost.isExplicit() && !originalPost.isNsfwRevealed()) {
                holder.originalPostNsfwSpoiler.setVisibility(View.VISIBLE);
                holder.originalPostNsfwSpoiler.setAlpha(1f);
                holder.originalPostPostBodyContainer.setVisibility(View.INVISIBLE);
                
                holder.originalPostNsfwSpoiler.setOnClickListener(v -> {
                    v.getRootView().clearFocus();
                    
                    originalPost.setNsfwRevealed(true);
                    
                    holder.originalPostPostBodyContainer.setVisibility(View.VISIBLE);
                    holder.originalPostNsfwSpoiler.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            holder.originalPostNsfwSpoiler.setVisibility(View.GONE);
                            holder.originalPostNsfwSpoiler.setAlpha(1f);
                        })
                        .start();
                });
            } else {
                holder.originalPostNsfwSpoiler.setVisibility(View.GONE);
                holder.originalPostNsfwSpoiler.setOnClickListener(null);
                holder.originalPostPostBodyContainer.setVisibility(View.VISIBLE);
                holder.originalPostPostBodyContainer.setAlpha(1f);
            }
        }
        
        // Заполняем данные оригинального поста
        holder.originalPostAuthorName.setText(ValidationUtils.sanitizeUserInput(originalPost.getAuthorName()));
        holder.originalPostTimestamp.setText(originalPost.getTimestamp());
        setDeviceIcon(holder.originalPostDeviceIcon, originalPost.getPlatform());

        String originalContent = originalPost.getContent();
        if (ValidationUtils.isValidPostText(originalContent)) {
            String sanitizedText = ValidationUtils.SanitizeText(originalContent);
            holder.originalPostContent.setText(sanitizedText);
            Linkify.addLinks(holder.originalPostContent, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            holder.originalPostContent.setMovementMethod(SafeLinkMovementMethod.getInstance());
            holder.originalPostContent.setVisibility(View.VISIBLE);
        } else {
            holder.originalPostContent.setVisibility(View.GONE);
        }
        
        // Отображение галочки верификации автора оригинального поста
        if (holder.originalPostAuthorVerified != null) {
            holder.originalPostAuthorVerified.setVisibility(originalPost.isAuthorVerified() ? View.VISIBLE : View.GONE);
        }

        // Загружаем аватар автора оригинального поста
        if (ImageUtils.isValidImageUrl(originalPost.getAuthorAvatarUrl())) {
            ImageUtils.loadAvatar(originalPost.getAuthorAvatarUrl(), holder.originalPostAvatar);
        } else {
            holder.originalPostAvatar.setImageResource(R.drawable.camera_200);
        }
        
        // Устанавливаем клик на аватар и имя автора оригинального поста
        View.OnClickListener originalAuthorClickListener = v -> {
            if (clickListener != null && originalPost.getAuthorId() != 0) {
                clickListener.onAuthorClick(originalPost.getAuthorId(), originalPost.getAuthorName(), originalPost.isGroup());
            }
        };
        holder.originalPostAvatar.setOnClickListener(originalAuthorClickListener);
        holder.originalPostAuthorName.setOnClickListener(originalAuthorClickListener);
        
        // Обрабатываем изображения оригинального поста
        handleOriginalPostImages(holder, originalPost);
        
        // Обрабатываем аудио оригинального поста
        handleOriginalPostAudio(holder, originalPost);
        
        // Обрабатываем видео оригинального поста
        handleOriginalPostVideo(holder, originalPost);
        
        handleOriginalPostPoll(holder, originalPost);
        
        // Обрабатываем неподдерживаемые элементы оригинального поста
        handleOriginalPostUnsupportedElements(holder, originalPost);
        
        handleCopyright(holder.originalPostCopyrightContainer, holder.originalPostCopyrightLink, originalPost);
        
        // Устанавливаем клик на оригинальный пост
        holder.originalPostContainer.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onCommentClick(originalPost); // Переходим к оригинальному посту
            }
        });
    }
    
    /**
     * Обработка аудио вложений оригинального поста
     */
    private void handleOriginalPostAudio(@NonNull PostViewHolder holder, Post originalPost) {
        if (originalPost.getAudioAttachments() != null && !originalPost.getAudioAttachments().isEmpty()) {
            AudioAttachmentView.addAudioAttachments(
                context, 
                holder.originalPostAudioContainer, 
                originalPost.getAudioAttachments(), 
                null
            );
        } else {
            AudioAttachmentView.clearAudioAttachments(holder.originalPostAudioContainer);
        }
    }
    
    /**
     * Обработка видео вложений оригинального поста
     */
    private void handleOriginalPostVideo(@NonNull PostViewHolder holder, Post originalPost) {
        if (originalPost.getVideoAttachments() != null && !originalPost.getVideoAttachments().isEmpty()) {
            addVideoAttachments(holder.originalPostVideoContainer, originalPost.getVideoAttachments());
        } else {
            clearVideoAttachments(holder.originalPostVideoContainer);
        }
    }
    
    /**
     * Обработка обычного поста
     */
    private void handleRegularPost(@NonNull PostViewHolder holder, Post post) {
        
        // Устанавливаем текст поста - sanitizeText обрабатывает переносы
        String postContent = post.getContent();
        if (ValidationUtils.isValidPostText(postContent)) {
            String sanitizedText = ValidationUtils.SanitizeText(postContent);
            holder.content.setText(sanitizedText);
            Linkify.addLinks(holder.content, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            holder.content.setMovementMethod(SafeLinkMovementMethod.getInstance());
            holder.content.setVisibility(View.VISIBLE);
        } else {
            holder.content.setVisibility(View.GONE);
        }
        
        // Скрываем контейнер оригинального поста
        holder.originalPostContainer.setVisibility(View.GONE);
        org.nikanikoo.flux.ui.views.PollAttachmentView.clearPollAttachments(holder.originalPostPollContainer);
        
        // Обработка изображений поста с оптимизацией
        handlePostImages(holder, post);
        
        // Обработка аудио вложений
        handlePostAudio(holder, post);
        
        // Обработка видео вложений
        handlePostVideo(holder, post);

        handlePostPoll(holder, post);
    }
    
    /**
     * Обработка аудио вложений поста
     */
    private void handlePostAudio(@NonNull PostViewHolder holder, Post post) {
        if (post.getAudioAttachments() != null && !post.getAudioAttachments().isEmpty()) {
            AudioAttachmentView.addAudioAttachments(
                context, 
                holder.audioContainer, 
                post.getAudioAttachments(), 
                null
            );
        } else {
            AudioAttachmentView.clearAudioAttachments(holder.audioContainer);
        }
    }
    
    /**
     * Обработка видео вложений поста
     */
    private void handlePostVideo(@NonNull PostViewHolder holder, Post post) {
        if (post.getVideoAttachments() != null && !post.getVideoAttachments().isEmpty()) {
            addVideoAttachments(holder.videoContainer, post.getVideoAttachments());
        } else {
            clearVideoAttachments(holder.videoContainer);
        }
    }

    private void handleOriginalPostPoll(@NonNull PostViewHolder holder, Post originalPost) {
        if (originalPost.getPollAttachments() != null && !originalPost.getPollAttachments().isEmpty()) {
            org.nikanikoo.flux.ui.views.PollAttachmentView.addPollAttachments(
                context,
                holder.originalPostPollContainer,
                originalPost.getPollAttachments()
            );
        } else {
            org.nikanikoo.flux.ui.views.PollAttachmentView.clearPollAttachments(holder.originalPostPollContainer);
        }
    }

    private void handlePostPoll(@NonNull PostViewHolder holder, Post post) {
        if (post.getPollAttachments() != null && !post.getPollAttachments().isEmpty()) {
            org.nikanikoo.flux.ui.views.PollAttachmentView.addPollAttachments(
                context,
                holder.pollContainer,
                post.getPollAttachments()
            );
        } else {
            org.nikanikoo.flux.ui.views.PollAttachmentView.clearPollAttachments(holder.pollContainer);
        }
    }
    
    /**
     * Обработка изображений оригинального поста
     */
    private void handleOriginalPostImages(@NonNull PostViewHolder holder, Post originalPost) {
        List<String> imageUrls = originalPost.getImageUrls();
        
        if (imageUrls == null || imageUrls.isEmpty()) {
            holder.originalPostImagesCollage.setVisibility(View.GONE);
            return;
        }
        
        // Фильтруем валидные URL
        List<String> validUrls = new ArrayList<>();
        for (String url : imageUrls) {
            if (ImageUtils.isValidImageUrl(url)) {
                validUrls.add(url);
            }
        }
        
        if (validUrls.isEmpty()) {
            holder.originalPostImagesCollage.setVisibility(View.GONE);
            return;
        }
        
        // Показываем коллаж изображений
        holder.originalPostImagesCollage.setVisibility(View.VISIBLE);
        holder.originalPostImagesCollage.setImages(validUrls);
        holder.originalPostImagesCollage.setOnImageClickListener((imagePosition, urls) -> 
            openPhotoViewer(imagePosition, urls, originalPost));
    }
    
    /**
     * Обработка неподдерживаемых элементов оригинального поста
     */
    private void handleOriginalPostUnsupportedElements(@NonNull PostViewHolder holder, Post originalPost) {
        String unsupportedText = originalPost.getUnsupportedElementsText();
        if (ValidationUtils.isValidPostText(unsupportedText)) {
            String sanitizedText = ValidationUtils.SanitizeText(unsupportedText);
            holder.originalPostUnsupportedElements.setText(sanitizedText);
            Linkify.addLinks(holder.originalPostUnsupportedElements, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            holder.originalPostUnsupportedElements.setMovementMethod(SafeLinkMovementMethod.getInstance());
            holder.originalPostUnsupportedElements.setVisibility(View.VISIBLE);
        } else {
            holder.originalPostUnsupportedElements.setVisibility(View.GONE);
        }
    }
    
    /**
     * Обработка изображений поста с оптимизацией
     */
    private void handlePostImages(@NonNull PostViewHolder holder, Post post) {
        List<String> imageUrls = post.getImageUrls();
        
        if (imageUrls == null || imageUrls.isEmpty()) {
            // Нет изображений
            holder.imagesCollage.setVisibility(View.GONE);
            return;
        }
        
        // Фильтруем валидные URL
        List<String> validUrls = new ArrayList<>();
        for (String url : imageUrls) {
            if (ImageUtils.isValidImageUrl(url)) {
                validUrls.add(url);
            }
        }
        
        if (validUrls.isEmpty()) {
            holder.imagesCollage.setVisibility(View.GONE);
            return;
        }
        
        holder.imagesCollage.setVisibility(View.VISIBLE);
        
        holder.imagesCollage.setImages(validUrls);
        holder.imagesCollage.setOnImageClickListener((imagePosition, urls) ->
            openPhotoViewer(imagePosition, urls, post));
    }
    
    /**
     * Обработка неподдерживаемых элементов
     */
    private void handleUnsupportedElements(@NonNull PostViewHolder holder, Post post) {
        String unsupportedText = post.getUnsupportedElementsText();
        if (ValidationUtils.isValidPostText(unsupportedText)) {
            String sanitizedText = ValidationUtils.SanitizeText(unsupportedText);
            holder.unsupportedElements.setText(sanitizedText);
            Linkify.addLinks(holder.unsupportedElements, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            holder.unsupportedElements.setMovementMethod(SafeLinkMovementMethod.getInstance());
            holder.unsupportedElements.setVisibility(View.VISIBLE);
        } else {
            holder.unsupportedElements.setVisibility(View.GONE);
        }
    }

    private void handleCopyright(View container, TextView linkView, Post post) {
        if (container == null || linkView == null) return;
        if (post != null && post.getCopyrightLink() != null && !post.getCopyrightLink().isEmpty()) {
            String displayText = post.getCopyrightName() != null && !post.getCopyrightName().isEmpty() 
                ? post.getCopyrightName() 
                : post.getCopyrightLink();
            linkView.setText(displayText);
            linkView.setOnClickListener(v -> {
                SafeLinkMovementMethod.handleLinkClick(context, post.getCopyrightLink());
            });
            container.setVisibility(View.VISIBLE);
        } else {
            container.setVisibility(View.GONE);
            linkView.setOnClickListener(null);
        }
    }

    private void setDeviceIcon(ImageView imageView, String platform) {
        if (imageView == null) return;
        if (platform != null) {
            int iconResId = 0;
            switch (platform.toLowerCase()) {
                case "android":
                    iconResId = R.drawable.ic_android;
                    break;
                case "iphone":
                    iconResId = R.drawable.ic_ios;
                    break;
                case "wphone":
                    iconResId = R.drawable.ic_window;
                    break;
                case "mobile":
                    iconResId = R.drawable.ic_mobile_3;
                    break;
                case "api":
                    iconResId = R.drawable.ic_settings;
                    break;
            }
            if (iconResId != 0) {
                imageView.setImageResource(iconResId);
                imageView.setVisibility(View.VISIBLE);
            } else {
                imageView.setVisibility(View.GONE);
            }
        } else {
            imageView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Установка обработчиков кликов
     */
    private void setupClickListeners(@NonNull PostViewHolder holder, Post post) {
        View.OnClickListener authorClickListener = v -> {
            if (clickListener != null && post.getAuthorId() != 0) {
                clickListener.onAuthorClick(post.getAuthorId(), post.getAuthorName(), post.isGroup());
            }
        };
        
        holder.avatar.setOnClickListener(authorClickListener);
        
        boolean isWallPostOnOtherWall = !isProfileWall && post.getOwnerId() != 0 && post.getAuthorId() != 0 && 
                !(Math.abs(post.getOwnerId()) == Math.abs(post.getAuthorId()) && post.isGroup() == post.isOwnerGroup());
                
        if (isWallPostOnOtherWall) {
            holder.authorName.setOnClickListener(null);
        } else {
            holder.authorName.setOnClickListener(authorClickListener);
        }

        holder.likeButton.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onLikeClick(post);
            }
        });
        
        holder.commentButton.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onCommentClick(post);
            }
        });
        
        holder.shareButton.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onShareClick(post);
            }
        });
        
        // Обработчик клика по кнопке меню поста (вместо long press)
        if (holder.postMenuButton != null) {
            holder.postMenuButton.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onPostLongClick(post, v);
                }
            });
        }
    }

    private void updateLikeState(PostViewHolder holder, Post post) {
        holder.likeCount.setText(String.valueOf(post.getLikeCount()));
        
        System.out.println("Updating like state for post " + post.getPostId() + ": liked=" + post.isLiked() + " count=" + post.getLikeCount());
        
        if (post.isLiked()) {
            holder.likeIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, R.color.like_active));
            holder.likeCount.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.like_active));
        } else {
            holder.likeIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, R.color.like_inactive));
            holder.likeCount.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.like_inactive));
        }
    }

    public void updatePost(int position, Post updatedPost) {
        synchronized (postsLock) {
            if (position >= 0 && position < posts.size()) {
                posts.set(position, updatedPost);
                notifyItemChanged(position);
            }
        }
    }

    // Добавление новых постов для бесконечного скролла
    public void addPosts(List<Post> newPosts) {
        hideLoading(); // Скрываем индикатор загрузки
        if (newPosts == null || newPosts.isEmpty()) {
            Logger.d(TAG, "No new posts to add");
            return;
        }

        Logger.d(TAG, "Adding " + newPosts.size() + " new posts to existing " + posts.size() + " posts");

        // Оптимизированная фильтрация дубликатов
        List<Post> uniquePosts = new ArrayList<>();
        for (Post newPost : newPosts) {
            if (newPost != null && !isDuplicateOptimized(newPost)) {
                uniquePosts.add(newPost);
                String postKey = generatePostKey(newPost);
                if (postKey != null) {
                    postIds.add(postKey);
                }
            }
        }

        if (!uniquePosts.isEmpty()) {
            int startPosition;
            synchronized (postsLock) {
                startPosition = posts.size();
                posts.addAll(uniquePosts);
            }
            notifyItemRangeInserted(startPosition, uniquePosts.size());
            Logger.d(TAG, "Successfully added " + uniquePosts.size() + " unique posts, filtered " +
                     (newPosts.size() - uniquePosts.size()) + " duplicates");
        } else {
            Logger.d(TAG, "All " + newPosts.size() + " posts were duplicates, nothing added");
        }
    }

    // Оптимизированная проверка на дубликат поста
    private boolean isDuplicateOptimized(Post newPost) {
        if (newPost == null) return true;
        
        String postKey = generatePostKey(newPost);
        if (postKey != null) {
            return postIds.contains(postKey);
        }
        
        // Fallback к старому методу если не удалось сгенерировать ключ
        return isDuplicateLegacy(newPost);
    }

    // Старый метод проверки дубликатов (fallback)
    private boolean isDuplicateLegacy(Post newPost) {
        synchronized (postsLock) {
            for (Post existingPost : posts) {
                if (existingPost == null) continue;

                if (ValidationUtils.isValidPostOwnerId(newPost.getPostId()) && ValidationUtils.isValidPostOwnerId(newPost.getOwnerId()) &&
                    ValidationUtils.isValidPostOwnerId(existingPost.getPostId()) && ValidationUtils.isValidPostOwnerId(existingPost.getOwnerId())) {
                    if (existingPost.getPostId() == newPost.getPostId() &&
                        existingPost.getOwnerId() == newPost.getOwnerId()) {
                        return true;
                    }
                } else {
                    if (existingPost.getAuthorName() != null && newPost.getAuthorName() != null &&
                        existingPost.getContent() != null && newPost.getContent() != null &&
                        existingPost.getTimestamp() != null && newPost.getTimestamp() != null &&
                        existingPost.getAuthorName().equals(newPost.getAuthorName()) &&
                        existingPost.getContent().equals(newPost.getContent()) &&
                        existingPost.getTimestamp().equals(newPost.getTimestamp())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Очистка и установка новых постов (для обновления)
    public void setPosts(List<Post> newPosts) {
        hideLoading(); // Скрываем индикатор загрузки
        synchronized (postsLock) {
            posts.clear();
            postIds.clear();

            if (newPosts != null) {
                posts.addAll(newPosts);
                updatePostIds();
            }
        }
        notifyDataSetChanged();
        Logger.d(TAG, "Set " + (newPosts != null ? newPosts.size() : 0) + " posts");
    }

    // Обновление постов с использованием DiffUtil для эффективных обновлений
    public void updatePostsWithDiff(List<Post> newPosts) {
        hideLoading();

        if (newPosts == null) {
            newPosts = new ArrayList<>();
        }

        // Create DiffUtil callback
        List<Post> oldList;
        synchronized (postsLock) {
            oldList = new ArrayList<>(posts);
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PostDiffCallback(oldList, newPosts), false);

        // Clear and add new posts
        synchronized (postsLock) {
            posts.clear();
            postIds.clear();
            posts.addAll(newPosts);
            updatePostIds();
        }

        // Apply diff result
        diffResult.dispatchUpdatesTo(this);

        Logger.d(TAG, "Updated " + newPosts.size() + " posts with DiffUtil");
    }

    // DiffUtil callback for efficient post updates
    private static class PostDiffCallback extends DiffUtil.Callback {
        private final List<Post> oldList;
        private final List<Post> newList;

        PostDiffCallback(List<Post> oldList, List<Post> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            Post oldPost = oldList.get(oldItemPosition);
            Post newPost = newList.get(newItemPosition);
            return oldPost.getPostId() == newPost.getPostId() 
                    && oldPost.getOwnerId() == newPost.getOwnerId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Post oldPost = oldList.get(oldItemPosition);
            Post newPost = newList.get(newItemPosition);

            boolean sameContent = (oldPost.getContent() == null && newPost.getContent() == null)
                    || (oldPost.getContent() != null && oldPost.getContent().equals(newPost.getContent()));
            
            return sameContent 
                    && oldPost.getLikeCount() == newPost.getLikeCount()
                    && oldPost.isLiked() == newPost.isLiked()
                    && oldPost.getCommentCount() == newPost.getCommentCount();
        }
    }
    
    // Получение поста по позиции
    public Post getPost(int position) {
        synchronized (postsLock) {
            if (position >= 0 && position < posts.size()) {
                return posts.get(position);
            }
        }
        return null;
    }

    // Получение количества постов (реальных, без loading item)
    public int getPostsCount() {
        synchronized (postsLock) {
            return posts.size();
        }
    }

    // Поиск позиции поста по ID
    public int findPostPosition(int postId, int ownerId) {
        synchronized (postsLock) {
            for (int i = 0; i < posts.size(); i++) {
                Post post = posts.get(i);
                if (post.getPostId() == postId && post.getOwnerId() == ownerId) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        synchronized (postsLock) {
            return (isLoading && position == posts.size()) ? VIEW_TYPE_LOADING : VIEW_TYPE_POST;
        }
    }

    @Override
    public int getItemCount() {
        synchronized (postsLock) {
            return posts.size() + (isLoading ? 1 : 0);
        }
    }

    // Показать индикатор загрузки
    public void showLoading() {
        synchronized (postsLock) {
            if (!isLoading) {
                isLoading = true;
                notifyItemInserted(posts.size());
            }
        }
    }

    // Скрыть индикатор загрузки
    public void hideLoading() {
        synchronized (postsLock) {
            if (isLoading) {
                isLoading = false;
                notifyItemRemoved(posts.size());
            }
        }
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView authorName;
        TextView timestamp;
        TextView content;
        PostImagesCollage imagesCollage;
        android.widget.LinearLayout audioContainer;
        android.widget.LinearLayout videoContainer;
        android.widget.LinearLayout pollContainer;
        TextView unsupportedElements;
        View likeButton;
        ImageView likeIcon;
        TextView likeCount;
        View commentButton;
        TextView commentCount;
        View shareButton;
        View repostIndicator; // Индикатор репоста
        ImageView postMenuButton;
        ImageView authorVerified;
        ImageView deviceIcon;
        
        // Поля для репостов
        View originalPostContainer;
        ImageView originalPostAvatar;
        TextView originalPostAuthorName;
        TextView originalPostTimestamp;
        ImageView originalPostDeviceIcon;
        TextView originalPostContent;
        PostImagesCollage originalPostImagesCollage;
        android.widget.LinearLayout originalPostAudioContainer;
        android.widget.LinearLayout originalPostVideoContainer;
        android.widget.LinearLayout originalPostPollContainer;
        TextView originalPostUnsupportedElements;
        TextView originalPostLikeCount;
        TextView originalPostCommentCount;
        ImageView originalPostAuthorVerified;
        
        // NSFW spoiler views
        View postBodyContainer;
        View nsfwSpoiler;
        View originalPostPostBodyContainer;
        View originalPostNsfwSpoiler;

        // Copyright views
        View copyrightContainer;
        TextView copyrightLink;
        View originalPostCopyrightContainer;
        TextView originalPostCopyrightLink;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.post_avatar);
            authorName = itemView.findViewById(R.id.post_author_name);
            timestamp = itemView.findViewById(R.id.post_timestamp);
            deviceIcon = itemView.findViewById(R.id.post_device_icon);
            content = itemView.findViewById(R.id.post_content);
            imagesCollage = itemView.findViewById(R.id.post_images_collage);
            audioContainer = itemView.findViewById(R.id.post_audio_container);
            videoContainer = itemView.findViewById(R.id.post_video_container);
            pollContainer = itemView.findViewById(R.id.post_poll_container);
            unsupportedElements = itemView.findViewById(R.id.post_unsupported_elements);
            likeButton = itemView.findViewById(R.id.post_like_button);
            likeIcon = itemView.findViewById(R.id.post_like_icon);
            likeCount = itemView.findViewById(R.id.post_like_count);
            commentButton = itemView.findViewById(R.id.post_comment_button);
            commentCount = itemView.findViewById(R.id.post_comment_count);
            shareButton = itemView.findViewById(R.id.post_share_button);
            postMenuButton = itemView.findViewById(R.id.post_menu_button);
            authorVerified = itemView.findViewById(R.id.post_author_verified);
            
            // Инициализация полей репоста
            originalPostContainer = itemView.findViewById(R.id.original_post_container);
            originalPostAvatar = itemView.findViewById(R.id.original_post_avatar);
            originalPostAuthorName = itemView.findViewById(R.id.original_post_author_name);
            originalPostTimestamp = itemView.findViewById(R.id.original_post_timestamp);
            originalPostDeviceIcon = itemView.findViewById(R.id.original_post_device_icon);
            originalPostContent = itemView.findViewById(R.id.original_post_content);
            originalPostImagesCollage = itemView.findViewById(R.id.original_post_images_collage);
            originalPostAudioContainer = itemView.findViewById(R.id.original_post_audio_container);
            originalPostVideoContainer = itemView.findViewById(R.id.original_post_video_container);
            originalPostPollContainer = itemView.findViewById(R.id.original_post_poll_container);
            originalPostUnsupportedElements = itemView.findViewById(R.id.original_post_unsupported_elements);
            originalPostLikeCount = itemView.findViewById(R.id.original_post_like_count);
            originalPostCommentCount = itemView.findViewById(R.id.original_post_comment_count);
            originalPostAuthorVerified = itemView.findViewById(R.id.original_post_author_verified);

            postBodyContainer = itemView.findViewById(R.id.post_body_container);
            nsfwSpoiler = itemView.findViewById(R.id.nsfw_spoiler);
            originalPostPostBodyContainer = itemView.findViewById(R.id.original_post_body_container);
            originalPostNsfwSpoiler = itemView.findViewById(R.id.original_post_nsfw_spoiler);

            copyrightContainer = itemView.findViewById(R.id.post_copyright_container);
            copyrightLink = itemView.findViewById(R.id.post_copyright_link);
            originalPostCopyrightContainer = itemView.findViewById(R.id.original_post_copyright_container);
            originalPostCopyrightLink = itemView.findViewById(R.id.original_post_copyright_link);
        }
    }

    public static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
    
    private void openPhotoViewer(int position, List<String> imageUrls, Post post) {
        PhotoViewerActivity.start(
            context,
            imageUrls, position, post, post.getAuthorName()
        );
    }
    
    /**
     * Добавление видео вложений в контейнер
     */
    private void addVideoAttachments(android.widget.LinearLayout container, List<org.nikanikoo.flux.data.models.Video> videos) {
        if (container == null || videos == null || videos.isEmpty()) {
            return;
        }
        
        container.removeAllViews();
        container.setVisibility(View.VISIBLE);
        
        for (org.nikanikoo.flux.data.models.Video video : videos) {
            org.nikanikoo.flux.ui.views.VideoAttachmentView videoView =
                new org.nikanikoo.flux.ui.views.VideoAttachmentView(context);
            videoView.setVideo(video);
            container.addView(videoView);
        }
    }
    
    /**
     * Очистка видео вложений из контейнера
     */
    private void clearVideoAttachments(android.widget.LinearLayout container) {
        if (container != null) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof PostViewHolder) {
            PostViewHolder postHolder = (PostViewHolder) holder;
            if (postHolder.content != null) {
                postHolder.content.clearFocus();
            }
            if (postHolder.originalPostContent != null) {
                postHolder.originalPostContent.clearFocus();
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof PostViewHolder) {
            PostViewHolder postHolder = (PostViewHolder) holder;
            if (postHolder.content != null) {
                postHolder.content.clearFocus();
            }
            if (postHolder.originalPostContent != null) {
                postHolder.originalPostContent.clearFocus();
            }
        }
    }
}