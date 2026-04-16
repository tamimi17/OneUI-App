package com.example.oneuiapp.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.example.oneuiapp.activity.MainActivity;
import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.fontlist.FontFileInfo;
import com.example.oneuiapp.fontlist.search.FontSearchManager;
import com.example.oneuiapp.fontlist.FontSortManager;
import com.example.oneuiapp.fontlist.FontUIStateManager;
import com.example.oneuiapp.fontlist.adapter.SystemFontListAdapter;
import com.example.oneuiapp.R;
import com.example.oneuiapp.ui.widget.SortByItemLayout;
import com.example.oneuiapp.viewmodel.SystemFontListViewModel;
import com.example.oneuiapp.viewmodel.SearchViewModel;
import com.example.oneuiapp.viewmodel.SettingsViewModel;

/**
 * SystemFontListFragment — محدث ليفوّض الفرز بالكامل إلى SortedList داخل SystemFontListAdapter.
 * ★ لم يعد هذا الـ Fragment يستدعي FontSortManager.sortFontsList() ★
 * ★ عند تغيير الفرز → mAdapter.setSortOptions() → أنيميشن مباشر ★
 * ★ عند وصول بيانات جديدة → mAdapter.updateFilteredFonts() → SortedList يرتبها تلقائياً ★
 *
 * ★ يستخدم FontSortManager(context, true) → مفاتيح DataStore خاصة بخطوط النظام فقط ★
 * هذا يضمن عدم تأثر هذا الـ Fragment بأي تغيير في فرز المجلد المحلي ويحل مشكلة التجمد نهائياً.
 */
public class SystemFontListFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "SystemFontListFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;
    private SystemFontListAdapter mAdapter;
    private OnFontSelectedListener mFontSelectedListener;
    private Handler mMainHandler;
    private ExecutorService mExecutor;
    private AppBarLayout mAppBarLayout;
    private DrawerLayout mDrawerLayout;

    private FontSearchManager mSearchManager;
    private FontSortManager mSortManager;
    private FontUIStateManager mUIManager;

    private SystemFontListViewModel mViewModel;
    private SearchViewModel mSearchViewModel;
    private SettingsViewModel mSettingsViewModel;
    private List<FontEntity> mCurrentFontsList = new ArrayList<>();

    private boolean mIsFirstLoad = true;

    // ★ علامة تضمن استعادة موضع التمرير فقط عند أول وصول للبيانات بعد إعادة البناء من الـ bundle ★
    //
    // السبب الجذري للمشكلة التي كانت قائمة:
    //   كانت العلامة تُضبط أيضاً داخل onHiddenChanged(false) عند العودة من عارض الخطوط،
    //   غير أن mMainHandler.post() الموجود هناك يُعيد موضع التمرير مباشرةً بشكل كافٍ،
    //   ولا يحدث أي استدعاء لـ refreshAdapterData() بعده لاستهلاك العلامة وإعادتها إلى false،
    //   لأن Room لا تُصدر قيمة جديدة في غياب تغيير في قاعدة البيانات.
    //   فإذا ضغط المستخدم على Home وأعاد فتح التطبيق دون إنهاء العملية،
    //   تبقى العلامة true، وعند النقر على أي خط لاحق يستدعي recordFontAccess كتابةً
    //   في قاعدة البيانات فتُصدر Room LiveData قيمة وتجد refreshAdapterData العلامة true
    //   فتقفز للموضع القديم.
    //
    // القاعدة الصارمة: لا تُضبط هذه العلامة إلا في restoreInstanceState() فقط.
    // حالة onHiddenChanged(false) تعتمد على mMainHandler.post() المباشر ولا تحتاج للعلامة.
    private boolean mNeedsScrollRestore = false;

    // ★ يحجب جميع أحداث اللمس على الـ RecyclerView ★
    private final RecyclerView.OnItemTouchListener mTouchBlocker =
        new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                 @NonNull MotionEvent e) { return true; }
            @Override
            public void onTouchEvent(@NonNull RecyclerView rv,
                                     @NonNull MotionEvent e) {}
            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean b) {}
        };

    public interface OnFontSelectedListener {
        void onFontSelected(String fontPath, String realName, String fileName, int ttcIndex);
    }

    // ─────────────────────────────────────────────────────────
    // دوال التحكم في اللمس — تُستدعى من MainActivity
    // ─────────────────────────────────────────────────────────

    /** تعطيل اللمس فوراً عند النقر على خط */
    public void blockTouch() {
        if (mRecyclerView != null) {
            mRecyclerView.removeOnItemTouchListener(mTouchBlocker);
            mRecyclerView.addOnItemTouchListener(mTouchBlocker);
        }
    }

    /** تفعيل اللمس عند العودة للقائمة */
    public void unblockTouch() {
        if (mRecyclerView != null)
            mRecyclerView.removeOnItemTouchListener(mTouchBlocker);
        // ★ إعادة تفعيل الحارس لقبول النقرات مجدداً ★
        if (mAdapter != null) mAdapter.resetClickGuard();
        View root = getView();
        if (root != null) {
            root.setClickable(true);
            root.setFocusable(true);
            root.setEnabled(true);
            root.bringToFront();
            root.requestFocus();
        }
    }

    /** حفظ آخر خط مفتوح وتمييزه — يُستدعى بعد تأكيد الانتقال */
    public void saveAndHighlight(String path) {
        if (mAdapter != null) {
            mAdapter.saveLastOpenedAndUpdate(path);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        if (context instanceof OnFontSelectedListener) {
            mFontSelectedListener = (OnFontSelectedListener) context;
        }

        initializeManagers();
        setupManagerListeners();
    }

    private void initializeManagers() {
        mSearchManager = new FontSearchManager();

        // ★ true = خطوط النظام → يقرأ/يكتب KEY_SYSTEM_SORT_TYPE و KEY_SYSTEM_SORT_ASCENDING ★
        // هذا يمنع تماماً أن يتسبب تغيير فرز المجلد المحلي في إعادة فرز آلاف خطوط النظام
        mSortManager = new FontSortManager(mContext, true);

        mUIManager = new FontUIStateManager(mContext);
    }

    private void setupManagerListeners() {
        // ★ المستمع يحدّث حالة الواجهة فقط — تحديث الـ Adapter يتم من المراقب ★
        mSearchManager.setSearchResultListener((count, empty) -> {
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive());
        });

        // ★ التغيير الجوهري: عند تغيير الفرز نستدعي setSortOptions مباشرة ★
        // هذا يُشغّل أنيميشن SortedList بدلاً من إعادة تحميل البيانات كلها
        mSortManager.setSortChangeListener((type, asc) -> {
            if (mAdapter != null) {
                mAdapter.setSortOptions(type, asc);
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        mMainHandler = new Handler(Looper.getMainLooper());
        mExecutor    = Executors.newSingleThreadExecutor();

        initializeViewModels();

        if (state != null) {
            restoreInstanceState(state);
            mIsFirstLoad = state.getBoolean("is_first_load", true);
        }
    }

    private void initializeViewModels() {
        mViewModel         = new ViewModelProvider(this).get(SystemFontListViewModel.class);
        mSearchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        mViewModel.getFontsLiveData().observe(this, fonts -> {
            if (fonts != null) {
                mCurrentFontsList = new ArrayList<>(fonts);
                // ★ تحديث البيانات — SortedList يرتبها تلقائياً حسب معيار الفرز الحالي ★
                refreshAdapterData();
                updateMainActivityFontsCount(fonts.size());
            }
        });

        mViewModel.getIsLoadingLiveData().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                mUIManager.showLoadingState();
            } else {
                mUIManager.hideLoadingState();
            }
        });

        mViewModel.getIsApiAvailableLiveData().observe(this, isAvailable -> {
            if (isAvailable != null && !isAvailable) {
                showUnsupportedView();
            }
        });

        mSearchViewModel.getSearchQueryLiveData().observe(this, query -> {
            if (query != null) {
                // فلترة ثم تحديث الـ Adapter مرة واحدة فقط
                mSearchManager.filterFonts(query);
                if (mAdapter != null) {
                    mAdapter.updateFilteredFonts(
                        mSearchManager.getFilteredFonts(),
                        mSearchManager.getCurrentSearchQuery()
                    );
                }
            }
        });

        mSettingsViewModel.getFontPreviewEnabled().observe(this, enabled -> {
            if (mAdapter != null && isAdded()) {
                mMainHandler.post(() -> mAdapter.smartUpdate());
                Log.d(TAG, "Font preview setting changed: " + enabled);
            }
        });
    }

    private void restoreInstanceState(Bundle state) {
        mUIManager.setRecyclerViewState(state.getParcelable("recycler_state"));

        // ★ تفعيل الاستعادة فقط إذا كانت هناك حالة تمرير محفوظة فعلاً ★
        // هذا هو المكان الوحيد المسموح فيه بضبط mNeedsScrollRestore على true.
        // عند وصول البيانات الأولى من LiveData بعد إعادة البناء، تستهلك refreshAdapterData()
        // هذه العلامة وتُعيدها إلى false، ما يحول دون أي قفز لاحق ناتج عن recordFontAccess.
        if (mUIManager.getRecyclerViewState() != null) {
            mNeedsScrollRestore = true;
        }

        String sortType = state.getString("sort_type");
        if (sortType != null) {
            try {
                mUIManager.saveSortState(
                    SortByItemLayout.SortType.valueOf(sortType),
                    state.getBoolean("sort_asc", true)
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore sort state", e);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_system_font_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        initializeViews(view);
        setupRecyclerView();
        setupAppBarLayout();

        // ★ الإصلاح الجوهري: حُذف بلوك hasSavedSortState و mMainHandler.post ★
        // السبب: كلاهما كان يعمل بعد وصول البيانات من LiveData، مما يُسبب "سباق زمني"
        // يجعل الـ SortedList يرتب البيانات أولاً بالفرز الافتراضي (الاسم)، ثم يتلقى
        // أمر الفرز الصحيح متأخراً فلا يُعيد ترتيب القائمة أمام المستخدم.
        // الحل: تهيئة الفرز داخل setupRecyclerView() مباشرةً بعد setAdapter()
        // لضمان جهوزية الـ Adapter قبل أي وصول للبيانات.

        if (mIsFirstLoad) {
            mViewModel.loadSystemFonts();
            mIsFirstLoad = false;
        }
    }

    private void initializeViews(View view) {
        mRecyclerView = view.findViewById(R.id.system_font_recycler_view);
        mUIManager.setViews(
            null,
            view.findViewById(R.id.main_content_layout),
            view.findViewById(R.id.empty_view),
            view.findViewById(R.id.empty_text),
            mRecyclerView
        );
    }

    private void setupRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        mAdapter = new SystemFontListAdapter(mContext, mExecutor);

        mAdapter.setFontClickListener((fontPath, realName, fileName, ttcIndex) -> {
            mViewModel.recordFontAccess(fontPath);
            if (mFontSelectedListener != null) {
                mFontSelectedListener.onFontSelected(fontPath, realName, fileName, ttcIndex);
            }
        });

        // ★ عند الضغط على شريط الفرز: حفظ التفضيل في DataStore عبر SortManager
        // SortManager يُشعر مستمعه (setupManagerListeners) الذي يستدعي mAdapter.setSortOptions → أنيميشن ★
        mAdapter.setSortChangeListener((type, asc) -> {
            mSortManager.setSortOptions(type, asc);
        });

        mRecyclerView.setAdapter(mAdapter);

        // ★★★ الإصلاح السحري: إخبار الـ Adapter بنوع الفرز المحفوظ قبل أن يستلم أي بيانات ★★★
        // يضمن هذا أنه عندما تصل البيانات من LiveData (حتى لو وصلت قبل onViewCreated)،
        // سيقوم الـ SortedList بترتيبها مباشرةً بالمعيار الصحيح بدون أي سباق زمني
        mAdapter.updateSortOptionsOnly(
            mSortManager.getCurrentSortType(),
            mSortManager.isSortAscending()
        );

        // ★ استدعاء دالة الأنيميشن المركزية بدلاً من كتابة الكود هنا مباشرة ★
        // هذا يسمح بإعادة تهيئة الأنيميشن بسهولة عند العودة للشاشة بعد إيقافه
        setupRecyclerViewAnimator();

        mRecyclerView.seslSetFillBottomEnabled(false);
        mRecyclerView.seslSetLastRoundedCorner(false);
        mRecyclerView.seslSetFastScrollerEnabled(false);
        mRecyclerView.seslSetIndexTipEnabled(false);
        mRecyclerView.seslSetGoToTopEnabled(true);
        mRecyclerView.seslSetSmoothScrollEnabled(true);
    }

    /**
     * ★ دالة مركزية لتهيئة أنيميشن الـ RecyclerView ★
     * تُستدعى عند الإنشاء الأول وعند العودة للشاشة بعد إيقاف الأنيميشن.
     * سرعات متوازنة لتجنب العشوائية عند الكتابة السريعة في البحث.
     */
    private void setupRecyclerViewAnimator() {
        if (mRecyclerView == null) return;
        androidx.recyclerview.widget.DefaultItemAnimator animator =
            new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setAddDuration(150);
        animator.setRemoveDuration(150);
        animator.setMoveDuration(250);
        animator.setSupportsChangeAnimations(false);
        mRecyclerView.setItemAnimator(animator);
    }

    private void setupAppBarLayout() {
        if (getActivity() != null) {
            View drawer = getActivity().findViewById(R.id.drawer_layout);
            if (drawer instanceof DrawerLayout) {
                mDrawerLayout = (DrawerLayout) drawer;
                mAppBarLayout = mDrawerLayout.getAppBarLayout();
                if (mAppBarLayout != null) {
                    mAppBarLayout.addOnOffsetChangedListener(this);
                    mUIManager.setAppBarLayout(mAppBarLayout);
                }
            }
        }
    }

    private void showUnsupportedView() {
        if (getView() == null) return;
        View unsupportedView = getView().findViewById(R.id.unsupported_view);
        View mainContent     = getView().findViewById(R.id.main_content_layout);
        if (unsupportedView != null) unsupportedView.setVisibility(View.VISIBLE);
        if (mainContent != null)     mainContent.setVisibility(View.GONE);
    }

    /**
     * ★ يُغذّي الـ Adapter بالبيانات الخام بدون فرز مسبق.
     * SortedList داخل الـ Adapter يتولى الترتيب وتوليد الأنيميشن المناسب.
     */
    private void refreshAdapterData() {
        if (mCurrentFontsList.isEmpty()) {
            mSearchManager.updateFontsList(new ArrayList<>());
            if (mAdapter != null) {
                mAdapter.updateFilteredFonts(new ArrayList<>(), mSearchManager.getCurrentSearchQuery());
                mAdapter.updateSortOptionsOnly(
                    mSortManager.getCurrentSortType(),
                    mSortManager.isSortAscending()
                );
            }
            return;
        }

        // بناء قائمة FontFileInfo — بدون استدعاء sortFontsList ★
        List<FontFileInfo> rawFonts = new ArrayList<>();
        for (FontEntity font : mCurrentFontsList) {
            rawFonts.add(new FontFileInfo(
                font.getFileName(),
                font.getPath(),
                font.getSize(),
                font.getLastModified()
            ));
        }

        mSearchManager.updateFontsList(rawFonts);

        if (mAdapter != null) {
            mAdapter.setAllFontsInfo(convertEntitiesToSystemFontInfo(mCurrentFontsList));

            // SortedList يرتب القائمة تلقائياً حسب currentSortType/currentSortAscending
            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
            // تحديث الهيدر فقط ليعكس الحالة الحالية
            mAdapter.updateSortOptionsOnly(
                mSortManager.getCurrentSortType(),
                mSortManager.isSortAscending()
            );
        }

        // ★ الإصلاح الجذري لمشكلة القفز: استعادة موضع التمرير فقط عند أول وصول للبيانات
        // بعد إعادة البناء من الـ bundle (مثل إعادة الفتح بعد قتل العملية من النظام).
        //
        // هذه العلامة لا تُضبط إلا في restoreInstanceState()، وتُستهلك هنا مرة واحدة فقط.
        // استدعاءات recordFontAccess التي تُشغّل Room LiveData لن تجد العلامة true
        // لأنها تُستهلك قبل أن يتاح للمستخدم النقر على أي خط.
        //
        // حالة العودة من عارض الخطوط (onHiddenChanged → false) لا تحتاج هذه العلامة إطلاقاً؛
        // يكفيها mMainHandler.post() المباشر الموجود في onHiddenChanged.
        if (mNeedsScrollRestore) {
            mNeedsScrollRestore = false;
            mMainHandler.post(() -> {
                if (isAdded() && getView() != null) {
                    mUIManager.restoreRecyclerViewState();
                }
            });
        }
    }

    /**
     * تحويل كيانات قاعدة البيانات إلى نماذج SystemFontInfo لاستخدامها في الـ Adapter.
     *
     * ★ التعديل: إضافة info.setWeightWidthLabel() لتمرير وصف الوزن والعرض
     *   المخزَّن في قاعدة البيانات إلى SystemFontInfo، ومنه إلى SystemFontListAdapter
     *   ثم إلى SystemFontViewHolder للعرض في السطر الثاني.
     *   بدون هذا السطر يصل weightWidthLabel كـ null دائماً بغض النظر عن
     *   القيم المحفوظة في قاعدة البيانات.
     */
    private List<com.example.oneuiapp.fontlist.systemfont.SystemFontInfo> convertEntitiesToSystemFontInfo(
            List<FontEntity> entities) {
        List<com.example.oneuiapp.fontlist.systemfont.SystemFontInfo> result = new ArrayList<>();
        for (FontEntity entity : entities) {
            com.example.oneuiapp.fontlist.systemfont.SystemFontInfo info =
                new com.example.oneuiapp.fontlist.systemfont.SystemFontInfo(
                    entity.getFileName(),
                    entity.getPath(),
                    entity.getSize(),
                    entity.getLastModified(),
                    400,
                    0,
                    entity.getTtcIndex(),
                    null
                );
            if (entity.getRealName() != null && !entity.getRealName().isEmpty()) {
                info.setRealName(entity.getRealName());
            }
            // ★ تمرير وصف الوزن والعرض من FontEntity إلى SystemFontInfo ★
            // هذا يضمن وصول القيمة المخزَّنة في قاعدة البيانات إلى المحوّل والـ ViewHolder
            info.setWeightWidthLabel(entity.getWeightWidthLabel());
            result.add(info);
        }
        return result;
    }

    public void filterFonts(String query) {
        mSearchManager.filterFonts(query);
        if (mAdapter != null) {
            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
        }
    }

    public void resetFilter() {
        mSearchManager.resetFilter();
        if (mAdapter != null) {
            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mUIManager.saveRecyclerViewState();
        if (mUIManager.getRecyclerViewState() != null) {
            outState.putParcelable("recycler_state", mUIManager.getRecyclerViewState());
        }
        outState.putString("sort_type", mSortManager.getCurrentSortType().name());
        outState.putBoolean("sort_asc", mSortManager.isSortAscending());
        outState.putBoolean("is_first_load", mIsFirstLoad);
    }

    @Override
    public void onResume() {
        super.onResume();
        // ★ الإصلاح: تحديث العدد فقط إذا كان هذا الـ Fragment ظاهراً للمستخدم
        // هذا يمنع الكتابة فوق العدد الصحيح عند استئناف التطبيق ★
        if (!isHidden()) {
            updateMainActivityFontsCount(mCurrentFontsList.size());
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            // ★ 1. حفظ موضع التمرير فوراً قبل إخفاء الشاشة وقبل أي تحديث للـ Adapter ★
            mUIManager.saveRecyclerViewState();

            // ★ 2. إيقاف الأنيميشن فوراً لنزع قدرة القائمة على الحركة في الخلفية ★
            // هذا يضمن أن أي تحديثات تحدث في الخلفية (مثل إعادة الترتيب عند إغلاق البحث)
            // تُطبَّق بصمت تام دون أن يراها المستخدم عند العودة
            if (mRecyclerView != null) {
                mRecyclerView.setItemAnimator(null);
            }

            mSearchViewModel.deactivateSearch();
        } else {
            // ★ إعادة تفعيل اللمس وإعادة تفعيل الحارس لقبول النقرات مجدداً ★
            unblockTouch();

            // ★ إعادة رسم القائمة عند العودة لإظهار تمييز آخر خط تم فتحه ★
            if (mAdapter != null) mAdapter.smartUpdate();

            updateMainActivityFontsCount(mCurrentFontsList.size());

            // ★ 3. استعادة موضع التمرير بعد ظهور الشاشة مع تأجيل بـ post()
            // لضمان اكتمال layout قبل تطبيق الاستعادة.
            // ملاحظة: لا نضبط mNeedsScrollRestore هنا عمداً — هذا المسار يعتمد فقط
            // على هذا الـ post() المباشر. ضبط العلامة هنا كان سبب المشكلة السابقة:
            // كانت تبقى true حتى بعد Home ثم تُشغَّل خطأً عند أول recordFontAccess. ★
            mMainHandler.post(() -> mUIManager.restoreRecyclerViewState());

            // ★ 4. الضربة القاضية للأنيميشن: تأخير إعادته 100 ملي ثانية ★
            // هذا يضمن أن الـ RecyclerView قد رسم العناصر في مواضعها النهائية بدون حركة،
            // وبعد ذلك فقط نعيد الأنيميشن للاستخدام الطبيعي
            if (mRecyclerView != null) {
                mRecyclerView.postDelayed(() -> {
                    if (isAdded() && !isHidden()) {
                        setupRecyclerViewAnimator();
                    }
                }, 100);
            }
        }
    }

    private void updateMainActivityFontsCount(int count) {
        // ★ الإصلاح (المشكلتان 1 و 3): تمرير الفهرس 3 لتمييز هذا الفراجمنت عن فراجمنت المجلد ★
        // يمنع هذا الفراجمنت من الكتابة فوق عدد المجلد المحلي عند إعادة بناء النشاط،
        // ويضمن تحديث العنوان الفرعي فقط عندما تكون قائمة النظام هي الظاهرة فعلاً
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(3, count);
        }
    }

    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mAppBarLayout != null) {
            mAppBarLayout.removeOnOffsetChangedListener(this);
            mAppBarLayout = null;
        }
        mDrawerLayout = null;
        mRecyclerView = null;
        mAdapter      = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mFontSelectedListener = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMainHandler != null) mMainHandler.removeCallbacksAndMessages(null);
        if (mExecutor != null)    mExecutor.shutdown();
    }
}
