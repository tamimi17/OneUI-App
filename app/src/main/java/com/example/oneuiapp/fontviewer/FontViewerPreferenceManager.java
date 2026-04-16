package com.example.oneuiapp.fontviewer;

import android.content.Context;
import android.util.Log;

import com.example.oneuiapp.data.datastore.SettingsDataStore;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * FontViewerPreferenceManager - DataStore version
 * Completely removed SharedPreferences, now uses SettingsDataStore
 */
public class FontViewerPreferenceManager {
    
    private static final String TAG = "FontViewerPrefManager";
    private final SettingsDataStore dataStore;
    
    public FontViewerPreferenceManager(Context context) {
        this.dataStore = SettingsDataStore.getInstance(context);
    }
    
    /**
     * Save last viewed font information
     * 
     * @param fontPath Local path to font file
     * @param fileName File name
     * @param realName Real font name from metadata
     */
    public void saveLastViewedFont(String fontPath, String fileName, String realName) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path");
            return;
        }
        
        dataStore.setLastViewedFont(fontPath, fileName, realName)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved last viewed font: " + fontPath),
                    error -> Log.e(TAG, "Error saving font: " + error.getMessage())
                );
    }
    
    /**
     * Get last viewed font path
     * 
     * @return Font path or null if not saved
     */
    public String getLastViewedFontPath() {
        try {
            return dataStore.getViewerFontPath().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error getting font path: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get last viewed font file name
     * 
     * @return File name or null if not saved
     */
    public String getLastViewedFontFileName() {
        try {
            return dataStore.getViewerFileName().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get last viewed font real name
     * 
     * @return Real name or null if not saved
     */
    public String getLastViewedFontRealName() {
        try {
            return dataStore.getViewerRealName().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error getting real name: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if there is a saved font
     * 
     * @return true if font is saved
     */
    public boolean hasLastViewedFont() {
        return getLastViewedFontPath() != null;
    }
    
    /**
     * Clear last viewed font information
     */
    public void clearLastViewedFont() {
        dataStore.clearLastViewedFont()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Cleared last viewed font"),
                    error -> Log.e(TAG, "Error clearing font: " + error.getMessage())
                );
    }
    
    /**
     * Save current font size
     * 
     * @param fontSize Font size in SP
     */
    public void saveFontSize(float fontSize) {
        dataStore.setViewerFontSize(fontSize)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font size: " + fontSize),
                    error -> Log.e(TAG, "Error saving font size: " + error.getMessage())
                );
    }
    
    /**
     * Get saved font size
     * 
     * @return Font size or default value
     */
    public float getFontSize() {
        return getFontSize(SettingsDataStore.DEFAULT_VIEWER_FONT_SIZE);
    }
    
    /**
     * Get saved font size with custom default
     * 
     * @param defaultSize Default value to return if not saved
     * @return Saved font size or default value
     */
    public float getFontSize(float defaultSize) {
        try {
            Float value = dataStore.getViewerFontSize().blockingFirst();
            return value != null ? value : defaultSize;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font size: " + e.getMessage());
            return defaultSize;
        }
    }
    
    /**
     * Save current font weight (for variable fonts)
     * 
     * @param fontWeight Font weight (e.g., 400 for normal, 700 for bold)
     */
    public void saveFontWeight(float fontWeight) {
        dataStore.setViewerFontWeight(fontWeight)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    prefs -> Log.d(TAG, "Saved font weight: " + fontWeight),
                    error -> Log.e(TAG, "Error saving font weight: " + error.getMessage())
                );
    }
    
    /**
     * Get saved font weight
     * 
     * @return Font weight or default (400)
     */
    public float getFontWeight() {
        return getFontWeight(SettingsDataStore.DEFAULT_VIEWER_FONT_WEIGHT);
    }
    
    /**
     * Get saved font weight with custom default
     * 
     * @param defaultWeight Default value to return if not saved
     * @return Saved font weight or default value
     */
    public float getFontWeight(float defaultWeight) {
        try {
            Float value = dataStore.getViewerFontWeight().blockingFirst();
            return value != null ? value : defaultWeight;
        } catch (Exception e) {
            Log.e(TAG, "Error getting font weight: " + e.getMessage());
            return defaultWeight;
        }
    }
    
    /**
     * Reset font size to default
     */
    public void resetFontSize() {
        saveFontSize(SettingsDataStore.DEFAULT_VIEWER_FONT_SIZE);
    }
    
    /**
     * Reset font weight to default
     */
    public void resetFontWeight() {
        saveFontWeight(SettingsDataStore.DEFAULT_VIEWER_FONT_WEIGHT);
    }
}
