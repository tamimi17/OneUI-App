package com.example.oneuiapp.metadata;

import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * FontMetadataExtractor - Enhanced version
 * ★ تحسينات: منطق أفضل لاستخراج الأسماء الحقيقية ★
 */
public class FontMetadataExtractor {
    
    private static final String TAG = "FontMetadataExtractor";
    
    /**
     * استخراج الاسم الحقيقي من الخط
     */
    public static String extractFontName(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist");
            return "Unknown Font";
        }
        
        try {
            String fontName = FontMetaDataFallback.extractFontName(fontFile, ttcIndex);
            
            if (fontName != null && !fontName.isEmpty() && !fontName.equals("Unknown Font")) {
                Log.d(TAG, "Successfully extracted font name: " + fontName);
                return fontName;
            }
        } catch (Exception e) {
            Log.w(TAG, "FontMetaDataFallback.extractFontName failed: " + e.getMessage());
        }
        
        Log.d(TAG, "Could not extract real name, returning Unknown Font");
        return "Unknown Font";
    }
    
    /**
     * استخراج اسم الخط بدون TTC Index (للتوافق)
     */
    public static String extractFontName(File fontFile) {
        return extractFontName(fontFile, 0);
    }
    
    /**
     * استخراج البيانات الوصفية مع دعم TTC Index
     */
    public static Map<String, String> extractMetadataWithTtcIndex(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist");
            return new HashMap<>();
        }
        
        try {
            Map<String, String> metadata = FontMetaDataFallback.extractMetaDataWithTtcIndex(fontFile, ttcIndex);
            
            if (metadata != null && !metadata.isEmpty() && 
                (metadata.containsKey("FullName") || metadata.containsKey("Family"))) {
                Log.d(TAG, "Successfully extracted metadata with TTC index: " + ttcIndex);
                return metadata;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract metadata with TTC index: " + e.getMessage());
        }
        
        // Fallback: محاولة بدون TTC Index
        try {
            Map<String, String> metadata = FontMetaDataFallback.extractMetaData(fontFile);
            
            if (metadata != null && !metadata.isEmpty() && 
                (metadata.containsKey("FullName") || metadata.containsKey("Family"))) {
                Log.d(TAG, "Successfully extracted metadata using fallback method");
                return metadata;
            }
        } catch (Exception e) {
            Log.w(TAG, "FontMetaDataFallback.extractMetaData failed: " + e.getMessage());
        }
        
        Log.w(TAG, "Failed to extract metadata, returning empty map");
        return new HashMap<>();
    }
    
    /**
     * استخراج البيانات الوصفية بدون TTC Index (للتوافق)
     */
    public static Map<String, String> extractMetadata(File fontFile) {
        return extractMetadataWithTtcIndex(fontFile, 0);
    }
    
    /**
     * فحص توفر البيانات الوصفية
     */
    public static boolean isMetadataAvailable(File fontFile) {
        if (fontFile == null || !fontFile.exists()) {
            return false;
        }
        
        try {
            String fontName = FontMetaDataFallback.extractFontName(fontFile);
            return fontName != null && !fontName.isEmpty() && !fontName.equals("Unknown Font");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * استخراج حقل معين من البيانات الوصفية
     */
    public static String extractSpecificMetadata(File fontFile, String key) {
        if (fontFile == null || !fontFile.exists() || key == null) {
            return null;
        }
        
        Map<String, String> metadata = extractMetadata(fontFile);
        return metadata.get(key);
    }
    
    /**
     * فحص وجود حقل معين
     */
    public static boolean hasMetadata(File fontFile, String key) {
        String value = extractSpecificMetadata(fontFile, key);
        return value != null && !value.isEmpty();
    }
    
    /**
     * استخراج المعلومات الأساسية فقط
     */
    public static Map<String, String> extractBasicInfo(File fontFile) {
        Map<String, String> basicInfo = new HashMap<>();
        
        if (fontFile == null || !fontFile.exists()) {
            return basicInfo;
        }
        
        Map<String, String> fullMetadata = extractMetadata(fontFile);
        
        if (fullMetadata.containsKey("FullName")) {
            basicInfo.put("FullName", fullMetadata.get("FullName"));
        }
        
        if (fullMetadata.containsKey("Family")) {
            basicInfo.put("Family", fullMetadata.get("Family"));
        }
        
        if (fullMetadata.containsKey("Version")) {
            basicInfo.put("Version", fullMetadata.get("Version"));
        }
        
        if (fullMetadata.containsKey("FontType")) {
            basicInfo.put("FontType", fullMetadata.get("FontType"));
        }
        
        return basicInfo;
    }
    }
