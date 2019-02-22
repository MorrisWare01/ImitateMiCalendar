package com.morrisware.imitatemicalendar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.animation.AnimationUtils;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.math.MathUtils;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;

/**
 * Created by mmw on 2019/2/15.
 **/
public class CalendarView extends ViewGroup implements CoordinatorLayout.AttachedBehavior {

    // 当前日期所在行
    private int currentPos = 1;

    private int mTotalLength;

    private int itemHeight;

    public CalendarView(Context context) {
        this(context, null);
    }

    public CalendarView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CalendarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        itemHeight = (int) (context.getResources().getDisplayMetrics().density * 50);
        addView(generateView(context, Color.parseColor("#ff0000")));
        addView(generateView(context, Color.parseColor("#00ff00")));
        addView(generateView(context, Color.parseColor("#ff00ff")));
        addView(generateView(context, Color.parseColor("#0000ff")));
        addView(generateView(context, Color.parseColor("#ffff00")));
    }

    private View generateView(Context context, int color) {
        View view = new View(context);
        view.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, itemHeight));
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
        return Math.min(getHeight() * currentPos / 5, getHeight() - itemHeight);
    }

    @NonNull
    @Override
    public CoordinatorLayout.Behavior getBehavior() {
        return new Behavior();
    }

    public static class Behavior extends ViewOffsetBehavior<CalendarView> {

        private static final int MAX_OFFSET_ANIMATION_DURATION = 600;

        private ValueAnimator mOffsetAnimator;
        private WeakReference<View> mLastNestedScrollingChildRef;

        @Override
        public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull CalendarView child, @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
            final boolean started = (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;

            if (started && mOffsetAnimator != null) {
                // Cancel any offset animation
                mOffsetAnimator.cancel();
            }

            if (child.getTotalScrollRange() == 0) {
                final CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) target.getLayoutParams()).getBehavior();
                if (behavior != null) {
                    behavior.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type);
                }
            }

            // A new nested scroll has started so clear out the previous ref
            mLastNestedScrollingChildRef = null;

            return started;
        }

        @Override
        public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull CalendarView child, @NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
            if (dy < 0 && target.canScrollVertically(-1)) {
                return;
            }
            if (dy != 0) {
                if (child.getTotalScrollRange() == 0) {
                    final CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) target.getLayoutParams()).getBehavior();
                    if (behavior != null) {
                        behavior.onNestedPreScroll(coordinatorLayout, target, child, dx, dy, consumed, type);
                    }
                } else {
                    int min, max;
                    min = -child.getTotalScrollRange();
                    max = 0;
                    int newDy = dy * child.getTotalScrollRange() / (child.getHeight() - child.itemHeight);
                    int consumedY = setHeaderTopBottomOffset(coordinatorLayout, child, getTopAndBottomOffset() - newDy, min, max);
                    consumed[1] = consumedY * (child.getHeight() - child.itemHeight) / child.getTotalScrollRange();
                }
            }
        }

        @Override
        public void onStopNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull CalendarView child, @NonNull View target, int type) {
            if (child.getTotalScrollRange() == 0) {
                final CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) target.getLayoutParams()).getBehavior();
                if (behavior != null) {
                    behavior.onStopNestedScroll(coordinatorLayout, target, child, type);
                }
            } else if (type == ViewCompat.TYPE_TOUCH) {
                snapToChildIfNeeded(coordinatorLayout, child);
            }

            // Keep a reference to the previous nested scrolling child
            mLastNestedScrollingChildRef = new WeakReference<>(target);
        }

        @Override
        public boolean onNestedPreFling(@NonNull CoordinatorLayout coordinatorLayout, @NonNull CalendarView child, @NonNull View target, float velocityX, float velocityY) {
            if (getTopAndBottomOffset() == 0) {
                final CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) target.getLayoutParams()).getBehavior();
                if (behavior != null) {
                    return behavior.onNestedPreFling(coordinatorLayout, target, child, velocityX, velocityY);
                }
            } else if (getTopAndBottomOffset() > -child.getTotalScrollRange()) {
                animateOffsetTo(coordinatorLayout, child, -child.getTotalScrollRange(), velocityY);
                return true;
            }
            return super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY);
        }

        private void snapToChildIfNeeded(CoordinatorLayout coordinatorLayout, CalendarView calendarView) {
            final int offset = getTopAndBottomOffset();

            if (offset <= -calendarView.getTotalScrollRange()) {
                return;
            }

            final int newOffset = offset < (-calendarView.getTotalScrollRange()) / 2
                    ? -calendarView.getTotalScrollRange()
                    : 0;
            animateOffsetTo(coordinatorLayout, calendarView, MathUtils.clamp(newOffset, -calendarView.getTotalScrollRange(), 0), 0);
        }

        private void animateOffsetTo(CoordinatorLayout coordinatorLayout, CalendarView child, final int offset, float velocity) {
            final int distance = Math.abs(getTopAndBottomOffset() - offset);

            final int duration;
            velocity = Math.abs(velocity);
            if (velocity > 0) {
                duration = 3 * Math.round(1000 * (distance / velocity));
            } else {
                final float distanceRatio = (float) distance / child.getHeight();
                duration = (int) ((distanceRatio + 1) * 150);
            }

            animateOffsetWithDuration(coordinatorLayout, child, offset, duration);
        }

        private void animateOffsetWithDuration(final CoordinatorLayout coordinatorLayout, final CalendarView child, int offset, int duration) {
            final int currentOffset = getTopAndBottomOffset();
            if (currentOffset == offset) {
                if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
                    mOffsetAnimator.cancel();
                }
                return;
            }

            if (mOffsetAnimator == null) {
                mOffsetAnimator = new ValueAnimator();
                mOffsetAnimator.setInterpolator(AnimationUtils.DECELERATE_INTERPOLATOR);
                mOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        setHeaderTopBottomOffset(coordinatorLayout, child, (int) animation.getAnimatedValue());
                    }
                });
            } else {
                mOffsetAnimator.cancel();
            }

            mOffsetAnimator.setDuration(Math.min(duration, MAX_OFFSET_ANIMATION_DURATION));
            mOffsetAnimator.setIntValues(currentOffset, offset);
            mOffsetAnimator.start();
        }

        private int setHeaderTopBottomOffset(CoordinatorLayout parent, View header, int newOffset) {
            return setHeaderTopBottomOffset(parent, header, newOffset,
                    Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        private int setHeaderTopBottomOffset(CoordinatorLayout parent, View header, int newOffset,
                                             int minOffset, int maxOffset) {
            final int curOffset = getTopAndBottomOffset();
            int consumed = 0;

            if (minOffset != 0 && curOffset >= minOffset && curOffset <= maxOffset) {
                // If we have some scrolling range, and we're currently within the min and max
                // offsets, calculate a new offset
                newOffset = MathUtils.clamp(newOffset, minOffset, maxOffset);

                if (curOffset != newOffset) {
                    setTopAndBottomOffset(newOffset);
                    // Update how much dy we have consumed
                    consumed = curOffset - newOffset;
                }
            }

            return consumed;
        }
    }

    public static class ScrollingViewBehavior extends ViewOffsetBehavior<View> {

        private static final int MAX_OFFSET_ANIMATION_DURATION = 600;

        private ValueAnimator mOffsetAnimator;

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
                    final Behavior clBehavior = (Behavior) behavior;
                    int extraOffset = 0;
                    if (calendarView.getTotalScrollRange() == 0) {
                        int dy = child.getTop() - calendarView.getBottom();
                        int min, max;
                        min = calendarView.itemHeight - (calendarView.getBottom() - calendarView.getTop());
                        max = 0;
                        int newOffset = MathUtils.clamp(getTopAndBottomOffset() - dy, min, max);
                        if (newOffset != getTopAndBottomOffset()) {
                            extraOffset = newOffset;
                        }
                    } else {
                        extraOffset = clBehavior.getTopAndBottomOffset() * (calendarView.getHeight() - calendarView.itemHeight)
                                / calendarView.getTotalScrollRange() - clBehavior.getTopAndBottomOffset();
                    }
                    setTopAndBottomOffset(getTopAndBottomOffset() + (dependency.getBottom() - child.getTop() + extraOffset));
                }
            }
            return false;
        }

        @Override
        public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
            if (mOffsetAnimator != null) {
                // Cancel any offset animation
                mOffsetAnimator.cancel();
            }
            return super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type);
        }

        @Override
        public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type);
            if (target instanceof CalendarView) {
                CalendarView calendarView = (CalendarView) target;
                int min, max;
                min = calendarView.itemHeight - (calendarView.getBottom() - calendarView.getTop());
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
        public void onStopNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int type) {
            super.onStopNestedScroll(coordinatorLayout, child, target, type);
            if (type == ViewCompat.TYPE_TOUCH) {
                snapToChildIfNeeded(coordinatorLayout, child, 0);
            }
        }

        @Override
        public boolean onNestedPreFling(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, float velocityX, float velocityY) {
            CalendarView calendarView = findFirstDependency(coordinatorLayout.getDependencies(child));
            if (calendarView != null) {
                int min = calendarView.itemHeight - (calendarView.getBottom() - calendarView.getTop());
                if (getTopAndBottomOffset() > min) {
                    snapToChildIfNeeded(coordinatorLayout, child, velocityY);
                    return true;
                }
            }
            return super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY);
        }

        private void snapToChildIfNeeded(CoordinatorLayout coordinatorLayout, View child, float velocityY) {
            CalendarView calendarView = findFirstDependency(coordinatorLayout.getDependencies(child));
            if (calendarView != null) {
                int min = calendarView.itemHeight - (calendarView.getBottom() - calendarView.getTop());
                int max = 0;
                final int offset = getTopAndBottomOffset();
                if (offset <= min) {
                    return;
                }

                final int newOffset = offset < (min + max) / 2 ? min : max;
                animateOffsetTo(coordinatorLayout, child, MathUtils.clamp(newOffset, min, max), velocityY);
            }
        }

        private void animateOffsetTo(CoordinatorLayout coordinatorLayout, View child, final int offset, float velocity) {
            final int distance = Math.abs(getTopAndBottomOffset() - offset);

            final int duration;
            velocity = Math.abs(velocity);
            if (velocity > 0) {
                duration = 3 * Math.round(1000 * (distance / velocity));
            } else {
                final float distanceRatio = (float) distance / child.getHeight();
                duration = (int) ((distanceRatio + 1) * 150);
            }

            animateOffsetWithDuration(coordinatorLayout, child, offset, duration);
        }

        private void animateOffsetWithDuration(final CoordinatorLayout coordinatorLayout, final View child, int offset, int duration) {
            final int currentOffset = getTopAndBottomOffset();
            if (currentOffset == offset) {
                if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
                    mOffsetAnimator.cancel();
                }
                return;
            }

            if (mOffsetAnimator == null) {
                mOffsetAnimator = new ValueAnimator();
                mOffsetAnimator.setInterpolator(AnimationUtils.DECELERATE_INTERPOLATOR);
                mOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        setHeaderTopBottomOffset(coordinatorLayout, child, (int) animation.getAnimatedValue());
                    }
                });
            } else {
                mOffsetAnimator.cancel();
            }

            mOffsetAnimator.setDuration(Math.min(duration, MAX_OFFSET_ANIMATION_DURATION));
            mOffsetAnimator.setIntValues(currentOffset, offset);
            mOffsetAnimator.start();
        }

        private int setHeaderTopBottomOffset(CoordinatorLayout parent, View header, int newOffset) {
            return setHeaderTopBottomOffset(parent, header, newOffset,
                    Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        private int setHeaderTopBottomOffset(CoordinatorLayout parent, View header, int newOffset,
                                             int minOffset, int maxOffset) {
            final int curOffset = getTopAndBottomOffset();
            int consumed = 0;

            if (minOffset != 0 && curOffset >= minOffset && curOffset <= maxOffset) {
                // If we have some scrolling range, and we're currently within the min and max
                // offsets, calculate a new offset
                newOffset = MathUtils.clamp(newOffset, minOffset, maxOffset);

                if (curOffset != newOffset) {
                    setTopAndBottomOffset(newOffset);
                    // Update how much dy we have consumed
                    consumed = curOffset - newOffset;
                }
            }

            return consumed;
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
