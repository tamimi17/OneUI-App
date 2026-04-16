package com.example.oneuiapp.data.datastore;

import android.content.Context;
import android.util.Log;

import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * TranslationDataStore - Separate DataStore for translation cache
 * Stores translated text to avoid repeated API calls
 */
public class TranslationDataStore {
    
    private static final String TAG = "TranslationDataStore";
    private static final String DATASTORE_NAME = "translation_cache";
    private static final int MAX_CACHE_SIZE = 500;
    
    private static volatile TranslationDataStore INSTANCE;
    private final RxDataStore<Preferences> dataStore;
    private final Context context;
    
    private TranslationDataStore(Context context) {
        this.context = context.getApplicationContext();
        this.dataStore = new RxPreferenceDataStoreBuilder(
                this.context,
                DATASTORE_NAME
        ).build();
    }
    
    public static TranslationDataStore getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TranslationDataStore.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TranslationDataStore(context);
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Get cached translation
     * 
     * @param key Cache key (generated from text + language)
     * @return Single with translation text or empty string if not found
     */
    public Single<String> getTranslation(String key) {
        if (key == null || key.isEmpty()) {
            return Single.just("");
        }
        
        Preferences.Key<String> prefKey = PreferencesKeys.stringKey(key);
        return dataStore.data()
                .firstOrError()
                .map(prefs -> {
                    String value = prefs.get(prefKey);
                    return value != null ? value : "";
                })
                .onErrorReturnItem("");
    }
    
    /**
     * Save translation to cache
     * 
     * @param key Cache key
     * @param value Translated text
     */
    public void saveTranslation(String key, String value) {
        if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
            return;
        }
        
        Preferences.Key<String> prefKey = PreferencesKeys.stringKey(key);
        dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.set(prefKey, value);
            return Single.just(mutablePrefs);
        })
        .subscribeOn(Schedulers.io())
        .subscribe(
            prefs -> {
                Log.d(TAG, "Translation cached successfully");
                checkAndCleanCache();
            },
            error -> Log.e(TAG, "Error saving translation: " + error.getMessage())
        );
    }
    
    /**
     * Check cache size and clear if too large
     */
    private void checkAndCleanCache() {
        dataStore.data()
                .firstOrError()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> {
                        int size = prefs.asMap().size();
                        if (size > MAX_CACHE_SIZE) {
                            Log.i(TAG, "Cache size exceeded " + MAX_CACHE_SIZE + ", clearing cache");
                            clearCache();
                        }
                    },
                    error -> Log.e(TAG, "Error checking cache size: " + error.getMessage())
                );
    }
    
    /**
     * Clear all cached translations
     */
    public void clearCache() {
        dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutablePrefs = prefs.toMutablePreferences();
            mutablePrefs.clear();
            return Single.just(mutablePrefs);
        })
        .subscribeOn(Schedulers.io())
        .subscribe(
            prefs -> Log.i(TAG, "Translation cache cleared"),
            error -> Log.e(TAG, "Error clearing cache: " + error.getMessage())
        );
    }
    
    /**
     * Get current cache size
     * 
     * @return Single with number of cached items
     */
    public Single<Integer> getCacheSize() {
        return dataStore.data()
                .firstOrError()
                .map(prefs -> prefs.asMap().size())
                .onErrorReturnItem(0);
    }
}
