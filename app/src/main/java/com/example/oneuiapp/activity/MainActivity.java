package com.example.oneuiapp.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.oneuiproject.oneui.layout.DrawerLayout;
import dev.oneuiproject.oneui.dialog.ProgressDialog;

import com.example.oneuiapp.dialog.FontInfoDialog;
import com.example.oneuiapp.fragment.FontViewerFragment;
import com.example.oneuiapp.fragment.LocalFontListFragment;
import com.example.oneuiapp.fragment.SystemFontListFragment;
import com.example.oneuiapp.fragment.HomeFragment;
import com.example.oneuiapp.ui.drawer.DrawerListAdapter;
import com.example.oneuiapp.R;
import com.example.oneuiapp.utils.FileUtils;
import com.example.oneuiapp.utils.TranslationService;
import com.example.oneuiapp.fontlist.search.SearchCoordinator;

/**
 * MainActivity - معدّل لعرض العناوين بشكل صحيح
 * العنوان الرئيسي: الاسم الحقيقي للخط
 * العنوان الفرعي: اسم الملف بدون صيغة
 *
 * ★ نظام التنقل ★
 * جميع منطق التنقل (زر الرجوع، المكدس، الأنيميشن، إدارة الفراغمنتات)
 * منقول إلى NavManager.java للفصل بين مسؤوليات النشاط ومنطق التنقل.
 * تُنفّذ هذه الفئة واجهة NavManager.Host لتزويد NavManager بما يحتاجه
 * دون اقتران مباشر بين الكلاسين.
 *
 * ★ آلية رصد حالة الدرج ★
 * مكتبة OneUI تُغلِّف الـ androidx.drawerlayout.widget.DrawerLayout داخلياً
 * ولا تُوفّر دالة عامة للوصول إليه. يتولى NavManager البحث عنه مرة واحدة
 * في setup() عبر اجتياز شجرة الـ Views، واستدعاء isDrawerOpen() مباشرةً
 * دون الحاجة لأي متغير تتبع منفصل أو مستمع إضافي.
 *
 * ★ التعديل: تحديث onFontSelected لاستقبال weightWidthLabel وتمريره لـ NavManager ★
 * يجب تحديث واجهتَي LocalFontListFragment.OnFontSelectedListener
 * و SystemFontListFragment.OnFontSelectedListener في ملفي الفراغمنتات المقابلين
 * ليتضمنا المعامل الجديد String weightWidthLabel.
 */
public class MainActivity extends BaseActivity
        implements FontViewerFragment.OnFontChangedListener,
        LocalFontListFragment.OnFontSelectedListener,
        SystemFontListFragment.OnFontSelectedListener,
        NavManager.Host {

    private boolean isUIReady = false;
    private DrawerLayout mDrawerLayout;
    private RecyclerView mDrawerListView;
    private DrawerListAdapter mDrawerAdapter;
    private List<Fragment> mFragments = new ArrayList<>();
    private int mCurrentFragmentIndex = 1;
    private static final String KEY_CURRENT_FRAGMENT    = "current_fragment_index";
    private static final String KEY_LOCAL_FONTS_COUNT   = "local_fonts_count";
    private static final String KEY_SYSTEM_FONTS_COUNT  = "system_fonts_count";
    private static final String TAG_HOME                = "fragment_home";
    private static final String TAG_FONT_VIEWER         = "fragment_font_viewer";
    private static final String TAG_LOCAL_FONT_LIST     = "fragment_font_list";
    private static final String TAG_SYSTEM_FONT_LIST    = "fragment_system_font_list";

    private String currentFontRealName;
    private String currentFontFileName;

    // ★ الإصلاح الجوهري: فصل عداد المجلد المحلي عن عداد خطوط النظام ★
    // هذا يمنع أي فراجمنت من الكتابة فوق عدد الفراجمنت الآخر عند إعادة البناء
    private int mLocalFontsCount  = 0;
    private int mSystemFontsCount = 0;

    // ★ مدير التنقل المركزي — يتولى جميع عمليات التنقل بين الشاشات ★
    // يتواصل مع هذا النشاط عبر واجهة NavManager.Host المُنفَّذة أدناه
    private NavManager mNavManager;

    private MenuItem mFontMetaMenuItem;
    private MenuItem mSearchMenuItem;
    private FloatingActionButton fabFontSize;

    private SearchCoordinator mSearchCoordinator;
    private ProgressDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        splashScreen.setKeepOnScreenCondition(() -> !isUIReady);

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
        }

        setContentView(R.layout.activity_main);

        // ★ تهيئة NavManager مبكراً قبل أي دالة تستدعي التنقل ★
        mNavManager = new NavManager(this);

        initViews();
        initFragmentsList();
        setupMenus();
        setupDrawerButton();
        setupSearchCoordinator();

        if (savedInstanceState != null) {
            // ★ استعادة العدادين المستقلين عند إعادة البناء ★
            mLocalFontsCount  = savedInstanceState.getInt(KEY_LOCAL_FONTS_COUNT, 0);
            mSystemFontsCount = savedInstanceState.getInt(KEY_SYSTEM_FONTS_COUNT, 0);
            // ★ استعادة مكدس التنقل عند إعادة البناء — مُفوَّضة لـ NavManager ★
            mNavManager.restoreNavBackStack(savedInstanceState);
            restoreFragmentsState(savedInstanceState);
            mSearchCoordinator.restoreState(savedInstanceState);
        } else {
            addAllFragments();
            mCurrentFragmentIndex = 1;
            mNavManager.showFragmentFast(1);
        }

        setupDrawer();
        setupFabFontSize();
        updateDrawerTitle(mCurrentFragmentIndex);

        handleIntent(getIntent());
        isUIReady = true;
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(android.content.Intent intent) {
        mSearchCoordinator.handleSearchIntent(intent);
    }

    private void initViews() {
        mDrawerLayout   = findViewById(R.id.drawer_layout);
        mDrawerListView = findViewById(R.id.drawer_list_view);
        fabFontSize     = findViewById(R.id.fab_font_size);
    }

    private void initFragmentsList() {
        if (mFragments.isEmpty()) {
            mFragments.add(new HomeFragment());
            mFragments.add(new FontViewerFragment());
            mFragments.add(new LocalFontListFragment());
            mFragments.add(new SystemFontListFragment());
        }
    }

    private void setupMenus() {
        try {
            if (mDrawerLayout != null && mDrawerLayout.getToolbar() != null) {
                mDrawerLayout.getToolbar().inflateMenu(R.menu.menu_main_font_meta);
                mDrawerLayout.getToolbar().inflateMenu(R.menu.menu_font_list_search);

                Menu menu = mDrawerLayout.getToolbar().getMenu();
                if (menu != null) {
                    mFontMetaMenuItem = menu.findItem(R.id.action_font_meta);
                    mSearchMenuItem   = menu.findItem(R.id.action_search_fonts);

                    if (mFontMetaMenuItem != null) mFontMetaMenuItem.setVisible(false);
                    if (mSearchMenuItem   != null) mSearchMenuItem.setVisible(false);
                }

                mDrawerLayout.getToolbar().setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.action_font_meta) {
                        showFontMetaFromFragment();
                        return true;
                    }
                    return false;
                });
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to setup menus", e);
        }
    }

    private void setupSearchCoordinator() {
        mSearchCoordinator = new SearchCoordinator(this, mDrawerLayout);
        mSearchCoordinator.setup(mSearchMenuItem, mFragments, () -> mCurrentFragmentIndex);

        mSearchCoordinator.setSearchStateListener(new SearchCoordinator.SearchStateListener() {
            @Override
            public void onSearchExpanded() {
            }

            @Override
            public void onSearchCollapsed() {
                updateDrawerTitle(mCurrentFragmentIndex);
            }

            @Override
            public void onSearchQueryChanged(String query) {
            }
        });
    }

    private void setupDrawerButton() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerButtonIcon(getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_settings_outline));
            mDrawerLayout.setDrawerButtonTooltip(getText(R.string.title_settings));
            mDrawerLayout.setDrawerButtonOnClickListener(v -> openSettingsActivity());
        }
    }

    private void setDrawerOpen(boolean open, boolean animate) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerOpen(open, animate);
        }
    }

    private void openSettingsActivity() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void setupFabFontSize() {
        if (fabFontSize != null) {
            fabFontSize.setOnClickListener(v -> {
                Fragment currentFragment = mFragments.get(mCurrentFragmentIndex);
                if (currentFragment instanceof FontViewerFragment) {
                    ((FontViewerFragment) currentFragment).showFontSizeDialogPublic();
                }
            });
        }
    }

    private void restoreFragmentsState(Bundle savedInstanceState) {
        mCurrentFragmentIndex = savedInstanceState.getInt(KEY_CURRENT_FRAGMENT, 1);

        if (mCurrentFragmentIndex == 0) {
            mCurrentFragmentIndex = 1;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment homeFragment           = fm.findFragmentByTag(TAG_HOME);
        Fragment fontViewerFragment     = fm.findFragmentByTag(TAG_FONT_VIEWER);
        Fragment localFontListFragment  = fm.findFragmentByTag(TAG_LOCAL_FONT_LIST);
        Fragment systemFontListFragment = fm.findFragmentByTag(TAG_SYSTEM_FONT_LIST);

        if (homeFragment != null && fontViewerFragment != null
                && localFontListFragment != null && systemFontListFragment != null) {
            mFragments.clear();
            mFragments.add(homeFragment);
            mFragments.add(fontViewerFragment);
            mFragments.add(localFontListFragment);
            mFragments.add(systemFontListFragment);
        }

        mNavManager.showFragmentFast(mCurrentFragmentIndex);

        if (mDrawerAdapter != null) {
            mDrawerAdapter.setSelectedItem(mCurrentFragmentIndex);
        }
    }

    private void addAllFragments() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        transaction.add(R.id.main_content, mFragments.get(0), TAG_HOME);
        transaction.hide(mFragments.get(0));

        transaction.add(R.id.main_content, mFragments.get(1), TAG_FONT_VIEWER);

        transaction.add(R.id.main_content, mFragments.get(2), TAG_LOCAL_FONT_LIST);
        transaction.hide(mFragments.get(2));

        transaction.add(R.id.main_content, mFragments.get(3), TAG_SYSTEM_FONT_LIST);
        transaction.hide(mFragments.get(3));

        transaction.commit();
    }

    private void setupDrawer() {
        mDrawerListView.setLayoutManager(new LinearLayoutManager(this));
        mDrawerAdapter = new DrawerListAdapter(
                this,
                mFragments,
                position -> {
                    if (position == 0) {
                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        startActivity(intent);
                        return false;
                    }

                    setDrawerOpen(false, true);

                    if (position != mCurrentFragmentIndex) {
                        // ★ التنقل عبر الدرج مُفوَّض بالكامل لـ NavManager ★
                        mNavManager.navigateFromDrawer(position);
                        return true;
                    }
                    return false;
                });
        mDrawerListView.setAdapter(mDrawerAdapter);

        mDrawerAdapter.setSelectedItem(mCurrentFragmentIndex);

        // ★ تهيئة NavManager بالـ DrawerLayout لاستخراج الـ inner DrawerLayout ★
        // يستخرج NavManager.setup() الـ inner DrawerLayout مرة واحدة ويحفظه داخلياً
        // لاستدعاء isDrawerOpen() مباشرةً عند كل ضغطة رجوع.
        if (mDrawerLayout != null) {
            mNavManager.setup(mDrawerLayout);
        }

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        mDrawerListView.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    startX = ev.getX();
                    startY = ev.getY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float dx = Math.abs(ev.getX() - startX);
                    float dy = Math.abs(ev.getY() - startY);
                    if (dx > touchSlop && dx > dy) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    } else {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
    }

    /**
     * ★ التعديل الرئيسي للهدف 2 و 3 ★
     * العنوان الرئيسي = الاسم الحقيقي
     * العنوان الفرعي = اسم الملف بدون صيغة
     *
     * كل فراجمنت يستخدم عداده المستقل (mLocalFontsCount أو mSystemFontsCount)
     */
    @Override
    public void updateDrawerTitle(int fragmentIndex) {
        if (mDrawerLayout == null) {
            return;
        }

        if (mSearchCoordinator.isSearchExpanded()) {
            return;
        }

        String title;
        String subtitle;

        if (fragmentIndex == 0) {
            title    = getString(R.string.app_name);
            subtitle = getString(R.string.app_subtitle);

        } else if (fragmentIndex == 1) {
            if (currentFontRealName != null && !currentFontRealName.isEmpty()) {
                title = currentFontRealName;
            } else {
                title = getString(R.string.drawer_font_viewer);
            }

            if (currentFontFileName != null && !currentFontFileName.isEmpty()) {
                subtitle = FileUtils.removeExtension(currentFontFileName);
            } else {
                subtitle = getString(R.string.font_viewer_select_description);
            }

        } else if (fragmentIndex == 2) {
            title    = getString(R.string.drawer_local_fonts);
            // ★ يستخدم العداد المخصص للمجلد المحلي فقط ★
            subtitle = getFontsCountString(mLocalFontsCount);

        } else if (fragmentIndex == 3) {
            title    = getString(R.string.drawer_system_fonts);
            // ★ يستخدم العداد المخصص لخطوط النظام فقط ★
            subtitle = getFontsCountString(mSystemFontsCount);

        } else {
            title    = getString(R.string.app_name);
            subtitle = getString(R.string.app_subtitle);
        }

        mDrawerLayout.setTitle(title);
        mDrawerLayout.setExpandedSubtitle(subtitle);
    }

    private String getFontsCountString(int count) {
        if (count == 0) {
            return getString(R.string.font_list_count_none);
        } else if (count == 1) {
            return getString(R.string.font_list_count_single, count);
        } else {
            return getString(R.string.font_list_count_multiple, count);
        }
    }

    @Override
    public void updateMenuVisibility(int position) {
        if (mFontMetaMenuItem != null) {
            mFontMetaMenuItem.setVisible(position == 1);
        }

        if (mSearchMenuItem != null) {
            mSearchMenuItem.setVisible(position == 2 || position == 3);
        }
    }

    @Override
    public void updateFabVisibility(int position) {
        if (fabFontSize != null) {
            fabFontSize.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * ★ الإصلاح الجوهري للمشكلتين 1 و 3 ★
     * كل فراجمنت يُرسل فهرسه مع العدد، فيُخزَّن في عداده المستقل.
     * تحديث الواجهة يحدث فقط إذا كان الطلب قادماً من الفراجمنت الظاهر حالياً،
     * مما يمنع أي فراجمنت مخفي من الكتابة فوق العنوان الفرعي الصحيح.
     *
     * @param fromFragmentIndex فهرس الفراجمنت المُرسِل (2 = محلي، 3 = نظام)
     * @param count             العدد الجديد للخطوط
     */
    public void updateFontsCount(int fromFragmentIndex, int count) {
        // تخزين العدد لكل فراجمنت بشكل مستقل بغض النظر عن الفراجمنت الظاهر
        if (fromFragmentIndex == 2) {
            mLocalFontsCount = count;
        } else if (fromFragmentIndex == 3) {
            mSystemFontsCount = count;
        }

        // ★ تحديث الواجهة فقط إذا كان المُرسِل هو الفراجمنت الظاهر حالياً ★
        if (fromFragmentIndex == mCurrentFragmentIndex && !mSearchCoordinator.isSearchExpanded()) {
            runOnUiThread(() -> {
                if (mDrawerLayout != null) {
                    mDrawerLayout.setExpandedSubtitle(getFontsCountString(count));
                }
            });
        }
    }

    private void showFontMetaFromFragment() {
        Fragment frag = (mFragments.size() > 1) ? mFragments.get(1) : null;
        if (!(frag instanceof FontViewerFragment)) {
            showNoFontDialog();
            return;
        }

        FontViewerFragment fvf = (FontViewerFragment) frag;
        if (!fvf.hasFontSelected()) {
            showNoFontDialog();
            return;
        }

        Map<String, String> meta = fvf.getFontMetaData();

        TranslationService translationService = new TranslationService(this);
        if (translationService.isTranslationEnabled()) {
            showLoadingDialog();
            translationService.translateMetadata(meta, new TranslationService.TranslationCallback() {
                @Override
                public void onTranslationComplete(Map<String, String> translatedData) {
                    runOnUiThread(() -> {
                        dismissLoadingDialog();
                        showFontInfoDialog(translatedData);
                    });
                }

                @Override
                public void onTranslationFailed(String error) {
                    runOnUiThread(() -> {
                        dismissLoadingDialog();
                        android.util.Log.w("MainActivity", "Translation failed: " + error);
                        showFontInfoDialog(meta);
                    });
                }
            });
        } else {
            showFontInfoDialog(meta);
        }
    }

    private void showFontInfoDialog(Map<String, String> metadata) {
        Fragment frag = mFragments.get(1);
        if (!(frag instanceof FontViewerFragment)) {
            return;
        }

        FontViewerFragment fvf = (FontViewerFragment) frag;

        String fileName = fvf.getCurrentFontFileName();
        String path     = fvf.originalFontPath;

        FontInfoDialog dialog = new FontInfoDialog(this, metadata, fileName, path);
        dialog.show();
    }

    private void showLoadingDialog() {
        dismissLoadingDialog();
        try {
            loadingDialog = new ProgressDialog(this);
            loadingDialog.setMessage("Translating...");
            loadingDialog.setCancelable(false);
            loadingDialog.show();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to show loading dialog", e);
        }
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            try {
                loadingDialog.dismiss();
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to dismiss loading dialog", e);
            }
            loadingDialog = null;
        }
    }

    private void showNoFontDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.font_viewer_select_font))
                .setMessage(getString(R.string.font_viewer_no_font_selected))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ════════════════════════════════════════════════════════
    //  واجهات Listener — مُفوَّضة لـ NavManager
    // ════════════════════════════════════════════════════════

    /**
     * ★ التعديل: استقبال weightWidthLabel وتمريره لـ NavManager ★
     *
     * يتطلب هذا التعديل تحديث واجهتَي:
     *   - LocalFontListFragment.OnFontSelectedListener
     *   - SystemFontListFragment.OnFontSelectedListener
     * بإضافة المعامل الخامس: String weightWidthLabel
     *
     * كما يتطلب تحديث التنفيذ في كلا الفراغمنتَين ليمرروا weightWidthLabel
     * عند استدعاء listener.onFontSelected(..., weightWidthLabel).
     */
    @Override
    public void onFontSelected(String fontPath, String realName, String fileName,
                               int ttcIndex, String weightWidthLabel) {
        mNavManager.handleFontSelected(fontPath, realName, fileName, ttcIndex, weightWidthLabel);
    }

    @Override
    public void onFontChanged(String fontRealName, String fontFileName) {
        this.currentFontRealName = fontRealName;
        this.currentFontFileName = fontFileName;

        if (mCurrentFragmentIndex == 1) {
            runOnUiThread(() -> {
                updateDrawerTitle(mCurrentFragmentIndex);
            });
        }
    }

    @Override
    public void onFontCleared() {
        this.currentFontRealName = null;
        this.currentFontFileName = null;

        if (mCurrentFragmentIndex == 1) {
            updateDrawerTitle(mCurrentFragmentIndex);
        }
    }

    // ════════════════════════════════════════════════════════
    //  تنفيذ NavManager.Host
    // ════════════════════════════════════════════════════════

    /**
     * ★ الإصلاح: تُسمَّى getAppFragmentManager() بدلاً من getFragmentManager() ★
     * السبب: getFragmentManager() موجودة في Activity وتُعيد android.app.FragmentManager (مُهمَلة).
     * تجاوزها بنوع إعادة androidx.fragment.app.FragmentManager يُسبب خطأ تجميع
     * لأن النوعين غير متوافقَين من منظور Java. تغيير الاسم يتجنب التعارض كلياً.
     */
    @Override
    public FragmentManager getAppFragmentManager() {
        return getSupportFragmentManager();
    }

    @Override
    public List<Fragment> getFragments() {
        return mFragments;
    }

    @Override
    public int getCurrentIndex() {
        return mCurrentFragmentIndex;
    }

    @Override
    public void setCurrentIndex(int index) {
        mCurrentFragmentIndex = index;
    }

    @Override
    public DrawerLayout getDrawerLayout() {
        return mDrawerLayout;
    }

    @Override
    public DrawerListAdapter getDrawerAdapter() {
        return mDrawerAdapter;
    }

    @Override
    public SearchCoordinator getSearchCoordinator() {
        return mSearchCoordinator;
    }

    @Override
    public String getFontRealName() {
        return currentFontRealName;
    }

    @Override
    public String getFontFileName() {
        return currentFontFileName;
    }

    @Override
    public void setFontRealName(String name) {
        currentFontRealName = name;
    }

    @Override
    public void setFontFileName(String name) {
        currentFontFileName = name;
    }

    /**
     * يُنفّذ الخروج الفعلي من التطبيق.
     * يُفرّق بين Android O (API 26) مع isTaskRoot وبقية الإصدارات.
     */
    @Override
    public void performExit() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTaskRoot()) {
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

    /** يعرض رسالة Toast تطلب من المستخدم الضغط مرة أخرى للخروج */
    @Override
    public void showPressAgainToExitToast() {
        Toast.makeText(this,
                getString(R.string.exit_on_double_back),
                Toast.LENGTH_SHORT).show();
    }

    // ════════════════════════════════════════════════════════
    //  دورة حياة النشاط
    // ════════════════════════════════════════════════════════

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_FRAGMENT, mCurrentFragmentIndex);
        // ★ حفظ كلا العدادين بشكل مستقل لاستعادتهما بدقة عند إعادة البناء ★
        outState.putInt(KEY_LOCAL_FONTS_COUNT,  mLocalFontsCount);
        outState.putInt(KEY_SYSTEM_FONTS_COUNT, mSystemFontsCount);
        // ★ حفظ مكدس التنقل — مُفوَّض لـ NavManager ★
        mNavManager.saveState(outState);
        mSearchCoordinator.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        dismissLoadingDialog();
        mSearchCoordinator.cleanup();
        super.onDestroy();
    }

    /**
     * ★ منطق زر الرجوع — مُفوَّض بالكامل لـ NavManager ★
     * التفاصيل الكاملة لأولويات التنفيذ موثَّقة في NavManager.handleBackPressed().
     */
    @Override
    public void onBackPressed() {
        mNavManager.handleBackPressed();
    }

    // ════════════════════════════════════════════════════════
    //  دوال مساعدة عامة
    // ════════════════════════════════════════════════════════

    public void updateDrawerSelection(int position) {
        if (mDrawerAdapter != null && position >= 0 && position < mFragments.size()) {
            mCurrentFragmentIndex = position;
            mDrawerAdapter.setSelectedItem(position);
            updateDrawerTitle(position);
        }
    }
                }
