package com.example.oneuiapp.fontlist.localfont;

import android.content.res.Configuration;
import android.os.Build;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fontlist.adapter.LocalFontListAdapter;
import com.example.oneuiapp.ui.widget.SortByItemLayout;

import java.util.ArrayList;
import java.util.List;

import dev.oneuiproject.oneui.layout.DrawerLayout;

/**
 * LocalFontSelectionManager - إدارة التحديد المتعدد للخطوط
 */
public class LocalFontSelectionManager {

    private final FragmentActivity activity;
    private final DrawerLayout drawerLayout;
    private final LocalFontListAdapter adapter;
    private final RecyclerView recyclerView;
    private final SortByItemLayout sortBar;
    
    private boolean isSelecting = false;
    private SparseBooleanArray selectedItems = new SparseBooleanArray();
    private boolean checkAllListening = true;
    
    private SelectionActionListener actionListener;
    private OnBackPressedCallback onBackPressedCallback;
    private OnBackInvokedCallback onBackInvokedCallback;

    public interface SelectionActionListener {
        void onRenameRequested(int position);
        void onDeleteRequested(List<Integer> positions);
    }

    public LocalFontSelectionManager(FragmentActivity activity,
        DrawerLayout drawerLayout,
        LocalFontListAdapter adapter,
        RecyclerView recyclerView,
        SortByItemLayout sortBar) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.adapter = adapter;
        this.recyclerView = recyclerView;
        this.sortBar = sortBar;

        setupRecyclerViewListener();
        setupBackHandling();
    }

    public void setActionListener(SelectionActionListener listener) {
        this.actionListener = listener;
    }

    private void setupRecyclerViewListener() {
        recyclerView.seslSetLongPressMultiSelectionListener(
            new RecyclerView.SeslLongPressMultiSelectionListener() {
                @Override
                public void onItemSelected(RecyclerView view, View child, int position, long id) {
                    if (adapter.getItemViewType(position) == LocalFontListAdapter.VIEW_TYPE_FONT) {
                        toggleSelection(position);
                    }
                }

                @Override
                public void onLongPressMultiSelectionStarted(int x, int y) {}

                @Override
                public void onLongPressMultiSelectionEnded(int x, int y) {}
            }
        );
    }

    private void setupBackHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedCallback = () -> {
                if (isSelecting) setSelecting(false);
            };
        }

        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (isSelecting) setSelecting(false);
            }
        };
    }

    public void setSelecting(boolean enabled) {
        if (isSelecting == enabled) return;
        isSelecting = enabled;
        if (enabled) activateSelectionMode();
        else deactivateSelectionMode();
    }

    private void activateSelectionMode() {
        disableSortBar();
        adapter.setSelectionMode(true);

        drawerLayout.getActionModeBottomMenu().clear();
        drawerLayout.setActionModeMenu(R.menu.menu_font_actions);
        drawerLayout.showActionMode();

        drawerLayout.setActionModeMenuListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_delete) {
                handleDeleteAction();
                return true;
            } else if (id == R.id.action_rename) {
                handleRenameAction();
                return true;
            }
            return false;
        });

        drawerLayout.setActionModeCheckboxListener((menuItem, isChecked) -> {
            if (checkAllListening) toggleSelectAll(isChecked);
            updateActionModeUI();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                onBackInvokedCallback
            );
        }
        onBackPressedCallback.setEnabled(true);
    }

    private void deactivateSelectionMode() {
        // ★ التسلسل الحرفي لتطبيق المثال الرسمي:
        // 1. تحديث الـ adapter فوراً (يخفي checkboxes في نفس الـ frame)
        // 2. setActionModeAllSelector → يُخفي الشريط السفلي
        // 3. dismissActionMode → يبدأ أنيميشن تلاشي الـ toolbar
        // الثلاثة تحدث معاً فيُخفق عين المستخدم عن ملاحظة اختفاء الشريط ★
        selectedItems.clear();
        adapter.clearSelection();
        adapter.setSelectionMode(false);

        drawerLayout.setActionModeAllSelector(0, true, false);
        drawerLayout.dismissActionMode();

        enableSortBar();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                onBackInvokedCallback
            );
        }
        onBackPressedCallback.setEnabled(false);
    }

    public void toggleSelection(int position) {
        if (!isSelecting) setSelecting(true);

        if (selectedItems.get(position, false)) selectedItems.delete(position);
        else selectedItems.put(position, true);

        adapter.setItemSelected(position, selectedItems.get(position, false));
        updateActionModeUI();
    }

    private void toggleSelectAll(boolean selectAll) {
        selectedItems.clear();
        int itemCount = adapter.getItemCount();
        // ★ الإصلاح: البدء من 1 لتخطي الـ Header، والانتهاء قبل itemCount - 1 لتخطي الـ Footer ★
        // هذا يمنع احتساب عناصر الهيدر والفوتر ضمن عدد المحدد ويصحح الإجمالي المعروض
        for (int i = 1; i < itemCount - 1; i++) {
            if (selectAll) selectedItems.put(i, true);
            adapter.setItemSelected(i, selectAll);
        }
    }

    private void updateActionModeUI() {
        checkAllListening = false;

        int selectedCount = selectedItems.size();

        // ★ الإصلاح: العدد الفعلي للخطوط هو الإجمالي ناقص 2 (الهيدر والفوتر) ★
        // هذا يضمن أن شريط الـ DrawerLayout يعرض النسبة الصحيحة ويُفعّل "تحديد الكل" بدقة
        int totalCount = adapter.getItemCount() - 2;

        // 1. نُحدّث شريط الـ DrawerLayout بالعدد الجديد (وهذا ما يُشغّل أنيميشن النزول إذا كان العدد 0)
        drawerLayout.setActionModeAllSelector(selectedCount, true, selectedCount == totalCount);

        // 2. ★ الإصلاح الجوهري: نُحدّث الأيقونات والنصوص فقط إذا كان هناك عناصر محددة.
        // أما إذا كان العدد 0، فنتجاهل التحديث لكي لا تختفي الأيقونات فجأة أثناء نزول الشريط! ★
        if (selectedCount > 0) {
            Menu bottomMenu  = drawerLayout.getActionModeBottomMenu();
            Menu toolbarMenu = drawerLayout.getActionModeToolbarMenu();

            MenuItem renameItemBottom  = bottomMenu  != null ? bottomMenu.findItem(R.id.action_rename)  : null;
            MenuItem renameItemToolbar = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_rename) : null;
            MenuItem deleteItemBottom  = bottomMenu  != null ? bottomMenu.findItem(R.id.action_delete)  : null;
            MenuItem deleteItemToolbar = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_delete) : null;

            boolean isSingleSelection = (selectedCount == 1);

            // ★ في الوضع العمودي يظهر Rename في البوتوم بار فقط،
            // أما في الأفقي فيظهر في الـ toolbar عند التحديد الفردي فقط ★
            boolean isPortrait = activity.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;

            if (renameItemBottom  != null) renameItemBottom.setVisible(isSingleSelection);
            if (renameItemToolbar != null) renameItemToolbar.setVisible(!isPortrait && isSingleSelection);

            String deleteText = (selectedCount == totalCount && selectedCount > 1)
                    ? activity.getString(R.string.action_delete_all)
                    : activity.getString(R.string.action_delete);

            if (deleteItemBottom  != null) deleteItemBottom.setTitle(deleteText);
            if (deleteItemToolbar != null) deleteItemToolbar.setTitle(deleteText);
        }

        checkAllListening = true;
    }

    public void refreshActionMode() {
        if (isSelecting) {
            // ★ تأجيل بـ post() لضمان تطبيق updateActionModeUI() بعد أن تُعيد
            // DrawerLayout بناء قائمة الـ action mode عند دوران الجهاز ★
            recyclerView.post(this::updateActionModeUI);
        }
    }

    private void handleRenameAction() {
        if (selectedItems.size() != 1 || actionListener == null) return;
        actionListener.onRenameRequested(selectedItems.keyAt(0));
    }

    private void handleDeleteAction() {
        if (selectedItems.size() == 0 || actionListener == null) return;
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < selectedItems.size(); i++) positions.add(selectedItems.keyAt(i));
        actionListener.onDeleteRequested(positions);
    }

    private void disableSortBar() {
        if (sortBar != null) {
            sortBar.setEnabled(false);
            sortBar.setClickable(false);
            sortBar.setAlpha(0.4f);
        }
    }

    private void enableSortBar() {
        if (sortBar != null) {
            sortBar.setEnabled(true);
            sortBar.setClickable(true);
            sortBar.setAlpha(1.0f);
        }
    }

    public List<Integer> getSelectedPositions() {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < selectedItems.size(); i++) positions.add(selectedItems.keyAt(i));
        return positions;
    }

    public int getSelectedCount()  { return selectedItems.size(); }
    public boolean isSelecting()   { return isSelecting; }

    public OnBackPressedCallback getOnBackPressedCallback() { return onBackPressedCallback; }

    public boolean handleBackPress() {
        if (isSelecting) {
            setSelecting(false);
            return true;
        }
        return false;
    }

    public void cleanup() {
        if (isSelecting) setSelecting(false);
        onBackPressedCallback = null;
        onBackInvokedCallback = null;
        actionListener = null;
    }
                                 }
