package com.example.oneuiapp.ui.drawer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.example.oneuiapp.fragment.FontViewerFragment;
import com.example.oneuiapp.fragment.LocalFontListFragment;
import com.example.oneuiapp.fragment.SystemFontListFragment;
import com.example.oneuiapp.fragment.FavoriteFontListFragment; // ★ جديد ★
import com.example.oneuiapp.fragment.HomeFragment;
import com.example.oneuiapp.R;

/**
 * DrawerListAdapter - محول قائمة الدرج
 *
 * التعديلات المطبقة:
 * 1. إضافة SystemFontListFragment إلى القائمة
 * 2. تحديث الأيقونات والعناوين المناسبة للـ Fragments (الآن 4 fragments)
 * 3. ★ إضافة FavoriteFontListFragment إلى القائمة (الآن 5 fragments) ★
 *    - الأيقونة: ic_oui_favorite_off
 *    - العنوان: drawer_favorites
 * ★ 4. إضافة دعم الخط الفاصل المتقطع (Separator) بين مجموعات العناصر ★
 *    - يُدرج الـ Adapter الفواصل تلقائياً بناءً على نوع الـ Fragment
 *      دون أي تعديل على NavManager أو MainActivity.
 *    - قائمة العرض (mDisplayList) تختلف عن قائمة الفراغمنتات (mFragments):
 *      تحتوي الأولى على null في مواضع الفواصل.
 *    - يُحوِّل الـ Adapter مواضع العرض إلى فهارس الفراغمنتات الفعلية
 *      داخلياً قبل إرسالها للـ Listener، بحيث يظل NavManager بمعزل تام
 *      عن آلية إدراج الفواصل.
 *
 * ━━ ترتيب العرض في الدرج ━━
 *   [0] Home
 *   [1] Font Viewer
 *   [─] ── فاصل ──
 *   [2] Local Fonts
 *   [3] System Fonts
 *   [4] Favorites
 *   [─] ── فاصل ──
 */
public class DrawerListAdapter extends RecyclerView.Adapter<DrawerListViewHolder> {

    // ════════════════════════════════════════════════════════
    //  ثوابت نوع الـ View
    // ════════════════════════════════════════════════════════

    // ★ نوع العرض للفاصل المتقطع — يتوافق مع قيم getItemViewType ★
    private static final int VIEW_TYPE_SEPARATOR = 0;
    private static final int VIEW_TYPE_ITEM      = 1;

    // ════════════════════════════════════════════════════════
    //  الحقول
    // ════════════════════════════════════════════════════════

    private final Context mContext;

    /** قائمة الفراغمنتات الفعلية بدون فواصل (الفهارس 0–4 تطابق NavManager) */
    private final List<Fragment> mFragments;

    /**
     * قائمة العرض المُوسَّعة — تحتوي على الفراغمنتات مُتخلَّلةً بـ null
     * في مواضع الفواصل. يبنيها buildDisplayList() عند إنشاء الـ Adapter.
     *
     * الترتيب الحالي:
     *   displayPos 0 → HomeFragment          (fragmentIndex 0)
     *   displayPos 1 → FontViewerFragment     (fragmentIndex 1)
     *   displayPos 2 → null  ← فاصل
     *   displayPos 3 → LocalFontListFragment  (fragmentIndex 2)
     *   displayPos 4 → SystemFontListFragment (fragmentIndex 3)
     *   displayPos 5 → FavoriteFontListFragment (fragmentIndex 4)
     *   displayPos 6 → null  ← فاصل
     */
    private final List<Fragment> mDisplayList;

    private final DrawerListener mListener;

    /** موضع العرض (displayPos) للعنصر المختار حالياً */
    private int mSelectedDisplayPos = 0;

    // ════════════════════════════════════════════════════════
    //  الواجهة
    // ════════════════════════════════════════════════════════

    public interface DrawerListener {
        /**
         * يُستدعى عند نقر المستخدم على عنصر تنقل.
         *
         * @param fragmentIndex الفهرس الفعلي للفراغمنت في قائمة NavManager (0–4)،
         *                      لا موضع العرض في الـ RecyclerView.
         * @return true إذا نجح التنقل ويجب تحديث التحديد البصري
         */
        boolean onDrawerItemSelected(int fragmentIndex);
    }

    // ════════════════════════════════════════════════════════
    //  البناء
    // ════════════════════════════════════════════════════════

    public DrawerListAdapter(
            @NonNull Context context, List<Fragment> fragments, DrawerListener listener) {
        mContext   = context;
        mFragments = fragments;
        mListener  = listener;

        // ★ بناء قائمة العرض مرة واحدة عند إنشاء الـ Adapter ★
        mDisplayList = buildDisplayList();
    }

    // ════════════════════════════════════════════════════════
    //  بناء قائمة العرض
    // ════════════════════════════════════════════════════════

    /**
     * ★ بناء قائمة العرض بإدراج null بعد كل Fragment يستوجب فاصلاً ★
     *
     * الفواصل تُدرج بعد:
     *   - FontViewerFragment  : يفصل عارض الخطوط عن قوائم الخطوط
     *   - FavoriteFontListFragment : يفصل قائمة المفضلة عمّا يليها (إن وُجد)
     *
     * هذه الدالة هي المكان الوحيد الذي يُحدَّد فيه موضع الفواصل،
     * مما يُسهّل إضافة فواصل جديدة أو تغيير مواضعها مستقبلاً.
     */
    private List<Fragment> buildDisplayList() {
        List<Fragment> display = new ArrayList<>();
        for (Fragment f : mFragments) {
            display.add(f);
            // ★ إدراج فاصل بعد عارض الخطوط وبعد قائمة المفضلة ★
            if (f instanceof FontViewerFragment
                    || f instanceof FavoriteFontListFragment) {
                display.add(null); // null = فاصل
            }
        }
        return display;
    }

    // ════════════════════════════════════════════════════════
    //  تحويل المواضع
    // ════════════════════════════════════════════════════════

    /**
     * تحويل موضع العرض (displayPos) إلى الفهرس الفعلي للفراغمنت (fragmentIndex).
     *
     * @param displayPos موضع العنصر في RecyclerView
     * @return الفهرس الفعلي في mFragments، أو -1 إذا كان الموضع فاصلاً
     */
    private int toFragmentIndex(int displayPos) {
        if (displayPos < 0 || displayPos >= mDisplayList.size()) return -1;
        Fragment f = mDisplayList.get(displayPos);
        if (f == null) return -1; // فاصل
        return mFragments.indexOf(f);
    }

    /**
     * ★ تحويل فهرس الفراغمنت (fragmentIndex) إلى موضع العرض (displayPos) ★
     * يُستخدم في setSelectedItem() الذي يستقبل fragmentIndex من NavManager.
     *
     * @param fragmentIndex الفهرس الفعلي في mFragments (0–4)
     * @return موضع العرض في mDisplayList، أو -1 عند الفهرس غير الصالح
     */
    private int toDisplayPos(int fragmentIndex) {
        if (fragmentIndex < 0 || fragmentIndex >= mFragments.size()) return -1;
        Fragment target = mFragments.get(fragmentIndex);
        return mDisplayList.indexOf(target);
    }

    // ════════════════════════════════════════════════════════
    //  RecyclerView.Adapter
    // ════════════════════════════════════════════════════════

    @Override
    public int getItemCount() {
        return mDisplayList.size();
    }

    /**
     * ★ يُميّز بين الفاصل (VIEW_TYPE_SEPARATOR=0) والعنصر العادي (VIEW_TYPE_ITEM=1) ★
     * null في mDisplayList يعني فاصلاً.
     */
    @Override
    public int getItemViewType(int position) {
        return (mDisplayList.get(position) == null) ? VIEW_TYPE_SEPARATOR : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public DrawerListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        // ★ اختيار الـ layout المناسب بحسب نوع الـ ViewHolder ★
        boolean isSeparator = (viewType == VIEW_TYPE_SEPARATOR);
        int layoutRes = isSeparator ? R.layout.drawer_list_separator
                                    : R.layout.drawer_list_item;
        View view = inflater.inflate(layoutRes, parent, false);
        return new DrawerListViewHolder(view, isSeparator);
    }

    @Override
    public void onBindViewHolder(@NonNull DrawerListViewHolder holder, int position) {
        // ★ الفاصل لا يحتاج أي ربط بيانات — نتوقف هنا مباشرةً ★
        if (holder.isSeparator()) return;

        Fragment fragment = mDisplayList.get(position);
        if (fragment == null) return;

        int iconRes = getIconForFragment(fragment);
        String title = getTitleForFragment(fragment);

        if (iconRes != 0)        holder.setIcon(iconRes);
        if (!title.isEmpty())    holder.setTitle(title);

        holder.setSelected(position == mSelectedDisplayPos);

        holder.itemView.setOnClickListener(v -> {
            final int adapterPos = holder.getBindingAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;

            // ★ تحويل موضع العرض → فهرس الفراغمنت قبل إرساله للـ Listener ★
            // NavManager يعمل دائماً بالفهارس الفعلية (0–4)، لا بمواضع العرض
            int fragIndex = toFragmentIndex(adapterPos);
            if (fragIndex < 0) return;

            boolean selectionChanged = false;
            if (mListener != null) {
                selectionChanged = mListener.onDrawerItemSelected(fragIndex);
            }

            if (selectionChanged) {
                setSelectedItem(fragIndex);
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  بيانات العناصر
    // ════════════════════════════════════════════════════════

    /**
     * تحديد الأيقونة المناسبة لكل Fragment
     * الآن هناك 5 fragments: Home, FontViewer, LocalFontList, SystemFontList, FavoriteFontList
     */
    private int getIconForFragment(Fragment fragment) {
        if (fragment instanceof HomeFragment) {
            return dev.oneuiproject.oneui.R.drawable.ic_oui_home_outline;
        } else if (fragment instanceof FontViewerFragment) {
            return R.drawable.ic_oui_text_style_default;
        } else if (fragment instanceof LocalFontListFragment) {
            return dev.oneuiproject.oneui.R.drawable.ic_oui_device_outline;
        } else if (fragment instanceof SystemFontListFragment) {
            return R.drawable.ic_android_3;
        } else if (fragment instanceof FavoriteFontListFragment) {
            // ★ أيقونة قائمة المفضلة في الدرج ★
            return dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off;
        }
        return 0;
    }

    /**
     * تحديد العنوان المناسب لكل Fragment
     * الآن هناك 5 fragments: Home, FontViewer, LocalFontList, SystemFontList, FavoriteFontList
     */
    private String getTitleForFragment(Fragment fragment) {
        if (fragment instanceof HomeFragment) {
            return mContext.getString(R.string.drawer_home);
        } else if (fragment instanceof FontViewerFragment) {
            return mContext.getString(R.string.drawer_font_viewer);
        } else if (fragment instanceof LocalFontListFragment) {
            return mContext.getString(R.string.drawer_local_fonts);
        } else if (fragment instanceof SystemFontListFragment) {
            return mContext.getString(R.string.drawer_system_fonts);
        } else if (fragment instanceof FavoriteFontListFragment) {
            // ★ عنوان قائمة المفضلة في الدرج ★
            return mContext.getString(R.string.drawer_favorites);
        }
        return "";
    }

    // ════════════════════════════════════════════════════════
    //  التحديد
    // ════════════════════════════════════════════════════════

    /**
     * ★ تحديد العنصر المختار بناءً على فهرس الفراغمنت ★
     *
     * يستقبل fragmentIndex (0–4) من NavManager ويحوّله إلى موضع العرض داخلياً،
     * بحيث يظل الاستدعاء في NavManager دون تغيير:
     *     mHost.getDrawerAdapter().setSelectedItem(1); // ← FontViewer
     *
     * @param fragmentIndex الفهرس الفعلي للفراغمنت في قائمة NavManager
     */
    public void setSelectedItem(int fragmentIndex) {
        int newDisplayPos = toDisplayPos(fragmentIndex);
        if (newDisplayPos < 0) return;

        int prev = mSelectedDisplayPos;
        mSelectedDisplayPos = newDisplayPos;

        // تحديث العنصرين المتأثرين فقط بدلاً من تحديث القائمة كاملاً
        if (prev != newDisplayPos) {
            notifyItemChanged(prev);
            notifyItemChanged(newDisplayPos);
        }
    }

    public int getSelectedPosition() {
        // ★ إعادة الفهرس الفعلي للفراغمنت (fragmentIndex) لا موضع العرض ★
        return toFragmentIndex(mSelectedDisplayPos);
    }
                }
