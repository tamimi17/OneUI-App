package com.example.oneuiapp.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * FontEntity - الكيان الأساسي لتمثيل الخطوط في قاعدة البيانات
 * 
 * يخزن المعلومات الأساسية والوصفية لكل خط
 *
 * ★ الإصدار 2: إضافة حقل weight_width_label لتخزين وصف الوزن والعرض ★
 * مثال: "Bold, Condensed" أو "VF · Regular" أو "غير معروف"
 */
@Entity(
    tableName = "fonts",
    indices = {
        @Index(value = "path", unique = true),
        @Index(value = "is_system_font"),
        @Index(value = "last_modified")
    }
)
public class FontEntity {
    
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;
    
    @NonNull
    @ColumnInfo(name = "path")
    private String path;
    
    @NonNull
    @ColumnInfo(name = "file_name")
    private String fileName;
    
    @Nullable
    @ColumnInfo(name = "real_name")
    private String realName;
    
    @ColumnInfo(name = "size")
    private long size;
    
    @ColumnInfo(name = "last_modified")
    private long lastModified;
    
    @Nullable
    @ColumnInfo(name = "font_type")
    private String fontType;
    
    @ColumnInfo(name = "ttc_index")
    private int ttcIndex;
    
    @ColumnInfo(name = "is_system_font")
    private boolean isSystemFont;
    
    @ColumnInfo(name = "is_variable_font")
    private boolean isVariableFont;
    
    @ColumnInfo(name = "is_cached")
    private boolean isCached;
    
    @ColumnInfo(name = "last_access_time")
    private long lastAccessTime;
    
    @ColumnInfo(name = "access_count")
    private int accessCount;
    
    @ColumnInfo(name = "created_at")
    private long createdAt;
    
    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    // ★ الحقل الجديد: وصف الوزن والعرض المُستخرج من جدول OS/2 ★
    // أمثلة: "Bold, Condensed" / "VF · Regular" / "غير معروف"
    // يُعبأ بواسطة FontWeightWidthExtractor عند المزامنة أو في الخلفية
    @Nullable
    @ColumnInfo(name = "weight_width_label")
    private String weightWidthLabel;
    
    public FontEntity(@NonNull String path, @NonNull String fileName) {
        this.path = path;
        this.fileName = fileName;
        this.size = 0;
        this.lastModified = 0;
        this.ttcIndex = 0;
        this.isSystemFont = false;
        this.isVariableFont = false;
        this.isCached = false;
        this.lastAccessTime = 0;
        this.accessCount = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    @NonNull
    public String getPath() {
        return path;
    }
    
    public void setPath(@NonNull String path) {
        this.path = path;
    }
    
    @NonNull
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(@NonNull String fileName) {
        this.fileName = fileName;
    }
    
    @Nullable
    public String getRealName() {
        return realName;
    }
    
    public void setRealName(@Nullable String realName) {
        this.realName = realName;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    @Nullable
    public String getFontType() {
        return fontType;
    }
    
    public void setFontType(@Nullable String fontType) {
        this.fontType = fontType;
    }
    
    public int getTtcIndex() {
        return ttcIndex;
    }
    
    public void setTtcIndex(int ttcIndex) {
        this.ttcIndex = ttcIndex;
    }
    
    public boolean isSystemFont() {
        return isSystemFont;
    }
    
    public void setSystemFont(boolean systemFont) {
        isSystemFont = systemFont;
    }
    
    public boolean isVariableFont() {
        return isVariableFont;
    }
    
    public void setVariableFont(boolean variableFont) {
        isVariableFont = variableFont;
    }
    
    public boolean isCached() {
        return isCached;
    }
    
    public void setCached(boolean cached) {
        isCached = cached;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }
    
    public int getAccessCount() {
        return accessCount;
    }
    
    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ════════════════════════════════════════════════════════════
    // ★ getter/setter للحقل الجديد weight_width_label ★
    // ════════════════════════════════════════════════════════════

    @Nullable
    public String getWeightWidthLabel() {
        return weightWidthLabel;
    }

    public void setWeightWidthLabel(@Nullable String weightWidthLabel) {
        this.weightWidthLabel = weightWidthLabel;
    }
    
    public String getDisplayName() {
        if (realName != null && !realName.isEmpty()) {
            return realName;
        }
        
        String name = fileName;
        if (name.toLowerCase().endsWith(".ttf") || 
            name.toLowerCase().endsWith(".otf") ||
            name.toLowerCase().endsWith(".ttc")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
    
    public void recordAccess() {
        this.lastAccessTime = System.currentTimeMillis();
        this.accessCount++;
        this.updatedAt = System.currentTimeMillis();
    }
}
