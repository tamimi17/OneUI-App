package com.example.oneuiapp.fontlist;

/**
 * FontFileInfo - نموذج بيانات مشترك يمثّل ملف خط واحد
 *
 * تم فصل هذا الكلاس عن FontDirectoryManager (المُعاد تسميته LocalFontDirectoryManager)
 * ليكون نموذجاً مستقلاً قابلاً للاستخدام في كلٍّ من:
 *   - قائمة الخطوط المحلية  (LocalFontListAdapter, LocalFontRepository …)
 *   - قائمة خطوط النظام     (SystemFontListAdapter, SystemFontListFragment …)
 *   - محرك البحث             (FontSearchManager)
 *   - مدير الفرز             (FontSortManager)
 *
 * الفائدة: أي ملف يحتاج هذا النموذج لا يضطر إلى استيراد منطق
 * قراءة المجلدات (LocalFontDirectoryManager) دون حاجة حقيقية إليه.
 */
public class FontFileInfo {

    private final String name;
    private final String path;
    private final long size;
    private final long lastModified;

    public FontFileInfo(String name, String path, long size, long lastModified) {
        this.name         = name;
        this.path         = path;
        this.size         = size;
        this.lastModified = lastModified;
    }

    public String getName()        { return name; }
    public String getPath()        { return path; }
    public long   getSize()        { return size; }
    public long   getLastModified(){ return lastModified; }
}
