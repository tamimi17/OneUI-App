package com.example.oneuiapp.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.oneuiapp.data.entity.FontEntity;

import java.util.List;

/**
 * FontDao - محسّن مع دوال فعّالة لإدارة الكاش
 *
 * ★ الإصدار 3: إضافة استعلامات المفضلة ★
 */
@Dao
public interface FontDao {
    
    // ════════════════════════════════════════════════════════════
    // عمليات الإدراج والتحديث والحذف الأساسية
    // ════════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(FontEntity font);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<FontEntity> fonts);
    
    @Update
    int update(FontEntity font);
    
    @Delete
    int delete(FontEntity font);
    
    @Query("DELETE FROM fonts WHERE id = :fontId")
    int deleteById(long fontId);
    
    @Query("DELETE FROM fonts WHERE path = :path")
    int deleteByPath(String path);
    
    @Query("DELETE FROM fonts WHERE is_system_font = 0")
    int deleteAllLocalFonts();
    
    @Query("DELETE FROM fonts WHERE is_system_font = 1")
    int deleteAllSystemFonts();
    
    @Query("DELETE FROM fonts")
    int deleteAll();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الاسترجاع الأساسية
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM fonts WHERE id = :fontId")
    LiveData<FontEntity> getFontById(long fontId);
    
    @Query("SELECT * FROM fonts WHERE id = :fontId")
    FontEntity getFontByIdSync(long fontId);
    
    @Query("SELECT * FROM fonts WHERE path = :path LIMIT 1")
    LiveData<FontEntity> getFontByPath(String path);
    
    @Query("SELECT * FROM fonts WHERE path = :path LIMIT 1")
    FontEntity getFontByPathSync(String path);
    
    @Query("SELECT * FROM fonts")
    LiveData<List<FontEntity>> getAllFonts();
    
    @Query("SELECT * FROM fonts")
    List<FontEntity> getAllFontsSync();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات حسب النوع (خطوط النظام / المجلدات)
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getSystemFonts();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    List<FontEntity> getSystemFontsSync();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getLocalFonts();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 0 ORDER BY file_name ASC")
    List<FontEntity> getLocalFontsSync();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الفرز
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFontsSortedByName(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem ORDER BY file_name DESC")
    LiveData<List<FontEntity>> getFontsSortedByNameDesc(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem ORDER BY last_modified ASC")
    LiveData<List<FontEntity>> getFontsSortedByDate(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem ORDER BY last_modified DESC")
    LiveData<List<FontEntity>> getFontsSortedByDateDesc(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem ORDER BY size ASC")
    LiveData<List<FontEntity>> getFontsSortedBySize(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem ORDER BY size DESC")
    LiveData<List<FontEntity>> getFontsSortedBySizeDesc(boolean isSystem);
    
    // ════════════════════════════════════════════════════════════
    // استعلامات البحث
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    LiveData<List<FontEntity>> searchFonts(boolean isSystem, String query);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    List<FontEntity> searchFontsSync(boolean isSystem, String query);
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الكاش والاستخدام
    // ════════════════════════════════════════════════════════════
    
    @Query("UPDATE fonts SET is_cached = :isCached, updated_at = :timestamp WHERE path = :path")
    int updateCacheStatus(String path, boolean isCached, long timestamp);
    
    @Query("UPDATE fonts SET last_access_time = :accessTime, " +
           "access_count = access_count + 1, updated_at = :timestamp " +
           "WHERE path = :path")
    int recordAccess(String path, long accessTime, long timestamp);
    
    @Query("SELECT * FROM fonts WHERE is_cached = 1 ORDER BY last_access_time DESC")
    List<FontEntity> getCachedFonts();
    
    @Query("SELECT * FROM fonts ORDER BY access_count DESC LIMIT :limit")
    List<FontEntity> getMostAccessedFonts(int limit);
    
    @Query("SELECT * FROM fonts ORDER BY last_access_time DESC LIMIT :limit")
    List<FontEntity> getRecentlyAccessedFonts(int limit);
    
    // ════════════════════════════════════════════════════════════
    // ★ دوال محسّنة لمسح الكاش (عملية واحدة بدلاً من loop) ★
    // ════════════════════════════════════════════════════════════
    
    /**
     * مسح حالة الكاش لجميع الخطوط دفعة واحدة (عملية SQL واحدة فقط)
     * هذا أسرع بكثير من loop على كل خط
     */
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp")
    int resetAllCacheStatus(long timestamp);
    
    /**
     * مسح حالة الكاش لخطوط النظام فقط دفعة واحدة
     */
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp WHERE is_system_font = 1")
    int resetSystemFontsCacheStatus(long timestamp);
    
    /**
     * مسح حالة الكاش للخطوط المحلية فقط دفعة واحدة
     */
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp WHERE is_system_font = 0")
    int resetLocalFontsCacheStatus(long timestamp);
    
    // ════════════════════════════════════════════════════════════
    // استعلامات الإحصائيات
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT COUNT(*) FROM fonts")
    LiveData<Integer> getTotalFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts")
    int getTotalFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 1")
    LiveData<Integer> getSystemFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 1")
    int getSystemFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 0")
    LiveData<Integer> getLocalFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 0")
    int getLocalFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_variable_font = 1")
    int getVariableFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_cached = 1")
    int getCachedFontsCount();
    
    @Query("SELECT SUM(size) FROM fonts WHERE is_system_font = 0")
    long getTotalLocalFontsSize();
    
    // ════════════════════════════════════════════════════════════
    // استعلامات خاصة
    // ════════════════════════════════════════════════════════════
    
    @Query("SELECT EXISTS(SELECT 1 FROM fonts WHERE path = :path)")
    boolean fontExists(String path);
    
    @Query("SELECT * FROM fonts WHERE is_variable_font = 1 ORDER BY file_name ASC")
    List<FontEntity> getVariableFonts();
    
    @Query("UPDATE fonts SET real_name = :realName, updated_at = :timestamp WHERE path = :path")
    int updateRealName(String path, String realName, long timestamp);
    
    @Query("SELECT * FROM fonts WHERE real_name IS NULL OR real_name = '' LIMIT :limit")
    List<FontEntity> getFontsWithoutRealName(int limit);
    
    // ════════════════════════════════════════════════════════════
    // ★ دوال إضافية لميزة التحديد المتعدد والحذف وإعادة التسمية ★
    // ════════════════════════════════════════════════════════════
    
    /**
     * تحديث مسار الخط واسم الملف بعد إعادة التسمية
     * تُستخدم في عملية إعادة التسمية لتحديث البيانات في قاعدة البيانات
     */
    @Query("UPDATE fonts SET path = :newPath, file_name = :newFileName, updated_at = :updatedAt WHERE path = :oldPath")
    void updatePath(String oldPath, String newPath, String newFileName, long updatedAt);

    // ════════════════════════════════════════════════════════════
    // ★ استعلامات وصف الوزن والعرض (weight_width_label) ★
    // ════════════════════════════════════════════════════════════

    /**
     * تحديث وصف الوزن والعرض لخط محدد بمساره.
     * يُستدعى بعد الاستخراج عبر FontWeightWidthExtractor.
     *
     * @param path      مسار ملف الخط
     * @param label     الوصف النصي ("Bold, Condensed" أو "VF · Regular" إلخ)
     * @param timestamp وقت التحديث بالميلي ثانية
     */
    @Query("UPDATE fonts SET weight_width_label = :label, updated_at = :timestamp WHERE path = :path")
    int updateWeightWidthLabel(String path, String label, long timestamp);

    /**
     * جلب الخطوط التي لم يُستخرج وصف وزنها وعرضها بعد.
     * تُستخدم في الاستخراج التدريجي في الخلفية.
     *
     * @param limit الحد الأقصى لعدد النتائج المُعادة دفعةً واحدة
     */
    @Query("SELECT * FROM fonts WHERE weight_width_label IS NULL LIMIT :limit")
    List<FontEntity> getFontsWithoutWeightWidth(int limit);

    // ════════════════════════════════════════════════════════════
    // ★ استعلامات المفضلة (الإصدار 3) ★
    // ════════════════════════════════════════════════════════════

    /**
     * جلب جميع الخطوط المفضلة (المحلية فقط) مرتبةً أبجدياً.
     * تُستخدم في FavoriteFontListFragment عبر FavoriteFontListViewModel.
     * Room يُحدِّث النتيجة تلقائياً عند تغيير is_favorite في قاعدة البيانات.
     */
    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_system_font = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFavoriteFonts();

    /**
     * جلب الخطوط المفضلة بشكل متزامن (للاستخدام في خيوط الخلفية)
     */
    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_system_font = 0 ORDER BY file_name ASC")
    List<FontEntity> getFavoriteFontsSync();

    /**
     * تحديث حالة المفضلة لخط محدد بمساره.
     * تُستدعى من LocalFontRepository.updateFavoriteStatus() على خيط خلفي.
     *
     * @param path      مسار ملف الخط
     * @param isFavorite true لإضافة للمفضلة، false للإزالة
     * @param timestamp  وقت التحديث بالميلي ثانية
     */
    @Query("UPDATE fonts SET is_favorite = :isFavorite, updated_at = :timestamp WHERE path = :path")
    int updateFavoriteStatus(String path, boolean isFavorite, long timestamp);

    /**
     * البحث في الخطوط المفضلة بالاسم.
     * تُستخدم لدعم ميزة البحث في قائمة المفضلة.
     *
     * @param query نص البحث
     */
    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_system_font = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    LiveData<List<FontEntity>> searchFavoriteFonts(String query);

    /**
     * عدد الخطوط المفضلة — يُستخدم لتحديث العنوان الفرعي في الدرج.
     */
    @Query("SELECT COUNT(*) FROM fonts WHERE is_favorite = 1")
    LiveData<Integer> getFavoriteFontsCount();

    /**
     * عدد الخطوط المفضلة بشكل متزامن
     */
    @Query("SELECT COUNT(*) FROM fonts WHERE is_favorite = 1")
    int getFavoriteFontsCountSync();

    /**
     * التحقق من كون خط مفضلاً بمساره
     *
     * @param path مسار ملف الخط
     */
    @Query("SELECT is_favorite FROM fonts WHERE path = :path LIMIT 1")
    boolean isFontFavorite(String path);
}
