package com.example.oneuiapp.fontlist.localfont;

import android.util.Log;

import com.example.oneuiapp.fontlist.FontFileInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * LocalFontDirectoryManager - مدير قراءة الخطوط من المجلدات المحلية
 * محدّث لدعم ملفات TTC بالكامل
 *
 * ملاحظة معمارية:
 * نموذج البيانات FontFileInfo تم فصله إلى ملف مستقل في حزمة fontlist
 * ليكون قابلاً للاستخدام من قِبَل قائمة النظام وقائمة المجلد المحلي دون
 * الحاجة لاستيراد منطق قراءة المجلدات.
 */
public class LocalFontDirectoryManager {

    private static final String TAG = "LocalFontDirectoryMgr";

    /**
     * الحصول على قائمة الخطوط من مجلد محلي مباشر
     * @param directoryPath المسار المباشر للمجلد
     * @return قائمة بمعلومات ملفات الخطوط
     */
    public static List<FontFileInfo> getFontsInDirectory(String directoryPath) {
        List<FontFileInfo> fontFiles = new ArrayList<>();

        if (directoryPath == null || directoryPath.isEmpty()) {
            Log.w(TAG, "Directory path is null or empty");
            return fontFiles;
        }

        try {
            File directory = new File(directoryPath);

            if (!directory.exists()) {
                Log.w(TAG, "Directory does not exist: " + directoryPath);
                return fontFiles;
            }

            if (!directory.isDirectory()) {
                Log.w(TAG, "Path is not a directory: " + directoryPath);
                return fontFiles;
            }

            File[] files = directory.listFiles();

            if (files == null) {
                Log.w(TAG, "Cannot list files in directory (permission denied?): " + directoryPath);
                return fontFiles;
            }

            for (File file : files) {
                if (file.isFile() && file.canRead()) {
                    String name      = file.getName();
                    String nameLower = name.toLowerCase();

                    // ★★★ الإصلاح: إضافة دعم TTC ★★★
                    if (nameLower.endsWith(".ttf") || nameLower.endsWith(".otf") || nameLower.endsWith(".ttc")) {
                        fontFiles.add(new FontFileInfo(
                            name,
                            file.getAbsolutePath(),
                            file.length(),
                            file.lastModified()
                        ));
                    }
                }
            }

            Log.d(TAG, "Found " + fontFiles.size() + " font files in: " + directoryPath);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception reading directory: " + directoryPath, e);
        } catch (Exception e) {
            Log.e(TAG, "Error reading directory: " + directoryPath, e);
        }

        return fontFiles;
    }

    /**
     * فحص ما إذا كان المجلد موجوداً وقابلاً للقراءة
     * @param directoryPath المسار المراد فحصه
     * @return true إذا كان المجلد موجوداً وقابلاً للقراءة
     */
    public static boolean isDirectoryAccessible(String directoryPath) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            return false;
        }

        try {
            File directory = new File(directoryPath);
            return directory.exists() && directory.isDirectory() && directory.canRead();
        } catch (Exception e) {
            Log.e(TAG, "Error checking directory accessibility: " + directoryPath, e);
            return false;
        }
    }

    /**
     * عدّ ملفات الخطوط في مجلد دون قراءتها بالكامل
     * @param directoryPath المسار المراد فحصه
     * @return عدد ملفات الخطوط
     */
    public static int countFontsInDirectory(String directoryPath) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            return 0;
        }

        try {
            File directory = new File(directoryPath);

            if (!directory.exists() || !directory.isDirectory()) {
                return 0;
            }

            File[] files = directory.listFiles();
            if (files == null) {
                return 0;
            }

            int count = 0;
            for (File file : files) {
                if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    // ★★★ الإصلاح: إضافة دعم TTC ★★★
                    if (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc")) {
                        count++;
                    }
                }
            }

            return count;

        } catch (Exception e) {
            Log.e(TAG, "Error counting fonts in directory: " + directoryPath, e);
            return 0;
        }
    }
}
