package com.example.oneuiapp.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.oneuiapp.data.dao.FontDao;
import com.example.oneuiapp.data.database.AppDatabase;
import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.fontlist.systemfont.SystemFontInfo;
import com.example.oneuiapp.fontlist.systemfont.SystemFontManager;
import com.example.oneuiapp.metadata.FontMetadataExtractor;
import com.example.oneuiapp.metadata.FontWeightWidthExtractor; // ★ جديد ★

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * SystemFontRepository - Fixed version
 * ★ الحل: عدم حفظ اسم الملف كاسم حقيقي في قاعدة البيانات ★
 * ★ الإضافة: استخراج فوري وخلفي لوصف الوزن والعرض (weight_width_label) ★
 */
public class SystemFontRepository {

    private static final String TAG = "SystemFontRepository";
    private static volatile SystemFontRepository INSTANCE;

    private final FontDao fontDao;
    private final ExecutorService executorService;
    private final Context context;

    private SystemFontRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(this.context);
        fontDao = database.fontDao();
        executorService = AppDatabase.databaseWriteExecutor;
    }

    public static SystemFontRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SystemFontRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SystemFontRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<FontEntity>> getSystemFonts() {
        return fontDao.getSystemFonts();
    }

    public LiveData<Integer> getSystemFontsCount() {
        return fontDao.getSystemFontsCount();
    }

    public void loadAndSyncSystemFonts(OnSyncCompleteListener listener) {
        executorService.execute(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                    if (listener != null) {
                        listener.onSyncComplete(0, 0, 0);
                    }
                    return;
                }

                SystemFontManager manager = SystemFontManager.getInstance();
                List<SystemFontInfo> systemFonts = manager.getSystemFonts();

                if (systemFonts == null || systemFonts.isEmpty()) {
                    if (listener != null) {
                        listener.onSyncComplete(0, 0, 0);
                    }
                    return;
                }

                List<FontEntity> existingFonts = fontDao.getSystemFontsSync();
                List<FontEntity> fontsToAdd = new ArrayList<>();
                int updated = 0;

                for (SystemFontInfo fontInfo : systemFonts) {
                    FontEntity existing = findByPath(existingFonts, fontInfo.getPath());

                    if (existing == null) {
                        FontEntity newFont = createFontEntityFromSystemFont(fontInfo);
                        fontsToAdd.add(newFont);

                        if (newFont.getRealName() != null && !newFont.getRealName().isEmpty()) {
                            Log.d(TAG, "✓ System font with ready name: " + newFont.getRealName());
                        }
                    } else {
                        boolean needsUpdate = false;

                        if (existing.getLastModified() != fontInfo.getLastModified()) {
                            existing.setLastModified(fontInfo.getLastModified());
                            needsUpdate = true;
                        }

                        if (existing.getSize() != fontInfo.getSize()) {
                            existing.setSize(fontInfo.getSize());
                            needsUpdate = true;
                        }

                        // ★ الحل: التحقق من الاسم الحقيقي بشكل صحيح ★
                        String infoRealName = fontInfo.getRealName();
                        if (infoRealName != null && !infoRealName.isEmpty()) {
                            String currentRealName = existing.getRealName();
                            if (currentRealName == null || currentRealName.isEmpty() ||
                                !currentRealName.equals(infoRealName)) {
                                existing.setRealName(infoRealName);
                                needsUpdate = true;
                            }
                        }

                        if (needsUpdate) {
                            existing.setUpdatedAt(System.currentTimeMillis());
                            fontDao.update(existing);
                            updated++;
                        }
                    }
                }

                if (!fontsToAdd.isEmpty()) {
                    fontDao.insertAll(fontsToAdd);
                }

                if (listener != null) {
                    listener.onSyncComplete(fontsToAdd.size(), updated, 0);
                }

                // ★ استخراج موازي في الخلفية للأسماء الحقيقية لخطوط النظام ★
                extractSystemFontsRealNamesInBackgroundPrioritized();

                // ★ استخراج موازي في الخلفية لوصف الوزن والعرض لخطوط النظام ★
                extractSystemFontsWeightWidthInBackground();

            } catch (Exception e) {
                Log.e(TAG, "Failed to sync system fonts", e);
                if (listener != null) {
                    listener.onSyncComplete(0, 0, 0);
                }
            }
        });
    }

    /**
     * ★★★ الحل الأساسي: عدم حفظ اسم الملف كاسم حقيقي ★★★
     *
     * يستخرج الاسم الحقيقي ووصف الوزن/العرض فورياً عند إنشاء الكيان الجديد.
     */
    private FontEntity createFontEntityFromSystemFont(SystemFontInfo fontInfo) {
        FontEntity entity = new FontEntity(fontInfo.getPath(), fontInfo.getName());
        entity.setSize(fontInfo.getSize());
        entity.setLastModified(fontInfo.getLastModified());
        entity.setSystemFont(true);
        entity.setTtcIndex(fontInfo.getTtcIndex());
        entity.setVariableFont(fontInfo.isVariableFont());

        // ★ الحل: getRealName() الآن ترجع null إذا لم يوجد اسم حقيقي ★
        // لن نحفظ اسم الملف كاسم حقيقي أبداً
        String realName = fontInfo.getRealName();
        if (realName != null && !realName.isEmpty()) {
            entity.setRealName(realName);
        }
        // إذا كان null، لن نحفظ شيء (ستبقى القيمة null في قاعدة البيانات)

        // ★ محاولة استخراج الاسم الحقيقي فوراً للخطوط الصغيرة ★
        if (realName == null || realName.isEmpty()) {
            try {
                File fontFile = new File(fontInfo.getPath());
                if (fontFile.exists()) {
                    String extractedName = FontMetadataExtractor.extractFontName(
                        fontFile, fontInfo.getTtcIndex());

                    if (extractedName != null && !extractedName.isEmpty() &&
                        !extractedName.equals("Unknown Font")) {
                        entity.setRealName(extractedName);
                        Log.d(TAG, "✓ Instantly extracted system font name: " + extractedName);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Quick extraction failed for system font: " + e.getMessage());
            }
        }

        // ★ استخراج فوري لوصف الوزن والعرض ★
        // العملية سريعة لأنها تقرأ بضعة بايتات فقط من جدول OS/2
        try {
            File fontFile = new File(fontInfo.getPath());
            if (fontFile.exists()) {
                String weightWidthLabel = FontWeightWidthExtractor.extract(
                    fontFile, fontInfo.getTtcIndex());
                entity.setWeightWidthLabel(weightWidthLabel);
                Log.d(TAG, "✓ Instantly extracted weight/width: " + weightWidthLabel);
            }
        } catch (Exception e) {
            Log.w(TAG, "Quick weight/width extraction failed for system font: " + e.getMessage());
        }

        String fileName = fontInfo.getName().toLowerCase();
        if (fileName.endsWith(".ttc")) {
            entity.setFontType("TTC");
        } else if (fileName.endsWith(".otf")) {
            entity.setFontType("OTF");
        } else {
            entity.setFontType("TTF");
        }

        if (fontInfo.isVariableFont()) {
            entity.setFontType("Variable");
        }

        return entity;
    }

    private FontEntity findByPath(List<FontEntity> fonts, String path) {
        for (FontEntity font : fonts) {
            if (font.getPath().equals(path)) {
                return font;
            }
        }
        return null;
    }

    private void extractSystemFontsRealNamesInBackgroundPrioritized() {
        executorService.execute(() -> {
            try {
                List<FontEntity> fontsWithoutNames = fontDao.getFontsWithoutRealName(500);

                fontsWithoutNames.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

                int extracted = 0;
                for (FontEntity font : fontsWithoutNames) {
                    if (font.isSystemFont()) {
                        try {
                            File fontFile = new File(font.getPath());
                            if (fontFile.exists()) {
                                String realName = FontMetadataExtractor.extractFontName(
                                    fontFile, font.getTtcIndex());

                                if (realName != null && !realName.isEmpty() &&
                                    !realName.equals("Unknown Font")) {
                                    fontDao.updateRealName(font.getPath(), realName,
                                        System.currentTimeMillis());
                                    extracted++;

                                    if (extracted % 10 == 0) {
                                        Log.d(TAG, "Background extraction: " + extracted + " system font names");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to extract name in background: " + e.getMessage());
                        }
                    }
                }

                Log.d(TAG, "★ Background extraction complete: " + extracted + " system font names extracted");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract system fonts real names", e);
            }
        });
    }

    /**
     * ★ استخراج وصف الوزن والعرض في الخلفية لخطوط النظام التي لم تُعالَج بعد ★
     *
     * تُستدعى بعد كل مزامنة للتأكد من أن جميع خطوط النظام — بما فيها الموجودة مسبقاً
     * في قاعدة البيانات قبل إضافة هذه الميزة — تحمل وصفاً لوزنها وعرضها.
     * تستخدم getFontsWithoutWeightWidth() للعثور على الخطوط غير المعالَجة،
     * وتُرتّبها من الأصغر إلى الأكبر للحصول على أسرع نتيجة أولى.
     */
    private void extractSystemFontsWeightWidthInBackground() {
        executorService.execute(() -> {
            try {
                List<FontEntity> fontsWithoutLabel = fontDao.getFontsWithoutWeightWidth(500);

                // ترتيب حسب الحجم (الأصغر أولاً) لضمان أسرع استجابة
                fontsWithoutLabel.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

                int extracted = 0;
                for (FontEntity font : fontsWithoutLabel) {
                    if (font.isSystemFont()) {
                        try {
                            File fontFile = new File(font.getPath());
                            if (fontFile.exists()) {
                                String label = FontWeightWidthExtractor.extract(
                                    fontFile, font.getTtcIndex());
                                fontDao.updateWeightWidthLabel(
                                    font.getPath(), label, System.currentTimeMillis());
                                extracted++;

                                if (extracted % 10 == 0) {
                                    Log.d(TAG, "System font weight/width extraction: " + extracted + " labels");
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to extract weight/width for system font: " + e.getMessage());
                        }
                    }
                }

                Log.d(TAG, "★ System font weight/width extraction complete: " + extracted + " labels extracted");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract weight/width for system fonts in background", e);
            }
        });
    }

    public void updateRealName(String path, String realName) {
        executorService.execute(() -> {
            try {
                fontDao.updateRealName(path, realName, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to update real name", e);
            }
        });
    }

    public void recordAccess(String path) {
        executorService.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                fontDao.recordAccess(path, now, now);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record access", e);
            }
        });
    }

    public void updateCacheStatus(String path, boolean isCached) {
        executorService.execute(() -> {
            try {
                fontDao.updateCacheStatus(path, isCached, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cache status", e);
            }
        });
    }

    public LiveData<FontEntity> getFontByPath(String path) {
        return fontDao.getFontByPath(path);
    }

    public LiveData<List<FontEntity>> searchSystemFonts(String query) {
        return fontDao.searchFonts(true, query);
    }

    public LiveData<List<FontEntity>> getSystemFontsSorted(SortType sortType, boolean ascending) {
        switch (sortType) {
            case DATE:
                return ascending ? fontDao.getFontsSortedByDate(true)
                                : fontDao.getFontsSortedByDateDesc(true);
            case SIZE:
                return ascending ? fontDao.getFontsSortedBySize(true)
                                : fontDao.getFontsSortedBySizeDesc(true);
            case NAME:
            default:
                return ascending ? fontDao.getFontsSortedByName(true)
                                : fontDao.getFontsSortedByNameDesc(true);
        }
    }

    public void deleteAllSystemFonts(OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.deleteAllSystemFonts();
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete system fonts", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void getVariableFontsCount(OnCountListener listener) {
        executorService.execute(() -> {
            try {
                int count = fontDao.getVariableFontsCount();
                if (listener != null) {
                    listener.onCount(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get variable fonts count", e);
                if (listener != null) {
                    listener.onCount(0);
                }
            }
        });
    }

    public enum SortType {
        NAME, DATE, SIZE
    }

    public interface OnSyncCompleteListener {
        void onSyncComplete(int added, int updated, int deleted);
    }

    public interface OnCompleteListener {
        void onComplete(boolean success);
    }

    public interface OnCountListener {
        void onCount(int count);
    }
                                }
