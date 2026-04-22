package com.example.oneuiapp.activity;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fragment.LocalFontListFragment;
import com.example.oneuiapp.fragment.FontViewerFragment;
import com.example.oneuiapp.fragment.SystemFontListFragment;
import com.example.oneuiapp.fragment.FavoriteFontListFragment;
import com.example.oneuiapp.ui.drawer.DrawerListAdapter;
import com.example.oneuiapp.fontlist.search.SearchCoordinator;

import java.util.ArrayDeque;
import java.util.List;

import dev.oneuiproject.oneui.layout.DrawerLayout;

/**
 * NavManager - مدير التنقل المركزي للتطبيق
 *
 * يتولى هذا الكلاس جميع عمليات التنقل بين الشاشات (الفراغمنتات)
 * التي كانت مضمّنة سابقاً في MainActivity، ويتواصل مع النشاط
 * عبر واجهة Host لتجنب الاقتران المباشر.
 *
 * ★ نظام التنقل بزر الرجوع ★
 * يعتمد على مكدس تنقل مخصص (mNavBackStack) يتتبع مصدر اختيار الخط فقط.
 * أولوية زر الرجوع:
 *   1. إغلاق درج التنقل إذا كان مفتوحاً
 *   2. إلغاء الانتقال المعلّق خلال الـ 150ms
 *   3. إلغاء وضع التحديد المتعدد إذا كان نشطاً
 *   4. إغلاق البحث إذا كان مفتوحاً
 *   5. العودة لمصدر اختيار الخط من مكدس التنقل
 *   6. الانتقال لشاشة عارض الخطوط (الشاشة الجذر دائماً قبل الخروج)
 *   7. الخروج من التطبيق
 *
 * ★ آلية رصد حالة الدرج ★
 * مكتبة OneUI تُغلِّف الـ androidx.drawerlayout.widget.DrawerLayout داخلياً
 * ولا تُوفّر دالة عامة للوصول إليه. نبحث عنه مرة واحدة في setup()
 * عبر اجتياز شجرة الـ Views، ونحفظه في mInnerDrawer.
 * عند كل ضغطة رجوع نستدعي mInnerDrawer.isDrawerOpen() مباشرةً،
 * وهي دالة عامة موجودة في androidx DrawerLayout تعكس الحالة الفعلية دائماً
 * دون الحاجة لأي متغير تتبع منفصل أو مستمع إضافي.
 *
 * ★ التعديل: إضافة weightWidthLabel كمعامل في handleFontSelected
 *   ليُمرَّر مباشرةً إلى FontViewerFragment.loadFontFromPath دون إعادة استخراجه.
 */
public class NavManager {

    // ════════════════════════════════════════════════════════
    //  واجهة Host — تربط NavManager بـ MainActivity
    // ════════════════════════════════════════════════════════

    /**
     * واجهة الربط بين NavManager والنشاط المضيف (MainActivity).
     * تُزوّد NavManager بكل ما يحتاجه للتنقل دون اقتران مباشر بالنشاط.
     */
    public interface Host {
        /**
         * يُعيد الـ FragmentManager للتعاملات مع الفراغمنتات.
         * ★ الإصلاح: تُسمَّى getAppFragmentManager() بدلاً من getFragmentManager()
         *   لتجنب التعارض مع الدالة المُهمَلة android.app.FragmentManager
         *   الموروثة من Activity، والتي تُسبب خطأ تجميع عند التنفيذ في MainActivity. ★
         */
        FragmentManager getAppFragmentManager();

        /** يُعيد قائمة الفراغمنتات المرتّبة */
        List<Fragment> getFragments();

        /** يُعيد فهرس الفراغمنت الحالي */
        int getCurrentIndex();

        /** يُحدّث فهرس الفراغمنت الحالي */
        void setCurrentIndex(int index);

        /** يُعيد الـ OneUI DrawerLayout */
        DrawerLayout getDrawerLayout();

        /** يُعيد محوّل درج التنقل */
        DrawerListAdapter getDrawerAdapter();

        /** يُعيد منسّق البحث */
        SearchCoordinator getSearchCoordinator();

        /** يُعيد الاسم الحقيقي للخط المحدد حالياً */
        String getFontRealName();

        /** يُعيد اسم ملف الخط المحدد حالياً */
        String getFontFileName();

        /** يُحدّث الاسم الحقيقي للخط المحدد */
        void setFontRealName(String name);

        /** يُحدّث اسم ملف الخط المحدد */
        void setFontFileName(String name);

        /** يُحدّث عنوان الدرج للفراغمنت المعطى */
        void updateDrawerTitle(int index);

        /** يُحدّث ظهور عناصر قائمة شريط الأدوات لموضع معيّن */
        void updateMenuVisibility(int position);

        /** يُحدّث ظهور زر الإجراء العائم (FAB) لموضع معيّن */
        void updateFabVisibility(int position);

        /**
         * يُنفّذ الخروج الفعلي من التطبيق.
         * يُفرّق بين Android O (API 26) مع isTaskRoot وبقية الإصدارات.
         */
        void performExit();

        /** يعرض رسالة Toast تطلب من المستخدم الضغط مرة أخرى للخروج */
        void showPressAgainToExitToast();
    }

    // ════════════════════════════════════════════════════════
    //  ثوابت التنقل
    // ════════════════════════════════════════════════════════

    // ★ مدة تأخير الانتقال لإتاحة الوقت الكافي لظهور تأثير الريبل ★
    // تُطبَّق فقط على الانتقال الفعلي (showFragmentWithAnimation وتحميل الخط)،
    // بينما تحديث العناوين يحدث فوراً بدون أي تأخير.
    private static final long RIPPLE_DELAY_MS = 200;

    // ★ مدة أنيميشن الانتقال = startOffset (50ms) + duration (450ms) ★
    // تُستخدم لتأجيل تعليم الخط باللون الأزرق حتى اكتمال الانتقال بصرياً،
    // بحيث يرى المستخدم التمييز فقط عند عودته للقائمة لا قبل مغادرتها.
    private static final long FRAGMENT_ANIMATION_DURATION_MS = 500L;

    // ★ المهلة الزمنية المسموح بها بين الضغطتين بالمللي ثانية (2 ثانية) ★
    private static final long BACK_PRESS_EXIT_INTERVAL = 2000;

    // ★ مفتاح حفظ مكدس التنقل عند إعادة البناء ★
    static final String KEY_NAV_BACK_STACK = "nav_back_stack";

    // ════════════════════════════════════════════════════════
    //  حقول الحالة
    // ════════════════════════════════════════════════════════

    private final Host mHost;

    // ★ مكدس التنقل المخصص — يتتبع مصدر اختيار الخط فقط لدعم زر الرجوع ★
    // القاعدة: المكدس يُملأ فقط عند اختيار خط من قائمة (handleFontSelected).
    // التنقل عبر درج التنقل يُفرّغ المكدس دائماً، لأن المستخدم اختار وجهة جديدة بشكل صريح.
    // شاشة عارض الخطوط (index=1) هي الشاشة الجذر وهي آخر شاشة قبل الخروج.
    private final ArrayDeque<Integer> mNavBackStack = new ArrayDeque<>();

    // ★ مرجع الـ inner androidx DrawerLayout المُغلَّف داخل OneUI DrawerLayout ★
    // يُستخرج مرة واحدة في setup() ويُستخدم لاستدعاء isDrawerOpen() مباشرةً
    // عند كل ضغطة رجوع، وهو أنظف من تتبع الحالة عبر متغير أو مستمع منفصل.
    private androidx.drawerlayout.widget.DrawerLayout mInnerDrawer;

    // ★ مرجع لـ Runnable الانتقال المعلّق لإمكانية إلغائه عند الضغط على زر الرجوع ★
    private Runnable mPendingNavigation;

    // ★ يحفظ حالة العنوان قبل النقر لاستعادتها إذا أُلغي الانتقال ★
    private String mSavedFontRealName;
    private String mSavedFontFileName;

    // ★ متغير لتتبع وقت أول ضغطة رجوع على الشاشة الجذر ★
    // يُستخدم لتفعيل ميزة "اضغط مرة أخرى للخروج"
    // القيمة الافتراضية 0 تعني لم يُضغط بعد
    private long mBackPressedTime = 0;

    // ════════════════════════════════════════════════════════
    //  البناء والإعداد
    // ════════════════════════════════════════════════════════

    public NavManager(Host host) {
        mHost = host;
    }

    /**
     * ★ تهيئة NavManager عبر الـ OneUI DrawerLayout ★
     * يستخرج الـ inner DrawerLayout مرة واحدة ويحفظه في mInnerDrawer.
     * يجب استدعاؤها بعد inflating الـ layout مباشرةً من setupDrawer() في MainActivity.
     *
     * @param drawerLayout الـ OneUI DrawerLayout الرئيسي في النشاط
     */
    public void setup(DrawerLayout drawerLayout) {
        if (drawerLayout != null) {
            // ★ استخراج الـ inner DrawerLayout مرة واحدة وتخزينه في mInnerDrawer ★
            // يُستخدم لاحقاً في isDrawerCurrentlyOpen() لاستدعاء isDrawerOpen() مباشرةً
            // دون الحاجة لأي متغير تتبع أو مستمع إضافي.
            mInnerDrawer = findInnerDrawerLayout(drawerLayout);
        }
    }

    // ════════════════════════════════════════════════════════
    //  حالة الدرج
    // ════════════════════════════════════════════════════════

    /**
     * ★ التحقق من حالة الدرج مباشرةً من mInnerDrawer ★
     * isDrawerOpen() دالة عامة في androidx DrawerLayout تعكس الحالة الفعلية
     * لحظة الاستدعاء، وتشمل الفتح البرمجي والفتح بالسحب اليدوي على حدٍّ سواء.
     * GravityCompat.START يستهدف الدرج الجانبي بغض النظر عن اتجاه التخطيط.
     */
    public boolean isDrawerCurrentlyOpen() {
        return mInnerDrawer != null && mInnerDrawer.isDrawerOpen(GravityCompat.START);
    }

    /**
     * ★ البحث عن الـ inner androidx DrawerLayout داخل شجرة الـ Views ★
     * يتجول في أبناء الـ ViewGroup بشكل تكراري حتى يجد أول
     * androidx.drawerlayout.widget.DrawerLayout مُغلَّف داخل OneUI DrawerLayout.
     * هذا البديل ضروري لأن مكتبة OneUI لا تُوفّر دالة مباشرة للوصول للـ inner drawer.
     * تُستدعى هذه الدالة مرة واحدة فقط في setup() لتجنب أي تكلفة متكررة.
     *
     * @param parent جذر الشجرة للبحث فيها
     * @return الـ inner DrawerLayout إن وُجد، أو null إن لم يُعثر عليه
     */
    private androidx.drawerlayout.widget.DrawerLayout findInnerDrawerLayout(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof androidx.drawerlayout.widget.DrawerLayout) {
                return (androidx.drawerlayout.widget.DrawerLayout) child;
            }
            if (child instanceof ViewGroup) {
                androidx.drawerlayout.widget.DrawerLayout result =
                        findInnerDrawerLayout((ViewGroup) child);
                if (result != null) return result;
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  التنقل بين الفراغمنتات
    // ════════════════════════════════════════════════════════

    /**
     * ★ التنقل عبر درج التنقل — يُفرَّغ المكدس دائماً ★
     *
     * القاعدة الجوهرية:
     * - التنقل عبر الدرج يمثّل اختياراً صريحاً من المستخدم لوجهة جديدة،
     *   لذا يُفرَّغ المكدس بالكامل في جميع الحالات.
     * - المكدس مخصص حصراً لتتبع "مصدر اختيار الخط" (handleFontSelected)،
     *   وليس لتتبع تاريخ التنقل عبر الدرج.
     *
     * @param targetPosition الشاشة المستهدفة
     */
    public void navigateFromDrawer(int targetPosition) {
        // ★ تفريغ المكدس دائماً عند التنقل عبر الدرج ★
        // أي تنقل عبر الدرج يلغي تاريخ اختيار الخط السابق
        mNavBackStack.clear();

        mHost.setCurrentIndex(targetPosition);
        showFragmentFast(targetPosition);
        mHost.getDrawerAdapter().setSelectedItem(targetPosition);
        mHost.updateDrawerTitle(targetPosition);
    }

    /**
     * عرض الفراغمنت بشكل فوري دون أنيميشن.
     * يُستخدم للتنقل عبر الدرج وعند استعادة حالة النشاط.
     *
     * @param position فهرس الفراغمنت المستهدف
     */
    public void showFragmentFast(int position) {
        List<Fragment> fragments = mHost.getFragments();
        if (position < 0 || position >= fragments.size()) {
            return;
        }

        // ★ الإصلاح: استدعاء getAppFragmentManager() بدلاً من getFragmentManager() ★
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            if (fragment.isAdded()) {
                transaction.hide(fragment);
            }
        }

        Fragment targetFragment = fragments.get(position);
        if (targetFragment.isAdded()) {
            transaction.show(targetFragment);
        }

        transaction.commitNow();

        mHost.updateMenuVisibility(position);
        mHost.updateFabVisibility(position);
        mHost.getSearchCoordinator().onFragmentChanged(position);
    }

    /**
     * ★ عرض الفراغمنت مع أنيميشن انتقال أفقي ★
     * يُستخدم حصراً عند الانتقال بين قوائم الخطوط وشاشة عارض الخطوط،
     * ولا يُطبَّق على أي حالة تنقل أخرى في التطبيق.
     *
     * الأنيميشن المستخدم مأخوذ من ملفات depth_* الموجودة مسبقاً في المشروع،
     * وهي تعتمد على حركة أفقية بنسبة 27.8% مع تلاشي تدريجي.
     *
     * @param targetPosition الشاشة المستهدفة
     * @param isForward      true للانتقال إلى الأمام (قائمة ← عارض)،
     *                       false للرجوع إلى الخلف (عارض ← قائمة)
     */
    public void showFragmentWithAnimation(int targetPosition, boolean isForward) {
        List<Fragment> fragments = mHost.getFragments();
        if (targetPosition < 0 || targetPosition >= fragments.size()) {
            return;
        }

        // ★ الإصلاح: استدعاء getAppFragmentManager() بدلاً من getFragmentManager() ★
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        // ★ تطبيق الأنيميشن المناسب بناءً على اتجاه الانتقال ★
        // isForward=true : الشاشة الجديدة تدخل من اليمين، والحالية تخرج لليسار
        // isForward=false: الشاشة السابقة تعود من اليسار، والحالية تخرج لليمين
        if (isForward) {
            transaction.setCustomAnimations(
                    R.anim.depth_in_current_view,   // enter: شاشة العارض تدخل من اليمين
                    R.anim.depth_in_previous_view   // exit:  شاشة القائمة تخرج لليسار
            );
        } else {
            transaction.setCustomAnimations(
                    R.anim.depth_out_current_view,  // enter: شاشة القائمة تعود من اليسار
                    R.anim.depth_out_previous_view  // exit:  شاشة العارض تخرج لليمين
            );
        }

        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            if (fragment.isAdded()) {
                transaction.hide(fragment);
            }
        }

        Fragment targetFragment = fragments.get(targetPosition);
        if (targetFragment.isAdded()) {
            transaction.show(targetFragment);
        }

        // ★ commit() بدلاً من commitNow() لأن الأنيميشن لا يعمل مع commitNow() ★
        transaction.commit();

        mHost.updateMenuVisibility(targetPosition);
        mHost.updateFabVisibility(targetPosition);
        mHost.getSearchCoordinator().onFragmentChanged(targetPosition);
    }

    // ════════════════════════════════════════════════════════
    //  اختيار الخط والتنقل إلى العارض
    // ════════════════════════════════════════════════════════

    /**
     * ★ الإصلاح الجوهري لمشكلة أنيميشن إعادة الترتيب ★
     * الترتيب الصحيح للعمليات:
     * 1. الانتقال إلى شاشة العرض أولاً (لإخفاء القائمة عن أعين المستخدم)
     * 2. إغلاق البحث بعد الإخفاء (لكي يتم إعادة ترتيب القائمة في الخلفية بصمت تام)
     * هذا يضمن أن المستخدم لا يرى أي أنيميشن غير مرغوب فيه عند العودة للقائمة
     *
     * ★ ملاحظة على مكدس التنقل ★
     * عند اختيار خط من قائمة، نُضيف الشاشة الحالية للمكدس قبل الانتقال لعارض الخطوط،
     * مما يُمكّن المستخدم من العودة للقائمة التي جاء منها بزر الرجوع.
     * هذا هو المكان الوحيد الذي يُضاف فيه للمكدس (وليس navigateFromDrawer).
     *
     * ★ آلية تأخير الريبل ★
     * لإظهار تأثير الريبل على عنصر القائمة بشكل كامل قبل الانتقال، تنقسم العملية إلى:
     *   - فوري: تحديث العناوين وتحديد عنصر الدرج وحفظ المكدس وتعطيل اللمس.
     *     هذا يضمن أن العنوان يتغير لحظة الضغط دون أي تأخير مرئي.
     *   - مؤجَّل بـ RIPPLE_DELAY_MS: الانتقال الفعلي للفراغمنت وتحميل الخط وتفعيل العارض.
     *     هذا يمنح الريبل وقته الكامل للظهور قبل إخفاء شاشة القائمة.
     *
     * ★ ترتيب العمليات داخل mPendingNavigation ★
     * 1. enableTouch() أولاً — قبل showFragmentWithAnimation() لضمان استجابة اللمس
     *    الفورية على العارض بمجرد ظهوره، بدلاً من الانتظار حتى اكتمال commit() غير المتزامن.
     * 2. showFragmentWithAnimation() — يبدأ الأنيميشن
     * 3. collapseSearch() + تحميل الخط — عمليات مرافقة للأنيميشن
     * 4. saveAndHighlight() مؤجَّل بـ FRAGMENT_ANIMATION_DURATION_MS — يضمن أن التمييز
     *    الأزرق يظهر فقط بعد اكتمال الانتقال بصرياً، لا خلاله.
     *
     * ★ الأنيميشن ★
     * يُطبَّق أنيميشن الانتقال الأفقي عند الانتقال من أي قائمة خطوط (index 2 أو 3 أو 4)
     * إلى شاشة عارض الخطوط (index 1) فقط، دون أي حالة تنقل أخرى.
     *
     * ★ التعديل: إضافة معامل weightWidthLabel ★
     * يُمرَّر مباشرةً إلى FontViewerFragment.loadFontFromPath بدلاً من إعادة استخراجه،
     * إذ أن الوزن مستخرج مسبقاً وموجود في بيانات القائمة.
     *
     * @param fontPath         مسار ملف الخط أو content URI
     * @param realName         الاسم الحقيقي للخط
     * @param fileName         اسم ملف الخط
     * @param ttcIndex         فهرس الخط داخل ملف TTC
     * @param weightWidthLabel وصف الوزن والعرض الجاهز من القائمة (قد يكون null)
     */
    public void handleFontSelected(String fontPath, String realName, String fileName,
                                   int ttcIndex, String weightWidthLabel) {
        List<Fragment> fragments = mHost.getFragments();

        // ★ تحديد الفراغمنت المصدر الفعلي بدلاً من الاعتماد على getCurrentIndex() ★
        // الحالة المُعطِلة: عند ضغط زر الرجوع والنقر على خط في آنٍ واحد،
        // يُغيِّر handleBackPressed قيمة getCurrentIndex إلى 1 بشكل متزامن
        // قبل أن تُستدعى هذه الدالة. لكن عمليات hide/show تعتمد على commit()
        // غير المتزامن، لذا يعكس isHidden() الحالة الصحيحة لحظة الاستدعاء.
        // الحل: نبحث عن قائمة الخطوط غير المخفية فعلياً للحصول على المصدر الصحيح.
        int detectedSourceIndex = mHost.getCurrentIndex();
        for (int i = 0; i < fragments.size(); i++) {
            Fragment f = fragments.get(i);
            if (!f.isHidden()
                    && (f instanceof LocalFontListFragment
                        || f instanceof SystemFontListFragment
                        || f instanceof FavoriteFontListFragment)) { // ★ إضافة قائمة المفضلة ★
                detectedSourceIndex = i;
                break;
            }
        }
        final int sourceFragmentIndex = detectedSourceIndex;

        // ★ حفظ حالة العنوان الحالية قبل أي تعديل للاستعادة عند الإلغاء ★
        mSavedFontRealName = mHost.getFontRealName();
        mSavedFontFileName = mHost.getFontFileName();

        // ★ الإجراءان الفوريان قبل أي تأخير أو أنيميشن ★

        // الإجراء الأول: تعطيل قائمة الخطوط وظيفياً فوراً
        final Fragment sourceFragment = fragments.get(sourceFragmentIndex);
        if (sourceFragment instanceof LocalFontListFragment)
            ((LocalFontListFragment) sourceFragment).blockTouch();
        else if (sourceFragment instanceof SystemFontListFragment)
            ((SystemFontListFragment) sourceFragment).blockTouch();
        else if (sourceFragment instanceof FavoriteFontListFragment) // ★ إضافة قائمة المفضلة ★
            ((FavoriteFontListFragment) sourceFragment).blockTouch();

        // الإجراء الثاني: تفعيل شاشة عارض الخطوط فوراً قبل بدء الأنيميشن ★
        // استدعاؤه هنا يضمن أن root view العارض يملك clickable=true
        // قبل بدء الـ 150ms وقبل commit() غير المتزامن،
        // مما يُحل مشكلة بطء الاستجابة عند الدخول للعارض
        Fragment viewerFragment = fragments.get(1);
        if (viewerFragment instanceof FontViewerFragment)
            ((FontViewerFragment) viewerFragment).enableTouch();

        // ★ حفظ الشاشة الحالية (مصدر اختيار الخط) في المكدس للعودة إليها بزر الرجوع ★
        mNavBackStack.addLast(sourceFragmentIndex);

        // ★ تحديث فوري: العناوين وعنصر الدرج بدون أي تأخير ★
        // يضمن أن المستخدم يرى العنوان الجديد لحظة الضغط على الخط
        mHost.setCurrentIndex(1);
        mHost.getDrawerAdapter().setSelectedItem(1);
        mHost.setFontRealName(realName);
        mHost.setFontFileName(fileName);
        mHost.updateDrawerTitle(1);

        DrawerLayout drawerLayout = mHost.getDrawerLayout();

        // ★ تأخير RIPPLE_DELAY_MS قبل الانتقال الفعلي ★
        // يمنح تأثير الريبل على عنصر القائمة وقتاً كافياً للظهور بالكامل
        // قبل أن تُخفى شاشة القائمة وتظهر شاشة العارض.
        mPendingNavigation = () -> {
            mPendingNavigation = null;

            // 1. ★ الانتقال إلى شاشة العرض مع أنيميشن أفقي (قائمة ← عارض) ★
            showFragmentWithAnimation(1, true);

            // 2. ★ إغلاق البحث بعد الإخفاء (لكي يتم إعادة ترتيب القائمة في الخلفية بدون وميض) ★
            // onHiddenChanged في الـ Fragment سيُوقف الأنيميشن فور استدعاء collapseSearch
            mHost.getSearchCoordinator().collapseSearch();

            // 3. ★ تحميل الخط في فراغمنت العارض ★
            Fragment frag = mHost.getFragments().get(1);
            if (frag instanceof FontViewerFragment) {
                FontViewerFragment fontViewerFragment = (FontViewerFragment) frag;
                fontViewerFragment.originalFontPath = fontPath;
                // ★ خطوط المفضلة هي خطوط محلية دائماً (isSystemFont = false) ★
                boolean isSystemFont = (sourceFragmentIndex == 3);
                if (fontPath != null && fontPath.startsWith("content://")) {
                    // ★ خطوط URI: لا يوجد weightWidthLabel من القائمة — يُستخرج التنوع تلقائياً ★
                    fontViewerFragment.loadFontFromUri(Uri.parse(fontPath), realName);
                } else {
                    // ★ التعديل: تمرير weightWidthLabel مباشرةً للفراغمنت ★
                    fontViewerFragment.loadFontFromPath(fontPath, fileName, realName,
                                                        ttcIndex, isSystemFont, weightWidthLabel);
                }
            }

            // ★ الإصلاح 2: تعليم الخط باللون الأزرق بعد اكتمال الأنيميشن ★
            // التأجيل بـ FRAGMENT_ANIMATION_DURATION_MS يضمن أن التمييز البصري
            // يظهر فقط بعد انتهاء الانتقال، لا أثناءه أو قبله
            drawerLayout.postDelayed(() -> {
                if (sourceFragment instanceof LocalFontListFragment)
                    ((LocalFontListFragment) sourceFragment).saveAndHighlight(fontPath);
                else if (sourceFragment instanceof SystemFontListFragment)
                    ((SystemFontListFragment) sourceFragment).saveAndHighlight(fontPath);
                else if (sourceFragment instanceof FavoriteFontListFragment) // ★ إضافة قائمة المفضلة ★
                    ((FavoriteFontListFragment) sourceFragment).saveAndHighlight(fontPath);
            }, FRAGMENT_ANIMATION_DURATION_MS);
        };
        drawerLayout.postDelayed(mPendingNavigation, RIPPLE_DELAY_MS);
    }

    // ════════════════════════════════════════════════════════
    //  زر الرجوع
    // ════════════════════════════════════════════════════════

    /**
     * ★ منطق زر الرجوع المحدّث ★
     *
     * أولوية التنفيذ:
     * 1. إغلاق درج التنقل إذا كان مفتوحاً — يُستعلم مباشرةً من mInnerDrawer
     * 2. إلغاء الانتقال المعلّق إذا ضغط المستخدم على زر الرجوع خلال الـ 150ms
     *    مع استعادة حالة العنوان واللمس كما كانت قبل النقر
     * 3. إلغاء وضع التحديد المتعدد — مع الإبقاء على البحث مفتوحاً
     * 4. إغلاق البحث إذا كان مفتوحاً
     * 5. العودة لمصدر اختيار الخط من مكدس التنقل مع أنيميشن أفقي (عارض ← قائمة)
     * 6. الانتقال لشاشة عارض الخطوط (الشاشة الجذر دائماً قبل الخروج)
     * 7. الخروج من التطبيق
     */
    public void handleBackPressed() {
        List<Fragment> fragments = mHost.getFragments();

        // 1. ★ الأولوية القصوى: إغلاق درج التنقل إذا كان مفتوحاً ★
        // isDrawerCurrentlyOpen() تستعلم من mInnerDrawer مباشرةً دون أي متغير وسيط
        if (isDrawerCurrentlyOpen()) {
            mHost.getDrawerLayout().setDrawerOpen(false, true);
            return;
        }

        // 2. ★ إلغاء الانتقال المعلّق إذا ضغط المستخدم على زر الرجوع خلال الـ 150ms ★
        if (mPendingNavigation != null) {
            mHost.getDrawerLayout().removeCallbacks(mPendingNavigation);
            mPendingNavigation = null;
            // ★ استعادة حالة العنوان كما كانت قبل النقر ★
            mHost.setFontRealName(mSavedFontRealName);
            mHost.setFontFileName(mSavedFontFileName);
            int sourceIndex = mNavBackStack.isEmpty() ? mHost.getCurrentIndex()
                    : mNavBackStack.removeLast();
            mHost.setCurrentIndex(sourceIndex);
            mHost.getDrawerAdapter().setSelectedItem(sourceIndex);
            mHost.updateDrawerTitle(sourceIndex);
            // ★ إعادة تفعيل اللمس على القائمة المصدر ★
            Fragment sourceFrag = fragments.get(sourceIndex);
            if (sourceFrag instanceof LocalFontListFragment)
                ((LocalFontListFragment) sourceFrag).unblockTouch();
            else if (sourceFrag instanceof SystemFontListFragment)
                ((SystemFontListFragment) sourceFrag).unblockTouch();
            else if (sourceFrag instanceof FavoriteFontListFragment) // ★ إضافة قائمة المفضلة ★
                ((FavoriteFontListFragment) sourceFrag).unblockTouch();
            return;
        }

        // 3. ★ الأولوية الثانية: إغلاق وضع التحديد في الفراغمنت ★
        // إذا كان الفراغمنت في وضع التحديد، سيقوم بإلغائه ونتوقف هنا
        // ليبقى البحث مفتوحاً مع نتائجه كما هو
        Fragment currentFragment = fragments.get(mHost.getCurrentIndex());
        if (currentFragment instanceof LocalFontListFragment) {
            if (((LocalFontListFragment) currentFragment).handleBackPressed()) {
                return;
            }
        } else if (currentFragment instanceof FavoriteFontListFragment) {
            // ★ دعم إلغاء وضع التحديد المتعدد في قائمة المفضلة ★
            if (((FavoriteFontListFragment) currentFragment).handleBackPressed()) {
                return;
            }
        }

        // 4. ★ الأولوية الثالثة: إغلاق البحث إذا كان مفتوحاً ★
        if (mHost.getSearchCoordinator().isSearchExpanded()) {
            mHost.getSearchCoordinator().collapseSearch();
            return;
        }

        // 5. ★ الأولوية الرابعة: العودة لمصدر اختيار الخط مع أنيميشن أفقي ★
        // المكدس يحتوي فقط على مصادر اختيار الخطوط (قائمة محلية أو نظام أو مفضلة)
        // الأنيميشن يُطبَّق هنا فقط لأن المصدر دائماً قائمة خطوط (index 2 أو 3 أو 4)
        if (!mNavBackStack.isEmpty()) {
            int previousIndex = mNavBackStack.removeLast();
            mHost.setCurrentIndex(previousIndex);
            showFragmentWithAnimation(previousIndex, false);
            // ★ تفعيل الشاشة التي نعود إليها وظيفياً فوراً ★
            Fragment prevFrag = fragments.get(previousIndex);
            if (prevFrag instanceof LocalFontListFragment)
                ((LocalFontListFragment) prevFrag).unblockTouch();
            else if (prevFrag instanceof SystemFontListFragment)
                ((SystemFontListFragment) prevFrag).unblockTouch();
            else if (prevFrag instanceof FavoriteFontListFragment) // ★ إضافة قائمة المفضلة ★
                ((FavoriteFontListFragment) prevFrag).unblockTouch();
            else if (prevFrag instanceof FontViewerFragment)
                ((FontViewerFragment) prevFrag).enableTouch();
            mHost.getDrawerAdapter().setSelectedItem(previousIndex);
            mHost.updateDrawerTitle(previousIndex);
            return;
        }

        // 6. ★ الأولوية الخامسة: الانتقال لشاشة عارض الخطوط (الشاشة الجذر) مع أنيميشن ★
        // شاشة عارض الخطوط هي آخر شاشة دائماً قبل الخروج من التطبيق
        // يُطبَّق أنيميشن الرجوع الأفقي لأن المستخدم يتراجع نحو الشاشة الجذر
        if (mHost.getCurrentIndex() != 1) {
            int prev = mHost.getCurrentIndex();
            mHost.setCurrentIndex(1);
            showFragmentWithAnimation(1, false);
            // ★ القائمة الخارجة تُحجب، والعارض الداخل يُفعَّل ★
            Fragment prevFrag = fragments.get(prev);
            if (prevFrag instanceof LocalFontListFragment)
                ((LocalFontListFragment) prevFrag).blockTouch();
            else if (prevFrag instanceof SystemFontListFragment)
                ((SystemFontListFragment) prevFrag).blockTouch();
            else if (prevFrag instanceof FavoriteFontListFragment) // ★ إضافة قائمة المفضلة ★
                ((FavoriteFontListFragment) prevFrag).blockTouch();
            Fragment viewer = fragments.get(1);
            if (viewer instanceof FontViewerFragment)
                ((FontViewerFragment) viewer).enableTouch();
            mHost.getDrawerAdapter().setSelectedItem(1);
            mHost.updateDrawerTitle(1);
            return;
        }

        // 7. ★ الخروج من التطبيق — نحن على الشاشة الجذر والمكدس فارغ ★
        // ميزة "اضغط مرة أخرى للخروج": عند الضغطة الأولى تظهر رسالة Toast،
        // وعند الضغطة الثانية خلال المهلة المحددة يتم الخروج فعلياً.
        long currentTime = System.currentTimeMillis();
        if (currentTime - mBackPressedTime < BACK_PRESS_EXIT_INTERVAL) {
            // الضغطة الثانية في الوقت المحدد — الخروج من التطبيق
            mHost.performExit();
        } else {
            // الضغطة الأولى — عرض رسالة Toast وحفظ وقت الضغط
            mBackPressedTime = currentTime;
            mHost.showPressAgainToExitToast();
        }
    }

    // ════════════════════════════════════════════════════════
    //  حفظ الحالة واستعادتها
    // ════════════════════════════════════════════════════════

    /**
     * ★ حفظ مكدس التنقل في الـ Bundle ★
     * يُستدعى من onSaveInstanceState في MainActivity.
     *
     * @param outState الـ Bundle المستهدف
     */
    public void saveState(Bundle outState) {
        // ★ حفظ مكدس التنقل كمصفوفة أعداد صحيحة ★
        outState.putIntArray(KEY_NAV_BACK_STACK, navBackStackToArray());
    }

    /**
     * ★ استعادة مكدس التنقل من الـ Bundle ★
     * يُحوّل المصفوفة المحفوظة إلى ArrayDeque بنفس الترتيب الأصلي.
     *
     * @param savedInstanceState الـ Bundle المصدر
     */
    public void restoreNavBackStack(Bundle savedInstanceState) {
        mNavBackStack.clear();
        int[] stackArray = savedInstanceState.getIntArray(KEY_NAV_BACK_STACK);
        if (stackArray != null) {
            for (int index : stackArray) {
                mNavBackStack.addLast(index);
            }
        }
    }

    /**
     * ★ تحويل مكدس التنقل إلى مصفوفة للحفظ في Bundle ★
     * يحافظ على الترتيب الصحيح (من الأقدم للأحدث).
     */
    private int[] navBackStackToArray() {
        int[] array = new int[mNavBackStack.size()];
        int i = 0;
        for (int index : mNavBackStack) {
            array[i++] = index;
        }
        return array;
    }
}
