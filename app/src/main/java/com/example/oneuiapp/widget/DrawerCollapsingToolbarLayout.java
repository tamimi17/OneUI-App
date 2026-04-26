package com.example.oneuiapp.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import com.google.android.material.R;
import com.google.android.material.appbar.CollapsingToolbarLayout;

/**
 * نسخة مخصصة من CollapsingToolbarLayout تُطبَّق حصراً على درج التنقل.
 *
 * ★ سبب الوجود ★
 * دالة updateTitleLayout() في المكتبة الأصلية تُعيّن حجم الخط برمجياً
 * في كل استدعاء لـ setTitle() أو seslSetSubtitle()، مما يتجاوز أي
 * تعديل خارجي. تحلّ هذه الكلاس المشكلة بإعادة تطبيق الأحجام المخصصة
 * بعد كل استدعاء مباشرةً دون تأخير لتجنب الوميض.
 *
 * ★ نطاق التأثير ★
 * يُستخدم هذا الكلاس فقط في oui_layout_drawer_appbar.xml الخاص بدرج
 * التنقل. جميع الشاشات الأخرى تبقى تستخدم CollapsingToolbarLayout
 * الأصلي دون أي تأثير. وضعا البحث والتحديد محمِيّان أيضاً لأن
 * applyCustomTextSizes() تتجاهل حالة غياب العنوان الفرعي.
 *
 * ★ وحدة حجم الخط ★
 * COMPLEX_UNIT_SP  → تحترم إعداد حجم الخط في النظام (موصى به)
 * COMPLEX_UNIT_DIP → ثابتة دائماً بغض النظر عن إعدادات المستخدم
 */
public class DrawerCollapsingToolbarLayout extends CollapsingToolbarLayout {

    // ★ أحجام الخط المخصصة لدرج التنقل — عدِّلهما حسب الحاجة ★
    private static final float DRAWER_TITLE_TEXT_SIZE    = 34f;
    private static final float DRAWER_SUBTITLE_TEXT_SIZE = 15f;

    // ★ وحدة الحجم: غيّرها إلى COMPLEX_UNIT_DIP إذا أردت dp بدلاً من sp ★
    private static final int TEXT_SIZE_UNIT = TypedValue.COMPLEX_UNIT_DIP;

    public DrawerCollapsingToolbarLayout(Context context) {
        super(context);
    }

    public DrawerCollapsingToolbarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawerCollapsingToolbarLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * ★ إعادة تطبيق الأحجام المخصصة عند تغيير اتجاه الشاشة ★
     * onConfigurationChanged() في المكتبة الأصلية تستدعي updateTitleLayout()
     * مباشرةً، مما يُعيّن الأحجام الافتراضية دون المرور بـ setTitle()
     * أو seslSetSubtitle(). تجاوز هذه الدالة يضمن استمرار الأحجام
     * المخصصة بعد كل تغيير في اتجاه الشاشة.
     */
    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyCustomTextSizes();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        applyCustomTextSizes();
    }

    @Override
    public void seslSetSubtitle(CharSequence subtitle) {
        super.seslSetSubtitle(subtitle);
        applyCustomTextSizes();
    }

    /**
     * يطبّق أحجام الخط المخصصة على العنوانين الموسّعين.
     *
     * ★ شرط التطبيق ★
     * تُطبَّق الأحجام المخصصة فقط عند وجود العنوان الفرعي (subtitleView != null).
     * هذا يحمي وضعَي البحث والتحديد لأن كليهما يُلغي العنوان الفرعي
     * عبر seslSetSubtitle(null)، مما يجعل subtitleView غير موجود
     * فتُهمَل الدالة تلقائياً وتبقى الأحجام الافتراضية للمكتبة.
     *
     * ★ التطبيق الفوري ★
     * لا يُستخدم post() هنا لأنه يُسبّب وميضاً بين الإطارين.
     * يتم البحث عن الـ Views مباشرةً لأنها تكون متاحة بعد super.
     * post() محفوظ كاحتياط للحالة الأولى فقط عند إنشاء الـ View.
     */
    private void applyCustomTextSizes() {
        // ★ لا تطبّق عند غياب العنوان الفرعي (وضع البحث أو التحديد) ★
        TextView subtitleView = findViewById(R.id.collapsing_appbar_extended_subtitle);
        if (subtitleView == null) return;

        TextView titleView = findViewById(R.id.collapsing_appbar_extended_title);

        if (titleView != null) {
            // ★ تطبيق فوري لتجنب الوميض ★
            titleView.setTextSize(TEXT_SIZE_UNIT, DRAWER_TITLE_TEXT_SIZE);
        } else {
            // ★ احتياط: post() فقط عند غياب العنوان الرئيسي (الحالة الأولى) ★
            post(() -> {
                TextView tv = findViewById(R.id.collapsing_appbar_extended_title);
                if (tv != null) tv.setTextSize(TEXT_SIZE_UNIT, DRAWER_TITLE_TEXT_SIZE);
            });
        }

        subtitleView.setTextSize(TEXT_SIZE_UNIT, DRAWER_SUBTITLE_TEXT_SIZE);
    }
}
