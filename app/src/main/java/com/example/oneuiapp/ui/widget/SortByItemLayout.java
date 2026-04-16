package com.example.oneuiapp.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;

import com.example.oneuiapp.R;

public class SortByItemLayout extends LinearLayout {

    private TextView mSortTextView;
    private View mTextContainer;
    private ImageView mOrderIcon;
    private OnSortChangeListener mListener;

    public enum SortType {
        NAME, DATE, SIZE
    }

    private SortType mCurrentSortType = SortType.NAME;
    private boolean mIsAscending = true;

    public interface OnSortChangeListener {
        void onSortChanged(SortType type, boolean ascending);
    }

    public SortByItemLayout(Context context) {
        super(context);
        init(context);
    }

    public SortByItemLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SortByItemLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        setPadding(
            dpToPx(context, 12),
            dpToPx(context, 8),
            dpToPx(context, 12),
            dpToPx(context, 8)
        );

        LayoutInflater.from(context).inflate(R.layout.view_sort_by_item, this, true);

        mSortTextView = findViewById(R.id.sort_current_text);
        mTextContainer = findViewById(R.id.sort_text_container);
        mOrderIcon = findViewById(R.id.sort_order_icon);

        setupClickListeners();

        updateUI();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        
        if (mTextContainer != null) mTextContainer.setEnabled(enabled);
        if (mOrderIcon != null) mOrderIcon.setEnabled(enabled);
        
        setAlpha(enabled ? 1.0f : 0.4f);
    }

    public void setupClickListeners() {
        if (mTextContainer != null) {
            mTextContainer.setClickable(true);
            mTextContainer.setFocusable(true);
            mTextContainer.setOnClickListener(v -> {
                if (isEnabled()) showSortMenu();
            });
        }

        if (mOrderIcon != null) {
            mOrderIcon.setClickable(true);
            mOrderIcon.setFocusable(true);

            mOrderIcon.setOnClickListener(v -> {
                if (isEnabled()) {
                    mIsAscending = !mIsAscending;
                    updateUI();
                    notifyListener();
                }
            });
        }
    }

    private void showSortMenu() {
        if (mTextContainer == null) return;

        PopupMenu popup = new PopupMenu(getContext(), mTextContainer);
        popup.getMenuInflater().inflate(R.menu.menu_sort_options, popup.getMenu());

        if (mCurrentSortType == SortType.NAME) popup.getMenu().findItem(R.id.sort_by_name).setChecked(true);
        else if (mCurrentSortType == SortType.DATE) popup.getMenu().findItem(R.id.sort_by_date).setChecked(true);
        else if (mCurrentSortType == SortType.SIZE) popup.getMenu().findItem(R.id.sort_by_size).setChecked(true);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.sort_by_name) {
                mCurrentSortType = SortType.NAME;
            } else if (id == R.id.sort_by_date) {
                mCurrentSortType = SortType.DATE;
            } else if (id == R.id.sort_by_size) {
                mCurrentSortType = SortType.SIZE;
            }
            updateUI();
            notifyListener();
            return true;
        });

        popup.show();
    }

    private void updateUI() {
        if (mSortTextView == null || mOrderIcon == null) return;

        if (mCurrentSortType == SortType.NAME) {
            mSortTextView.setText(R.string.sort_name);
        } else if (mCurrentSortType == SortType.DATE) {
            mSortTextView.setText(R.string.sort_date);
        } else if (mCurrentSortType == SortType.SIZE) {
            mSortTextView.setText(R.string.sort_size);
        }

        if (mIsAscending) {
            mOrderIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_arrow_up);
            TooltipCompat.setTooltipText(mOrderIcon, getContext().getString(R.string.sort_ascending));
            mOrderIcon.setContentDescription(getContext().getString(R.string.sort_ascending));
        } else {
            mOrderIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_arrow_down);
            TooltipCompat.setTooltipText(mOrderIcon, getContext().getString(R.string.sort_descending));
            mOrderIcon.setContentDescription(getContext().getString(R.string.sort_descending));
        }
    }

    public void setOnSortChangeListener(OnSortChangeListener listener) {
        this.mListener = listener;
    }

    private void notifyListener() {
        if (mListener != null) {
            mListener.onSortChanged(mCurrentSortType, mIsAscending);
        }
    }
    
    public void setSortType(SortType type) {
        this.mCurrentSortType = type;
        updateUI();
    }

    public void setSortAscending(boolean ascending) {
        this.mIsAscending = ascending;
        updateUI();
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()));
    }
}
