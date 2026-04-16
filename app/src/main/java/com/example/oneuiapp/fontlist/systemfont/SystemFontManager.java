package com.example.oneuiapp.fontlist.systemfont;

import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SystemFontManager - مدير خطوط النظام
 * يتعامل مع SystemFonts API للحصول على قائمة خطوط النظام المتاحة
 * يتطلب Android API 29 أو أعلى
 */
public class SystemFontManager {
    
    private static final String TAG = "SystemFontManager";
    private static SystemFontManager instance;
    
    private SystemFontManager() {
    }
    
    public static synchronized SystemFontManager getInstance() {
        if (instance == null) {
            instance = new SystemFontManager();
        }
        return instance;
    }
    
    /**
     * التحقق من توفر واجهة SystemFonts
     */
    public boolean isSystemFontsAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
    
    /**
     * الحصول على قائمة جميع خطوط النظام المتاحة
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public List<SystemFontInfo> getSystemFonts() {
        if (!isSystemFontsAvailable()) {
            Log.w(TAG, "SystemFonts API not available on this Android version");
            return new ArrayList<>();
        }
        
        List<SystemFontInfo> fontInfoList = new ArrayList<>();
        Set<String> processedPaths = new HashSet<>();
        
        try {
            Set<Font> availableFonts = SystemFonts.getAvailableFonts();
            
            if (availableFonts == null || availableFonts.isEmpty()) {
                Log.w(TAG, "No system fonts available");
                return fontInfoList;
            }
            
            Log.d(TAG, "Found " + availableFonts.size() + " system fonts");
            
            for (Font font : availableFonts) {
                try {
                    File fontFile = font.getFile();
                    
                    if (fontFile == null || !fontFile.exists()) {
                        continue;
                    }
                    
                    String fontPath = fontFile.getAbsolutePath();
                    
                    if (processedPaths.contains(fontPath)) {
                        continue;
                    }
                    
                    processedPaths.add(fontPath);
                    
                    String fontName = fontFile.getName();
                    long fileSize = fontFile.length();
                    long lastModified = fontFile.lastModified();
                    
                    int weight = 400;
                    int slant = 0;
                    int ttcIndex = 0;
                    String axes = null;
                    
                    try {
                        weight = font.getStyle().getWeight();
                        slant = font.getStyle().getSlant();
                        ttcIndex = font.getTtcIndex();
                        axes = extractAxesInfo(font);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not extract font style info for: " + fontName);
                    }
                    
                    SystemFontInfo fontInfo = new SystemFontInfo(
                        fontName,
                        fontPath,
                        fileSize,
                        lastModified,
                        weight,
                        slant,
                        ttcIndex,
                        axes
                    );
                    
                    fontInfoList.add(fontInfo);
                    
                } catch (Exception e) {
                    Log.w(TAG, "Error processing font: " + e.getMessage());
                }
            }
            
            Collections.sort(fontInfoList, (f1, f2) -> 
                f1.getName().compareToIgnoreCase(f2.getName()));
            
            Log.d(TAG, "Successfully processed " + fontInfoList.size() + " unique system fonts");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting system fonts", e);
        }
        
        return fontInfoList;
    }
    
    /**
     * استخراج معلومات المحاور للخطوط المتغيرة
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private String extractAxesInfo(Font font) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.graphics.fonts.FontVariationAxis[] axes = font.getAxes();
                if (axes != null && axes.length > 0) {
                    StringBuilder axesInfo = new StringBuilder();
                    for (int i = 0; i < axes.length; i++) {
                        if (i > 0) axesInfo.append(", ");
                        axesInfo.append(axes[i].getTag());
                    }
                    return axesInfo.toString();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting axes info: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * البحث عن خط معين بالاسم
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public SystemFontInfo findFontByName(String fontName) {
        if (fontName == null || fontName.isEmpty()) {
            return null;
        }
        
        List<SystemFontInfo> allFonts = getSystemFonts();
        
        for (SystemFontInfo fontInfo : allFonts) {
            if (fontInfo.getName().equalsIgnoreCase(fontName)) {
                return fontInfo;
            }
        }
        
        return null;
    }
    
    /**
     * البحث عن خط معين بالمسار
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public SystemFontInfo findFontByPath(String fontPath) {
        if (fontPath == null || fontPath.isEmpty()) {
            return null;
        }
        
        List<SystemFontInfo> allFonts = getSystemFonts();
        
        for (SystemFontInfo fontInfo : allFonts) {
            if (fontInfo.getPath().equals(fontPath)) {
                return fontInfo;
            }
        }
        
        return null;
    }
    
    /**
     * عد الخطوط المتغيرة في النظام
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public int countVariableFonts() {
        List<SystemFontInfo> allFonts = getSystemFonts();
        int count = 0;
        
        for (SystemFontInfo fontInfo : allFonts) {
            if (fontInfo.isVariableFont()) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * الحصول على قائمة الخطوط المتغيرة فقط
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public List<SystemFontInfo> getVariableFonts() {
        List<SystemFontInfo> allFonts = getSystemFonts();
        List<SystemFontInfo> variableFonts = new ArrayList<>();
        
        for (SystemFontInfo fontInfo : allFonts) {
            if (fontInfo.isVariableFont()) {
                variableFonts.add(fontInfo);
            }
        }
        
        return variableFonts;
    }
    
    /**
     * الحصول على معلومات إحصائية عن خطوط النظام
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public SystemFontStatistics getStatistics() {
        List<SystemFontInfo> allFonts = getSystemFonts();
        
        int totalFonts = allFonts.size();
        int variableFonts = 0;
        long totalSize = 0;
        
        Set<Integer> uniqueWeights = new HashSet<>();
        
        for (SystemFontInfo fontInfo : allFonts) {
            if (fontInfo.isVariableFont()) {
                variableFonts++;
            }
            totalSize += fontInfo.getSize();
            uniqueWeights.add(fontInfo.getWeight());
        }
        
        return new SystemFontStatistics(
            totalFonts,
            variableFonts,
            totalSize,
            uniqueWeights.size()
        );
    }
    
    /**
     * فئة لتخزين الإحصائيات
     */
    public static class SystemFontStatistics {
        public final int totalFonts;
        public final int variableFonts;
        public final long totalSize;
        public final int uniqueWeights;
        
        public SystemFontStatistics(int totalFonts, int variableFonts, long totalSize, int uniqueWeights) {
            this.totalFonts = totalFonts;
            this.variableFonts = variableFonts;
            this.totalSize = totalSize;
            this.uniqueWeights = uniqueWeights;
        }
        
        public String getFormattedTotalSize() {
            if (totalSize < 1024) {
                return totalSize + " B";
            } else if (totalSize < 1024 * 1024) {
                return String.format("%.2f KB", totalSize / 1024.0);
            } else {
                return String.format("%.2f MB", totalSize / (1024.0 * 1024.0));
            }
        }
    }
    }
