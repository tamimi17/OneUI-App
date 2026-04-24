package com.example.oneuiapp.fontlist;

import android.content.Context;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import java.lang.reflect.Method;

import com.example.oneuiapp.R;
import com.example.oneuiapp.ui.widget.SortByItemLayout;

/**
 * FontUIStateManager - فئة لإدارة حالات واجهة المستخدم المختلفة
 * تتولى التحكم في إظهار وإخفاء العناصر وحفظ واستعادة حالة المكونات
 *
 * ★ الإصلاح (المشكلة 3): إضافة defaultEmptyMessageResId مع setDefaultEmptyMessage() ★
 * يتيح لكل Fragment تخصيص رسالة الشاشة الفارغة بدلاً من استخدام
 * R.string.font_fragment_empty_message المثبتة لجميع القوائم.
 * يُستدعى setDefaultEmptyMessage() مباشرةً بعد إنشاء FontUIStateManager في كل Fragment.
 *
 * ★ الإصلاح (مشكلة العنوان): إضافة emptyTitleView مع setEmptyTitleView() ★
 * يُخفى عنوان الحالة الفارغة تلقائياً عند تفعيل البحث بلا نتائج،
 * لأن جملة "لا توجد نتائج" تكفي وحدها دون الحاجة لعرض العنوان معها.
 * يُستدعى setEmptyTitleView() بعد setViews() مباشرةً في كل Fragment يحتوي على empty_title.
 *
 * ★ الإضافة (تمييز الحالتين): إضافة noResultsTextView مع setNoResultsTextView() ★
 * يُتيح تمييز رسالة "لا توجد نتائج" (عند البحث) عن رسالة الحالة الفارغة الحقيقية
 * بلون وحجم مختلفين، لأن كلًّا منهما يخدم سياقاً مختلفاً من منظور تجربة المستخدم.
 *
 * ★ آلية التمييز:
 *   - عند البحث بلا نتائج : يُخفى empty_title + empty_text، يُعرض no_results_text
 *   - عند الفراغ الحقيقي  : يُعرض empty_title + empty_text، يُخفى no_results_text
 *
 * ★ التوافق مع الوراء: إذا لم يُربط no_results_text (setNoResultsTextView لم تُستدعَ)،
 *   يعود الـ Manager تلقائياً إلى السلوك القديم (empty_text للحالتين معاً). ★
 *
 * يُستدعى setNoResultsTextView() بعد setViews() وsetEmptyTitleView() في كل Fragment.
 */
public class FontUIStateManager {
    
    private static final String TAG = "FontUIStateManager";
    
    private final Context context;
    private View selectFolderContainer;
    private View mainContentLayout;
    private View emptyView;
    private TextView emptyTextView;

    // ★ عنوان الحالة الفارغة — يُخفى تلقائياً عند تفعيل البحث بلا نتائج ★
    private TextView emptyTitleView;

    // ★ رسالة البحث بلا نتائج — مستقلة عن emptyTextView لتمكين تخصيص مظهرها من الـ layout ★
    // نصها وأيقونتها ثابتان ("لا توجد نتائج") ويُعيَّنان في XML مباشرةً.
    // الـ Manager يكتفي بإظهارها وإخفائها دون الحاجة لاستدعاء setText() عليها.
    private TextView noResultsTextView;

    private RecyclerView recyclerView;
    private AppBarLayout appBarLayout;
    
    private Parcelable recyclerViewState;
    private SortByItemLayout.SortType savedSortType;
    private boolean savedSortAscending;

    // ★ الإصلاح (المشكلة 3): رسالة الشاشة الفارغة الافتراضية لكل Fragment ★
    // القيمة الابتدائية هي رسالة المجلد المحلي للتوافق مع LocalFontListFragment.
    // يستدعي FavoriteFontListFragment (وأي Fragment مستقبلي) setDefaultEmptyMessage()
    // لتعيين رسالته الخاصة بعد إنشاء هذا الـ Manager مباشرةً.
    private int defaultEmptyMessageResId = R.string.font_fragment_empty_message;
    
    /**
     * Constructor
     * @param context السياق المطلوب
     */
    public FontUIStateManager(Context context) {
        this.context = context;
    }
    
    /**
     * تعيين العناصر المُدارة
     * @param selectFolderContainer حاوية زر اختيار المجلد
     * @param mainContentLayout المحتوى الرئيسي
     * @param emptyView عرض الحالة الفارغة
     * @param emptyTextView نص وصف الحالة الفارغة الحقيقية
     * @param recyclerView قائمة الخطوط
     */
    public void setViews(View selectFolderContainer, View mainContentLayout, 
                        View emptyView, TextView emptyTextView, RecyclerView recyclerView) {
        this.selectFolderContainer = selectFolderContainer;
        this.mainContentLayout = mainContentLayout;
        this.emptyView = emptyView;
        this.emptyTextView = emptyTextView;
        this.recyclerView = recyclerView;
    }

    /**
     * تعيين عنوان الحالة الفارغة (empty_title).
     *
     * مُصمَّمة كدالة منفصلة عن setViews() للحفاظ على توافق الاستدعاءات الموجودة.
     * تُستدعى مباشرةً بعد setViews() في كل Fragment يحتوي على empty_title.
     *
     * ★ السلوك:
     *   - عند البحث بلا نتائج  → يُخفى العنوان، يبقى الوصف فقط ("لا توجد نتائج")
     *   - عند فراغ القائمة     → يُعرض العنوان والوصف معاً ★
     *
     * @param emptyTitleView الـ TextView المعرَّف بـ id/empty_title في الـ layout
     */
    public void setEmptyTitleView(TextView emptyTitleView) {
        this.emptyTitleView = emptyTitleView;
    }

    /**
     * ★ تعيين رسالة البحث بلا نتائج (no_results_text) المستقلة ★
     *
     * تُستدعى بعد setViews() وsetEmptyTitleView() في كل Fragment يريد تمييز
     * مظهر رسالة "لا توجد نتائج" عن مظهر رسالة الحالة الفارغة الحقيقية.
     *
     * ★ الفرق عن emptyTextView:
     *   - emptyTextView  → رسالة الحالة الفارغة الحقيقية (القائمة فارغة أصلاً)
     *   - noResultsTextView → رسالة البحث بلا نتائج (توجد خطوط لكن لا نتائج للبحث)
     *   كلٌّ منهما يُعيَّن لونه وحجمه بشكل مستقل تماماً من الـ layout. ★
     *
     * ★ ملاحظة: نص هذا الـ TextView يُعيَّن في XML مباشرةً (android:text="@string/no_results_found")
     *   ولا يحتاج الـ Manager لاستدعاء setText() عليه. ★
     *
     * ★ التوافق مع الوراء: إذا لم تُستدعَ هذه الدالة، يعود الـ Manager تلقائياً
     *   إلى السلوك القديم (empty_text يخدم الحالتين معاً). ★
     *
     * @param noResultsTextView الـ TextView المعرَّف بـ id/no_results_text في الـ layout
     */
    public void setNoResultsTextView(TextView noResultsTextView) {
        this.noResultsTextView = noResultsTextView;
    }
    
    /**
     * تعيين AppBarLayout
     * @param appBarLayout شريط التطبيق العلوي
     */
    public void setAppBarLayout(AppBarLayout appBarLayout) {
        this.appBarLayout = appBarLayout;
    }

    /**
     * ★ الإصلاح (المشكلة 3): تعيين رسالة الشاشة الفارغة المخصصة لكل Fragment ★
     *
     * يُستدعى مباشرةً بعد إنشاء FontUIStateManager في أي Fragment يحتاج رسالة مختلفة.
     * مثال في FavoriteFontListFragment.onAttach():
     *   mUIManager = new FontUIStateManager(mContext);
     *   mUIManager.setDefaultEmptyMessage(R.string.favorites_empty_message);
     *
     * إذا لم يُستدعَ، تُستخدم القيمة الافتراضية (font_fragment_empty_message)
     * وهي مناسبة لـ LocalFontListFragment دون الحاجة لأي استدعاء.
     *
     * @param resId معرّف نص الرسالة المطلوبة من ملفات strings
     */
    public void setDefaultEmptyMessage(int resId) {
        this.defaultEmptyMessageResId = resId;
    }
    
    /**
     * تحديث رؤية واجهة المستخدم بناءً على وجود مجلد محدد
     * @param hasFolderUri هل يوجد مجلد محدد
     */
    public void updateUIVisibility(boolean hasFolderUri) {
        if (selectFolderContainer != null) {
            selectFolderContainer.setVisibility(hasFolderUri ? View.GONE : View.VISIBLE);
        }
        
        if (mainContentLayout != null) {
            mainContentLayout.setVisibility(hasFolderUri ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * تحديث عرض الحالة الفارغة
     * @param isEmpty هل القائمة فارغة
     * @param isSearchActive هل هناك بحث نشط
     */
    public void updateEmptyView(boolean isEmpty, boolean isSearchActive) {
        if (isEmpty) {
            showEmptyView(isSearchActive);
        } else {
            hideEmptyView();
        }
    }
    
    /**
     * إظهار عرض الحالة الفارغة مع التمييز بين الحالتين.
     *
     * ★ إذا كان noResultsTextView مُربوطاً (setNoResultsTextView استُدعيت):
     *   - عند البحث : أخفِ empty_title + empty_text، أظهر no_results_text
     *   - عند الفراغ: أظهر empty_title + empty_text، أخفِ no_results_text
     *
     * ★ إذا لم يكن noResultsTextView مُربوطاً (التوافق مع الوراء):
     *   - يُستخدم empty_text للحالتين مع تغيير نصه برمجياً كما كان سابقاً ★
     *
     * @param isSearchActive هل هناك بحث نشط
     */
    private void showEmptyView(boolean isSearchActive) {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
            
            if (appBarLayout != null) {
                updateEmptyViewPosition(Math.abs(appBarLayout.getTop()));
            }
        }

        if (noResultsTextView != null) {
            // ★ وضع التمييز: كل TextView مخصص لحالة واحدة فقط ★
            // اللون والحجم يُعيَّنان بالكامل من الـ layout — الـ Manager لا يتدخل فيهما
            if (isSearchActive) {
                // حالة البحث بلا نتائج: يظهر no_results_text وحده
                if (emptyTitleView != null) emptyTitleView.setVisibility(View.GONE);
                if (emptyTextView  != null) emptyTextView.setVisibility(View.GONE);
                noResultsTextView.setVisibility(View.VISIBLE);
            } else {
                // حالة الفراغ الحقيقي: يظهر empty_title + empty_text
                if (emptyTitleView != null) emptyTitleView.setVisibility(View.VISIBLE);
                if (emptyTextView  != null) {
                    emptyTextView.setVisibility(View.VISIBLE);
                    // ★ الإصلاح (المشكلة 3): استخدام الرسالة المخصصة بدلاً من الرسالة المثبتة ★
                    emptyTextView.setText(context.getString(defaultEmptyMessageResId));
                }
                noResultsTextView.setVisibility(View.GONE);
            }
        } else {
            // ★ التوافق مع الوراء: noResultsTextView غير مُربوط → السلوك القديم ★
            // empty_text يخدم الحالتين مع تغيير نصه برمجياً
            if (emptyTitleView != null) {
                emptyTitleView.setVisibility(isSearchActive ? View.GONE : View.VISIBLE);
            }
            if (emptyTextView != null) {
                if (isSearchActive) {
                    emptyTextView.setText(context.getString(R.string.no_results_found));
                } else {
                    // ★ الإصلاح (المشكلة 3): استخدام الرسالة المخصصة بدلاً من الرسالة المثبتة ★
                    emptyTextView.setText(context.getString(defaultEmptyMessageResId));
                }
            }
        }
    }
    
    /**
     * إخفاء عرض الحالة الفارغة وإعادة إظهار القائمة.
     *
     * ★ يُخفى noResultsTextView أيضاً لضمان عدم بقائه ظاهراً عند استعادة القائمة ★
     */
    private void hideEmptyView() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }

        // ★ إخفاء رسالة البحث بلا نتائج عند استعادة القائمة ★
        // ضروري لتجنب ظهورها في الحالة التالية: كان المستخدم يبحث بلا نتائج،
        // ثم حذف نص البحث، فأعادت القائمة نفسها → يجب أن يختفي no_results_text تماماً.
        if (noResultsTextView != null) {
            noResultsTextView.setVisibility(View.GONE);
        }
    }
    
    /**
     * تحديث موضع عرض الحالة الفارغة بناءً على حركة AppBar
     * @param verticalOffset الإزاحة العمودية للـ AppBar
     */
    public void updateEmptyViewPosition(int verticalOffset) {
        if (emptyView == null || emptyView.getVisibility() != View.VISIBLE) {
            return;
        }
        
        if (appBarLayout == null) {
            return;
        }
        
        int totalScrollRange = appBarLayout.getTotalScrollRange();
        int inputMethodWindowVisibleHeight = getInputMethodWindowVisibleHeight();
        
        float translationY;
        
        if (totalScrollRange != 0) {
            translationY = (Math.abs(verticalOffset) - totalScrollRange) / 2.0f;
        } else {
            translationY = (Math.abs(verticalOffset) - inputMethodWindowVisibleHeight) / 2.0f;
        }
        
        emptyView.setTranslationY(translationY);
    }
    
    /**
     * الحصول على ارتفاع نافذة لوحة المفاتيح المرئية
     * @return ارتفاع النافذة
     */
    private int getInputMethodWindowVisibleHeight() {
        try {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            Method method = InputMethodManager.class.getMethod("getInputMethodWindowVisibleHeight");
            Object result = method.invoke(imm);
            return result != null ? (int) result : 0;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get input method window visible height", e);
            return 0;
        }
    }
    
    /**
     * حفظ حالة RecyclerView
     */
    public void saveRecyclerViewState() {
        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
            recyclerViewState = recyclerView.getLayoutManager().onSaveInstanceState();
            Log.d(TAG, "RecyclerView state saved");
        }
    }
    
    /**
     * استعادة حالة RecyclerView
     */
    public void restoreRecyclerViewState() {
        if (recyclerViewState != null && recyclerView != null && recyclerView.getLayoutManager() != null) {
            recyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
            recyclerViewState = null;
            Log.d(TAG, "RecyclerView state restored");
        }
    }
    
    /**
     * تعيين حالة RecyclerView المحفوظة مسبقاً
     * @param state الحالة المحفوظة
     */
    public void setRecyclerViewState(Parcelable state) {
        this.recyclerViewState = state;
    }
    
    /**
     * الحصول على حالة RecyclerView الحالية
     * @return الحالة المحفوظة أو null
     */
    public Parcelable getRecyclerViewState() {
        return recyclerViewState;
    }
    
    /**
     * حفظ حالة الفرز
     * @param sortType نوع الفرز
     * @param ascending اتجاه الفرز
     */
    public void saveSortState(SortByItemLayout.SortType sortType, boolean ascending) {
        this.savedSortType = sortType;
        this.savedSortAscending = ascending;
        Log.d(TAG, "Sort state saved: type=" + sortType + ", ascending=" + ascending);
    }
    
    /**
     * الحصول على نوع الفرز المحفوظ
     * @return نوع الفرز
     */
    public SortByItemLayout.SortType getSavedSortType() {
        return savedSortType;
    }
    
    /**
     * الحصول على اتجاه الفرز المحفوظ
     * @return اتجاه الفرز
     */
    public boolean isSavedSortAscending() {
        return savedSortAscending;
    }
    
    /**
     * فحص ما إذا كانت هناك حالة فرز محفوظة
     * @return true إذا كانت هناك حالة محفوظة
     */
    public boolean hasSavedSortState() {
        return savedSortType != null;
    }
    
    /**
     * مسح جميع الحالات المحفوظة
     */
    public void clearSavedStates() {
        recyclerViewState = null;
        savedSortType = null;
        savedSortAscending = true;
        Log.d(TAG, "All saved states cleared");
    }
    
    /**
     * إظهار حالة التحميل
     * يمكن توسيع هذه الدالة لإضافة مؤشر تحميل مخصص
     */
    public void showLoadingState() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
    }
    
    /**
     * إخفاء حالة التحميل وإظهار المحتوى
     */
    public void hideLoadingState() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * التمرير إلى أعلى القائمة
     */
    public void scrollToTop() {
        if (recyclerView != null) {
            recyclerView.scrollToPosition(0);
        }
    }
    
    /**
     * التمرير إلى موضع محدد
     * @param position الموضع المراد التمرير إليه
     */
    public void scrollToPosition(int position) {
        if (recyclerView != null && position >= 0) {
            recyclerView.scrollToPosition(position);
        }
    }
    
    /**
     * التمرير السلس إلى موضع محدد
     * @param position الموضع المراد التمرير إليه
     */
    public void smoothScrollToPosition(int position) {
        if (recyclerView != null && position >= 0) {
            recyclerView.smoothScrollToPosition(position);
        }
    }
    }
