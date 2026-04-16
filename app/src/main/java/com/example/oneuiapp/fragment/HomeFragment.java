package com.example.oneuiapp.fragment;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import dev.oneuiproject.oneui.utils.internal.ToolbarLayoutUtils;

import com.example.oneuiapp.R;
import com.example.oneuiapp.utils.FontHelper;

public class HomeFragment extends Fragment {

    private boolean mEnableBackToHeader;
    private AppBarLayout mAppBarLayout;
    private CollapsingToolbarLayout mCollapsingToolbar;
    private Toolbar mToolbar;
    private View mSwipeUpContainer;
    private ViewGroup mBottomContainer;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    // ★ الإصلاح: تعريف المستمع كمتغير على مستوى الكلاس لتجنب تكراره وتسرب الذاكرة ★
    private AppBarOffsetListener mOffsetListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupToolbar();
        setupAppBar(getResources().getConfiguration());

        TextView versionText = view.findViewById(R.id.bottom_app_version);
        if (versionText != null) {
            try {
                String versionName = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                versionText.setText("Version " + versionName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        applyCustomFontToCollapsingToolbar();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                applyCustomFontToCollapsingToolbar();
            }
        }, 100);
    }

    private void initViews(View view) {
        mAppBarLayout = view.findViewById(R.id.app_bar);
        mCollapsingToolbar = view.findViewById(R.id.collapsing_toolbar);
        mToolbar = view.findViewById(R.id.toolbar);
        mSwipeUpContainer = view.findViewById(R.id.swipe_up_container);
        mBottomContainer = view.findViewById(R.id.bottom_container);

        // ★ تهيئة المستمع مرة واحدة فقط هنا ★
        mOffsetListener = new AppBarOffsetListener();
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) requireActivity()).setSupportActionBar(mToolbar);
            if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
                ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }
    }

    private void setupAppBar(Configuration config) {
        ToolbarLayoutUtils.hideStatusBarForLandscape(requireActivity(), config.orientation);
        ToolbarLayoutUtils.updateListBothSideMargin(requireActivity(), mBottomContainer);

        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode()) {
            mAppBarLayout.seslSetCustomHeightProportion(true, 0.5f);
            mEnableBackToHeader = true;

            // ★ الإصلاح: إزالة المستمع أولاً ثم إضافته لتجنب تكراره في كل تدوير ★
            if (mOffsetListener != null) {
                mAppBarLayout.removeOnOffsetChangedListener(mOffsetListener);
                mAppBarLayout.addOnOffsetChangedListener(mOffsetListener);
            }

            mAppBarLayout.setExpanded(true, false);

            if (mSwipeUpContainer != null) {
                mSwipeUpContainer.setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams lp = mSwipeUpContainer.getLayoutParams();
                lp.height = getResources().getDisplayMetrics().heightPixels / 2;
            }
        } else {
            // ★ الإصلاح الجوهري: إزالة المستمع في الوضع الأفقي حتى لا يحسب Alpha
            // بقيم غير صالحة ويُخفي المحتوى بعد ضبط setAlpha(1f) ★
            if (mOffsetListener != null) {
                mAppBarLayout.removeOnOffsetChangedListener(mOffsetListener);
            }

            mAppBarLayout.setExpanded(false, false);
            mEnableBackToHeader = false;
            mAppBarLayout.seslSetCustomHeightProportion(true, 0);

            if (mBottomContainer != null) {
                mBottomContainer.setAlpha(1f);
            }
            if (mSwipeUpContainer != null) {
                mSwipeUpContainer.setVisibility(View.GONE);
            }
        }
    }

    private void applyCustomFontToCollapsingToolbar() {
        if (mCollapsingToolbar != null) {
            FontHelper.applyFontToCollapsingToolbar(mCollapsingToolbar);
        }
    }

    public boolean handleBackPressed() {
        if (mEnableBackToHeader && mAppBarLayout != null && mAppBarLayout.seslIsCollapsed()) {
            mAppBarLayout.setExpanded(true, true);
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        applyCustomFontToCollapsingToolbar();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && mCollapsingToolbar != null) {
                    applyCustomFontToCollapsingToolbar();
                }
            }
        }, 150);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupAppBar(newConfig);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    applyCustomFontToCollapsingToolbar();
                }
            }
        }, 100);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // ★ الإصلاح: إزالة المستمع بشكل نظيف لتجنب تسرب الذاكرة ★
        if (mAppBarLayout != null && mOffsetListener != null) {
            mAppBarLayout.removeOnOffsetChangedListener(mOffsetListener);
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    private boolean isInMultiWindowMode() {
        return Build.VERSION.SDK_INT >= 24 && requireActivity().isInMultiWindowMode();
    }

    private class AppBarOffsetListener implements AppBarLayout.OnOffsetChangedListener {
        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            final int totalScrollRange = appBarLayout.getTotalScrollRange();
            final int abs = Math.abs(verticalOffset);

            if (mSwipeUpContainer != null) {
                if (abs >= totalScrollRange / 2) {
                    mSwipeUpContainer.setAlpha(0f);
                } else if (abs == 0) {
                    mSwipeUpContainer.setAlpha(1f);
                } else {
                    float offsetAlpha = (appBarLayout.getY() / totalScrollRange);
                    float arrowAlpha = 1 - (offsetAlpha * -3);

                    if (arrowAlpha < 0) {
                        arrowAlpha = 0;
                    } else if (arrowAlpha > 1) {
                        arrowAlpha = 1;
                    }
                    mSwipeUpContainer.setAlpha(arrowAlpha);
                }
            }

            if (mBottomContainer != null) {
                final float alphaRange = mCollapsingToolbar.getHeight() * 0.143f;
                final float layoutPosition = Math.abs(appBarLayout.getTop());
                float bottomAlpha = (150.0f / alphaRange) * (layoutPosition - (mCollapsingToolbar.getHeight() * 0.35f));

                if (bottomAlpha < 0) {
                    bottomAlpha = 0;
                } else if (bottomAlpha >= 255) {
                    bottomAlpha = 255;
                }

                mBottomContainer.setAlpha(bottomAlpha / 255);
            }
        }
    }
}
