package com.example.oneuiapp.fontlist.viewholder;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.oneuiapp.ui.widget.SortByItemLayout;
import com.example.oneuiapp.R;

/**
 * SortHeaderViewHolder - حامل عرض لرأس الفرز
 * يدير عرض عنصر رأس القائمة الذي يحتوي على خيارات الفرز
 */
public class SortHeaderViewHolder extends RecyclerView.ViewHolder {
    
    private final SortByItemLayout sortLayout;
    private boolean isInitialized = false;
    private boolean isListenerSet = false;
    
    public SortHeaderViewHolder(@NonNull View itemView) {
        super(itemView);
        sortLayout = itemView.findViewById(R.id.sort_layout);
        // استدعاء setupClickListeners مرة واحدة فقط عند الإنشاء
        sortLayout.setupClickListeners();
        isInitialized = true;
    }
    
    /**
     * ربط البيانات بالعرض - بدون إعادة ضبط المستمعات
     */
    public void bind(SortByItemLayout.SortType sortType, boolean ascending, 
                    SortByItemLayout.OnSortChangeListener listener) {
        if (sortLayout != null) {
            // تعيين المستمع مرة واحدة فقط
            if (listener != null && !isListenerSet) {
                sortLayout.setOnSortChangeListener(listener);
                isListenerSet = true;
            }
            // تحديث القيم فقط بدون إعادة ضبط المستمعات
            sortLayout.setSortType(sortType);
            sortLayout.setSortAscending(ascending);
        }
    }
    
    /**
     * ★ الحل 4: تعطيل/تفعيل شريط الفرز ★
     */
    public void setSortEnabled(boolean enabled) {
        if (sortLayout != null) {
            sortLayout.setEnabled(enabled);
            sortLayout.setClickable(enabled);
            sortLayout.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }
}
