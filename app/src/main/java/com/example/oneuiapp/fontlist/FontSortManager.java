package com.example.oneuiapp.fontlist;

import android.content.Context;
import android.util.Log;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.example.oneuiapp.data.datastore.SettingsDataStore;
import com.example.oneuiapp.ui.widget.SortByItemLayout;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * FontSortManager — مسؤول عن حفظ تفضيلات الفرز في DataStore وإشعار المستمعين.
 *
 * ★ ملاحظة معمارية مهمة ★
 * منطق الفرز الفعلي (المقارنة وترتيب العناصر) انتقل بالكامل إلى FontSortedListCallback
 * داخل كل من FontListAdapter و SystemFontListAdapter. هذا يعني أن SortedList
 * يتولى إنتاج أنيميشن الانزلاق عند تغيير ترتيب العناصر.
 *
 * دور هذا الكلاس الآن:
 *   1. حفظ نوع الفرز واتجاهه في DataStore (في مفاتيح منفصلة لكل نوع قائمة)
 *   2. إشعار المستمع (Fragment) بالتغيير ليستدعي mAdapter.setSortOptions()
 *
 * ★ isSystemFont يضمن أن خطوط النظام والمجلد المحلي لا يتشاركان نفس مفاتيح DataStore
 *   وهذا هو الحل الجذري لمشكلة التجمد عند التنقل بين الـ Fragments ★
 *
 * ★ الإضافة: دعم معرّف نصي "FAVORITES" عبر مُنشئ ثانٍ (String listType) ★
 *   يتيح لـ FavoriteFontListFragment استخدام مفاتيح DataStore المستقلة:
 *   KEY_FAVORITES_SORT_TYPE و KEY_FAVORITES_SORT_ASCENDING
 *   دون المساس بمفاتيح القائمتين الأخريين.
 *
 * الدالة sortFontsList() محتفظ بها لأغراض التوافق مع الإصدارات السابقة،
 * لكنها لم تعد تُستدعى من الـ Fragments.
 */
public class FontSortManager {

    private static final String TAG = "FontSortManager";

    // ════════════════════════════════════════════════════════════
    // ★ أنواع القوائم المدعومة ★
    // ════════════════════════════════════════════════════════════

    /** معرّف قائمة الخطوط المحلية (المجلد) */
    private static final String LIST_TYPE_LOCAL     = "LOCAL";
    /** معرّف قائمة خطوط النظام */
    private static final String LIST_TYPE_SYSTEM    = "SYSTEM";
    /** معرّف قائمة المفضلة */
    private static final String LIST_TYPE_FAVORITES = "FAVORITES";

    private final SettingsDataStore dataStore;

    // ★ نوع القائمة — يحدد مجموعة مفاتيح DataStore التي تُستخدم للقراءة والكتابة ★
    private final String listType;

    // ★ isSystemFont محتفظ به للتوافق مع المُنشئ الأصلي (boolean) ★
    private final boolean isSystemFont;

    private SortByItemLayout.SortType currentSortType;
    private boolean isSortAscending;
    private SortChangeListener listener;

    public interface SortChangeListener {
        void onSortChanged(SortByItemLayout.SortType sortType, boolean ascending);
    }

    // ════════════════════════════════════════════════════════════
    // المُنشئون
    // ════════════════════════════════════════════════════════════

    /**
     * ★ المُنشئ الأصلي (للتوافق مع الإصدارات السابقة) ★
     *
     * @param context     السياق المطلوب للوصول إلى DataStore
     * @param isSystemFont true لخطوط النظام، false للمجلد المحلي
     *                     يضمن قراءة/كتابة المفتاح الصحيح ومنع التداخل بين القائمتين
     */
    public FontSortManager(Context context, boolean isSystemFont) {
        this.dataStore    = SettingsDataStore.getInstance(context);
        this.isSystemFont = isSystemFont;
        this.listType     = isSystemFont ? LIST_TYPE_SYSTEM : LIST_TYPE_LOCAL;
        loadSortPreferences();
    }

    /**
     * ★ المُنشئ الجديد للدعم الموسّع (قائمة المفضلة وما قد يُضاف مستقبلاً) ★
     *
     * الاستخدام في FavoriteFontListFragment:
     *   mSortManager = new FontSortManager(mContext, "FAVORITES");
     *
     * القيم المقبولة لـ listType:
     *   "LOCAL"     → مفاتيح المجلد المحلي  (KEY_SORT_TYPE / KEY_SORT_ASCENDING)
     *   "SYSTEM"    → مفاتيح خطوط النظام   (KEY_SYSTEM_SORT_TYPE / KEY_SYSTEM_SORT_ASCENDING)
     *   "FAVORITES" → مفاتيح قائمة المفضلة (KEY_FAVORITES_SORT_TYPE / KEY_FAVORITES_SORT_ASCENDING)
     *
     * @param context  السياق المطلوب للوصول إلى DataStore
     * @param listType معرّف نوع القائمة ("LOCAL" | "SYSTEM" | "FAVORITES")
     */
    public FontSortManager(Context context, String listType) {
        this.dataStore    = SettingsDataStore.getInstance(context);
        this.listType     = (listType != null) ? listType.toUpperCase() : LIST_TYPE_LOCAL;
        this.isSystemFont = LIST_TYPE_SYSTEM.equals(this.listType);
        loadSortPreferences();
    }

    public void setSortChangeListener(SortChangeListener listener) {
        this.listener = listener;
    }

    // ════════════════════════════════════════════════════════════
    // قراءة وحفظ التفضيلات
    // ════════════════════════════════════════════════════════════

    /**
     * يقرأ تفضيلات الفرز من المفتاح الصحيح بناءً على نوع القائمة.
     *
     * LOCAL     → KEY_SORT_TYPE / KEY_SORT_ASCENDING
     * SYSTEM    → KEY_SYSTEM_SORT_TYPE / KEY_SYSTEM_SORT_ASCENDING
     * FAVORITES → KEY_FAVORITES_SORT_TYPE / KEY_FAVORITES_SORT_ASCENDING
     */
    private void loadSortPreferences() {
        try {
            String sortTypeName;
            switch (listType) {
                case LIST_TYPE_SYSTEM:
                    sortTypeName = dataStore.getSystemSortType().blockingFirst();
                    break;
                case LIST_TYPE_FAVORITES:
                    sortTypeName = dataStore.getFavoritesSortType().blockingFirst();
                    break;
                case LIST_TYPE_LOCAL:
                default:
                    sortTypeName = dataStore.getSortType().blockingFirst();
                    break;
            }
            currentSortType = SortByItemLayout.SortType.valueOf(sortTypeName);
        } catch (Exception e) {
            Log.w(TAG, "Invalid or missing sort type, using default", e);
            currentSortType = SortByItemLayout.SortType.NAME;
        }

        try {
            switch (listType) {
                case LIST_TYPE_SYSTEM:
                    isSortAscending = dataStore.getSystemSortAscending().blockingFirst();
                    break;
                case LIST_TYPE_FAVORITES:
                    isSortAscending = dataStore.getFavoritesSortAscending().blockingFirst();
                    break;
                case LIST_TYPE_LOCAL:
                default:
                    isSortAscending = dataStore.getSortAscending().blockingFirst();
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Missing sort ascending value, using default", e);
            isSortAscending = true;
        }

        Log.d(TAG, "Loaded sort preferences [" + listType + "]: "
                + "type=" + currentSortType + ", ascending=" + isSortAscending);
    }

    /**
     * يحفظ في المفتاح الصحيح بناءً على نوع القائمة.
     *
     * هذا هو الحل الجذري لمنع التداخل:
     * تغيير فرز أي قائمة لا يلمس مفاتيح القوائم الأخرى أبداً.
     */
    private void saveSortPreferences() {
        switch (listType) {

            case LIST_TYPE_SYSTEM:
                dataStore.setSystemSortType(currentSortType.name())
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[SYSTEM] Saved sort type: " + currentSortType),
                            error -> Log.e(TAG, "[SYSTEM] Error saving sort type", error)
                        );
                dataStore.setSystemSortAscending(isSortAscending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[SYSTEM] Saved sort ascending: " + isSortAscending),
                            error -> Log.e(TAG, "[SYSTEM] Error saving sort ascending", error)
                        );
                break;

            case LIST_TYPE_FAVORITES:
                // ★ مفاتيح قائمة المفضلة المستقلة — لا تؤثر على أي قائمة أخرى ★
                dataStore.setFavoritesSortType(currentSortType.name())
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[FAVORITES] Saved sort type: " + currentSortType),
                            error -> Log.e(TAG, "[FAVORITES] Error saving sort type", error)
                        );
                dataStore.setFavoritesSortAscending(isSortAscending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[FAVORITES] Saved sort ascending: " + isSortAscending),
                            error -> Log.e(TAG, "[FAVORITES] Error saving sort ascending", error)
                        );
                break;

            case LIST_TYPE_LOCAL:
            default:
                dataStore.setSortType(currentSortType.name())
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[LOCAL] Saved sort type: " + currentSortType),
                            error -> Log.e(TAG, "[LOCAL] Error saving sort type", error)
                        );
                dataStore.setSortAscending(isSortAscending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[LOCAL] Saved sort ascending: " + isSortAscending),
                            error -> Log.e(TAG, "[LOCAL] Error saving sort ascending", error)
                        );
                break;
        }
    }

    // ════════════════════════════════════════════════════════════
    // واجهة عامة
    // ════════════════════════════════════════════════════════════

    /**
     * يحفظ خيارات الفرز ويُشعر المستمع بالتغيير.
     * المستمع (Fragment) يستدعي بدوره mAdapter.setSortOptions() لتشغيل أنيميشن SortedList.
     */
    public void setSortOptions(SortByItemLayout.SortType sortType, boolean ascending) {
        boolean changed = (this.currentSortType != sortType) || (this.isSortAscending != ascending);

        this.currentSortType = sortType;
        this.isSortAscending = ascending;

        if (changed) {
            saveSortPreferences();
            notifySortChanged();
        }
    }

    public void setSortType(SortByItemLayout.SortType sortType) {
        setSortOptions(sortType, this.isSortAscending);
    }

    public void setSortAscending(boolean ascending) {
        setSortOptions(this.currentSortType, ascending);
    }

    public void toggleSortDirection() {
        setSortAscending(!isSortAscending);
    }

    /**
     * @deprecated منطق الفرز الفعلي انتقل إلى SortedList.Callback داخل الـ Adapters.
     * هذه الدالة محتفظ بها للتوافق مع الإصدارات السابقة فقط.
     */
    @Deprecated
    public void sortFontsList(List<FontFileInfo> fontsToSort) {
        if (fontsToSort == null || fontsToSort.isEmpty()) return;
        Comparator<FontFileInfo> comparator = getComparatorForCurrentSort();
        Collections.sort(fontsToSort, comparator);
    }

    private Comparator<FontFileInfo> getComparatorForCurrentSort() {
        Comparator<FontFileInfo> comparator;

        switch (currentSortType) {
            case DATE:
                comparator = (f1, f2) -> {
                    if (f1 == null || f2 == null) return f1 == null ? 1 : -1;
                    return Long.compare(f1.getLastModified(), f2.getLastModified());
                };
                break;
            case SIZE:
                comparator = (f1, f2) -> {
                    if (f1 == null || f2 == null) return f1 == null ? 1 : -1;
                    return Long.compare(f1.getSize(), f2.getSize());
                };
                break;
            case NAME:
            default:
                comparator = (f1, f2) -> {
                    if (f1 == null || f1.getName() == null) return 1;
                    if (f2 == null || f2.getName() == null) return -1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                };
                break;
        }

        if (!isSortAscending) comparator = Collections.reverseOrder(comparator);
        return comparator;
    }

    public SortByItemLayout.SortType getCurrentSortType() { return currentSortType; }

    public boolean isSortAscending() { return isSortAscending; }

    public void reloadPreferences() {
        loadSortPreferences();
        notifySortChanged();
    }

    public void resetToDefaults() {
        setSortOptions(SortByItemLayout.SortType.NAME, true);
    }

    private void notifySortChanged() {
        if (listener != null) listener.onSortChanged(currentSortType, isSortAscending);
    }

    public String getSortDescription() {
        String typeName;
        switch (currentSortType) {
            case DATE:  typeName = "Date";  break;
            case SIZE:  typeName = "Size";  break;
            case NAME:
            default:    typeName = "Name";  break;
        }
        return typeName + " (" + (isSortAscending ? "Ascending" : "Descending") + ")"
                + " [" + listType + "]";
    }
}
