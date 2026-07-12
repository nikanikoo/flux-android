package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.fragments.profile.edit.ProfileEditAdditionalFragment;
import org.nikanikoo.flux.ui.fragments.profile.edit.ProfileEditContactsFragment;
import org.nikanikoo.flux.ui.fragments.profile.edit.ProfileEditInterestsFragment;
import org.nikanikoo.flux.ui.fragments.profile.edit.ProfileEditMainFragment;
import org.nikanikoo.flux.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class ProfileEditActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ProfileEditPagerAdapter pagerAdapter;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabSave;

    public static void start(Context context) {
        Intent intent = new Intent(context, ProfileEditActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applyThemeToActivity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        initViews();
        setupToolbar();
        setupViewPager();
        setupTabLayout();
        setupFab();
    }

    private void setupFab() {
        fabSave.setOnClickListener(v -> {
            Fragment currentFragment = pagerAdapter.createFragment(viewPager.getCurrentItem());
            if (currentFragment instanceof ProfileEditMainFragment) {
                ((ProfileEditMainFragment) currentFragment).saveProfile(() -> {
                    setResult(RESULT_OK);
                    finish();
                });
            } else {
                Toast.makeText(this, "Сохранение доступно только на основной вкладке", Toast.LENGTH_SHORT).show();
            }
        });
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    fabSave.show();
                } else {
                    fabSave.hide();
                }
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        fabSave = findViewById(R.id.fab_save);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.profile_edit_title);
        }
    }

    private void setupViewPager() {
        pagerAdapter = new ProfileEditPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(pagerAdapter.getItemCount());
    }

    private void setupTabLayout() {
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.profile_edit_main);
                    break;
                case 1:
                    tab.setText(R.string.profile_edit_contacts);
                    break;
                case 2:
                    tab.setText(R.string.profile_edit_interests);
                    break;
                case 3:
                    tab.setText(R.string.profile_edit_additional);
                    break;
            }
        }).attach();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class ProfileEditPagerAdapter extends FragmentStateAdapter {

        private final List<Fragment> fragments;

        public ProfileEditPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
            fragments = new ArrayList<>();
            fragments.add(new ProfileEditMainFragment());
            fragments.add(new ProfileEditContactsFragment());
            fragments.add(new ProfileEditInterestsFragment());
            fragments.add(new ProfileEditAdditionalFragment());
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments.get(position);
        }

        @Override
        public int getItemCount() {
            return fragments.size();
        }
    }
}