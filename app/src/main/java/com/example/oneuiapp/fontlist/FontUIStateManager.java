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
 */
public class FontUIStateManager {
    
    private static final String TAG = "FontUIStateManager";
    
    private final Context context;
    private View selectFolderContainer;
    private View mainContentLayout;
    private View emptyView;
    private TextView emptyTextView;
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
     * @param emptyTextView نص الحالة الفارغة
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
     * إظهار عرض الحالة الفارغة
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
        
        if (emptyTextView != null) {
            if (isSearchActive) {
                // رسالة البحث ثابتة لجميع القوائم
                emptyTextView.setText(context.getString(R.string.no_results_found));
            } else {
                // ★ الإصلاح (المشكلة 3): استخدام الرسالة المخصصة بدلاً من الرسالة المثبتة ★
                // كل Fragment يُعيّن رسالته عبر setDefaultEmptyMessage() في onAttach()
                emptyTextView.setText(context.getString(defaultEmptyMessageResId));
            }
        }
    }
    
    /**
     * إخفاء عرض الحالة الفارغة
     */
    private void hideEmptyView() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
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
