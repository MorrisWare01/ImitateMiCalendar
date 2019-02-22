package com.morrisware.imitatemicalendar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.math.MathUtils;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by mmw on 2019/2/15.
 **/
public class CalendarView extends ViewGroup implements CoordinatorLayout.AttachedBehavior {

    public static final int ITEM_HEIGHT = 60;

    private int currentPos = 4;
    private int mTotalLength;

    public CalendarView(Context context) {
        this(context, null);
    }

    public CalendarView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CalendarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        addView(generateView(context, Color.parseColor("#ff0000")));
        addView(generateView(context, Color.parseColor("#00ff00")));
        addView(generateView(context, Color.parseColor("#ff00ff")));
        addView(generateView(context, Color.parseColor("#0000ff")));
        addView(generateView(context, Color.parseColor("#ffff00")));
    }

    private View generateView(Context context, int color) {
        View view = new View(context);
        view.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, ITEM_HEIGHT));
        view.setBackgroundColor(color);
        return view;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int specWidthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int specHeightSize = MeasureSpec.getSize(heightMeasureSpec);

        mTotalLength = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            mTotalLength += child.getMeasuredHeight();
        }

        setMeasuredDimension(specWidthSize, Math.min(mTotalLength, specHeightSize));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();

        final int parentLeft = getPaddingLeft();
        final int parentWidth = r - l - parentLeft - getPaddingRight();
        final int parentHeight = b - t - getPaddingTop() - getPaddingBottom();

        int childTop = getPaddingTop() + currentPos * (parentHeight - mTotalLength) / (count - 1);

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }

            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();

            int delta = (parentWidth - width) / 2;
            int childLeft = parentLeft + delta;

            child.layout(childLeft, childTop, childLeft + width, childTop + height);

            childTop += height;
        }
    }

    private int getTotalScrollRange() {
        return Math.min(0, getHeight() - ITEM_HEIGHT);
    }

    private float getScrollRate() {
        return getTotalScrollRange() * 1.0f / (getHeight() - ITEM_HEIGHT);
    }

    @NonNull
    @Override
    public CoordinatorLayout.Behavior getBehavior() {
        return new Behavior();
    }

    public static class Behavior extends ViewOffsetBehavior<CalendarView> {

        private int offsetDelta;

        @Override
        public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull CalendarView child, @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
            return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        }

        @Override
        public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull CalendarView child, @NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
            if (dy < 0 && target.canScrollVertically(-1)) {
                return;
            }

            if (dy != 0) {
                int min, max;
                min = -child.getTotalScrollRange();
                max = 0;

                if (min != 0) {
                    int newDy = (int) (dy * child.getScrollRate());
                    int newOffset = MathUtils.clamp(getTopAndBottomOffset() - newDy, min, max);
                    if (getTopAndBottomOffset() != newOffset) {
                        final int curOffset = getTopAndBottomOffset();
                        setTopAndBottomOffset(newOffset);
                        newDy = curOffset - newOffset;
                        consumed[1] = (int) (newDy / child.getScrollRate());
                    }
                } else {
                    final CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) target.getLayoutParams()).getBehavior();
                    if (behavior != null) {
                        behavior.onNestedPreScroll(coordinatorLayout, target, child, dx, dy, consumed, type);
                    }
                }
            }
        }
    }

    public static class ScrollingViewBehavior extends ViewOffsetBehavior<View> {

        public ScrollingViewBehavior(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean layoutDependsOn(@NotNull CoordinatorLayout parent, @NotNull View child, @NotNull View dependency) {
            return dependency instanceof CalendarView;
        }

        @Override
        public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
            final CalendarView calendarView = findFirstDependency(parent.getDependencies(child));
            if (calendarView != null) {
                final CoordinatorLayout.Behavior behavior =
                        ((CoordinatorLayout.LayoutParams) calendarView.getLayoutParams()).getBehavior();
                if (behavior instanceof Behavior) {
                    final Behavior ablBehavior = (Behavior) behavior;
                    setTopAndBottomOffset((int) (getTopAndBottomOffset() + (dependency.getBottom() - child.getTop()
                            + (ablBehavior.getTopAndBottomOffset() / calendarView.getScrollRate() - ablBehavior.getTopAndBottomOffset()))));
                }
            }
            return false;
        }

        @Override
        public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type);
            if (target instanceof CalendarView) {
                CalendarView calendarView = (CalendarView) target;
                int min, max;
                min = ITEM_HEIGHT - (calendarView.getBottom() - calendarView.getTop());
                max = 0;
                int newOffset = MathUtils.clamp(getTopAndBottomOffset() - dy, min, max);
                if (newOffset != getTopAndBottomOffset()) {
                    final int curOffset = getTopAndBottomOffset();
                    setTopAndBottomOffset(newOffset);
                    consumed[1] = curOffset - newOffset;
                }
            }
        }

        @Override
        protected void layoutChild(CoordinatorLayout parent, View child, int layoutDirection) {
            super.layoutChild(parent, child, layoutDirection);
            final List<View> dependencies = parent.getDependencies(child);
            final View header = findFirstDependency(dependencies);
            if (header != null) {
                final CoordinatorLayout.LayoutParams lp =
                        (CoordinatorLayout.LayoutParams) child.getLayoutParams();

                final Rect available = new Rect();
                available.set(parent.getPaddingLeft() + lp.leftMargin,
                        header.getBottom() + lp.topMargin,
                        parent.getWidth() - parent.getPaddingRight() - lp.rightMargin,
                        parent.getHeight() + header.getBottom()
                                - parent.getPaddingBottom() - lp.bottomMargin);

                final Rect out = new Rect();
                GravityCompat.apply(resolveGravity(lp.gravity), child.getMeasuredWidth(),
                        child.getMeasuredHeight(), available, out, layoutDirection);
                child.layout(out.left, out.top, out.right, out.bottom);
            }
        }

        private static int resolveGravity(int gravity) {
            return gravity == Gravity.NO_GRAVITY ? GravityCompat.START | Gravity.TOP : gravity;
        }

        CalendarView findFirstDependency(List<View> views) {
            for (int i = 0, z = views.size(); i < z; i++) {
                View view = views.get(i);
                if (view instanceof CalendarView) {
                    return (CalendarView) view;
                }
            }
            return null;
        }

    }

}
