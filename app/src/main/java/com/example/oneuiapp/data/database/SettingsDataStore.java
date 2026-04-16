package com.example.oneuiapp.data.datastore;

import android.content.Context;

import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * SettingsDataStore - Complete DataStore implementation
 * All SharedPreferences removed and replaced with DataStore
 */
public class SettingsDataStore {
    
    private static final String DATASTORE_NAME = "settings";
    private static volatile SettingsDataStore INSTANCE;
    
    private final RxDataStore<Preferences> dataStore;
    
    // Core keys
    private final Preferences.Key<Integer> KEY_LANGUAGE_MODE;
    private final Preferences.Key<Integer> KEY_THEME_MODE;
    private final Preferences.Key<Boolean> KEY_THEME_AUTO;
    private final Preferences.Key<Integer> KEY_FONT_MODE;
    private final Preferences.Key<Boolean> KEY_FONT_PREVIEW_ENABLED;
    private final Preferences.Key<Boolean> KEY_TRANSLATION_ENABLED;
    private final Preferences.Key<Boolean> KEY_NOTIFICATIONS_ENABLED;
    private final Preferences.Key<String> KEY_PREVIEW_TEXT;
    private final Preferences.Key<String> KEY_LAST_OPENED_FONT_PATH;
    private final Preferences.Key<String> KEY_FOLDER_PATH;

    // ★ مفتاح الثيم الشفاف (البطاقات المتباعدة ذات الزوايا الدائرية الكاملة) ★
    private final Preferences.Key<Boolean> KEY_THEME_TRANSPARENT;
    
    // FontList keys (Local Folder)
    private final Preferences.Key<String> KEY_SORT_TYPE;
    private final Preferences.Key<Boolean> KEY_SORT_ASCENDING;
    private final Preferences.Key<String> KEY_LAST_OPENED_SYSTEM_FONT_PATH;

    // ★ System Font sort keys — منفصلة تماماً عن مفاتيح المجلد المحلي ★
    private final Preferences.Key<String> KEY_SYSTEM_SORT_TYPE;
    private final Preferences.Key<Boolean> KEY_SYSTEM_SORT_ASCENDING;
    
    // Font Viewer keys
    private final Preferences.Key<String> KEY_VIEWER_FONT_PATH;
    private final Preferences.Key<String> KEY_VIEWER_FILE_NAME;
    private final Preferences.Key<String> KEY_VIEWER_REAL_NAME;
    private final Preferences.Key<Float> KEY_VIEWER_FONT_SIZE;
    private final Preferences.Key<Float> KEY_VIEWER_FONT_WEIGHT;
    
    // Default values
    public static final int DEFAULT_LANGUAGE_MODE = 0;
    public static final int DEFAULT_THEME_MODE = 0;
    public static final boolean DEFAULT_THEME_AUTO = true;
    public static final int DEFAULT_FONT_MODE = 0;
    public static final boolean DEFAULT_FONT_PREVIEW_ENABLED = true;
    public static final boolean DEFAULT_TRANSLATION_ENABLED = false;
    public static final boolean DEFAULT_NOTIFICATIONS_ENABLED = true;
    public static final String DEFAULT_PREVIEW_TEXT = 
            "The quick brown fox jumps over the lazy dog.\n\n" +
            "0123456789\n\n" +
            "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z\n\n" +
            "a b c d e f g h i j k l m n o p q r s t u v w x y z\n\n" +
            ". , ; : ! ? ' \" ( ) - [ ] { } < > / \\\n\n" +
            "$ € £ ¥ % @ © ® ™ + - × ÷ = *\n\n" +
            "# & ^ _ | ~ `";
    public static final float DEFAULT_VIEWER_FONT_SIZE = 18f;
    public static final float DEFAULT_VIEWER_FONT_WEIGHT = 400f;
    // ★ القيمة الافتراضية للثيم الشفاف: معطّل ★
    public static final boolean DEFAULT_THEME_TRANSPARENT = false;
    
    private SettingsDataStore(Context context) {
        dataStore = new RxPreferenceDataStoreBuilder(
                context.getApplicationContext(),
                DATASTORE_NAME
        ).build();
        
        // Initialize core keys
        KEY_LANGUAGE_MODE = PreferencesKeys.intKey("language_mode");
        KEY_THEME_MODE = PreferencesKeys.intKey("theme_mode");
        KEY_THEME_AUTO = PreferencesKeys.booleanKey("theme_auto");
        KEY_FONT_MODE = PreferencesKeys.intKey("font_mode");
        KEY_FONT_PREVIEW_ENABLED = PreferencesKeys.booleanKey("font_preview_enabled");
        KEY_TRANSLATION_ENABLED = PreferencesKeys.booleanKey("translation_enabled");
        KEY_NOTIFICATIONS_ENABLED = PreferencesKeys.booleanKey("notifications_enabled");
        KEY_PREVIEW_TEXT = PreferencesKeys.stringKey("preview_text");
        KEY_LAST_OPENED_FONT_PATH = PreferencesKeys.stringKey("last_opened_font_path");
        KEY_FOLDER_PATH = PreferencesKeys.stringKey("folder_path");

        // ★ تهيئة مفتاح الثيم الشفاف ★
        KEY_THEME_TRANSPARENT = PreferencesKeys.booleanKey("theme_transparent");
        
        // Initialize FontList (Local) keys
        KEY_SORT_TYPE = PreferencesKeys.stringKey("sort_type");
        KEY_SORT_ASCENDING = PreferencesKeys.booleanKey("sort_ascending");
        KEY_LAST_OPENED_SYSTEM_FONT_PATH = PreferencesKeys.stringKey("last_opened_system_font_path");

        // ★ Initialize System Font sort keys ★
        KEY_SYSTEM_SORT_TYPE = PreferencesKeys.stringKey("system_sort_type");
        KEY_SYSTEM_SORT_ASCENDING = PreferencesKeys.booleanKey("system_sort_ascending");
        
        // Initialize Font Viewer keys
        KEY_VIEWER_FONT_PATH = PreferencesKeys.stringKey("viewer_font_path");
        KEY_VIEWER_FILE_NAME = PreferencesKeys.stringKey("viewer_file_name");
        KEY_VIEWER_REAL_NAME = PreferencesKeys.stringKey("viewer_real_name");
        KEY_VIEWER_FONT_SIZE = PreferencesKeys.floatKey("viewer_font_size");
        KEY_VIEWER_FONT_WEIGHT = PreferencesKeys.floatKey("viewer_font_weight");
    }
    
    public static SettingsDataStore getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SettingsDataStore.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SettingsDataStore(context);
                }
            }
        }
        return INSTANCE;
    }
    
    // ════════════════════════════════════════════════════════════
    // Language Mode
    // ════════════════════════════════════════════════════════════
    
    public Flowable<Integer> getLanguageMode() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_LANGUAGE_MODE) != null ? 
            prefs.get(KEY_LANGUAGE_MODE) : DEFAULT_LANGUAGE_MODE
        );
    }
    
    public Single<Preferences> setLanguageMode(int mode) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_LANGUAGE_MODE, mode);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Theme Mode
    // ════════════════════════════════════════════════════════════
    
    public Flowable<Integer> getThemeMode() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_THEME_MODE) != null ? 
            prefs.get(KEY_THEME_MODE) : DEFAULT_THEME_MODE
        );
    }
    
    public Single<Preferences> setThemeMode(int mode) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_THEME_MODE, mode);
            return Single.just(mutablePrefs);
        });
    }
    
    public Flowable<Boolean> getThemeAuto() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_THEME_AUTO) != null ? 
            prefs.get(KEY_THEME_AUTO) : DEFAULT_THEME_AUTO
        );
    }
    
    public Single<Preferences> setThemeAuto(boolean auto) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_THEME_AUTO, auto);
            return Single.just(mutablePrefs);
        });
    }

    // ════════════════════════════════════════════════════════════
    // ★ Transparent Theme — الثيم الشفاف ذو البطاقات المتباعدة ★
    // منفصل تماماً عن الثيم الفاتح/الداكن ولا يؤثر عليه
    // ════════════════════════════════════════════════════════════

    public Flowable<Boolean> getThemeTransparent() {
        return dataStore.data().map(prefs ->
            prefs.get(KEY_THEME_TRANSPARENT) != null ?
            prefs.get(KEY_THEME_TRANSPARENT) : DEFAULT_THEME_TRANSPARENT
        );
    }

    public Single<Preferences> setThemeTransparent(boolean enabled) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_THEME_TRANSPARENT, enabled);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Font Mode
    // ════════════════════════════════════════════════════════════
    
    public Flowable<Integer> getFontMode() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_FONT_MODE) != null ? 
            prefs.get(KEY_FONT_MODE) : DEFAULT_FONT_MODE
        );
    }
    
    public Single<Preferences> setFontMode(int mode) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_FONT_MODE, mode);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Font Preview
    // ════════════════════════════════════════════════════════════
    
    public Flowable<Boolean> getFontPreviewEnabled() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_FONT_PREVIEW_ENABLED) != null ? 
            prefs.get(KEY_FONT_PREVIEW_ENABLED) : DEFAULT_FONT_PREVIEW_ENABLED
        );
    }
    
    public Single<Preferences> setFontPreviewEnabled(boolean enabled) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_FONT_PREVIEW_ENABLED, enabled);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Translation
    // ════════════════════════════════════════════════════════════
    
    public Flowable<Boolean> getTranslationEnabled() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_TRANSLATION_ENABLED) != null ? 
            prefs.get(KEY_TRANSLATION_ENABLED) : DEFAULT_TRANSLATION_ENABLED
        );
    }
    
    public Single<Preferences> setTranslationEnabled(boolean enabled) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_TRANSLATION_ENABLED, enabled);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Notifications
    // ════════════════════════════════════════════════════════════
    
    public Flowable<Boolean> getNotificationsEnabled() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_NOTIFICATIONS_ENABLED) != null ? 
            prefs.get(KEY_NOTIFICATIONS_ENABLED) : DEFAULT_NOTIFICATIONS_ENABLED
        );
    }
    
    public Single<Preferences> setNotificationsEnabled(boolean enabled) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_NOTIFICATIONS_ENABLED, enabled);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Preview Text
    // ════════════════════════════════════════════════════════════
    
    public Flowable<String> getPreviewText() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_PREVIEW_TEXT) != null ? 
            prefs.get(KEY_PREVIEW_TEXT) : DEFAULT_PREVIEW_TEXT
        );
    }
    
    public Single<Preferences> setPreviewText(String text) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_PREVIEW_TEXT, text);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Last Opened Font (Local)
    // ════════════════════════════════════════════════════════════
    
    public Flowable<String> getLastOpenedFontPath() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_LAST_OPENED_FONT_PATH)
        );
    }
    
    public Single<Preferences> setLastOpenedFontPath(String path) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            if (path != null) {
                mutablePrefs.set(KEY_LAST_OPENED_FONT_PATH, path);
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_FONT_PATH);
            }
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Folder Path
    // ════════════════════════════════════════════════════════════
    
    public Flowable<String> getFolderPath() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_FOLDER_PATH)
        );
    }
    
    public Single<Preferences> setFolderPath(String path) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            if (path != null) {
                mutablePrefs.set(KEY_FOLDER_PATH, path);
            } else {
                mutablePrefs.remove(KEY_FOLDER_PATH);
            }
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Sort Options (Local Folder Fonts)
    // ════════════════════════════════════════════════════════════
    
    public Flowable<String> getSortType() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_SORT_TYPE) != null ? 
            prefs.get(KEY_SORT_TYPE) : "NAME"
        );
    }
    
    public Single<Preferences> setSortType(String type) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_SORT_TYPE, type);
            return Single.just(mutablePrefs);
        });
    }
    
    public Flowable<Boolean> getSortAscending() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_SORT_ASCENDING) != null ? 
            prefs.get(KEY_SORT_ASCENDING) : true
        );
    }
    
    public Single<Preferences> setSortAscending(boolean ascending) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_SORT_ASCENDING, ascending);
            return Single.just(mutablePrefs);
        });
    }

    // ════════════════════════════════════════════════════════════
    // Sort Options (System Fonts) ★ منفصلة تماماً لتجنب التجمد ★
    // ════════════════════════════════════════════════════════════

    public Flowable<String> getSystemSortType() {
        return dataStore.data().map(prefs ->
            prefs.get(KEY_SYSTEM_SORT_TYPE) != null ? prefs.get(KEY_SYSTEM_SORT_TYPE) : "NAME"
        );
    }

    public Single<Preferences> setSystemSortType(String type) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_SYSTEM_SORT_TYPE, type);
            return Single.just(mutablePrefs);
        });
    }

    public Flowable<Boolean> getSystemSortAscending() {
        return dataStore.data().map(prefs ->
            prefs.get(KEY_SYSTEM_SORT_ASCENDING) != null ? prefs.get(KEY_SYSTEM_SORT_ASCENDING) : true
        );
    }

    public Single<Preferences> setSystemSortAscending(boolean ascending) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_SYSTEM_SORT_ASCENDING, ascending);
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Last Opened System Font
    // ════════════════════════════════════════════════════════════
    
    public Flowable<String> getLastOpenedSystemFontPath() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_LAST_OPENED_SYSTEM_FONT_PATH)
        );
    }
    
    public Single<Preferences> setLastOpenedSystemFontPath(String path) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            if (path != null) {
                mutablePrefs.set(KEY_LAST_OPENED_SYSTEM_FONT_PATH, path);
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_SYSTEM_FONT_PATH);
            }
            return Single.just(mutablePrefs);
        });
    }
    
    // ════════════════════════════════════════════════════════════
    // Font Viewer - Last Viewed Font
    // ════════════════════════════════════════════════════════════
    
    public Single<Preferences> setLastViewedFont(String path, String fileName, String realName) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            if (path != null) mutablePrefs.set(KEY_VIEWER_FONT_PATH, path);
            if (fileName != null) mutablePrefs.set(KEY_VIEWER_FILE_NAME, fileName);
            if (realName != null) mutablePrefs.set(KEY_VIEWER_REAL_NAME, realName);
            return Single.just(mutablePrefs);
        });
    }
    
    public Single<Preferences> clearLastViewedFont() {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.remove(KEY_VIEWER_FONT_PATH);
            mutablePrefs.remove(KEY_VIEWER_FILE_NAME);
            mutablePrefs.remove(KEY_VIEWER_REAL_NAME);
            return Single.just(mutablePrefs);
        });
    }
    
    public Flowable<String> getViewerFontPath() {
        return dataStore.data().map(prefs -> prefs.get(KEY_VIEWER_FONT_PATH));
    }
    
    public Flowable<String> getViewerFileName() {
        return dataStore.data().map(prefs -> prefs.get(KEY_VIEWER_FILE_NAME));
    }
    
    public Flowable<String> getViewerRealName() {
        return dataStore.data().map(prefs -> prefs.get(KEY_VIEWER_REAL_NAME));
    }
    
    // ════════════════════════════════════════════════════════════
    // Font Viewer - Font Size
    // ════════════════════════════════════════════════════════════
    
    public Single<Preferences> setViewerFontSize(float size) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_VIEWER_FONT_SIZE, size);
            return Single.just(mutablePrefs);
        });
    }
    
    public Flowable<Float> getViewerFontSize() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_VIEWER_FONT_SIZE) != null ? 
            prefs.get(KEY_VIEWER_FONT_SIZE) : DEFAULT_VIEWER_FONT_SIZE
        );
    }
    
    // ════════════════════════════════════════════════════════════
    // Font Viewer - Font Weight
    // ════════════════════════════════════════════════════════════
    
    public Single<Preferences> setViewerFontWeight(float weight) {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(KEY_VIEWER_FONT_WEIGHT, weight);
            return Single.just(mutablePrefs);
        });
    }
    
    public Flowable<Float> getViewerFontWeight() {
        return dataStore.data().map(prefs -> 
            prefs.get(KEY_VIEWER_FONT_WEIGHT) != null ? 
            prefs.get(KEY_VIEWER_FONT_WEIGHT) : DEFAULT_VIEWER_FONT_WEIGHT
        );
    }
    
    // ════════════════════════════════════════════════════════════
    // Clear All
    // ════════════════════════════════════════════════════════════
    
    public Single<Preferences> clearAll() {
        return dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.clear();
            return Single.just(mutablePrefs);
        });
    }
                }
