package com.example.oneuiapp.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

import dev.oneuiproject.oneui.preference.HorizontalRadioPreference;
import dev.oneuiproject.oneui.widget.Toast;

import com.example.oneuiapp.R;
import com.example.oneuiapp.MyApplication;
import com.example.oneuiapp.activity.AboutActivity;
import com.example.oneuiapp.utils.LanguageHelper;
import com.example.oneuiapp.utils.SettingsHelper;
import com.example.oneuiapp.viewmodel.SettingsViewModel;

/**
 * SettingsFragment - نسخة محدّثة تعتمد بالكامل على DataStore
 * تم إزالة كل استخدام مباشر لـ SharedPreferences
 *
 * ملاحظة مهمة: تم حذف جميع دوال فرض اتجاه النص والتنسيق يدوياً
 * (applyCorrectTextDirection وما يتبعها)، لأن النظام يتكفل بذلك
 * تلقائياً بناءً على الـ Locale الصحيح المضبوط في Context.
 * فرضها يدوياً كان يتعارض مع "خيارات المطورين" ويسبب خللاً في الاتجاهات.
 *
 * ★ آلية تغيير اللغة بدون وميض (Android 13+): ★
 * - بما أن locale|layoutDirection مُضاف إلى configChanges في المانيفيست
 *   لـ SettingsActivity فقط، لن يُدمَّر هذا الـ Fragment عند تغيير اللغة.
 * - onConfigurationChanged يُستدعى بدلاً من ذلك، ويقوم بـ:
 *   1. إنشاء freshContext عبر LanguageHelper يحمل اللغة الجديدة.
 *   2. تحديث mContext لاستخدامه في العمليات اللاحقة.
 *   3. إجبار View الجذر على تغيير اتجاهه (RTL/LTR) فوراً عبر LanguageHelper.
 *   4. تحديث النصوص فقط دون المساس بالصور أو الرسومات أو البطاقات.
 *
 * ★ جوهر مشكلة النصوص وحلها: ★
 * attachBaseContext() في BaseActivity يُنشئ Context ملفوفاً باللغة القديمة
 * مرة واحدة عند بدء التطبيق. حتى بعد تغيير اللغة عبر LocaleManager،
 * يظل هذا الـ Context القديم هو مصدر getString() في الـ Fragment.
 * الحل: LanguageHelper.createFreshContext(newConfig) يُنشئ سياقاً مستقلاً
 * يقرأ الموارد باللغة الجديدة بشكل مضمون.
 *
 * ★ مزامنة اللغة مع إعدادات النظام: ★
 * - onResume يقرأ اللغة الفعلية من LocaleManager في كل مرة يُستأنَم فيها الـ Fragment،
 *   مما يضمن تحديث الـ Dropdown ليعكس أي تغيير جرى من إعدادات النظام.
 * - عند اكتشاف تغيير اللغة من إعدادات النظام، يُجدول إعادة بناء الأنشطة الخلفية
 *   التي لم يُعدها النظام تلقائياً بسبب اعتراض SettingsActivity لحدث locale.
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    
    private static final String TAG = "SettingsFragment";
    
    private Context mContext;
    private SettingsViewModel viewModel;

    // ── عناصر الواجهة الرئيسية ──
    private DropDownPreference languagePreference;
    private HorizontalRadioPreference themePreference;
    private SwitchPreferenceCompat themeAutoPreference;
    // ★ الثيم الشفاف: مستقل تماماً عن themePreference و themeAutoPreference ★
    private SwitchPreferenceCompat themeTransparentPreference;
    private DropDownPreference fontPreference;
    private SwitchPreferenceCompat fontPreviewPreference;
    private SwitchPreferenceCompat translationPreference;
    private SwitchPreferenceCompat notificationsPreference;
    private EditTextPreference previewTextPreference;

    // ★ مراجع لأقسام الإعدادات — ضرورية لتحديث عناوينها يدوياً في onConfigurationChanged ★
    private PreferenceCategory categoryTheme;
    private PreferenceCategory categoryAppearance;
    private PreferenceCategory categoryFontDisplay;
    private PreferenceCategory categoryNotifications;
    private PreferenceCategory categoryGeneral;
    private PreferenceCategory categoryAbout;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        
        initPreferences();
        setupPreferenceListeners();
        observeViewModel();
    }

    /**
     * ★ مزامنة قيمة اللغة مع ما هو مطبق فعلياً في النظام ★
     *
     * يُحلّ مشكلتين:
     *
     * المشكلة الأولى: عند تغيير لغة التطبيق من إعدادات النظام والعودة
     * للتطبيق وهو لا يزال في الـ RAM، كان الـ Dropdown يعرض القيمة القديمة.
     *
     * المشكلة الثانية: SettingsActivity تعترض حدث locale في configChanges
     * وتتعامل معه بنفسها عبر onConfigurationChanged، مما يجعل نظام Android
     * يعتبر أن التطبيق قد تعامل مع التغيير بالكامل، فلا يُعيد بناء MainActivity
     * تلقائياً. عند العودة من SettingsActivity إلى MainActivity، تبقى
     * MainActivity على اللغة القديمة.
     *
     * الحل: في كل مرة يُستأنَم الـ Fragment، نستعلم من LocaleManager
     * عن اللغة الفعلية المطبقة ونحدّث الـ Dropdown وDataStore معاً.
     * ثم نُجدول إعادة بناء الأنشطة الخلفية بعد تأخير يضمن اكتمال
     * onConfigurationChanged وتحديثاته في SettingsActivity.
     *
     * ★ لماذا لا تحدث مشكلة التكرار عند التغيير من القائمة المنسدلة؟ ★
     * لأن setLanguageMode() في ViewModel يُحدّث languagePreference.getValue()
     * فوراً، فعند وصول onResume لاحقاً لا يجد أي تعارض ولا يُرسل RECREATE.
     */
    @Override
    public void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && languagePreference != null) {
            int actualLang = SettingsHelper.getSystemAssignedLanguage(requireContext());
            String actualLangStr = String.valueOf(actualLang);

            // تحديث الـ Dropdown فقط إذا كانت القيمة المعروضة مختلفة عن الواقع
            if (!actualLangStr.equals(languagePreference.getValue())) {
                languagePreference.setValue(actualLangStr);

                // مزامنة DataStore مع قيمة النظام الحقيقية
                // نستخدم syncLanguageModeFromSystem لتجنب تشغيل LocaleManager مرة أخرى
                Integer currentDataStoreLang = viewModel.getLanguageMode().getValue();
                if (currentDataStoreLang == null || currentDataStoreLang != actualLang) {
                    viewModel.syncLanguageModeFromSystem(actualLang);
                }

                // ★ إعادة بناء الأنشطة الخلفية التي لم تُعَد بناؤها تلقائياً ★
                //
                // السبب: SettingsActivity تعترض حدث locale في configChanges وتمنع النظام
                // من تمرير إعادة البناء تلقائياً إلى MainActivity في خلفية مكدس التنقل.
                // عند العودة من SettingsActivity ستجد MainActivity باللغة القديمة.
                //
                // التأخير 300ms: يضمن اكتمال onConfigurationChanged في SettingsActivity
                // وكل تحديثاته على النصوص والاتجاه قبل أن تُعاد بناء الأنشطة الخلفية.
                // هذا يمنع أي تعارض بين تحديث onConfigurationChanged وعملية إعادة البناء.
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> {
                            if (getActivity() != null && !getActivity().isFinishing()) {
                                viewModel.requestBackgroundRecreation();
                            }
                        }, 300);
            }
        }
    }

    private void initPreferences() {
        // ── تهيئة مراجع الأقسام (لتحديث عناوينها في onConfigurationChanged) ──
        categoryTheme = findPreference("category_theme");
        categoryAppearance = findPreference("category_appearance");
        categoryFontDisplay = findPreference("category_font_display");
        categoryNotifications = findPreference("category_notifications");
        categoryGeneral = findPreference("category_general");
        categoryAbout = findPreference("category_about");

        // ── تهيئة عناصر الإعدادات الفردية ──
        languagePreference = findPreference("language_mode");
        if (languagePreference != null) {
            languagePreference.seslSetSummaryColor(getColoredSummaryColor(true));
            languagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int mode = Integer.parseInt((String) newValue);
                // ★ تأخير 250ms قبل تطبيق اللغة ★
                // يمنح DropDownPreference وقتاً كافياً لإغلاق نافذته بسلاسة
                // قبل أن يبدأ النظام في تطبيق تغيير اللغة وتحديث الواجهة.
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> viewModel.setLanguageMode(mode), 250);
                return true;
            });
        }
        
        themePreference = findPreference("theme_mode");
        if (themePreference != null) {
            themePreference.setDividerEnabled(false);
            themePreference.setTouchEffectEnabled(false);
        }
        
        themeAutoPreference = findPreference("theme_auto");

        // ★ تهيئة زر الثيم الشفاف ★
        themeTransparentPreference = findPreference("theme_transparent");
        if (themeTransparentPreference != null) {
            themeTransparentPreference.seslSetSummaryColor(getColoredSummaryColor(false));
            themeTransparentPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                // ★ تفعيل/إلغاء الثيم الشفاف لا يؤثر على خيار الثيم الفاتح/الداكن ★
                // ★ تأخير قبل تطبيق الثيم الشفاف ★
                // يمنح السويتش وقتاً كافياً لإكمال أنيميشنه قبل أن تبدأ
                // عملية إعادة تحميل الـ layouts المرتبطة بتغيير الثيم
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> viewModel.setThemeTransparent(enabled), 300);
                return true;
        });
        }
        
        fontPreference = findPreference("font_mode");
        if (fontPreference != null) {
            fontPreference.seslSetSummaryColor(getColoredSummaryColor(true));
            fontPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int mode = Integer.parseInt((String) newValue);
                viewModel.setFontMode(mode);
                return true;
            });
        }
        
        fontPreviewPreference = findPreference("font_preview_enabled");
        if (fontPreviewPreference != null) {
            fontPreviewPreference.seslSetSummaryColor(getColoredSummaryColor(false));
            fontPreviewPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                viewModel.setFontPreviewEnabled(enabled);
                return true;
            });
        }
        
        translationPreference = findPreference("enable_translation");
        if (translationPreference != null) {
            translationPreference.seslSetSummaryColor(getColoredSummaryColor(false));
            translationPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                viewModel.setTranslationEnabled(enabled);
                return true;
            });
        }
        
        notificationsPreference = findPreference("notifications_enabled");
        if (notificationsPreference != null) {
            notificationsPreference.seslSetSummaryColor(getColoredSummaryColor(false));
            notificationsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                viewModel.setNotificationsEnabled(enabled);
                return true;
            });
        }
        
        previewTextPreference = findPreference("preview_text");
        if (previewTextPreference != null) {
            previewTextPreference.seslSetSummaryColor(getColoredSummaryColor(true));
            previewTextPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String text = (String) newValue;
                viewModel.setPreviewText(text);
                return true;
            });
        }
        
        Preference aboutPreference = findPreference("about_app");
        if (aboutPreference != null) {
            aboutPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(mContext, AboutActivity.class);
                startActivity(intent);
                return true;
            });
        }
    }

    private void setupPreferenceListeners() {
        if (themePreference != null) {
            themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = (String) newValue;
                int mode = Integer.parseInt(value);
                
                // تطبيق الثيم مباشرة
                if (mode == SettingsHelper.THEME_DARK) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
                
                // حفظ القيمة في ViewModel (الذي بدوره يحفظها في DataStore)
                viewModel.setThemeMode(mode);
                
                return true;
            });
        }
        
        if (themeAutoPreference != null) {
            themeAutoPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                
                // تعطيل/تفعيل اختيار الثيم اليدوي فوراً
                if (themePreference != null) {
                    themePreference.setEnabled(!enabled);
                }
                
                // تطبيق الوضع التلقائي مباشرة
                if (enabled) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    // عند تعطيل الوضع التلقائي، تطبيق الثيم المحفوظ
                    int savedMode = SettingsHelper.getThemeMode(mContext);
                    if (savedMode == SettingsHelper.THEME_DARK) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    }
                }
                
                // تحديث الإعدادات في ViewModel
                viewModel.setThemeAuto(enabled);
                
                return true;
            });
        }
    }

    private void observeViewModel() {
        viewModel.getLanguageMode().observe(this, mode -> {
            if (languagePreference != null && mode != null) {
                languagePreference.setValue(String.valueOf(mode));
            }
        });

        viewModel.getThemeMode().observe(this, mode -> {
            if (themePreference != null && mode != null) {
                themePreference.setValue(String.valueOf(mode));
            }
        });

        viewModel.getThemeAuto().observe(this, enabled -> {
            if (themeAutoPreference != null && enabled != null) {
                themeAutoPreference.setChecked(enabled);
                
                if (themePreference != null) {
                    themePreference.setEnabled(!enabled);
                }
            }
        });

        // ★ مراقبة حالة الثيم الشفاف وتحديث الزر ★
        viewModel.getThemeTransparent().observe(this, enabled -> {
            if (themeTransparentPreference != null && enabled != null) {
                themeTransparentPreference.setChecked(enabled);
            }
        });

        viewModel.getFontMode().observe(this, mode -> {
            if (fontPreference != null && mode != null) {
                fontPreference.setValue(String.valueOf(mode));
            }
        });

        viewModel.getFontPreviewEnabled().observe(this, enabled -> {
            if (fontPreviewPreference != null && enabled != null) {
                fontPreviewPreference.setChecked(enabled);
            }
        });

        viewModel.getTranslationEnabled().observe(this, enabled -> {
            if (translationPreference != null && enabled != null) {
                translationPreference.setChecked(enabled);
            }
        });

        viewModel.getNotificationsEnabled().observe(this, enabled -> {
            if (notificationsPreference != null && enabled != null) {
                notificationsPreference.setChecked(enabled);
            }
        });

        viewModel.getPreviewText().observe(this, text -> {
            if (previewTextPreference != null && text != null) {
                previewTextPreference.setText(text);
            }
        });

        viewModel.getSettingsEvent().observe(this, event -> {
            if (event != null && event.getContentIfNotHandled()) {
                handleSettingsEvent(event);
            }
        });
    }

    private void handleSettingsEvent(SettingsViewModel.SettingsEvent event) {
        switch (event.getType()) {
            case SHOW_TOAST:
                if (event.getMessage() != null) {
                    Toast.makeText(mContext, event.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;

            case RECREATE_ACTIVITY:
                if (getActivity() != null) {
                    requireActivity().recreate();
                }
                break;

            case RECREATE_ALL_ACTIVITIES:
                MyApplication app = MyApplication.getInstance();
                if (app != null) {
                    app.recreateAllActivities();
                } else if (getActivity() != null) {
                    requireActivity().recreate();
                }
                break;

            case RECREATE_BACKGROUND_ACTIVITIES:
                // ★ إعادة بناء الأنشطة الخلفية باللغة الجديدة دون SettingsActivity ★
                //
                // السبب: SettingsActivity تتلقى تغيير اللغة عبر onConfigurationChanged
                // (لأن locale في configChanges)، وإعادة بناؤها ستكسر سلوك عدم الوميض.
                // أما الأنشطة الأخرى (كـ MainActivity) فتحتاج إعادة بناء صريحة
                // لأن النظام قد لا يُعيدها تلقائياً بعد تغيير الثيم + تغيير اللغة،
                // أو بعد تغيير اللغة من إعدادات النظام مع اعتراض locale في configChanges.
                //
                // إذا كان المستخدم قد خرج من SettingsActivity قبل انتهاء التأخير،
                // نُعيد بناء كل الأنشطة الحية لأن SettingsActivity ستكون isFinishing()
                // وستُستثنى تلقائياً من recreateAllActivities().
                MyApplication appBg = MyApplication.getInstance();
                Activity currentActivity = getActivity();
                if (appBg != null) {
                    if (currentActivity != null && !currentActivity.isFinishing()) {
                        // المستخدم لا يزال في SettingsActivity — نستثنيها صراحةً
                        appBg.recreateAllActivitiesExcept(currentActivity);
                    } else {
                        // المستخدم غادر SettingsActivity — تُستثنى تلقائياً عبر isFinishing()
                        appBg.recreateAllActivities();
                    }
                }
                break;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getView().setBackgroundColor(mContext.getColor(dev.oneuiproject.oneui.design.R.color.oui_background_color));
        getListView().seslSetLastRoundedCorner(false);
    }

    /**
     * ★ تحديث جميع النصوص في الإعدادات من سياق يحمل اللغة الجديدة بشكل مضمون ★
     *
     * @param freshContext سياق مُنشأ عبر LanguageHelper.createFreshContext(newConfig)
     *                     يحمل اللغة الجديدة بشكل مضمون، متجاوزاً السياق القديم
     *                     الذي أنشأته attachBaseContext() وقت بدء التطبيق.
     *
     * ★ مهم جداً: نحدّث النصوص والعناوين والقوائم فقط.
     *    لا نلمس الصور ولا الرسومات ولا الألوان ولا البطاقات،
     *    وهذا هو السبب الذي يمنع وميض الشاشة تماماً. ★
     */
    private void updatePreferencesText(Context freshContext) {
        Resources res = freshContext.getResources();

        // ── قسم الثيم ──
        // لا عنوان للقسم نفسه، نحدّث العناصر الداخلية فقط
        if (themePreference != null) {
            themePreference.setTitle(freshContext.getString(R.string.settings_theme));
            // ★ تحديث نصوص HorizontalRadioPreference عبر Reflection ★
            //
            // المشكلة: الحقل mEntries خاص (private) ولا توجد دالة setEntries() عامة
            // في مكتبة One UI. المكتبة تستخدم mEntries في onBindViewHolder لضبط
            // نص icon_title تحت كل صورة.
            //
            // الحل: نصل إلى mEntries بـ Reflection ونستبدل قيمته بالنصوص المترجمة
            // الجديدة المسحوبة من freshContext، ثم نستدعي notifyChanged() لإعادة
            // تشغيل onBindViewHolder الذي يعيد رسم النصوص تحت الصور.
            try {
                java.lang.reflect.Field entriesField =
                        HorizontalRadioPreference.class.getDeclaredField("mEntries");
                entriesField.setAccessible(true);
                entriesField.set(
                        themePreference,
                        freshContext.getResources().getTextArray(R.array.settings_theme_entries)
                );
                // ★ notifyChanged() محمية (protected) في Preference، نستدعيها بـ Reflection أيضاً ★
                // هي المسؤولة عن إعادة تشغيل onBindViewHolder لتطبيق النصوص الجديدة على الواجهة
                java.lang.reflect.Method notifyChangedMethod =
                        Preference.class.getDeclaredMethod("notifyChanged");
                notifyChangedMethod.setAccessible(true);
                notifyChangedMethod.invoke(themePreference);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Could not update HorizontalRadioPreference entries via reflection", e);
                // الفشل هنا غير مؤثر على وظيفة التطبيق،
                // الصور ستظل ظاهرة وفقط نصوص Light/Dark لن تُترجم فورياً.
            }
        }
        if (themeAutoPreference != null) {
            themeAutoPreference.setTitle(freshContext.getString(R.string.settings_theme_system));
        }
        if (themeTransparentPreference != null) {
            themeTransparentPreference.setTitle(freshContext.getString(R.string.settings_theme_transparent));
            themeTransparentPreference.setSummary(freshContext.getString(R.string.settings_theme_transparent_description));
        }

        // ── قسم المظهر ──
        if (categoryAppearance != null) {
            categoryAppearance.setTitle(freshContext.getString(R.string.settings_appearance));
        }
        if (languagePreference != null) {
            languagePreference.setTitle(freshContext.getString(R.string.settings_language));
            // ★ CharSequence[] يُجبر القائمة على عرض النصوص المترجمة الجديدة فوراً ★
            languagePreference.setEntries(res.getStringArray(R.array.settings_language_entries));
        }
        if (fontPreference != null) {
            fontPreference.setTitle(freshContext.getString(R.string.settings_font));
            // ★ CharSequence[] يُجبر القائمة على عرض النصوص المترجمة الجديدة فوراً ★
            fontPreference.setEntries(res.getStringArray(R.array.settings_font_entries));
        }

        // ── قسم عرض الخطوط ──
        if (categoryFontDisplay != null) {
            categoryFontDisplay.setTitle(freshContext.getString(R.string.settings_font_display_section));
        }
        if (fontPreviewPreference != null) {
            fontPreviewPreference.setTitle(freshContext.getString(R.string.settings_font_preview));
            fontPreviewPreference.setSummary(freshContext.getString(R.string.settings_font_preview_description));
        }
        if (translationPreference != null) {
            translationPreference.setTitle(freshContext.getString(R.string.settings_translation));
            translationPreference.setSummary(freshContext.getString(R.string.settings_translation_description));
        }

        // ── قسم الإشعارات ──
        if (categoryNotifications != null) {
            categoryNotifications.setTitle(freshContext.getString(R.string.settings_notifications_section));
        }
        if (notificationsPreference != null) {
            notificationsPreference.setTitle(freshContext.getString(R.string.settings_notifications));
            notificationsPreference.setSummary(freshContext.getString(R.string.settings_notifications_description));
        }

        // ── القسم العام ──
        if (categoryGeneral != null) {
            categoryGeneral.setTitle(freshContext.getString(R.string.settings_general));
        }
        if (previewTextPreference != null) {
            previewTextPreference.setTitle(freshContext.getString(R.string.settings_preview_text));
            // ★ لا نستدعي setSummary() هنا عمداً ★
            // هذا الـ Preference يملك useSimpleSummaryProvider="true" في XML،
            // مما يُنشئ SummaryProvider تلقائياً يعرض القيمة الحالية.
            // استدعاء setSummary() معه يرمي IllegalStateException.
            previewTextPreference.setDialogTitle(freshContext.getString(R.string.settings_preview_text_dialog_title));
            previewTextPreference.setDialogMessage(freshContext.getString(R.string.settings_preview_text_dialog_message));
        }

        // ── قسم حول التطبيق ──
        Preference aboutPreference = findPreference("about_app");
        if (aboutPreference != null) {
            aboutPreference.setTitle(freshContext.getString(R.string.about_title));
        }
    }

    /**
     * ★ تحديث الاتجاه والنصوص فوراً عند تغيير اللغة — بدون تدمير الشاشة ★
     *
     * يُستدعى لأن locale|layoutDirection في configChanges يمنع تدمير الـ Activity.
     *
     * الترتيب مهم:
     * 1. إنشاء freshContext عبر LanguageHelper (مصدر النصوص الجديدة المضمون)
     * 2. تحديث mContext للعمليات اللاحقة
     * 3. تحديث الاتجاه عبر LanguageHelper (يطبّق فوراً على كل العناصر الداخلية)
     * 4. تحديث النصوص من freshContext
     * 5. إخبار القائمة بإعادة الرسم
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // ★ الخطوة الأولى: إنشاء سياق جديد يحمل اللغة المحدّثة بشكل مضمون ★
        // createConfigurationContext(newConfig) مستقل تماماً عن السياق القديم
        // الذي أنشأته attachBaseContext() عند بدء التطبيق.
        Context freshContext = LanguageHelper.createFreshContext(requireContext(), newConfig);

        // ★ الخطوة الثانية: تحديث mContext ليكون جاهزاً للعمليات اللاحقة ★
        mContext = freshContext;

        // ★ الخطوة الثالثة: تفويض تحديث الاتجاه إلى LanguageHelper ★
        // يحلّ مشكلة System Default والتأخر في تغيير الاتجاه بين أي لغتين
        LanguageHelper.forceUpdateLayoutDirectionForView(getView(), newConfig);

        // ★ الخطوة الرابعة: تحديث جميع النصوص من freshContext ★
        updatePreferencesText(freshContext);

        // ★ الخطوة الخامسة: إخبار القائمة بإعادة رسم عناصرها ★
        if (getListView() != null && getListView().getAdapter() != null) {
            getListView().getAdapter().notifyDataSetChanged();
        }
    }

    private ColorStateList getColoredSummaryColor(boolean enabled) {
        if (enabled) {
            TypedValue colorPrimaryDark = new TypedValue();
            mContext.getTheme().resolveAttribute(
                    dev.oneuiproject.oneui.design.R.attr.colorPrimaryDark, colorPrimaryDark, true);

            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_enabled},
                    new int[] {-android.R.attr.state_enabled}
            };
            int[] colors = new int[] {
                    Color.argb(0xff,
                            Color.red(colorPrimaryDark.data),
                            Color.green(colorPrimaryDark.data),
                            Color.blue(colorPrimaryDark.data)),
                    Color.argb(0x4d,
                            Color.red(colorPrimaryDark.data),
                            Color.green(colorPrimaryDark.data),
                            Color.blue(colorPrimaryDark.data))
            };
            return new ColorStateList(states, colors);
        } else {
            TypedValue outValue = new TypedValue();
            mContext.getTheme().resolveAttribute(
                    dev.oneuiproject.oneui.design.R.attr.isLightTheme, outValue, true);
            return mContext.getColorStateList(outValue.data != 0
                    ? dev.oneuiproject.oneui.design.R.color.sesl_secondary_text_light
                    : dev.oneuiproject.oneui.design.R.color.sesl_secondary_text_dark);
        }
    }
    }
