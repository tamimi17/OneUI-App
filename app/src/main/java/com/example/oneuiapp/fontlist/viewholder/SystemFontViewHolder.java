package com.example.oneuiapp.fontlist.viewholder;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fontlist.search.FontTextHighlighter;
import com.example.oneuiapp.metadata.FontWeightWidthExtractor;
import com.google.android.material.color.MaterialColors;

/**
 * SystemFontViewHolder - حامل عرض لعناصر خطوط النظام
 * يدير عرض عنصر واحد من قائمة خطوط النظام
 *
 * ★ التعديل: إضافة weightWidthTextView لعرض وصف الوزن والعرض
 *   في سطر ثانٍ بالنص الثانوي تحت اسم الخط مباشرةً.
 *
 * ★ الإصلاح (ومضة اللون): إضافة bindLastOpened() لتحديث لون اسم الخط
 *   بصمت عبر PAYLOAD_UPDATE_LAST_OPENED دون إعادة رسم العنصر كاملاً.
 *   بدون هذه الدالة، كان الـ Adapter يستدعي bind() الكامل عند العودة من
 *   عارض الخطوط، مما يُسبب ومضة مرئية في لون اسم الخط المفتوح. ★
 */
public class SystemFontViewHolder extends RecyclerView.ViewHolder {

    private final TextView nameView;
    // ★ المرجع الجديد لعرض وصف الوزن والعرض ★
    private final TextView weightWidthView;
    public final View dividerView; // ★ مرجع الخط الفاصل ★

    public SystemFontViewHolder(@NonNull View itemView) {
        super(itemView);
        nameView        = itemView.findViewById(R.id.font_item_name);
        weightWidthView = itemView.findViewById(R.id.font_item_weight_width); // ★ جديد ★
        dividerView     = itemView.findViewById(R.id.item_divider); // ★ ربط الخط الفاصل ★
    }

    /**
     * ربط البيانات الكاملة بالعرض.
     * يُستدعى عند الإنشاء الأول لكل عنصر أو عند تغيير البيانات الجوهرية.
     *
     * @param displayName      اسم الخط للعرض (بدون امتداد الملف)
     * @param path             المسار الكامل للملف
     * @param isSearchActive   هل البحث نشط
     * @param searchQuery      نص البحث الحالي
     * @param isLastOpened     هل آخر خط تم فتحه
     * @param highlighter      أداة تمييز نص البحث
     * @param weightWidthLabel وصف الوزن والعرض ("Bold, Condensed" أو "غير معروف" إلخ)
     */
    public void bind(String displayName, String path, boolean isSearchActive,
                     String searchQuery, boolean isLastOpened, FontTextHighlighter highlighter,
                     String weightWidthLabel) {

        // عرض النص مع إبراز البحث إذا لزم الأمر
        if (isSearchActive && searchQuery != null && !searchQuery.isEmpty()) {
            SpannableString highlighted = highlighter.highlightText(displayName, searchQuery);
            nameView.setText(highlighted);
        } else {
            nameView.setText(displayName);
        }

        // ★ تفويض تحديث اللون لـ bindLastOpened() لضمان مسار تحديث واحد لا ومضة فيه ★
        bindLastOpened(isLastOpened);

        // ★ عرض وصف الوزن والعرض في السطر الثاني ★
        // اللون الثانوي مُضبوط في XML عبر textColorSecondary
        if (weightWidthView != null) {
            String label = (weightWidthLabel != null && !weightWidthLabel.isEmpty())
                    ? weightWidthLabel
                    : FontWeightWidthExtractor.UNKNOWN;
            weightWidthView.setText(label);
        }

        // حفظ المسار كـ tag للاستخدام لاحقاً في loadFontPreview
        nameView.setTag(path);
    }

    /**
     * ★ الإصلاح (ومضة اللون): تحديث لون اسم الخط فقط دون إعادة رسم العنصر كاملاً. ★
     *
     * يُستدعى بطريقتين:
     *   1. من bind() أثناء الربط الكامل للعنصر.
     *   2. من onBindViewHolder() في الـ Adapter عبر PAYLOAD_UPDATE_LAST_OPENED
     *      لتحديث العنصرين المتأثرين فقط (القديم والجديد) عند العودة من عارض الخطوط.
     *
     * بدون هذا الفصل، كان bind() الكامل يُعيد رسم العنصر بأكمله مما يُسبب
     * ومضة مرئية في لون الاسم عند العودة من شاشة عارض الخطوط.
     *
     * @param isLastOpened هل هذا العنصر هو آخر خط تم فتحه
     */
    public void bindLastOpened(boolean isLastOpened) {
        if (nameView == null) return;

        // يستخدم colorPrimary الديناميكي للتكيف مع لوحة الألوان الحالية للنظام،
        // بدلاً من اللون الأزرق الثابت
        if (isLastOpened) {
            int primaryColor = MaterialColors.getColor(
                nameView.getContext(),
                androidx.appcompat.R.attr.colorPrimary,
                nameView.getContext().getColor(android.R.color.holo_blue_light) // لون احتياطي
            );
            nameView.setTextColor(primaryColor);
        } else {
            nameView.setTextColor(
                ContextCompat.getColor(nameView.getContext(), R.color.primary_text_color)
            );
        }
    }

    /**
     * تعيين نوع الخط للمعاينة.
     * يُطبَّق على اسم الخط فقط، دون المساس بسطر الوزن/العرض.
     */
    public void setTypeface(Typeface typeface) {
        if (nameView != null && typeface != null) {
            nameView.setTypeface(typeface);
        }
    }

    /**
     * تعيين نوع الخط الافتراضي
     */
    public void setDefaultTypeface(Typeface defaultTypeface) {
        if (nameView != null) {
            nameView.setTypeface(defaultTypeface != null ? defaultTypeface : Typeface.DEFAULT);
        }
    }

    /**
     * الحصول على الـ tag المحفوظ (المسار)
     */
    public Object getTag() {
        return nameView != null ? nameView.getTag() : null;
    }

    /**
     * تعيين مستمع النقر على العنصر
     */
    public void setOnClickListener(View.OnClickListener listener) {
        if (itemView != null) {
            itemView.setOnClickListener(listener);
        }
    }
}
