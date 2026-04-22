package com.example.oneuiapp.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.oneuiapp.data.dao.FontDao;
import com.example.oneuiapp.data.entity.FontEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppDatabase - قاعدة البيانات الرئيسية للتطبيق
 *
 * ★ الإصدار 2: إضافة عمود weight_width_label في جدول fonts ★
 * يُخزّن وصف الوزن والعرض المُستخرج من جدول OS/2 لكل خط.
 *
 * ★ الإصدار 3: إضافة عمود is_favorite في جدول fonts ★
 * يُخزّن حالة المفضلة (0 = عادي، 1 = مفضل).
 * تُستخدم Migration 2→3 للحفاظ على البيانات الموجودة بدلاً من
 * إعادة بناء قاعدة البيانات (fallbackToDestructiveMigration).
 */
@Database(
    entities = {FontEntity.class},
    version = 3,          // ★ رُفع من 2 إلى 3 بسبب إضافة is_favorite ★
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "oneui_fonts_database";
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // ════════════════════════════════════════════════════════════
    // ★ Migration 2 → 3: إضافة عمود is_favorite ★
    // يضيف العمود بقيمة افتراضية 0 (false) لجميع السجلات الموجودة،
    // مما يُحافظ على بيانات الخطوط (الأسماء الحقيقية والوزن/العرض إلخ)
    // دون الحاجة لإعادة مسح المجلد من الصفر.
    // ════════════════════════════════════════════════════════════
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // إضافة عمود is_favorite بقيمة افتراضية 0 (ليس في المفضلة)
            database.execSQL(
                "ALTER TABLE fonts ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0"
            );
            // إضافة فهرس على is_favorite لتسريع استعلام getFavoriteFonts()
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_fonts_is_favorite ON fonts (is_favorite)"
            );
        }
    };
    
    public abstract FontDao fontDao();
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    // ★ استخدام Migration بدلاً من fallbackToDestructiveMigration ★
                    // للحفاظ على البيانات الموجودة عند الترقية من الإصدار 2 إلى 3
                    .addMigrations(MIGRATION_2_3)
                    // ★ احتياطي: في حال وُجد إصدار غير متوقع ★
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    public static void destroyInstance() {
        INSTANCE = null;
    }
}
