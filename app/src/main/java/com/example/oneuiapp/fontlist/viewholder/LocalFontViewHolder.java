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
 *
 * ★ التعديل الثالث: إضافة دالة setFavoriteIndicator(boolean) لتحديث أيقونة المفضلة
 *   بشكل مستقل عبر PAYLOAD_UPDATE_FAVORITE دون إعادة رسم العنصر كاملاً.
 *   تُستدعى من LocalFontListAdapter عند:
 *   - notifyFavoriteChanged(String path)   → تحديث عنصر واحد
 *   - notifyAllFavoritesChanged()          → تحديث جميع العناصر دفعةً
 *
 * ★ الإصلاح: إضافة overload بـ 9 معاملات (بدون isFavorite) ★
 *   يُستدعى من LocalFontListAdapter.bindLocalFontViewHolder() التي تُعيِّن
 *   أيقونة المفضلة بصمت عبر setFavoriteIndicator() بعد استدعاء bind().
 *   بدون هذا الـ overload يحدث خطأ تجميعي لأن الـ Adapter يمرر 9 معاملات
 *   ولا يوجد في الـ ViewHolder ما يطابقها (8 أو 10 فقط).
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

    // ════════════════════════════════════════════════════════════
    // دوال الربط (bind)
    // ════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════
    // ★ الإصلاح: overload بـ 9 معاملات (بدون isFavorite) ★
    // يُستدعى من LocalFontListAdapter.bindLocalFontViewHolder() التي:
    //   1. تستدعي هذه الدالة بـ 9 معاملات (مع isSelectionMode/isSelected، بدون isFavorite)
    //   2. ثم تستدعي setFavoriteIndicator() بشكل منفصل لتعيين حالة المفضلة
    //
    // بدون هذا الـ overload: خطأ تجميعي لأن Java لا يجد ما يطابق 9 معاملات.
    // ════════════════════════════════════════════════════════════

    /**
     * ★ دالة bind بـ 9 معاملات (بدون isFavorite) ★
     *
     * تُستدعى من LocalFontListAdapter.bindLocalFontViewHolder() حيث يُعيَّن
     * ظهور أيقونة المفضلة بصمت عبر setFavoriteIndicator() بعد الاستدعاء.
     * تُحيل داخلياً إلى الدالة الكاملة بـ isFavorite = false ريثما يُعيَّن برمجياً.
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
     */
    public void bind(String displayName,
                     String path,
                     boolean isSearchActive,
                     String searchQuery,
                     boolean isLastOpened,
                     FontTextHighlighter highlighter,
                     boolean isSelectionMode,
                     boolean isSelected,
                     String weightWidthLabel) {
        // ★ تُحيل للدالة الكاملة بـ isFavorite = false ★
        // الـ Adapter سيستدعي setFavoriteIndicator() بعد هذه الدالة لتعيين القيمة الصحيحة
        bind(displayName, path, isSearchActive, searchQuery, isLastOpened,
             highlighter, isSelectionMode, isSelected, weightWidthLabel, false);
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
        setFavoriteIndicator(isFavorite);

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

    // ════════════════════════════════════════════════════════════
    // ★ تحديث أيقونة المفضلة بشكل مستقل ★
    // ════════════════════════════════════════════════════════════

    /**
     * ★ تحديث ظهور أيقونة المفضلة (ic_favorite الصفراء) دون إعادة رسم العنصر كاملاً ★
     *
     * تُستدعى من LocalFontListAdapter في حالتين:
     *   1. ضمن onBindViewHolder() العادي بعد استدعاء bind() (نتيجة الـ overload بـ 9 معاملات)
     *   2. ضمن onBindViewHolder(payloads) عند استقبال PAYLOAD_UPDATE_FAVORITE
     *      لتحديث الأيقونة بكفاءة دون الحاجة لإعادة ربط كامل بيانات العنصر
     *
     * @param isFavorite true لإظهار النجمة الصفراء، false لإخفائها
     */
    public void setFavoriteIndicator(boolean isFavorite) {
        if (favoriteIconView != null) {
            favoriteIconView.setVisibility(isFavorite ? View.VISIBLE : View.GONE);
        }
    }

    // ════════════════════════════════════════════════════════════
    // دوال Typeface والمساعدة
    // ════════════════════════════════════════════════════════════

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
