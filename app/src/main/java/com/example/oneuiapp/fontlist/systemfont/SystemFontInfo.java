package com.example.oneuiapp.fontlist.systemfont;


/**
 * SystemFontInfo - Fixed version
 * ★ الحل: إضافة دالة للحصول على الاسم الحقيقي فقط (بدون fallback لاسم الملف) ★
 *
 * ★ التعديل: إضافة حقل weightWidthLabel لتمرير وصف الوزن والعرض
 *   من SystemFontRepository إلى SystemFontListAdapter ومنه إلى SystemFontViewHolder.
 *   أمثلة: "Bold, Condensed" | "VF · Regular" | "غير معروف"
 */
public class SystemFontInfo {
    
    private final String name;
    private final String path;
    private final long size;
    private final long lastModified;
    private final int weight;
    private final int slant;
    private final int ttcIndex;
    private final String axes;
    private String realName;
    // ★ حقل جديد: وصف الوزن والعرض المُستخرج من جدول OS/2 ★
    private String weightWidthLabel;
    
    public SystemFontInfo(String name, String path, long size, long lastModified,
                         int weight, int slant, int ttcIndex, String axes) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
        this.weight = weight;
        this.slant = slant;
        this.ttcIndex = ttcIndex;
        this.axes = axes;
        this.realName = null;
        this.weightWidthLabel = null; // ★ يُملأ لاحقاً من FontEntity ★
    }
    
    public String getName() {
        return name;
    }
    
    public String getPath() {
        return path;
    }
    
    public long getSize() {
        return size;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public int getWeight() {
        return weight;
    }
    
    public int getSlant() {
        return slant;
    }
    
    public int getTtcIndex() {
        return ttcIndex;
    }
    
    public String getAxes() {
        return axes;
    }
    
    public boolean isVariableFont() {
        return axes != null && !axes.isEmpty();
    }
    
    public void setRealName(String realName) {
        this.realName = realName;
    }
    
    /**
     * ★ الدالة الصحيحة: ترجع الاسم الحقيقي فقط (أو null) ★
     */
    public String getRealName() {
        return realName;
    }

    // ════════════════════════════════════════════════════════════
    // ★ getter/setter لوصف الوزن والعرض ★
    // ════════════════════════════════════════════════════════════

    /**
     * تعيين وصف الوزن والعرض المُستخرج من قاعدة البيانات.
     * يُستدعى في SystemFontListFragment.convertEntitiesToSystemFontInfo().
     */
    public void setWeightWidthLabel(String weightWidthLabel) {
        this.weightWidthLabel = weightWidthLabel;
    }

    /**
     * الحصول على وصف الوزن والعرض.
     * يُستخدم في SystemFontListAdapter لتمريره إلى SystemFontViewHolder.
     */
    public String getWeightWidthLabel() {
        return weightWidthLabel;
    }
    
    public String getDisplayName() {
        if (realName != null && !realName.isEmpty()) {
            return realName;
        }
        
        String displayName = name;
        if (displayName.toLowerCase().endsWith(".ttf") || displayName.toLowerCase().endsWith(".otf")) {
            displayName = displayName.substring(0, displayName.length() - 4);
        }
        return displayName;
    }
    
    public String getWeightName() {
        switch (weight) {
            case 100: return "Thin";
            case 200: return "Extra Light";
            case 300: return "Light";
            case 400: return "Normal";
            case 500: return "Medium";
            case 600: return "Semi Bold";
            case 700: return "Bold";
            case 800: return "Extra Bold";
            case 900: return "Black";
            default: return "Weight " + weight;
        }
    }
    
    public String getSlantName() {
        switch (slant) {
            case 0: return "Upright";
            case 1: return "Italic";
            default: return "Slant " + slant;
        }
    }
    
    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}
