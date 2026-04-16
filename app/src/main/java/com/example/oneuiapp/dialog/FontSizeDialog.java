package com.example.oneuiapp.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SeslSeekBar;

import java.io.File;
import java.util.List;

import com.example.oneuiapp.utils.VariableFontHelper;
import com.example.oneuiapp.R;

/**
 * FontSizeDialog - محدّث لدعم TTC Index
 * 
 * الإصلاح: إضافة دعم ttcIndex لاستخراج الأوزان الصحيحة من ملفات TTC
 */
public class FontSizeDialog {

    public interface OnFontSizeChangedListener {
        void onFontSizeChanged(float newSize);
    }
    
    public interface OnWeightChangedListener {
        void onWeightChanged(VariableFontHelper.VariableInstance instance);
    }
    
    public interface OnDialogCancelledListener {
        void onDialogCancelled();
    }

    private final Context context;
    private final float currentSize;
    private final float minSize;
    private final float maxSize;
    private OnFontSizeChangedListener sizeListener;
    private OnWeightChangedListener weightListener;
    private OnDialogCancelledListener cancelListener;
    
    private TextView fontSizeValue;
    private SeslSeekBar seekBar;
    private LinearLayout weightContainer;
    private AppCompatSpinner weightSpinner;
    private View divider;
    
    private AlertDialog dialog;
    private float tempSize;
    
    private File fontFile;
    private List<VariableFontHelper.VariableInstance> variableInstances;
    private VariableFontHelper.VariableInstance selectedInstance;
    private float currentWeight;
    
    // ★★★ الإضافة: دعم TTC Index ★★★
    private int ttcIndex = 0;

    public FontSizeDialog(Context context, float currentSize, float minSize, float maxSize) {
        this.context = context;
        this.currentSize = currentSize;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.tempSize = currentSize;
        this.currentWeight = 400f;
    }

    public void setOnFontSizeChangedListener(OnFontSizeChangedListener listener) {
        this.sizeListener = listener;
    }
    
    public void setOnWeightChangedListener(OnWeightChangedListener listener) {
        this.weightListener = listener;
    }
    
    public void setOnDialogCancelledListener(OnDialogCancelledListener listener) {
        this.cancelListener = listener;
    }

    public void setFontFile(File fontFile) {
        this.fontFile = fontFile;
    }

    public void setCurrentWeight(float weight) {
        this.currentWeight = weight;
    }
    
    /**
     * ★★★ الإضافة: دالة لتعيين TTC Index ★★★
     */
    public void setTtcIndex(int ttcIndex) {
        this.ttcIndex = ttcIndex;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Font Adjustment");

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_font_size, null);
        builder.setView(dialogView);

        fontSizeValue = dialogView.findViewById(R.id.font_size_value);
        seekBar = dialogView.findViewById(R.id.font_size_seekbar);
        weightContainer = dialogView.findViewById(R.id.weight_selection_container);
        weightSpinner = dialogView.findViewById(R.id.weight_spinner);
        divider = dialogView.findViewById(R.id.divider);

        setupWeightSpinner();
        setupSeekBar();

        builder.setPositiveButton("OK", (dialog, which) -> {
            if (sizeListener != null) {
                sizeListener.onFontSizeChanged(tempSize);
            }
            if (weightListener != null && selectedInstance != null) {
                weightListener.onWeightChanged(selectedInstance);
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

    /**
     * ★★★ الإصلاح: تمرير ttcIndex لاستخراج الأوزان ★★★
     */
    private void setupWeightSpinner() {
        if (weightContainer == null || weightSpinner == null) {
            return;
        }

        if (fontFile == null || !fontFile.exists()) {
            weightContainer.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
            return;
        }

        // ★★★ الإصلاح: استخدام ttcIndex بدلاً من 0 ★★★
        boolean isVariable = VariableFontHelper.isVariableFont(fontFile, ttcIndex);

        if (!isVariable) {
            weightContainer.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
            return;
        }

        // ★★★ الإصلاح: تمرير ttcIndex لاستخراج القائمة الصحيحة ★★★
        variableInstances = VariableFontHelper.extractVariableInstances(fontFile, ttcIndex);

        if (variableInstances == null || variableInstances.isEmpty()) {
            weightContainer.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
            return;
        }

        weightContainer.setVisibility(View.VISIBLE);
        if (divider != null) divider.setVisibility(View.VISIBLE);

        ArrayAdapter<VariableFontHelper.VariableInstance> adapter = new ArrayAdapter<>(
            context,
            android.R.layout.simple_spinner_item,
            variableInstances
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weightSpinner.setAdapter(adapter);

        int selectedPosition = findWeightPosition(currentWeight);
        if (selectedPosition != -1) {
            selectedInstance = variableInstances.get(selectedPosition);
            weightSpinner.setSelection(selectedPosition, false);
        } else if (!variableInstances.isEmpty()) {
            selectedPosition = findWeightPosition(400f);
            if (selectedPosition == -1) {
                selectedPosition = 0;
            }
            selectedInstance = variableInstances.get(selectedPosition);
            weightSpinner.setSelection(selectedPosition, false);
        }

        weightSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedInstance = variableInstances.get(position);
                if (weightListener != null) {
                    weightListener.onWeightChanged(selectedInstance);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    
    private int findWeightPosition(float weight) {
        if (variableInstances == null || variableInstances.isEmpty()) {
            return -1;
        }
        
        for (int i = 0; i < variableInstances.size(); i++) {
            if (Math.abs(variableInstances.get(i).value - weight) < 0.1f) {
                return i;
            }
        }
        
        int closestIndex = -1;
        float closestDiff = Float.MAX_VALUE;
        
        for (int i = 0; i < variableInstances.size(); i++) {
            float diff = Math.abs(variableInstances.get(i).value - weight);
            if (diff < closestDiff) {
                closestDiff = diff;
                closestIndex = i;
            }
        }
        
        return closestIndex;
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
