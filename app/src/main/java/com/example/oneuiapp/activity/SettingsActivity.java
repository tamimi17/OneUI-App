package com.example.oneuiapp.activity;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fragment.SettingsFragment;
import com.example.oneuiapp.utils.LanguageHelper;

import dev.oneuiproject.oneui.layout.ToolbarLayout;

/**
 * SettingsActivity - شاشة الإعدادات المستقلة
 * 
 * تعرض SettingsFragment في شاشة منفصلة مع شريط أدوات علوي
 * يمكن الوصول إليها من أيقونة الإعدادات في درج التنقل في MainActivity
 *
 * ★ آلية تغيير اللغة بدون تدمير الـ Activity: ★
 * بما أن locale|layoutDirection مُضاف إلى configChanges في المانيفيست،
 * عند تغيير اللغة يُستدعى onConfigurationChanged بدلاً من إعادة بناء الـ Activity.
 * نعتمد على LanguageHelper لتطبيق الاتجاه الصحيح بشكل مركزي ومنظم.
 *
 * ★ لماذا locale|layoutDirection هنا فقط؟ ★
 * لأن SettingsActivity هي الشاشة الوحيدة التي يرى فيها المستخدم تغيير اللغة
 * بشكل مباشر. منع تدميرها يُلغي وميض الصور والرسومات أثناء التغيير.
 * باقي الأنشطة (MainActivity, HomeActivity) لا تملك هذا الإعداد فيعيد
 * النظام بناءها تلقائياً في الخلفية، وعند الرجوع إليها تكون محدّثة بالكامل.
 */
public class SettingsActivity extends BaseActivity {

    private ToolbarLayout mToolbarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initToolbar();
        
        // إضافة SettingsFragment فقط عند الإنشاء الأول
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
        }
    }

    private void initToolbar() {
        mToolbarLayout = findViewById(R.id.toolbar_layout);
        if (mToolbarLayout != null) {
            // ★ ضبط اتجاه ToolbarLayout صريحاً عند كل بناء للـ Activity ★
            //
            // السبب: عند تغيير الثيم يُعاد بناء SettingsActivity عبر recreate().
            // في الدورة الجديدة يمر الـ Activity بـ onCreate من جديد، لكن
            // ToolbarLayout لا يحصل على اتجاه صريح فيرث اتجاهاً خاطئاً
            // من السياق الداخلي للمكتبة قبل أن يُطبَّق السياق الملفوف.
            // الحل: نضبط الاتجاه من Configuration الحالي فور تهيئة الـ Toolbar.
            int direction = LanguageHelper.resolveLayoutDirection(
                    getResources().getConfiguration());
            mToolbarLayout.setLayoutDirection(direction);

            // تعيين tooltip لزر الرجوع
            mToolbarLayout.setNavigationButtonTooltip(
                getString(R.string.navigate_up));
            
            // معالجة النقر على زر الرجوع
            mToolbarLayout.setNavigationButtonOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    /**
     * ★ تحديث الاتجاه وعناصر الـ Activity فوراً عند تغيير اللغة ★
     *
     * يُستدعى بدلاً من إعادة بناء الـ Activity لأن locale|layoutDirection
     * مُضاف إلى configChanges. النظام يُحدّث الموارد قبل هذا الاستدعاء.
     *
     * نفوّض تحديث الاتجاه إلى LanguageHelper ليبقى المنطق مركزياً.
     * SettingsFragment يتلقى نفس الاستدعاء تلقائياً عبر سلسلة Activity → Fragment.
     *
     * ★ سبب تحديث CollapsingToolbar يدوياً: ★
     * ToolbarLayout من مكتبة OneUI يحتفظ بالعنوان والعنوان الفرعي كقيم مستقلة
     * في الذاكرة ولا يُعيد تحميلهما تلقائياً عند تغيير اللغة. يجب سحب النصوص
     * الجديدة من freshContext وتمريرها صريحاً.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // ★ تفويض تحديث اتجاه DecorView إلى LanguageHelper ★
        // يحلّ مشكلة System Default والتأخر في تغيير الاتجاه بين أي لغتين
        LanguageHelper.forceUpdateLayoutDirection(this, newConfig);

        // ★ تحديث اتجاه ToolbarLayout صريحاً ★
        // السبب: ToolbarLayout كـ ViewGroup مستقل يحتفظ باتجاهه الداخلي
        // الذي يُعيَّن عند بناء الـ Activity (onCreate). عند تغيير الثيم يُعاد
        // البناء ويُضبط الاتجاه من جديد، لكن عند تغيير اللغة لاحقاً يُحدَّث
        // DecorView فقط ويبقى ToolbarLayout على اتجاهه القديم مما يسبب خللاً
        // في موضع زر الرجوع والعنوان. نحتاج لتحديثه صريحاً بنفس الاتجاه المحسوب.
        int resolvedDirection = LanguageHelper.resolveLayoutDirection(newConfig);
        if (mToolbarLayout != null) {
            mToolbarLayout.setLayoutDirection(resolvedDirection);
        }

        // ★ إنشاء freshContext لسحب النصوص المترجمة للغة الجديدة بشكل مضمون ★
        // ضروري لأن getString() يعود إلى السياق القديم الذي أنشأته attachBaseContext()
        Context freshContext = LanguageHelper.createFreshContext(this, newConfig);

        // ★ تحديث عنوان وعنوان فرعي CollapsingToolbar يدوياً ★
        // ToolbarLayout لا يُحدّثهما تلقائياً عند تغيير اللغة بدون تدمير الـ Activity
        if (mToolbarLayout != null) {
            mToolbarLayout.setTitle(freshContext.getString(R.string.title_settings));
            // إذا كان لديك subtitle، أضفه هنا:
            mToolbarLayout.setExpandedSubtitle(freshContext.getString(R.string.app_name));
            mToolbarLayout.setNavigationButtonTooltip(
                    freshContext.getString(R.string.navigate_up));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // معالجة زر الرجوع في شريط الأدوات
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // السماح بالرجوع بالطريقة العادية
        super.onBackPressed();
    }
}
