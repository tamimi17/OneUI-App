package com.example.oneuiapp.fontlist.search;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.ColorInt;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;

/**
 * FontTextHighlighter - فئة لإدارة تلوين وإبراز نصوص البحث
 * تستخدم لتمييز النص المطابق لاستعلام البحث في قائمة الخطوط
 */
public class FontTextHighlighter {
    
    private final Context context;
    private final int highlightColor;
    
    /**
     * Constructor مع لون افتراضي
     * يستخدم colorPrimary من Theme النظام تلقائياً، مما يجعل اللون
     * متكيفاً مع لوحة الألوان الديناميكية (Dynamic Color) للنظام.
     * @param context السياق المطلوب للحصول على الألوان من الموارد
     */
    public FontTextHighlighter(Context context) {
        this.context = context;
        // استخدام MaterialColors للحصول على colorPrimary من الـ Theme الحالي
        // بدلاً من لون ثابت، مما يضمن التكيف مع لوحة الألوان الديناميكية للنظام
        this.highlightColor = MaterialColors.getColor(
            context,
            androidx.appcompat.R.attr.colorPrimary,
            context.getColor(android.R.color.holo_blue_light) // لون احتياطي في حال تعذّر الحصول على colorPrimary
        );
    }
    
    /**
     * Constructor مع لون مخصص
     * @param context السياق المطلوب
     * @param highlightColor لون الإبراز المخصص
     */
    public FontTextHighlighter(Context context, @ColorInt int highlightColor) {
        this.context = context;
        this.highlightColor = highlightColor;
    }
    
    /**
     * إنشاء نص قابل للتنسيق مع إبراز الكلمة المطابقة
     * 
     * @param text النص الأصلي الذي سيتم البحث فيه
     * @param searchQuery كلمة البحث المراد إبرازها
     * @return SpannableString مع تطبيق التلوين، أو null إذا كان النص فارغاً
     */
    public SpannableString highlightText(String text, String searchQuery) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new SpannableString(text);
        }
        
        SpannableString spannableString = new SpannableString(text);
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = searchQuery.toLowerCase(Locale.getDefault());
        
        int startPos = lowerText.indexOf(lowerQuery);
        
        if (startPos >= 0) {
            spannableString.setSpan(
                new ForegroundColorSpan(highlightColor),
                startPos,
                startPos + lowerQuery.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        
        return spannableString;
    }
    
    /**
     * إبراز كل التطابقات الموجودة في النص (وليس فقط الأول)
     * 
     * @param text النص الأصلي
     * @param searchQuery كلمة البحث
     * @return SpannableString مع إبراز جميع التطابقات
     */
    public SpannableString highlightAllOccurrences(String text, String searchQuery) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new SpannableString(text);
        }
        
        SpannableString spannableString = new SpannableString(text);
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = searchQuery.toLowerCase(Locale.getDefault());
        
        int startPos = 0;
        while (startPos >= 0 && startPos < lowerText.length()) {
            startPos = lowerText.indexOf(lowerQuery, startPos);
            
            if (startPos >= 0) {
                spannableString.setSpan(
                    new ForegroundColorSpan(highlightColor),
                    startPos,
                    startPos + lowerQuery.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                startPos += lowerQuery.length();
            }
        }
        
        return spannableString;
    }
    
    /**
     * فحص ما إذا كان النص يحتوي على كلمة البحث
     * 
     * @param text النص المراد فحصه
     * @param searchQuery كلمة البحث
     * @return true إذا كان النص يحتوي على كلمة البحث (غير حساس لحالة الأحرف)
     */
    public static boolean containsQuery(String text, String searchQuery) {
        if (text == null || searchQuery == null) {
            return false;
        }
        
        if (searchQuery.isEmpty()) {
            return true;
        }
        
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = searchQuery.toLowerCase(Locale.getDefault());
        
        return lowerText.contains(lowerQuery);
    }
    
    /**
     * الحصول على لون الإبراز المستخدم حالياً
     * @return لون الإبراز
     */
    @ColorInt
    public int getHighlightColor() {
        return highlightColor;
    }
}
