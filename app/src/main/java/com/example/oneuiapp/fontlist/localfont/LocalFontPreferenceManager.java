package com.example.oneuiapp.fontlist.localfont;

import android.content.Context;
import android.util.Log;

import com.example.oneuiapp.data.datastore.SettingsDataStore;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * LocalFontPreferenceManager - نسخة DataStore
 * تم إزالة SharedPreferences بالكامل واستبدالها بـ SettingsDataStore
 * 
 * التحسينات:
 * - استخدام DataStore للتخزين الدائم
 * - كاش محلي للقراءة السريعة في RecyclerView
 * - عمليات الكتابة غير متزامنة (Async)
 */
public class LocalFontPreferenceManager {
    
    private static final String TAG = "LocalFontPreferenceManager";
    private final SettingsDataStore dataStore;
    
    // كاش محلي سريع لتجنب استدعاءات متعددة في RecyclerView
    private String cachedLastOpenedPath;
    
    public LocalFontPreferenceManager(Context context) {
        this.dataStore = SettingsDataStore.getInstance(context);
        
        // تحميل أولي للكاش
        try {
            cachedLastOpenedPath = dataStore.getLastOpenedFontPath().blockingFirst();
        } catch (Exception e) {
            cachedLastOpenedPath = null;
            Log.d(TAG, "No cached last opened font");
        }
    }
    
    /**
     * حفظ مسار آخر خط تم فتحه
     * @param fontPath مسار الخط
     */
    public void saveLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path");
            return;
        }
        
        // تحديث الكاش المحلي فوراً للسرعة
        cachedLastOpenedPath = fontPath;
        
        // الحفظ في DataStore في الخلفية
        dataStore.setLastOpenedFontPath(fontPath)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved last opened font: " + fontPath),
                    error -> Log.e(TAG, "Error saving last opened font", error)
                );
    }
    
    /**
     * الحصول على مسار آخر خط تم فتحه
     * @return مسار الخط أو null إذا لم يكن محفوظاً
     */
    public String getLastOpenedFont() {
        try {
            // تحديث من DataStore لضمان الدقة
            cachedLastOpenedPath = dataStore.getLastOpenedFontPath().blockingFirst();
            return cachedLastOpenedPath;
        } catch (Exception e) {
            Log.d(TAG, "No last opened font found");
            return null;
        }
    }
    
    /**
     * فحص ما إذا كان خط معين هو آخر خط تم فتحه
     * يستخدم الكاش المحلي للسرعة (مناسب للاستخدام في RecyclerView)
     * @param fontPath مسار الخط المراد فحصه
     * @return true إذا كان هذا الخط هو آخر خط تم فتحه
     */
    public boolean isLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            return false;
        }
        
        // استخدام الكاش للسرعة
        return cachedLastOpenedPath != null && cachedLastOpenedPath.equals(fontPath);
    }
    
    /**
     * مسح آخر خط تم فتحه من التفضيلات
     */
    public void clearLastOpenedFont() {
        cachedLastOpenedPath = null;
        
        dataStore.setLastOpenedFontPath(null)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Cleared last opened font"),
                    error -> Log.e(TAG, "Error clearing last opened font", error)
                );
    }
    
    /**
     * حفظ مسار مجلد الخطوط المختار
     * @param folderPath المسار المباشر للمجلد
     */
    public void saveFontFolderPath(String folderPath) {
        if (folderPath == null) {
            Log.w(TAG, "Attempted to save null folder path");
            return;
        }
        
        dataStore.setFolderPath(folderPath)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font folder path: " + folderPath),
                    error -> Log.e(TAG, "Error saving folder path", error)
                );
    }
    
    /**
     * الحصول على مسار مجلد الخطوط المختار
     * @return المسار المباشر للمجلد أو null إذا لم يكن محفوظاً
     */
    public String getFontFolderPath() {
        try {
            return dataStore.getFolderPath().blockingFirst();
        } catch (Exception e) {
            Log.d(TAG, "No folder path found");
            return null;
        }
    }
    
    /**
     * فحص ما إذا كان مسار مجلد الخطوط محفوظاً
     * @return true إذا كان المجلد محفوظاً
     */
    public boolean hasFontFolderPath() {
        return getFontFolderPath() != null;
    }
    
    /**
     * مسح مسار مجلد الخطوط من التفضيلات
     */
    public void clearFontFolderPath() {
        dataStore.setFolderPath(null)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Cleared font folder path"),
                    error -> Log.e(TAG, "Error clearing folder path", error)
                );
    }
}
