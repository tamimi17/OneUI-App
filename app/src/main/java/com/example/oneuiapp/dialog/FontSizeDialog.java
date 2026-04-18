package com.example.oneuiapp.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SeslSeekBar;

import com.example.oneuiapp.R;

/**
 * FontSizeDialog - ديالوج ضبط حجم الخط
 *
 * ★ التعديل: حُذف كل ما يتعلق بمنتقي الوزن (Spinner) من هذا الديالوج بالكامل.
 *   انتقلت هذه الوظيفة إلى FontViewerFragment لتكون في متناول المستخدم
 *   مباشرةً على شاشة العارض دون الحاجة لفتح أي ديالوج.
 *   يختص هذا الكلاس الآن بضبط حجم الخط (Font Size) فقط.
 */
public class FontSizeDialog {

    public interface OnFontSizeChangedListener {
        void onFontSizeChanged(float newSize);
    }

    public interface OnDialogCancelledListener {
        void onDialogCancelled();
    }

    private final Context context;
    private final float currentSize;
    private final float minSize;
    private final float maxSize;
    private OnFontSizeChangedListener sizeListener;
    private OnDialogCancelledListener cancelListener;

    private TextView fontSizeValue;
    private SeslSeekBar seekBar;

    private AlertDialog dialog;
    private float tempSize;

    public FontSizeDialog(Context context, float currentSize, float minSize, float maxSize) {
        this.context     = context;
        this.currentSize = currentSize;
        this.minSize     = minSize;
        this.maxSize     = maxSize;
        this.tempSize    = currentSize;
    }

    public void setOnFontSizeChangedListener(OnFontSizeChangedListener listener) {
        this.sizeListener = listener;
    }

    public void setOnDialogCancelledListener(OnDialogCancelledListener listener) {
        this.cancelListener = listener;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Font Adjustment");

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_font_size, null);
        builder.setView(dialogView);

        fontSizeValue = dialogView.findViewById(R.id.font_size_value);
        seekBar       = dialogView.findViewById(R.id.font_size_seekbar);

        setupSeekBar();

        builder.setPositiveButton("OK", (dialog, which) -> {
            if (sizeListener != null) {
                sizeListener.onFontSizeChanged(tempSize);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            if (cancelListener != null) {
                cancelListener.onDialogCancelled();
            }
            dialog.dismiss();
        });

        dialog = builder.create();
        dialog.show();
    }

    private void setupSeekBar() {
        if (seekBar == null) {
            return;
        }

        int range = (int) (maxSize - minSize);
        seekBar.setMax(range);
        seekBar.setMode(SeslSeekBar.MODE_EXPAND);

        int currentProgress = (int) (currentSize - minSize);
        seekBar.setProgress(currentProgress);
        updateFontSizeText(currentSize);

        seekBar.setOnSeekBarChangeListener(new SeslSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeslSeekBar seekBar, int progress, boolean fromUser) {
                tempSize = minSize + progress;
                updateFontSizeText(tempSize);

                if (sizeListener != null && fromUser) {
                    sizeListener.onFontSizeChanged(tempSize);
                }
            }

            @Override
            public void onStartTrackingTouch(SeslSeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeslSeekBar seekBar) {}
        });
    }

    private void updateFontSizeText(float size) {
        if (fontSizeValue != null) {
            fontSizeValue.setText(String.format("%.0f", size));
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
