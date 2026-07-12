package org.nikanikoo.flux.ui.fragments.profile.edit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;
import com.yalantis.ucrop.UCrop;

import org.json.JSONObject;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.PhotoUploadManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;

import java.io.File;

public class ProfileEditMainFragment extends Fragment {

    private ShapeableImageView avatarImage;
    private MaterialButton changeAvatarButton;
    private TextInputEditText nameEdit;
    private TextInputEditText surnameEdit;
    private TextInputEditText nicknameEdit;
    private TextInputEditText statusEdit;
    private TextInputEditText homeTownEdit;
    private RadioGroup sexRadioGroup;
    private MaterialRadioButton sexMale;
    private MaterialRadioButton sexFemale;
    private AutoCompleteTextView relationAutoComplete;
    private TextInputEditText bdateEdit;
    private RadioGroup bdateVisibilityRadioGroup;
    private MaterialRadioButton bdateVisibilityFull;
    private MaterialRadioButton bdateVisibilityDayMonth;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    startCrop(uri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        uploadAvatar(resultUri);
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR && result.getData() != null) {
                    Throwable cropError = UCrop.getError(result.getData());
                    if (cropError != null) {
                        Toast.makeText(requireContext(), cropError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final String[] relationOptions = {
            "Не выбрано", // 0
            "Не женат/Не замужем", // 1
            "Встречаюсь", // 2
            "Помолвлен(а)", // 3
            "Женат/Замужем", // 4
            "В гражданском браке", // 5
            "Влюблен(а)", // 6
            "Всё сложно", // 7
            "В активном поиске" // 8
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_edit_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupClickListeners();
        loadProfileData();
    }

    private void initViews(View view) {
        avatarImage = view.findViewById(R.id.avatar_image);
        changeAvatarButton = view.findViewById(R.id.change_avatar_button);
        nameEdit = view.findViewById(R.id.name_edit);
        surnameEdit = view.findViewById(R.id.surname_edit);
        nicknameEdit = view.findViewById(R.id.nickname_edit);
        statusEdit = view.findViewById(R.id.status_edit);
        homeTownEdit = view.findViewById(R.id.home_town_edit);
        sexRadioGroup = view.findViewById(R.id.sex_radio_group);
        sexMale = view.findViewById(R.id.sex_male);
        sexFemale = view.findViewById(R.id.sex_female);
        relationAutoComplete = view.findViewById(R.id.relation_auto_complete);
        bdateEdit = view.findViewById(R.id.bdate_edit);
        bdateVisibilityRadioGroup = view.findViewById(R.id.bdate_visibility_radio_group);
        bdateVisibilityFull = view.findViewById(R.id.bdate_visibility_full);
        bdateVisibilityDayMonth = view.findViewById(R.id.bdate_visibility_day_month);

        ArrayAdapter<String> relationAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, relationOptions);
        relationAutoComplete.setAdapter(relationAdapter);
    }

    private void setupClickListeners() {
        bdateEdit.setOnClickListener(v -> {
            String currentBdate = bdateEdit.getText().toString();
            int year = 1990, month = 0, day = 1;
            
            if (!currentBdate.isEmpty()) {
                String[] parts = currentBdate.split("\\.");
                if (parts.length >= 2) {
                    day = Integer.parseInt(parts[0]);
                    month = Integer.parseInt(parts[1]) - 1;
                    if (parts.length == 3) {
                        year = Integer.parseInt(parts[2]);
                    }
                }
            }
            
            android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(requireContext(),
                    (view, year1, month1, dayOfMonth) -> {
                        String date = String.format(java.util.Locale.US, "%02d.%02d.%04d", dayOfMonth, month1 + 1, year1);
                        bdateEdit.setText(date);
                    }, year, month, day);
            datePickerDialog.show();
        });

        relationAutoComplete.setOnClickListener(v -> relationAutoComplete.showDropDown());

        changeAvatarButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void startCrop(@NonNull Uri uri) {
        String destinationFileName = "cropped_avatar_" + System.currentTimeMillis() + ".jpg";
        File destinationFile = new File(requireContext().getCacheDir(), destinationFileName);
        
        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(90);
        options.setHideBottomControls(false);
        options.setFreeStyleCropEnabled(false);
        
        Intent intent = UCrop.of(uri, Uri.fromFile(destinationFile))
                .withAspectRatio(1, 1)
                .withMaxResultSize(1000, 1000)
                .withOptions(options)
                .getIntent(requireContext());
        
        cropImageLauncher.launch(intent);
    }

    private void uploadAvatar(@NonNull Uri uri) {
        Toast.makeText(requireContext(), "Загрузка аватарки...", Toast.LENGTH_SHORT).show();
        PhotoUploadManager.getInstance(requireContext()).uploadOwnerPhoto(uri, new PhotoUploadManager.PhotoUploadCallback() {
            @Override
            public void onSuccess(String attachment) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Аватарка успешно изменена", Toast.LENGTH_SHORT).show();
                        Picasso.get().invalidate(uri);
                        Picasso.get().load(uri).placeholder(R.drawable.ic_account_circle).into(avatarImage);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    public void saveProfile(SaveProfileListener listener) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        
        String firstName = nameEdit.getText().toString().trim();
        String lastName = surnameEdit.getText().toString().trim();
        String screenName = nicknameEdit.getText().toString().trim();
        String status = statusEdit.getText().toString().trim();
        String homeTown = homeTownEdit.getText().toString().trim();
        String bdate = bdateEdit.getText().toString().trim();

        params.put("first_name", firstName);
        params.put("last_name", lastName);
        params.put("screen_name", screenName);
        params.put("status", status);
        params.put("home_town", homeTown);
        params.put("bdate", bdate);
        
        int sex = 0;
        if (sexMale.isChecked()) sex = 2;
        else if (sexFemale.isChecked()) sex = 1;
        params.put("sex", String.valueOf(sex));
        
        String selectedRelation = relationAutoComplete.getText().toString();
        int relationIndex = 0;
        for (int i = 0; i < relationOptions.length; i++) {
            if (relationOptions[i].equals(selectedRelation)) {
                relationIndex = i;
                break;
            }
        }
        params.put("relation", String.valueOf(relationIndex));
        
        int bdateVisibility = 1; // Показывает дату рождения
        if (bdateVisibilityDayMonth.isChecked()) bdateVisibility = 2; // Показывает только день и месяц
        params.put("bdate_visibility", String.valueOf(bdateVisibility));

        org.nikanikoo.flux.data.managers.ProfileManager.getInstance(requireContext())
                .saveProfileInfo(params, new org.nikanikoo.flux.data.managers.ProfileManager.SaveProfileCallback() {
            @Override
            public void onSuccess() {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), R.string.profile_edit_success, Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onSaveComplete();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    public interface SaveProfileListener {
        void onSaveComplete();
    }

    private void loadProfileData() {
        OpenVKApi.getInstance(requireContext()).callMethod("account.getProfileInfo", null, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) return;
                try {
                    JSONObject responseObj = response.getJSONObject("response");

                    String firstName = responseObj.isNull("first_name") ? "" : responseObj.optString("first_name");
                    String lastName = responseObj.isNull("last_name") ? "" : responseObj.optString("last_name");
                    String photoUrl = responseObj.optString("photo_200");
                    String screenName = responseObj.isNull("screen_name") ? (responseObj.isNull("nickname") ? "" : responseObj.optString("nickname")) : responseObj.optString("screen_name");
                    String status = responseObj.isNull("status") ? "" : responseObj.optString("status");
                    String homeTown = responseObj.isNull("home_town") ? "" : responseObj.optString("home_town");
                    String bdate = responseObj.isNull("bdate") ? "" : responseObj.optString("bdate");
                    int sex = responseObj.optInt("sex", 0);
                    int relation = responseObj.optInt("relation", 0);
                    int bdateVisibility = responseObj.optInt("bdate_visibility", 0);

                    nameEdit.setText(firstName);
                    surnameEdit.setText(lastName);
                    nicknameEdit.setText(screenName);
                    statusEdit.setText(status);
                    homeTownEdit.setText(homeTown);
                    bdateEdit.setText(bdate);

                    if (sex == 1) {
                        sexFemale.setChecked(true);
                    } else if (sex == 2) {
                        sexMale.setChecked(true);
                    }

                    if (relation >= 0 && relation < relationOptions.length) {
                        relationAutoComplete.setText(relationOptions[relation], false);
                    }

                    if (bdateVisibility == 1) {
                        bdateVisibilityDayMonth.setChecked(true);
                    } else {
                        bdateVisibilityFull.setChecked(true);
                    }

                    if (!photoUrl.isEmpty()) {
                        Picasso.get().load(photoUrl).placeholder(R.drawable.ic_account_circle).into(avatarImage);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(requireContext(), "Ошибка парсинга данных", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Ошибка загрузки: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}