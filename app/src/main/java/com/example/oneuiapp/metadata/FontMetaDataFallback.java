package com.example.oneuiapp.metadata;

import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FontMetaDataFallback - Enhanced version
 * ★ تحسينات: استخراج أفضل للأسماء الحقيقية من الخطوط ★
 */
public class FontMetaDataFallback {
    
    private static final String TAG = "FontMetaDataFallback";
    
    public static Map<String, String> extractMetaDataWithTtcIndex(File fontFile, int ttcIndex) {
        Map<String, String> out = new HashMap<>();
        
        try {
            String copyright = getFontNameDirectWithTtc(fontFile, 0, ttcIndex);
            String fullName = getFontNameDirectWithTtc(fontFile, 4, ttcIndex);
            String family = getFontNameDirectWithTtc(fontFile, 1, ttcIndex);
            String subfamily = getFontNameDirectWithTtc(fontFile, 2, ttcIndex);
            String postScriptName = getFontNameDirectWithTtc(fontFile, 6, ttcIndex);
            String version = getFontNameDirectWithTtc(fontFile, 5, ttcIndex);
            String manufacturer = getFontNameDirectWithTtc(fontFile, 8, ttcIndex);
            String designer = getFontNameDirectWithTtc(fontFile, 9, ttcIndex);
            String trademark = getFontNameDirectWithTtc(fontFile, 7, ttcIndex);
            String description = getFontNameDirectWithTtc(fontFile, 10, ttcIndex);
            String designerURL = getFontNameDirectWithTtc(fontFile, 12, ttcIndex);
            String vendorURL = getFontNameDirectWithTtc(fontFile, 11, ttcIndex);
            String licenseDescription = getFontNameDirectWithTtc(fontFile, 13, ttcIndex);
            String licenseURL = getFontNameDirectWithTtc(fontFile, 14, ttcIndex);
            
            if (copyright != null && !copyright.isEmpty()) out.put("Copyright", copyright);
            if (fullName != null && !fullName.isEmpty()) out.put("FullName", fullName);
            if (family != null && !family.isEmpty()) out.put("Family", family);
            if (subfamily != null && !subfamily.isEmpty()) out.put("SubFamily", subfamily);
            if (postScriptName != null && !postScriptName.isEmpty()) out.put("PostScriptName", postScriptName);
            if (version != null && !version.isEmpty()) out.put("Version", version);
            if (manufacturer != null && !manufacturer.isEmpty()) out.put("Manufacturer", manufacturer);
            if (designer != null && !designer.isEmpty()) out.put("Designer", designer);
            if (trademark != null && !trademark.isEmpty()) out.put("Trademark", trademark);
            if (description != null && !description.isEmpty()) out.put("Description", description);
            if (designerURL != null && !designerURL.isEmpty()) out.put("DesignerURL", designerURL);
            if (vendorURL != null && !vendorURL.isEmpty()) out.put("VendorURL", vendorURL);
            if (licenseDescription != null && !licenseDescription.isEmpty()) out.put("LicenseDescription", licenseDescription);
            if (licenseURL != null && !licenseURL.isEmpty()) out.put("LicenseURL", licenseURL);
            
            Map<String, String> technicalData = extractTechnicalDataDirectWithTtc(fontFile, ttcIndex);
            if (technicalData != null) {
                out.putAll(technicalData);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Fallback extraction failed: " + e.getMessage());
        }
        
        return out.isEmpty() ? null : out;
    }
    
    public static Map<String, String> extractMetaData(File fontFile) {
        return extractMetaDataWithTtcIndex(fontFile, 0);
    }
    
    /**
     * ★★★ تحسين: محاولة استخراج الاسم من حقول متعددة بترتيب أفضل ★★★
     */
    public static String extractFontName(File fontFile, int ttcIndex) {
        // المحاولة 1: Full Name (nameId 4) - الاسم الكامل
        String directName = getFontNameDirectWithTtc(fontFile, 4, ttcIndex);
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 4 (Full Name): " + directName);
            return directName;
        }
        
        // المحاولة 2: Family Name (nameId 1) - اسم العائلة
        directName = getFontNameDirectWithTtc(fontFile, 1, ttcIndex);
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 1 (Family): " + directName);
            return directName;
        }
        
        // المحاولة 3: PostScript Name (nameId 6)
        directName = getFontNameDirectWithTtc(fontFile, 6, ttcIndex);
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 6 (PostScript): " + directName);
            return directName;
        }
        
        // المحاولة 4: Unique Font Identifier (nameId 3) - معرف فريد
        directName = getFontNameDirectWithTtc(fontFile, 3, ttcIndex);
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 3 (Unique ID): " + directName);
            return directName;
        }
        
        // المحاولة 5: Subfamily Name (nameId 2) - قد يكون مفيداً
        directName = getFontNameDirectWithTtc(fontFile, 2, ttcIndex);
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 2 (SubFamily): " + directName);
            return directName;
        }
        
        // ★ تحسين: إذا فشلت جميع المحاولات، نرجع "Unknown Font" ★
        // هذا يضمن أن الخطوط التالفة لن تعرض اسم الملف
        Log.w(TAG, "Failed to extract font name from: " + fontFile.getName());
        return "Unknown Font";
    }
    
    public static String extractFontName(File fontFile) {
        return extractFontName(fontFile, 0);
    }
    
    private static String getFontNameDirectWithTtc(File fontFile, int nameId, int ttcIndex) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(fontFile, "r");
            
            byte[] headerBytes = new byte[4];
            raf.read(headerBytes);
            String headerTag = new String(headerBytes, "US-ASCII");
            
            long fontOffset = 0;
            
            if ("ttcf".equals(headerTag)) {
                raf.skipBytes(4);
                long numFonts = readUInt32(raf);
                
                if (ttcIndex >= numFonts) {
                    Log.w(TAG, "TTC index " + ttcIndex + " exceeds number of fonts: " + numFonts);
                    return null;
                }
                
                raf.seek(12 + (ttcIndex * 4));
                fontOffset = readUInt32(raf);
            }
            
            raf.seek(fontOffset + 4);
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6);
            
            long nameTableOffset = -1;
            for (int i = 0; i < numTables; i++) {
                byte[] tag = new byte[4];
                raf.read(tag);
                String tagStr = new String(tag, "ISO-8859-1");
                
                raf.skipBytes(4);
                long offset = raf.readInt() & 0xFFFFFFFFL;
                raf.skipBytes(4);
                
                if ("name".equals(tagStr)) {
                    nameTableOffset = offset;
                    break;
                }
            }
            
            if (nameTableOffset == -1) {
                return null;
            }
            
            raf.seek(nameTableOffset);
            int format = raf.readUnsignedShort();
            int count = raf.readUnsignedShort();
            int stringOffset = raf.readUnsignedShort();
            
            String bestName = null;
            int bestPriority = Integer.MAX_VALUE;
            
            for (int i = 0; i < count; i++) {
                int platformID = raf.readUnsignedShort();
                int encodingID = raf.readUnsignedShort();
                int languageID = raf.readUnsignedShort();
                int nameID = raf.readUnsignedShort();
                int length = raf.readUnsignedShort();
                int offset = raf.readUnsignedShort();
                
                if (nameID == nameId && length > 0) {
                    int priority = getPlatformPriority(platformID, encodingID, languageID);
                    
                    if (priority < bestPriority) {
                        long currentPos = raf.getFilePointer();
                        raf.seek(nameTableOffset + stringOffset + offset);
                        
                        byte[] nameBytes = new byte[length];
                        raf.readFully(nameBytes);
                        
                        String name = decodeNameBytes(nameBytes, platformID, encodingID);
                        if (name != null && !name.trim().isEmpty() && isValidFontName(name)) {
                            bestName = name.trim();
                            bestPriority = priority;
                        }
                        
                        raf.seek(currentPos);
                        
                        if (bestPriority == 0) {
                            break;
                        }
                    }
                }
            }
            
            return bestName;
            
        } catch (Exception e) {
            Log.w(TAG, "getFontNameDirectWithTtc failed: " + e.getMessage());
            return null;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception e) {
                }
            }
        }
    }
    
    private static String getFontNameDirect(File fontFile, int nameId) {
        return getFontNameDirectWithTtc(fontFile, nameId, 0);
    }
    
    private static Map<String, String> extractTechnicalDataDirectWithTtc(File fontFile, int ttcIndex) {
        Map<String, String> data = new HashMap<>();
        RandomAccessFile raf = null;
        
        try {
            raf = new RandomAccessFile(fontFile, "r");
            
            byte[] headerBytes = new byte[4];
            raf.read(headerBytes);
            String headerTag = new String(headerBytes, "US-ASCII");
            
            long fontOffset = 0;
            
            if ("ttcf".equals(headerTag)) {
                raf.skipBytes(4);
                long numFonts = readUInt32(raf);
                
                if (ttcIndex >= numFonts) {
                    return data;
                }
                
                raf.seek(12 + (ttcIndex * 4));
                fontOffset = readUInt32(raf);
            }
            
            raf.seek(fontOffset + 4);
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6);
            
            long os2TableOffset = -1;
            long headTableOffset = -1;
            long maxpTableOffset = -1;
            long cmapTableOffset = -1;
            long fvarTableOffset = -1;
            
            for (int i = 0; i < numTables; i++) {
                byte[] tag = new byte[4];
                raf.read(tag);
                String tagStr = new String(tag, "ISO-8859-1");
                
                raf.skipBytes(4);
                long offset = raf.readInt() & 0xFFFFFFFFL;
                long length = raf.readInt() & 0xFFFFFFFFL;
                
                if ("OS/2".equals(tagStr)) {
                    os2TableOffset = offset;
                } else if ("head".equals(tagStr)) {
                    headTableOffset = offset;
                } else if ("maxp".equals(tagStr)) {
                    maxpTableOffset = offset;
                } else if ("cmap".equals(tagStr)) {
                    cmapTableOffset = offset;
                } else if ("fvar".equals(tagStr)) {
                    fvarTableOffset = offset;
                }
            }
            
            if (fvarTableOffset != -1) {
                data.put("FontType", "Variable Font");
            } else {
                data.put("FontType", "Static Font");
            }
            
            if (os2TableOffset != -1) {
                try {
                    raf.seek(os2TableOffset);
                    int version = raf.readUnsignedShort();
                    raf.skipBytes(2);
                    int weightClass = raf.readUnsignedShort();
                    int widthClass = raf.readUnsignedShort();
                    
                    String weight = getWeightClassName(weightClass);
                    String width = getWidthClassName(widthClass);
                    
                    if (weight != null) data.put("Weight", weight);
                    if (width != null) data.put("Width", width);
                    
                    raf.skipBytes(54);
                    byte[] vendorID = new byte[4];
                    raf.read(vendorID);
                    String vendorId = new String(vendorID, "ISO-8859-1").trim();
                    if (!vendorId.isEmpty()) {
                        data.put("VendorID", vendorId);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read OS/2 table: " + e.getMessage());
                }
            }
            
            if (headTableOffset != -1) {
                try {
                    raf.seek(headTableOffset);
                    raf.skipBytes(4);
                    raf.skipBytes(4);
                    raf.skipBytes(4);
                    raf.skipBytes(4);
                    raf.skipBytes(2);
                    int unitsPerEm = raf.readUnsignedShort();
                    data.put("UnitsPerEm", String.valueOf(unitsPerEm));
                    
                    long created = raf.readLong();
                    long modified = raf.readLong();
                    
                    try {
                        long createdMs = (created - 2082844800L) * 1000L;
                        Date createdDate = new Date(createdMs);
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        data.put("CreatedDate", sdf.format(createdDate));
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse created date: " + e.getMessage());
                    }
                    
                    try {
                        long modifiedMs = (modified - 2082844800L) * 1000L;
                        Date modifiedDate = new Date(modifiedMs);
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        data.put("ModifiedDate", sdf.format(modifiedDate));
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse modified date: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read head table: " + e.getMessage());
                }
            }
            
            if (maxpTableOffset != -1) {
                try {
                    raf.seek(maxpTableOffset);
                    raf.skipBytes(4);
                    int numGlyphs = raf.readUnsignedShort();
                    data.put("GlyphCount", String.valueOf(numGlyphs));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read maxp table: " + e.getMessage());
                }
            }
            
            if (cmapTableOffset != -1) {
                try {
                    Set<String> scripts = detectSupportedScriptsDirect(raf, cmapTableOffset);
                    if (!scripts.isEmpty()) {
                        StringBuilder scriptsStr = new StringBuilder();
                        for (String script : scripts) {
                            if (scriptsStr.length() > 0) scriptsStr.append(", ");
                            scriptsStr.append(script);
                        }
                        data.put("SupportedScripts", scriptsStr.toString());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to detect scripts: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "extractTechnicalDataDirectWithTtc failed: " + e.getMessage());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception e) {
                }
            }
        }
        
        return data;
    }
    
    private static Map<String, String> extractTechnicalDataDirect(File fontFile) {
        return extractTechnicalDataDirectWithTtc(fontFile, 0);
    }
    
    private static long readUInt32(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[4];
        raf.read(bytes);
        return ((long)(bytes[0] & 0xFF) << 24) | 
               ((long)(bytes[1] & 0xFF) << 16) | 
               ((long)(bytes[2] & 0xFF) << 8) | 
               (long)(bytes[3] & 0xFF);
    }
    
    private static Set<String> detectSupportedScriptsDirect(RandomAccessFile raf, long cmapOffset) {
        Set<String> scripts = new HashSet<>();
        
        try {
            raf.seek(cmapOffset);
            int version = raf.readUnsignedShort();
            int numTables = raf.readUnsignedShort();
            
            long bestSubtableOffset = -1;
            int bestPriority = Integer.MAX_VALUE;
            
            for (int i = 0; i < numTables; i++) {
                int platformID = raf.readUnsignedShort();
                int encodingID = raf.readUnsignedShort();
                long offset = raf.readInt() & 0xFFFFFFFFL;
                
                int priority = 10;
                if (platformID == 3 && encodingID == 10) {
                    priority = 0;
                } else if (platformID == 3 && encodingID == 1) {
                    priority = 1;
                } else if (platformID == 0 && encodingID == 3) {
                    priority = 2;
                }
                
                if (priority < bestPriority) {
                    bestSubtableOffset = cmapOffset + offset;
                    bestPriority = priority;
                }
            }
            
            if (bestSubtableOffset != -1) {
                raf.seek(bestSubtableOffset);
                int format = raf.readUnsignedShort();
                
                if (format == 4) {
                    scripts = detectScriptsFormat4(raf);
                } else if (format == 12) {
                    scripts = detectScriptsFormat12(raf);
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "detectSupportedScriptsDirect failed: " + e.getMessage());
        }
        
        return scripts;
    }
    
    private static Set<String> detectScriptsFormat4(RandomAccessFile raf) {
        Set<String> scripts = new HashSet<>();
        
        try {
            raf.skipBytes(2);
            raf.skipBytes(2);
            int segCountX2 = raf.readUnsignedShort();
            int segCount = segCountX2 / 2;
            
            raf.skipBytes(6);
            
            int[] endCodes = new int[segCount];
            for (int i = 0; i < segCount; i++) {
                endCodes[i] = raf.readUnsignedShort();
            }
            
            raf.skipBytes(2);
            
            int[] startCodes = new int[segCount];
            for (int i = 0; i < segCount; i++) {
                startCodes[i] = raf.readUnsignedShort();
            }
            
            for (int i = 0; i < segCount; i++) {
                int start = startCodes[i];
                int end = endCodes[i];
                
                if (start <= 0x007A && end >= 0x0041) scripts.add("Latin");
                if (start <= 0x06FF && end >= 0x0600) scripts.add("Arabic");
                if (start <= 0x04FF && end >= 0x0400) scripts.add("Cyrillic");
                if (start <= 0x03FF && end >= 0x0370) scripts.add("Greek");
                if (start <= 0x05FF && end >= 0x0590) scripts.add("Hebrew");
                if (start <= 0x9FFF && end >= 0x4E00) scripts.add("CJK");
                if (start <= 0x097F && end >= 0x0900) scripts.add("Devanagari");
                if (start <= 0x0E7F && end >= 0x0E00) scripts.add("Thai");
                if (start <= 0xD7AF && end >= 0xAC00) scripts.add("Hangul");
                if (start <= 0x309F && end >= 0x3040) scripts.add("Hiragana");
                if (start <= 0x30FF && end >= 0x30A0) scripts.add("Katakana");
            }
            
        } catch (Exception e) {
            Log.w(TAG, "detectScriptsFormat4 failed: " + e.getMessage());
        }
        
        return scripts;
    }
    
    private static Set<String> detectScriptsFormat12(RandomAccessFile raf) {
        Set<String> scripts = new HashSet<>();
        
        try {
            raf.skipBytes(2);
            raf.skipBytes(4);
            raf.skipBytes(4);
            long numGroups = raf.readInt() & 0xFFFFFFFFL;
            
            for (long i = 0; i < numGroups && i < 1000; i++) {
                long startCode = raf.readInt() & 0xFFFFFFFFL;
                long endCode = raf.readInt() & 0xFFFFFFFFL;
                raf.skipBytes(4);
                
                if (startCode <= 0x007A && endCode >= 0x0041) scripts.add("Latin");
                if (startCode <= 0x06FF && endCode >= 0x0600) scripts.add("Arabic");
                if (startCode <= 0x04FF && endCode >= 0x0400) scripts.add("Cyrillic");
                if (startCode <= 0x03FF && endCode >= 0x0370) scripts.add("Greek");
                if (startCode <= 0x05FF && endCode >= 0x0590) scripts.add("Hebrew");
                if (startCode <= 0x9FFF && endCode >= 0x4E00) scripts.add("CJK");
                if (startCode <= 0x097F && endCode >= 0x0900) scripts.add("Devanagari");
                if (startCode <= 0x0E7F && endCode >= 0x0E00) scripts.add("Thai");
                if (startCode <= 0xD7AF && endCode >= 0xAC00) scripts.add("Hangul");
                if (startCode <= 0x309F && endCode >= 0x3040) scripts.add("Hiragana");
                if (startCode <= 0x30FF && endCode >= 0x30A0) scripts.add("Katakana");
            }
            
        } catch (Exception e) {
            Log.w(TAG, "detectScriptsFormat12 failed: " + e.getMessage());
        }
        
        return scripts;
    }
    
    private static String decodeNameBytes(byte[] bytes, int platformID, int encodingID) {
        try {
            String charset;
            if (platformID == 3) {
                charset = "UTF-16BE";
            } else if (platformID == 1) {
                if (encodingID == 0) {
                    charset = "MacRoman";
                } else {
                    charset = "UTF-16BE";
                }
            } else if (platformID == 0) {
                charset = "UTF-16BE";
            } else {
                charset = "UTF-8";
            }
            
            return new String(bytes, charset);
        } catch (Exception e) {
            try {
                return new String(bytes, "ISO-8859-1");
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    private static int getPlatformPriority(int platformId, int encodingId, int languageId) {
        if (platformId == 3 && encodingId == 1 && languageId == 0x0409) {
            return 0;
        }
        if (platformId == 3 && encodingId == 1) {
            return 1;
        }
        if (platformId == 0 && encodingId == 3) {
            return 2;
        }
        if (platformId == 1 && encodingId == 0) {
            return 3;
        }
        if (platformId == 3) {
            return 4;
        }
        if (platformId == 0) {
            return 5;
        }
        if (platformId == 1) {
            return 6;
        }
        return 10;
    }
    
    /**
     * ★ تحسين: تخفيف شروط التحقق من صحة الاسم ★
     */
    private static boolean isValidFontName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        // إزالة المسافات الزائدة
        name = name.trim();
        
        // رفض الأسماء القصيرة جداً
        if (name.length() < 2) {
            return false;
        }
        
        // رفض الأسماء التي تحتوي على أحرف تحكم فقط
        int controlChars = 0;
        int validChars = 0;
        int totalChars = name.length();
        
        for (int i = 0; i < totalChars; i++) {
            char c = name.charAt(i);
            
            // أحرف التحكم
            if (c < 32 || (c >= 127 && c < 160)) {
                controlChars++;
                continue;
            }
            
            // أحرف صالحة
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || 
                (c >= '0' && c <= '9') || c == ' ' || c == '-' || c == '_' || 
                c == '.' || c == '\'' || c == ',' || c >= 128) {
                validChars++;
            }
        }
        
        // رفض إذا كانت كل الأحرف تحكم
        if (controlChars == totalChars) {
            return false;
        }
        
        // ★ تخفيف الشرط: نقبل إذا كان 30% على الأقل من الأحرف صالحة ★
        // (بدلاً من 50% في الكود القديم)
        return validChars > 0 && (validChars * 100 / totalChars) >= 30;
    }
    
    private static String getWeightClassName(int weightClass) {
        switch (weightClass) {
            case 100: return "Thin";
            case 200: return "Extra Light";
            case 300: return "Light";
            case 400: return "Normal";
            case 500: return "Medium";
            case 600: return "Semi Bold";
            case 700: return "Bold";
            case 800: return "Extra Bold";
            case 900: return "Black";
            default: return "Weight " + weightClass;
        }
    }

    private static String getWidthClassName(int widthClass) {
        switch (widthClass) {
            case 1: return "Ultra Condensed";
            case 2: return "Extra Condensed";
            case 3: return "Condensed";
            case 4: return "Semi Condensed";
            case 5: return "Normal";
            case 6: return "Semi Expanded";
            case 7: return "Expanded";
            case 8: return "Extra Expanded";
            case 9: return "Ultra Expanded";
            default: return "Width " + widthClass;
        }
    }
                    }
