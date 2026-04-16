package com.example.oneuiapp.fontlist.systemfont;

import android.content.Context;
import android.util.Log;

import com.example.oneuiapp.data.datastore.SettingsDataStore;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SystemFontPreferenceManager - نسخة DataStore
 * تم إزالة SharedPreferences بالكامل واستبدالها بـ SettingsDataStore
 * 
 * إدارة تفضيلات شاشة خطوط النظام
 * يحفظ آخر خط تم فتحه من خطوط النظام
 */
public class SystemFontPreferenceManager {
    
    private static final String TAG = "SystemFontPrefManager";
    private final SettingsDataStore dataStore;
    
    // كاش محلي للقراءة السريعة في RecyclerView
    private String cachedLastOpenedPath;
    
    public SystemFontPreferenceManager(Context context) {
        this.dataStore = SettingsDataStore.getInstance(context);
        
        // تحميل أولي للكاش
        try {
            cachedLastOpenedPath = dataStore.getLastOpenedSystemFontPath().blockingFirst();
        } catch (Exception e) {
            cachedLastOpenedPath = null;
            Log.d(TAG, "No cached last opened system font");
        }
    }
    
    /**
     * حفظ مسار آخر خط نظام تم فتحه
     * @param fontPath مسار الخط
     */
    public void saveLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path");
            return;
        }
        
        // تحديث الكاش المحلي فوراً
        cachedLastOpenedPath = fontPath;
        
        // الحفظ في DataStore في الخلفية
        dataStore.setLastOpenedSystemFontPath(fontPath)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved last opened system font: " + fontPath),
                    error -> Log.e(TAG, "Error saving last opened system font", error)
                );
    }
    
    /**
     * الحصول على مسار آخر خط نظام تم فتحه
     * @return مسار الخط أو null إذا لم يكن محفوظاً
     */
    public String getLastOpenedFont() {
        try {
            // تحديث من DataStore لضمان الدقة
            cachedLastOpenedPath = dataStore.getLastOpenedSystemFontPath().blockingFirst();
            return cachedLastOpenedPath;
        } catch (Exception e) {
            Log.d(TAG, "No last opened system font found");
            return null;
        }
    }
    
    /**
     * فحص ما إذا كان خط معين هو آخر خط نظام تم فتحه
     * يستخدم الكاش المحلي للسرعة (مناسب للاستخدام في RecyclerView)
     * @param fontPath مسار الخط المراد فحصه
     * @return true إذا كان هذا الخط هو آخر خط نظام تم فتحه
     */
    public boolean isLastOpenedFont(String fontPath) {
        if (fontPath == null) {
            return false;
        }
        
        // استخدام الكاش للسرعة
        return cachedLastOpenedPath != null && cachedLastOpenedPath.equals(fontPath);
    }
    
    /**
     * مسح آخر خط نظام تم فتحه من التفضيلات
     */
    public void clearLastOpenedFont() {
        cachedLastOpenedPath = null;
        
        dataStore.setLastOpenedSystemFontPath(null)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Cleared last opened system font"),
                    error -> Log.e(TAG, "Error clearing last opened system font", error)
                );
    }
}
