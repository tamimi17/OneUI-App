package com.example.oneuiapp.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
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

import dev.oneuiproject.oneui.widget.Toast;
import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.example.oneuiapp.activity.MainActivity;
import com.example.oneuiapp.dialog.FontActionDialogs;
import com.example.oneuiapp.fontlist.FontFileInfo;
import com.example.oneuiapp.fontlist.localfont.LocalFontSelectionManager;
import com.example.oneuiapp.fontlist.localfont.LocalFontPermissionManager;
import com.example.oneuiapp.fontlist.localfont.LocalFontDirectoryPickerManager;
import com.example.oneuiapp.fontlist.FontUIStateManager;
import com.example.oneuiapp.fontlist.search.FontTextHighlighter;
import com.example.oneuiapp.fontlist.FontSortManager;
import com.example.oneuiapp.fontlist.search.FontSearchManager;
import com.example.oneuiapp.fontlist.localfont.LocalFontCache;
import com.example.oneuiapp.fontlist.adapter.LocalFontListAdapter;
import com.example.oneuiapp.fontlist.adapter.FontItemDecoration;
import com.example.oneuiapp.R;
import com.example.oneuiapp.ui.widget.SortByItemLayout;
import com.example.oneuiapp.viewmodel.LocalFontListViewModel;
import com.example.oneuiapp.viewmodel.SearchViewModel;
import com.example.oneuiapp.viewmodel.SettingsViewModel;

/**
 * LocalFontListFragment — محدث ليفوّض الفرز بالكامل إلى SortedList داخل LocalFontListAdapter.
 * ★ لم يعد هذا الـ Fragment يستدعي FontSortManager.sortFontsList() ★
 * ★ عند تغيير الفرز → mAdapter.setSortOptions() → أنيميشن مباشر ★
 * ★ عند وصول بيانات جديدة → mAdapter.updateFilteredFonts() → SortedList يرتبها تلقائياً ★
 *
 * ★ يستخدم FontSortManager(context, false) → مفاتيح DataStore خاصة بالمجلد المحلي فقط ★
 *
 * ★ التعديل: تحديث OnFontSelectedListener ليشمل weightWidthLabel كمعامل خامس ★
 *   لتمريره مباشرةً إلى NavManager ثم FontViewerFragment دون إعادة استخراجه،
 *   إذ أن الوزن مستخرج مسبقاً وموجود في بيانات القائمة.
 *
 * ★ الإضافة: دعم المفضلة بثلاثة عناصر:
 *   1. setFavoriteStatusProvider → يُعرض أيقونة النجمة بجانب العناصر المفضلة
 *   2. setFavoriteStatusChecker → يُطبَّق منطق Samsung Notes في وضع التحديد
 *   3. onFavoriteRequested + handleFavoriteAction → تنفيذ الإضافة/الإزالة الفعلية ★
 *
 * ★ الإصلاح (المشكلة 2): استدعاء notifyAllFavoritesChanged() داخل مراقب getFontsLiveData()
 *   بعد تجديد mCurrentFontsList مباشرةً، لضمان قراءة FavoriteStatusProvider للقيم المحدّثة.
 *   كان الاستدعاء في callback handleFavoriteAction يسبق وصول LiveData، فتظهر النجمة
 *   فقط بعد Scroll بسبب قراءة القيم القديمة. ★
 */
public class LocalFontListFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "LocalFontListFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;
    private LocalFontListAdapter mAdapter;
    private OnFontSelectedListener mFontSelectedListener;
    private Handler mMainHandler;
    private ExecutorService mExecutor;
    private AppBarLayout mAppBarLayout;
    private DrawerLayout mDrawerLayout;
    private SortByItemLayout mSortBar;

    private LocalFontPermissionManager mLocalFontPermissionManager;
    private LocalFontDirectoryPickerManager mLocalFontDirectoryPickerManager;
    private FontSearchManager mSearchManager;
    private FontSortManager mSortManager;
    private FontUIStateManager mUIManager;

    private LocalFontSelectionManager mSelectionManager;

    private LocalFontListViewModel mViewModel;
    private SearchViewModel mSearchViewModel;
    private SettingsViewModel mSettingsViewModel;
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mCurrentFontsList = new ArrayList<>();

    private boolean mIsFirstLoad = true;

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

    /**
     * ★ التعديل: إضافة weightWidthLabel كمعامل خامس ★
     * يحمل وصف الوزن/العرض الجاهز من القائمة لتمريره لـ NavManager ثم FontViewerFragment.
     */
    public interface OnFontSelectedListener {
        void onFontSelected(String fontPath, String realName, String fileName,
                            int ttcIndex, String weightWidthLabel);
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

        mLocalFontPermissionManager      = new LocalFontPermissionManager(this);
        mLocalFontDirectoryPickerManager = new LocalFontDirectoryPickerManager(this);
        mSearchManager          = new FontSearchManager();

        // ★ false = خطوط المجلد المحلي → يقرأ/يكتب KEY_SORT_TYPE و KEY_SORT_ASCENDING ★
        // هذا يمنع التداخل مع إعدادات فرز خطوط النظام ويحل مشكلة التجمد نهائياً
        mSortManager = new FontSortManager(mContext, false);

        mUIManager = new FontUIStateManager(mContext);

        setupDirectoryPickerListener();
        setupPermissionListener();
        setupSearchListener();
        setupSortListener();
    }

    private void setupDirectoryPickerListener() {
        mLocalFontDirectoryPickerManager.setDirectorySelectionListener(new LocalFontDirectoryPickerManager.DirectorySelectionListener() {
            public void onDirectorySelected(String directoryPath) {
                if (mViewModel != null) {
                    mViewModel.saveFolderPath(directoryPath);
                    mViewModel.loadFontsFromPath(directoryPath);
                }
                mUIManager.updateUIVisibility(true);
                Toast.makeText(mContext, "Folder selected: " + directoryPath, Toast.LENGTH_SHORT).show();
            }

            public void onDirectorySelectionCancelled() {
                Toast.makeText(mContext, "Folder selection cancelled", Toast.LENGTH_SHORT).show();
            }

            public void onDirectorySelectionError(Exception e) {
                Toast.makeText(mContext, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupPermissionListener() {
        mLocalFontPermissionManager.setPermissionResultListener(new LocalFontPermissionManager.PermissionResultListener() {
            public void onPermissionGranted() {
                mLocalFontDirectoryPickerManager.openDirectoryPicker();
            }

            public void onPermissionDenied() {
                Toast.makeText(mContext, getString(R.string.font_fragment_permission_denied), Toast.LENGTH_LONG).show();
            }

            public void onManageStoragePermissionRequired() {
                Toast.makeText(mContext, "Storage permission required. Please grant access.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupSearchListener() {
        // ★ المستمع يحدّث حالة الواجهة فقط — تحديث الـ Adapter يتم من المراقب ★
        mSearchManager.setSearchResultListener((count, empty) -> {
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive());
        });
    }

    private void setupSortListener() {
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
        setupViewModelObservers();

        if (state != null) {
            restoreInstanceState(state);
        }
    }

    private void initializeViewModels() {
        mViewModel         = new ViewModelProvider(this).get(LocalFontListViewModel.class);
        mSearchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
    }

    private void setupViewModelObservers() {
        mViewModel.getFontsLiveData().observe(this, fonts -> {
            if (fonts != null) {
                boolean wasEmpty = mCurrentFontsList.isEmpty();
                mCurrentFontsList = new ArrayList<>(fonts);

                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(fonts);

                    // ★ الإصلاح (المشكلة 2): إجبار الـ Adapter على تحديث أيقونات النجمة
                    //   بعد وصول البيانات المحدّثة من Room مباشرةً.
                    //
                    // السبب الجذري للمشكلة:
                    //   عند إضافة خط للمفضلة، يُستدعى notifyAllFavoritesChanged() في
                    //   callback handleFavoriteAction قبل أن تُحدِّث Room LiveData
                    //   mCurrentFontsList. نتيجةً لذلك تقرأ FavoriteStatusProvider
                    //   القيم القديمة (isFavorite = false) فلا تظهر النجمة.
                    //
                    // الحل: إعادة استدعاء notifyAllFavoritesChanged() هنا بعد أن
                    //   تجدّد mCurrentFontsList بالبيانات الصحيحة من Room. ★
                    mAdapter.notifyAllFavoritesChanged();
                }

                // ★ تحديث البيانات — SortedList يرتبها تلقائياً حسب معيار الفرز الحالي ★
                refreshAdapterData();

                if (!wasEmpty) {
                    preloadNewlyAddedFonts(fonts);
                }

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
                mAdapter.smartUpdate();
                Log.d(TAG, "Font preview setting changed: " + enabled);
            }
        });
    }

    private void restoreInstanceState(@NonNull Bundle state) {
        mUIManager.setRecyclerViewState(state.getParcelable("recycler_state"));
        String sortType = state.getString("sort_type");
        if (sortType != null) {
            try {
                mUIManager.saveSortState(
                    SortByItemLayout.SortType.valueOf(sortType),
                    state.getBoolean("sort_asc", true)
                );
            } catch (Exception e) {
                Log.e(TAG, "Error restoring sort state", e);
            }
        }
        mIsFirstLoad = state.getBoolean("is_first_load", true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_local_font_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        initializeViews(view);
        setupRecyclerView();
        setupDrawerLayout();
        setupFolderButton(view);

        initializeSelectionManager();

        // ★ الإصلاح الجوهري: حُذف بلوك hasSavedSortState ★
        // السبب: كان يعمل بعد وصول البيانات من LiveData مما يُسبب "سباق زمني".
        // الحل: تهيئة الفرز داخل setupRecyclerView() مباشرةً بعد setAdapter()
        // لضمان جهوزية الـ Adapter قبل أي وصول للبيانات.

        if (mIsFirstLoad && mViewModel.hasSavedFolder()) {
            mViewModel.loadFonts();
            mIsFirstLoad = false;
        }
    }

    private void initializeViews(@NonNull View view) {
        mRecyclerView = view.findViewById(R.id.font_recycler_view);
        mSortBar = null;

        mUIManager.setViews(
            view.findViewById(R.id.select_folder_container),
            view.findViewById(R.id.main_content_layout),
            view.findViewById(R.id.empty_view),
            view.findViewById(R.id.empty_text),
            mRecyclerView
        );
        // ★ ربط عنوان الحالة الفارغة — يُخفى تلقائياً عند البحث بلا نتائج ★
        mUIManager.setEmptyTitleView(view.findViewById(R.id.empty_title));

        mUIManager.updateUIVisibility(mViewModel.hasSavedFolder());
    }

    private void setupDrawerLayout() {
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

    private void setupFolderButton(@NonNull View view) {
        View folderBtn = view.findViewById(R.id.select_folder_button);
        if (folderBtn != null) {
            folderBtn.setOnClickListener(v -> {
                if (mLocalFontPermissionManager.hasRequiredPermissions()) {
                    mLocalFontDirectoryPickerManager.openDirectoryPicker();
                } else {
                    mLocalFontPermissionManager.requestPermissions();
                }
            });
        }
    }

    private void setupRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        mAdapter = new LocalFontListAdapter(mContext, mExecutor);

        // ★ التعديل: استقبال weightWidthLabel كمعامل خامس وتمريره إلى mFontSelectedListener ★
        mAdapter.setFontClickListener((fontPath, realName, fileName, ttcIndex, weightWidthLabel) -> {
            if (mFontSelectedListener != null) {
                mFontSelectedListener.onFontSelected(fontPath, realName, fileName,
                                                     ttcIndex, weightWidthLabel);
            }
        });

        // ★ عند الضغط على شريط الفرز: حفظ التفضيل في DataStore عبر SortManager
        // SortManager يُشعر مستمعه (setupSortListener) الذي يستدعي mAdapter.setSortOptions → أنيميشن ★
        mAdapter.setSortChangeListener((type, asc) -> {
            mSortManager.setSortOptions(type, asc);
        });

        // ★ FavoriteStatusProvider: يُزوّد الـ Adapter بحالة المفضلة لكل مسار خط ★
        // يُستخدم لعرض أيقونة النجمة الصفراء (ic_favorite) بجانب العناصر المفضلة
        // في قائمة الخطوط المحلية بجانب ظهورها في قائمة المفضلة.
        // mCurrentFontsList تحتوي على isFavorite المُحدَّث من Room LiveData تلقائياً.
        mAdapter.setFavoriteStatusProvider(fontPath -> {
            for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFontsList) {
                if (font.getPath().equals(fontPath)) {
                    return font.isFavorite();
                }
            }
            return false;
        });

        mRecyclerView.setAdapter(mAdapter);

        // ★★★ الإصلاح السحري: إخبار الـ Adapter بنوع الفرز المحفوظ قبل أن يستلم أي بيانات ★★★
        // يضمن هذا أنه عندما تصل البيانات من LiveData، سيرتبها الـ SortedList مباشرةً
        // بالمعيار الصحيح بدون أي سباق زمني بين وصول البيانات وتهيئة الفرز
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

    private void initializeSelectionManager() {
        if (mDrawerLayout == null || mAdapter == null || mRecyclerView == null) return;

        mSelectionManager = new LocalFontSelectionManager(
            requireActivity(),
            mDrawerLayout,
            mAdapter,
            mRecyclerView,
            mSortBar
        );

        // ★ FavoriteStatusChecker: يُزوّد SelectionManager بحالة المفضلة لكل موضع ★
        // يُطبَّق منطق Samsung Notes بناءً على هذه القيم:
        //   - كل العناصر المحددة مفضلة   → يُعرض "إزاله من المفضله"
        //   - مختلطة أو كلها غير مفضلة  → يُعرض "إضافه إلى المفضله"
        mSelectionManager.setFavoriteStatusChecker(position -> {
            String path = mAdapter.getFilePath(position);
            if (path == null) return false;
            for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFontsList) {
                if (font.getPath().equals(path)) {
                    return font.isFavorite();
                }
            }
            return false;
        });

        mAdapter.setSelectionListener(new LocalFontListAdapter.OnSelectionListener() {
            @Override
            public void onStartSelection(int position) {
                mSelectionManager.setSelecting(true);
                mSelectionManager.toggleSelection(position);
            }

            @Override
            public void onToggleSelection(int position) {
                mSelectionManager.toggleSelection(position);
            }
        });

        mSelectionManager.setActionListener(new LocalFontSelectionManager.SelectionActionListener() {
            @Override
            public void onRenameRequested(int position) {
                handleRename(position);
            }

            @Override
            public void onDeleteRequested(List<Integer> positions) {
                handleDelete(positions);
            }

            /**
             * ★ إجراء المفضلة — يُستدعى من SelectionManager بعد تحديد نوع العملية ★
             * addToFavorites يأتي جاهزاً من resolveFavoriteAction() في SelectionManager
             * وفق منطق Samsung Notes المُطبَّق هناك.
             */
            @Override
            public void onFavoriteRequested(List<Integer> positions, boolean addToFavorites) {
                handleFavoriteAction(positions, addToFavorites);
            }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(
            getViewLifecycleOwner(),
            mSelectionManager.getOnBackPressedCallback()
        );
    }

    private void handleRename(int position) {
        String path = mAdapter.getFilePath(position);
        if (path == null) return;

        FontActionDialogs.showRenameDialog(mContext, path, (oldPath, newFileName) -> {
            boolean success = mViewModel.renameFontInMemory(oldPath, newFileName);

            if (success) {
                mSelectionManager.setSelecting(false);

                mMainHandler.postDelayed(() -> {
                    String newPath = oldPath.substring(0, oldPath.lastIndexOf("/") + 1) + newFileName;
                    int newPosition = mAdapter.findPositionByPath(newPath);
                    if (newPosition != -1 && mRecyclerView != null) {
                        mRecyclerView.smoothScrollToPosition(newPosition);
                    }
                }, 300);

                Toast.makeText(mContext, R.string.success_renamed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, R.string.error_rename_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleDelete(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        // ★ الإصلاح: طرح 2 (الهيدر + الفوتر) للحصول على العدد الحقيقي للخطوط ★
        int totalCount = mAdapter.getItemCount() - 2;

        FontActionDialogs.showDeleteDialog(mContext, positions.size(), totalCount, () -> {
            List<String> pathsToDelete = new ArrayList<>();
            for (int position : positions) {
                String path = mAdapter.getFilePath(position);
                if (path != null) pathsToDelete.add(path);
            }

            mSelectionManager.setSelecting(false);

            mViewModel.deleteFontsInMemory(pathsToDelete, () -> {
                String message = pathsToDelete.size() == 1
                    ? getString(R.string.success_deleted)
                    : getString(R.string.success_deleted_multiple, pathsToDelete.size());
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            });
        });
    }

    /**
     * ★ إجراء المفضلة في قائمة الخطوط المحلية ★
     *
     * يُطبّق الإضافة أو الإزالة من المفضلة على العناصر المحددة.
     * Room LiveData يُحدَّث تلقائياً → مراقب getFontsLiveData() يُجدّد mCurrentFontsList
     * → notifyAllFavoritesChanged() يُحدّث أيقونات النجمة بالبيانات الصحيحة.
     *
     * ملاحظة للمطوّر: الاستدعاء الاحترازي لـ notifyAllFavoritesChanged() في callback
     * هذه الدالة يُحدِّث الأيقونات بسرعة، لكنه قد يقرأ قيماً قديمة إذا سبق وصول LiveData.
     * الاستدعاء الثاني في مراقب getFontsLiveData() هو الضمان النهائي للصحة.
     *
     * @param positions     مواضع العناصر المحددة في الـ Adapter
     * @param addToFavorites true = إضافة إلى المفضلة، false = إزالة منها
     */
    private void handleFavoriteAction(List<Integer> positions, boolean addToFavorites) {
        if (positions == null || positions.isEmpty()) return;

        // جمع مسارات العناصر المحددة
        List<String> paths = new ArrayList<>();
        for (int position : positions) {
            String path = mAdapter.getFilePath(position);
            if (path != null) paths.add(path);
        }

        if (paths.isEmpty()) return;

        mSelectionManager.setSelecting(false);

        // ★ تحديث قاعدة البيانات في الخلفية عبر ViewModel ★
        // toggleFavoritesBatch يستدعي updateFavoriteStatusBatch في Repository
        // الذي يُحدّث Room → LiveData يُحدَّث تلقائياً → مراقب getFontsLiveData()
        // يُجدّد mCurrentFontsList → notifyAllFavoritesChanged() يُحدّث الأيقونات بصحة تامة.
        mViewModel.toggleFavoritesBatch(paths, addToFavorites, () -> {
            // ★ استدعاء احترازي: يُحدّث الأيقونات فوراً إن كانت LiveData قد وصلت ★
            // في حالة تأخر LiveData، سيُكمل المراقب في setupViewModelObservers() العمل.
            if (mAdapter != null) {
                mAdapter.notifyAllFavoritesChanged();
            }
        });
    }

    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSelectionManager != null) mSelectionManager.refreshActionMode();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        mUIManager.saveRecyclerViewState();
        if (mUIManager.getRecyclerViewState() != null) {
            out.putParcelable("recycler_state", mUIManager.getRecyclerViewState());
        }
        out.putString("sort_type", mSortManager.getCurrentSortType().name());
        out.putBoolean("sort_asc", mSortManager.isSortAscending());
        out.putBoolean("is_first_load", mIsFirstLoad);
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
            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                mSelectionManager.setSelecting(false);
            }
        } else {
            // ★ إعادة تفعيل اللمس وإعادة تفعيل الحارس لقبول النقرات مجدداً ★
            unblockTouch();

            // ★ إعادة رسم القائمة عند العودة لإظهار تمييز آخر خط تم فتحه ★
            if (mAdapter != null) mAdapter.smartUpdate();

            updateMainActivityFontsCount(mCurrentFontsList.size());

            // ★ 3. استعادة موضع التمرير بعد ظهور الشاشة مع تأجيل بـ post()
            // لضمان اكتمال layout قبل تطبيق الاستعادة ★
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (!mLocalFontDirectoryPickerManager.handleActivityResult(requestCode, resultCode, data)) {
            if (mLocalFontPermissionManager.handleActivityResult(requestCode)) {
                mLocalFontDirectoryPickerManager.openDirectoryPicker();
            }
        }
    }

    private void updateMainActivityFontsCount(int count) {
        // ★ الإصلاح 1 (المشكلة 4): لا تُحدّث العنوان الفرعي إذا كان وضع التحديد نشطاً ★
        // هذا يمنع ظهور عدد الخطوط في العنوان الفرعي أثناء وضع التحديد عند العودة للتطبيق
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        // ★ الإصلاح 2 (المشكلة 1): تمرير الفهرس 2 لتمييز هذا الفراجمنت عن فراجمنت النظام ★
        // يمنع فراجمنت النظام من الكتابة فوق عدد المجلد المحلي بعد تغيير الثيم أو اللغة
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(2, count);
        }
    }

    private void preloadNewlyAddedFonts(List<LocalFontListViewModel.FontFileInfoWithMetadata> newFonts) {
        if (newFonts == null || newFonts.isEmpty()) return;

        mExecutor.execute(() -> {
            LocalFontCache cache = LocalFontCache.getInstance();
            List<String> newPathsToPreload = new ArrayList<>();

            for (LocalFontListViewModel.FontFileInfoWithMetadata font : newFonts) {
                String path = font.getPath();
                if (!cache.wasLoadedBefore(path)) {
                    Typeface typeface = cache.getTypeface(path);
                    if (typeface != null) newPathsToPreload.add(path);
                }
            }

            if (!newPathsToPreload.isEmpty()) {
                Log.d(TAG, "Preloaded " + newPathsToPreload.size() + " new fonts");
                mMainHandler.post(() -> {
                    if (mAdapter != null && isAdded()) {
                        mAdapter.updateFilteredFonts(
                            mSearchManager.getFilteredFonts(),
                            mSearchManager.getCurrentSearchQuery()
                        );
                    }
                });
            }
        });
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
        for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFontsList) {
            rawFonts.add(new FontFileInfo(
                font.getName(),
                font.getPath(),
                font.getSize(),
                font.getLastModified()
            ));
        }

        mSearchManager.updateFontsList(rawFonts);

        if (mAdapter != null) {
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

        mMainHandler.post(() -> {
            if (isAdded() && getView() != null) {
                mUIManager.restoreRecyclerViewState();
            }
        });
    }

    public void filterFonts(String query) {
        mSearchViewModel.setSearchQuery(query);
        mSearchViewModel.activateSearch();
    }

    public void resetFilter() {
        mSearchViewModel.deactivateSearch();
    }

    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        mLocalFontPermissionManager.handlePermissionResult(code, results);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mSelectionManager != null) {
            mSelectionManager.cleanup();
            mSelectionManager = null;
        }

        if (mAppBarLayout != null) {
            mAppBarLayout.removeOnOffsetChangedListener(this);
            mAppBarLayout = null;
        }

        mDrawerLayout = null;
        mRecyclerView = null;
        mAdapter      = null;
        mSortBar      = null;
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
