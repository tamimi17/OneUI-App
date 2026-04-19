package com.example.oneuiapp.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import dev.oneuiproject.oneui.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.oneuiapp.dialog.FontSizeDialog;
import com.example.oneuiapp.R;
import com.example.oneuiapp.utils.VariableFontHelper;
import com.example.oneuiapp.utils.SettingsHelper;
import com.example.oneuiapp.fontviewer.FontViewerStorageManager;
import com.example.oneuiapp.fontviewer.FontViewerPreferenceManager;
import com.example.oneuiapp.fontlist.systemfont.SystemFontCache;
import com.example.oneuiapp.metadata.FontMetadataExtractor;
import com.example.oneuiapp.viewmodel.SettingsViewModel;

/**
 * FontViewerFragment - Clean DataStore version
 * All SharedPreferences removed, using SettingsViewModel for reactive updates
 * ★ يختار Layout المناسب بناءً على حالة الثيم الشفاف عند الإنشاء ★
 *
 * ★ التعديل: نقل عرض وزن الخط من FontSizeDialog إلى هذا الفراغمنت ★
 *   - الخطوط الثابتة: عرض weight_label_text بوصف الوزن/العرض القادم من القائمة
 *   - الخطوط المتغيرة: عرض weight_spinner لاختيار الوزن تفاعلياً
 *   يبدأ كلا العنصرين مخفياً (GONE) ويُظهره الفراغمنت بعد تحميل الخط.
 *
 * ★ التعديل الجديد: إضافة شريط أدوات التنسيق السفلي العائم ★
 *   يحتوي الشريط على 3 أزرار بدون نصوص:
 *   - btn_font_size  : فتح FontSizeDialog (منقول من الـ FAB)
 *   - btn_font_bold  : تبديل حالة الخط العريض مع تمييز الخلفية
 *   - btn_font_italic: تبديل حالة الخط المائل مع تمييز الخلفية
 *
 * ★ الإصلاحات المضافة ★
 *   - (المشكلة 1) عدّاد الجيل mFontLoadGeneration يمنع تطبيق نتيجة طلب تحميل
 *     قديم فوق خط جديد عند التبديل السريع بين الخطوط (race condition).
 *   - (المشكلة 4) تصفير isBoldActive و isItalicActive في loadFontFromPath
 *     يضمن بدء كل خط جديد بحالة تنسيق نظيفة.
 */
public class FontViewerFragment extends Fragment {

    private static final String KEY_FONT_PATH          = "font_path";
    private static final String KEY_FONT_FILE_NAME     = "font_file_name";
    private static final String KEY_FONT_REAL_NAME     = "font_real_name";
    private static final String KEY_ORIGINAL_FONT_PATH = "original_font_path";
    private static final String KEY_FONT_SIZE          = "font_size";
    private static final String KEY_FONT_WEIGHT        = "font_weight";
    private static final String KEY_IS_VARIABLE_FONT   = "is_variable_font";
    private static final String KEY_TTC_INDEX          = "ttc_index";
    private static final String KEY_IS_SYSTEM_FONT     = "is_system_font";
    // ★ مفتاح حفظ وصف الوزن/العرض في الـ Bundle لاستعادته عند إعادة البناء ★
    private static final String KEY_WEIGHT_WIDTH_LABEL = "weight_width_label";
    // ★ مفاتيح حفظ حالتَي التنسيق لاستعادتهما عند إعادة البناء ★
    private static final String KEY_IS_BOLD_ACTIVE     = "is_bold_active";
    private static final String KEY_IS_ITALIC_ACTIVE   = "is_italic_active";
    private static final String TAG = "FontViewerFragment";

    private static final float DEFAULT_FONT_SIZE   = 18f;
    private static final float MIN_FONT_SIZE       = 12f;
    private static final float MAX_FONT_SIZE       = 45f;
    private static final float DEFAULT_FONT_WEIGHT = 400f;

    // ★ مراجع واجهة المستخدم ★
    private TextView previewSentence;
    // ★ عنصرا عرض الوزن في أعلى الصفحة ★
    private TextView weightLabelText;
    private AppCompatSpinner weightSpinner;

    // ★ أزرار شريط أدوات التنسيق السفلي ★
    private ImageView btnFontSize;
    private ImageView btnFontBold;
    private ImageView btnFontItalic;

    // ★ متغيرات لحفظ حالة التنسيق الحالي (تستمر مع Scroll وإعادة البناء) ★
    private boolean isBoldActive   = false;
    private boolean isItalicActive = false;

    private String currentFontPath;
    private String currentFontFileName;
    private String currentFontRealName;
    public String originalFontPath;
    private Typeface currentTypeface;
    private float currentFontSize   = DEFAULT_FONT_SIZE;
    private float currentFontWeight = DEFAULT_FONT_WEIGHT;
    private boolean isVariableFont  = false;
    private int currentTtcIndex     = 0;
    private boolean isSystemFont    = false;

    // ★ وصف الوزن/العرض القادم من قائمة الخطوط (مُستخرج مسبقاً، لا يُعاد استخراجه) ★
    private String currentWeightWidthLabel;

    // ★ قائمة أوزان الخط المتغير — تُستخدم في setupWeightSpinner ★
    private List<VariableFontHelper.VariableInstance> currentVariableInstances;

    // ★ الإصلاح (المشكلة 1): عدّاد الجيل لمنع race condition عند التبديل السريع بين الخطوط ★
    // volatile يضمن رؤية القيمة المحدَّثة من الخيط الخلفي فور تغييرها في الخيط الرئيسي.
    // عند اختيار خط جديد يُزاد العدّاد، فيتجاهل أي mainHandler.post قديم لا يطابق القيمة الحالية.
    private volatile int mFontLoadGeneration = 0;

    private OnFontChangedListener fontChangedListener;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FontViewerStorageManager storageManager;
    private FontViewerPreferenceManager preferenceManager;
    private SettingsViewModel settingsViewModel;

    /**
     * ★ يُفعّل اللمس على شاشة عارض الخطوط فوراً قبل اكتمال الأنيميشن ★
     * يُستدعى من MainActivity عند النقر على خط
     */
    public void enableTouch() {
        View root = getView();
        if (root != null) {
            root.setClickable(true);
            root.setFocusable(true);
            root.setEnabled(true);
            // ★ يضمن أولوية اللمس خلال الأنيميشن عبر رفع الـ View لأعلى Z-order ★
            root.bringToFront();
            root.requestFocus();
        }
    }

    public interface OnFontChangedListener {
        void onFontChanged(String fontRealName, String fontFileName);
        void onFontCleared();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnFontChangedListener) {
            fontChangedListener = (OnFontChangedListener) context;
        }

        storageManager    = new FontViewerStorageManager(context);
        preferenceManager = new FontViewerPreferenceManager(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        fontChangedListener = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize ViewModel
        settingsViewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

        currentFontSize   = preferenceManager.getFontSize(DEFAULT_FONT_SIZE);
        currentFontWeight = preferenceManager.getFontWeight(DEFAULT_FONT_WEIGHT);
    }

    /**
     * ★ اختيار Layout المناسب عند الإنشاء بناءً على حالة الثيم الشفاف ★
     * - الثيم الشفاف: fragment_font_viewer_transparent.xml (بدون RoundLinearLayout الملوّن)
     * - الثيم الافتراضي: fragment_font_viewer.xml (مع RoundLinearLayout وخلفية OneUI)
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                @Nullable Bundle savedInstanceState) {
        boolean transparentTheme = SettingsHelper.isTransparentThemeEnabled(requireContext());
        int layoutRes = transparentTheme
                ? R.layout.fragment_font_viewer_transparent
                : R.layout.fragment_font_viewer;
        return inflater.inflate(layoutRes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);

        // ★ NEW: Observe preview text changes from SettingsViewModel ★
        settingsViewModel.getPreviewText().observe(getViewLifecycleOwner(), previewText -> {
            if (previewSentence != null && previewText != null) {
                previewSentence.setText(previewText);
                if (currentTypeface != null) {
                    applyFontToPreviewTexts();
                }
                Log.d(TAG, "Preview text updated from settings");
            }
        });

        if (savedInstanceState != null) {
            currentFontPath        = savedInstanceState.getString(KEY_FONT_PATH);
            currentFontFileName    = savedInstanceState.getString(KEY_FONT_FILE_NAME);
            currentFontRealName    = savedInstanceState.getString(KEY_FONT_REAL_NAME);
            originalFontPath       = savedInstanceState.getString(KEY_ORIGINAL_FONT_PATH);
            currentFontSize        = savedInstanceState.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
            currentFontWeight      = savedInstanceState.getFloat(KEY_FONT_WEIGHT, DEFAULT_FONT_WEIGHT);
            isVariableFont         = savedInstanceState.getBoolean(KEY_IS_VARIABLE_FONT, false);
            currentTtcIndex        = savedInstanceState.getInt(KEY_TTC_INDEX, 0);
            isSystemFont           = savedInstanceState.getBoolean(KEY_IS_SYSTEM_FONT, false);
            // ★ استعادة وصف الوزن/العرض (قد يكون null إذا لم يُحفظ) ★
            currentWeightWidthLabel = savedInstanceState.getString(KEY_WEIGHT_WIDTH_LABEL);
            // ★ استعادة حالتَي Bold وItalic وتحديث مظهر الأزرار فوراً ★
            // ملاحظة: هذا المسار لا يمرّ بـ loadFontFromPath، لذا لا تصفير هنا —
            // نريد فعلاً استعادة الحالة المحفوظة.
            isBoldActive   = savedInstanceState.getBoolean(KEY_IS_BOLD_ACTIVE, false);
            isItalicActive = savedInstanceState.getBoolean(KEY_IS_ITALIC_ACTIVE, false);
            if (btnFontBold   != null) btnFontBold.setSelected(isBoldActive);
            if (btnFontItalic != null) btnFontItalic.setSelected(isItalicActive);

            if (currentFontPath != null && !currentFontPath.isEmpty()) {
                notifyFontChangedImmediate();
                // ★ يستدعي loadFontFromPathWithWeight مباشرةً لتجنب تصفير حالة Bold/Italic
                //   المستعادة للتو من savedInstanceState ★
                loadFontFromPathWithWeight(currentFontPath, currentFontFileName, currentFontRealName, currentFontWeight);
            }
        } else {
            loadLastUsedFont();
        }

        updatePreviewTexts();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Preview text is now handled by SettingsViewModel observer
        // No manual SharedPreferences listener needed
    }

    @Override
    public void onPause() {
        super.onPause();
        // No SharedPreferences listener to unregister
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        previewSentence = null;
        // ★ إلغاء ربط عناصر الوزن الجديدة عند تدمير الـ View ★
        weightLabelText = null;
        weightSpinner   = null;
        // ★ إلغاء ربط أزرار شريط التنسيق عند تدمير الـ View ★
        btnFontSize  = null;
        btnFontBold  = null;
        btnFontItalic = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        bgExecutor.shutdownNow();
    }

    private void onFontSizeChanged(float newSize) {
        currentFontSize = newSize;
        applyFontSize();
        preferenceManager.saveFontSize(newSize);
    }

    /**
     * ★ يُستدعى من weight_spinner عند اختيار المستخدم وزناً جديداً ★
     * يُطبّق الوزن الجديد على الخط المتغير ويُحدّث معاينة النص.
     */
    private void onFontWeightChanged(VariableFontHelper.VariableInstance instance) {
        if (instance == null || currentFontPath == null) {
            return;
        }

        currentFontWeight = instance.value;
        preferenceManager.saveFontWeight(instance.value);

        File fontFile = new File(currentFontPath);
        if (!fontFile.exists()) {
            return;
        }

        Typeface newTypeface = null;

        if (isSystemFont) {
            SystemFontCache cache = SystemFontCache.getInstance();
            newTypeface = cache.getTypefaceWithWeight(currentFontPath, instance.value, currentTtcIndex);
        } else {
            newTypeface = VariableFontHelper.createTypefaceWithWeight(fontFile, instance.value, currentTtcIndex);
        }

        if (newTypeface != null) {
            currentTypeface = newTypeface;
            applyFontToPreviewTexts();
        } else {
            Toast.makeText(requireContext(),
                "Failed to apply weight: " + instance.name,
                Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePreviewTexts() {
        if (previewSentence == null) {
            return;
        }

        String previewText = SettingsHelper.getPreviewText(requireContext());
        previewSentence.setText(previewText);

        if (currentTypeface != null) {
            applyFontToPreviewTexts();
        }

        applyFontSize();
    }

    /**
     * ★ التعديل: ربط عناصر واجهة الوزن الجديدة وأزرار شريط التنسيق ★
     * يتضمن الآن ربط الأزرار الثلاثة في الشريط السفلي وضبط مستمعاتها.
     */
    private void initViews(View view) {
        previewSentence = view.findViewById(R.id.preview_sentence);
        // ★ ربط عنصري عرض الوزن في أعلى الصفحة ★
        weightLabelText = view.findViewById(R.id.weight_label_text);
        weightSpinner   = view.findViewById(R.id.weight_spinner);

        // ★ ربط أزرار شريط أدوات التنسيق السفلي ★
        btnFontSize  = view.findViewById(R.id.btn_font_size);
        btnFontBold  = view.findViewById(R.id.btn_font_bold);
        btnFontItalic = view.findViewById(R.id.btn_font_italic);

        // ★ زر حجم الخط — يستدعي FontSizeDialog (منقول من الـ FAB في MainActivity) ★
        if (btnFontSize != null) {
            btnFontSize.setOnClickListener(v -> showFontSizeDialogPublic());
        }

        // ★ زر الخط العريض (Bold) — تبديل الحالة مع تمييز الخلفية ★
        // setSelected(true) يُشغّل الـ selector في bg_format_toggle ليُلوّن الخلفية
        if (btnFontBold != null) {
            btnFontBold.setOnClickListener(v -> {
                isBoldActive = !isBoldActive;
                btnFontBold.setSelected(isBoldActive);
                applyFontToPreviewTexts();
            });
        }

        // ★ زر الخط المائل (Italic) — تبديل الحالة مع تمييز الخلفية ★
        // setSelected(true) يُشغّل الـ selector في bg_format_toggle ليُلوّن الخلفية
        if (btnFontItalic != null) {
            btnFontItalic.setOnClickListener(v -> {
                isItalicActive = !isItalicActive;
                btnFontItalic.setSelected(isItalicActive);
                applyFontToPreviewTexts();
            });
        }
    }

    // ─────────────────────────────────────────────────────────
    // دوال loadFontFromPath — ترتيب التفويض يحافظ على تعيين currentWeightWidthLabel
    // ─────────────────────────────────────────────────────────

    /**
     * ★ يُعيد تعيين currentWeightWidthLabel قبل التفويض — لا label متاح هنا ★
     */
    public void loadFontFromPath(String path, String fileName, String realName) {
        currentWeightWidthLabel = null;
        loadFontFromPath(path, fileName, realName, 0, false);
    }

    /**
     * ★ يُعيد تعيين currentWeightWidthLabel قبل التفويض — لا label متاح هنا ★
     */
    public void loadFontFromPath(String path, String fileName, String realName, int ttcIndex) {
        currentWeightWidthLabel = null;
        loadFontFromPath(path, fileName, realName, ttcIndex, false);
    }

    /**
     * Enhanced handling for real font name
     * ★ هذه هي الدالة الأساسية — لا تُعيد تعيين currentWeightWidthLabel
     *   لأن المُستدعي (سواء الـ 6-param أو غيره) مسؤول عن ضبطها مسبقاً ★
     *
     * ★ الإصلاح (المشكلة 4): تصفير حالتَي Bold وItalic عند كل تحميل خط جديد ★
     * يضمن ألا تنتقل حالة التنسيق من خط سابق إلى الخط الجديد الذي يختاره المستخدم.
     * هذا المسار لا يُستدعى من savedInstanceState (الذي يستدعي loadFontFromPathWithWeight
     * مباشرةً)، لذا التصفير هنا لا يتعارض مع استعادة الحالة المحفوظة.
     */
    public void loadFontFromPath(String path, String fileName, String realName, int ttcIndex, boolean isSystemFont) {
        Log.d(TAG, "loadFontFromPath - Received data:");
        Log.d(TAG, "  realName: " + realName);
        Log.d(TAG, "  fileName: " + fileName);
        Log.d(TAG, "  ttcIndex: " + ttcIndex);
        Log.d(TAG, "  isSystemFont: " + isSystemFont);

        currentFontPath     = path;
        currentFontFileName = fileName;
        currentFontRealName = realName;
        currentTtcIndex     = ttcIndex;
        this.isSystemFont   = isSystemFont;

        // ★ الإصلاح (المشكلة 4): تصفير حالتَي التنسيق وتحديث مظهر الأزرار فوراً ★
        // يُطبَّق قبل استدعاء loadFontFromPathWithWeight حتى إذا ضغط المستخدم
        // Bold أثناء تحميل الخط الجديد، تبدأ من حالة نظيفة.
        isBoldActive   = false;
        isItalicActive = false;
        if (btnFontBold   != null) btnFontBold.setSelected(false);
        if (btnFontItalic != null) btnFontItalic.setSelected(false);

        if (originalFontPath == null || originalFontPath.isEmpty()) {
            originalFontPath = extractRealPathFromUri(path);
        }

        // Update title immediately (will show real name or "Unknown Font" as received)
        notifyFontChangedImmediate();

        // Start loading font in background
        loadFontFromPathWithWeight(path, fileName, realName, DEFAULT_FONT_WEIGHT);
    }

    /**
     * ★ الدالة الجديدة: تُستدعى من NavManager مع weightWidthLabel القادم من القائمة ★
     * تُعيّن currentWeightWidthLabel ثم تُفوّض للدالة الأساسية.
     * هذا يُغني عن إعادة استخراج الوزن لأنه مستخرج مسبقاً في القائمة.
     *
     * @param weightWidthLabel وصف الوزن/العرض الجاهز من القائمة (مثل "Bold, Condensed" أو "VF · Regular")
     */
    public void loadFontFromPath(String path, String fileName, String realName,
                                 int ttcIndex, boolean isSystemFont, String weightWidthLabel) {
        // ★ تعيين الـ label قبل استدعاء الدالة الأساسية حتى يكون متاحاً عند عرض الخط ★
        currentWeightWidthLabel = weightWidthLabel;
        loadFontFromPath(path, fileName, realName, ttcIndex, isSystemFont);
    }

    private String extractRealPathFromUri(String pathOrUri) {
        if (pathOrUri == null) return null;

        if (pathOrUri.startsWith("content://")) {
            Uri uri = Uri.parse(pathOrUri);
            String realPath = storageManager.getRealPathFromUri(uri);
            if (realPath != null && !realPath.isEmpty()) {
                return realPath;
            }
        }

        return pathOrUri;
    }

    private void notifyFontChangedImmediate() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performNotification();
        } else {
            mainHandler.post(this::performNotification);
        }
    }

    private void performNotification() {
        if (fontChangedListener != null && currentFontRealName != null && currentFontFileName != null) {
            fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
            Log.d(TAG, "MainActivity notified with realName: " + currentFontRealName + ", fileName: " + currentFontFileName);
        }
    }

    /**
     * Enhanced handling for corrupted fonts
     * ★ التعديل: إضافة استخراج أوزان الخط المتغير في الخيط الخلفي ★
     *   وتحديث واجهة الوزن (spinner أو label) في الخيط الرئيسي بعد تحميل الخط.
     *
     * ★ الإصلاح (المشكلة 1): منع race condition عبر عدّاد الجيل mFontLoadGeneration ★
     * عند استدعاء هذه الدالة يُزاد العدّاد ويُحفظ في myGeneration. عند اكتمال
     * الخيط الخلفي، إذا كانت myGeneration لا تساوي mFontLoadGeneration الحالية،
     * فهذا يعني أن المستخدم اختار خطاً جديداً أثناء التحميل، فتُتجاهل النتيجة القديمة.
     *
     * يُمسح currentTypeface فوراً في بداية الدالة حتى لا يظهر خط قديم إذا
     * ضغط المستخدم على Bold أثناء تحميل الخط الجديد.
     */
    private void loadFontFromPathWithWeight(String path, String fileName, String realName, float weight) {

        // ★ الإصلاح (المشكلة 1): مسح الخط الحالي فوراً لتجنب عرض خط قديم ★
        // إذا ضغط المستخدم Bold أثناء تحميل الخط الجديد، سيظهر الخط الافتراضي
        // بدلاً من خط الجلسة السابقة.
        currentTypeface = null;

        // ★ الإصلاح (المشكلة 1): حفظ رقم الجيل لتحديد ما إذا كان هذا الطلب لا يزال صالحاً ★
        final int myGeneration = ++mFontLoadGeneration;

        bgExecutor.execute(() -> {
            try {
                File fontFile = new File(path);
                if (!fontFile.exists()) {
                    mainHandler.post(() -> {
                        // ★ تجاهل النتيجة إذا بدأ طلب تحميل أحدث ★
                        if (myGeneration != mFontLoadGeneration) return;
                        resetFontDisplay();
                    });
                    return;
                }

                boolean isVar = VariableFontHelper.isVariableFont(fontFile, currentTtcIndex);
                float finalWeight = weight;

                if (finalWeight == DEFAULT_FONT_WEIGHT && isVar) {
                    finalWeight = 400f;
                    preferenceManager.saveFontWeight(400f);
                }

                // ★ استخراج قائمة أوزان الخط المتغير في الخيط الخلفي لتجنب تأخير الواجهة ★
                // للخطوط الثابتة: لا داعي للاستخراج لأن الوزن موجود في currentWeightWidthLabel
                List<VariableFontHelper.VariableInstance> variableInstances = null;
                if (isVar) {
                    variableInstances = VariableFontHelper.extractVariableInstances(fontFile, currentTtcIndex);
                }

                Typeface typeface = null;

                try {
                    if (isSystemFont) {
                        SystemFontCache cache = SystemFontCache.getInstance();
                        typeface = cache.getTypefaceWithWeight(path, finalWeight, currentTtcIndex);
                    } else {
                        typeface = VariableFontHelper.createTypefaceWithWeight(fontFile, finalWeight, currentTtcIndex);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "★ Typeface creation failed - font might be corrupted", e);

                    // ★★★ Solution: Immediate title update when corrupted font detected ★★★
                    mainHandler.post(() -> {
                        // ★ تجاهل النتيجة إذا بدأ طلب تحميل أحدث ★
                        if (myGeneration != mFontLoadGeneration) return;

                        // 1. Set name to "Unknown Font"
                        currentFontRealName = getString(R.string.unknown_font);
                        currentTypeface     = null;

                        // 2. ★ Force MainActivity to update title immediately ★
                        if (fontChangedListener != null) {
                            // Pass "Unknown Font" as real name and file name as subtitle
                            fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
                            Log.d(TAG, "★ Updated title to 'Unknown Font' for corrupted font");
                        }

                        // 3. إخفاء عناصر الوزن عند فشل تحميل الخط
                        hideWeightUI();

                        // 4. Reset displayed typeface
                        Typeface defaultTypeface = Typeface.DEFAULT;
                        if (previewSentence != null) {
                            previewSentence.setTypeface(defaultTypeface);
                        }

                        // 5. Show error message
                        Toast.makeText(requireContext(),
                            getString(R.string.font_viewer_error_loading_font) +
                            " (" + getString(R.string.unknown_font) + ")",
                            Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                if (typeface != null) {
                    final Typeface finalTypeface             = typeface;
                    final float finalWeightForHandler        = finalWeight;
                    final boolean finalIsVariable            = isVar;
                    // ★ تمرير القائمة إلى الخيط الرئيسي لإعداد الـ Spinner إن لزم ★
                    final List<VariableFontHelper.VariableInstance> finalInstances = variableInstances;

                    mainHandler.post(() -> {
                        // ★ تجاهل النتيجة إذا بدأ طلب تحميل أحدث (الإصلاح الجوهري للمشكلة 1) ★
                        if (myGeneration != mFontLoadGeneration) return;

                        currentTypeface   = finalTypeface;
                        currentFontWeight = finalWeightForHandler;
                        isVariableFont    = finalIsVariable;

                        // ★ تحديث عرض الوزن بعد تحديد نوع الخط ★
                        // الخط المتغير → Spinner لاختيار الوزن
                        // الخط الثابت  → نص يعرض الوزن/العرض من القائمة
                        if (finalIsVariable && finalInstances != null && !finalInstances.isEmpty()) {
                            setupWeightSpinner(finalInstances);
                        } else {
                            showWeightLabel(currentWeightWidthLabel);
                        }

                        applyFontToPreviewTexts();
                        Log.d(TAG, "Font typeface loaded and applied successfully");
                    });
                } else {
                    throw new Exception("Failed to create Typeface - returned null");
                }

            } catch (Exception e) {
                // ★★★ General error handling ★★★
                mainHandler.post(() -> {
                    // ★ تجاهل النتيجة إذا بدأ طلب تحميل أحدث ★
                    if (myGeneration != mFontLoadGeneration) return;

                    // 1. Set name to "Unknown Font"
                    currentFontRealName = getString(R.string.unknown_font);

                    // 2. Notify MainActivity of update
                    if (fontChangedListener != null) {
                        fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
                        Log.d(TAG, "★ Updated title to 'Unknown Font' after general error");
                    }

                    // 3. إخفاء عناصر الوزن عند الخطأ العام
                    hideWeightUI();

                    // 4. Reset typeface
                    currentTypeface = null;
                    Typeface defaultTypeface = Typeface.DEFAULT;
                    if (previewSentence != null) {
                        previewSentence.setTypeface(defaultTypeface);
                    }

                    // 5. Show error message
                    Toast.makeText(requireContext(),
                        getString(R.string.font_viewer_error_loading_font) +
                        " (" + getString(R.string.unknown_font) + ")",
                        Toast.LENGTH_SHORT).show();

                    Log.e(TAG, "Error creating typeface from path: " + path, e);
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // ★ دوال إدارة عرض الوزن ★
    // ─────────────────────────────────────────────────────────

    /**
     * ★ يُعدّ weight_spinner للخطوط المتغيرة ويُظهره بدلاً من weight_label_text ★
     *
     * ترتيب العمليات المقصود:
     * 1. ضبط الـ Adapter
     * 2. ضبط الاختيار الأولي
     * 3. تعيين المستمع في الـ post التالي — لتجنب تشغيل onFontWeightChanged أثناء التهيئة،
     *    لأن setAdapter و setSelection يُشغّلان onItemSelected في دورة الرسم التالية.
     *
     * @param instances قائمة أوزان الخط المتغير المُستخرجة في الخيط الخلفي
     */
    private void setupWeightSpinner(List<VariableFontHelper.VariableInstance> instances) {
        if (weightSpinner == null || weightLabelText == null || !isAdded()) return;

        currentVariableInstances = instances;
        weightLabelText.setVisibility(View.GONE);
        weightSpinner.setVisibility(View.VISIBLE);

        // بناء قائمة أسماء الأوزان للـ Spinner
        List<String> instanceNames = new ArrayList<>();
        for (VariableFontHelper.VariableInstance inst : instances) {
            instanceNames.add(inst.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            instanceNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // ★ ضبط الـ Adapter والاختيار الأولي قبل تعيين المستمع ★
        weightSpinner.setAdapter(adapter);

        // البحث عن الوزن الحالي في قائمة الأوزان للاختيار المبدئي الصحيح
        int selectedIndex = 0;
        for (int i = 0; i < instances.size(); i++) {
            if (Math.abs(instances.get(i).value - currentFontWeight) < 1f) {
                selectedIndex = i;
                break;
            }
        }
        weightSpinner.setSelection(selectedIndex);

        // ★ تعيين المستمع في الـ post التالي بعد اكتمال دورة الرسم ★
        // هذا يضمن أن onItemSelected الناتج عن setAdapter و setSelection لن يصل إلى المستمع،
        // وأي تغيير لاحق من المستخدم سيُشغّل onFontWeightChanged بشكل صحيح.
        final List<VariableFontHelper.VariableInstance> finalInstances = instances;
        weightSpinner.post(() -> {
            if (weightSpinner == null || !isAdded()) return;
            weightSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < finalInstances.size()) {
                        onFontWeightChanged(finalInstances.get(position));
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
    }

    /**
     * ★ يُظهر weight_label_text بوصف الوزن/العرض للخطوط الثابتة ★
     * إذا كان الـ label فارغاً أو null يُخفي العنصر.
     *
     * @param label النص القادم من القائمة (مثل "Bold, Condensed") أو null
     */
    private void showWeightLabel(String label) {
        if (weightLabelText == null || weightSpinner == null) return;

        weightSpinner.setVisibility(View.GONE);

        if (label != null && !label.isEmpty()) {
            weightLabelText.setText(label);
            weightLabelText.setVisibility(View.VISIBLE);
        } else {
            // لا label متاح (خط محلي قديم أو خط مُحمَّل من URI) — نُخفي العنصر بهدوء
            weightLabelText.setVisibility(View.GONE);
        }
    }

    /**
     * ★ يُخفي كلا عنصري الوزن — يُستدعى عند خطأ تحميل الخط أو إعادة ضبط العارض ★
     */
    private void hideWeightUI() {
        if (weightLabelText != null) weightLabelText.setVisibility(View.GONE);
        if (weightSpinner != null)   weightSpinner.setVisibility(View.GONE);
    }

    public void loadFontFromUri(Uri uri, String fileName) {
        originalFontPath = storageManager.getRealPathFromUri(uri);
        isSystemFont     = false;
        // ★ لا label متاح عند التحميل من URI (خارج القائمة) ★
        currentWeightWidthLabel = null;

        bgExecutor.execute(() -> {
            File copiedFont = storageManager.copyFontForViewing(uri, fileName);

            if (copiedFont != null && copiedFont.exists()) {
                String realName = null;

                try {
                    realName = FontMetadataExtractor.extractFontName(copiedFont, 0);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to extract font name from URI", e);
                }

                // If name extraction failed
                if (realName == null || realName.isEmpty() || "Unknown Font".equals(realName)) {
                    String finalFileName = fileName != null ? fileName : copiedFont.getName();
                    realName = getString(R.string.unknown_font);

                    preferenceManager.saveLastViewedFont(copiedFont.getAbsolutePath(), finalFileName, realName);

                    final String finalRealName = realName;
                    mainHandler.post(() -> {
                        loadFontFromPath(copiedFont.getAbsolutePath(), finalFileName, finalRealName, 0, false);

                        Toast.makeText(requireContext(),
                            "Warning: Font name could not be extracted",
                            Toast.LENGTH_SHORT).show();
                    });
                } else {
                    final String finalFileName = fileName != null ? fileName : copiedFont.getName();
                    final String finalRealName = realName;

                    preferenceManager.saveLastViewedFont(copiedFont.getAbsolutePath(), finalFileName, finalRealName);

                    mainHandler.post(() -> {
                        loadFontFromPath(copiedFont.getAbsolutePath(), finalFileName, finalRealName, 0, false);
                    });
                }
            } else {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(),
                            getString(R.string.font_viewer_error_loading_font),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * ★ التعديل: دمج حالتَي Bold وItalic عند تطبيق الخط على نص المعاينة ★
     * يُحدَّث الـ textStyle بناءً على isBoldActive وisItalicActive معاً.
     */
    private void applyFontToPreviewTexts() {
        if (previewSentence == null) return;

        // ★ تحديد الستايل المطلوب بناءً على حالة أزرار التنسيق ★
        int textStyle = Typeface.NORMAL;
        if (isBoldActive && isItalicActive) {
            textStyle = Typeface.BOLD_ITALIC;
        } else if (isBoldActive) {
            textStyle = Typeface.BOLD;
        } else if (isItalicActive) {
            textStyle = Typeface.ITALIC;
        }

        // ★ تطبيق الخط مع الستايل المدمج — setTypeface(face, style) يدمجهما معاً ★
        if (currentTypeface != null) {
            previewSentence.setTypeface(currentTypeface, textStyle);
        } else {
            previewSentence.setTypeface(Typeface.DEFAULT, textStyle);
        }

        applyFontSize();
    }

    private void applyFontSize() {
        if (previewSentence != null) {
            previewSentence.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
        }
    }

    /**
     * ★ التعديل: حُذفت جميع استدعاءات الوزن من هذه الدالة ★
     * يختص FontSizeDialog الآن بضبط حجم الخط فقط.
     * وزن الخط يُدار مباشرةً عبر weight_spinner في هذا الفراغمنت.
     */
    public void showFontSizeDialogPublic() {
        final float originalSize      = currentFontSize;
        final Typeface originalTypeface = currentTypeface;

        FontSizeDialog fontSizeDialog = new FontSizeDialog(
            requireContext(),
            currentFontSize,
            MIN_FONT_SIZE,
            MAX_FONT_SIZE
        );

        fontSizeDialog.setOnFontSizeChangedListener(this::onFontSizeChanged);

        fontSizeDialog.setOnDialogCancelledListener(() -> {
            // ★ استعادة الحجم فقط عند الإلغاء — الوزن يُدار الآن بالـ Spinner ★
            currentFontSize   = originalSize;
            currentTypeface   = originalTypeface;
            applyFontToPreviewTexts();
            applyFontSize();
        });

        fontSizeDialog.show();
    }

    /**
     * ★ التعديل: إضافة تصفير حالتَي Bold وItalic عند مسح الخط المعروض ★
     * يضمن ألا تبقى حالة التنسيق مُفعَّلة بعد عدم وجود خط.
     */
    private void resetFontDisplay() {
        currentTypeface         = null;
        currentFontPath         = null;
        currentFontFileName     = null;
        currentFontRealName     = null;
        originalFontPath        = null;
        isVariableFont          = false;
        currentFontWeight       = DEFAULT_FONT_WEIGHT;
        currentTtcIndex         = 0;
        isSystemFont            = false;
        // ★ إعادة ضبط الـ label عند مسح حالة العارض ★
        currentWeightWidthLabel  = null;
        currentVariableInstances = null;

        // ★ تصفير حالتَي التنسيق وتحديث مظهر الأزرار ★
        isBoldActive   = false;
        isItalicActive = false;
        if (btnFontBold   != null) btnFontBold.setSelected(false);
        if (btnFontItalic != null) btnFontItalic.setSelected(false);

        Typeface defaultTypeface = Typeface.DEFAULT;
        if (previewSentence != null) previewSentence.setTypeface(defaultTypeface);

        // ★ إخفاء عناصر الوزن عند مسح الخط ★
        hideWeightUI();

        if (fontChangedListener != null) {
            fontChangedListener.onFontCleared();
        }
    }

    private void loadLastUsedFont() {
        String lastPath     = preferenceManager.getLastViewedFontPath();
        String lastFileName = preferenceManager.getLastViewedFontFileName();
        String lastRealName = preferenceManager.getLastViewedFontRealName();
        float lastWeight    = preferenceManager.getFontWeight(DEFAULT_FONT_WEIGHT);

        if (lastPath != null && !lastPath.isEmpty()) {
            File localFile = new File(lastPath);
            if (localFile.exists()) {
                currentFontPath     = lastPath;
                currentFontFileName = lastFileName;
                currentFontRealName = lastRealName;
                currentTtcIndex     = 0;
                isSystemFont        = false;
                // ★ لا label متاح للخط الأخير المحفوظ (لم يُفتح من القائمة مباشرةً) ★
                currentWeightWidthLabel = null;

                // Check real name
                if (currentFontRealName == null || currentFontRealName.isEmpty()) {
                    currentFontRealName = getString(R.string.unknown_font);
                }

                if (originalFontPath == null || originalFontPath.isEmpty()) {
                    originalFontPath = extractRealPathFromUri(lastPath);
                }

                notifyFontChangedImmediate();
                // ★ يستدعي loadFontFromPathWithWeight مباشرةً (لا مرور بـ loadFontFromPath)
                //   لأن isBoldActive/isItalicActive عند بداية التطبيق يساويان false افتراضياً ★
                loadFontFromPathWithWeight(lastPath, lastFileName, lastRealName, lastWeight);
            } else {
                preferenceManager.clearLastViewedFont();
            }
        }
    }

    public Map<String, String> getFontMetaData() {
        if (currentFontPath == null) {
            return new java.util.HashMap<>();
        }

        File fontFile = new File(currentFontPath);

        Map<String, String> metadata = FontMetadataExtractor.extractMetadataWithTtcIndex(fontFile, currentTtcIndex);

        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }

        String displayPath = (originalFontPath != null && !originalFontPath.isEmpty())
            ? originalFontPath
            : currentFontPath;

        metadata.put("Path", displayPath);
        metadata.put("FileName", currentFontFileName != null ? currentFontFileName : "");

        // If font is corrupted, add "Unknown Font" to metadata
        if (!metadata.containsKey("FullName") && currentFontRealName != null) {
            if (currentFontRealName.equals(getString(R.string.unknown_font))) {
                metadata.put("FullName", getString(R.string.unknown_font));
                metadata.put("Warning", "Font metadata could not be extracted - file may be corrupted");
            } else {
                metadata.put("FullName", currentFontRealName);
            }
        }

        if (currentTtcIndex > 0) {
            metadata.put("TTC Index", String.valueOf(currentTtcIndex));
        }

        return metadata;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentFontPath != null) {
            outState.putString(KEY_FONT_PATH, currentFontPath);
        }
        if (currentFontFileName != null) {
            outState.putString(KEY_FONT_FILE_NAME, currentFontFileName);
        }
        if (currentFontRealName != null) {
            outState.putString(KEY_FONT_REAL_NAME, currentFontRealName);
        }
        if (originalFontPath != null) {
            outState.putString(KEY_ORIGINAL_FONT_PATH, originalFontPath);
        }
        // ★ حفظ وصف الوزن/العرض لاستعادته عند إعادة البناء ★
        if (currentWeightWidthLabel != null) {
            outState.putString(KEY_WEIGHT_WIDTH_LABEL, currentWeightWidthLabel);
        }
        // ★ حفظ حالتَي التنسيق لاستعادتهما عند إعادة البناء (دوران الشاشة مثلاً) ★
        outState.putBoolean(KEY_IS_BOLD_ACTIVE, isBoldActive);
        outState.putBoolean(KEY_IS_ITALIC_ACTIVE, isItalicActive);
        outState.putFloat(KEY_FONT_SIZE, currentFontSize);
        outState.putFloat(KEY_FONT_WEIGHT, currentFontWeight);
        outState.putBoolean(KEY_IS_VARIABLE_FONT, isVariableFont);
        outState.putInt(KEY_TTC_INDEX, currentTtcIndex);
        outState.putBoolean(KEY_IS_SYSTEM_FONT, isSystemFont);
    }

    public String getCurrentFontRealName() {
        return currentFontRealName;
    }

    public String getCurrentFontFileName() {
        return currentFontFileName;
    }

    public boolean hasFontSelected() {
        return currentFontPath != null && !currentFontPath.isEmpty();
    }
                    }
