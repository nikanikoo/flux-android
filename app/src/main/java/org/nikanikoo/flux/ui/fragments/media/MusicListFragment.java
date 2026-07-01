package org.nikanikoo.flux.ui.fragments.media;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.fragments.BaseFragment;

public class MusicListFragment extends BaseFragment {

    private static final String TAG = "MusicListFragment";

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MusicPagerAdapter pagerAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_main, container, false);

        tabLayout = view.findViewById(R.id.music_tabs);
        viewPager = view.findViewById(R.id.music_view_pager);

        setupViewPager();
        setupTabLayout();

        return view;
    }

    private void setupViewPager() {
        pagerAdapter = new MusicPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(1);
        // Disable swipe on ViewPager2 so it does not intercept touch events
        // inside child fragments (horizontal RecyclerViews, track/playlist clicks).
        // Tab switching is handled by TabLayout only.
        viewPager.setUserInputEnabled(false);
    }

    private void setupTabLayout() {
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Главная");
                    break;
                case 1:
                    tab.setText("Коллекция");
                    break;
            }
        }).attach();
    }

    private static class MusicPagerAdapter extends FragmentStateAdapter {

        public MusicPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new MusicDiscoverFragment();
                case 1:
                    return new MyMusicTabFragment();
                default:
                    return new MusicDiscoverFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}