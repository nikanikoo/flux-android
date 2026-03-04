package org.nikanikoo.flux.ui.adapters.posts;

import android.content.Context;
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
import org.nikanikoo.flux.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "PostAdapter";
    private static final int VIEW_TYPE_POST = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    private final List<Post> posts = new CopyOnWriteArrayList<>();
    private Context context;
    private OnPostClickListener clickListener;
    private volatile boolean isLoading = false;
    
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
        for (Post post : posts) {
            if (post != null) {
                String postKey = generatePostKey(post);
                if (postKey != null) {
                    postIds.add(postKey);
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

    private void bindPostViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        
        // Валидация данных поста
        if (post == null) {
            Logger.w(TAG, "Post at position " + position + " is null");
            return;
        }
        
        // Безопасная установка текстовых данных
        holder.authorName.setText(ValidationUtils.sanitizeUserInput(post.getAuthorName()));
        holder.timestamp.setText(post.getTimestamp());
        holder.likeCount.setText(String.valueOf(Math.max(0, post.getLikeCount())));
        holder.commentCount.setText(String.valueOf(Math.max(0, post.getCommentCount())));
        
        // Отображение галочки верификации автора поста
        Logger.d(TAG, "Post author " + post.getAuthorName() + " verified: " + post.isAuthorVerified());
        if (holder.authorVerified != null) {
            holder.authorVerified.setVisibility(post.isAuthorVerified() ? View.VISIBLE : View.GONE);
        }
        
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
            holder.content.setText(ValidationUtils.SanitizeText(repostText));
            holder.content.setVisibility(View.VISIBLE);
        } else {
            holder.content.setVisibility(View.GONE);
        }
        
        // Скрываем изображения основного поста для репостов
        holder.postImage.setVisibility(View.GONE);
        holder.imagesCollage.setVisibility(View.GONE);
        AudioAttachmentView.clearAudioAttachments(holder.audioContainer);
        clearVideoAttachments(holder.videoContainer);
        
        // Показываем контейнер оригинального поста
        holder.originalPostContainer.setVisibility(View.VISIBLE);
        
        // Заполняем данные оригинального поста
        holder.originalPostAuthorName.setText(ValidationUtils.sanitizeUserInput(originalPost.getAuthorName()));
        holder.originalPostTimestamp.setText(originalPost.getTimestamp());
        holder.originalPostContent.setText(ValidationUtils.SanitizeText(originalPost.getContent()));
        
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
        
        // Обрабатываем неподдерживаемые элементы оригинального поста
        handleOriginalPostUnsupportedElements(holder, originalPost);
        
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
        holder.content.setText(ValidationUtils.SanitizeText(post.getContent()));
        holder.content.setVisibility(View.VISIBLE);
        
        // Скрываем контейнер оригинального поста
        holder.originalPostContainer.setVisibility(View.GONE);
        
        // Обработка изображений поста с оптимизацией
        handlePostImages(holder, post);
        
        // Обработка аудио вложений
        handlePostAudio(holder, post);
        
        // Обработка видео вложений
        handlePostVideo(holder, post);
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
            holder.originalPostUnsupportedElements.setText(ValidationUtils.SanitizeText(unsupportedText));
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
            holder.postImage.setVisibility(View.GONE);
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
            holder.postImage.setVisibility(View.GONE);
            holder.imagesCollage.setVisibility(View.GONE);
            return;
        }
        
        if (validUrls.size() == 1) {
            // Одно изображение
            holder.postImage.setVisibility(View.VISIBLE);
            holder.imagesCollage.setVisibility(View.GONE);
            
            ImageUtils.loadPostImage(validUrls.get(0), holder.postImage);
            
            holder.postImage.setOnClickListener(v -> openPhotoViewer(0, validUrls, post));
        } else {
            // Несколько изображений - коллаж
            holder.postImage.setVisibility(View.GONE);
            holder.imagesCollage.setVisibility(View.VISIBLE);
            
            holder.imagesCollage.setImages(validUrls);
            holder.imagesCollage.setOnImageClickListener((imagePosition, urls) -> 
                openPhotoViewer(imagePosition, urls, post));
        }
    }
    
    /**
     * Обработка неподдерживаемых элементов
     */
    private void handleUnsupportedElements(@NonNull PostViewHolder holder, Post post) {
        String unsupportedText = post.getUnsupportedElementsText();
        if (ValidationUtils.isValidPostText(unsupportedText)) {
            holder.unsupportedElements.setText(ValidationUtils.SanitizeText(unsupportedText));
            holder.unsupportedElements.setVisibility(View.VISIBLE);
        } else {
            holder.unsupportedElements.setVisibility(View.GONE);
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
        holder.authorName.setOnClickListener(authorClickListener);

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
        if (position >= 0 && position < posts.size()) {
            posts.set(position, updatedPost);
            notifyItemChanged(position);
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
            int startPosition = posts.size();
            posts.addAll(uniquePosts);
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
        for (Post existingPost : posts) {
            if (existingPost == null) continue;
            
            // Проверяем по ID поста и владельца (если они есть)
            if (ValidationUtils.isValidPostOwnerId(newPost.getPostId()) && ValidationUtils.isValidPostOwnerId(newPost.getOwnerId()) &&
                ValidationUtils.isValidPostOwnerId(existingPost.getPostId()) && ValidationUtils.isValidPostOwnerId(existingPost.getOwnerId())) {
                if (existingPost.getPostId() == newPost.getPostId() && 
                    existingPost.getOwnerId() == newPost.getOwnerId()) {
                    return true;
                }
            } else {
                // Если нет ID, проверяем по содержимому и автору
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
        return false;
    }

    // Очистка и установка новых постов (для обновления)
    public void setPosts(List<Post> newPosts) {
        hideLoading(); // Скрываем индикатор загрузки
        posts.clear();
        postIds.clear(); // Очищаем кеш ID
        
        if (newPosts != null) {
            posts.addAll(newPosts);
            updatePostIds(); // Обновляем кеш ID
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
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PostDiffCallback(posts, newPosts), false);
        
        // Clear and add new posts
        posts.clear();
        postIds.clear();
        posts.addAll(newPosts);
        updatePostIds();
        
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
        if (position >= 0 && position < posts.size()) {
            return posts.get(position);
        }
        return null;
    }

    // Получение количества постов (реальных, без loading item)
    public int getPostsCount() {
        return posts.size();
    }

    // Поиск позиции поста по ID
    public int findPostPosition(int postId, int ownerId) {
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.getPostId() == postId && post.getOwnerId() == ownerId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        return (isLoading && position == posts.size()) ? VIEW_TYPE_LOADING : VIEW_TYPE_POST;
    }

    @Override
    public int getItemCount() {
        return posts.size() + (isLoading ? 1 : 0);
    }

    // Показать индикатор загрузки
    public void showLoading() {
        if (!isLoading) {
            isLoading = true;
            notifyItemInserted(posts.size());
        }
    }

    // Скрыть индикатор загрузки
    public void hideLoading() {
        if (isLoading) {
            isLoading = false;
            notifyItemRemoved(posts.size());
        }
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView authorName;
        TextView timestamp;
        TextView content;
        ImageView postImage;
        PostImagesCollage imagesCollage;
        android.widget.LinearLayout audioContainer;
        android.widget.LinearLayout videoContainer;
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
        
        // Поля для репостов
        View originalPostContainer;
        ImageView originalPostAvatar;
        TextView originalPostAuthorName;
        TextView originalPostTimestamp;
        TextView originalPostContent;
        PostImagesCollage originalPostImagesCollage;
        android.widget.LinearLayout originalPostAudioContainer;
        android.widget.LinearLayout originalPostVideoContainer;
        TextView originalPostUnsupportedElements;
        TextView originalPostLikeCount;
        TextView originalPostCommentCount;
        ImageView originalPostAuthorVerified;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.post_avatar);
            authorName = itemView.findViewById(R.id.post_author_name);
            timestamp = itemView.findViewById(R.id.post_timestamp);
            content = itemView.findViewById(R.id.post_content);
            postImage = itemView.findViewById(R.id.post_image);
            imagesCollage = itemView.findViewById(R.id.post_images_collage);
            audioContainer = itemView.findViewById(R.id.post_audio_container);
            videoContainer = itemView.findViewById(R.id.post_video_container);
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
            originalPostContent = itemView.findViewById(R.id.original_post_content);
            originalPostImagesCollage = itemView.findViewById(R.id.original_post_images_collage);
            originalPostAudioContainer = itemView.findViewById(R.id.original_post_audio_container);
            originalPostVideoContainer = itemView.findViewById(R.id.original_post_video_container);
            originalPostUnsupportedElements = itemView.findViewById(R.id.original_post_unsupported_elements);
            originalPostLikeCount = itemView.findViewById(R.id.original_post_like_count);
            originalPostCommentCount = itemView.findViewById(R.id.original_post_comment_count);
            originalPostAuthorVerified = itemView.findViewById(R.id.original_post_author_verified);
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
}