package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.adapters.media.SelectedImagesAdapter;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class CreatePostActivity extends AppCompatActivity {

    private TextInputEditText editPostContent;
    private Button btnPublish;
    private View btnAddPhoto;
    private PostsManager postsManager;
    private ProfileManager profileManager;
    private TextView authorName;
    private ImageView authorAvatar;
    private RecyclerView recyclerSelectedImages;
    private CardView cardImagePreview;
    private CheckBox checkboxGroupPost;
    private CheckBox checkboxSignPost;
    private CheckBox checkboxNsfw;

    private List<Uri> selectedImages;
    private SelectedImagesAdapter imagesAdapter;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    private int targetOwnerId = 0; // (0 = своя стена, >0 = пользователь, <0 = группа)
    private boolean isPostingToGroup = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = LocaleManager.getInstance(newBase);
        Context context = localeManager.updateContext(newBase);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applyThemeToActivity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);
        
        ThemeManager.applySystemBarsAppearance(this);

        postsManager = PostsManager.getInstance(this);
        profileManager = ProfileManager.getInstance(this);
        selectedImages = new ArrayList<>();

        targetOwnerId = getIntent().getIntExtra("owner_id", 0);
        Logger.d("CreatePostActivity:", "targetOwnerId = " + targetOwnerId);
        
        setupImagePicker();
        initViews();
        setupToolbar();
        setupTextWatcher();
        setupClickListeners();
        setupImagesRecyclerView();
        loadUserProfile();
    }

    private void initViews() {
        editPostContent = findViewById(R.id.edit_post_content);
        btnPublish = findViewById(R.id.btn_publish);
        btnAddPhoto = findViewById(R.id.btn_add_photo);
        authorName = findViewById(R.id.author_name);
        authorAvatar = findViewById(R.id.author_avatar);
        recyclerSelectedImages = findViewById(R.id.recycler_selected_images);
        cardImagePreview = findViewById(R.id.card_image_preview);
        checkboxGroupPost = findViewById(R.id.checkbox_group_post);
        checkboxSignPost = findViewById(R.id.checkbox_sign_post);
        checkboxNsfw = findViewById(R.id.checkbox_nsfw);

        if (authorAvatar != null) {
            authorAvatar.setImageResource(R.drawable.camera_200);
        }

        if (editPostContent == null || btnPublish == null) {
            Logger.e("CreatePostActivity:", "Required views not found in layout!");
        }

        isPostingToGroup = targetOwnerId < 0;
        Logger.d("CreatePostActivity", "isPostingToGroup: " + isPostingToGroup + ", targetOwnerId: " + targetOwnerId);
        
        if (checkboxGroupPost != null) {
            checkboxGroupPost.setVisibility(isPostingToGroup ? View.VISIBLE : View.GONE);
        }
        updateSignCheckboxVisibility();
    }

    private void updateSignCheckboxVisibility() {
        if (checkboxSignPost != null) {
            if (checkboxGroupPost != null && checkboxGroupPost.isChecked()) {
                checkboxSignPost.setVisibility(View.VISIBLE);
            } else {
                checkboxSignPost.setVisibility(View.GONE);
            }
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupTextWatcher() {
        editPostContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePublishButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupClickListeners() {
        btnPublish.setOnClickListener(v -> {
            String content = editPostContent.getText().toString();
            Logger.d("CreatePost", "Content before send: [" + content + "]");
            Logger.d("CreatePost", "Contains newline: " + content.contains("\n"));
            if (!content.isEmpty() || !selectedImages.isEmpty()) {
                publishPost(content);
            }
        });

        if (btnAddPhoto != null) {
            btnAddPhoto.setOnClickListener(v -> {
                openImagePicker();
            });
        }

        if (checkboxGroupPost != null) {
            checkboxGroupPost.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateSignCheckboxVisibility();
            });
        }
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                Uri imageUri = data.getClipData().getItemAt(i).getUri();
                                selectedImages.add(imageUri);
                            }
                        } else if (data.getData() != null) {
                            selectedImages.add(data.getData());
                        }
                        
                        updateImagesDisplay();
                        updatePublishButton();
                    }
                }
        );
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.choose_image)));
    }

    private void setupImagesRecyclerView() {
        if (recyclerSelectedImages != null) {
            recyclerSelectedImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            imagesAdapter = new SelectedImagesAdapter(selectedImages, this::removeImage);
            recyclerSelectedImages.setAdapter(imagesAdapter);
        }
    }

    private void updateImagesDisplay() {
        if (selectedImages.isEmpty()) {
            if (cardImagePreview != null) {
                cardImagePreview.setVisibility(View.GONE);
            }
            if (recyclerSelectedImages != null) {
                recyclerSelectedImages.setVisibility(View.GONE);
            }
        } else {
            if (recyclerSelectedImages != null) {
                recyclerSelectedImages.setVisibility(View.VISIBLE);
                imagesAdapter.notifyDataSetChanged();
            }
        }
    }

    private void removeImage(int position) {
        selectedImages.remove(position);
        updateImagesDisplay();
        updatePublishButton();
    }

    private void updatePublishButton() {
        if (editPostContent == null || btnPublish == null) return;
        
        String content = editPostContent.getText().toString();
        btnPublish.setEnabled(!content.isEmpty() || !selectedImages.isEmpty());
    }

    private void publishPost(String content) {
        btnPublish.setEnabled(false);
        btnPublish.setText(getString(R.string.post_publishing));

        boolean fromGroup = checkboxGroupPost != null && checkboxGroupPost.isChecked();
        boolean signed = checkboxSignPost != null && checkboxSignPost.isChecked();
        boolean explicit = checkboxNsfw != null && checkboxNsfw.isChecked();

        Logger.d("CreatePost", "fromGroup: " + fromGroup + ", signed: " + signed + ", explicit: " + explicit);

        if (!selectedImages.isEmpty()) {
            postsManager.createPostWithImages(targetOwnerId, content, selectedImages, fromGroup, signed, explicit, new PostsManager.CreatePostCallback() {
                @Override
                public void onSuccess(int postId) {
                    runOnUiThread(() -> {
                        Toast.makeText(CreatePostActivity.this, getString(R.string.post_published), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(CreatePostActivity.this, getString(R.string.post_publish_error) + error, Toast.LENGTH_LONG).show();
                        btnPublish.setEnabled(true);
                        btnPublish.setText(getString(R.string.create_post_publish));
                    });
                }
            });
        } else {
            postsManager.createPost(targetOwnerId, content, fromGroup, signed, explicit, new PostsManager.CreatePostCallback() {
                @Override
                public void onSuccess(int postId) {
                    runOnUiThread(() -> {
                        Toast.makeText(CreatePostActivity.this, getString(R.string.post_published), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(CreatePostActivity.this, getString(R.string.post_publish_error) + error, Toast.LENGTH_LONG).show();
                        btnPublish.setEnabled(true);
                        btnPublish.setText(getString(R.string.create_post_publish));
                    });
                }
            });
        }
    }

    private void loadUserProfile() {
        Logger.d("CreatePostActivity", "Загрузка профиля пользователя...");
        profileManager.loadProfile(true, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                Logger.d("CreatePostActivity", "Профиль загрузился!");
                Logger.d("CreatePostActivity", "User name: " + profile.getFirstName() + " " + profile.getLastName());
                Logger.d("CreatePostActivity", "Photo50: " + profile.getPhoto50());
                Logger.d("CreatePostActivity", "Photo200: " + profile.getPhoto200());
                
                runOnUiThread(() -> {
                    if (authorName != null) {
                        authorName.setText(profile.getFirstName() + " " + profile.getLastName());
                    }
                    
                    if (authorAvatar != null) {
                        if (profile.getPhoto50() != null && !profile.getPhoto50().isEmpty()) {
                            Logger.d("CreatePostActivity", "Загрузка аватарки: " + profile.getPhoto50());
                            Picasso.get()
                                    .load(profile.getPhoto50())
                                    .placeholder(R.drawable.camera_200)
                                    .error(R.drawable.camera_200)
                                    .into(authorAvatar);
                        } else {
                            Logger.d("CreatePostActivity", "photo50 нема");
                            authorAvatar.setImageResource(R.drawable.camera_200);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.d("CreatePostActivity", "Ошибка загрузки профиля: " + error);
                runOnUiThread(() -> {
                    if (authorAvatar != null) {
                        authorAvatar.setImageResource(R.drawable.camera_200);
                    }
                    if (authorName != null) {
                        authorName.setText(getString(R.string.empty_loading));
                    }
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}