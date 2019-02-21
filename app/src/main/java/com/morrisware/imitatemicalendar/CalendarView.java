package com.morrisware.imitatemicalendar;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import com.google.android.material.appbar.AppBarLayout;
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
        return getHeight() / 5;
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

                int newDy = (int) (dy * child.getScrollRate());
                int newOffset = MathUtils.clamp(getTopAndBottomOffset() - newDy, min, max);
                if (getTopAndBottomOffset() != newOffset) {
                    setTopAndBottomOffset(newOffset);
                    newDy = getTopAndBottomOffset() - newOffset;

                    consumed[1] = (int) (newDy / child.getScrollRate());
                }
            }
        }
    }

    public static class ScrollingViewBehavior extends ViewOffsetBehavior<View> {

        private int calendarHeight;

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
                    setTopAndBottomOffset((int) (getTopAndBottomOffset() + (dependency.getBottom() - child.getTop() + (ablBehavior.getTopAndBottomOffset() / calendarView.getScrollRate() - ablBehavior.getTopAndBottomOffset()))));
                }
            }
            return false;
        }

        @Override
        protected void layoutChild(CoordinatorLayout parent, View child, int layoutDirection) {
            super.layoutChild(parent, child, layoutDirection);
            if (calendarHeight == 0) {
                final List<View> dependencies = parent.getDependencies(child);
                for (int i = 0, z = dependencies.size(); i < z; i++) {
                    View view = dependencies.get(i);
                    if (view instanceof CalendarView) {
                        calendarHeight = view.getMeasuredHeight();
                    }
                }
            }
            child.setTop(calendarHeight);
            child.setBottom(child.getBottom() + calendarHeight);
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
