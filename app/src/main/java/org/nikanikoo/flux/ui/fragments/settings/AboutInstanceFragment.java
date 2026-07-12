package org.nikanikoo.flux.ui.fragments.settings;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;

import java.util.HashMap;
import java.util.Map;

public class AboutInstanceFragment extends Fragment {

    private static final String TAG = "AboutInstanceFragment";
    
    private ProgressBar progressBar;
    private View contentLayout;
    private TextView instanceName;
    private TextView instanceDescription;
    private TextView instanceUrl;
    private TextView instanceConnectionType;
    private TextView instanceVersion;
    
    // Statistics
    private TextView statsUsers;
    private TextView statsOnline;
    private TextView statsActive;
    private TextView statsGroups;
    
    // Info items
    private View itemRules;
    private View itemPrivacy;
    
    // Sections
    private LinearLayout adminsSection;
    private LinearLayout adminsContainer;
    private LinearLayout linksSection;
    private LinearLayout linksContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about_instance, container, false);
        
        initViews(view);
        loadInstanceInfo();
        
        return view;
    }
    
    private void initViews(View view) {
        progressBar = view.findViewById(R.id.progress_bar);
        contentLayout = view.findViewById(R.id.content_layout);
        instanceName = view.findViewById(R.id.instance_name);
        instanceDescription = view.findViewById(R.id.instance_description);
        instanceUrl = view.findViewById(R.id.instance_url);
        instanceConnectionType = view.findViewById(R.id.instance_connection_type);
        instanceVersion = view.findViewById(R.id.instance_version);
        
        statsUsers = view.findViewById(R.id.stats_users);
        statsOnline = view.findViewById(R.id.stats_online);
        statsActive = view.findViewById(R.id.stats_active);
        statsGroups = view.findViewById(R.id.stats_groups);
        
        itemRules = view.findViewById(R.id.item_rules);
        itemPrivacy = view.findViewById(R.id.item_privacy);
        
        adminsSection = view.findViewById(R.id.admins_section);
        adminsContainer = view.findViewById(R.id.admins_container);
        linksSection = view.findViewById(R.id.links_section);
        linksContainer = view.findViewById(R.id.links_container);
    }
    
    private void loadInstanceInfo() {
        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        
        Map<String, String> params = new HashMap<>();
        params.put("fields", "statistics,links,administrators");
        params.put("admin_fields", "photo_100");
        
        OpenVKApi.getInstance(requireContext()).callMethod("ovk.aboutInstance", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (getActivity() == null) return;
                
                Log.d(TAG, "API Response: " + response.toString());
                
                getActivity().runOnUiThread(() -> {
                    try {
                        // Check if response has the expected structure
                        if (!response.has("response")) {
                            Log.e(TAG, "Response missing 'response' field: " + response.toString());
                            showError(getString(R.string.request_server_error));
                            return;
                        }
                        
                        JSONObject data = response.getJSONObject("response");
                        Log.d(TAG, "Response data: " + data.toString());
                        
                        // Instance info
                        String name = data.optString("name", "OpenVK");
                        String description = data.optString("description", "");
                        String version = data.optString("openvk_version", "");
                        String url = OpenVKApi.getInstance(requireContext()).getBaseUrl();
                        
                        instanceName.setText(name);
                        if (!description.isEmpty()) {
                            instanceDescription.setText(description);
                            instanceDescription.setVisibility(View.VISIBLE);
                        } else {
                            instanceDescription.setVisibility(View.GONE);
                        }
                        
                        String displayUrl = url.replace("https://", "").replace("http://", "");
                        instanceUrl.setText(displayUrl);
                        
                        // Connection type
                        String connectionType = url.startsWith("https://") ? getString(R.string.about_instance_https) : getString(R.string.about_instance_http);
                        instanceConnectionType.setText(connectionType);
                        
                        if (!version.isEmpty()) {
                            instanceVersion.setText(getString(R.string.about_instance_version) + version);
                            instanceVersion.setVisibility(View.VISIBLE);
                        }
                        
                        // Statistics
                        if (data.has("statistics")) {
                            JSONObject stats = data.getJSONObject("statistics");
                            Log.d(TAG, "Statistics: " + stats.toString());
                            statsUsers.setText(formatNumber(stats.optInt("users_count", 0)));
                            statsOnline.setText(formatNumber(stats.optInt("online_users_count", 0)));
                            statsActive.setText(formatNumber(stats.optInt("active_users_count", 0)));
                            statsGroups.setText(formatNumber(stats.optInt("groups_count", 0)));
                        } else {
                            Log.w(TAG, "No statistics in response");
                            statsUsers.setText("0");
                            statsOnline.setText("0");
                            statsActive.setText("0");
                            statsGroups.setText("0");
                        }
                        
                        // Administrators
                        if (data.has("administrators")) {
                            JSONObject adminsObj = data.getJSONObject("administrators");
                            if (adminsObj.has("items")) {
                                JSONArray admins = adminsObj.getJSONArray("items");
                                Log.d(TAG, "Admins count: " + admins.length());
                                if (admins.length() > 0) {
                                    displayAdmins(admins);
                                }
                            }
                        }
                        
                        // Links
                        if (data.has("links")) {
                            JSONObject linksObj = data.getJSONObject("links");
                            if (linksObj.has("items")) {
                                JSONArray links = linksObj.getJSONArray("items");
                                Log.d(TAG, "Links count: " + links.length());
                                displayLinks(links);
                            } else {
                                Log.w(TAG, "No items in links object");
                            }
                        } else {
                            Log.w(TAG, "No links in response");
                        }
                        
                        // Setup info items
                        setupInfoItems();
                        
                        progressBar.setVisibility(View.GONE);
                        contentLayout.setVisibility(View.VISIBLE);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response", e);
                        showError(getString(R.string.about_instance_error) + e.getMessage());
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                
                Log.e(TAG, "API Error: " + error);
                
                getActivity().runOnUiThread(() -> {
                    showError(getString(R.string.about_instance_error) + error);
                });
            }
        });
    }
    
    private void displayLinks(JSONArray links) {
        linksContainer.removeAllViews();
        
        try {
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.getJSONObject(i);
                String title = link.optString("name", "");
                String url = link.optString("url", "");
                
                if (!title.isEmpty() && !url.isEmpty()) {
                    View linkView = createLinkView(title, url);
                    linksContainer.addView(linkView);
                }
            }
            
            if (linksContainer.getChildCount() > 0) {
                linksSection.setVisibility(View.VISIBLE);
            } else {
                linksSection.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            linksSection.setVisibility(View.GONE);
        }
    }
    
    private void displayAdmins(JSONArray admins) {
        adminsContainer.removeAllViews();
        
        try {
            for (int i = 0; i < admins.length(); i++) {
                JSONObject admin = admins.getJSONObject(i);
                String firstName = admin.optString("first_name", "");
                String lastName = admin.optString("last_name", "");
                String photoUrl = admin.optString("photo_100", "");
                int userId = admin.optInt("id", 0);
                
                if (!firstName.isEmpty() && userId > 0) {
                    View adminView = createAdminView(firstName + " " + lastName, userId, photoUrl);
                    adminsContainer.addView(adminView);
                }
            }
            
            if (adminsContainer.getChildCount() > 0) {
                adminsSection.setVisibility(View.VISIBLE);
            } else {
                adminsSection.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            adminsSection.setVisibility(View.GONE);
        }
    }
    
    private View createAdminView(String name, int userId, String photoUrl) {
        View view = getLayoutInflater().inflate(R.layout.item_admin, adminsContainer, false);
        
        ImageView avatarView = view.findViewById(R.id.admin_avatar);
        TextView nameView = view.findViewById(R.id.admin_name);
        
        nameView.setText(name);
        
        // Load avatar
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Picasso.get()
                .load(photoUrl)
                .placeholder(R.drawable.ic_account_circle)
                .error(R.drawable.ic_account_circle)
                .transform(new org.nikanikoo.flux.ui.custom.CircularImageTransformation())
                .into(avatarView);
        } else {
            avatarView.setImageResource(R.drawable.ic_account_circle);
        }
        
        view.setOnClickListener(v -> openUserProfile(userId));
        
        return view;
    }
    
    private void openUserProfile(int userId) {
        // Navigate to user profile
        // TODO: Implement profile navigation when ProfileFragment is available
        Toast.makeText(requireContext(), getString(R.string.about_instance_error) + userId, Toast.LENGTH_SHORT).show();
    }
    
    private void setupInfoItems() {
        String baseUrl = OpenVKApi.getInstance(requireContext()).getBaseUrl();
        
        itemRules.setOnClickListener(v -> {
            String rulesUrl = baseUrl + "/terms";
            openUrl(rulesUrl);
        });
        
        itemPrivacy.setOnClickListener(v -> {
            String privacyUrl = baseUrl + "/privacy";
            openUrl(privacyUrl);
        });
    }
    
    private void openUrl(String url) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setData(android.net.Uri.parse(url));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.link_error), Toast.LENGTH_SHORT).show();
        }
    }
    
    private View createLinkView(String title, String url) {
        View view = getLayoutInflater().inflate(R.layout.item_instance_link, linksContainer, false);
        
        TextView titleView = view.findViewById(R.id.link_title);
        TextView urlView = view.findViewById(R.id.link_url);
        
        titleView.setText(title);
        urlView.setText(url);
        
        view.setOnClickListener(v -> openUrl(url));
        
        return view;
    }
    
    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        } else {
            return String.valueOf(number);
        }
    }
    
    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        requireActivity().onBackPressed();
    }
}
