package com.example.oneuiapp.metadata;

import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * FontWeightWidthExtractor — استخراج وصف الوزن والعرض من ملفات الخطوط
 *
 * يقرأ جدول OS/2 مباشرةً من الملف دون تحميله بالكامل في الذاكرة،
 * مع دعم كامل لملفات TTC وخطوط Variable Font.
 *
 * أمثلة على القيم المُعادة:
 *   "Bold, Condensed"            ← خط ثابت بوزن وعرض غير Normal
 *   "Bold"                       ← العرض Normal يُحذف للإيجاز
 *   "VF · Regular"               ← خط متغير بعرض Normal
 *   "VF · Bold, Semi Condensed"  ← خط متغير بعرض غير Normal
 *   "غير معروف"                  ← الخط تالف أو لا يحتوي على OS/2 table
 *
 * ملاحظة: يعتمد هذا الملف على نفس منطق قراءة TTC المستخدم في FontMetaDataFallback
 * لضمان الاتساق في التعامل مع الملفات المختلطة.
 */
public class FontWeightWidthExtractor {

    private static final String TAG = "FontWeightWidthExtractor";

    /** نص يُعرض عند تعذّر استخراج المعلومات أو تلف الخط */
    public static final String UNKNOWN = "غير معروف";

    /** بادئة تُضاف لخطوط Variable Font قبل وصف الوزن والعرض */
    private static final String VF_PREFIX = "VF";

    // ════════════════════════════════════════════════════════════
    // الواجهة العامة
    // ════════════════════════════════════════════════════════════

    /**
     * استخراج وصف الوزن والعرض من ملف خط مع دعم TTC Index.
     *
     * @param fontFile ملف الخط (ttf / otf / ttc)
     * @param ttcIndex رقم الخط داخل TTC (0 للملفات العادية)
     * @return وصف نصي جاهز للعرض في واجهة المستخدم
     */
    public static String extract(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists() || !fontFile.canRead()) {
            return UNKNOWN;
        }
        try {
            int[] ww = readWeightWidth(fontFile, ttcIndex);
            if (ww == null) return UNKNOWN;

            boolean isVF  = isVariableFont(fontFile, ttcIndex);
            String weight = getWeightName(ww[0]);
            String width  = getWidthName(ww[1]);
            String label  = buildLabel(weight, width);

            return isVF ? VF_PREFIX + " · " + label : label;

        } catch (Exception e) {
            Log.w(TAG, "extract() failed for " + fontFile.getName() + ": " + e.getMessage());
            return UNKNOWN;
        }
    }

    /** اختصار لملفات TTF/OTF العادية (ttcIndex = 0) */
    public static String extract(File fontFile) {
        return extract(fontFile, 0);
    }

    // ════════════════════════════════════════════════════════════
    // قراءة جدول OS/2
    // ════════════════════════════════════════════════════════════

    /**
     * يقرأ usWeightClass و usWidthClass من جدول OS/2.
     *
     * هيكل بداية جدول OS/2:
     *   offset 0 : version       (USHORT) — 2 bytes
     *   offset 2 : xAvgCharWidth (SHORT)  — 2 bytes
     *   offset 4 : usWeightClass (USHORT) — 2 bytes ← المطلوب
     *   offset 6 : usWidthClass  (USHORT) — 2 bytes ← المطلوب
     *
     * @return int[]{weightClass, widthClass} أو null عند الفشل
     */
    private static int[] readWeightWidth(File fontFile, int ttcIndex) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(fontFile, "r");
            long fontOffset = resolveFontOffset(raf, ttcIndex);
            if (fontOffset < 0) return null;

            // قراءة جدول الجداول (Table Directory)
            raf.seek(fontOffset + 4); // تخطي sfVersion
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6); // searchRange, entrySelector, rangeShift

            long os2Offset = -1;
            for (int i = 0; i < numTables; i++) {
                byte[] tag = new byte[4];
                raf.read(tag);
                String t = new String(tag, "ISO-8859-1");
                raf.skipBytes(4); // checkSum
                long off = raf.readInt() & 0xFFFFFFFFL;
                raf.skipBytes(4); // length

                if ("OS/2".equals(t)) {
                    os2Offset = off;
                    break;
                }
            }

            if (os2Offset < 0) return null;

            // قراءة الحقلَين المطلوبَين من جدول OS/2
            raf.seek(os2Offset + 2); // تخطي version (2 bytes)
            raf.skipBytes(2);        // تخطي xAvgCharWidth (2 bytes)
            int weightClass = raf.readUnsignedShort();
            int widthClass  = raf.readUnsignedShort();

            return new int[]{weightClass, widthClass};

        } catch (Exception e) {
            Log.w(TAG, "readWeightWidth failed: " + e.getMessage());
            return null;
        } finally {
            if (raf != null) try { raf.close(); } catch (Exception ignored) {}
        }
    }

    // ════════════════════════════════════════════════════════════
    // كشف خطوط Variable Font عبر جدول fvar
    // ════════════════════════════════════════════════════════════

    /**
     * يكتشف وجود جدول fvar الذي يدل على أن الخط متغير (Variable Font).
     * وجود هذا الجدول يعني أن الخط يدعم محاور الوزن والعرض الديناميكية.
     */
    private static boolean isVariableFont(File fontFile, int ttcIndex) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(fontFile, "r");
            long fontOffset = resolveFontOffset(raf, ttcIndex);
            if (fontOffset < 0) return false;

            raf.seek(fontOffset + 4);
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6);

            for (int i = 0; i < numTables; i++) {
                byte[] tag = new byte[4];
                raf.read(tag);
                String t = new String(tag, "ISO-8859-1");
                raf.skipBytes(12); // checkSum + offset + length

                if ("fvar".equals(t)) return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "isVariableFont check failed: " + e.getMessage());
        } finally {
            if (raf != null) try { raf.close(); } catch (Exception ignored) {}
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════
    // دوال مساعدة
    // ════════════════════════════════════════════════════════════

    /**
     * يحسب إزاحة بداية الخط داخل الملف.
     * — للملفات العادية (TTF/OTF) يُعيد 0.
     * — لملفات TTC يقرأ جدول الإزاحات ويُعيد الإزاحة المناسبة لـ ttcIndex.
     *
     * @return إزاحة البداية بالبايت، أو -1 إذا كان ttcIndex خارج النطاق
     */
    private static long resolveFontOffset(RandomAccessFile raf, int ttcIndex) throws Exception {
        raf.seek(0);
        byte[] hdr = new byte[4];
        raf.read(hdr);
        String tag = new String(hdr, "US-ASCII");

        if ("ttcf".equals(tag)) {
            raf.skipBytes(4); // version
            long numFonts = readUInt32(raf);
            if (ttcIndex < 0 || ttcIndex >= numFonts) return -1;
            raf.seek(12 + (long) ttcIndex * 4);
            return readUInt32(raf);
        }
        return 0L;
    }

    private static long readUInt32(RandomAccessFile raf) throws Exception {
        byte[] b = new byte[4];
        raf.read(b);
        return ((long)(b[0] & 0xFF) << 24) | ((long)(b[1] & 0xFF) << 16)
             | ((long)(b[2] & 0xFF) <<  8) |  (long)(b[3] & 0xFF);
    }

    /**
     * يُركّب التسمية النهائية من الوزن والعرض.
     * — إذا لم يُعاد وصف للعرض (Normal) يُعرض الوزن وحده.
     * — إذا كلاهما null يُعاد UNKNOWN.
     * — إذا أُعيدا معاً يُفصل بينهما بفاصلة ومسافة.
     */
    private static String buildLabel(String weight, String width) {
        boolean hasWeight = weight != null && !weight.isEmpty();
        boolean hasWidth  = width  != null && !width.isEmpty();
        if (!hasWeight && !hasWidth) return UNKNOWN;
        if (!hasWeight) return width;
        if (!hasWidth)  return weight;
        return weight + " • " + width;
    }

    // ════════════════════════════════════════════════════════════
    // جداول أسماء الوزن والعرض
    // ════════════════════════════════════════════════════════════

    /**
     * يُحوّل قيمة usWeightClass إلى اسم نصي وصفي.
     *
     * يستخدم نطاقات بدلاً من قيم ثابتة لمرونة أكبر مع الخطوط الحقيقية
     * التي كثيراً ما تستخدم قيماً مثل 380 أو 420 بدلاً من 400 بالضبط.
     *
     * @param w قيمة usWeightClass من جدول OS/2
     * @return اسم الوزن النصي، أو null إذا كانت القيمة غير صالحة
     */
    public static String getWeightName(int w) {
        if (w <= 0)   return null;
        if (w <= 150) return "Thin";
        if (w <= 250) return "Extra Light";
        if (w <= 350) return "Light";
        if (w <= 450) return "Regular";
        if (w <= 550) return "Medium";
        if (w <= 650) return "Semi Bold";
        if (w <= 750) return "Bold";
        if (w <= 850) return "Extra Bold";
        return "Black";
    }

    /**
     * يُحوّل قيمة usWidthClass إلى اسم نصي وصفي.
     *
     * widthClass = 5 (Normal) يُعاد null عمداً لتجنب الازدواجية في الوصف،
     * إذ إن معظم الخطوط عرضها Normal وإظهاره في كل عنصر مكرر وغير مفيد.
     * يظهر العرض فقط إذا كان غير اعتيادي (Condensed, Expanded, إلخ).
     *
     * @param d قيمة usWidthClass من جدول OS/2
     * @return اسم العرض النصي، أو null للعرض الاعتيادي (Normal)
     */
    public static String getWidthName(int d) {
        switch (d) {
            case 1: return "Ultra Condensed";
            case 2: return "Extra Condensed";
            case 3: return "Condensed";
            case 4: return "Semi Condensed";
            case 5: return null; // Normal — يُحذف للإيجاز
            case 6: return "Semi Expanded";
            case 7: return "Expanded";
            case 8: return "Extra Expanded";
            case 9: return "Ultra Expanded";
            default: return null;
        }
    }
                }
