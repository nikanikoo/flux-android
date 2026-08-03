package org.nikanikoo.flux.ui.fragments.profile;

import android.content.Context;
import android.text.util.Linkify;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.utils.SafeLinkMovementMethod;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Controller для управления отображением информации профиля.
 * Инкапсулирует логику обновления UI элементов профиля.
 */
public class ProfileInfoController {
    
    // Основные View
    private ImageView profileAvatarLarge;
    private TextView profileNameLarge;
    private ImageView profileVerified;
    private TextView profileOnline;
    private TextView profileStatus;
    
    // Счетчики
    private TextView friendsCount;
    private TextView followersCount;
    private TextView groupsCount;
    private TextView photosCount;
    private TextView videosCount;
    private TextView audiosCount;
    
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat dateTimeFormat;
    private Context context;
    
    public ProfileInfoController(View rootView) {
        this.context = rootView.getContext().getApplicationContext();
        initViews(rootView);
        dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    }
    
    private void initViews(View view) {
        // Основные View
        profileAvatarLarge = view.findViewById(R.id.profile_avatar_large);
        profileNameLarge = view.findViewById(R.id.profile_name_large);
        profileVerified = view.findViewById(R.id.profile_verified);
        profileOnline = view.findViewById(R.id.profile_online);
        profileStatus = view.findViewById(R.id.profile_status);
        
        // Счетчики
        friendsCount = view.findViewById(R.id.friends_count);
        followersCount = view.findViewById(R.id.followers_count);
        groupsCount = view.findViewById(R.id.groups_count);
        photosCount = view.findViewById(R.id.photos_count);
        videosCount = view.findViewById(R.id.videos_count);
        audiosCount = view.findViewById(R.id.audios_count);
    }
    
    /**
     * Обновить всю информацию профиля
     */
    public void updateProfileInfo(UserProfile profile) {
        if (profile == null) {
            return;
        }
        
        updateBasicInfo(profile);
        updateCounters(profile);
    }
    
    /**
     * Обновить основную информацию (имя, статус, аватар)
     */
    private void updateBasicInfo(UserProfile profile) {
        if (profileNameLarge != null) {
            profileNameLarge.setText(profile.getFullName());
        }
        
        if (profileVerified != null) {
            profileVerified.setVisibility(profile.isVerified() ? View.VISIBLE : View.GONE);
        }
        
        if (profileOnline != null) {
            profileOnline.setText(profile.isProfileOnline()
                    ? context.getString(R.string.profile_status_online)
                    : context.getString(R.string.profile_status_offline));
            profileOnline.setVisibility(View.VISIBLE);
        }
        
        if (profileStatus != null) {
            String status = profile.getProfileStatus();
            profileStatus.setText(status);
            setupLinkify(profileStatus);
            profileStatus.setVisibility(status != null && !status.isEmpty() ? View.VISIBLE : View.GONE);
        }
        
        // Загрузка аватара
        if (profileAvatarLarge != null) {
            String photoUrl = profile.getPhoto200();
            if (photoUrl != null && !photoUrl.isEmpty()) {
                Picasso.get()
                        .load(photoUrl)
                        .placeholder(R.drawable.camera_200)
                        .error(R.drawable.camera_200)
                        .into(profileAvatarLarge);
            }
        }
    }
    
    /**
     * Обновить счетчики
     */
    private void updateCounters(UserProfile profile) {
        setTextSafe(friendsCount, profile.getFriendsCount());
        setTextSafe(followersCount, profile.getFollowersCount());
        setTextSafe(groupsCount, profile.getGroupsCount());
        setTextSafe(photosCount, profile.getPhotosCount());
        setTextSafe(videosCount, profile.getVideosCount());
        setTextSafe(audiosCount, profile.getAudiosCount());
    }

    public void bindDetailsSheet(View sheetView, UserProfile profile) {
        if (sheetView == null || profile == null) {
            return;
        }
        
        // Основная информация
        boolean hasBasic = setSheetText(sheetView, R.id.sheet_row_name,
                context.getString(R.string.profile_name), profile.getFullName());
        hasBasic |= setSheetText(sheetView, R.id.sheet_row_screen_name,
                context.getString(R.string.profile_username), profile.getScreenName(),
                value -> "@" + value);
        
        String status = profile.getProfileStatus();
        if (status == null || status.isEmpty()) {
            status = profile.isProfileOnline()
                    ? context.getString(R.string.profile_status_online)
                    : context.getString(R.string.profile_status_offline);
        }
        hasBasic |= setSheetText(sheetView, R.id.sheet_row_status,
                context.getString(R.string.profile_status), status);
        hasBasic |= setSheetSex(sheetView, profile.getSex());
        hasBasic |= setSheetDate(sheetView, R.id.sheet_row_reg_date,
                context.getString(R.string.profile_date_registered),
                profile.getRegDate(), false);
        hasBasic |= setSheetDate(sheetView, R.id.sheet_row_last_seen,
                context.getString(R.string.profile_last_activity),
                profile.getLastSeen(), true);
        
        boolean hasContact = setSheetText(sheetView, R.id.sheet_row_city,
                context.getString(R.string.profile_city), profile.getCity());
        hasContact |= setSheetText(sheetView, R.id.sheet_row_email,
                context.getString(R.string.profile_email), profile.getEmail());
        hasContact |= setSheetText(sheetView, R.id.sheet_row_telegram,
                context.getString(R.string.profile_telegram), profile.getTelegram(),
                value -> "@" + value);
        
        View contactTitle = sheetView.findViewById(R.id.sheet_contact_title);
        View contactCard = sheetView.findViewById(R.id.sheet_contact_card);
        if (contactTitle != null) {
            contactTitle.setVisibility(hasContact ? View.VISIBLE : View.GONE);
        }
        if (contactCard != null) {
            contactCard.setVisibility(hasContact ? View.VISIBLE : View.GONE);
        }
        
        boolean hasPersonal = setSheetText(sheetView, R.id.sheet_row_about,
                context.getString(R.string.profile_about), profile.getAbout());
        hasPersonal |= setSheetText(sheetView, R.id.sheet_row_interests,
                context.getString(R.string.profile_hobby), profile.getInterests());
        hasPersonal |= setSheetText(sheetView, R.id.sheet_row_music,
                context.getString(R.string.profile_favorite_music), profile.getMusic());
        hasPersonal |= setSheetText(sheetView, R.id.sheet_row_movies,
                context.getString(R.string.profile_favorite_film), profile.getMovies());
        hasPersonal |= setSheetText(sheetView, R.id.sheet_row_books,
                context.getString(R.string.profile_favorite_book), profile.getBooks());
        hasPersonal |= setSheetText(sheetView, R.id.sheet_row_quotes,
                context.getString(R.string.profile_favorite_quote), profile.getQuotes());
        
        View personalTitle = sheetView.findViewById(R.id.sheet_personal_title);
        View personalCard = sheetView.findViewById(R.id.sheet_personal_card);
        if (personalTitle != null) {
            personalTitle.setVisibility(hasPersonal ? View.VISIBLE : View.GONE);
        }
        if (personalCard != null) {
            personalCard.setVisibility(hasPersonal ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * Безопасно установить текст счетчика
     */
    private void setTextSafe(TextView textView, int value) {
        if (textView != null) {
            textView.setText(String.valueOf(value));
        }
    }

    private void setupLinkify(TextView textView) {
        if (textView != null) {
            Linkify.addLinks(textView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            textView.setMovementMethod(SafeLinkMovementMethod.getInstance());
        }
    }

    private boolean setSheetText(View sheetView, int rowId, String label, String value) {
        return setSheetText(sheetView, rowId, label, value, null);
    }
    
    private boolean setSheetText(View sheetView, int rowId, String label, String value,
                                 TextFormatter formatter) {
        View row = sheetView.findViewById(rowId);
        if (row == null) {
            return false;
        }
        
        TextView labelView = row.findViewById(R.id.item_info_row_label);
        TextView valueView = row.findViewById(R.id.item_info_row_value);
        if (labelView != null) {
            labelView.setText(label);
        }
        
        if (value != null && !value.isEmpty() && !"null".equals(value)) {
            if (valueView != null) {
                valueView.setText(formatter != null ? formatter.format(value) : value);
                setupLinkify(valueView);
            }
            row.setVisibility(View.VISIBLE);
            return true;
        }
        
        row.setVisibility(View.GONE);
        return false;
    }

    private boolean setSheetDate(View sheetView, int rowId, String label,
                                 long timestamp, boolean includeTime) {
        View row = sheetView.findViewById(rowId);
        if (row == null) {
            return false;
        }
        
        TextView labelView = row.findViewById(R.id.item_info_row_label);
        TextView valueView = row.findViewById(R.id.item_info_row_value);
        if (labelView != null) {
            labelView.setText(label);
        }
        
        if (timestamp > 0) {
            if (valueView != null) {
                SimpleDateFormat format = includeTime ? dateTimeFormat : dateFormat;
                valueView.setText(format.format(new Date(timestamp * 1000)));
            }
            row.setVisibility(View.VISIBLE);
            return true;
        }
        
        row.setVisibility(View.GONE);
        return false;
    }

    private boolean setSheetSex(View sheetView, int sex) {
        View row = sheetView.findViewById(R.id.sheet_row_sex);
        if (row == null) {
            return false;
        }
        
        TextView labelView = row.findViewById(R.id.item_info_row_label);
        TextView valueView = row.findViewById(R.id.item_info_row_value);
        if (labelView != null) {
            labelView.setText(context.getString(R.string.profile_gender));
        }
        
        if (sex == 1) {
            if (valueView != null) {
                valueView.setText(context.getString(R.string.profile_sex_female));
            }
            row.setVisibility(View.VISIBLE);
            return true;
        } else if (sex == 2) {
            if (valueView != null) {
                valueView.setText(context.getString(R.string.profile_sex_male));
            }
            row.setVisibility(View.VISIBLE);
            return true;
        }
        
        row.setVisibility(View.GONE);
        return false;
    }
    
    /**
     * Получить View аватара для обработки кликов
     */
    public ImageView getAvatarView() {
        return profileAvatarLarge;
    }
    
    /**
     * Интерфейс для форматирования текста
     */
    private interface TextFormatter {
        String format(String value);
    }
}
