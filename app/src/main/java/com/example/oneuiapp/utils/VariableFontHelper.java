package com.example.oneuiapp.utils;

import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontVariationAxis;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * VariableFontHelper - مساعد الخطوط المتغيرة مع دعم كامل لملفات TTC
 * 
 * الإصلاحات المطبقة:
 * 1. إضافة دعم كامل لملفات TTC في جميع الدوال
 * 2. إصلاح createTypefaceWithWeight لتطبيق الوزن بشكل صحيح مع TTC Index
 * 3. إصلاح extractVariableInstances لقراءة المعلومات من الفهرس الصحيح
 * 4. ★ إصلاح createTypefaceWithWeight: إزالة الاستثناء الخاص بالوزن 400
 *    لضمان تطبيق محور wght صراحةً دائماً، حتى لا يُرسَم الخط بقيمته الافتراضية
 *    الداخلية (في fvar) عندما تختلف عن 400 كما في بعض خطوط Samsung One VF ★
 */
public class VariableFontHelper {
    
    private static final String TAG = "VariableFontHelper";
    
    public static class VariableInstance {
        public String name;
        public String tag;
        public float value;
        
        public VariableInstance(String name, String tag, float value) {
            this.name = name;
            this.tag = tag;
            this.value = value;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    /**
     * فحص إذا كان الخط متغيراً - مع دعم TTC Index
     */
    public static boolean isVariableFont(File fontFile, int ttcIndex) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        
        if (fontFile == null || !fontFile.exists()) {
            return false;
        }
        
        try {
            FontVariationAxis[] axes = getVariationAxes(fontFile, ttcIndex);
            if (axes != null && axes.length > 0) {
                return true;
            }
        } catch (Exception e) {
            // Continue to fallback check
        }
        
        return fvarTableExists(fontFile, ttcIndex);
    }
    
    /**
     * نسخة للتوافق مع الكود القديم
     */
    public static boolean isVariableFont(File fontFile) {
        return isVariableFont(fontFile, 0);
    }
    
    /**
     * الحصول على محاور التغيير من الخط مع دعم TTC Index
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static FontVariationAxis[] getVariationAxes(File fontFile, int ttcIndex) {
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
            }
            Font font = fontBuilder.build();
            return font.getAxes();
        } catch (Exception e) {
            return null; 
        }
    }
    
    /**
     * فحص وجود جدول fvar في الخط مع دعم TTC Index
     */
    private static boolean fvarTableExists(File fontFile, int ttcIndex) {
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            
            if ("ttcf".equals(tag)) {
                // ملف TTC - نقفز للفهرس المطلوب
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) {
                    return false;
                }
                
                // القفز للفهرس المطلوب
                raf.seek(12 + (ttcIndex * 4));
                long fontOffset = readUInt32(raf);
                return checkFvarAtOffset(raf, fontOffset);
            } else {
                // ملف TTF/OTF عادي
                raf.seek(4);
                int numTables = readUInt16(raf);
                
                for (int i = 0; i < numTables; i++) {
                    raf.seek(12 + i * 16);
                    byte[] tableTag = new byte[4];
                    raf.read(tableTag);
                    String tagStr = new String(tableTag, "US-ASCII");
                    
                    if ("fvar".equals(tagStr)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Failed to read file
        }
        return false;
    }
    
    /**
     * فحص وجود جدول fvar في موقع محدد
     */
    private static boolean checkFvarAtOffset(RandomAccessFile raf, long fontOffset) {
        try {
            raf.seek(fontOffset + 4);
            int numTables = readUInt16(raf);
            
            for (int i = 0; i < numTables; i++) {
                raf.seek(fontOffset + 12 + i * 16);
                byte[] tableTag = new byte[4];
                raf.read(tableTag);
                String tagStr = new String(tableTag, "US-ASCII");
                
                if ("fvar".equals(tagStr)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Failed
        }
        return false;
    }
    
    /**
     * استخراج حالات الخطوط المتغيرة مع دعم TTC Index
     */
    public static List<VariableInstance> extractVariableInstances(File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return instances;
        }
        
        if (!isVariableFont(fontFile, ttcIndex)) {
            return instances;
        }
        
        try {
            float[] range = readWeightRangeFromFvar(fontFile, ttcIndex);
            float minWeight = range[0];
            float maxWeight = range[1];
            
            addWeightIfInRange(instances, "Thin", 100, minWeight, maxWeight);
            addWeightIfInRange(instances, "Extra Light", 200, minWeight, maxWeight);
            addWeightIfInRange(instances, "Light", 300, minWeight, maxWeight);
            addWeightIfInRange(instances, "Regular", 400, minWeight, maxWeight);
            addWeightIfInRange(instances, "Medium", 500, minWeight, maxWeight);
            addWeightIfInRange(instances, "Semi Bold", 600, minWeight, maxWeight);
            addWeightIfInRange(instances, "Bold", 700, minWeight, maxWeight);
            addWeightIfInRange(instances, "Extra Bold", 800, minWeight, maxWeight);
            addWeightIfInRange(instances, "Black", 900, minWeight, maxWeight);
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to extract instances", e);
        }
        
        return instances;
    }
    
    /**
     * نسخة للتوافق مع الكود القديم
     */
    public static List<VariableInstance> extractVariableInstances(File fontFile) {
        return extractVariableInstances(fontFile, 0);
    }
    
    /**
     * قراءة نطاق الوزن من جدول fvar مع دعم TTC Index
     */
    private static float[] readWeightRangeFromFvar(File fontFile, int ttcIndex) {
        float[] defaultRange = {100f, 900f};
        
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            
            long fontOffset = 0;
            
            if ("ttcf".equals(tag)) {
                // ملف TTC - القفز للفهرس المطلوب
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) {
                    return defaultRange;
                }
                
                raf.seek(12 + (ttcIndex * 4));
                fontOffset = readUInt32(raf);
            }
            
            // البحث عن جدول fvar
            raf.seek(fontOffset + 4);
            int numTables = readUInt16(raf);
            
            long fvarOffset = -1;
            for (int i = 0; i < numTables; i++) {
                raf.seek(fontOffset + 12 + i * 16);
                byte[] tableTag = new byte[4];
                raf.read(tableTag);
                String tagStr = new String(tableTag, "US-ASCII");
                
                if ("fvar".equals(tagStr)) {
                    raf.seek(fontOffset + 12 + i * 16 + 8);
                    fvarOffset = readUInt32(raf);
                    break;
                }
            }
            
            if (fvarOffset == -1) {
                return defaultRange;
            }
            
            // قراءة معلومات fvar
            raf.seek(fvarOffset + 4);
            int axesArrayOffset = readUInt16(raf);
            raf.seek(fvarOffset + 8);
            int axisCount = readUInt16(raf);
            int axisSize = readUInt16(raf);
            
            // البحث عن محور wght
            for (int i = 0; i < axisCount; i++) {
                long axisPos = fvarOffset + axesArrayOffset + (i * axisSize);
                raf.seek(axisPos);
                
                byte[] axisTag = new byte[4];
                raf.read(axisTag);
                String axisTagStr = new String(axisTag, "US-ASCII");
                
                if ("wght".equals(axisTagStr)) {
                    float minValue = readFixed(raf);
                    float defaultValue = readFixed(raf);
                    float maxValue = readFixed(raf);
                    
                    return new float[]{minValue, maxValue};
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to read weight range from fvar", e);
        }
        
        return defaultRange;
    }
    
    private static int readUInt16(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[2];
        raf.read(bytes);
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }
    
    private static long readUInt32(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[4];
        raf.read(bytes);
        return ((long)(bytes[0] & 0xFF) << 24) | 
               ((long)(bytes[1] & 0xFF) << 16) | 
               ((long)(bytes[2] & 0xFF) << 8) | 
               (long)(bytes[3] & 0xFF);
    }
    
    private static float readFixed(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[4];
        raf.read(bytes);
        int value = ((bytes[0] & 0xFF) << 24) | 
                    ((bytes[1] & 0xFF) << 16) | 
                    ((bytes[2] & 0xFF) << 8) | 
                    (bytes[3] & 0xFF);
        return value / 65536.0f;
    }
    
    private static void addWeightIfInRange(List<VariableInstance> instances, 
                                          String name, float value, 
                                          float min, float max) {
        if (value >= min && value <= max) {
            instances.add(new VariableInstance(name, "wght", value));
        }
    }

    /**
     * إنشاء Typeface مع وزن محدد - مع دعم TTC
     */
    public static Typeface createTypefaceWithWeight(File fontFile, float weight) {
        return createTypefaceWithWeight(fontFile, weight, 0);
    }

    /**
     * إنشاء Typeface مع وزن محدد و TTC Index - النسخة المحدثة
     *
     * ★ الإصلاح: حُذف الاستثناء الخاص بالوزن 400 (weight != 400).
     *   سابقاً كان الكود يتخطى تعيين محور wght عندما يكون الوزن المطلوب 400،
     *   مما يجعل Android يُرسم الخط بقيمته الافتراضية الداخلية في جدول fvar.
     *   بعض الخطوط المتغيرة (كـ Samsung One Extra Lite VF) تُعرّف قيمة افتراضية
     *   مختلفة عن 400، فيظهر الخط بوزن خاطئ رغم اختيار Regular في المنتقي.
     *   الإصلاح: تطبيق محور wght صراحةً لأي وزن موجب، بما فيه 400. ★
     */
    public static Typeface createTypefaceWithWeight(File fontFile, float weight, int ttcIndex) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception e) {
                return null;
            }
        }
        
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);
            
            // تعيين TTC Index
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                android.util.Log.d(TAG, "Set TTC index: " + ttcIndex);
            }
            
            // ★ تطبيق الوزن صراحةً لأي قيمة موجبة دون استثناء 400 ★
            // هذا يضمن أن الخط يُرسم بالوزن المطلوب تحديداً وليس بقيمته الافتراضية
            // المدوّنة في جدول fvar، والتي قد تختلف عن 400 في بعض الخطوط.
            if (weight > 0) {
                String variationSettings = "'wght' " + weight;
                fontBuilder.setFontVariationSettings(variationSettings);
                android.util.Log.d(TAG, "Set variation settings: " + variationSettings);
            }
            
            Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            Typeface result = fallbackBuilder.build();
            android.util.Log.d(TAG, "Successfully created variable Typeface with weight: " + weight + 
                                   ", ttcIndex: " + ttcIndex);
            
            return result;
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create variable Typeface with weight: " + weight + 
                                    ", ttcIndex: " + ttcIndex, e);
            
            // Fallback: محاولة إنشاء typeface عادي
            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception ex) {
                return null;
            }
        }
    }
        }
