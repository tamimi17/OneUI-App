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
 * ★ الإصلاح الجوهري (وميض النجمة): استبدال الإحالة المباشرة في الـ overload ذي 9 معاملات
 *   بـ bindCore() الخاص الذي يُنفِّذ جميع خطوات الربط ما عدا setFavoriteIndicator().
 *
 *   المشكلة السابقة: كان الـ overload ذو 9 معاملات يُحيل إلى bind() الكاملة بـ isFavorite=false،
 *   مما يستدعي setFavoriteIndicator(false) → النجمة مخفية → ثم يستدعي الـ Adapter
 *   setFavoriteIndicator(true) بعدها. إذا كان ItemAnimator نشطاً لحظة النداء
 *   (قبل إبطاله في onHiddenChanged أو بعد إعادة تفعيله)، كان يلتقط الحالة الوسيطة
 *   (نجمة مخفية) ويُشغّل cross-fade → ظهور الوميض.
 *
 *   الحل: bindCore() لا تلمس setFavoriteIndicator إطلاقاً — يتولاها الـ Adapter حصراً.
 *   بذلك: لا حالة وسيطة → لا وميض.
 *
 * ★ إضافة updateLastOpenedHighlight(boolean) لتحديث لون اسم الخط بصمت
 *   عبر PAYLOAD_UPDATE_LAST_OPENED دون المساس بأيقونة النجمة. ★
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
    // ★ الإصلاح الجوهري: bindCore() — قلب منطق الربط بدون setFavoriteIndicator ★
    //
    // هذه الدالة الخاصة هي المرجع الوحيد لجميع خطوات الربط المشتركة.
    // لا تستدعي setFavoriteIndicator() إطلاقاً، مما يضمن:
    //   1. أن الـ overload ذا 9 معاملات لا يمر بحالة وسيطة (نجمة مخفية).
    //   2. أن الـ Adapter يتولى تعيين حالة النجمة بعد الاستدعاء مباشرةً
    //      عبر setFavoriteIndicator(isFavorited) بقيمة صحيحة من FavoriteStatusProvider.
    //
    // @param displayName      اسم العرض (بدون صيغة الملف)
    // @param path             المسار الكامل للملف
    // @param isSearchActive   هل البحث نشط
    // @param searchQuery      نص البحث الحالي
    // @param isLastOpened     هل آخر خط تم فتحه
    // @param highlighter      أداة التمييز للبحث
    // @param isSelectionMode  هل وضع التحديد مفعّل
    // @param isSelected       هل العنصر محدد حالياً
    // @param weightWidthLabel وصف الوزن والعرض
    // ════════════════════════════════════════════════════════════
    private void bindCore(String displayName,
                          String path,
                          boolean isSearchActive,
                          String searchQuery,
                          boolean isLastOpened,
                          FontTextHighlighter highlighter,
                          boolean isSelectionMode,
                          boolean isSelected,
                          String weightWidthLabel) {

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
        bindCore(displayName, path, isSearchActive, searchQuery, isLastOpened,
                 highlighter, false, false, weightWidthLabel);
        // ★ setFavoriteIndicator يُستدعى دائماً بعد bindCore لضمان الصحة ★
        setFavoriteIndicator(isFavorite);
    }

    // ════════════════════════════════════════════════════════════
    // ★ الإصلاح: overload بـ 9 معاملات (بدون isFavorite) — مُصلَح لمنع وميض النجمة ★
    //
    // يُستدعى من LocalFontListAdapter.bindLocalFontViewHolder() التي:
    //   1. تستدعي هذه الدالة بـ 9 معاملات (مع isSelectionMode/isSelected، بدون isFavorite)
    //   2. ثم تستدعي setFavoriteIndicator() بشكل منفصل لتعيين حالة المفضلة
    //
    // ★ الإصلاح: يستدعي bindCore() مباشرةً بدلاً من bind() الكاملة بـ isFavorite=false.
    //   bindCore() لا تلمس setFavoriteIndicator إطلاقاً، فيبقى favoriteIconView على
    //   حالته الحالية حتى يُعيَّن بقيمة صحيحة من الـ Adapter فور عودة هذه الدالة.
    //   بذلك تُزال الحالة الوسيطة (نجمة مخفية) التي كانت تُسبب وميض الأيقونة. ★
    // ════════════════════════════════════════════════════════════

    /**
     * ★ دالة bind بـ 9 معاملات (بدون isFavorite) — مُصلَحة ★
     *
     * تُستدعى من LocalFontListAdapter.bindLocalFontViewHolder() حيث يُعيَّن
     * ظهور أيقونة المفضلة بصمت عبر setFavoriteIndicator() بعد الاستدعاء.
     *
     * ★ التغيير عن النسخة السابقة: لا تُحيل بعد الآن إلى bind() الكاملة بـ isFavorite=false،
     *   بل تستدعي bindCore() مباشرةً التي لا تلمس favoriteIconView إطلاقاً. ★
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
        // ★ الإصلاح: bindCore() لا تستدعي setFavoriteIndicator إطلاقاً ★
        // الـ Adapter سيستدعي setFavoriteIndicator(isFavorited) بعد هذه الدالة
        // بقيمة صحيحة من FavoriteStatusProvider — بدون أي حالة وسيطة — بدون وميض
        bindCore(displayName, path, isSearchActive, searchQuery, isLastOpened,
                 highlighter, isSelectionMode, isSelected, weightWidthLabel);
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
        bindCore(displayName, path, isSearchActive, searchQuery, isLastOpened,
                 highlighter, isSelectionMode, isSelected, weightWidthLabel);
        // ★ setFavoriteIndicator يُستدعى دائماً بعد bindCore لضمان الصحة ★
        setFavoriteIndicator(isFavorite);
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
    // ★ تحديث تمييز آخر خط مفتوح بصمت (PAYLOAD_UPDATE_LAST_OPENED) ★
    // ════════════════════════════════════════════════════════════

    /**
     * ★ تحديث لون نص اسم الخط فقط دون المساس بأي عنصر آخر ★
     *
     * تُستدعى من LocalFontListAdapter عند استقبال PAYLOAD_UPDATE_LAST_OPENED،
     * أي عند عودة المستخدم من شاشة عارض الخطوط إلى القائمة أو عند حفظ آخر خط مفتوح.
     *
     * ★ هذه الدالة لا تلمس favoriteIconView إطلاقاً، مما يمنع أي وميض للنجمة ★
     *
     * @param isLastOpened true إذا كان هذا الخط هو آخر خط فتحه المستخدم
     */
    public void updateLastOpenedHighlight(boolean isLastOpened) {
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
        // ★ لا setFavoriteIndicator — لا وميض ★
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
