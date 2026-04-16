package com.example.oneuiapp.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.data.repository.LocalFontRepository;
import com.example.oneuiapp.fontlist.localfont.LocalFontPreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * LocalFontListViewModel - محدث بدعم الحذف وإعادة التسمية في الذاكرة
 *
 * ★ التعديل: إضافة weightWidthLabel إلى FontFileInfoWithMetadata ★
 * يُمرَّر من FontEntity إلى الـ Adapter ومنه إلى LocalFontViewHolder.
 */
public class LocalFontListViewModel extends AndroidViewModel {
    
    private static final String TAG = "LocalFontListViewModel";
    
    private final LocalFontRepository repository;
    private final LocalFontPreferenceManager preferenceManager;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<String> errorMessageLiveData;
    private final MutableLiveData<List<FontEntity>> fontsLiveData;
    
    // ════════════════════════════════════════════════════════════
    // نموذج البيانات الموسّع بالوزن/العرض
    // ════════════════════════════════════════════════════════════

    public static class FontFileInfoWithMetadata {
        private final String name;
        private final String path;
        private final long size;
        private final long lastModified;
        private final String realName;
        // ★ الحقل الجديد: وصف الوزن والعرض ("Bold, Condensed" أو "غير معروف" إلخ) ★
        private final String weightWidthLabel;
        
        /**
         * المُنشئ الأساسي: يُنشأ من FontEntity المُخزَّن في قاعدة البيانات.
         * يقرأ weightWidthLabel مباشرةً من الكيان.
         */
        public FontFileInfoWithMetadata(FontEntity entity) {
            this.name             = entity.getFileName();
            this.path             = entity.getPath();
            this.size             = entity.getSize();
            this.lastModified     = entity.getLastModified();
            this.realName         = entity.getRealName();
            this.weightWidthLabel = entity.getWeightWidthLabel(); // ★ جديد ★
        }
        
        /**
         * المُنشئ اليدوي: يُستخدم في حالات خاصة (مثل إعادة التسمية).
         * weightWidthLabel اختياري ويمكن تمريره null إذا لم يكن متاحاً.
         */
        public FontFileInfoWithMetadata(String name, String path, long size,
                                        long lastModified, String realName,
                                        String weightWidthLabel) {
            this.name             = name;
            this.path             = path;
            this.size             = size;
            this.lastModified     = lastModified;
            this.realName         = realName;
            this.weightWidthLabel = weightWidthLabel; // ★ جديد ★
        }
        
        public String getName()             { return name; }
        public String getPath()             { return path; }
        public long   getSize()             { return size; }
        public long   getLastModified()     { return lastModified; }
        public String getRealName()         { return realName; }
        // ★ getter الجديد ★
        public String getWeightWidthLabel() { return weightWidthLabel; }
        
        private String getDisplayName() {
            String displayName = name;
            if (displayName.toLowerCase().endsWith(".ttf") || 
                displayName.toLowerCase().endsWith(".otf") ||
                displayName.toLowerCase().endsWith(".ttc")) {
                int extensionPos = displayName.lastIndexOf('.');
                if (extensionPos > 0) {
                    displayName = displayName.substring(0, extensionPos);
                }
            }
            return displayName;
        }
    }
    
    public LocalFontListViewModel(@NonNull Application application) {
        super(application);
        
        repository = LocalFontRepository.getInstance(application);
        preferenceManager = new LocalFontPreferenceManager(application);
        isLoadingLiveData = new MutableLiveData<>(false);
        errorMessageLiveData = new MutableLiveData<>();
        
        // ★ تحويل إلى MutableLiveData للتحكم اليدوي ★
        fontsLiveData = new MutableLiveData<>(new ArrayList<>());
        
        // تحميل البيانات من قاعدة البيانات وربطها
        repository.getLocalFonts().observeForever(entities -> {
            if (entities != null) {
                fontsLiveData.postValue(entities);
            }
        });
    }
    
    public LiveData<List<FontFileInfoWithMetadata>> getFontsLiveData() {
        return Transformations.map(fontsLiveData, entities -> {
            if (entities == null) {
                return new ArrayList<>();
            }
            
            List<FontFileInfoWithMetadata> result = new ArrayList<>();
            for (FontEntity entity : entities) {
                result.add(new FontFileInfoWithMetadata(entity));
            }
            return result;
        });
    }
    
    public LiveData<Integer> getFontsCountLiveData() {
        return repository.getLocalFontsCount();
    }
    
    public LiveData<Boolean> getIsLoadingLiveData() {
        return isLoadingLiveData;
    }
    
    public LiveData<String> getErrorMessageLiveData() {
        return errorMessageLiveData;
    }
    
    public void loadFonts() {
        String folderPath = preferenceManager.getFontFolderPath();
        if (folderPath == null) {
            Log.w(TAG, "No folder path saved");
            return;
        }
        
        loadFontsFromPath(folderPath);
    }
    
    public void loadFontsFromPath(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            return;
        }
        
        List<FontEntity> currentData = fontsLiveData.getValue();
        boolean hasExistingData = currentData != null && !currentData.isEmpty();
        
        if (!hasExistingData) {
            isLoadingLiveData.postValue(true);
        }
        
        repository.loadAndSyncLocalFonts(folderPath, new LocalFontRepository.OnSyncCompleteListener() {
            @Override
            public void onSyncComplete(int added, int updated, int deleted) {
                if (!hasExistingData) {
                    isLoadingLiveData.postValue(false);
                }
                
                String message = String.format("Synced: %d added, %d updated, %d deleted", 
                    added, updated, deleted);
                Log.d(TAG, message);
            }
        });
    }
    
    /**
     * ★ إعادة تسمية خط وتحديث القائمة في الذاكرة (النقطة 9) ★
     * 
     * هذه الدالة تعيد تسمية الملف الفعلي ثم تحدث القائمة في الذاكرة
     * مباشرة دون إعادة تحميل من القرص لتجنب الوميض
     */
    public boolean renameFontInMemory(String oldPath, String newFileName) {
        File oldFile = new File(oldPath);
        
        if (!oldFile.exists()) {
            errorMessageLiveData.postValue("الملف غير موجود");
            return false;
        }
        
        File parentDir = oldFile.getParentFile();
        if (parentDir == null) {
            errorMessageLiveData.postValue("خطأ في المسار");
            return false;
        }
        
        File newFile = new File(parentDir, newFileName);
        
        // التحقق من عدم وجود ملف بنفس الاسم
        if (newFile.exists()) {
            errorMessageLiveData.postValue("الاسم موجود بالفعل");
            return false;
        }
        
        // إعادة تسمية الملف الفعلي
        if (!oldFile.renameTo(newFile)) {
            errorMessageLiveData.postValue("فشلت إعادة التسمية");
            return false;
        }
        
        // ★ تحديث القائمة في الذاكرة ★
        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList != null) {
            List<FontEntity> updatedList = new ArrayList<>(currentList);
            
            for (int i = 0; i < updatedList.size(); i++) {
                FontEntity entity = updatedList.get(i);
                if (entity.getPath().equals(oldPath)) {
                    // إنشاء كيان محدث مع الحفاظ على weightWidthLabel الأصلي
                    FontEntity updatedEntity = new FontEntity(
                        newFile.getAbsolutePath(),
                        newFileName
                    );
                    updatedEntity.setSize(entity.getSize());
                    updatedEntity.setLastModified(newFile.lastModified());
                    updatedEntity.setRealName(entity.getRealName());
                    updatedEntity.setAccessCount(entity.getAccessCount());
                    updatedEntity.setLastAccessTime(entity.getLastAccessTime());
                    // ★ الحفاظ على وصف الوزن/العرض بعد إعادة التسمية ★
                    updatedEntity.setWeightWidthLabel(entity.getWeightWidthLabel());
                    
                    updatedList.set(i, updatedEntity);
                    break;
                }
            }
            
            // تحديث LiveData فوراً
            fontsLiveData.postValue(updatedList);
            
            // تحديث قاعدة البيانات في الخلفية
            repository.updatePath(oldPath, newFile.getAbsolutePath(), newFileName);
            
            Log.d(TAG, "Font renamed in memory: " + oldPath + " -> " + newFile.getAbsolutePath());
        }
        
        return true;
    }
    
    /**
     * ★ حذف خطوط وتحديث القائمة في الذاكرة باستخدام Background Thread ★
     * ★ الإصلاح: نقل عملية I/O إلى خيط خلفي لمنع تجميد النظام ★
     */
    public void deleteFontsInMemory(List<String> pathsToDelete, Runnable onSuccess) {
        if (pathsToDelete == null || pathsToDelete.isEmpty()) {
            return;
        }
        
        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList == null) {
            return;
        }

        // نقل عملية الحذف الثقيلة (I/O) إلى خيط خلفي لتجنب تجميد الواجهة والنظام
        com.example.oneuiapp.data.database.AppDatabase.databaseWriteExecutor.execute(() -> {
            List<String> successfullyDeleted = new ArrayList<>();
            
            // 1. حذف الملفات الفعلية في الخلفية
            for (String path : pathsToDelete) {
                File file = new File(path);
                if (file.exists() && file.delete()) {
                    successfullyDeleted.add(path);
                    Log.d(TAG, "Deleted file: " + path);
                } else {
                    Log.w(TAG, "Failed to delete file: " + path);
                }
            }
            
            if (successfullyDeleted.isEmpty()) {
                errorMessageLiveData.postValue("فشل الحذف");
                return;
            }
            
            // 2. تحديث القائمة في الذاكرة
            List<FontEntity> updatedList = new ArrayList<>();
            for (FontEntity entity : currentList) {
                if (!successfullyDeleted.contains(entity.getPath())) {
                    updatedList.add(entity);
                }
            }
            
            // 3. تحديث الواجهة فوراً عبر postValue
            fontsLiveData.postValue(updatedList);
            
            // 4. حذف من قاعدة البيانات
            for (String path : successfullyDeleted) {
                repository.deleteByPath(path, null);
            }
            
            Log.d(TAG, "Deleted " + successfullyDeleted.size() + " fonts from memory");
            
            // 5. إعلام الـ Fragment بالنجاح على الخيط الرئيسي
            if (onSuccess != null) {
                new Handler(Looper.getMainLooper()).post(onSuccess);
            }
        });
    }
    
    /**
     * البحث عن موقع خط بعد إعادة التسمية (للتمرير السلس)
     */
    public int findFontPositionByPath(String path) {
        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList == null || path == null) {
            return -1;
        }
        
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).getPath().equals(path)) {
                return i;
            }
        }
        
        return -1;
    }
    
    public LiveData<List<FontEntity>> searchFonts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return repository.getLocalFonts();
        }
        return repository.searchFonts(false, query.trim());
    }
    
    public LiveData<List<FontEntity>> getSortedFonts(LocalFontRepository.SortType sortType, boolean ascending) {
        if (sortType == null) {
            return repository.getLocalFonts();
        }
        
        switch (sortType) {
            case DATE:
                return ascending ? repository.getFontsSortedByDate(false, true)
                                : repository.getFontsSortedByDate(false, false);
            case SIZE:
                return ascending ? repository.getFontsSortedBySize(false, true)
                                : repository.getFontsSortedBySize(false, false);
            case NAME:
            default:
                return ascending ? repository.getFontsSortedByName(false, true)
                                : repository.getFontsSortedByName(false, false);
        }
    }
    
    public void recordFontAccess(String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            repository.recordAccess(fontPath);
        }
    }
    
    public void updateFontRealName(String fontPath, String realName) {
        if (fontPath != null && realName != null) {
            repository.updateRealName(fontPath, realName);
        }
    }
    
    public void updateFontCacheStatus(String fontPath, boolean isCached) {
        if (fontPath != null) {
            repository.updateCacheStatus(fontPath, isCached);
        }
    }
    
    public void refreshFonts() {
        String folderPath = preferenceManager.getFontFolderPath();
        if (folderPath != null) {
            loadFontsFromPath(folderPath);
        }
    }
    
    public void saveFolderPath(String folderPath) {
        if (folderPath != null && !folderPath.isEmpty()) {
            preferenceManager.saveFontFolderPath(folderPath);
        }
    }
    
    public String getSavedFolderPath() {
        return preferenceManager.getFontFolderPath();
    }
    
    public boolean hasSavedFolder() {
        return preferenceManager.hasFontFolderPath();
    }
    
    public LiveData<FontEntity> getFontByPath(String fontPath) {
        if (fontPath == null || fontPath.isEmpty()) {
            return new MutableLiveData<>(null);
        }
        return repository.getFontByPath(fontPath);
    }
    
    public void deleteFont(FontEntity font, LocalFontRepository.OnCompleteListener listener) {
        if (font != null) {
            repository.delete(font, listener);
        }
    }
    
    public void deleteAllLocalFonts(LocalFontRepository.OnCompleteListener listener) {
        repository.deleteByPath(null, success -> {
            if (listener != null) {
                listener.onComplete(success);
            }
            if (success) {
                Log.d(TAG, "All local fonts deleted");
            }
        });
    }
}
