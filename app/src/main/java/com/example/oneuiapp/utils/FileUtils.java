package com.example.oneuiapp.utils;

/**
 * FileUtils - أدوات مساعدة للتعامل مع أسماء الملفات
 * 
 * تُستخدم لإزالة صيغة الملف (.ttf, .otf, .ttc) من الأسماء المعروضة في القوائم
 */
public class FileUtils {
    
    /**
     * إزالة صيغة الملف من الاسم
     * 
     * أمثلة:
     * - "Cairo-Bold.ttf" → "Cairo-Bold"
     * - "Roboto.otf" → "Roboto"
     * - "NotoSans.ttc" → "NotoSans"
     * - "MyFont" → "MyFont" (بدون تغيير إذا لم توجد صيغة)
     * 
     * @param fileName اسم الملف مع الصيغة
     * @return اسم الملف بدون الصيغة
     */
    public static String removeExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        // البحث عن آخر نقطة في الاسم
        int lastDotIndex = fileName.lastIndexOf('.');
        
        // إذا وجدت نقطة وليست في البداية
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        
        // إذا لم توجد نقطة، نرجع الاسم كما هو
        return fileName;
    }
    
    /**
     * الحصول على صيغة الملف فقط
     * 
     * أمثلة:
     * - "Cairo-Bold.ttf" → "ttf"
     * - "Roboto.otf" → "otf"
     * - "MyFont" → "" (فارغ إذا لم توجد صيغة)
     * 
     * @param fileName اسم الملف
     * @return صيغة الملف بدون النقطة
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        
        return "";
    }
    
    /**
     * فحص إذا كان الملف هو ملف خط
     * 
     * @param fileName اسم الملف
     * @return true إذا كان ملف خط (ttf, otf, ttc)
     */
    public static boolean isFontFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        String extension = getExtension(fileName).toLowerCase();
        return "ttf".equals(extension) || 
               "otf".equals(extension) || 
               "ttc".equals(extension);
    }
    
    /**
     * الحصول على اسم الملف من المسار الكامل
     * 
     * مثال:
     * - "/storage/emulated/0/Fonts/Cairo.ttf" → "Cairo.ttf"
     * 
     * @param fullPath المسار الكامل للملف
     * @return اسم الملف فقط
     */
    public static String getFileNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }
        
        int lastSlashIndex = fullPath.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < fullPath.length() - 1) {
            return fullPath.substring(lastSlashIndex + 1);
        }
        
        return fullPath;
    }
    
    /**
     * الحصول على اسم الملف بدون صيغة من المسار الكامل
     * 
     * مثال:
     * - "/storage/emulated/0/Fonts/Cairo.ttf" → "Cairo"
     * 
     * @param fullPath المسار الكامل للملف
     * @return اسم الملف بدون صيغة
     */
    public static String getFileNameWithoutExtension(String fullPath) {
        String fileName = getFileNameFromPath(fullPath);
        return removeExtension(fileName);
    }
}
