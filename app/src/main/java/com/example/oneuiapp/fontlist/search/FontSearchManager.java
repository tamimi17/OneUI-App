package com.example.oneuiapp.fontlist.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.oneuiapp.fontlist.FontFileInfo;
import com.example.oneuiapp.utils.FileUtils;

/**
 * FontSearchManager - فئة لإدارة عمليات البحث والتصفية في قائمة الخطوط
 * تتولى تطبيق استعلامات البحث على قائمة الخطوط وإنتاج قائمة مصفاة
 *
 * ★ التصحيح الجوهري: يُجرى البحث على اسم الملف بعد إزالة الامتداد عبر FileUtils.removeExtension()
 *   وليس على الاسم الخام، لأن جميع ملفات الخطوط تنتهي بـ .ttf/.otf مما كان يمنع
 *   فلترة أي عنصر عند البحث بحرف t أو f. ★
 */
public class FontSearchManager {
    
    private static final String TAG = "FontSearchManager";
    
    private final List<FontFileInfo> allFonts;
    private final List<FontFileInfo> filteredFonts;
    private String currentSearchQuery;
    private SearchResultListener listener;
    
    /**
     * واجهة للاستماع إلى نتائج البحث
     */
    public interface SearchResultListener {
        void onSearchResultsChanged(int resultCount, boolean isEmpty);
    }
    
    /**
     * Constructor
     */
    public FontSearchManager() {
        this.allFonts          = new ArrayList<>();
        this.filteredFonts     = new ArrayList<>();
        this.currentSearchQuery = "";
    }
    
    /**
     * تعيين المستمع لنتائج البحث
     * @param listener المستمع الذي سيتلقى إشعارات تغيير النتائج
     */
    public void setSearchResultListener(SearchResultListener listener) {
        this.listener = listener;
    }
    
    /**
     * تحديث القائمة الأساسية للخطوط
     * @param fonts القائمة الجديدة للخطوط
     */
    public void updateFontsList(List<FontFileInfo> fonts) {
        allFonts.clear();
        if (fonts != null) {
            allFonts.addAll(fonts);
        }
        applyCurrentFilter();
    }
    
    /**
     * تطبيق استعلام بحث جديد
     * @param query استعلام البحث (يمكن أن يكون null أو فارغاً)
     */
    public void filterFonts(String query) {
        currentSearchQuery = query == null ? "" : query.trim();
        applyCurrentFilter();
    }
    
    /**
     * إعادة تعيين البحث وإظهار جميع الخطوط
     */
    public void resetFilter() {
        if (!currentSearchQuery.isEmpty()) {
            currentSearchQuery = "";
            applyCurrentFilter();
        }
    }
    
    /**
     * تطبيق الفلتر الحالي على القائمة الأساسية.
     *
     * ★ المقارنة تتم على الاسم بدون امتداد (removeExtension) لا على الاسم الخام،
     *   مما يضمن أن البحث بـ t أو f لا يُعيد كل ملفات .ttf/.otf تلقائياً. ★
     */
    private void applyCurrentFilter() {
        filteredFonts.clear();
        
        if (currentSearchQuery.isEmpty()) {
            filteredFonts.addAll(allFonts);
        } else {
            String lowerCaseQuery = currentSearchQuery.toLowerCase(Locale.getDefault());
            for (FontFileInfo font : allFonts) {
                if (font.getName() != null) {
                    // ★ إزالة الامتداد قبل المقارنة — هذا هو جوهر الإصلاح ★
                    String nameWithoutExtension = FileUtils.removeExtension(font.getName());
                    if (nameWithoutExtension.toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) {
                        filteredFonts.add(font);
                    }
                }
            }
        }
        
        notifySearchResultsChanged();
    }
    
    /**
     * إعادة تطبيق الفلتر الحالي
     * مفيد بعد إجراء عملية فرز على القائمة الأساسية
     */
    public void reapplyFilter() {
        applyCurrentFilter();
    }
    
    /**
     * الحصول على القائمة المصفاة الحالية
     * @return قائمة الخطوط بعد تطبيق الفلتر
     */
    public List<FontFileInfo> getFilteredFonts() {
        return new ArrayList<>(filteredFonts);
    }
    
    /**
     * الحصول على القائمة الأساسية (غير المصفاة)
     * @return القائمة الكاملة للخطوط
     */
    public List<FontFileInfo> getAllFonts() {
        return new ArrayList<>(allFonts);
    }
    
    /**
     * الحصول على استعلام البحث الحالي
     * @return استعلام البحث النشط
     */
    public String getCurrentSearchQuery() {
        return currentSearchQuery;
    }
    
    /**
     * فحص ما إذا كان هناك بحث نشط حالياً
     * @return true إذا كان هناك استعلام بحث غير فارغ
     */
    public boolean isSearchActive() {
        return !currentSearchQuery.isEmpty();
    }
    
    /**
     * فحص ما إذا كانت القائمة المصفاة فارغة
     * @return true إذا لم يكن هناك نتائج
     */
    public boolean isFilteredListEmpty() {
        return filteredFonts.isEmpty();
    }
    
    /**
     * الحصول على عدد النتائج المصفاة
     * @return عدد الخطوط في القائمة المصفاة
     */
    public int getFilteredCount() {
        return filteredFonts.size();
    }
    
    /**
     * الحصول على عدد الخطوط الكلي
     * @return عدد الخطوط في القائمة الأساسية
     */
    public int getTotalCount() {
        return allFonts.size();
    }
    
    /**
     * مسح جميع البيانات
     */
    public void clear() {
        allFonts.clear();
        filteredFonts.clear();
        currentSearchQuery = "";
        notifySearchResultsChanged();
    }
    
    /**
     * إشعار المستمع بتغيير نتائج البحث
     */
    private void notifySearchResultsChanged() {
        if (listener != null) {
            listener.onSearchResultsChanged(filteredFonts.size(), filteredFonts.isEmpty());
        }
    }
    
    /**
     * فحص ما إذا كان خط معين يطابق استعلام البحث الحالي.
     *
     * ★ المقارنة تتم على الاسم بدون امتداد، متسقة مع applyCurrentFilter(). ★
     *
     * @param fontInfo معلومات الخط المراد فحصه
     * @return true إذا كان الخط يطابق استعلام البحث
     */
    public boolean matchesCurrentQuery(FontFileInfo fontInfo) {
        if (currentSearchQuery.isEmpty()) {
            return true;
        }
        
        if (fontInfo == null || fontInfo.getName() == null) {
            return false;
        }

        // ★ إزالة الامتداد قبل المقارنة — متسق مع applyCurrentFilter() ★
        String nameWithoutExtension = FileUtils.removeExtension(fontInfo.getName());
        String lowerFontName = nameWithoutExtension.toLowerCase(Locale.getDefault());
        String lowerQuery    = currentSearchQuery.toLowerCase(Locale.getDefault());
        
        return lowerFontName.contains(lowerQuery);
    }
}
