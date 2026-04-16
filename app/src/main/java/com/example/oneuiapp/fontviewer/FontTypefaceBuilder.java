package com.example.oneuiapp.fontviewer;

import android.graphics.Typeface;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.oneuiapp.utils.VariableFontHelper;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * FontTypefaceBuilder - مدير متخصص في إنشاء كائنات Typeface من ملفات الخطوط
 * محدّث لدعم ملفات TTC والخطوط المتغيرة بشكل كامل
 */
public class FontTypefaceBuilder {
    
    private static final String TAG = "FontTypefaceBuilder";
    
    /**
     * فحص إذا كان الملف هو TTC
     */
    private static boolean isTTCFile(File fontFile) {
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            return "ttcf".equals(tag);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * إنشاء Typeface عادي من ملف خط دون تخصيص الوزن
     */
    public static Typeface createTypeface(File fontFile) {
        return createTypeface(fontFile, 0);
    }

    /**
     * إنشاء Typeface عادي من ملف خط مع دعم TTC
     */
    public static Typeface createTypeface(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist");
            return null;
        }
        
        if (!fontFile.canRead()) {
            Log.w(TAG, "Font file cannot be read: " + fontFile.getAbsolutePath());
            return null;
        }
        
        Typeface typeface = null;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            typeface = createTypefaceUsingBuilder(fontFile, 0, ttcIndex);
            if (typeface != null) {
                Log.d(TAG, "Successfully created Typeface using Font.Builder with TTC index: " + ttcIndex);
                return typeface;
            }
        }
        
        try {
            typeface = Typeface.createFromFile(fontFile);
            if (typeface != null) {
                Log.d(TAG, "Successfully created Typeface using createFromFile");
                return typeface;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating Typeface from file", e);
        }
        
        Log.e(TAG, "Failed to create Typeface from file: " + fontFile.getAbsolutePath());
        return null;
    }
    
    /**
     * إنشاء Typeface مع وزن محدد (للخطوط المتغيرة) - النسخة القديمة للتوافق
     */
    public static Typeface createTypefaceWithWeight(File fontFile, float weight) {
        return createTypefaceWithWeight(fontFile, weight, 0);
    }

    /**
     * إنشاء Typeface مع وزن محدد ودعم TTC - النسخة الجديدة المحسّنة
     */
    public static Typeface createTypefaceWithWeight(File fontFile, float weight, int ttcIndex) {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist");
            return null;
        }
        
        if (!fontFile.canRead()) {
            Log.w(TAG, "Font file cannot be read: " + fontFile.getAbsolutePath());
            return null;
        }
        
        boolean isVariable = VariableFontHelper.isVariableFont(fontFile);
        
        if (!isVariable) {
            Log.d(TAG, "Font is not variable, creating standard Typeface with TTC index: " + ttcIndex);
            return createTypeface(fontFile, ttcIndex);
        }
        
        Log.d(TAG, "Font is variable, creating Typeface with weight: " + weight + ", TTC index: " + ttcIndex);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Typeface typeface = createTypefaceUsingBuilder(fontFile, weight, ttcIndex);
            if (typeface != null) {
                Log.d(TAG, "Successfully created variable Typeface with weight using Font.Builder");
                return typeface;
            }
        }
        
        Typeface typeface = VariableFontHelper.createTypefaceWithWeight(fontFile, weight);
        
        if (typeface != null) {
            Log.d(TAG, "Successfully created variable Typeface with weight using VariableFontHelper");
            return typeface;
        }
        
        Log.w(TAG, "Failed to create variable Typeface, falling back to standard Typeface");
        return createTypeface(fontFile, ttcIndex);
    }
    
    /**
     * إنشاء Typeface باستخدام Font.Builder (Android O وما بعده)
     * مع دعم كامل لملفات TTC والخطوط المتغيرة
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    private static Typeface createTypefaceUsingBuilder(File fontFile, float weight, int ttcIndex) {
        try {
            android.graphics.fonts.Font.Builder fontBuilder = 
                new android.graphics.fonts.Font.Builder(fontFile);
            
            // دعم TTC: تعيين رقم الخط داخل ملف TTC
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                Log.d(TAG, "Set TTC index: " + ttcIndex);
            }
            
            // دعم الخطوط المتغيرة: تعيين الوزن
            if (weight > 0 && weight != 400) {
                fontBuilder.setFontVariationSettings("'wght' " + weight);
                Log.d(TAG, "Set font variation settings: wght=" + weight);
            }
            
            android.graphics.fonts.Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            Typeface result = fallbackBuilder.build();
            Log.d(TAG, "Font.Builder succeeded for: " + fontFile.getName() + 
                       " (TTC: " + ttcIndex + ", Weight: " + weight + ")");
            return result;
            
        } catch (Exception e) {
            Log.w(TAG, "Font.Builder failed for: " + fontFile.getAbsolutePath() + 
                       " (TTC: " + ttcIndex + ", Weight: " + weight + "), error: " + e.getMessage());
        }
        
        // Fallback: محاولة استخدام ParcelFileDescriptor
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                fontFile, 
                ParcelFileDescriptor.MODE_READ_ONLY
            );
            
            if (pfd != null) {
                Typeface.Builder builder = new Typeface.Builder(pfd.getFileDescriptor());
                
                if (ttcIndex > 0) {
                    builder.setTtcIndex(ttcIndex);
                }
                
                if (weight > 0 && weight != 400) {
                    builder.setFontVariationSettings("'wght' " + weight);
                }
                
                Typeface typeface = builder.build();
                pfd.close();
                Log.d(TAG, "ParcelFileDescriptor method succeeded");
                return typeface;
            }
        } catch (Exception e) {
            Log.w(TAG, "ParcelFileDescriptor method failed: " + e.getMessage());
        }
        
        return null;
    }
    
    public static boolean supportsWeight(File fontFile, float weight) {
        if (fontFile == null || !fontFile.exists()) {
            return false;
        }
        
        if (!VariableFontHelper.isVariableFont(fontFile)) {
            return false;
        }
        
        java.util.List<VariableFontHelper.VariableInstance> instances = 
            VariableFontHelper.extractVariableInstances(fontFile);
        
        if (instances == null || instances.isEmpty()) {
            return false;
        }
        
        for (VariableFontHelper.VariableInstance instance : instances) {
            if (Math.abs(instance.value - weight) < 0.1f) {
                return true;
            }
        }
        
        return false;
    }
    
    public static java.util.List<VariableFontHelper.VariableInstance> getAvailableWeights(File fontFile) {
        if (fontFile == null || !fontFile.exists()) {
            return new java.util.ArrayList<>();
        }
        
        if (!VariableFontHelper.isVariableFont(fontFile)) {
            return new java.util.ArrayList<>();
        }
        
        java.util.List<VariableFontHelper.VariableInstance> instances = 
            VariableFontHelper.extractVariableInstances(fontFile);
        
        return instances != null ? instances : new java.util.ArrayList<>();
    }
    
    public static boolean isValidFontFile(File fontFile) {
        if (fontFile == null) {
            return false;
        }
        
        if (!fontFile.exists()) {
            Log.d(TAG, "Font file does not exist");
            return false;
        }
        
        if (!fontFile.isFile()) {
            Log.d(TAG, "Path is not a file");
            return false;
        }
        
        if (!fontFile.canRead()) {
            Log.d(TAG, "Font file cannot be read");
            return false;
        }
        
        String fileName = fontFile.getName().toLowerCase();
        if (!fileName.endsWith(".ttf") && !fileName.endsWith(".otf") && !fileName.endsWith(".ttc")) {
            Log.d(TAG, "File does not have a valid font extension");
            return false;
        }
        
        return true;
    }
    
    public static Typeface createTypefaceWithFallback(File fontFile) {
        return createTypefaceWithFallback(fontFile, 0);
    }

    public static Typeface createTypefaceWithFallback(File fontFile, int ttcIndex) {
        Typeface typeface = createTypeface(fontFile, ttcIndex);
        
        if (typeface != null) {
            return typeface;
        }
        
        Log.w(TAG, "All attempts failed, returning default Typeface");
        return Typeface.DEFAULT;
    }
    
    public static Typeface createTypefaceWithWeightAndFallback(File fontFile, float weight) {
        return createTypefaceWithWeightAndFallback(fontFile, weight, 0);
    }

    public static Typeface createTypefaceWithWeightAndFallback(File fontFile, float weight, int ttcIndex) {
        Typeface typeface = createTypefaceWithWeight(fontFile, weight, ttcIndex);
        
        if (typeface != null) {
            return typeface;
        }
        
        Log.w(TAG, "All attempts failed, returning default Typeface");
        return Typeface.DEFAULT;
    }
                }
