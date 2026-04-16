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
 * الدالة sortFontsList() محتفظ بها لأغراض التوافق مع الإصدارات السابقة،
 * لكنها لم تعد تُستدعى من الـ Fragments.
 */
public class FontSortManager {

    private static final String TAG = "FontSortManager";

    private final SettingsDataStore dataStore;

    // ★ يحدد ما إذا كانت هذه النسخة لخطوط النظام أم للمجلد المحلي ★
    private final boolean isSystemFont;

    private SortByItemLayout.SortType currentSortType;
    private boolean isSortAscending;
    private SortChangeListener listener;

    public interface SortChangeListener {
        void onSortChanged(SortByItemLayout.SortType sortType, boolean ascending);
    }

    /**
     * @param context     السياق المطلوب للوصول إلى DataStore
     * @param isSystemFont true لخطوط النظام، false للمجلد المحلي
     *                     يضمن قراءة/كتابة المفتاح الصحيح ومنع التداخل بين القائمتين
     */
    public FontSortManager(Context context, boolean isSystemFont) {
        this.dataStore    = SettingsDataStore.getInstance(context);
        this.isSystemFont = isSystemFont;
        loadSortPreferences();
    }

    public void setSortChangeListener(SortChangeListener listener) {
        this.listener = listener;
    }

    /**
     * يقرأ تفضيلات الفرز من المفتاح الصحيح بناءً على نوع القائمة.
     * خطوط النظام → KEY_SYSTEM_SORT_TYPE / KEY_SYSTEM_SORT_ASCENDING
     * المجلد المحلي → KEY_SORT_TYPE / KEY_SORT_ASCENDING
     */
    private void loadSortPreferences() {
        try {
            String sortTypeName = isSystemFont
                    ? dataStore.getSystemSortType().blockingFirst()
                    : dataStore.getSortType().blockingFirst();
            currentSortType = SortByItemLayout.SortType.valueOf(sortTypeName);
        } catch (Exception e) {
            Log.w(TAG, "Invalid or missing sort type, using default", e);
            currentSortType = SortByItemLayout.SortType.NAME;
        }

        try {
            isSortAscending = isSystemFont
                    ? dataStore.getSystemSortAscending().blockingFirst()
                    : dataStore.getSortAscending().blockingFirst();
        } catch (Exception e) {
            Log.w(TAG, "Missing sort ascending value, using default", e);
            isSortAscending = true;
        }

        Log.d(TAG, "Loaded sort preferences [" + (isSystemFont ? "SYSTEM" : "LOCAL") + "]: "
                + "type=" + currentSortType + ", ascending=" + isSortAscending);
    }

    /**
     * يحفظ في المفتاح الصحيح بناءً على نوع القائمة.
     * هذا هو الحل الجذري: تغيير فرز المجلد لا يلمس مفاتيح خطوط النظام أبداً.
     */
    private void saveSortPreferences() {
        if (isSystemFont) {
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
        } else {
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
        }
    }

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
                + " [" + (isSystemFont ? "SYSTEM" : "LOCAL") + "]";
    }
    }
