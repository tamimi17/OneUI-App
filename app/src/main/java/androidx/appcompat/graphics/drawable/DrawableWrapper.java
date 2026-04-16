package androidx.appcompat.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SESL Compatibility Shim
 * This class fixes the crash when using SplashScreen with SESL libraries
 * and ensures Ripple effects work correctly by propagating states.
 */
public class DrawableWrapper extends Drawable implements Drawable.Callback {
    private final Drawable mDrawable;

    public DrawableWrapper(@Nullable Drawable drawable) {
        mDrawable = drawable;
        if (mDrawable != null) {
            mDrawable.setCallback(this);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mDrawable != null) mDrawable.draw(canvas);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        if (mDrawable != null) mDrawable.setBounds(bounds);
    }

    @Override
    public void setChangingConfigurations(int configs) {
        if (mDrawable != null) mDrawable.setChangingConfigurations(configs);
    }

    @Override
    public int getChangingConfigurations() {
        return mDrawable != null ? mDrawable.getChangingConfigurations() : 0;
    }

    @Override
    public void setDither(boolean dither) {
        if (mDrawable != null) mDrawable.setDither(dither);
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        if (mDrawable != null) mDrawable.setFilterBitmap(filter);
    }

    @Override
    public void setAlpha(int alpha) {
        if (mDrawable != null) mDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (mDrawable != null) mDrawable.setColorFilter(colorFilter);
    }

    @Override
    public boolean isStateful() {
        return mDrawable != null && mDrawable.isStateful();
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        if (mDrawable != null && mDrawable.isStateful()) {
            boolean changed = mDrawable.setState(state);
            if (changed) {
                invalidateSelf();
            }
            return changed;
        }
        return false;
    }

    @Override
    protected boolean onLevelChange(int level) {
        return mDrawable != null && mDrawable.setLevel(level);
    }

    @Override
    public int getOpacity() {
        return mDrawable != null ? mDrawable.getOpacity() : PixelFormat.UNKNOWN;
    }

    @Override
    public int getIntrinsicWidth() {
        return mDrawable != null ? mDrawable.getIntrinsicWidth() : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return mDrawable != null ? mDrawable.getIntrinsicHeight() : -1;
    }

    // Callback methods for animations and ripples
    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }
}
