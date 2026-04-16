package com.example.oneuiapp.custom;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DropDownPreference;
import androidx.preference.PreferenceViewHolder;

import java.lang.reflect.Field;

import com.example.oneuiapp.utils.SettingsHelper;

/**
 * CustomDropDownPreference - DropDownPreference مع دعم كامل للخطوط المخصصة
 * 
 * المشكلة:
 * DropDownPreference يستخدم Spinner لعرض القائمة المنسدلة.
 * عند فتح القائمة المنسدلة، يتم إنشاء PopupWindow يحتوي على قائمة الخيارات.
 * هذا الـ PopupWindow يستخدم ArrayAdapter افتراضي لا يطبق الخط المخصص.
 * 
 * الحل:
 * نتجاوز onBindViewHolder للوصول إلى الـ Spinner الداخلي.
 * نستبدل الـ Adapter الافتراضي بـ Adapter مخصص يطبق الخط على جميع العناصر.
 */
public class CustomDropDownPreference extends DropDownPreference {

    private static final String TAG = "CustomDropDownPref";

    public CustomDropDownPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CustomDropDownPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomDropDownPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomDropDownPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        // البحث عن الـ Spinner داخل View
        Spinner spinner = findSpinnerInView(holder.itemView);
        
        if (spinner != null) {
            setupCustomAdapter(spinner);
        }
    }

    /**
     * البحث عن Spinner داخل View hierarchy
     */
    private Spinner findSpinnerInView(View view) {
        if (view instanceof Spinner) {
            return (Spinner) view;
        }
        
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Spinner found = findSpinnerInView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }

    /**
     * إعداد Adapter مخصص للـ Spinner مع دعم الخط المخصص
     */
    private void setupCustomAdapter(Spinner spinner) {
        CharSequence[] entries = getEntries();
        if (entries == null || entries.length == 0) {
            return;
        }
        
        // الحصول على الخط المخصص
        Typeface customTypeface = SettingsHelper.getTypeface(getContext());
        
        // إنشاء Adapter مخصص
        CustomArrayAdapter adapter = new CustomArrayAdapter(
            getContext(),
            android.R.layout.simple_spinner_item,
            entries,
            customTypeface
        );
        
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        // تطبيق الـ Adapter على الـ Spinner
        spinner.setAdapter(adapter);
        
        // تعيين القيمة المحددة حالياً
        String value = getValue();
        int index = findIndexOfValue(value);
        if (index >= 0) {
            spinner.setSelection(index);
        }
        
        Log.d(TAG, "Custom adapter applied to spinner");
    }

    /**
     * ArrayAdapter مخصص يطبق الخط المخصص على جميع العناصر
     */
    private static class CustomArrayAdapter extends ArrayAdapter<CharSequence> {
        
        private final Typeface customTypeface;
        
        public CustomArrayAdapter(@NonNull Context context, int resource, 
                                @NonNull CharSequence[] objects, Typeface typeface) {
            super(context, resource, objects);
            this.customTypeface = typeface;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            applyFontToView(view);
            return view;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            applyFontToView(view);
            return view;
        }

        /**
         * تطبيق الخط على View
         */
        private void applyFontToView(View view) {
            if (view == null || customTypeface == null) {
                return;
            }
            
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                try {
                    int style = Typeface.NORMAL;
                    if (textView.getTypeface() != null) {
                        style = textView.getTypeface().getStyle();
                    }
                    textView.setTypeface(customTypeface, style);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to apply font to TextView", e);
                }
            }
            
            // إذا كان ViewGroup، طبق على الأبناء أيضاً
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyFontToView(group.getChildAt(i));
                }
            }
        }
    }
}
