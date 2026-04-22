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
 *
 * ★ الإضافة: دعم كامل لقائمة المفضلة ★
 * - favoritesLiveData يراقب قاعدة البيانات تلقائياً
 * - عمليات الحذف وإعادة التسمية تُحدّث القائمتين معاً لضمان التزامن
 * - منطق المفضلة مطابق لـ Samsung Notes (مختلط → إضافة، كلها مفضلة → إزالة)
 */
public class LocalFontListViewModel extends AndroidViewModel {
    
    private static final String TAG = "LocalFontListViewModel";
    
    private final LocalFontRepository repository;
    private final LocalFontPreferenceManager preferenceManager;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<String> errorMessageLiveData;
    private final MutableLiveData<List<FontEntity>> fontsLiveData;

    // ★ LiveData الخاصة بقائمة المفضلة — تراقب قاعدة البيانات تلقائياً ★
    private final MutableLiveData<List<FontEntity>> favoritesLiveData;
    
    // ════════════════════════════════════════════════════════════
    // نموذج البيانات الموسّع بالوزن/العرض والمفضلة
    // ════════════════════════════════════════════════════════════

    public static class FontFileInfoWithMetadata {
        private final String name;
        private final String path;
        private final long size;
        private final long lastModified;
        private final String realName;
        // ★ وصف الوزن والعرض ("Bold, Condensed" أو "غير معروف" إلخ) ★
        private final String weightWidthLabel;
        // ★ حالة المفضلة — تُستخدم لعرض أيقونة النجمة بجانب العنصر ★
        private final boolean isFavorite;
        
        /**
         * المُنشئ الأساسي: يُنشأ من FontEntity المُخزَّن في قاعدة البيانات.
         * يقرأ weightWidthLabel و isFavorite مباشرةً من الكيان.
         */
        public FontFileInfoWithMetadata(FontEntity entity) {
            this.name             = entity.getFileName();
            this.path             = entity.getPath();
            this.size             = entity.getSize();
            this.lastModified     = entity.getLastModified();
            this.realName         = entity.getRealName();
            this.weightWidthLabel = entity.getWeightWidthLabel(); // ★ جديد ★
            this.isFavorite       = entity.isFavorite();          // ★ جديد ★
        }
        
        /**
         * المُنشئ اليدوي: يُستخدم في حالات خاصة (مثل إعادة التسمية).
         * weightWidthLabel و isFavorite اختياريان ويمكن تمرير null/false إذا لم يكونا متاحَين.
         */
        public FontFileInfoWithMetadata(String name, String path, long size,
                                        long lastModified, String realName,
                                        String weightWidthLabel, boolean isFavorite) {
            this.name             = name;
            this.path             = path;
            this.size             = size;
            this.lastModified     = lastModified;
            this.realName         = realName;
            this.weightWidthLabel = weightWidthLabel; // ★ جديد ★
            this.isFavorite       = isFavorite;       // ★ جديد ★
        }
        
        public String  getName()             { return name; }
        public String  getPath()             { return path; }
        public long    getSize()             { return size; }
        public long    getLastModified()     { return lastModified; }
        public String  getRealName()         { return realName; }
        // ★ getters الجديدة ★
        public String  getWeightWidthLabel() { return weightWidthLabel; }
        public boolean isFavorite()          { return isFavorite; }
        
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

        // ★ تهيئة LiveData للمفضلة ★
        favoritesLiveData = new MutableLiveData<>(new ArrayList<>());
        
        // تحميل البيانات من قاعدة البيانات وربطها
        repository.getLocalFonts().observeForever(entities -> {
            if (entities != null) {
                fontsLiveData.postValue(entities);
            }
        });

        // ★ مراقبة قاعدة البيانات للمفضلة تلقائياً ★
        repository.getFavoriteFonts().observeForever(entities -> {
            if (entities != null) {
                favoritesLiveData.postValue(entities);
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

    // ════════════════════════════════════════════════════════════
    // ★ Favorites LiveData — قائمة المفضلة ★
    // ════════════════════════════════════════════════════════════

    /**
     * إرجاع قائمة المفضلة كـ LiveData<List<FontFileInfoWithMetadata>>
     * تُراقَب تلقائياً — أي تغيير في قاعدة البيانات يُحدِّث الواجهة فوراً
     */
    public LiveData<List<FontFileInfoWithMetadata>> getFavoritesLiveData() {
        return Transformations.map(favoritesLiveData, entities -> {
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

    /**
     * عدد الخطوط المفضلة — يُستخدم للعرض في رأس القائمة
     */
    public LiveData<Integer> getFavoritesCountLiveData() {
        return repository.getFavoriteFontsCount();
    }

    /**
     * ★ تبديل حالة المفضلة لخط واحد ★
     *
     * يُستخدم عند الضغط على أيقونة المفضلة في قائمة الخطوط المحلية
     *
     * @param path       مسار الخط
     * @param isFavorite true للإضافة، false للإزالة
     */
    public void toggleFavorite(String path, boolean isFavorite) {
        repository.updateFavoriteStatus(path, isFavorite, success -> {
            if (success) {
                Log.d(TAG, "★ Favorite toggled: " + path + " → " + isFavorite);
            } else {
                Log.w(TAG, "Failed to toggle favorite: " + path);
            }
        });
    }

    /**
     * ★ تبديل حالة المفضلة لمجموعة خطوط (التحديد المتعدد) ★
     *
     * المنطق المتبع (مطابق لـ Samsung Notes):
     * - إذا كانت العناصر مزيجاً من مفضلة وغير مفضلة → إضافة الجميع
     * - إذا كانت جميعها مفضلة → إزالة الجميع
     * - إذا كانت جميعها غير مفضلة → إضافة الجميع
     *
     * يُستدعى من وضع التحديد المتعدد بعد حساب isFavorite بناءً على المنطق أعلاه
     *
     * @param paths      قائمة مسارات الخطوط المحددة
     * @param isFavorite الحالة الجديدة المراد تطبيقها
     * @param onSuccess  يُنفَّذ على الخيط الرئيسي بعد نجاح العملية
     */
    public void toggleFavoritesBatch(List<String> paths, boolean isFavorite, Runnable onSuccess) {
        if (paths == null || paths.isEmpty()) return;

        repository.updateFavoriteStatusBatch(paths, isFavorite, success -> {
            if (success) {
                Log.d(TAG, "★ Batch favorite toggled: " + paths.size() + " fonts → " + isFavorite);
                if (onSuccess != null) {
                    new Handler(Looper.getMainLooper()).post(onSuccess);
                }
            } else {
                Log.w(TAG, "Failed to batch toggle favorites");
            }
        });
    }

    /**
     * ★ حساب حالة المفضلة للتحديد المتعدد (منطق Samsung Notes) ★
     *
     * يُستدعى من وضع التحديد المتعدد لتحديد ما إذا كان يجب عرض
     * "إضافة إلى المفضلة" أو "إزالة من المفضلة"
     *
     * - إذا كانت جميع العناصر المحددة مفضلة → return false (إزالة)
     * - في أي حالة أخرى (مختلطة أو كلها غير مفضلة) → return true (إضافة)
     *
     * @param selectedPaths قائمة مسارات العناصر المحددة
     * @return true إذا كان يجب إضافة إلى المفضلة، false للإزالة
     */
    public boolean shouldAddToFavorites(List<String> selectedPaths) {
        if (selectedPaths == null || selectedPaths.isEmpty()) return true;

        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList == null) return true;

        int favoriteCount = 0;
        for (FontEntity entity : currentList) {
            if (selectedPaths.contains(entity.getPath()) && entity.isFavorite()) {
                favoriteCount++;
            }
        }

        // إذا كانت جميعها مفضلة → إزالة، وإلا → إضافة
        return favoriteCount < selectedPaths.size();
    }

    /**
     * ★ نسخة shouldAddToFavorites للاستخدام من داخل قائمة المفضلة ★
     *
     * تعمل على favoritesLiveData بدلاً من fontsLiveData
     * لأن قائمة المفضلة تعرض فقط الخطوط المفضلة
     *
     * @param selectedPaths قائمة مسارات العناصر المحددة في قائمة المفضلة
     * @return دائماً false لأن جميع عناصر قائمة المفضلة هي مفضلة بالتعريف
     */
    public boolean shouldAddToFavoritesFromFavoritesList(List<String> selectedPaths) {
        // جميع عناصر قائمة المفضلة هي مفضلة بالتعريف → دائماً إزالة
        return false;
    }

    /**
     * البحث في الخطوط المفضلة
     */
    public LiveData<List<FontEntity>> searchFavorites(String query) {
        if (query == null || query.trim().isEmpty()) {
            return repository.getFavoriteFonts();
        }
        return repository.searchFavoriteFonts(query.trim());
    }

    /**
     * الخطوط المفضلة مع الترتيب
     */
    public LiveData<List<FontEntity>> getSortedFavorites(LocalFontRepository.SortType sortType,
                                                          boolean ascending) {
        if (sortType == null) {
            return repository.getFavoriteFonts();
        }

        switch (sortType) {
            case DATE:
                return repository.getFavoritesSortedByDate(ascending);
            case SIZE:
                return repository.getFavoritesSortedBySize(ascending);
            case NAME:
            default:
                return repository.getFavoritesSortedByName(ascending);
        }
    }

    // ════════════════════════════════════════════════════════════
    // عمليات الحذف وإعادة التسمية — تُحدِّث القائمتين معاً
    // ════════════════════════════════════════════════════════════
    
    /**
     * ★ إعادة تسمية خط وتحديث القائمتين في الذاكرة (النقطة 9) ★
     * 
     * هذه الدالة تعيد تسمية الملف الفعلي ثم تحدث قائمة الخطوط المحلية
     * وقائمة المفضلة في الذاكرة مباشرة دون إعادة تحميل من القرص لتجنب الوميض.
     *
     * ★ إضافة: تحديث favoritesLiveData إذا كان الخط مفضلاً ★
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
        
        // ★ تحديث قائمة الخطوط المحلية في الذاكرة ★
        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList != null) {
            List<FontEntity> updatedList = new ArrayList<>(currentList);
            
            for (int i = 0; i < updatedList.size(); i++) {
                FontEntity entity = updatedList.get(i);
                if (entity.getPath().equals(oldPath)) {
                    // إنشاء كيان محدث مع الحفاظ على البيانات الأصلية
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
                    // ★ الحفاظ على حالة المفضلة بعد إعادة التسمية ★
                    updatedEntity.setFavorite(entity.isFavorite());
                    
                    updatedList.set(i, updatedEntity);
                    break;
                }
            }
            
            // تحديث LiveData فوراً
            fontsLiveData.postValue(updatedList);
        }

        // ★ تحديث قائمة المفضلة في الذاكرة إذا كان الخط مفضلاً ★
        List<FontEntity> currentFavorites = favoritesLiveData.getValue();
        if (currentFavorites != null) {
            List<FontEntity> updatedFavorites = new ArrayList<>(currentFavorites);

            for (int i = 0; i < updatedFavorites.size(); i++) {
                FontEntity entity = updatedFavorites.get(i);
                if (entity.getPath().equals(oldPath)) {
                    FontEntity updatedEntity = new FontEntity(
                        newFile.getAbsolutePath(),
                        newFileName
                    );
                    updatedEntity.setSize(entity.getSize());
                    updatedEntity.setLastModified(newFile.lastModified());
                    updatedEntity.setRealName(entity.getRealName());
                    updatedEntity.setAccessCount(entity.getAccessCount());
                    updatedEntity.setLastAccessTime(entity.getLastAccessTime());
                    updatedEntity.setWeightWidthLabel(entity.getWeightWidthLabel());
                    updatedEntity.setFavorite(true); // بالتعريف دائماً true هنا
                    
                    updatedFavorites.set(i, updatedEntity);
                    break;
                }
            }

            favoritesLiveData.postValue(updatedFavorites);
        }
        
        // تحديث قاعدة البيانات في الخلفية
        repository.updatePath(oldPath, newFile.getAbsolutePath(), newFileName);
        
        Log.d(TAG, "Font renamed in memory: " + oldPath + " -> " + newFile.getAbsolutePath());
        
        return true;
    }
    
    /**
     * ★ حذف خطوط وتحديث القائمتين في الذاكرة باستخدام Background Thread ★
     * ★ الإصلاح: نقل عملية I/O إلى خيط خلفي لمنع تجميد النظام ★
     * ★ الإضافة: تحديث favoritesLiveData إذا كانت الخطوط المحذوفة تتضمن مفضلة ★
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
            
            // 2. تحديث قائمة الخطوط المحلية في الذاكرة
            List<FontEntity> updatedList = new ArrayList<>();
            for (FontEntity entity : currentList) {
                if (!successfullyDeleted.contains(entity.getPath())) {
                    updatedList.add(entity);
                }
            }
            fontsLiveData.postValue(updatedList);

            // 3. ★ تحديث قائمة المفضلة في الذاكرة إذا كانت الخطوط المحذوفة تتضمن مفضلة ★
            List<FontEntity> currentFavorites = favoritesLiveData.getValue();
            if (currentFavorites != null && !currentFavorites.isEmpty()) {
                List<FontEntity> updatedFavorites = new ArrayList<>();
                for (FontEntity entity : currentFavorites) {
                    if (!successfullyDeleted.contains(entity.getPath())) {
                        updatedFavorites.add(entity);
                    }
                }
                favoritesLiveData.postValue(updatedFavorites);
            }
            
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

    // ════════════════════════════════════════════════════════════
    // الدوال المساعدة
    // ════════════════════════════════════════════════════════════
    
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

    /**
     * البحث عن موقع خط في قائمة المفضلة (للتمرير السلس بعد إعادة التسمية)
     */
    public int findFavoritePositionByPath(String path) {
        List<FontEntity> currentFavorites = favoritesLiveData.getValue();
        if (currentFavorites == null || path == null) {
            return -1;
        }

        for (int i = 0; i < currentFavorites.size(); i++) {
            if (currentFavorites.get(i).getPath().equals(path)) {
                return i;
            }
        }

        return -1;
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
