package com.example.oneuiapp.fontlist.localfont;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import com.example.oneuiapp.data.database.AppDatabase;
import com.example.oneuiapp.data.entity.FontEntity;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LocalFontCache - النسخة النهائية المحسّنة
 * 
 * التحسينات:
 * 1. استخدام Room Database بدلاً من SharedPreferences ✓
 * 2. استخدام resetAllCacheStatus() بدلاً من loop ✓
 * 3. تحميل ذكي مبني على الأولويات (most accessed first) ✓
 * 4. أداء محسّن للغاية ✓
 */
public class LocalFontCache {
    
    private static final String TAG = "LocalFontCache";
    private static LocalFontCache instance;
    
    private final ConcurrentHashMap<String, Typeface> memoryCache;
    private Context context;
    private AppDatabase database;
    private volatile boolean isInitialized = false;
    
    private LocalFontCache() {
        memoryCache = new ConcurrentHashMap<>(150);
    }
    
    public static synchronized LocalFontCache getInstance() {
        if (instance == null) {
            instance = new LocalFontCache();
        }
        return instance;
    }
    
    /**
     * التهيئة مع Room Database
     */
    public void initialize(Context context) {
        if (isInitialized) {
            return;
        }
        
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        
        Log.d(TAG, "LocalFontCache initializing with Room Database");
        
        // تحميل الخطوط المحفوظة من Room Database في الخلفية
        preloadCachedFontsFromDatabase();
        
        isInitialized = true;
    }
    
    /**
     * ★ تحميل ذكي: الأكثر استخداماً أولاً ★
     */
    private void preloadCachedFontsFromDatabase() {
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // قراءة الخطوط المحفوظة من Room Database
                List<FontEntity> cachedFonts = database.fontDao().getCachedFonts();
                
                if (cachedFonts == null || cachedFonts.isEmpty()) {
                    Log.d(TAG, "No cached fonts found in database");
                    return;
                }
                
                // تصفية: خطوط محلية فقط (ليست من النظام)
                cachedFonts.removeIf(FontEntity::isSystemFont);
                
                if (cachedFonts.isEmpty()) {
                    Log.d(TAG, "No cached local fonts found");
                    return;
                }
                
                Log.d(TAG, "Found " + cachedFonts.size() + " cached local fonts in database");
                
                int loadedCount = 0;
                
                // ترتيب ذكي: الأكثر استخداماً أولاً
                cachedFonts.sort((f1, f2) -> {
                    // الأولوية الأولى: عدد مرات الاستخدام
                    int countCompare = Integer.compare(f2.getAccessCount(), f1.getAccessCount());
                    if (countCompare != 0) return countCompare;
                    
                    // الأولوية الثانية: آخر استخدام
                    return Long.compare(f2.getLastAccessTime(), f1.getLastAccessTime());
                });
                
                for (FontEntity font : cachedFonts) {
                    String path = font.getPath();
                    
                    // تحميل الخط إلى الذاكرة
                    Typeface typeface = loadTypefaceFromFile(path);
                    
                    if (typeface != null) {
                        memoryCache.put(path, typeface);
                        loadedCount++;
                        
                        if (loadedCount % 10 == 0) {
                            Log.d(TAG, "Preloaded " + loadedCount + " fonts...");
                        }
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "★ Auto-preloaded " + loadedCount + " fonts in " + duration + "ms");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to preload cached fonts from database", e);
            }
        }, "LocalFontCache-Preloader").start();
    }
    
    public Typeface getIfCached(String fontPath) {
        if (fontPath == null) return null;
        return memoryCache.get(fontPath);
    }
    
    public Typeface getTypeface(String fontPath) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        
        // فحص الذاكرة أولاً
        Typeface cachedTypeface = memoryCache.get(fontPath);
        if (cachedTypeface != null) {
            recordAccessInDatabase(fontPath);
            return cachedTypeface;
        }
        
        // تحميل من الملف
        Typeface typeface = loadTypefaceFromFile(fontPath);
        
        if (typeface != null) {
            memoryCache.put(fontPath, typeface);
            markAsCachedInDatabase(fontPath);
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
    
    private Typeface loadTypefaceFromFile(String fontPath) {
        try {
            File fontFile = new File(fontPath);
            if (!fontFile.exists() || !fontFile.canRead()) {
                Log.w(TAG, "Font file does not exist or cannot be read: " + fontPath);
                return null;
            }
            
            return Typeface.createFromFile(fontFile);
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
        return memoryCache.size();
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
        Log.d(TAG, "Memory cache cleared");
    }
    
    /**
     * ★ النسخة المحسّنة: عملية SQL واحدة بدلاً من loop ★
     */
    public void clearCache() {
        memoryCache.clear();
        
        if (database != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    long timestamp = System.currentTimeMillis();
                    
                    // ★ عملية واحدة فقط تحدث كل الصفوف دفعة واحدة ★
                    // بدلاً من loop يعمل 300 عملية كتابة منفصلة
                    int rowsUpdated = database.fontDao().resetLocalFontsCacheStatus(timestamp);
                    
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
            
            Log.d(TAG, "Preloaded " + loadedCount + " fonts into memory");
        }, "LocalFontCache-BackgroundPreload").start();
    }
    
    /**
     * تحميل الخطوط الأكثر استخداماً
     */
    public void preloadMostUsedFonts(int limit) {
        if (database == null) return;
        
        new Thread(() -> {
            try {
                List<FontEntity> mostUsed = database.fontDao().getMostAccessedFonts(limit);
                
                int loadedCount = 0;
                for (FontEntity font : mostUsed) {
                    if (font.isSystemFont()) continue; // تخطي خطوط النظام
                    
                    String path = font.getPath();
                    if (getIfCached(path) == null) {
                        Typeface typeface = getTypeface(path);
                        if (typeface != null) {
                            loadedCount++;
                        }
                    }
                }
                
                Log.d(TAG, "Preloaded " + loadedCount + " most used fonts");
            } catch (Exception e) {
                Log.e(TAG, "Failed to preload most used fonts", e);
            }
        }, "LocalFontCache-MostUsedPreload").start();
    }
    }
