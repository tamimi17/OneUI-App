package com.example.oneuiapp.fontviewer;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ImageView;

/**
 * BoldItalicFormatting - يُدير حالتَي الخط العريض والمائل بشكل مستقل
 *
 * ★ فُصل عن FontViewerFragment لتقليص حجمه وتحسين قابلية الصيانة ★
 * يتولى هذا الكلاس:
 *   - حفظ حالتَي isBoldActive و isItalicActive
 *   - ربط مستمعات النقر على أيقونتَي B وI
 *   - احتساب textStyle المناسب وإعادته عبر OnStyleChangedListener
 *   - حفظ الحالة واستعادتها عند إعادة البناء (دوران الشاشة)
 *   - تصفير الحالة عند تحميل خط جديد
 */
public class BoldItalicFormatting {

    // ★ مفاتيح Bundle — انتقلت من FontViewerFragment إلى هنا ★
    private static final String KEY_IS_BOLD_ACTIVE   = "is_bold_active";
    private static final String KEY_IS_ITALIC_ACTIVE = "is_italic_active";

    // ★ حالتا التنسيق الحالية ★
    private boolean isBoldActive   = false;
    private boolean isItalicActive = false;

    // ★ مراجع أيقونتَي B وI في واجهة المستخدم ★
    private ImageView btnBold;
    private ImageView btnItalic;

    // ★ المستمع الذي يُبلَّغ عند تغيير الستايل ★
    private OnStyleChangedListener listener;

    /**
     * واجهة الاستدعاء — تُبلَّغ FontViewerFragment بالستايل الجديد
     * لتطبيقه على نص المعاينة.
     */
    public interface OnStyleChangedListener {
        void onStyleChanged(int textStyle);
    }

    /**
     * ★ يُعدّ الكلاس بربط الأيقونتين وضبط مستمعاتهما ★
     *
     * @param btnBold   أيقونة الخط العريض (B)
     * @param btnItalic أيقونة الخط المائل (I)
     * @param listener  المستمع الذي يُبلَّغ عند كل تغيير
     */
    public void setup(ImageView btnBold, ImageView btnItalic, OnStyleChangedListener listener) {
        this.btnBold   = btnBold;
        this.btnItalic = btnItalic;
        this.listener  = listener;

        // ★ زر الخط العريض (Bold) — تبديل الحالة مع تمييز الخلفية ★
        // setSelected(true) يُشغّل الـ selector في bg_format_toggle ليُلوّن الخلفية
        if (btnBold != null) {
            btnBold.setOnClickListener(v -> {
                isBoldActive = !isBoldActive;
                btnBold.setSelected(isBoldActive);
                notifyStyleChanged();
            });
        }

        // ★ زر الخط المائل (Italic) — تبديل الحالة مع تمييز الخلفية ★
        // setSelected(true) يُشغّل الـ selector في bg_format_toggle ليُلوّن الخلفية
        if (btnItalic != null) {
            btnItalic.setOnClickListener(v -> {
                isItalicActive = !isItalicActive;
                btnItalic.setSelected(isItalicActive);
                notifyStyleChanged();
            });
        }
    }

    /**
     * ★ يحسب textStyle المناسب بناءً على الحالة الحالية ويُبلّغ المستمع ★
     */
    private void notifyStyleChanged() {
        if (listener != null) {
            listener.onStyleChanged(getCurrentTextStyle());
        }
    }

    /**
     * ★ يُعيد textStyle المناسب بناءً على حالتَي Bold وItalic ★
     *
     * @return أحد قيم: Typeface.NORMAL, BOLD, ITALIC, BOLD_ITALIC
     */
    public int getCurrentTextStyle() {
        if (isBoldActive && isItalicActive) return Typeface.BOLD_ITALIC;
        if (isBoldActive)                   return Typeface.BOLD;
        if (isItalicActive)                 return Typeface.ITALIC;
        return Typeface.NORMAL;
    }

    /**
     * ★ يُصفّر حالتَي التنسيق ويُحدّث مظهر الأيقونتين فوراً ★
     * يُستدعى عند تحميل خط جديد لضمان بدء كل خط بحالة تنسيق نظيفة.
     * (الإصلاح المشكلة 4 — منقول من FontViewerFragment)
     */
    public void reset() {
        isBoldActive   = false;
        isItalicActive = false;
        if (btnBold   != null) btnBold.setSelected(false);
        if (btnItalic != null) btnItalic.setSelected(false);
    }

    /**
     * ★ يُلغي ربط الأيقونتين لمنع memory leak عند تدمير الـ View ★
     * يُستدعى من FontViewerFragment.onDestroyView()
     */
    public void unbind() {
        if (btnBold   != null) btnBold.setOnClickListener(null);
        if (btnItalic != null) btnItalic.setOnClickListener(null);
        btnBold   = null;
        btnItalic = null;
        listener  = null;
    }

    /**
     * ★ يُعيد تطبيق الحالة المرئية على الأيقونتين بعد إعادة ربطهما ★
     * يُستدعى بعد setup() عند استعادة الحالة من savedInstanceState.
     */
    public void syncViewState() {
        if (btnBold   != null) btnBold.setSelected(isBoldActive);
        if (btnItalic != null) btnItalic.setSelected(isItalicActive);
    }

    /**
     * ★ يحفظ حالتَي التنسيق في Bundle لاستعادتهما عند إعادة البناء ★
     * يُستدعى من FontViewerFragment.onSaveInstanceState()
     */
    public void saveState(Bundle outState) {
        outState.putBoolean(KEY_IS_BOLD_ACTIVE,   isBoldActive);
        outState.putBoolean(KEY_IS_ITALIC_ACTIVE, isItalicActive);
    }

    /**
     * ★ يستعيد حالتَي التنسيق من Bundle ★
     * يُستدعى من FontViewerFragment.onViewCreated() عند وجود savedInstanceState.
     * لا يُحدّث مظهر الأيقونتين هنا — استدعِ syncViewState() بعد setup().
     */
    public void restoreState(Bundle savedInstanceState) {
        isBoldActive   = savedInstanceState.getBoolean(KEY_IS_BOLD_ACTIVE,   false);
        isItalicActive = savedInstanceState.getBoolean(KEY_IS_ITALIC_ACTIVE, false);
    }

    public boolean isBoldActive()   { return isBoldActive; }
    public boolean isItalicActive() { return isItalicActive; }
          }
