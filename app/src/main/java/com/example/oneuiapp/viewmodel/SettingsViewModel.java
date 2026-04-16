package com.example.oneuiapp.viewmodel;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.oneuiapp.data.datastore.SettingsDataStore;
import com.example.oneuiapp.utils.LanguageHelper;
import com.example.oneuiapp.utils.SettingsHelper;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SettingsViewModel - نسخة محدّثة تعتمد بالكامل على DataStore
 * تم إزالة كل استخدام لـ SharedPreferences والاستماع المباشر لـ PreferenceManager
 *
 * آلية تغيير اللغة:
 * - Android 13 فما فوق: يُفوّض إلى LanguageHelper.applyLanguage() الذي يستخدم
 *   LocaleManager من الـ Framework مباشرةً. بما أن locale|layoutDirection مُضاف
 *   إلى configChanges في SettingsActivity فقط، يقوم النظام تلقائياً بإعادة بناء
 *   MainActivity و HomeActivity في الخلفية، بينما يتلقى SettingsActivity الحدث
 *   عبر onConfigurationChanged فقط دون تدمير. لا نرسل RECREATE هنا.
 * - ما دون Android 13: يُعيد إنشاء جميع الأنشطة ليعمل ContextWrapper في BaseActivity.
 *
 * ★ دالة syncLanguageModeFromSystem مُضافة: ★
 * تُستخدم حصرياً لمزامنة DataStore مع قيمة LocaleManager دون تشغيل
 * أي تأثيرات جانبية (لا LocaleManager ولا RECREATE).
 *
 * ★ دالة requestBackgroundRecreation مُضافة: ★
 * تُستخدم من SettingsFragment.onResume() عند اكتشاف أن اللغة تغيّرت
 * من إعدادات النظام بينما كان التطبيق في الخلفية، وذلك لإعادة بناء
 * الأنشطة الخلفية التي لم يُعدها النظام تلقائياً بسبب اعتراض
 * SettingsActivity لحدث locale في configChanges.
 */
public class SettingsViewModel extends AndroidViewModel {

    private static final String TAG = "SettingsViewModel";
    
    private final SettingsDataStore dataStore;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // LiveData للإعدادات الأساسية
    private final MutableLiveData<Integer> languageMode = new MutableLiveData<>();
    private final MutableLiveData<Integer> themeMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> themeAuto = new MutableLiveData<>();
    private final MutableLiveData<Integer> fontMode = new MutableLiveData<>();
    
    // LiveData للإعدادات الثنائية (Boolean)
    private final MutableLiveData<Boolean> fontPreviewEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> translationEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> notificationsEnabled = new MutableLiveData<>();

    // ★ LiveData للثيم الشفاف — منفصل عن الثيم الفاتح/الداكن ★
    private final MutableLiveData<Boolean> themeTransparent = new MutableLiveData<>();
    
    // LiveData للنصوص
    private final MutableLiveData<String> previewText = new MutableLiveData<>();
    
    // LiveData لحالات خاصة
    private final MutableLiveData<SettingsEvent> settingsEvent = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        
        dataStore = SettingsDataStore.getInstance(application);
        
        // مراقبة DataStore مباشرة بدلاً من SharedPreferences Listener
        observeDataStore();
    }

    /**
     * مراقبة جميع القيم في DataStore وتحديث LiveData تلقائياً
     */
    private void observeDataStore() {
        // Language Mode
        disposables.add(
            dataStore.getLanguageMode()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    languageMode::setValue,
                    error -> Log.e(TAG, "Error observing language mode", error)
                )
        );

        // Theme Mode
        disposables.add(
            dataStore.getThemeMode()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    themeMode::setValue,
                    error -> Log.e(TAG, "Error observing theme mode", error)
                )
        );

        // Theme Auto
        disposables.add(
            dataStore.getThemeAuto()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    themeAuto::setValue,
                    error -> Log.e(TAG, "Error observing theme auto", error)
                )
        );

        // ★ Transparent Theme — مراقبة الثيم الشفاف ★
        disposables.add(
            dataStore.getThemeTransparent()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    themeTransparent::setValue,
                    error -> Log.e(TAG, "Error observing transparent theme", error)
                )
        );

        // Font Mode
        disposables.add(
            dataStore.getFontMode()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    fontMode::setValue,
                    error -> Log.e(TAG, "Error observing font mode", error)
                )
        );

        // Font Preview
        disposables.add(
            dataStore.getFontPreviewEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    fontPreviewEnabled::setValue,
                    error -> Log.e(TAG, "Error observing font preview", error)
                )
        );

        // Translation
        disposables.add(
            dataStore.getTranslationEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    translationEnabled::setValue,
                    error -> Log.e(TAG, "Error observing translation", error)
                )
        );

        // Notifications
        disposables.add(
            dataStore.getNotificationsEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    notificationsEnabled::setValue,
                    error -> Log.e(TAG, "Error observing notifications", error)
                )
        );

        // Preview Text
        disposables.add(
            dataStore.getPreviewText()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    previewText::setValue,
                    error -> Log.e(TAG, "Error observing preview text", error)
                )
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Getters للـ LiveData (للمراقبة من Fragment)
    // ═══════════════════════════════════════════════════════════════

    public LiveData<Integer> getLanguageMode() {
        return languageMode;
    }

    public LiveData<Integer> getThemeMode() {
        return themeMode;
    }

    public LiveData<Boolean> getThemeAuto() {
        return themeAuto;
    }

    public LiveData<Integer> getFontMode() {
        return fontMode;
    }

    public LiveData<Boolean> getFontPreviewEnabled() {
        return fontPreviewEnabled;
    }

    public LiveData<Boolean> getTranslationEnabled() {
        return translationEnabled;
    }

    public LiveData<Boolean> getNotificationsEnabled() {
        return notificationsEnabled;
    }

    // ★ Getter للثيم الشفاف ★
    public LiveData<Boolean> getThemeTransparent() {
        return themeTransparent;
    }

    public LiveData<String> getPreviewText() {
        return previewText;
    }

    public LiveData<SettingsEvent> getSettingsEvent() {
        return settingsEvent;
    }

    // ═══════════════════════════════════════════════════════════════
    // Methods لتحديث الإعدادات (الكتابة إلى DataStore)
    // ═══════════════════════════════════════════════════════════════

    /**
     * تغيير وضع اللغة
     *
     * آلية العمل حسب إصدار النظام:
     * - Android 13+: يُفوّض إلى LanguageHelper.applyLanguage() الذي يكتب في LocaleManager.
     *   بما أن locale|layoutDirection مُضاف إلى configChanges لـ SettingsActivity فقط:
     *   - SettingsActivity تتلقى onConfigurationChanged وتحدّث نصوصها بدون تدمير.
     *   - MainActivity و HomeActivity يُعاد بناؤهما تلقائياً بالخلفية من قِبَل النظام.
     *   لا نرسل RECREATE_ALL_ACTIVITIES هنا عمداً لأن النظام يتكفل بذلك.
     * - ما دون Android 13: يُعيد إنشاء جميع الأنشطة ليعمل ContextWrapper في BaseActivity.
     */
    public void setLanguageMode(int mode) {
        if (languageMode.getValue() != null && languageMode.getValue() == mode) {
            return;
        }
        
        disposables.add(
            dataStore.setLanguageMode(mode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Language mode saved to DataStore: " + mode);

                        // ★ تفويض تطبيق اللغة إلى LanguageHelper ★
                        // يتكفل LanguageHelper باختيار الآلية الصحيحة
                        // بناءً على إصدار Android تلقائياً
                        LanguageHelper.applyLanguage(getApplication(), mode);

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            // ما دون Android 13: إعادة إنشاء الأنشطة ليعمل ContextWrapper
                            Log.d(TAG, "Sending RECREATE for pre-Android 13");
                            settingsEvent.setValue(new SettingsEvent(
                                    SettingsEventType.RECREATE_ALL_ACTIVITIES));
                        } else {
                            // ★ Android 13+: إعادة بناء الأنشطة الخلفية بعد تأخير ★
                            //
                            // المشكلة: عند تغيير الثيم قبل تغيير اللغة، تُعاد بناء الأنشطة
                            // الخلفية (كـ MainActivity) باللغة القديمة بواسطة AppCompat.
                            // بعد تغيير اللغة، يُبلَّغ SettingsActivity عبر onConfigurationChanged
                            // فقط، لكن النظام قد لا يُعيد بناء الأنشطة الخلفية تلقائياً مرة ثانية.
                            //
                            // الحل: بعد تأخير يضمن اكتمال onConfigurationChanged في SettingsActivity،
                            // نُرسل حدث RECREATE_BACKGROUND_ACTIVITIES الذي يُعيد بناء
                            // الأنشطة الخلفية فقط دون SettingsActivity.
                            //
                            // التأخير 400ms: يضمن أن onConfigurationChanged والتحديثات
                            // المرتبطة به قد اكتملت قبل إعادة البناء.
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(() -> settingsEvent.setValue(new SettingsEvent(
                                            SettingsEventType.RECREATE_BACKGROUND_ACTIVITIES)),
                                            400);
                            Log.d(TAG, "Language delegated to LocaleManager (Android 13+), background recreation scheduled");
                        }
                    },
                    error -> Log.e(TAG, "Error setting language mode", error)
                )
        );
    }

    /**
     * ★ مزامنة وضع اللغة من النظام إلى DataStore فقط ★
     *
     * تُستدعى من SettingsFragment.onResume() عند اكتشاف اختلاف بين
     * قيمة LocaleManager (مصدر الحقيقة) وقيمة DataStore.
     *
     * الفرق عن setLanguageMode():
     * - لا تُشغّل LanguageHelper (اللغة مُطبَّقة بالفعل من النظام)
     * - لا ترسل RECREATE (الواجهة محدّثة بالفعل)
     * - لا تتحقق من تطابق القيمة الحالية (المزامنة مطلوبة دائماً)
     * - تكتب فقط إلى DataStore للحفاظ على التزامن الداخلي
     */
    public void syncLanguageModeFromSystem(int mode) {
        disposables.add(
            dataStore.setLanguageMode(mode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> Log.d(TAG, "Language mode synced from system to DataStore: " + mode),
                    error -> Log.e(TAG, "Error syncing language mode from system", error)
                )
        );
    }

    /**
     * ★ طلب إعادة بناء الأنشطة الخلفية عند تغيير اللغة من إعدادات النظام ★
     *
     * تُستدعى من SettingsFragment.onResume() بعد اكتشاف أن اللغة تغيّرت
     * من إعدادات النظام بينما كان التطبيق في الخلفية.
     *
     * جوهر المشكلة التي تحلّها:
     * SettingsActivity تعترض حدث locale في configChanges وتتعامل معه عبر
     * onConfigurationChanged، مما يجعل نظام Android يعتبر أن التطبيق قد
     * تعامل مع التغيير بالكامل، فلا يُعيد بناء MainActivity تلقائياً.
     * هذه الدالة تعوّض هذا النقص بإرسال حدث صريح لإعادة بناء الأنشطة الخلفية.
     *
     * الفرق عن setLanguageMode():
     * - لا تُشغّل LanguageHelper (اللغة مُطبَّقة بالفعل من النظام)
     * - لا تكتب إلى DataStore (syncLanguageModeFromSystem تتكفل بذلك مسبقاً)
     * - تُرسل RECREATE_BACKGROUND_ACTIVITIES مباشرةً دون تأخير
     *   (التأخير موجود في استدعائها من onResume)
     */
    public void requestBackgroundRecreation() {
        Log.d(TAG, "Requesting background activities recreation due to system locale change");
        settingsEvent.setValue(new SettingsEvent(SettingsEventType.RECREATE_BACKGROUND_ACTIVITIES));
    }

    /**
     * تغيير وضع الثيم
     * يطبق الثيم فوراً ويعطل الوضع التلقائي
     */
    public void setThemeMode(int mode) {
        if (themeMode.getValue() != null && themeMode.getValue() == mode) {
            return;
        }
        
        Log.d(TAG, "Setting theme mode to: " + mode);
        
        // تعطيل الوضع التلقائي أولاً عند اختيار ثيم يدوي
        disposables.add(
            dataStore.setThemeAuto(false)
                .flatMap(prefs -> dataStore.setThemeMode(mode))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Theme mode updated and auto disabled");
                        SettingsHelper.applyTheme(getApplication());
                    },
                    error -> Log.e(TAG, "Error setting theme mode", error)
                )
        );
    }

    /**
     * تفعيل/تعطيل الوضع التلقائي للثيم
     */
    public void setThemeAuto(boolean enabled) {
        if (themeAuto.getValue() != null && themeAuto.getValue() == enabled) {
            return;
        }
        
        Log.d(TAG, "Setting theme auto to: " + enabled);
        
        disposables.add(
            dataStore.setThemeAuto(enabled)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Theme auto updated");
                        SettingsHelper.applyTheme(getApplication());
                    },
                    error -> Log.e(TAG, "Error setting theme auto", error)
                )
        );
    }

    /**
     * ★ تفعيل/تعطيل الثيم الشفاف ★
     * هذا الثيم مستقل تماماً عن خيار الثيم الفاتح/الداكن:
     * - لا يغير الثيم المحدد
     * - يُعيد فقط بناء الأنشطة لتطبيق الـ Layouts الشفافة الجديدة
     */
    public void setThemeTransparent(boolean enabled) {
        if (themeTransparent.getValue() != null && themeTransparent.getValue() == enabled) {
            return;
        }

        Log.d(TAG, "Setting transparent theme to: " + enabled);

        disposables.add(
            dataStore.setThemeTransparent(enabled)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Transparent theme updated to: " + enabled);
                        // ★ إعادة بناء جميع الأنشطة لاختيار الـ Layouts الصحيحة ★
                        settingsEvent.setValue(new SettingsEvent(
                            SettingsEventType.RECREATE_ALL_ACTIVITIES));
                    },
                    error -> Log.e(TAG, "Error setting transparent theme", error)
                )
        );
    }

    /**
     * تغيير وضع الخط
     * يتطلب إعادة إنشاء جميع الأنشطة
     */
    public void setFontMode(int mode) {
        if (fontMode.getValue() != null && fontMode.getValue() == mode) {
            return;
        }
        
        disposables.add(
            dataStore.setFontMode(mode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Font mode updated to: " + mode);
                        settingsEvent.setValue(new SettingsEvent(
                            SettingsEventType.RECREATE_ALL_ACTIVITIES));
                    },
                    error -> Log.e(TAG, "Error setting font mode", error)
                )
        );
    }

    /**
     * تفعيل/تعطيل معاينة الخطوط
     */
    public void setFontPreviewEnabled(boolean enabled) {
        if (fontPreviewEnabled.getValue() != null && fontPreviewEnabled.getValue() == enabled) {
            return;
        }
        
        disposables.add(
            dataStore.setFontPreviewEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    preferences -> Log.d(TAG, "Font preview updated to: " + enabled),
                    error -> Log.e(TAG, "Error setting font preview", error)
                )
        );
    }

    /**
     * تفعيل/تعطيل الترجمة
     */
    public void setTranslationEnabled(boolean enabled) {
        if (translationEnabled.getValue() != null && translationEnabled.getValue() == enabled) {
            return;
        }
        
        disposables.add(
            dataStore.setTranslationEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    preferences -> Log.d(TAG, "Translation updated to: " + enabled),
                    error -> Log.e(TAG, "Error setting translation", error)
                )
        );
    }

    /**
     * تفعيل/تعطيل الإشعارات
     */
    public void setNotificationsEnabled(boolean enabled) {
        if (notificationsEnabled.getValue() != null && notificationsEnabled.getValue() == enabled) {
            return;
        }
        
        disposables.add(
            dataStore.setNotificationsEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    preferences -> Log.d(TAG, "Notifications updated to: " + enabled),
                    error -> Log.e(TAG, "Error setting notifications", error)
                )
        );
    }

    /**
     * تحديث نص المعاينة
     */
    public void setPreviewText(String text) {
        if (previewText.getValue() != null && previewText.getValue().equals(text)) {
            return;
        }
        
        disposables.add(
            dataStore.setPreviewText(text)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Preview text updated");
                        settingsEvent.setValue(new SettingsEvent(
                            SettingsEventType.SHOW_TOAST,
                            "Preview text updated"
                        ));
                    },
                    error -> Log.e(TAG, "Error setting preview text", error)
                )
        );
    }

    /**
     * إعادة تعيين جميع الإعدادات إلى القيم الافتراضية
     */
    public void resetAllSettings() {
        disposables.add(
            dataStore.clearAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "All settings reset to defaults");
                        SettingsHelper.applyTheme(getApplication());
                        settingsEvent.setValue(new SettingsEvent(
                            SettingsEventType.RECREATE_ALL_ACTIVITIES));
                    },
                    error -> Log.e(TAG, "Error resetting settings", error)
                )
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
        Log.d(TAG, "ViewModel cleared");
    }

    // ═══════════════════════════════════════════════════════════════
    // فئات داخلية للأحداث
    // ═══════════════════════════════════════════════════════════════

    public static class SettingsEvent {
        private final SettingsEventType type;
        private final String message;
        private boolean handled = false;

        public SettingsEvent(SettingsEventType type) {
            this(type, null);
        }

        public SettingsEvent(SettingsEventType type, String message) {
            this.type = type;
            this.message = message;
        }

        public SettingsEventType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public boolean getContentIfNotHandled() {
            if (handled) {
                return false;
            }
            handled = true;
            return true;
        }
    }

    public enum SettingsEventType {
        SHOW_TOAST,
        RECREATE_ACTIVITY,
        RECREATE_ALL_ACTIVITIES,
        // ★ نوع حدث جديد: إعادة بناء الأنشطة الخلفية فقط دون SettingsActivity ★
        // يُستخدم بعد تغيير اللغة على Android 13+ لضمان أن الأنشطة الخلفية
        // التي أُعيد بناؤها بسبب تغيير الثيم تحمل اللغة الجديدة الصحيحة.
        RECREATE_BACKGROUND_ACTIVITIES
    }
                           }
