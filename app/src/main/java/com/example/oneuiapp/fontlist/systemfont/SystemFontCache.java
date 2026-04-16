package com.example.oneuiapp.fontlist.systemfont;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

import com.example.oneuiapp.data.database.AppDatabase;
import com.example.oneuiapp.data.entity.FontEntity;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemFontCache - النسخة النهائية المحسّنة
 * 
 * التحسينات:
 * 1. استخدام Room Database بدلاً من SharedPreferences ✓
 * 2. استخدام resetSystemFontsCacheStatus() بدلاً من loop ✓
 * 3. تحميل ذكي مبني على الأولويات ✓
 * 4. أداء محسّن للغاية ✓
 */
public class SystemFontCache {
    
    private static final String TAG = "SystemFontCache";
    private static SystemFontCache instance;
    
    private final ConcurrentHashMap<String, Typeface> memoryCache;
    private final ConcurrentHashMap<String, Typeface> weightedCache;
    private Context context;
    private AppDatabase database;
    private volatile boolean isInitialized = false;
    
    private SystemFontCache() {
        memoryCache = new ConcurrentHashMap<>(150);
        weightedCache = new ConcurrentHashMap<>(300);
    }
    
    public static synchronized SystemFontCache getInstance() {
        if (instance == null) {
            instance = new SystemFontCache();
        }
        return instance;
    }
    
    public void initialize(Context context) {
        if (isInitialized) {
            return;
        }
        
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        
        Log.d(TAG, "SystemFontCache initializing with Room Database");
        
        preloadCachedFontsFromDatabase();
        
        isInitialized = true;
    }
    
    /**
     * ★ تحميل ذكي: خطوط النظام الأكثر استخداماً أولاً ★
     */
    private void preloadCachedFontsFromDatabase() {
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                List<FontEntity> cachedSystemFonts = database.fontDao().getCachedFonts();
                
                if (cachedSystemFonts == null || cachedSystemFonts.isEmpty()) {
                    Log.d(TAG, "No cached system fonts found in database");
                    return;
                }
                
                // تصفية: خطوط النظام فقط
                cachedSystemFonts.removeIf(font -> !font.isSystemFont());
                
                if (cachedSystemFonts.isEmpty()) {
                    Log.d(TAG, "No cached system fonts after filtering");
                    return;
                }
                
                Log.d(TAG, "Found " + cachedSystemFonts.size() + " cached system fonts");
                
                int loadedCount = 0;
                
                // ترتيب ذكي: الأكثر استخداماً أولاً
                cachedSystemFonts.sort((f1, f2) -> {
                    int countCompare = Integer.compare(f2.getAccessCount(), f1.getAccessCount());
                    if (countCompare != 0) return countCompare;
                    return Long.compare(f2.getLastAccessTime(), f1.getLastAccessTime());
                });
                
                for (FontEntity font : cachedSystemFonts) {
                    String path = font.getPath();
                    
                    Typeface typeface = loadTypefaceInternal(path, 0, 0);
                    
                    if (typeface != null) {
                        memoryCache.put(path, typeface);
                        loadedCount++;
                        
                        if (loadedCount % 10 == 0) {
                            Log.d(TAG, "Preloaded " + loadedCount + " system fonts...");
                        }
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "★ Auto-preloaded " + loadedCount + " system fonts in " + duration + "ms");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to preload cached system fonts from database", e);
            }
        }, "SystemFontCache-Preloader").start();
    }
    
    public Typeface getIfCached(String fontPath) {
        if (fontPath == null) return null;
        return memoryCache.get(fontPath);
    }
    
    public Typeface getTypeface(String fontPath) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        
        Typeface cachedTypeface = memoryCache.get(fontPath);
        if (cachedTypeface != null) {
            recordAccessInDatabase(fontPath);
            return cachedTypeface;
        }
        
        Typeface typeface = loadTypefaceInternal(fontPath, 0, 0);
        
        if (typeface != null) {
            memoryCache.put(fontPath, typeface);
            markAsCachedInDatabase(fontPath);
        }
        
        return typeface;
    }
    
    public Typeface getTypefaceWithWeight(String fontPath, float weight, int ttcIndex) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        
        Log.d(TAG, "Loading typeface with weight: " + weight + ", ttcIndex: " + ttcIndex);
        
        Typeface typeface = loadTypefaceInternal(fontPath, weight, ttcIndex);
        
        if (typeface != null) {
            Log.d(TAG, "Successfully created typeface with weight: " + weight);
            markAsCachedInDatabase(fontPath);
        } else {
            Log.e(TAG, "Failed to create typeface with weight: " + weight);
        }
        
        return typeface;
    }
    
    private void markAsCachedInDatabase(String fontPath) {
        if (database == null) return;
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                long timestamp = System.currentTimeMillis();
                database.fontDao().updateCacheStatus(fontPath, true, timestamp);
                database.fontDao().recordAccess(fontPath, timestamp, timestamp);
            } catch (Exception e) {
                Log.w(TAG, "Failed to mark as cached in database: " + fontPath, e);
            }
        });
    }
    
    private void recordAccessInDatabase(String fontPath) {
        if (database == null) return;
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                long timestamp = System.currentTimeMillis();
                database.fontDao().recordAccess(fontPath, timestamp, timestamp);
            } catch (Exception e) {
                Log.w(TAG, "Failed to record access in database", e);
            }
        });
    }
    
    private Typeface loadTypefaceInternal(String fontPath, float weight, int ttcIndex) {
        File fontFile = new File(fontPath);
        if (!fontFile.exists() || !fontFile.canRead()) {
            Log.w(TAG, "Font file does not exist or cannot be read: " + fontPath);
            return null;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return loadTypefaceUsingBuilder(fontFile, weight, ttcIndex);
        } else {
            return loadTypefaceFromFile(fontPath);
        }
    }
    
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    private Typeface loadTypefaceUsingBuilder(File fontFile, float weight, int ttcIndex) {
        try {
            android.graphics.fonts.Font.Builder fontBuilder = 
                new android.graphics.fonts.Font.Builder(fontFile);
            
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                Log.d(TAG, "Set TTC index: " + ttcIndex);
            }
            
            if (weight > 0 && weight != 400) {
                String variationSettings = "'wght' " + weight;
                fontBuilder.setFontVariationSettings(variationSettings);
                Log.d(TAG, "Set font variation settings: " + variationSettings);
            }
            
            android.graphics.fonts.Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            return fallbackBuilder.build();
            
        } catch (Exception e) {
            Log.e(TAG, "Font.Builder failed", e);
            
            if (weight <= 0 || weight == 400) {
                return loadTypefaceFromFile(fontFile.getAbsolutePath());
            }
            
            return null;
        }
    }
    
    private Typeface loadTypefaceFromFile(String fontPath) {
        try {
            return Typeface.createFromFile(fontPath);
        } catch (Exception e) {
            Log.e(TAG, "Error loading font from file: " + fontPath, e);
            return null;
        }
    }
    
    public boolean wasLoadedBefore(String fontPath) {
        if (database == null) return false;
        
        try {
            FontEntity font = database.fontDao().getFontByPathSync(fontPath);
            return font != null && font.isCached();
        } catch (Exception e) {
            return false;
        }
    }
    
    public int getCachedFontsCount() {
        return memoryCache.size() + weightedCache.size();
    }
    
    public int getPersistedFontsCount() {
        if (database == null) return 0;
        
        try {
            return database.fontDao().getCachedFontsCount();
        } catch (Exception e) {
            return 0;
        }
    }
    
    public void clearMemoryCache() {
        memoryCache.clear();
        weightedCache.clear();
        Log.d(TAG, "Memory cache cleared");
    }
    
    /**
     * ★ النسخة المحسّنة: عملية SQL واحدة بدلاً من loop ★
     */
    public void clearCache() {
        memoryCache.clear();
        weightedCache.clear();
        
        if (database != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    long timestamp = System.currentTimeMillis();
                    
                    // ★ عملية واحدة فقط تحدث كل خطوط النظام دفعة واحدة ★
                    int rowsUpdated = database.fontDao().resetSystemFontsCacheStatus(timestamp);
                    
                    Log.d(TAG, "★ Full cache cleared efficiently: " + rowsUpdated + " rows updated");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to clear cache from database", e);
                }
            });
        }
    }
    
    public void removeFont(String fontPath) {
        if (fontPath == null) return;
        
        memoryCache.remove(fontPath);
        weightedCache.keySet().removeIf(key -> key.startsWith(fontPath + ":"));
        
        if (database != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    database.fontDao().updateCacheStatus(
                        fontPath, 
                        false, 
                        System.currentTimeMillis()
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Failed to remove from database cache", e);
                }
            });
        }
    }
    
    public void preloadFonts(List<String> fontPaths) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            return;
        }
        
        new Thread(() -> {
            int loadedCount = 0;
            for (String fontPath : fontPaths) {
                if (getIfCached(fontPath) == null) {
                    Typeface typeface = getTypeface(fontPath);
                    if (typeface != null) {
                        loadedCount++;
                    }
                }
            }
            
            Log.d(TAG, "Preloaded " + loadedCount + " system fonts into memory");
        }, "SystemFontCache-BackgroundPreload").start();
    }
}
