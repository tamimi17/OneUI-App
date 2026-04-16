package com.example.oneuiapp.fontlist.localfont;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import androidx.fragment.app.Fragment;
import java.io.File;

/**
 * LocalFontDirectoryPickerManager - مدير اختيار المجلدات بالوصول المباشر
 * يستخدم Intent بسيط لاختيار المجلد من التخزين المباشر
 */
public class LocalFontDirectoryPickerManager {
    
    private static final String TAG = "LocalFontDirectoryPickerManager";
    public static final int FOLDER_PICKER_REQUEST_CODE = 200;
    
    private final Context context;
    private final Fragment fragment;
    private DirectorySelectionListener listener;
    
    public interface DirectorySelectionListener {
        void onDirectorySelected(String directoryPath);
        void onDirectorySelectionCancelled();
        void onDirectorySelectionError(Exception error);
    }
    
    public LocalFontDirectoryPickerManager(Fragment fragment) {
        this.fragment = fragment;
        this.context = fragment.requireContext();
    }
    
    public void setDirectorySelectionListener(DirectorySelectionListener listener) {
        this.listener = listener;
    }
    
    /**
     * فتح واجهة اختيار المجلد
     * يستخدم file picker بسيط للوصول المباشر
     */
    public void openDirectoryPicker() {
        try {
            // إنشاء Intent لاختيار مجلد
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            
            // محاولة تحديد مجلد البداية (Download أو المجلد الرئيسي)
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null && downloadsDir.exists()) {
                    intent.putExtra("android.provider.extra.INITIAL_URI", downloadsDir.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not set initial directory", e);
            }
            
            if (fragment != null) {
                fragment.startActivityForResult(intent, FOLDER_PICKER_REQUEST_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch directory picker", e);
            if (listener != null) {
                listener.onDirectorySelectionError(e);
            }
        }
    }
    
    /**
     * معالجة نتيجة اختيار المجلد
     * يحول URI إلى مسار مباشر
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FOLDER_PICKER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                try {
                    android.net.Uri treeUri = data.getData();
                    
                    // محاولة الحصول على المسار المباشر من URI
                    String directoryPath = getRealPathFromTreeUri(treeUri);
                    
                    if (directoryPath != null) {
                        Log.d(TAG, "Directory selected: " + directoryPath);
                        if (listener != null) {
                            listener.onDirectorySelected(directoryPath);
                        }
                    } else {
                        Log.e(TAG, "Could not convert URI to direct path: " + treeUri);
                        if (listener != null) {
                            listener.onDirectorySelectionError(
                                new Exception("Could not convert selected folder to direct path. Please select a folder in internal or external storage.")
                            );
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing directory selection", e);
                    if (listener != null) {
                        listener.onDirectorySelectionError(e);
                    }
                }
            } else {
                Log.d(TAG, "Directory selection cancelled");
                if (listener != null) {
                    listener.onDirectorySelectionCancelled();
                }
            }
            return true;
        }
        return false;
    }
    
    /**
     * تحويل Tree URI إلى مسار مباشر
     */
    private String getRealPathFromTreeUri(android.net.Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        
        try {
            String uriPath = treeUri.toString();
            
            // معالجة primary storage
            if (uriPath.contains("primary:")) {
                String relativePath = uriPath.substring(uriPath.indexOf("primary:") + 8);
                return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relativePath;
            }
            
            // معالجة external storage
            if (uriPath.contains("/storage/")) {
                int storageIndex = uriPath.indexOf("/storage/");
                String potentialPath = uriPath.substring(storageIndex);
                
                // تنظيف المسار من معلومات URI الإضافية
                if (potentialPath.contains("%3A")) {
                    potentialPath = potentialPath.replace("%3A", "/");
                }
                if (potentialPath.contains(":")) {
                    potentialPath = potentialPath.replace(":", "/");
                }
                
                // إزالة أي بادئات غير ضرورية
                if (potentialPath.startsWith("/storage/emulated/0/")) {
                    return potentialPath;
                } else if (potentialPath.contains("/storage/")) {
                    return potentialPath.substring(potentialPath.indexOf("/storage/"));
                }
            }
            
            // محاولة أخيرة: استخدام document ID
            String documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
            if (documentId != null) {
                if (documentId.startsWith("primary:")) {
                    String path = documentId.substring(8);
                    return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
                } else if (documentId.contains(":")) {
                    String[] parts = documentId.split(":");
                    if (parts.length > 1) {
                        // محاولة بناء المسار من المعرف
                        String basePath = "/storage/" + parts[0];
                        File baseDir = new File(basePath);
                        if (baseDir.exists()) {
                            return basePath + "/" + parts[1];
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting URI to path", e);
        }
        
        // الحل البديل: استخدام مجلد افتراضي في التخزين الداخلي
        Log.w(TAG, "Could not determine real path, using fallback");
        return null;
    }
    
    /**
     * الحصول على مجلد افتراضي للخطوط في حالة عدم تمكن من تحويل URI
     */
    public String getDefaultFontsFolder() {
        File defaultDir = new File(Environment.getExternalStorageDirectory(), "Fonts");
        if (!defaultDir.exists()) {
            defaultDir.mkdirs();
        }
        return defaultDir.getAbsolutePath();
    }
              }
