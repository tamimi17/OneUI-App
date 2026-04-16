package com.example.oneuiapp.dialog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.app.AlertDialog;
import android.content.res.Configuration;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.oneuiapp.R;
import com.example.oneuiapp.utils.FileUtils;

import java.io.File;

/**
 * FontActionDialogs - إدارة الديالوجات المتخصصة للحذف وإعادة التسمية
 *
 * الميزات:
 * - ديالوج إعادة التسمية مع تحديد تلقائي للنص وفتح الكيبورد
 * - تعطيل زر إعادة التسمية إذا لم يتغير الاسم
 * - رسالة خطأ باللون الأحمر تحت حقل الإدخال مباشرةً بدلاً من النافذة المنبثقة
 * - ديالوج الحذف بدون عنوان (فقط رسالة)
 * - دعم الحذف الفردي والمتعدد
 */
public class FontActionDialogs {

    public interface OnRenameListener {
        void onRename(String oldPath, String newFileName);
    }

    public interface OnDeleteListener {
        void onDeleteConfirmed();
    }

    /**
     * إظهار ديالوج إعادة التسمية
     *
     * @param context     السياق
     * @param currentPath المسار الحالي للملف
     * @param listener    مستمع إعادة التسمية
     */
    public static void showRenameDialog(Context context, String currentPath, OnRenameListener listener) {
        if (context == null || currentPath == null || listener == null) return;

        File file = new File(currentPath);
        String currentFileName = file.getName();
        String nameWithoutExt  = FileUtils.removeExtension(currentFileName);
        String extension       = FileUtils.getExtension(currentFileName);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename_font, null);
        final AppCompatEditText input     = dialogView.findViewById(R.id.rename_edit_text);
        final TextView          errorText = dialogView.findViewById(R.id.rename_error_text);

        input.setText(nameWithoutExt);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.rename_dialog_title);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.action_rename, null);
        builder.setNegativeButton(R.string.action_cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        dialog.setOnShowListener(d -> {
            input.requestFocus();
            input.selectAll();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
            }, 100);

            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setEnabled(false);

            // ★ لون الـ underline الافتراضي للحقل — نحتفظ به لاستعادته عند الحاجة ★
            int redColor   = ContextCompat.getColor(context, dev.oneuiproject.oneui.design.R.color.oui_functional_red_color);
            ColorStateList redTint = ColorStateList.valueOf(redColor);

            // ★ استخدام ألوان SESL مباشرةً بناءً على الوضع الحالي (داكن/فاتح) ★
            boolean isDarkMode = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int defaultTintColor = ContextCompat.getColor(context, isDarkMode
                    ? androidx.appcompat.R.color.sesl_edit_text_tint_color_dark
                    : androidx.appcompat.R.color.sesl_edit_text_tint_color_light);
            ColorStateList defaultTint = ColorStateList.valueOf(defaultTintColor);

            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String newText = s.toString().trim();

                    // ★ التحقق من وجود الملف أثناء الكتابة مباشرةً ★
                    boolean isEmpty       = newText.isEmpty();
                    boolean isSameName    = newText.equals(nameWithoutExt);
                    boolean isInvalid     = !isEmpty && !isValidFileName(newText);
                    boolean fileExists    = false;

                    if (!isEmpty && !isSameName && !isInvalid) {
                        String fullNewName = extension.isEmpty() ? newText : newText + "." + extension;
                        fileExists = new File(file.getParent(), fullNewName).exists();
                    }

                    if (fileExists) {
                        // ★ 1. تلوين الحقل باللون الأحمر فوراً ★
                        ViewCompat.setBackgroundTintList(input, redTint);
                        // ★ 2. عرض رسالة الخطأ فوراً ★
                        showError(errorText, context.getString(R.string.error_file_exists));
                        // ★ 3. تعطيل الزر ★
                        positiveButton.setEnabled(false);
                    } else {
                        ViewCompat.setBackgroundTintList(input, defaultTint);
                        errorText.setVisibility(View.GONE);

                        boolean isValid = !isEmpty && !isSameName && !isInvalid;
                        positiveButton.setEnabled(isValid);
                    }
                }
            });

            positiveButton.setOnClickListener(v -> {
                String newName = input.getText() != null ? input.getText().toString().trim() : "";

                if (newName.isEmpty() || !isValidFileName(newName)) {
                    showError(errorText, context.getString(R.string.error_invalid_name));
                    return;
                }

                if (newName.equals(nameWithoutExt)) {
                    dialog.dismiss();
                    return;
                }

                listener.onRename(currentPath, extension.isEmpty() ? newName : newName + "." + extension);

                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    /**
     * عرض رسالة الخطأ تحت حقل الإدخال.
     * اللون الأحمر محدد في الـ XML عبر @color/oui_functional_red_color.
     */
    private static void showError(TextView errorText, String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    /**
     * إظهار ديالوج تأكيد الحذف
     *
     * @param context    السياق
     * @param count      عدد العناصر المراد حذفها
     * @param totalCount إجمالي عدد العناصر (لتحديد "حذف الكل")
     * @param listener   مستمع الحذف
     */
    public static void showDeleteDialog(Context context, int count, int totalCount, OnDeleteListener listener) {
        if (context == null || listener == null || count <= 0) return;

        String message;
        if (count == 1) {
            message = context.getString(R.string.delete_single_confirmation);
        } else if (count == totalCount && count > 1) {
            message = context.getString(R.string.delete_all_confirmation);
        } else {
            message = context.getString(R.string.delete_multiple_confirmation, count);
        }

        new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> listener.onDeleteConfirmed())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * التحقق من صحة اسم الملف
     */
    private static boolean isValidFileName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String[] forbiddenChars = {"/", "\\", ":", "*", "?", "\"", "<", ">", "|"};
        for (String forbidden : forbiddenChars) {
            if (name.contains(forbidden)) return false;
        }
        return true;
    }
                    }
