package com.example.oneuiapp.ui.drawer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

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
 */
public class DrawerListAdapter extends RecyclerView.Adapter<DrawerListViewHolder> {

    private Context mContext;
    private List<Fragment> mFragments;
    private DrawerListener mListener;
    private int mSelectedPos = 0;

    public interface DrawerListener {
        boolean onDrawerItemSelected(int position);
    }

    public DrawerListAdapter(
            @NonNull Context context, List<Fragment> fragments, DrawerListener listener) {
        mContext = context;
        mFragments = fragments;
        mListener = listener;
    }

    @NonNull
    @Override
    public DrawerListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.drawer_list_item, parent, false);
        return new DrawerListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DrawerListViewHolder holder, int position) {
        Fragment fragment = mFragments.get(position);
        
        int iconRes = getIconForFragment(fragment);
        String title = getTitleForFragment(fragment);
        
        if (iconRes != 0) {
            holder.setIcon(iconRes);
        }
        if (!title.isEmpty()) {
            holder.setTitle(title);
        }

        holder.setSelected(position == mSelectedPos);
        
        holder.itemView.setOnClickListener(v -> {
            final int itemPos = holder.getBindingAdapterPosition();
            
            if (itemPos == RecyclerView.NO_POSITION) {
                return;
            }
            
            boolean selectionChanged = false;
            if (mListener != null) {
                selectionChanged = mListener.onDrawerItemSelected(itemPos);
            }
            
            if (selectionChanged) {
                setSelectedItem(itemPos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mFragments != null ? mFragments.size() : 0;
    }

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
            return R.drawable.ic_android;
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

    /**
     * تحديد العنصر المختار وتحديث العرض
     */
    public void setSelectedItem(int position) {
        if (position < 0 || position >= getItemCount()) {
            return;
        }
        
        int previousPos = mSelectedPos;
        mSelectedPos = position;
        
        if (previousPos != position) {
            notifyItemChanged(previousPos);
            notifyItemChanged(position);
        }
    }

    public int getSelectedPosition() {
        return mSelectedPos;
    }
}
