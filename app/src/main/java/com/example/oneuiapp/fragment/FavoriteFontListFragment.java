package com.example.oneuiapp.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
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

import com.example.oneuiapp.R;
import com.example.oneuiapp.activity.MainActivity;
import com.example.oneuiapp.dialog.FontActionDialogs;
import com.example.oneuiapp.fontlist.FontFileInfo;
import com.example.oneuiapp.fontlist.FontSortManager;
import com.example.oneuiapp.fontlist.FontUIStateManager;
import com.example.oneuiapp.fontlist.adapter.FontItemDecoration;
import com.example.oneuiapp.fontlist.adapter.LocalFontListAdapter;
import com.example.oneuiapp.fontlist.localfont.LocalFontCache;
import com.example.oneuiapp.fontlist.localfont.LocalFontSelectionManager;
import com.example.oneuiapp.fontlist.search.FontSearchManager;
import com.example.oneuiapp.ui.widget.SortByItemLayout;
import com.example.oneuiapp.viewmodel.LocalFontListViewModel;
import com.example.oneuiapp.viewmodel.SearchViewModel;
import com.example.oneuiapp.viewmodel.SettingsViewModel;

/**
 * FavoriteFontListFragment — قائمة الخطوط المفضلة
 *
 * ★ مبني هيكلياً على LocalFontListFragment مع التعديلات التالية:
 *   1. لا يوجد اختيار مجلد (لا Permission Manager، لا Directory Picker)
 *   2. يراقب getFavoritesLiveData() بدلاً من getFontsLiveData()
 *   3. يستخدم FontSortManager بمعامل "FAVORITES" لمفاتيح DataStore المستقلة
 *      (يتطلب تحديث FontSortManager لدعم معرّف نصي ثالث بجانب true/false)
 *   4. FavoriteStatusProvider يُعيد true دائماً (كل عناصر المفضلة هي مفضلة)
 *   5. FavoriteStatusChecker يُعيد true دائماً (نفس السبب)
 *      → resolveFavoriteAction() يُعيد true → يُعرض "إزاله من المفضله" دائماً
 *   6. onFavoriteRequested يستدعي toggleFavoritesBatch بـ false (إزالة)
 *   7. العناصر تختفي تلقائياً من القائمة عند إزالتها من المفضلة (Room LiveData reactive)
 *   8. فهرس الـ Fragment في MainActivity هو 4
 *
 * ★ الإصلاح (المشكلة 3): استدعاء mUIManager.setDefaultEmptyMessage(R.string.favorites_empty_message)
 *   مباشرةً بعد إنشاء FontUIStateManager في onAttach()، لضمان عرض رسالة المفضلة الصحيحة
 *   بدلاً من رسالة المجلد المحلي (font_fragment_empty_message) عند فراغ القائمة. ★
 *
 * ★ الإصلاح (مشكلة البحث): استدعاء mUIManager.setEmptyTitleView(empty_title) في initializeViews()
 *   لإخبار FontUIStateManager بوجود العنوان، مما يُتيح له إخفاءه تلقائياً عند البحث بلا نتائج.
 *   بدون هذا السطر، كان العنوان يظهر مع رسالة "لا توجد نتائج" معاً بدلاً من الرسالة وحدها. ★
 *
 * ★ الإضافة (تمييز الحالتين): استدعاء mUIManager.setNoResultsTextView(no_results_text)
 *   في initializeViews() لربط TextView مستقل برسالة "لا توجد نتائج"، مما يُتيح تخصيص
 *   لونها وحجمها من الـ layout بشكل مستقل تماماً عن رسالة الحالة الفارغة الحقيقية. ★
 *
 * ★ ملاحظات للمطوّر:
 *   - يجب أن تُنفّذ MainActivity واجهة FavoriteFontListFragment.OnFontSelectedListener
 *     وإضافتها إلى قائمة implements في تعريف الكلاس.
 *   - يجب إضافة دالة setFavoriteIndicator(boolean) إلى LocalFontViewHolder
 *     (مُشار إليها في LocalFontListAdapter عبر PAYLOAD_UPDATE_FAVORITE).
 *   - يتطلب FontSortManager دعم معرّف نصي "FAVORITES" لقراءة/كتابة
 *     KEY_FAVORITES_SORT_TYPE و KEY_FAVORITES_SORT_ASCENDING من SettingsDataStore.
 */
public class FavoriteFontListFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "FavoriteFontListFragment";

    // ★ فهرس هذا الـ Fragment في قائمة mFragments بـ MainActivity ★
    private static final int FRAGMENT_INDEX = 4;

    private Context mContext;
    private RecyclerView mRecyclerView;
    private LocalFontListAdapter mAdapter;
    private OnFontSelectedListener mFontSelectedListener;
    private Handler mMainHandler;
    private ExecutorService mExecutor;
    private AppBarLayout mAppBarLayout;
    private DrawerLayout mDrawerLayout;

    // ★ لا يوجد SortByItemLayout منفصل — الهيدر داخل الـ Adapter يتولى ذلك ★
    private FontSearchManager mSearchManager;
    private FontSortManager mSortManager;
    private FontUIStateManager mUIManager;
    private LocalFontSelectionManager mSelectionManager;

    private LocalFontListViewModel mViewModel;
    private SearchViewModel mSearchViewModel;
    private SettingsViewModel mSettingsViewModel;

    // ★ القائمة الحالية للخطوط المفضلة — تُحدَّث من favoritesLiveData ★
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mCurrentFavoritesList = new ArrayList<>();

    // ★ حالة التمرير المحفوظة قبل تفعيل البحث — لاستعادة الموضع عند إغلاق البحث ★
    // يُحفظ في filterFonts() أول مرة فقط (قبل تفعيل البحث)، ويُستعاد في مراقب
    // SearchQueryLiveData عندما يصبح الاستعلام فارغاً بعد انتهاء تحديث الـ Adapter.
    private Parcelable mPreSearchScrollState;

    // ─────────────────────────────────────────────────────────
    // ★ حاجب اللمس — يُستدعى من NavManager عند الانتقال لعارض الخطوط ★
    // ─────────────────────────────────────────────────────────
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

    // ════════════════════════════════════════════════════════════
    // ★ واجهة الإشعار عند اختيار خط ★
    // يجب أن تُنفّذها MainActivity (إضافتها إلى قائمة implements)
    // ════════════════════════════════════════════════════════════

    /**
     * مطابقة لـ LocalFontListFragment.OnFontSelectedListener
     * يُمرَّر weightWidthLabel لتجنب إعادة استخراجه في NavManager أو FontViewerFragment
     */
    public interface OnFontSelectedListener {
        void onFontSelected(String fontPath, String realName, String fileName,
                            int ttcIndex, String weightWidthLabel);
    }

    // ════════════════════════════════════════════════════════════
    // دوال التحكم في اللمس — تُستدعى من NavManager
    // ════════════════════════════════════════════════════════════

    /** تعطيل اللمس فوراً عند النقر على خط للانتقال لعارض الخطوط */
    public void blockTouch() {
        if (mRecyclerView != null) {
            mRecyclerView.removeOnItemTouchListener(mTouchBlocker);
            mRecyclerView.addOnItemTouchListener(mTouchBlocker);
        }
    }

    /** تفعيل اللمس عند العودة لقائمة المفضلة من عارض الخطوط */
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

    /**
     * حفظ آخر خط مفتوح وتمييزه بالأزرق عند العودة للقائمة.
     * يُستدعى من NavManager بعد اكتمال أنيميشن الانتقال.
     */
    public void saveAndHighlight(String path) {
        if (mAdapter != null) {
            mAdapter.saveLastOpenedAndUpdate(path);
        }
    }

    // ════════════════════════════════════════════════════════════
    // دورة حياة الـ Fragment
    // ════════════════════════════════════════════════════════════

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;

        // ★ ربط مستمع اختيار الخط بـ MainActivity ★
        if (context instanceof OnFontSelectedListener) {
            mFontSelectedListener = (OnFontSelectedListener) context;
        }

        // ★ لا يوجد Permission Manager أو Directory Picker في المفضلة ★

        mSearchManager = new FontSearchManager();

        // ★ "FAVORITES" يُخبر FontSortManager باستخدام مفاتيح DataStore الخاصة بالمفضلة:
        //   KEY_FAVORITES_SORT_TYPE و KEY_FAVORITES_SORT_ASCENDING
        //   (يتطلب تحديث FontSortManager لدعم هذا المعرّف النصي) ★
        mSortManager = new FontSortManager(mContext, "FAVORITES");

        mUIManager = new FontUIStateManager(mContext);

        // ★ الإصلاح (المشكلة 3): تخصيص رسالة الشاشة الفارغة لقائمة المفضلة ★
        // بدون هذا السطر، كانت FontUIStateManager تعرض font_fragment_empty_message
        // (رسالة المجلد المحلي) عند فراغ قائمة المفضلة — وهي رسالة غير صحيحة السياق.
        // setDefaultEmptyMessage() يُغيّر defaultEmptyMessageResId المُستخدَم في showEmptyView().
        mUIManager.setDefaultEmptyMessage(R.string.favorites_empty_message);

        setupSearchListener();
        setupSortListener();
    }

    private void setupSearchListener() {
        // ★ المستمع يُحدّث حالة الواجهة فقط — تحديث الـ Adapter يتم من المراقب ★
        mSearchManager.setSearchResultListener((count, empty) -> {
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive());
        });
    }

    private void setupSortListener() {
        // ★ عند تغيير الفرز: setSortOptions → SortedList يعيد الترتيب بأنيميشن ★
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
    }

    private void initializeViewModels() {
        // ★ نفس ViewModel المستخدم في LocalFontListFragment — يشاركان البيانات
        //   لضمان التزامن الفوري بين القائمتين عند تغيير حالة المفضلة ★
        mViewModel         = new ViewModelProvider(this).get(LocalFontListViewModel.class);
        mSearchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
    }

    private void setupViewModelObservers() {
        // ★ مراقبة قائمة المفضلة — تتحدث تلقائياً عند إضافة أو إزالة خط من المفضلة ★
        mViewModel.getFavoritesLiveData().observe(this, favorites -> {
            if (favorites != null) {
                mCurrentFavoritesList = new ArrayList<>(favorites);

                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(favorites);
                }

                refreshAdapterData();
                updateMainActivityFontsCount(favorites.size());
            }
        });

        // ★ مراقبة نص البحث — يُفلتر قائمة المفضلة في الذاكرة ★
        mSearchViewModel.getSearchQueryLiveData().observe(this, query -> {
            if (query != null) {
                mSearchManager.filterFonts(query);
                if (mAdapter != null) {
                    mAdapter.updateFilteredFonts(
                        mSearchManager.getFilteredFonts(),
                        mSearchManager.getCurrentSearchQuery()
                    );
                }

                // ★ الإصلاح (حفظ موضع التمرير عند البحث):
                //   عندما يصبح نص البحث فارغاً (إغلاق البحث) واستعدنا حالة التمرير المحفوظة،
                //   نؤخّر الاستعادة بـ post() لضمان اكتمال رسم الـ Adapter أولاً.
                //   الاستعادة هنا (داخل مراقب LiveData) وليس في resetFilter() مباشرةً،
                //   لأن تحديث الـ Adapter يتم بشكل غير متزامن عبر LiveData. ★
                if (query.isEmpty() && mPreSearchScrollState != null && mRecyclerView != null) {
                    final Parcelable stateToRestore = mPreSearchScrollState;
                    mPreSearchScrollState = null;
                    mRecyclerView.post(() -> {
                        LinearLayoutManager lm =
                            (LinearLayoutManager) mRecyclerView.getLayoutManager();
                        if (lm != null) lm.onRestoreInstanceState(stateToRestore);
                    });
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_favorite_font_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        initializeViews(view);
        setupRecyclerView();
        setupDrawerLayout();
        initializeSelectionManager();
    }

    private void initializeViews(@NonNull View view) {
        mRecyclerView = view.findViewById(R.id.font_recycler_view);

        // ★ لا يوجد select_folder_container في المفضلة ★
        // main_content_layout يكون مرئياً دائماً (قد يُعرض الـ empty_view بداخله)
        mUIManager.setViews(
            null,                                        // لا يوجد select_folder_container
            view.findViewById(R.id.main_content_layout),
            view.findViewById(R.id.empty_view),
            view.findViewById(R.id.empty_text),
            mRecyclerView
        );

        // ★ الإصلاح (مشكلة البحث): ربط عنوان الحالة الفارغة بـ FontUIStateManager ★
        // بدون هذا السطر، لا تعلم FontUIStateManager بوجود empty_title فلا تُخفيه
        // عند البحث بلا نتائج، فيظهر العنوان مع رسالة "لا توجد نتائج" معاً.
        // بعد هذا السطر، يُخفى العنوان تلقائياً عند البحث ويظهر فقط رسالة "لا توجد نتائج". ★
        mUIManager.setEmptyTitleView(view.findViewById(R.id.empty_title));

        // ★ الإضافة (تمييز الحالتين): ربط رسالة البحث بلا نتائج المستقلة ★
        // يُتيح تخصيص لون no_results_text وحجمه من الـ layout بشكل مستقل تماماً
        // عن empty_text، لأن كلًّا منهما يخدم سياقاً مختلفاً من منظور تجربة المستخدم.
        mUIManager.setNoResultsTextView(view.findViewById(R.id.no_results_text));

        // ★ قائمة المفضلة دائماً في وضع العرض (لا يوجد وضع "اختيار مجلد") ★
        mUIManager.updateUIVisibility(true);
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

    private void setupRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        mAdapter = new LocalFontListAdapter(mContext, mExecutor);

        // ★ مستمع النقر على الخط: تمرير weightWidthLabel لـ NavManager عبر MainActivity ★
        mAdapter.setFontClickListener((fontPath, realName, fileName, ttcIndex, weightWidthLabel) -> {
            if (mFontSelectedListener != null) {
                mFontSelectedListener.onFontSelected(fontPath, realName, fileName,
                                                     ttcIndex, weightWidthLabel);
            }
        });

        // ★ مستمع تغيير الفرز: يحفظ التفضيل عبر SortManager ثم يُطبّق الأنيميشن ★
        mAdapter.setSortChangeListener((type, asc) -> {
            mSortManager.setSortOptions(type, asc);
        });

        // ★ FavoriteStatusProvider: كل عناصر قائمة المفضلة هي مفضلة بالتعريف ★
        // → يُعرض أيقونة النجمة الصفراء (ic_favorite) بجانب جميع العناصر دائماً
        mAdapter.setFavoriteStatusProvider(fontPath -> true);

        mRecyclerView.setAdapter(mAdapter);

        // ★ تهيئة معيار الفرز المحفوظ قبل وصول أي بيانات لتجنب السباق الزمني ★
        mAdapter.updateSortOptionsOnly(
            mSortManager.getCurrentSortType(),
            mSortManager.isSortAscending()
        );

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
            null // ★ لا يوجد SortByItemLayout منفصل في المفضلة ★
        );

        // ★ FavoriteStatusChecker: كل عناصر قائمة المفضلة مفضلة بالتعريف ★
        // → resolveFavoriteAction() يُعيد true دائماً
        // → يُعرض "إزاله من المفضله" (ic_oui_favorite_off) في وضع التحديد دائماً
        mSelectionManager.setFavoriteStatusChecker(position -> true);

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
             * ★ إجراء المفضلة في قائمة المفضلة ★
             * addToFavorites سيكون دائماً false هنا لأن FavoriteStatusChecker يُعيد true دائماً،
             * مما يعني أن resolveFavoriteAction() يُعيد true → addToFavorites = !true = false.
             * النتيجة: يُزيل العناصر المحددة من المفضلة وتختفي تلقائياً من القائمة.
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

    // ════════════════════════════════════════════════════════════
    // معالجات الإجراءات في وضع التحديد المتعدد
    // ════════════════════════════════════════════════════════════

    /**
     * إعادة تسمية الخط المحدد وتحديث القائمتين (المحلية والمفضلة) في الذاكرة.
     * يستفيد من renameFontInMemory الذي يُحدّث favoritesLiveData تلقائياً.
     */
    private void handleRename(int position) {
        String path = mAdapter.getFilePath(position);
        if (path == null) return;

        FontActionDialogs.showRenameDialog(mContext, path, (oldPath, newFileName) -> {
            boolean success = mViewModel.renameFontInMemory(oldPath, newFileName);

            if (success) {
                mSelectionManager.setSelecting(false);

                // ★ التمرير السلس للعنصر المُعاد تسميته بعد إعادة الترتيب ★
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

    /**
     * حذف الخطوط المحددة من الجهاز وتحديث القائمتين في الذاكرة.
     * deleteFontsInMemory يُحدّث favoritesLiveData تلقائياً إذا كانت الخطوط مفضلة.
     */
    private void handleDelete(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        // ★ الإصلاح: طرح 2 (الهيدر + الفوتر) للحصول على العدد الحقيقي ★
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
     * ★ إزالة الخطوط المحددة من المفضلة ★
     *
     * addToFavorites سيكون دائماً false في هذه القائمة.
     * بعد نجاح العملية، تختفي العناصر من القائمة تلقائياً
     * لأن Room LiveData يُحدَّث فور تغيير is_favorite في قاعدة البيانات.
     *
     * ★ يُحدّث أيضاً أيقونة المفضلة في قائمة الخطوط المحلية تلقائياً
     *   لأن favoritesLiveData و fontsLiveData يراقبان نفس قاعدة البيانات ★
     */
    private void handleFavoriteAction(List<Integer> positions, boolean addToFavorites) {
        if (positions == null || positions.isEmpty()) return;

        List<String> paths = new ArrayList<>();
        for (int position : positions) {
            String path = mAdapter.getFilePath(position);
            if (path != null) paths.add(path);
        }

        if (paths.isEmpty()) return;

        mSelectionManager.setSelecting(false);

        mViewModel.toggleFavoritesBatch(paths, addToFavorites, () -> {
            // ★ العناصر تختفي من القائمة تلقائياً عبر Room LiveData — لا حاجة لتحديث يدوي ★
            String message = addToFavorites
                ? getString(R.string.action_favorite)    // نادراً ما يحدث هنا
                : getString(R.string.action_unfavorite);
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        });
    }

    // ════════════════════════════════════════════════════════════
    // دوال البحث — تُستدعى من SearchCoordinator
    // ════════════════════════════════════════════════════════════

    /**
     * تفعيل البحث وتصفية قائمة المفضلة بنص البحث المعطى.
     *
     * ★ الإصلاح (حفظ موضع التمرير):
     *   نحفظ حالة التمرير الحالية قبل أول تصفية فقط، أي عندما لا يكون البحث نشطاً بعد.
     *   هذا يضمن حفظ الموضع الأصلي للقائمة (قبل فلترة أي شيء)، لا موضع وسط البحث.
     *   الاستعادة تتم لاحقاً في مراقب SearchQueryLiveData عند إفراغ نص البحث. ★
     */
    public void filterFonts(String query) {
        // ★ حفظ موضع التمرير قبل أول تصفية فقط (أي قبل تفعيل البحث) ★
        if (!mSearchManager.isSearchActive() && mRecyclerView != null) {
            LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
            if (lm != null) mPreSearchScrollState = lm.onSaveInstanceState();
        }
        mSearchViewModel.setSearchQuery(query);
        mSearchViewModel.activateSearch();
    }

    /** إلغاء البحث وإعادة عرض قائمة المفضلة كاملةً */
    public void resetFilter() {
        // ★ مسح استعلام البحث يُطلق مراقب SearchQueryLiveData الذي يتولى استعادة موضع التمرير ★
        mSearchViewModel.deactivateSearch();
    }

    // ════════════════════════════════════════════════════════════
    // دعم زر الرجوع — تُستدعى من NavManager
    // ════════════════════════════════════════════════════════════

    /**
     * يُعيد true إذا أُلغي وضع التحديد المتعدد (يمنع NavManager من معالجة زر الرجوع)
     */
    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
    }

    // ════════════════════════════════════════════════════════════
    // تحديث بيانات الـ Adapter
    // ════════════════════════════════════════════════════════════

    /**
     * يُغذّي الـ Adapter بقائمة المفضلة الخام.
     * SortedList داخل الـ Adapter يتولى الترتيب وتوليد الأنيميشن.
     */
    private void refreshAdapterData() {
        if (mCurrentFavoritesList.isEmpty()) {
            mSearchManager.updateFontsList(new ArrayList<>());
            if (mAdapter != null) {
                mAdapter.updateFilteredFonts(new ArrayList<>(), mSearchManager.getCurrentSearchQuery());
                mAdapter.updateSortOptionsOnly(
                    mSortManager.getCurrentSortType(),
                    mSortManager.isSortAscending()
                );
            }
            // ★ إظهار الحالة الفارغة مع رسالة مناسبة ★
            mUIManager.updateEmptyView(true, mSearchManager.isSearchActive());
            return;
        }

        // ★ بناء قائمة FontFileInfo من بيانات المفضلة بدون فرز مسبق ★
        List<FontFileInfo> rawFonts = new ArrayList<>();
        for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFavoritesList) {
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
            mAdapter.updateSortOptionsOnly(
                mSortManager.getCurrentSortType(),
                mSortManager.isSortAscending()
            );
        }

        mMainHandler.post(() -> mUIManager.restoreRecyclerViewState());
    }

    // ════════════════════════════════════════════════════════════
    // دوال دورة حياة Fragment
    // ════════════════════════════════════════════════════════════

    /** تحديث عداد المفضلة في MainActivity بالفهرس 4 */
    private void updateMainActivityFontsCount(int count) {
        // ★ لا تُحدّث العنوان الفرعي إذا كان وضع التحديد نشطاً ★
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        // ★ تمرير الفهرس 4 لتمييز هذا الفراجمنت عن بقية القوائم في MainActivity ★
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(FRAGMENT_INDEX, count);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // ★ تحديث العدد فقط إذا كان هذا الـ Fragment ظاهراً للمستخدم ★
        if (!isHidden()) {
            updateMainActivityFontsCount(mCurrentFavoritesList.size());
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            // ★ 1. حفظ موضع التمرير قبل الإخفاء ★
            mUIManager.saveRecyclerViewState();

            // ★ 2. إيقاف الأنيميشن لتطبيق التحديثات الخلفية بصمت ★
            if (mRecyclerView != null) {
                mRecyclerView.setItemAnimator(null);
            }

            mSearchViewModel.deactivateSearch();

            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                mSelectionManager.setSelecting(false);
            }
        } else {
            // ★ إعادة تفعيل اللمس عند ظهور الشاشة ★
            unblockTouch();

            // ★ إعادة رسم القائمة لتمييز آخر خط مفتوح ★
            if (mAdapter != null) mAdapter.smartUpdate();

            updateMainActivityFontsCount(mCurrentFavoritesList.size());

            // ★ 3. استعادة موضع التمرير بعد اكتمال layout ★
            mMainHandler.post(() -> mUIManager.restoreRecyclerViewState());

            // ★ 4. تأخير إعادة الأنيميشن لضمان رسم العناصر في مواضعها أولاً ★
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // ★ تحديث واجهة وضع التحديد بعد دوران الجهاز ★
        if (mSelectionManager != null) mSelectionManager.refreshActionMode();
    }

    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
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
