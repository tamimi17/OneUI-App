package com.example.oneuiapp.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.lang.reflect.Field;

/**
 * FontHelper - مساعد تطبيق الخطوط الشامل (محسّن - الإصدار النهائي)
 * 
 * يستخدم LayoutInflater.Factory2 لاعتراض إنشاء جميع Views
 * وتطبيق الخط المخصص على جميع عناصر TextView ومشتقاتها
 * 
 * الحل النهائي لمشكلة CollapsingToolbarLayout:
 * 1. نستخدم Typeface مع style صريح للعنوان المطوي
 * 2. نستخدم Reflection للوصول إلى الحقول الداخلية إذا لزم الأمر
 * 3. نضمن أن العنوان المطوي يظهر دائماً بخط عريض
 */
public class FontHelper {
    
    private static final String TAG = "FontHelper";
    private static Typeface sCustomTypeface = null;
    private static Typeface sCustomTypefaceBold = null;

    /**
     * تطبيق الخط المخصص على Activity
     * 
     * يجب استدعاء هذه الدالة في onCreate() لكل Activity
     * قبل استدعاء setContentView()
     * 
     * @param context سياق الـ Activity
     */
    public static void applyFont(Context context) {
        // تحديث الخط المخصص المحفوظ
        sCustomTypeface = SettingsHelper.getTypeface(context);
        
        if (sCustomTypeface != null) {
            // إنشاء نسخة عريضة من الخط
            // نستخدم style 700 (Bold) للتأكد من أن الخط عريض حقاً
            sCustomTypefaceBold = Typeface.create(sCustomTypeface, Typeface.BOLD);
            
            Log.d(TAG, "Custom font loaded successfully with bold variant");
        } else {
            sCustomTypefaceBold = null;
            Log.d(TAG, "Using system default font");
        }
    }

    /**
     * تثبيت Factory مخصص على LayoutInflater للـ Activity
     * 
     * يجب استدعاء هذه الدالة في onCreate() قبل setContentView()
     * 
     * @param context سياق الـ Activity
     */
    public static void installFontFactory(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            LayoutInflater layoutInflater = activity.getLayoutInflater();
            
            // تحقق إذا كان هناك factory موجود بالفعل (من AppCompat مثلاً)
            LayoutInflater.Factory2 existingFactory = layoutInflater.getFactory2();
            
            // قم بتثبيت factory مخصص يغلف الموجود
            layoutInflater.setFactory2(new CustomFontFactory(existingFactory, context));
            
            Log.d(TAG, "Font factory installed for activity");
        }
    }

    /**
     * تطبيق الخط على CollapsingToolbarLayout بشكل مباشر وفعّال
     * 
     * هذه الدالة تستخدم عدة طرق لضمان تطبيق الخط الصحيح:
     * 1. الطريقة المباشرة عبر setCollapsedTitleTypeface / setExpandedTitleTypeface
     * 2. إذا فشلت، نستخدم Reflection للوصول للحقول الداخلية
     * 
     * @param collapsingToolbar الـ CollapsingToolbarLayout المراد تطبيق الخط عليه
     */
    public static void applyFontToCollapsingToolbar(CollapsingToolbarLayout collapsingToolbar) {
        if (collapsingToolbar == null || sCustomTypeface == null) {
            return;
        }

        try {
            // الطريقة الأولى: استخدام الدوال المباشرة
            // للعنوان المطوي: نستخدم النسخة العريضة
            collapsingToolbar.setCollapsedTitleTypeface(sCustomTypefaceBold);
            
            // للعنوان الموسع: نستخدم النسخة العادية
            collapsingToolbar.setExpandedTitleTypeface(sCustomTypeface);
            
            // الطريقة الثانية: التأكد من تطبيق الخط باستخدام Reflection
            // هذا يضمن أن الخط يُطبق حتى لو كان هناك TextAppearance يتداخل
            try {
                // الوصول إلى حقل mCollapsedTextAppearance في CollapsingToolbarLayout
                Field collapsedField = CollapsingToolbarLayout.class.getDeclaredField("collapsingTextHelper");
                collapsedField.setAccessible(true);
                Object textHelper = collapsedField.get(collapsingToolbar);
                
                if (textHelper != null) {
                    // تطبيق الخط العريض على الحالة المطوية
                    Class<?> helperClass = textHelper.getClass();
                    try {
                        java.lang.reflect.Method setCollapsedMethod = helperClass.getDeclaredMethod(
                            "setCollapsedTypeface", Typeface.class);
                        setCollapsedMethod.setAccessible(true);
                        setCollapsedMethod.invoke(textHelper, sCustomTypefaceBold);
                    } catch (Exception e) {
                        Log.d(TAG, "setCollapsedTypeface method not found, trying alternative");
                    }
                    
                    // تطبيق الخط العادي على الحالة الموسعة
                    try {
                        java.lang.reflect.Method setExpandedMethod = helperClass.getDeclaredMethod(
                            "setExpandedTypeface", Typeface.class);
                        setExpandedMethod.setAccessible(true);
                        setExpandedMethod.invoke(textHelper, sCustomTypeface);
                    } catch (Exception e) {
                        Log.d(TAG, "setExpandedTypeface method not found, trying alternative");
                    }
                }
            } catch (Exception reflectionError) {
                // Reflection فشل، لكن الطريقة المباشرة قد تكون كافية
                Log.d(TAG, "Reflection approach failed, using direct methods only");
            }
            
            // إجبار CollapsingToolbarLayout على إعادة رسم العنوان
            collapsingToolbar.requestLayout();
            collapsingToolbar.invalidate();
            
            Log.d(TAG, "Font applied to CollapsingToolbarLayout successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply font to CollapsingToolbarLayout", e);
        }
    }

    /**
     * تطبيق الخط على العناصر الخاصة التي لا يعترضها Factory
     * 
     * هذه الدالة تبحث عن عناصر خاصة مثل:
     * - CollapsingToolbarLayout: يحصل على معاملة خاصة عبر applyFontToCollapsingToolbar
     * - Toolbar: لتطبيق الخط على العنوان
     * - أي TextViews أخرى قد تكون مخفية في ViewGroups
     * 
     * يجب استدعاؤها في onPostCreate() لضمان إنشاء جميع Views
     * 
     * @param activity النشاط الذي نريد تطبيق الخط على عناصره
     */
    public static void applyFontToSpecialViews(Activity activity) {
        if (sCustomTypeface == null) {
            return; // لا يوجد خط مخصص، استخدم الافتراضي
        }

        try {
            // الحصول على الجذر لكل Views في النشاط
            View rootView = activity.getWindow().getDecorView().getRootView();
            
            // تطبيق الخط بشكل تعاودي على جميع Views
            applyFontRecursively(rootView);
            
            Log.d(TAG, "Font applied to special views in " + activity.getClass().getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply font to special views", e);
        }
    }

    /**
     * تطبيق الخط بشكل تعاودي على View وجميع أبنائه
     * 
     * @param view الـ View الجذر
     */
    private static void applyFontRecursively(View view) {
        if (view == null || sCustomTypeface == null) {
            return;
        }

        // إذا كان View هو CollapsingToolbarLayout، نستخدم الدالة المتخصصة
        if (view instanceof CollapsingToolbarLayout) {
            applyFontToCollapsingToolbar((CollapsingToolbarLayout) view);
        }
        
        // إذا كان View هو Toolbar، نطبق الخط على عنوانه
        else if (view instanceof Toolbar) {
            Toolbar toolbar = (Toolbar) view;
            try {
                // البحث عن TextViews داخل Toolbar لتطبيق الخط عليها مباشرة
                applyFontToToolbarTextViews(toolbar);
                
                Log.d(TAG, "Font applied to Toolbar");
            } catch (Exception e) {
                Log.w(TAG, "Failed to apply font to Toolbar", e);
            }
        }
        
        // إذا كان View هو TextView، نطبق الخط عليه
        else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            try {
                // احتفظ بـ style الحالي للخط (عادي، عريض، مائل، إلخ)
                int style = Typeface.NORMAL;
                if (textView.getTypeface() != null) {
                    style = textView.getTypeface().getStyle();
                }
                
                // طبق الخط المخصص مع المحافظة على الـ style
                textView.setTypeface(sCustomTypeface, style);
            } catch (Exception e) {
                Log.w(TAG, "Failed to apply font to TextView", e);
            }
        }

        // إذا كان View هو ViewGroup، نطبق الخط على جميع أبنائه
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyFontRecursively(viewGroup.getChildAt(i));
            }
        }
    }

    /**
     * تطبيق الخط على TextViews داخل Toolbar
     * 
     * Toolbar يحتوي على TextViews داخلية للعنوان والعنوان الفرعي
     * نحتاج للوصول إليها مباشرة لتطبيق الخط
     * 
     * @param toolbar الـ Toolbar المراد تطبيق الخط على عناصره
     */
    private static void applyFontToToolbarTextViews(Toolbar toolbar) {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                
                // احتفظ بـ style الحالي
                int style = Typeface.NORMAL;
                if (textView.getTypeface() != null) {
                    style = textView.getTypeface().getStyle();
                }
                
                // طبق الخط
                textView.setTypeface(sCustomTypeface, style);
            }
        }
    }

    /**
     * Factory مخصص لاعتراض إنشاء Views وتطبيق الخط
     */
    private static class CustomFontFactory implements LayoutInflater.Factory2 {
        
        private final LayoutInflater.Factory2 mBaseFactory;
        private final Context mContext;
        
        public CustomFontFactory(LayoutInflater.Factory2 baseFactory, Context context) {
            mBaseFactory = baseFactory;
            mContext = context;
        }

        @Nullable
        @Override
        public View onCreateView(@Nullable View parent, @NonNull String name, 
                                @NonNull Context context, @NonNull AttributeSet attrs) {
            // أولاً، دع الـ factory الأساسي (AppCompat) ينشئ الـ View
            View view = null;
            
            if (mBaseFactory != null) {
                view = mBaseFactory.onCreateView(parent, name, context, attrs);
            }
            
            // إذا لم ينشئ AppCompat الـ View، نحاول إنشاءه بأنفسنا
            if (view == null) {
                view = createViewOrFailQuietly(name, context, attrs);
            }
            
            // إذا كان الـ View هو TextView أو أحد مشتقاته، طبق الخط المخصص
            if (view instanceof TextView && sCustomTypeface != null) {
                TextView textView = (TextView) view;
                
                // احتفظ بـ style الحالي للخط (عادي، عريض، مائل، إلخ)
                int style = Typeface.NORMAL;
                if (textView.getTypeface() != null) {
                    style = textView.getTypeface().getStyle();
                }
                
                // طبق الخط المخصص مع المحافظة على الـ style
                textView.setTypeface(sCustomTypeface, style);
            }
            
            return view;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull String name, @NonNull Context context, 
                                @NonNull AttributeSet attrs) {
            return onCreateView(null, name, context, attrs);
        }
        
        /**
         * محاولة إنشاء View من اسم الفئة
         */
        private View createViewOrFailQuietly(String name, Context context, AttributeSet attrs) {
            // إذا كان الاسم لا يحتوي على نقطة، فهو على الأرجح من حزمة android.widget
            if (name.indexOf('.') == -1) {
                name = "android.widget." + name;
            }
            
            try {
                LayoutInflater inflater = LayoutInflater.from(context);
                return inflater.createView(name, null, attrs);
            } catch (ClassNotFoundException e) {
                // هذا طبيعي لبعض العناصر المخصصة
                return null;
            }
        }
    }

    /**
     * الحصول على الخط المخصص المحفوظ حالياً (النسخة العادية)
     * 
     * @return الخط المخصص أو null للخط الافتراضي
     */
    public static Typeface getCustomTypeface() {
        return sCustomTypeface;
    }

    /**
     * الحصول على الخط المخصص المحفوظ حالياً (النسخة العريضة)
     * 
     * @return الخط المخصص العريض أو null للخط الافتراضي
     */
    public static Typeface getCustomTypefaceBold() {
        return sCustomTypefaceBold;
    }
    }
