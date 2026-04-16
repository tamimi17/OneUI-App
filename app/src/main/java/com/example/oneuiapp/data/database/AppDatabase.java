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
 * بما أن fallbackToDestructiveMigration() مُفعَّل، تُعاد إنشاء
 * قاعدة البيانات تلقائياً عند ترقية التطبيق دون الحاجة لكتابة
 * Migration يدوي.
 */
@Database(
    entities = {FontEntity.class},
    version = 2,          // ★ رُفع من 1 إلى 2 بسبب إضافة weight_width_label ★
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "oneui_fonts_database";
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
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
                    .fallbackToDestructiveMigration() // يُعيد بناء قاعدة البيانات عند اختلاف الإصدار
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
