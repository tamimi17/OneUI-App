package com.example.oneuiapp.fontlist.viewholder;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fontlist.search.FontTextHighlighter;
import com.example.oneuiapp.metadata.FontWeightWidthExtractor;
import com.google.android.material.color.MaterialColors;

/**
 * LocalFontViewHolder - ViewHolder محدث بدعم التحديد المتعدد وعرض الوزن/العرض وأيقونة المفضلة
 *
 * ★ التعديل الأول: إضافة weightWidthTextView لعرض وصف الوزن والعرض
 *   في سطر ثانٍ بالنص الثانوي تحت اسم الخط مباشرةً.
 *   أمثلة: "Bold, Condensed" | "VF · Regular" | "غير معروف"
 *
 * ★ التعديل الثاني: إضافة favoriteIconView لعرض أيقونة النجمة الصفراء (ic_favorite)
 *   بجانب العنصر عندما يكون مفضلاً، في كلٍّ من قائمة الخطوط المحلية وقائمة المفضلة.
 */
public class LocalFontViewHolder extends RecyclerView.ViewHolder {
    
    public final TextView fontNameTextView;
    // ★ المرجع الجديد لعرض وصف الوزن والعرض ★
    public final TextView weightWidthTextView;
    public final CheckBox checkBox;
    public final View dividerView; // ★ مرجع الخط الفاصل ★
    // ★ أيقونة المفضلة الصفراء (ic_favorite)، تظهر فقط للعناصر المفضلة ★
    public final ImageView favoriteIconView;
    private String currentPath;

    public LocalFontViewHolder(@NonNull View itemView) {
        super(itemView);
        fontNameTextView    = itemView.findViewById(R.id.font_item_name);
        weightWidthTextView = itemView.findViewById(R.id.font_item_weight_width); // ★ جديد ★
        checkBox            = itemView.findViewById(R.id.checkbox);
        dividerView         = itemView.findViewById(R.id.item_divider);           // ★ ربط الخط الفاصل ★
        favoriteIconView    = itemView.findViewById(R.id.font_item_favorite_icon); // ★ جديد: أيقونة المفضلة ★
    }

    /**
     * ربط البيانات مع العنصر (بدون وضع التحديد).
     * يُستدعى عند الحاجة لعرض بسيط بدون اختيار متعدد.
     */
    public void bind(String displayName,
                     String path,
                     boolean isSearchActive,
                     String searchQuery,
                     boolean isLastOpened,
                     FontTextHighlighter highlighter,
                     String weightWidthLabel,
                     boolean isFavorite) {
        bind(displayName, path, isSearchActive, searchQuery, isLastOpened,
             highlighter, false, false, weightWidthLabel, isFavorite);
    }

    /**
     * ربط البيانات مع العنصر (مع دعم وضع التحديد وأيقونة المفضلة).
     *
     * @param displayName      اسم العرض (بدون صيغة الملف)
     * @param path             المسار الكامل للملف
     * @param isSearchActive   هل البحث نشط
     * @param searchQuery      نص البحث الحالي
     * @param isLastOpened     هل آخر خط تم فتحه
     * @param highlighter      أداة التمييز للبحث
     * @param isSelectionMode  هل وضع التحديد مفعّل
     * @param isSelected       هل العنصر محدد حالياً
     * @param weightWidthLabel وصف الوزن والعرض ("Bold, Condensed" أو "غير معروف" إلخ)
     * @param isFavorite       هل الخط مضاف إلى المفضلة (يتحكم في ظهور النجمة الصفراء)
     */
    public void bind(String displayName,
                     String path,
                     boolean isSearchActive,
                     String searchQuery,
                     boolean isLastOpened,
                     FontTextHighlighter highlighter,
                     boolean isSelectionMode,
                     boolean isSelected,
                     String weightWidthLabel,
                     boolean isFavorite) {
        
        this.currentPath = path;
        itemView.setTag(path);

        // ★ إظهار/إخفاء CheckBox حسب وضع التحديد ★
        if (isSelectionMode) {
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setChecked(isSelected);
        } else {
            checkBox.setVisibility(View.GONE);
            checkBox.setChecked(false);
        }

        // ★ إظهار/إخفاء أيقونة النجمة الصفراء حسب حالة المفضلة ★
        // تظهر النجمة في قائمة الخطوط المحلية وقائمة المفضلة على حدٍّ سواء
        if (favoriteIconView != null) {
            favoriteIconView.setVisibility(isFavorite ? View.VISIBLE : View.GONE);
        }

        // ★ تغيير لون النص لتمييز آخر خط تم فتحه ★
        // يستخدم colorPrimary الديناميكي للتكيف مع لوحة الألوان الحالية للنظام،
        // بدلاً من اللون الأزرق الثابت
        Context context = itemView.getContext();

        if (isLastOpened) {
            int primaryColor = MaterialColors.getColor(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                context.getColor(android.R.color.holo_blue_light) // لون احتياطي
            );
            fontNameTextView.setTextColor(primaryColor);
        } else {
            fontNameTextView.setTextColor(
                ContextCompat.getColor(context, dev.oneuiproject.oneui.design.R.color.oui_primary_text_color)
            );
        }

        // إعداد نص اسم الخط مع دعم تمييز نص البحث
        if (isSearchActive && searchQuery != null && !searchQuery.isEmpty()) {
            Spannable highlightedText = highlighter.highlightText(displayName, searchQuery);
            fontNameTextView.setText(highlightedText);
        } else {
            fontNameTextView.setText(displayName);
        }

        // ★ عرض وصف الوزن والعرض في السطر الثاني ★
        // اللون الثانوي مُضبوط في XML، لكن نتحقق من القيمة هنا
        if (weightWidthTextView != null) {
            String label = (weightWidthLabel != null && !weightWidthLabel.isEmpty())
                    ? weightWidthLabel
                    : FontWeightWidthExtractor.UNKNOWN;
            weightWidthTextView.setText(label);
        }
    }

    /**
     * ★ تعيين Typeface للمعاينة مع معالجة null بشكل صحيح ★
     * يُطبَّق على اسم الخط فقط، دون المساس بسطر الوزن/العرض
     */
    public void setTypeface(Typeface typeface) {
        fontNameTextView.setTypeface(typeface != null ? typeface : Typeface.DEFAULT);
    }

    /**
     * ★ تعيين Typeface الافتراضي مع معالجة null بشكل صحيح ★
     */
    public void setDefaultTypeface(Typeface typeface) {
        fontNameTextView.setTypeface(typeface != null ? typeface : Typeface.DEFAULT);
    }

    /**
     * الحصول على Tag (المسار)
     */
    public String getTag() {
        return currentPath;
    }
}
