package com.lixh.stickynavlibrary;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcelable;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.OverScroller;


public class StickyNavLayout extends LinearLayout implements NestedScrollingParent {
    private static final String TAG = "StickyNavLayout";
    private boolean isAutoScroll = true;
    private float ratio;//滑动的系数
    private int springBack = 200;
    private PanelState panelState = PanelState.EXPANDED;
    View target;
    public static enum PanelState {

        COLLAPSED(0),
        EXPANDED(1),
        SLIDING(2);

        private int asInt;

        PanelState(int i) {
            this.asInt = i;
        }

        static PanelState fromInt(int i) {
            switch (i) {
                case 0:
                    return COLLAPSED;
                case 2:
                    return SLIDING;
                default:
                case 1:
                    return EXPANDED;
            }
        }

        public int toInt() {
            return asInt;
        }
    }
    @Override
    protected Parcelable onSaveInstanceState() {

        Parcelable superState = super.onSaveInstanceState();
        SavedState state = new SavedState(superState);
        state.panelState = panelState.toInt();

        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {

        if (!(state instanceof SavedState)) {
            // FIX #10
            super.onRestoreInstanceState(BaseSavedState.EMPTY_STATE);
            return;
        }

        SavedState s = (SavedState) state;
        super.onRestoreInstanceState(s.getSuperState());

        this.panelState = PanelState.fromInt(s.panelState);
        if (panelState == PanelState.COLLAPSED) {
            closeTopView();
        } else {
            openTopView();
        }
    }

    /**
     * Save the instance state
     */
    private static class SavedState extends BaseSavedState {

        int panelState;

        SavedState(Parcelable superState) {
            super(superState);
        }

    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        this.target = target;
        Log.e(TAG, "onStartNestedScroll");
        return true;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        Log.e(TAG, "onNestedScrollAccepted");
    }

    @Override
    public void onStopNestedScroll(View target) {
        if (target != null && target instanceof RecyclerView || target instanceof ListView) {
            stopScroll();
            this.target = null;
        }
        Log.e(TAG, "onStopNestedScroll");
    }

    public void stopScroll() {
        if (getScrollY() >= 0 && getScrollY() < ratio + springBack) {
            openTopView();
        } else if (getScrollY() > ratio + springBack && getScrollY() <= mTopViewHeight) {
            closeTopView();
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        Log.e(TAG, "onNestedScroll");
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        Log.e(TAG, "onNestedPreScroll");

        boolean hiddenTop = dy > 0 && getScrollY() < mTopViewHeight;//在上面的View之间
        boolean showTop = dy < 0 && getScrollY() >= 0 && !ViewCompat.canScrollVertically(target, -1);

        if (hiddenTop || showTop) {
            scrollBy(0, dy);
            consumed[1] = dy;
        }

    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        Log.e(TAG, "onNestedFling");
        return false;
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        Log.e(TAG, "onNestedPreFling");
        //down - //up+
        if (getScrollY() >= mTopViewHeight) return false;
        fling((int) velocityY);
        return true;
    }

    @Override
    public int getNestedScrollAxes() {
        Log.e(TAG, "getNestedScrollAxes");
        return 0;
    }


    private View mTop;
    private View mBottom;
    private View mNav;
    private ViewPager mViewPager;

    private int mTopViewHeight;

    private OverScroller mScroller;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMaximumVelocity, mMinimumVelocity;

    private float mLastY;
    private boolean mDragging;

    private int topViewId = -1;
    private int navViewId = -1;
    private int bottomViewId = -1;

    public StickyNavLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.VERTICAL);
        // init from attrs
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.StickyNavLayout);
        navViewId = a.getResourceId(R.styleable.StickyNavLayout_navView, -1);
        topViewId = a.getResourceId(R.styleable.StickyNavLayout_topView, -1);
        bottomViewId = a.getResourceId(R.styleable.StickyNavLayout_bottomView, -1);
        mScroller = new OverScroller(context);//内置滚动
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();//判断是否点击还是拖拽
        mMaximumVelocity = ViewConfiguration.get(context)
                .getScaledMaximumFlingVelocity();//得到滑动的最大速度, 以像素/每秒来进行计算
        mMinimumVelocity = ViewConfiguration.get(context)//得到滑动的最小速度, 以像素/每秒来进行计算
                .getScaledMinimumFlingVelocity();

    }

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    //事件拦截，一次事件 从Action_Down 到Action_Up结束，此次事件结束后，下一次事件会重新调用onInterceptTouchEvent
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //拦截的情况
        //1:头部显示，用户向上滑动，头部不断缩小，需要拦截事件，自己处理
        //2:头部不显示，但是listView滚动到了顶部，再向下滑动，头部将要显示，需要拦截事件，自己处理，下滑的过程中，头部不断显示
        int action = ev.getAction();
        float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastY = y;//记录手指点击的Y
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = y - mLastY;//滑动时记录滑动的距离
                if (Math.abs(dy) > mTouchSlop) {//滑动距离大于mTouchSlop才认为时滑动
                    if (target != null && target instanceof RecyclerView || target instanceof ListView) {
                        return false;
                    } else {
                        return true;
                    }
        }
                break;
        }

        return super.

                onInterceptTouchEvent(ev);
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        initVelocityTrackerIfNotExists();

        int action = event.getActionMasked();
        float y = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                Log.e(TAG, "onTouchEvent: ACTION_DOWN");
                if (!mScroller.isFinished())
                    mScroller.abortAnimation();//关闭滚动动画
                //手指每次按下，清空VelocityTracker的状态
                mVelocityTracker.clear();
                //为VelocityTracker添加MotionEvent
                mVelocityTracker.addMovement(event);
                mLastY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                Log.e(TAG, "onTouchEvent: ACTION_MOVE:");
                /**
                 * size=4 表示 拖动的距离为屏幕的高度的1/4
                 */
                float dy = y - mLastY;
                if (!mDragging && Math.abs(dy) > mTouchSlop) {
                    mDragging = true;
                }
                if (mDragging) {
                    //跟随手势移动，用来缩放headerView
                    scrollBy(0, (int) -dy);
                    mLastY = y;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.e(TAG, "onTouchEvent:ACTION_CANCEL");
                mDragging = false;
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_UP:
                Log.e(TAG, "onTouchEvent: ACTION_UP");
                mDragging = false;
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityY = (int) mVelocityTracker.getYVelocity();

                if (Math.abs(velocityY) > mMinimumVelocity) {
                    fling(-velocityY);
                }
                recycleVelocityTracker();
                stopScroll();
                break;
        }

        return mDragging;

    }

    //关闭
    public void closeTopView() {
        panelState = PanelState.COLLAPSED;
        mScroller.startScroll(0, getScrollY(), 0, mTopViewHeight, 400);
        invalidate();
    }

    //打开
    public void openTopView() {
        panelState = PanelState.EXPANDED;
        mScroller.startScroll(0, getScrollY(), 0, -getScrollY() + springBack, 400);
        invalidate();

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() < 3) {
            throw new RuntimeException("Content view must contains two child views at least.");
        }

        if (topViewId != -1 || navViewId != -1 || bottomViewId != -1) {
            throw new IllegalArgumentException("You have set \"topViewId\" , \"navViewId\"bottomViewId");
        }

        if (bottomViewId != -1 && topViewId != -1 && bottomViewId != -1) {
            bindId(this);
        } else {
            mTop = getChildAt(0);
            mNav = getChildAt(1);
            mBottom = getChildAt(2);
        }
    }

    private void bindId(View view) {
        mTop = view.findViewById(topViewId);
        mNav = view.findViewById(navViewId);
        mBottom = view.findViewById(bottomViewId);
        if (!(mBottom instanceof ViewPager)) {
            throw new RuntimeException(
                    "id_stickynavlayout_viewpager show used by ViewPager !");
        }
        mViewPager = (ViewPager) mBottom;
        if (mNav == null) {
            throw new IllegalArgumentException("\"topViewId\" with id = \"@id/"
                    + getResources().getResourceEntryName(topViewId)
                    + "\" has NOT been found. Is a child with that id in this " + getClass().getSimpleName() + "?");
        }
        if (mNav == null) {
            throw new IllegalArgumentException("\"navViewId\" with id = \"@id/"
                    + getResources().getResourceEntryName(navViewId)
                    + "\" has NOT been found. Is a child with that id in this " + getClass().getSimpleName() + "?");
        }

        if (mBottom == null) {
            throw new IllegalArgumentException("\"bottomViewId\" with id = \"@id/"
                    + getResources().getResourceEntryName(bottomViewId)
                    + "\" has NOT been found. Is a child with that id in this "
                    + getClass().getSimpleName()
                    + "?");
        }
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //不限制顶部的高度
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!isInEditMode()) {
            mTop.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            ViewGroup.LayoutParams params = mBottom.getLayoutParams();
            params.height = getMeasuredHeight() - mNav.getMeasuredHeight();
            setMeasuredDimension(getMeasuredWidth(), mTop.getMeasuredHeight() + mNav.getMeasuredHeight() + mBottom.getMeasuredHeight());
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mTopViewHeight = mTop.getMeasuredHeight();
        ratio = (mTopViewHeight - springBack) / 2;
        scrollTo(0, springBack);
    }


    public void fling(int velocityY) {
        mScroller.fling(0, getScrollY(), 0, velocityY, 0, 0, 0, mTopViewHeight + springBack);
        invalidate();
    }

    @Override
    public void scrollTo(int x, int y) {
        Log.e("我是scrollTo1", y + "scrollTo" + getScrollY());
        if (y < 0) {
            y = 0;
        }
        if (y > mTopViewHeight) {
            y = mTopViewHeight;
        }
        if (y != getScrollY()) {
            panelState = PanelState.SLIDING;
            Log.e("我是scrollTo2", y + "scrollTo" + y);
            super.scrollTo(x, y);
        }
    }

    @Override
    public void computeScroll() {
        Log.e("我是computeScroll", "computeScroll");
        if (mScroller.computeScrollOffset()) {
            Log.e("我是computeScroll", "computeScrollOffset");
            scrollTo(0, mScroller.getCurrY());
            invalidate();
        }

    }


}
