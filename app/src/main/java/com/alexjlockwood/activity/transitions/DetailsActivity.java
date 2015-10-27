package com.alexjlockwood.activity.transitions;

import android.app.Activity;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewPager;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import java.util.List;
import java.util.Map;

import static com.alexjlockwood.activity.transitions.MainActivity.EXTRA_CURRENT_ITEM_POSITION;
import static com.alexjlockwood.activity.transitions.MainActivity.EXTRA_OLD_ITEM_POSITION;

public class DetailsActivity extends Activity implements ViewPager.OnPageChangeListener {
    private static final String TAG = "DetailsActivity";
    private static final boolean DEBUG = true;

    private static final String STATE_CURRENT_POSITION = "state_current_position";
    private static final String STATE_OLD_POSITION = "state_old_position";

    private DetailsFragmentPagerAdapter mAdapter;
    private int mCurrentPosition;
    private int mOriginalPosition;
    private boolean mIsReturning;
    /**
     * 每次进入和退出都会回调SharedElementCallback 判断是进入还是退出在finishAfterTransition中进行判断
     * onMapSharedElements是装载共享元素  供下面的使用
     * onSharedElementStart 是共享元素开始时候回调  一般是进入的时候使用 !mIsReturning
     * onSharedElementEnd  是共享元素结束的时候回调  一般是退出的时候使用 mIsReturning
     */
    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            LOG("onMapSharedElements(List<String>, Map<String, View>)", mIsReturning);
            if (mIsReturning) {
                View sharedView = mAdapter.getCurrentDetailsFragment().getSharedElement();
                if (sharedView == null) {
                    // If shared view is null, then it has likely been scrolled off screen and
                    // recycled. In this case we cancel the shared element transition by
                    // removing the shared elements from the shared elements map.
                    names.clear();
                    sharedElements.clear();
                } else if (mCurrentPosition != mOriginalPosition) {
                    names.clear();
                    sharedElements.clear();
                    names.add(sharedView.getTransitionName());
                    sharedElements.put(sharedView.getTransitionName(), sharedView);
                }
            }

            LOG("=== names: " + names.toString(), mIsReturning);
            LOG("=== sharedElements: " + Utils.setToString(sharedElements.keySet()), mIsReturning);
        }

        @Override
        public void onSharedElementStart(List<String> sharedElementNames, List<View> sharedElements,
                                         List<View> sharedElementSnapshots) {
            LOG("onSharedElementStart(List<String>, List<View>, List<View>)", mIsReturning);
            if (!mIsReturning) {
                getWindow().setEnterTransition(makeEnterTransition(getSharedElement(sharedElements)));
            }
        }

        @Override
        public void onSharedElementEnd(List<String> sharedElementNames, List<View> sharedElements,
                                       List<View> sharedElementSnapshots) {
            LOG("onSharedElementEnd(List<String>, List<View>, List<View>)", mIsReturning);
            if (mIsReturning) {
                getWindow().setReturnTransition(makeReturnTransition());
            }
        }

        private View getSharedElement(List<View> sharedElements) {
            for (final View view : sharedElements) {
                if (view instanceof ImageView) {
                    return view;
                }
            }
            return null;
        }
    };

    private Transition makeEnterTransition(View sharedElement) {
        View rootView = mAdapter.getCurrentDetailsFragment().getView();
        assert rootView != null;

        TransitionSet enterTransition = new TransitionSet();

        //圆形展开
        // Play a circular reveal animation starting beneath the shared element.
        Transition circularReveal = new CircularReveal(sharedElement);
        circularReveal.addTarget(rootView.findViewById(R.id.reveal_container));
        enterTransition.addTransition(circularReveal);

        //文字从底部进入
        // Slide the cards in through the bottom of the screen.
        Transition cardSlide = new Slide(Gravity.BOTTOM);
        cardSlide.addTarget(rootView.findViewById(R.id.text_container));
        enterTransition.addTransition(cardSlide);

        //状态栏渐变显示
        // Don't fade the navigation/status bars.
        Transition fade = new Fade();
        fade.excludeTarget(android.R.id.navigationBarBackground, true);
        fade.excludeTarget(android.R.id.statusBarBackground, true);
        enterTransition.addTransition(fade);

        //背景图片渐变显示
        final Resources res = getResources();
        final ImageView backgroundImage = (ImageView) rootView.findViewById(R.id.background_image);
        backgroundImage.setAlpha(0f);
        enterTransition.addListener(new TransitionListenerAdapter() {
            @Override
            public void onTransitionEnd(Transition transition) {
                backgroundImage.animate().alpha(1f).setDuration(res.getInteger(R.integer.image_background_fade_millis));
            }
        });

        enterTransition.setDuration(getResources().getInteger(R.integer.transition_duration_millis));
        return enterTransition;
    }

    private Transition makeReturnTransition() {
        View rootView = mAdapter.getCurrentDetailsFragment().getView();
        assert rootView != null;

        TransitionSet returnTransition = new TransitionSet();

        //上半部分向上渐变消失
        // Slide and fade the circular reveal container off the top of the screen.
        TransitionSet slideFade = new TransitionSet();
        slideFade.addTarget(rootView.findViewById(R.id.reveal_container));
        slideFade.addTransition(new Slide(Gravity.TOP));
        slideFade.addTransition(new Fade());
        returnTransition.addTransition(slideFade);

        //下面的文字向下消失
        // Slide the cards off the bottom of the screen.
        Transition cardSlide = new Slide(Gravity.BOTTOM);
        cardSlide.addTarget(rootView.findViewById(R.id.text_container));
        returnTransition.addTransition(cardSlide);

        returnTransition.setDuration(getResources().getInteger(R.integer.transition_duration_millis));
        return returnTransition;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        postponeEnterTransition();//在第二个里面似乎并不需要去调用start这个方法。。

        /**
         * setEnterSharedElementCallback 如果只是简单的共享元素 其实可以不写这句话 framwork已经帮我们实现好了
         * 这里写这个是因为有时(滑动页面之后)要返回非进入的共享元素 要去回调过去
         * 当然callback也是可以选择实现方法的  一般没有什么效果的只要实现onMapSharedElements就可以了
         * 其他的看情况实现
         */
        setEnterSharedElementCallback(mCallback);

        if (savedInstanceState == null) {
            mCurrentPosition = getIntent().getExtras().getInt(EXTRA_CURRENT_ITEM_POSITION);
            mOriginalPosition = mCurrentPosition;
        } else {
            mCurrentPosition = savedInstanceState.getInt(STATE_CURRENT_POSITION);
            mOriginalPosition = savedInstanceState.getInt(STATE_OLD_POSITION);
        }

        mAdapter = new DetailsFragmentPagerAdapter(getFragmentManager());
        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(mAdapter);
        pager.setOnPageChangeListener(this);
        pager.setCurrentItem(mCurrentPosition);

        //设置回调了  感觉这句话就可以去掉了
        //getWindow().getSharedElementEnterTransition().setDuration(getResources().getInteger(R.integer.transition_duration_millis));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_POSITION, mCurrentPosition);
        outState.putInt(STATE_OLD_POSITION, mOriginalPosition);
    }

    /**
     * 结束返回的时候调用  一般是按后退键
     */
    @Override
    public void finishAfterTransition() {
        LOG("finishAfterTransition()", true);
        mIsReturning = true;
        //getWindow().setReturnTransition(makeReturnTransition());这个也可以省略了
        Intent data = new Intent();
        data.putExtra(EXTRA_OLD_ITEM_POSITION, getIntent().getExtras().getInt(EXTRA_CURRENT_ITEM_POSITION));
        data.putExtra(EXTRA_CURRENT_ITEM_POSITION, mCurrentPosition);
        setResult(RESULT_OK, data);
        super.finishAfterTransition();
    }

    @Override
    public void onPageSelected(int position) {
        mCurrentPosition = position;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // Do nothing.
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // Do nothing.
    }

    private static void LOG(String message, boolean isReturning) {
        if (DEBUG) {
            Log.i(TAG, String.format("%s: %s", isReturning ? "RETURNING" : "ENTERING", message));
        }
    }
}
