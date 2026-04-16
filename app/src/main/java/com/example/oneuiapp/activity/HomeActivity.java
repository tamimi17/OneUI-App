package com.example.oneuiapp.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fragment.HomeFragment;

import dev.oneuiproject.oneui.layout.ToolbarLayout;

/**
 * HomeActivity - شاشة الصفحة الرئيسية المستقلة
 * 
 * تعرض HomeFragment في شاشة منفصلة مع شريط أدوات علوي غير قابل للتوسع
 * تم إنشاؤها لحل مشكلة تعارض CollapsingToolbar المزدوج
 * مصممة بنفس أسلوب SettingsActivity
 */
public class HomeActivity extends BaseActivity {

    private ToolbarLayout mToolbarLayout;
    private HomeFragment mHomeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initToolbar();
        
        if (savedInstanceState == null) {
            mHomeFragment = new HomeFragment();
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.home_container, mHomeFragment)
                .commit();
        } else {
            mHomeFragment = (HomeFragment) getSupportFragmentManager()
                .findFragmentById(R.id.home_container);
        }
    }

    private void initToolbar() {
        mToolbarLayout = findViewById(R.id.toolbar_layout);
        if (mToolbarLayout != null) {
            mToolbarLayout.setNavigationButtonTooltip(getString(R.string.navigate_up));
            mToolbarLayout.setNavigationButtonOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mHomeFragment != null && mHomeFragment.handleBackPressed()) {
            return;
        }
        
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTaskRoot()) {
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }
}
