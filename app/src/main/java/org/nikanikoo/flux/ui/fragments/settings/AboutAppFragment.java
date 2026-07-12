package org.nikanikoo.flux.ui.fragments.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.utils.BuildInfo;

public class AboutAppFragment extends Fragment {

    private View itemGithub;
    private TextView versionText;
    private TextView commitText;
    private TextView updateStatusText;
    private com.google.android.material.button.MaterialButton btnCheckUpdate;
    private org.nikanikoo.flux.utils.UpdateChecker.UpdateInfo pendingUpdateInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about_app, container, false);

        initViews(view);
        setupClickListeners();
        updateVersionInfo();
        checkUpdates();

        return view;
    }

    private void initViews(View view) {
        itemGithub = view.findViewById(R.id.item_github);
        versionText = view.findViewById(R.id.version_text);
        commitText = view.findViewById(R.id.commit_text);
        updateStatusText = view.findViewById(R.id.update_status_text);
        btnCheckUpdate = view.findViewById(R.id.btn_check_update);
    }

    private void updateVersionInfo() {
        if (getActivity() != null) {
            String version = BuildInfo.getAppVersion(getActivity());
            String commitHash = BuildInfo.getCommitHash();
            
            if (versionText != null) {
                versionText.setText(getString(R.string.about_app_version) + version);
            }
            
            if (commitText != null) {
                commitText.setText(getString(R.string.about_app_commit) + commitHash);
            }
        }
    }

    private void checkUpdates() {
        if (updateStatusText != null) {
            updateStatusText.setVisibility(View.VISIBLE);
            updateStatusText.setText(R.string.update_checking);
        }
        pendingUpdateInfo = null;
        
        org.nikanikoo.flux.utils.UpdateChecker.checkUpdateStatus((info, isNewer, error) -> {
            if (getActivity() == null || !isAdded()) return;
            
            if (info != null) {
                if (isNewer) {
                    pendingUpdateInfo = info;
                    if (updateStatusText != null) {
                        updateStatusText.setText(getString(R.string.update_available_short, info.versionName));
                        updateStatusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                    }
                    if (btnCheckUpdate != null) {
                        btnCheckUpdate.setText(R.string.update_download);
                        btnCheckUpdate.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (updateStatusText != null) {
                        updateStatusText.setText(R.string.update_latest_installed);
                        updateStatusText.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
                    }
                    if (btnCheckUpdate != null) {
                        btnCheckUpdate.setText(R.string.update_check_btn);
                        btnCheckUpdate.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                if (updateStatusText != null) {
                    updateStatusText.setText(error != null ? error : getString(R.string.update_check_failed));
                    updateStatusText.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                }
                if (btnCheckUpdate != null) {
                    btnCheckUpdate.setText(R.string.update_check_btn);
                    btnCheckUpdate.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void setupClickListeners() {
        itemGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(org.nikanikoo.flux.Constants.Links.GITHUB_REPO));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), getString(R.string.link_error), Toast.LENGTH_SHORT).show();
            }
        });
        
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> {
                if (pendingUpdateInfo != null && getActivity() != null) {
                    org.nikanikoo.flux.utils.UpdateChecker.showUpdateDialog(getActivity(), pendingUpdateInfo);
                } else {
                    checkUpdates();
                }
            });
        }
    }
}
