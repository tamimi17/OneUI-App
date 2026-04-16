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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import dev.oneuiproject.oneui.widget.Toast;

import java.io.File;
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
 */
public class FontViewerFragment extends Fragment {

    private static final String KEY_FONT_PATH = "font_path";
    private static final String KEY_FONT_FILE_NAME = "font_file_name";
    private static final String KEY_FONT_REAL_NAME = "font_real_name";
    private static final String KEY_ORIGINAL_FONT_PATH = "original_font_path";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_FONT_WEIGHT = "font_weight";
    private static final String KEY_IS_VARIABLE_FONT = "is_variable_font";
    private static final String KEY_TTC_INDEX = "ttc_index";
    private static final String KEY_IS_SYSTEM_FONT = "is_system_font";
    private static final String TAG = "FontViewerFragment";

    private static final float DEFAULT_FONT_SIZE = 18f;
    private static final float MIN_FONT_SIZE = 12f;
    private static final float MAX_FONT_SIZE = 45f;
    private static final float DEFAULT_FONT_WEIGHT = 400f;

    private TextView previewSentence;

    private String currentFontPath;
    private String currentFontFileName;
    private String currentFontRealName;
    public String originalFontPath;
    private Typeface currentTypeface;
    private float currentFontSize = DEFAULT_FONT_SIZE;
    private float currentFontWeight = DEFAULT_FONT_WEIGHT;
    private boolean isVariableFont = false;
    private int currentTtcIndex = 0;
    private boolean isSystemFont = false;

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

        storageManager = new FontViewerStorageManager(context);
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

        currentFontSize = preferenceManager.getFontSize(DEFAULT_FONT_SIZE);
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
            currentFontPath = savedInstanceState.getString(KEY_FONT_PATH);
            currentFontFileName = savedInstanceState.getString(KEY_FONT_FILE_NAME);
            currentFontRealName = savedInstanceState.getString(KEY_FONT_REAL_NAME);
            originalFontPath = savedInstanceState.getString(KEY_ORIGINAL_FONT_PATH);
            currentFontSize = savedInstanceState.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
            currentFontWeight = savedInstanceState.getFloat(KEY_FONT_WEIGHT, DEFAULT_FONT_WEIGHT);
            isVariableFont = savedInstanceState.getBoolean(KEY_IS_VARIABLE_FONT, false);
            currentTtcIndex = savedInstanceState.getInt(KEY_TTC_INDEX, 0);
            isSystemFont = savedInstanceState.getBoolean(KEY_IS_SYSTEM_FONT, false);

            if (currentFontPath != null && !currentFontPath.isEmpty()) {
                notifyFontChangedImmediate();
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

    private void initViews(View view) {
        previewSentence = view.findViewById(R.id.preview_sentence);
    }

    public void loadFontFromPath(String path, String fileName, String realName) {
        loadFontFromPath(path, fileName, realName, 0, false);
    }

    public void loadFontFromPath(String path, String fileName, String realName, int ttcIndex) {
        loadFontFromPath(path, fileName, realName, ttcIndex, false);
    }

    /**
     * Enhanced handling for real font name
     */
    public void loadFontFromPath(String path, String fileName, String realName, int ttcIndex, boolean isSystemFont) {
        Log.d(TAG, "loadFontFromPath - Received data:");
        Log.d(TAG, "  realName: " + realName);
        Log.d(TAG, "  fileName: " + fileName);
        Log.d(TAG, "  ttcIndex: " + ttcIndex);
        Log.d(TAG, "  isSystemFont: " + isSystemFont);

        currentFontPath = path;
        currentFontFileName = fileName;
        currentFontRealName = realName;
        currentTtcIndex = ttcIndex;
        this.isSystemFont = isSystemFont;

        if (originalFontPath == null || originalFontPath.isEmpty()) {
            originalFontPath = extractRealPathFromUri(path);
        }

        // Update title immediately (will show real name or "Unknown Font" as received)
        notifyFontChangedImmediate();

        // Start loading font in background
        loadFontFromPathWithWeight(path, fileName, realName, DEFAULT_FONT_WEIGHT);
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
     */
    private void loadFontFromPathWithWeight(String path, String fileName, String realName, float weight) {
        bgExecutor.execute(() -> {
            try {
                File fontFile = new File(path);
                if (!fontFile.exists()) {
                    mainHandler.post(this::resetFontDisplay);
                    return;
                }

                boolean isVar = VariableFontHelper.isVariableFont(fontFile, currentTtcIndex);
                float finalWeight = weight;

                if (finalWeight == DEFAULT_FONT_WEIGHT && isVar) {
                    finalWeight = 400f;
                    preferenceManager.saveFontWeight(400f);
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
                        // 1. Set name to "Unknown Font"
                        currentFontRealName = getString(R.string.unknown_font);
                        currentTypeface = null;

                        // 2. ★ Force MainActivity to update title immediately ★
                        if (fontChangedListener != null) {
                            // Pass "Unknown Font" as real name and file name as subtitle
                            fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
                            Log.d(TAG, "★ Updated title to 'Unknown Font' for corrupted font");
                        }

                        // 3. Reset displayed typeface
                        Typeface defaultTypeface = Typeface.DEFAULT;
                        if (previewSentence != null) {
                            previewSentence.setTypeface(defaultTypeface);
                        }

                        // 4. Show error message
                        Toast.makeText(requireContext(),
                            getString(R.string.font_viewer_error_loading_font) +
                            " (" + getString(R.string.unknown_font) + ")",
                            Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                if (typeface != null) {
                    final Typeface finalTypeface = typeface;
                    final float finalWeightForHandler = finalWeight;
                    final boolean finalIsVariable = isVar;

                    mainHandler.post(() -> {
                        currentTypeface = finalTypeface;
                        currentFontWeight = finalWeightForHandler;
                        isVariableFont = finalIsVariable;

                        applyFontToPreviewTexts();
                        Log.d(TAG, "Font typeface loaded and applied successfully");
                    });
                } else {
                    throw new Exception("Failed to create Typeface - returned null");
                }

            } catch (Exception e) {
                // ★★★ General error handling ★★★
                mainHandler.post(() -> {
                    // 1. Set name to "Unknown Font"
                    currentFontRealName = getString(R.string.unknown_font);

                    // 2. Notify MainActivity of update
                    if (fontChangedListener != null) {
                        fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
                        Log.d(TAG, "★ Updated title to 'Unknown Font' after general error");
                    }

                    // 3. Reset typeface
                    currentTypeface = null;
                    Typeface defaultTypeface = Typeface.DEFAULT;
                    if (previewSentence != null) {
                        previewSentence.setTypeface(defaultTypeface);
                    }

                    // 4. Show error message
                    Toast.makeText(requireContext(),
                        getString(R.string.font_viewer_error_loading_font) +
                        " (" + getString(R.string.unknown_font) + ")",
                        Toast.LENGTH_SHORT).show();

                    Log.e(TAG, "Error creating typeface from path: " + path, e);
                });
            }
        });
    }

    public void loadFontFromUri(Uri uri, String fileName) {
        originalFontPath = storageManager.getRealPathFromUri(uri);
        isSystemFont = false;

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

    private void applyFontToPreviewTexts() {
        if (currentTypeface != null && previewSentence != null) {
            previewSentence.setTypeface(currentTypeface);
        }
        applyFontSize();
    }

    private void applyFontSize() {
        if (previewSentence != null) {
            previewSentence.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
        }
    }

    public void showFontSizeDialogPublic() {
        final float originalSize = currentFontSize;
        final float originalWeight = currentFontWeight;
        final Typeface originalTypeface = currentTypeface;

        FontSizeDialog fontSizeDialog = new FontSizeDialog(
            requireContext(),
            currentFontSize,
            MIN_FONT_SIZE,
            MAX_FONT_SIZE
        );

        fontSizeDialog.setCurrentWeight(currentFontWeight);
        fontSizeDialog.setOnFontSizeChangedListener(this::onFontSizeChanged);

        if (currentFontPath != null) {
            File fontFile = new File(currentFontPath);
            fontSizeDialog.setFontFile(fontFile);
            fontSizeDialog.setTtcIndex(currentTtcIndex);
            fontSizeDialog.setOnWeightChangedListener(this::onFontWeightChanged);
        }

        fontSizeDialog.setOnDialogCancelledListener(() -> {
            currentFontSize = originalSize;
            currentFontWeight = originalWeight;
            currentTypeface = originalTypeface;

            applyFontToPreviewTexts();
            applyFontSize();
        });

        fontSizeDialog.show();
    }

    private void resetFontDisplay() {
        currentTypeface = null;
        currentFontPath = null;
        currentFontFileName = null;
        currentFontRealName = null;
        originalFontPath = null;
        isVariableFont = false;
        currentFontWeight = DEFAULT_FONT_WEIGHT;
        currentTtcIndex = 0;
        isSystemFont = false;

        Typeface defaultTypeface = Typeface.DEFAULT;
        if (previewSentence != null) previewSentence.setTypeface(defaultTypeface);

        if (fontChangedListener != null) {
            fontChangedListener.onFontCleared();
        }
    }

    private void loadLastUsedFont() {
        String lastPath = preferenceManager.getLastViewedFontPath();
        String lastFileName = preferenceManager.getLastViewedFontFileName();
        String lastRealName = preferenceManager.getLastViewedFontRealName();
        float lastWeight = preferenceManager.getFontWeight(DEFAULT_FONT_WEIGHT);

        if (lastPath != null && !lastPath.isEmpty()) {
            File localFile = new File(lastPath);
            if (localFile.exists()) {
                currentFontPath = lastPath;
                currentFontFileName = lastFileName;
                currentFontRealName = lastRealName;
                currentTtcIndex = 0;
                isSystemFont = false;

                // Check real name
                if (currentFontRealName == null || currentFontRealName.isEmpty()) {
                    currentFontRealName = getString(R.string.unknown_font);
                }

                if (originalFontPath == null || originalFontPath.isEmpty()) {
                    originalFontPath = extractRealPathFromUri(lastPath);
                }

                notifyFontChangedImmediate();
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
