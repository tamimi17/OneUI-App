package com.example.oneuiapp.fontlist.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fragment.LocalFontListFragment;
import com.example.oneuiapp.fragment.SystemFontListFragment;
import com.example.oneuiapp.fragment.FavoriteFontListFragment; // ★ جديد: دعم قائمة المفضلة ★

import java.util.List;

import dev.oneuiproject.oneui.layout.DrawerLayout;

/**
 * SearchCoordinator - منسّق البحث المركزي
 *
 * يُوجّه استدعاءات filterFonts() و resetFilter() للـ Fragment الظاهر حالياً.
 *
 * ★ الإضافة: دعم FavoriteFontListFragment (الفهرس 4) في جميع عمليات البحث:
 *   - performSearch()     → يُفعّل البحث في قائمة المفضلة
 *   - handleSearchCollapse() → يُعيد قائمة المفضلة لوضعها الكامل عند إغلاق البحث
 *   - handleSearchIntent() → يقبل Intent البحث عند وجود المفضلة في المقدمة
 *   - saveState()         → يحفظ حالة البحث عند كون الفهرس 4 في المقدمة
 *   - restoreState()      → يستعيد البحث في قائمة المفضلة بعد إعادة البناء
 */
public class SearchCoordinator {
    
    private static final String TAG = "SearchCoordinator";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_SEARCH_EXPANDED = "search_expanded";
    
    private final Activity activity;
    private final DrawerLayout drawerLayout;
    
    private MenuItem searchMenuItem;
    private SearchView searchView;
    private List<Fragment> fragments;
    private FragmentIndexProvider fragmentIndexProvider;
    
    private boolean isSearchExpanded = false;
    private String savedSearchQuery = "";
    private int lastFragmentIndex = -1; // ★ لتتبع الشاشة السابقة ومنع تسرب حالة البحث ★
    
    private SearchStateListener stateListener;
    
    public interface FragmentIndexProvider {
        int getCurrentFragmentIndex();
    }
    
    public interface SearchStateListener {
        void onSearchExpanded();
        void onSearchCollapsed();
        void onSearchQueryChanged(String query);
    }
    
    public SearchCoordinator(@NonNull Activity activity, @NonNull DrawerLayout drawerLayout) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
    }
    
    public void setup(@Nullable MenuItem searchMenuItem, 
                     @NonNull List<Fragment> fragments,
                     @NonNull FragmentIndexProvider fragmentIndexProvider) {
        this.searchMenuItem = searchMenuItem;
        this.fragments = fragments;
        this.fragmentIndexProvider = fragmentIndexProvider;
        
        if (searchMenuItem != null) {
            setupSearchView();
        } else {
            Log.w(TAG, "Search MenuItem is null, search functionality will not be available");
        }
    }
    
    public void setSearchStateListener(@Nullable SearchStateListener listener) {
        this.stateListener = listener;
    }
    
    private void setupSearchView() {
        if (searchMenuItem == null) {
            return;
        }
        
        searchView = (SearchView) searchMenuItem.getActionView();
        if (searchView == null) {
            Log.e(TAG, "SearchView is null");
            return;
        }
        
        searchView.setQueryHint(activity.getString(R.string.search_font));
        searchView.setMaxWidth(Integer.MAX_VALUE);
        
        SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        }
        
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                if (stateListener != null) {
                    stateListener.onSearchQueryChanged(query);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performSearch(newText);
                if (stateListener != null) {
                    stateListener.onSearchQueryChanged(newText);
                }
                return true;
            }
        });

        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return handleSearchExpand();
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                return handleSearchCollapse();
            }
        });
        
        Log.d(TAG, "SearchView setup completed successfully");
    }
    
    private boolean handleSearchExpand() {
        isSearchExpanded = true;
        
        if (drawerLayout != null) {
            drawerLayout.setTitle(activity.getString(R.string.search_font));
            drawerLayout.setExpandedSubtitle(null);

            // ★ الميزة الجديدة: طي الـ AppBar بتأثير حركي عند فتح البحث
            // يمنح المستخدم مساحة أكبر لعرض النتائج ولوحة المفاتيح ★
            if (drawerLayout.getAppBarLayout() != null) {
                drawerLayout.getAppBarLayout().setExpanded(false, true);
            }
        }
        
        if (stateListener != null) {
            stateListener.onSearchExpanded();
        }
        
        Log.d(TAG, "Search expanded");
        return true;
    }
    
    private boolean handleSearchCollapse() {
        isSearchExpanded = false;
        savedSearchQuery = "";
        
        if (searchView != null) {
            searchView.setQuery("", false);
        }
        
        performSearch("");
        
        int currentIndex = fragmentIndexProvider.getCurrentFragmentIndex();
        Fragment currentFragment = getCurrentFragment();

        // ★ إعادة القائمة لوضعها الكامل بعد إغلاق البحث ★
        // المفضلة (4) تُعامَل كالخطوط المحلية (2) وخطوط النظام (3)
        if (currentFragment instanceof LocalFontListFragment) {
            ((LocalFontListFragment) currentFragment).resetFilter();
        } else if (currentFragment instanceof SystemFontListFragment) {
            ((SystemFontListFragment) currentFragment).resetFilter();
        } else if (currentFragment instanceof FavoriteFontListFragment) {
            // ★ إعادة قائمة المفضلة كاملةً عند إغلاق البحث ★
            ((FavoriteFontListFragment) currentFragment).resetFilter();
        }

        // ★ إعادة عنوان الدرج المناسب لكل قائمة عند إغلاق البحث ★
        if (drawerLayout != null) {
            if (currentIndex == 2) {
                drawerLayout.setTitle(activity.getString(R.string.drawer_local_fonts));
            } else if (currentIndex == 3) {
                drawerLayout.setTitle(activity.getString(R.string.drawer_system_fonts));
            } else if (currentIndex == 4) {
                // ★ إعادة عنوان قائمة المفضلة بعد إغلاق البحث ★
                drawerLayout.setTitle(activity.getString(R.string.drawer_favorites));
            }
        }
        
        if (stateListener != null) {
            stateListener.onSearchCollapsed();
        }
        
        Log.d(TAG, "Search collapsed");
        return true;
    }

    /**
     * ★ تنفيذ البحث في الـ Fragment الظاهر حالياً ★
     *
     * يدعم ثلاث قوائم:
     *   - index 2: LocalFontListFragment  (الخطوط المحلية)
     *   - index 3: SystemFontListFragment (خطوط النظام)
     *   - index 4: FavoriteFontListFragment (المفضلة) ← الإضافة الجديدة
     */
    private void performSearch(String query) {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof LocalFontListFragment) {
            ((LocalFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on LocalFontListFragment with query: " + query);
        } else if (currentFragment instanceof SystemFontListFragment) {
            ((SystemFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on SystemFontListFragment with query: " + query);
        } else if (currentFragment instanceof FavoriteFontListFragment) {
            // ★ تمرير نص البحث لقائمة المفضلة لتصفية عناصرها في الذاكرة ★
            ((FavoriteFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on FavoriteFontListFragment with query: " + query);
        }
    }
    
    @Nullable
    private Fragment getCurrentFragment() {
        if (fragments == null || fragmentIndexProvider == null) {
            return null;
        }
        
        int currentIndex = fragmentIndexProvider.getCurrentFragmentIndex();
        if (currentIndex >= 0 && currentIndex < fragments.size()) {
            return fragments.get(currentIndex);
        }
        
        return null;
    }

    /**
     * ★ معالجة Intent البحث الخارجي ★
     *
     * يُقبل Intent البحث في قوائم الخطوط (2، 3، 4).
     * يُرفض Intent البحث عند وجود فراجمنت آخر في المقدمة.
     */
    public boolean handleSearchIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_SEARCH.equals(intent.getAction())) {
            return false;
        }
        
        int currentIndex = fragmentIndexProvider.getCurrentFragmentIndex();
        // ★ قبول البحث في قوائم الخطوط الثلاث: 2 (محلية)، 3 (نظام)، 4 (مفضلة) ★
        if (currentIndex != 2 && currentIndex != 3 && currentIndex != 4) {
            intent.removeExtra(SearchManager.QUERY);
            return false;
        }
        
        if (searchMenuItem == null || !searchMenuItem.isActionViewExpanded()) {
            intent.removeExtra(SearchManager.QUERY);
            return false;
        }
        
        String query = intent.getStringExtra(SearchManager.QUERY);
        if (query != null && searchView != null) {
            searchView.setQuery(query, false);
            performSearch(query);
            Log.d(TAG, "Search intent handled with query: " + query);
            return true;
        }
        
        return false;
    }

    /**
     * ★ حفظ حالة البحث عند دوران الجهاز أو إعادة البناء ★
     *
     * يحفظ الحالة لقوائم الخطوط (2، 3، 4).
     * المفضلة (4) تُحفظ وتُستعاد كالقوائم الأخرى.
     */
    public void saveState(@NonNull Bundle outState) {
        int currentIndex = fragmentIndexProvider.getCurrentFragmentIndex();
        
        // ★ حفظ الحالة لأي قائمة خطوط: محلية (2)، نظام (3)، مفضلة (4) ★
        if (isSearchExpanded && (currentIndex == 2 || currentIndex == 3 || currentIndex == 4)) {
            outState.putBoolean(KEY_SEARCH_EXPANDED, true);
            if (searchView != null) {
                String currentQuery = searchView.getQuery().toString();
                outState.putString(KEY_SEARCH_QUERY, currentQuery);
            } else {
                outState.putString(KEY_SEARCH_QUERY, "");
            }
        } else {
            outState.putBoolean(KEY_SEARCH_EXPANDED, false);
            outState.putString(KEY_SEARCH_QUERY, "");
        }
        
        Log.d(TAG, "Search state saved - expanded: " + isSearchExpanded + ", query: " + savedSearchQuery);
    }

    /**
     * ★ استعادة حالة البحث بعد إعادة البناء ★
     *
     * يستعيد البحث في أي قائمة خطوط (2، 3، 4) كانت ظاهرة
     * عند حدوث إعادة البناء.
     */
    public void restoreState(@NonNull Bundle savedInstanceState) {
        isSearchExpanded = savedInstanceState.getBoolean(KEY_SEARCH_EXPANDED, false);
        savedSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "");
        
        int currentIndex = fragmentIndexProvider.getCurrentFragmentIndex();
        // ★ استعادة البحث في أي قائمة خطوط: محلية (2)، نظام (3)، مفضلة (4) ★
        if (isSearchExpanded
                && (currentIndex == 2 || currentIndex == 3 || currentIndex == 4)
                && searchMenuItem != null
                && drawerLayout != null) {
            drawerLayout.post(() -> {
                if (searchMenuItem != null) {
                    searchMenuItem.expandActionView();
                    if (searchView != null && !savedSearchQuery.isEmpty()) {
                        searchView.setQuery(savedSearchQuery, false);
                    }
                }
            });
        }
        
        Log.d(TAG, "Search state restored - expanded: " + isSearchExpanded + ", query: " + savedSearchQuery);
    }
    
    public void collapseSearch() {
        if (searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
            searchMenuItem.collapseActionView();
            Log.d(TAG, "Search collapsed programmatically");
        }
    }
    
    public void expandSearch() {
        if (searchMenuItem != null && !searchMenuItem.isActionViewExpanded()) {
            searchMenuItem.expandActionView();
            Log.d(TAG, "Search expanded programmatically");
        }
    }
    
    public void setSearchQuery(@NonNull String query) {
        if (searchView != null) {
            searchView.setQuery(query, false);
            performSearch(query);
        }
    }
    
    public boolean isSearchExpanded() {
        return isSearchExpanded;
    }
    
    @NonNull
    public String getCurrentSearchQuery() {
        if (searchView != null) {
            return searchView.getQuery().toString();
        }
        return savedSearchQuery;
    }
    
    public boolean isSearchActive() {
        return isSearchExpanded && !getCurrentSearchQuery().isEmpty();
    }
    
    public void clearSearchQuery() {
        if (searchView != null) {
            searchView.setQuery("", false);
            performSearch("");
        }
    }
    
    public void onFragmentChanged(int newFragmentIndex) {
        // ★ الإصلاح الجوهري: إذا تغيرت الشاشة (من 2 إلى 3 أو العكس أو غيرهما)
        // نغلق البحث فوراً لمنع تسرب حالته إلى الشاشة الجديدة ★
        if (lastFragmentIndex != -1 && lastFragmentIndex != newFragmentIndex) {
            collapseSearch();
        }
        lastFragmentIndex = newFragmentIndex;
        
        Log.d(TAG, "Fragment changed to index: " + newFragmentIndex);
    }
    
    public void cleanup() {
        searchMenuItem = null;
        searchView = null;
        fragments = null;
        fragmentIndexProvider = null;
        stateListener = null;
        
        Log.d(TAG, "SearchCoordinator cleaned up");
    }
            }
