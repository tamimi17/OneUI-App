package com.example.oneuiapp.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.View;

import java.util.Locale;

/**
 * LanguageHelper - المدير الوحيد لكل ما يخص اللغة والاتجاهات
 *
 * ★ مبدأ "فصل الاهتمامات" (Separation of Concerns): ★
 * يجمع هذا الملف كل منطق اللغة في مكان واحد بدلاً من تشتيته عبر الملفات،
 * مما يجعل الصيانة والإصلاح سهلاً: أي تعديل يتم هنا وينعكس على كل التطبيق.
 *
 * المسؤوليات الثلاث لهذا الملف:
 * 1. تطبيق اللغة عبر LocaleManager (Android 13+) أو Locale.setDefault (ما دونه)
 * 2. إجبار الـ Activity أو الـ View على تحديث اتجاهه (RTL/LTR) فوراً
 * 3. إنشاء Context جديد يحمل اللغة المحدّثة لسحب النصوص المترجمة الصحيحة
 */
public class LanguageHelper {

    private static final String TAG = "LanguageHelper";

    // ═══════════════════════════════════════════════════════════════
    // تطبيق اللغة
    // ═══════════════════════════════════════════════════════════════

    /**
     * ★ تطبيق اللغة المختارة على مستوى التطبيق بالكامل ★
     *
     * Android 13+: يُفوّض إلى LocaleManager من الـ Framework مباشرةً.
     *   (لا نستخدم AppCompatDelegate.setApplicationLocales لأن AppCompat 1.6.0
     *   غير متاح مع مكتبة One UI 4)
     *   بعد الاستدعاء، يقوم النظام تلقائياً بإعادة بناء الأنشطة التي لا تملك
     *   locale في configChanges (كـ MainActivity و HomeActivity)،
     *   بينما تتلقى SettingsActivity الحدث عبر onConfigurationChanged فقط.
     *
     * ما دون Android 13: يضبط Locale.setDefault فقط.
     *   الانعكاس الكامل على الواجهة يتم عبر RECREATE_ALL_ACTIVITIES في SettingsViewModel.
     *
     * @param context      سياق التطبيق
     * @param languageMode SettingsHelper.LANGUAGE_SYSTEM / LANGUAGE_ARABIC / LANGUAGE_ENGLISH
     */
    public static void applyLanguage(Context context, int languageMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.app.LocaleManager localeManager =
                        context.getSystemService(android.app.LocaleManager.class);
                if (localeManager != null) {
                    switch (languageMode) {
                        case SettingsHelper.LANGUAGE_ARABIC:
                            localeManager.setApplicationLocales(
                                    android.os.LocaleList.forLanguageTags("ar"));
                            break;
                        case SettingsHelper.LANGUAGE_ENGLISH:
                            localeManager.setApplicationLocales(
                                    android.os.LocaleList.forLanguageTags("en"));
                            break;
                        case SettingsHelper.LANGUAGE_SYSTEM:
                        default:
                            // إعادة الـ Locale لإعداد النظام الافتراضي
                            localeManager.setApplicationLocales(
                                    android.os.LocaleList.getEmptyLocaleList());
                            break;
                    }
                    Log.d(TAG, "Language applied via LocaleManager: mode=" + languageMode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply language via LocaleManager", e);
            }
        } else {
            // ما دون Android 13: ضبط Locale.setDefault فقط.
            // الانعكاس الكامل يحتاج RECREATE (يُرسل من SettingsViewModel).
            Locale locale = resolveLocale(languageMode);
            Locale.setDefault(locale);
            Log.d(TAG, "Language applied via Locale.setDefault (pre-13): " + locale.getLanguage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // تحديث الاتجاه (RTL / LTR)
    // ═══════════════════════════════════════════════════════════════

    /**
     * ★ إجبار جذر نافذة الـ Activity على تطبيق الاتجاه الجديد فوراً ★
     *
     * يحلّ مشكلة: عند اختيار "System Default"، يتغير الـ Locale لكن
     * DecorView لا يُحدَّث اتجاهه لأن الـ Activity لم يُعَد بناؤه.
     * يحلّ أيضاً تأخر الاتجاه عند التبديل بين أي لغتين.
     *
     * يُستدعى من onConfigurationChanged في الـ Activity.
     *
     * @param activity  الـ Activity المراد تحديث اتجاهه
     * @param newConfig الـ Configuration الجديد الذي يحمل الاتجاه المحدّث
     */
    public static void forceUpdateLayoutDirection(Activity activity, Configuration newConfig) {
        if (activity == null || newConfig == null) return;

        // ★ نشتق الاتجاه من الـ Locale الفعلي المُحلَّل بدلاً من getLayoutDirection() ★
        //
        // السبب: عند اختيار System Default، يُعيد getLayoutDirection() أحياناً
        // الاتجاه القديم لأنه يعكس لحظة انتقالية في الـ Configuration.
        // أما TextUtils.getLayoutDirectionFromLocale() فيحسب الاتجاه مباشرةً
        // من الـ Locale النهائي المُحلَّل في newConfig، مما يضمن الصحة دائماً
        // بما في ذلك حالة System Default.
        Locale resolvedLocale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resolvedLocale = newConfig.getLocales().get(0);
        } else {
            resolvedLocale = newConfig.locale;
        }

        int newDirection = android.text.TextUtils.getLayoutDirectionFromLocale(resolvedLocale);
        activity.getWindow().getDecorView().setLayoutDirection(newDirection);

        Log.d(TAG, "Activity layout direction forced from locale '" +
                (resolvedLocale != null ? resolvedLocale.getLanguage() : "null") + "': " +
                (newDirection == View.LAYOUT_DIRECTION_RTL ? "RTL" : "LTR"));
    }

    /**
     * ★ إجبار View معين على تطبيق الاتجاه الجديد فوراً ★
     *
     * نسخة مخصصة للـ Fragments التي لا تملك Window مباشرة.
     * يُستدعى من onConfigurationChanged في الـ Fragment على الـ View الجذر.
     *
     * @param rootView  الـ View الجذر للـ Fragment
     * @param newConfig الـ Configuration الجديد
     */
    public static void forceUpdateLayoutDirectionForView(View rootView, Configuration newConfig) {
        if (rootView == null || newConfig == null) return;

        // ★ نفس المنطق: نشتق الاتجاه من الـ Locale الفعلي لضمان صحة System Default ★
        Locale resolvedLocale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resolvedLocale = newConfig.getLocales().get(0);
        } else {
            resolvedLocale = newConfig.locale;
        }

        int newDirection = android.text.TextUtils.getLayoutDirectionFromLocale(resolvedLocale);
        rootView.setLayoutDirection(newDirection);

        Log.d(TAG, "View layout direction forced from locale '" +
                (resolvedLocale != null ? resolvedLocale.getLanguage() : "null") + "': " +
                (newDirection == View.LAYOUT_DIRECTION_RTL ? "RTL" : "LTR"));
    }

    // ═══════════════════════════════════════════════════════════════
    // إنشاء Context محدّث باللغة الجديدة
    // ═══════════════════════════════════════════════════════════════

    /**
     * ★ إنشاء Context جديد يحمل اللغة المحدّثة بشكل مضمون ★
     *
     * يُستخدم في onConfigurationChanged لسحب النصوص المترجمة للغة الجديدة،
     * متجاوزاً السياق القديم الذي أنشأته attachBaseContext() وقت بدء التطبيق
     * والذي يظل حاملاً للغة القديمة في الذاكرة.
     *
     * @param context   أي سياق صالح (Activity, Fragment, Application)
     * @param newConfig الـ Configuration الجديد الذي يحمل اللغة المحدّثة
     * @return سياق جديد يقرأ الموارد (strings, arrays) باللغة الجديدة
     */
    public static Context createFreshContext(Context context, Configuration newConfig) {
        return context.createConfigurationContext(newConfig);
    }

    // ═══════════════════════════════════════════════════════════════
    // استرجاع الاتجاه المحسوب
    // ═══════════════════════════════════════════════════════════════

    /**
     * ★ إعادة الاتجاه المحسوب من الـ Locale الفعلي في newConfig ★
     *
     * تُستخدم من الخارج (مثل SettingsActivity) لتطبيق الاتجاه على
     * Views إضافية كـ ToolbarLayout بعد استدعاء forceUpdateLayoutDirection.
     *
     * @param newConfig الـ Configuration الجديد الحامل للغة المحدّثة
     * @return View.LAYOUT_DIRECTION_RTL أو View.LAYOUT_DIRECTION_LTR
     */
    public static int resolveLayoutDirection(Configuration newConfig) {
        if (newConfig == null) return View.LAYOUT_DIRECTION_LTR;

        Locale resolvedLocale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resolvedLocale = newConfig.getLocales().get(0);
        } else {
            resolvedLocale = newConfig.locale;
        }

        return android.text.TextUtils.getLayoutDirectionFromLocale(resolvedLocale);
    }

    // ═══════════════════════════════════════════════════════════════
    // دوال مساعدة داخلية
    // ═══════════════════════════════════════════════════════════════

    /**
     * تحويل رقم وضع اللغة إلى كائن Locale
     */
    private static Locale resolveLocale(int languageMode) {
        switch (languageMode) {
            case SettingsHelper.LANGUAGE_ARABIC:
                return new Locale("ar");
            case SettingsHelper.LANGUAGE_ENGLISH:
                return new Locale("en");
            case SettingsHelper.LANGUAGE_SYSTEM:
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return Resources.getSystem().getConfiguration().getLocales().get(0);
                } else {
                    return Resources.getSystem().getConfiguration().locale;
                }
        }
    }
}
