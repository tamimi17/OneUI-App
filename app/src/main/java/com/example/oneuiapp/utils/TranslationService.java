package com.example.oneuiapp.utils;

import android.content.Context;
import android.util.Log;

import com.example.oneuiapp.data.datastore.SettingsDataStore;
import com.example.oneuiapp.data.datastore.TranslationDataStore;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * TranslationService - DataStore version
 * Completely removed SharedPreferences, now uses TranslationDataStore for caching
 */
public class TranslationService {
    
    private static final String TAG = "TranslationService";
    private final Context context;
    private final TranslationDataStore translationCache;
    private final SettingsDataStore settingsDataStore;
    
    public interface TranslationCallback {
        void onTranslationComplete(Map<String, String> translatedData);
        void onTranslationFailed(String error);
    }
    
    public TranslationService(Context context) {
        this.context = context;
        this.translationCache = TranslationDataStore.getInstance(context);
        this.settingsDataStore = SettingsDataStore.getInstance(context);
    }
    
    /**
     * Translate metadata fields based on user language preference
     */
    public void translateMetadata(Map<String, String> metadata, TranslationCallback callback) {
        // Check if translation is enabled
        if (!isTranslationEnabled()) {
            callback.onTranslationComplete(metadata);
            return;
        }
        
        String targetLanguage = getCurrentLanguage();
        
        if (targetLanguage.equals("en")) {
            callback.onTranslationComplete(metadata);
            return;
        }
        
        new Thread(() -> {
            try {
                Map<String, String> translatedData = new HashMap<>(metadata);
                
                String[] fieldsToTranslate = {
                    "Copyright", 
                    "Trademark", 
                    "Description", 
                    "LicenseDescription", 
                    "SupportedScripts"
                };
                
                for (String field : fieldsToTranslate) {
                    if (metadata.containsKey(field)) {
                        String originalText = metadata.get(field);
                        
                        if (originalText != null && !originalText.isEmpty() && originalText.length() < 5000) {
                            String cacheKey = generateCacheKey(originalText, targetLanguage);
                            
                            if (cacheKey == null) {
                                continue;
                            }
                            
                            // Try to get from cache (blocking is acceptable here as we're in background thread)
                            String cachedTranslation = "";
                            try {
                                cachedTranslation = translationCache.getTranslation(cacheKey).blockingGet();
                            } catch (Exception e) {
                                Log.e(TAG, "Error reading from cache: " + e.getMessage());
                            }
                            
                            if (cachedTranslation != null && !cachedTranslation.isEmpty()) {
                                // Use cached translation
                                translatedData.put(field, cachedTranslation);
                                Log.d(TAG, "Using cached translation for field: " + field);
                            } else {
                                // Translate and cache
                                String translatedText = translateText(originalText, "en", targetLanguage);
                                
                                if (translatedText != null && !translatedText.isEmpty()) {
                                    translationCache.saveTranslation(cacheKey, translatedText);
                                    translatedData.put(field, translatedText);
                                    Log.d(TAG, "Translated and cached field: " + field);
                                }
                                
                                // Small delay to avoid rate limiting
                                Thread.sleep(100);
                            }
                        }
                    }
                }
                
                callback.onTranslationComplete(translatedData);
                
            } catch (Exception e) {
                Log.e(TAG, "Translation failed: " + e.getMessage(), e);
                callback.onTranslationFailed(e.getMessage());
            }
        }).start();
    }
    
    /**
     * Translate text using Google Translate API
     */
    private String translateText(String text, String sourceLang, String targetLang) {
        HttpURLConnection connection = null;
        try {
            String encodedText = URLEncoder.encode(text, "UTF-8");
            String urlString = String.format(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s",
                sourceLang, targetLang, encodedText
            );
            
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                
                JSONArray jsonArray = new JSONArray(response.toString());
                JSONArray translationsArray = jsonArray.getJSONArray(0);
                
                StringBuilder translatedText = new StringBuilder();
                for (int i = 0; i < translationsArray.length(); i++) {
                    JSONArray translation = translationsArray.getJSONArray(i);
                    translatedText.append(translation.getString(0));
                }
                
                return translatedText.toString();
            } else {
                Log.e(TAG, "Translation API returned error code: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to translate text: " + e.getMessage(), e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Get current language from settings
     */
    private String getCurrentLanguage() {
        try {
            Locale currentLocale = SettingsHelper.getLocale(context);
            String language = currentLocale.getLanguage();
            
            if (language.equals("ar")) {
                return "ar";
            } else {
                return "en";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting current language: " + e.getMessage());
            return "en";
        }
    }
    
    /**
     * Generate cache key from text and target language
     */
    private String generateCacheKey(String text, String targetLang) {
        try {
            String combined = text.substring(0, Math.min(text.length(), 100)) + "_" + targetLang;
            return String.valueOf(combined.hashCode());
        } catch (Exception e) {
            Log.e(TAG, "Error generating cache key: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Clear translation cache
     */
    public void clearCache() {
        translationCache.clearCache();
        Log.i(TAG, "Translation cache cleared by user request");
    }
    
    /**
     * Check if translation is enabled in settings
     */
    public boolean isTranslationEnabled() {
        try {
            return settingsDataStore.getTranslationEnabled().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error checking translation enabled: " + e.getMessage());
            return false;
        }
    }
}
