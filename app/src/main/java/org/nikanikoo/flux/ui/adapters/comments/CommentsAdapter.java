package org.nikanikoo.flux.ui.adapters.comments;

import android.content.Context;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.views.AudioAttachmentView;
import org.nikanikoo.flux.utils.SafeLinkMovementMethod;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.util.List;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private List<Comment> comments;
    private Context context;
    private OnCommentClickListener clickListener;

    public interface OnCommentClickListener {
        void onAuthorClick(int authorId, String authorName, boolean isGroup);
        void onLikeClick(Comment comment);
        void onReplyClick(Comment comment);
        void onImageClick(String imageUrl);
    }

    public CommentsAdapter(Context context, List<Comment> comments) {
        this.context = context;
        this.comments = comments;
    }

    public void setOnCommentClickListener(OnCommentClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        bindCommentViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            Comment comment = comments.get(position);
            boolean likeUpdated = false;
            for (Object payload : payloads) {
                if ("LIKE_UPDATE".equals(payload)) {
                    likeUpdated = true;
                    break;
                }
            }
            if (likeUpdated) {
                updateLikeState(holder, comment);
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindCommentViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        
        holder.authorName.setText(comment.getAuthorName());
        holder.timestamp.setText(comment.getTimestamp());
        holder.content.setText(ValidationUtils.SanitizeText(comment.getText()));
        holder.likeCount.setText(String.valueOf(comment.getLikesCount()));
        
        Linkify.addLinks(holder.content, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        holder.content.setMovementMethod(SafeLinkMovementMethod.getInstance());
        
        // Отображение галочки верификации автора комментария
        if (holder.authorVerified != null) {
            holder.authorVerified.setVisibility(comment.isAuthorVerified() ? View.VISIBLE : View.GONE);
        }
        
        // Загружаем аватарку автора
        if (comment.getAuthorAvatarUrl() != null && !comment.getAuthorAvatarUrl().isEmpty()) {
            Picasso.get()
                    .load(comment.getAuthorAvatarUrl())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.drawable.camera_200);
        }
        
        // Загружаем изображение комментария
        if (comment.getImageUrl() != null && !comment.getImageUrl().isEmpty()) {
            holder.commentImage.setVisibility(View.VISIBLE);
            Picasso.get()
                    .load(comment.getImageUrl())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(holder.commentImage);
            
            // Добавляем обработчик клика на изображение комментария
            holder.commentImage.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onImageClick(comment.getImageUrl());
                }
            });
        } else {
            holder.commentImage.setVisibility(View.GONE);
        }
        
        // Обработка аудио вложений
        if (comment.getAudioAttachments() != null && !comment.getAudioAttachments().isEmpty()) {
            AudioAttachmentView.addAudioAttachments(
                context,
                holder.audioContainer,
                comment.getAudioAttachments(),
                null
            );
        } else {
            AudioAttachmentView.clearAudioAttachments(holder.audioContainer);
        }
        
        // Обработка видео вложений
        if (comment.getVideoAttachments() != null && !comment.getVideoAttachments().isEmpty()) {
            addVideoAttachments(holder.videoContainer, comment.getVideoAttachments());
        } else {
            clearVideoAttachments(holder.videoContainer);
        }
        
        // Отображаем неподдерживаемые элементы
        if (comment.getUnsupportedElementsText() != null && !comment.getUnsupportedElementsText().isEmpty()) {
            holder.unsupportedElements.setText(comment.getUnsupportedElementsText());
            holder.unsupportedElements.setVisibility(View.VISIBLE);
            Linkify.addLinks(holder.unsupportedElements, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            holder.unsupportedElements.setMovementMethod(SafeLinkMovementMethod.getInstance());
        } else {
            holder.unsupportedElements.setVisibility(View.GONE);
        }
        
        // Обновляем состояние лайка
        updateLikeState(holder, comment);
        
        // Обработчики кликов
        View.OnClickListener authorClickListener = v -> {
            if (clickListener != null && comment.getFromId() != 0) {
                clickListener.onAuthorClick(comment.getFromId(), comment.getAuthorName(), comment.isGroup());
            }
        };
        
        holder.avatar.setOnClickListener(authorClickListener);
        holder.authorName.setOnClickListener(authorClickListener);

        holder.likeButton.setOnClickListener(v -> {
            System.out.println("Like button clicked for comment " + comment.getId());
            if (clickListener != null) {
                clickListener.onLikeClick(comment);
            }
        });
        
        holder.replyButton.setOnClickListener(v -> {
            System.out.println("Reply button clicked for comment " + comment.getId());
            if (clickListener != null) {
                clickListener.onReplyClick(comment);
            }
        });
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    private void updateLikeState(CommentViewHolder holder, Comment comment) {
        holder.likeCount.setText(String.valueOf(comment.getLikesCount()));
        
        System.out.println("Updating comment like state for comment " + comment.getId() + ": liked=" + comment.isLiked() + " count=" + comment.getLikesCount());
        
        if (comment.isLiked()) {
            holder.likeIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, R.color.like_active));
            holder.likeCount.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.like_active));
        } else {
            holder.likeIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, R.color.like_inactive));
            holder.likeCount.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.like_inactive));
        }
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView authorName;
        TextView timestamp;
        TextView content;
        ImageView commentImage;
        LinearLayout audioContainer;
        LinearLayout videoContainer;
        TextView unsupportedElements;
        View likeButton;
        ImageView likeIcon;
        TextView likeCount;
        TextView replyButton;
        ImageView authorVerified;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.comment_avatar);
            authorName = itemView.findViewById(R.id.comment_author_name);
            timestamp = itemView.findViewById(R.id.comment_timestamp);
            content = itemView.findViewById(R.id.comment_content);
            commentImage = itemView.findViewById(R.id.comment_image);
            audioContainer = itemView.findViewById(R.id.comment_audio_container);
            videoContainer = itemView.findViewById(R.id.comment_video_container);
            unsupportedElements = itemView.findViewById(R.id.comment_unsupported_elements);
            likeButton = itemView.findViewById(R.id.comment_like_button);
            likeIcon = itemView.findViewById(R.id.comment_like_icon);
            likeCount = itemView.findViewById(R.id.comment_like_count);
            replyButton = itemView.findViewById(R.id.comment_reply_button);
            authorVerified = itemView.findViewById(R.id.comment_author_verified);
        }
    }
    
    /**
     * Добавление видео вложений в контейнер
     */
    private void addVideoAttachments(LinearLayout container, List<org.nikanikoo.flux.data.models.Video> videos) {
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
    private void clearVideoAttachments(LinearLayout container) {
        if (container != null) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull CommentViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder.content != null) {
            holder.content.clearFocus();
        }
    }

    @Override
    public void onViewRecycled(@NonNull CommentViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.content != null) {
            holder.content.clearFocus();
        }
    }
}