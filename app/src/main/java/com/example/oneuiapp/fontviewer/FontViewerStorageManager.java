package com.example.oneuiapp.fontviewer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * FontViewerStorageManager - مدير متخصص في إدارة تخزين الخطوط المعروضة
 * يتولى نسخ ملفات الخطوط من URI إلى مساحة التطبيق الداخلية للعرض
 * منفصل عن FontImportManager الذي يتعامل مع استيراد مجموعات الخطوط
 */
public class FontViewerStorageManager {
    
    private static final String TAG = "FontViewerStorageManager";
    private static final String VIEWER_FONTS_DIR = "fonts";
    private static final int BUFFER_SIZE = 8192;
    
    private final Context context;
    
    /**
     * Constructor
     * @param context السياق المطلوب للوصول إلى نظام الملفات
     */
    public FontViewerStorageManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * نسخ ملف خط من URI إلى مساحة التطبيق الداخلية للعرض
     * 
     * @param fontUri URI للخط المراد نسخه
     * @param suggestedFileName اسم الملف المقترح (يمكن أن يكون null)
     * @return File يمثل النسخة المحلية أو null إذا فشلت العملية
     */
    public File copyFontForViewing(Uri fontUri, String suggestedFileName) {
        if (fontUri == null) {
            Log.w(TAG, "Font URI is null");
            return null;
        }
        
        try {
            String fileName = suggestedFileName != null ? suggestedFileName : getFileNameFromUri(fontUri);
            if (fileName == null) {
                fileName = "font_" + System.currentTimeMillis() + ".ttf";
            }
            
            String extension = ".ttf";
            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
                extension = fileName.substring(lastDotIndex);
            }
            
            File fontsDirectory = getFontsDirectory();
            if (fontsDirectory == null) {
                Log.e(TAG, "Failed to get or create fonts directory");
                return null;
            }
            
            File outputFile = new File(fontsDirectory, "viewer_font_" + UUID.randomUUID().toString() + extension);
            
            try (InputStream inputStream = context.getContentResolver().openInputStream(fontUri);
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                
                if (inputStream == null) {
                    Log.e(TAG, "Failed to open input stream for URI: " + fontUri);
                    return null;
                }
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
                Log.d(TAG, "Successfully copied font to: " + outputFile.getAbsolutePath());
                return outputFile;
                
            } catch (Exception e) {
                Log.e(TAG, "Error copying font file", e);
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in copyFontForViewing", e);
            return null;
        }
    }
    
    /**
     * استخراج اسم الملف من URI
     * يحاول عدة طرق لضمان الحصول على اسم مناسب
     * 
     * @param uri URI المراد استخراج اسم الملف منه
     * @return اسم الملف أو null إذا فشلت جميع المحاولات
     */
    public String getFileNameFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        
        String fileName = null;
        
        try {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(
                    uri, 
                    new String[]{OpenableColumns.DISPLAY_NAME}, 
                    null, 
                    null, 
                    null
                );
                
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get filename from ContentResolver", e);
        }
        
        if (fileName == null) {
            try {
                DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
                if (documentFile != null && documentFile.getName() != null) {
                    fileName = documentFile.getName();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get filename from DocumentFile", e);
            }
        }
        
        if (fileName == null) {
            String path = uri.getPath();
            if (path != null) {
                int lastSlashIndex = path.lastIndexOf('/');
                if (lastSlashIndex >= 0 && lastSlashIndex < path.length() - 1) {
                    fileName = path.substring(lastSlashIndex + 1);
                }
            }
        }
        
        if (fileName == null) {
            fileName = "font_" + System.currentTimeMillis() + ".ttf";
        }
        
        return fileName;
    }
    
    /**
     * الحصول على مجلد الخطوط المعروضة أو إنشاؤه
     * 
     * @return مجلد الخطوط أو null إذا فشل الإنشاء
     */
    public File getFontsDirectory() {
        File fontsDirectory = new File(context.getFilesDir(), VIEWER_FONTS_DIR);
        
        if (!fontsDirectory.exists()) {
            if (!fontsDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create fonts directory");
                return null;
            }
        }
        
        return fontsDirectory;
    }
    
    /**
     * تنظيف الملفات القديمة من مجلد الخطوط المعروضة
     * يحذف جميع الملفات ماعدا الملف الحالي المستخدم
     * 
     * @param currentFontPath مسار الملف الحالي الذي يجب الاحتفاظ به
     * @return عدد الملفات المحذوفة
     */
    public int cleanupOldViewerFonts(String currentFontPath) {
        File fontsDirectory = getFontsDirectory();
        if (fontsDirectory == null || !fontsDirectory.exists()) {
            return 0;
        }
        
        File[] files = fontsDirectory.listFiles();
        if (files == null) {
            return 0;
        }
        
        int deletedCount = 0;
        
        for (File file : files) {
            if (file.isFile()) {
                if (currentFontPath != null && file.getAbsolutePath().equals(currentFontPath)) {
                    continue;
                }
                
                if (file.delete()) {
                    deletedCount++;
                    Log.d(TAG, "Deleted old viewer font: " + file.getName());
                }
            }
        }
        
        Log.d(TAG, "Cleaned up " + deletedCount + " old viewer font files");
        return deletedCount;
    }
    
    /**
     * حذف ملف خط معين
     * 
     * @param fontPath مسار الملف المراد حذفه
     * @return true إذا تم الحذف بنجاح
     */
    public boolean deleteFontFile(String fontPath) {
        if (fontPath == null) {
            return false;
        }
        
        try {
            File fontFile = new File(fontPath);
            if (fontFile.exists() && fontFile.delete()) {
                Log.d(TAG, "Deleted font file: " + fontPath);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting font file: " + fontPath, e);
        }
        
        return false;
    }
    
    /**
     * الحصول على عدد ملفات الخطوط المعروضة
     * 
     * @return عدد الملفات في مجلد الخطوط المعروضة
     */
    public int getViewerFontsCount() {
        File fontsDirectory = getFontsDirectory();
        if (fontsDirectory == null || !fontsDirectory.exists()) {
            return 0;
        }
        
        File[] files = fontsDirectory.listFiles();
        return files != null ? files.length : 0;
    }
    
    /**
     * فحص ما إذا كان ملف موجود ويمكن قراءته
     * 
     * @param fontPath مسار الملف المراد فحصه
     * @return true إذا كان الملف موجود وقابل للقراءة
     */
    public boolean isFontFileValid(String fontPath) {
        if (fontPath == null) {
            return false;
        }
        
        try {
            File fontFile = new File(fontPath);
            return fontFile.exists() && fontFile.isFile() && fontFile.canRead();
        } catch (Exception e) {
            Log.e(TAG, "Error checking font file validity: " + fontPath, e);
            return false;
        }
    }
    /**
     * استخراج المسار الأصلي الكامل من URI (يعمل على Android 13+)
     */
    public String getRealPathFromUri(Uri uri) {
        if (uri == null) return null;

        String realPath = null;

        if (DocumentsContract.isDocumentUri(context, uri)) {
            String documentId = DocumentsContract.getDocumentId(uri);
            if (documentId != null && documentId.startsWith("primary:")) {
                realPath = "/storage/emulated/0/" + documentId.substring(8);
            }
        }

        if (realPath == null) {
            realPath = uri.getPath();
        }

        if (realPath != null && new File(realPath).exists()) {
            return realPath;
        }

        return null;
    }
}
