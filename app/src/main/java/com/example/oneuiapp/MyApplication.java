package com.example.oneuiapp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.oneuiapp.data.database.AppDatabase;
import com.example.oneuiapp.data.datastore.SettingsDataStore;
import com.example.oneuiapp.data.repository.LocalFontRepository;
import com.example.oneuiapp.data.repository.SystemFontRepository;
import com.example.oneuiapp.fontlist.localfont.LocalFontCache;
import com.example.oneuiapp.fontlist.systemfont.SystemFontCache;
import com.example.oneuiapp.utils.CrashHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * MyApplication - النسخة المحسّنة لحل مشكلة وميض الثيم
 * تم إصلاح مشكلة ظهور الثيم الخطأ لأجزاء من الثانية عند بدء التطبيق
 * 
 * ═══════════════════════════════════════════════════════════════════
 * ★ الحل الكامل: قراءة إعدادات الثيم بشكل متزامن (Blocking) ★
 * ═══════════════════════════════════════════════════════════════════
 */
public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    private static MyApplication sInstance;
    private static final List<WeakReference<Activity>> activities = new ArrayList<>();
    
    private AppDatabase database;
    private SettingsDataStore settingsDataStore;
    private LocalFontRepository localFontRepository;
    private SystemFontRepository systemFontRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        
        // Initialize crash handler first
        CrashHandler.init(this);
        
        // ★★★ الحل الرئيسي: تهيئة الكاش مبكراً جداً ★★★
        initializeCaches();
        
        // ★★★ تهيئة DataStore هنا مباشرة (ليس في خيط خلفي) ★★★
        // السبب: نحتاجه فوراً لقراءة إعدادات الثيم
        initializeDataStore();
        
        // ★★★ تطبيق الثيم بشكل متزامن لمنع الوميض ★★★
        applyInitialTheme();
        
        // Initialize other components in background thread
        new Thread(() -> {
            try {
                initializeDatabase();
                initializeRepositories();
                
                Log.d(TAG, "All components initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error during initialization", e);
            }
        }).start();
        
        setupActivityTracking();
    }
    
    /**
     * تهيئة الكاش للخطوط
     */
    private void initializeCaches() {
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                LocalFontCache.getInstance().initialize(this);
                Log.d(TAG, "✓ LocalFontCache initialized");
                
                SystemFontCache.getInstance().initialize(this);
                Log.d(TAG, "✓ SystemFontCache initialized");
                
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "★★★ Caches initialized in " + duration + "ms");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize font caches", e);
            }
        }, "LocalFontCache-Initializer").start();
    }
    
    private void initializeDatabase() {
        database = AppDatabase.getInstance(this);
        Log.d(TAG, "AppDatabase initialized");
    }
    
    private void initializeDataStore() {
        if (settingsDataStore == null) {
            settingsDataStore = SettingsDataStore.getInstance(this);
            Log.d(TAG, "SettingsDataStore initialized");
        }
    }
    
    /**
     * تهيئة مستودعات البيانات مسبقاً في الخلفية
     * يضمن هذا أن الـ Singleton لكل مستودع جاهز في الذاكرة
     * قبل أن يحتاجه أي ViewModel، مما يُقلل العبء عند فتح الشاشات
     */
    private void initializeRepositories() {
        localFontRepository = LocalFontRepository.getInstance(this);
        Log.d(TAG, "LocalFontRepository initialized");
        
        systemFontRepository = SystemFontRepository.getInstance(this);
        Log.d(TAG, "SystemFontRepository initialized");
    }
    
    /**
     * ★★★ الحل الكامل لمشكلة الوميض ★★★
     * 
     * تطبيق الثيم بشكل متزامن (Blocking) قبل رسم أي واجهة
     * 
     * ملاحظة مهمة:
     * - استخدام blockingFirst() آمن هنا لأننا نقرأ قيمة واحدة صغيرة فقط
     * - العملية سريعة جداً (أقل من 10ms عادةً) ولن تسبب تجميد
     * - هذا يضمن تطبيق الثيم الصحيح قبل ظهور أي شاشة
     */
    private void applyInitialTheme() {
        try {
            // قراءة إعداد الثيم التلقائي بشكل متزامن
            Boolean isAuto = settingsDataStore.getThemeAuto().blockingFirst();
            
            if (isAuto != null && isAuto) {
                // الثيم تلقائي - اتبع إعدادات النظام
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                Log.d(TAG, "✓ Theme set to FOLLOW_SYSTEM (Blocking Mode)");
            } else {
                // الثيم يدوي - اقرأ الوضع المحدد
                Integer themeMode = settingsDataStore.getThemeMode().blockingFirst();
                
                if (themeMode != null && themeMode == 1) {
                    // وضع داكن
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_YES);
                    Log.d(TAG, "✓ Theme set to DARK (Blocking Mode)");
                } else {
                    // وضع فاتح (القيمة الافتراضية)
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO);
                    Log.d(TAG, "✓ Theme set to LIGHT (Blocking Mode)");
                }
            }
        } catch (Exception e) {
            // في حالة حدوث خطأ، استخدم إعدادات النظام كقيمة افتراضية آمنة
            Log.e(TAG, "Failed to apply initial theme synchronously", e);
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
    
    private void setupActivityTracking() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                activities.add(new WeakReference<>(activity));
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                for (int i = activities.size() - 1; i >= 0; i--) {
                    Activity a = activities.get(i).get();
                    if (a == null || a == activity) {
                        activities.remove(i);
                    }
                }
            }

            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
        });
    }
    
    public static MyApplication getInstance() {
        return sInstance;
    }
    
    public AppDatabase getDatabase() {
        if (database == null) {
            database = AppDatabase.getInstance(this);
        }
        return database;
    }
    
    public SettingsDataStore getSettingsDataStore() {
        if (settingsDataStore == null) {
            settingsDataStore = SettingsDataStore.getInstance(this);
        }
        return settingsDataStore;
    }
    
    public LocalFontRepository getLocalFontRepository() {
        if (localFontRepository == null) {
            localFontRepository = LocalFontRepository.getInstance(this);
        }
        return localFontRepository;
    }
    
    public SystemFontRepository getSystemFontRepository() {
        if (systemFontRepository == null) {
            systemFontRepository = SystemFontRepository.getInstance(this);
        }
        return systemFontRepository;
    }

    /**
     * إعادة بناء جميع الأنشطة الحية
     * تُستخدم عند تغيير الثيم أو الخط أو الإعدادات العامة التي تتطلب
     * إعادة رسم كامل للواجهة
     */
    public void recreateAllActivities() {
        for (WeakReference<Activity> ref : new ArrayList<>(activities)) {
            Activity act = ref.get();
            if (act != null && !act.isFinishing()) {
                act.recreate();
            }
        }
    }

    /**
     * ★ إعادة بناء جميع الأنشطة الحية باستثناء نشاط محدد ★
     *
     * تُستخدم بعد تغيير اللغة على Android 13+ لضمان أن الأنشطة في
     * الخلفية (كـ MainActivity) تحمل اللغة الجديدة، دون إعادة بناء
     * النشاط المستثنى (SettingsActivity) الذي يتلقى التغيير بالفعل
     * عبر onConfigurationChanged دون الحاجة لإعادة بناء كاملة.
     *
     * السبب: عند تغيير الثيم قبل تغيير اللغة، تُعاد بناء الأنشطة الخلفية
     * باللغة القديمة. بعد تغيير اللغة، قد لا يُعيد النظام بناءها تلقائياً
     * مرة ثانية. هذه الدالة تضمن إعادة بنائها باللغة الجديدة.
     *
     * @param excludeActivity النشاط الذي يجب استثناؤه من إعادة البناء
     */
    public void recreateAllActivitiesExcept(Activity excludeActivity) {
        for (WeakReference<Activity> ref : new ArrayList<>(activities)) {
            Activity act = ref.get();
            if (act != null && !act.isFinishing() && act != excludeActivity) {
                act.recreate();
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning received");
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "Application terminated");
    }
}
