package com.example.oneuiapp.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;

import com.example.oneuiapp.R;
import com.example.oneuiapp.data.datastore.SettingsDataStore;

import java.util.Locale;

/**
 * SettingsHelper - نسخة محدّثة تعتمد بالكامل على DataStore
 * تم إزالة كل استخدام لـ SharedPreferences
 *
 * ملاحظة مهمة: تم حذف دالتَي getLayoutDirection و getLayoutDirectionFromLocale،
 * لأن الاتجاه (RTL/LTR) يُحدَّد تلقائياً من النظام بناءً على الـ Locale المضبوط،
 * وفرضه يدوياً كان يتعارض مع "خيارات المطورين" ويسبب خللاً في الاتجاهات.
 *
 * ★ تغيير جوهري في آلية اللغة (Android 13+): ★
 * - getLanguageMode() و getSystemAssignedLanguage() يقرآن مباشرة من LocaleManager
 *   الذي يُمثّل "مصدر الحقيقة" الوحيد لإعدادات اللغة.
 * - DataStore يُستخدم كقيمة احتياطية فقط على الأجهزة الأقدم من Android 13.
 * - هذا يضمن أن أي تغيير في لغة التطبيق من إعدادات النظام ينعكس فوراً
 *   على كل أجزاء التطبيق دون الحاجة لإعادة تشغيله.
 */
public class SettingsHelper {

    private static final String TAG = "SettingsHelper";

    // ═══════════════════════════════════════════════════════════════
    // الثوابت
    // ═══════════════════════════════════════════════════════════════
    
    public static final int LANGUAGE_SYSTEM = 0;
    public static final int LANGUAGE_ARABIC = 1;
    public static final int LANGUAGE_ENGLISH = 2;

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;

    public static final int FONT_SYSTEM = 0;
    public static final int FONT_WF = 1;
    public static final int FONT_SAMSUNG = 2;
    public static final int FONT_SST = 3;
    public static final int FONT_PRODUCT = 4;
    public static final int FONT_NEW = 5;
    public static final int FONT_DIN = 6;

    // ═══════════════════════════════════════════════════════════════
    // Getters (Static methods using DataStore)
    // ═══════════════════════════════════════════════════════════════

    /**
     * ★ الدالة الرئيسية لقراءة اللغة — مصدر الحقيقة هو LocaleManager ★
     *
     * على Android 13+: تقرأ مباشرة من LocaleManager (إعدادات النظام)،
     * مما يعني أن أي تغيير في لغة التطبيق من إعدادات النظام ينعكس فوراً.
     *
     * على ما دون Android 13: تقرأ من DataStore كالمعتاد.
     */
    public static int getLanguageMode(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getSystemAssignedLanguage(context);
        }
        try {
            return SettingsDataStore.getInstance(context)
                    .getLanguageMode()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading language mode", e);
            return LANGUAGE_SYSTEM;
        }
    }

    /**
     * ★ قراءة اللغة الحقيقية المطبقة حالياً من LocaleManager (Android 13+) ★
     *
     * هذه الدالة هي "مصدر الحقيقة" لإعدادات اللغة على Android 13 فما فوق.
     * تستعلم مباشرة من نظام التشغيل عن اللغة المعيّنة للتطبيق،
     * سواء تم تعيينها من داخل التطبيق أو من إعدادات النظام.
     *
     * الفائدة: حتى لو كان التطبيق لا يزال في الـ RAM وتم تغيير اللغة من
     * إعدادات النظام، ستعود هذه الدالة بالقيمة الصحيحة المحدّثة فوراً.
     *
     * على ما دون Android 13: تعود للقراءة من DataStore.
     */
    public static int getSystemAssignedLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.app.LocaleManager localeManager =
                        context.getSystemService(android.app.LocaleManager.class);
                if (localeManager != null) {
                    android.os.LocaleList locales = localeManager.getApplicationLocales();
                    if (!locales.isEmpty()) {
                        String lang = locales.get(0).getLanguage();
                        if (lang.equals("ar")) {
                            return LANGUAGE_ARABIC;
                        } else if (lang.equals("en")) {
                            return LANGUAGE_ENGLISH;
                        }
                    }
                }
                // إذا لم تكن هناك لغة مخصصة (قائمة فارغة)، فالتطبيق يتبع لغة الجهاز
                return LANGUAGE_SYSTEM;
            } catch (Exception e) {
                Log.e(TAG, "Error reading locale from LocaleManager", e);
                return LANGUAGE_SYSTEM;
            }
        }
        // للأجهزة الأقدم من Android 13، نقرأ من DataStore
        try {
            return SettingsDataStore.getInstance(context)
                    .getLanguageMode()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading language mode from DataStore", e);
            return LANGUAGE_SYSTEM;
        }
    }

    /**
     * الحصول على وضع الثيم الحالي
     * يستخدم blockingFirst للقراءة المتزامنة من DataStore
     */
    public static int getThemeMode(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getThemeMode()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading theme mode", e);
            return THEME_LIGHT;
        }
    }

    /**
     * فحص إذا كان الوضع التلقائي للثيم مفعّل
     */
    public static boolean isThemeAuto(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getThemeAuto()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading theme auto", e);
            return true;
        }
    }

    /**
     * ★ فحص إذا كان الثيم الشفاف (البطاقات المتباعدة) مفعّلاً ★
     * يُستخدم في الـ Adapters والـ Fragments لاختيار الـ Layout المناسب
     * ويُعطّل حسابات الزوايا الدائرية غير الضرورية لتوفير المعالجة
     */
    public static boolean isTransparentThemeEnabled(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getThemeTransparent()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading transparent theme", e);
            return false;
        }
    }
    
    /**
     * الحصول على وضع الخط الحالي
     */
    public static int getFontMode(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getFontMode()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading font mode", e);
            return FONT_SYSTEM;
        }
    }
    
    /**
     * فحص إذا كانت معاينة الخطوط مفعّلة
     */
    public static boolean isFontPreviewEnabled(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getFontPreviewEnabled()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading font preview setting", e);
            return true;
        }
    }
    
    /**
     * فحص إذا كانت الترجمة مفعّلة
     */
    public static boolean isTranslationEnabled(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getTranslationEnabled()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading translation setting", e);
            return false;
        }
    }
    
    /**
     * فحص إذا كانت الإشعارات مفعّلة
     */
    public static boolean areNotificationsEnabled(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getNotificationsEnabled()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading notifications setting", e);
            return true;
        }
    }
    
    /**
     * الحصول على نص المعاينة
     */
    public static String getPreviewText(Context context) {
        try {
            return SettingsDataStore.getInstance(context)
                    .getPreviewText()
                    .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error reading preview text", e);
            return SettingsDataStore.DEFAULT_PREVIEW_TEXT;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // إدارة الثيم
    // ═══════════════════════════════════════════════════════════════

    /**
     * تطبيق الثيم بناءً على الإعدادات الحالية
     */
    public static void applyTheme(Context context) {
        boolean isAuto = isThemeAuto(context);
        
        Log.d(TAG, "Applying theme - Auto mode: " + isAuto);
        
        if (isAuto) {
            Log.d(TAG, "Applying FOLLOW_SYSTEM mode");
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            int mode = getThemeMode(context);
            if (mode == THEME_DARK) {
                Log.d(TAG, "Applying DARK mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                Log.d(TAG, "Applying LIGHT mode");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // إدارة اللغة والـ Locale
    // ═══════════════════════════════════════════════════════════════

    /**
     * الحصول على Locale بناءً على الإعدادات الحالية
     * ★ على Android 13+: يقرأ من LocaleManager مباشرة (مصدر الحقيقة) ★
     */
    public static Locale getLocale(Context context) {
        int mode = getLanguageMode(context);
        
        switch (mode) {
            case LANGUAGE_ARABIC:
                return new Locale("ar");
            case LANGUAGE_ENGLISH:
                return new Locale("en");
            case LANGUAGE_SYSTEM:
            default:
                return getSystemLocale();
        }
    }

    /**
     * الحصول على Locale النظام
     */
    private static Locale getSystemLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Resources.getSystem().getConfiguration().getLocales().get(0);
        } else {
            return Resources.getSystem().getConfiguration().locale;
        }
    }

    /**
     * إنشاء Context ملفوف (Wrapped) باللغة المحددة
     *
     * ملاحظة: تعيين الـ Locale عبر config.setLocale() يكفي تماماً،
     * والنظام سيضبط اتجاه التنسيق (RTL/LTR) تلقائياً بناءً على اللغة.
     * لا حاجة لاستدعاء config.setLayoutDirection() بشكل يدوي.
     */
    @SuppressWarnings("deprecation")
    public static Context wrapContext(Context context) {
        Locale locale = getLocale(context);
        Locale.setDefault(locale);
        
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // تعيين الـ Locale فقط يكفي، وسيقوم النظام بضبط اتجاه التنسيق تلقائياً
            config.setLocale(locale);
            
            Log.d(TAG, "Context wrapped with locale: " + locale.getLanguage());
            
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // إدارة الخطوط
    // ═══════════════════════════════════════════════════════════════

    /**
     * الحصول على Typeface بناءً على وضع الخط المحدد
     */
    public static Typeface getTypeface(Context ctx) {
        int mode = getFontMode(ctx);
        
        try {
            switch (mode) {
                case FONT_WF:
                    return ResourcesCompat.getFont(ctx, R.font.wf_rglr);
                case FONT_SAMSUNG:
                    return ResourcesCompat.getFont(ctx, R.font.samsung_one);
                case FONT_SST:
                    return ResourcesCompat.getFont(ctx, R.font.sst_roman);
                case FONT_PRODUCT:
                    return ResourcesCompat.getFont(ctx, R.font.product_sans);
                case FONT_NEW:
                    return ResourcesCompat.getFont(ctx, R.font.new_sec_keypad);
                case FONT_DIN:
                    return ResourcesCompat.getFont(ctx, R.font.din_next_lt);
                case FONT_SYSTEM:
                default:
                    return null;
            }
        } catch (Exception e) {
            Log.w(TAG, "getTypeface failed, fallback to system", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════

    /**
     * تهيئة الإعدادات عند بدء التطبيق
     */
    public static void initializeFromSettings(Context context) {
        applyTheme(context);
        // اللغة تُطبّق تلقائياً عند استخدام wrapContext في BaseActivity
    }
        }
