package org.nikanikoo.flux.ui.activities;

import android.os.Bundle;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.fragments.settings.BalanceFragment;

public class BalanceActivity extends BaseSettingsActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_container);
        
        setupToolbar(getString(R.string.settings_balance));
        
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new BalanceFragment())
                .commit();
        }
    }
}
