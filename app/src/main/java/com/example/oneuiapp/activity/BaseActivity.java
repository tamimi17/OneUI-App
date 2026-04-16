package com.example.oneuiapp.activity;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.oneuiapp.utils.SettingsHelper;
import com.example.oneuiapp.utils.FontHelper;

/**
 * BaseActivity - الأساس لجميع الأنشطة في التطبيق
 * 
 * يضمن تطبيق الإعدادات (اللغة، الخط) على كل Activity
 * 
 * آلية العمل المحسّنة:
 * 1. في attachBaseContext: نطبق اللغة على السياق عبر ContextWrapper للأجهزة دون Android 13
 *    أما أجهزة Android 13 فما فوق فيتكفّل النظام بتطبيق اللغة والاتجاه تلقائياً عبر LocaleManager
 * 2. في onCreate: نثبت Factory مخصص لاعتراض إنشاء Views وتطبيق الخط
 * 3. في onPostCreate: نطبق الخط على العناصر الخاصة (Toolbar, AppBar)
 * 
 * ملاحظة مهمة: تم حذف كل أكواد فرض اتجاه التنسيق (RTL/LTR) يدوياً،
 * لأن النظام يتكفل بذلك تلقائياً بناءً على الـ Locale المضبوط في Context،
 * وفرضه يدوياً كان يتعارض مع خيارات المطورين ويسبب خللاً في الاتجاهات.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // تطبيق اللغة على السياق عبر ContextWrapper
        // للأجهزة دون Android 13: يضمن تحميل الموارد باللغة الصحيحة
        // للأجهزة Android 13+: تكفل النظام بالأمر عبر LocaleManager، وهذا مجرد تأكيد إضافي
        // الاتجاه (RTL/LTR) يُحدَّد تلقائياً من الـ Locale دون الحاجة لفرضه يدوياً
        Context wrappedContext = SettingsHelper.wrapContext(newBase);
        
        super.attachBaseContext(wrappedContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // تحميل معلومات الخط المخصص
        // يجب أن يحدث هذا قبل استدعاء super.onCreate()
        FontHelper.applyFont(this);
        
        // تثبيت Factory مخصص لاعتراض إنشاء Views
        // يجب أن يحدث هذا قبل استدعاء super.onCreate()
        // لأن AppCompat سيثبت factory خاص به في super.onCreate()
        // ونحن نريد أن نغلف factory الخاص به
        try {
            FontHelper.installFontFactory(this);
        } catch (Exception e) {
            android.util.Log.e("BaseActivity", "Failed to install font factory", e);
        }
        
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        
        // تطبيق الخط على العناصر الخاصة التي لا يعترضها Factory
        // مثل Toolbar و CollapsingToolbarLayout
        // نفعل هذا في onPostCreate لضمان أن جميع Views قد تم إنشاؤها
        FontHelper.applyFontToSpecialViews(this);
    }
}
