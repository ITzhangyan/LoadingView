package com.example.loadinganim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

/**
 * Author:${YAN}
 * Time:2019/8/7 0007 上午 9:17
 * Description:
 */
public class LoadingView extends SurfaceView implements SurfaceHolder.Callback, Runnable {




    //小球状态枚举
    private enum LoadingState {
        DOWN, UP, FREE
    }

    private float defaultDistance = 80;//默认小球到顶点的距离
    private float ballRadius = 10;//小球半径
    //默认小球状态是下降
    private LoadingState loadingState = LoadingState.DOWN;
    private Canvas canvas;
    private Paint paint;
    private Path path;

    private int ballColor;//小球颜色
    private int lineColor;//线的颜色
    private int lineWidth;//线的宽度
    private int strokeWidth;//线的粗细

    private float downDistance;//表示小球和线一起下移距离
    private float upDistance;//小球和线一起上移距离
    private float freeDownDistance;//小球自己上移做自由落体运动的偏移量

    private ValueAnimator downControl;//上移动画
    private ValueAnimator upControl;//下移动画
    private ValueAnimator freeDownControl;//小球自由落体动画

    private AnimatorSet animatorSet;//存放所以动画的集合
    private boolean isRunning;//动画结束把子线程关闭标志位
    private boolean isAnimationShowing;//动画是否执行标志位
    private SurfaceHolder surfaceHolder;

    public LoadingView(Context context) {
        this(context, null);
    }

    public LoadingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    /**
     * 初始化数据
     *
     * @param context
     * @param attrs
     */
    private void init(Context context, AttributeSet attrs) {
        initAttr(context, attrs);

        initView();
    }

    private void initView() {
        paint = new Paint();
        paint.setAntiAlias(true);//抗锯齿
        paint.setStrokeWidth(strokeWidth);//画笔宽度
        path = new Path();
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);

        //初始化动画控制器
        initControl();
    }

    /**
     * 初始化动画控制器
     */
    private void initControl() {
        //小球下降
        downControl = ValueAnimator.ofFloat(0, 1);
        downControl.setDuration(500);
        downControl.setInterpolator(new DecelerateInterpolator());//插值器，速度越来越慢
        downControl.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                //下降距离
                downDistance = defaultDistance * (float) animation.getAnimatedValue();
            }
        });
        downControl.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                //修改状态
                loadingState = LoadingState.DOWN;

                isAnimationShowing = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
            }

        });

        //小球上升
        upControl = ValueAnimator.ofFloat(0, 1);
        upControl.setDuration(500);
        upControl.setInterpolator(new ShockInterpolator());//插值器，速度越来越快
        upControl.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                upDistance = defaultDistance * (float) animation.getAnimatedValue();
                //如果小球上升的距离大于等于默认高度，就表示是小球需要上抛运动，开启自由落体动画
                if (upDistance >= defaultDistance && !freeDownControl.isStarted() && !freeDownControl.isRunning()) {
                    freeDownControl.start();
                }

            }
        });
        upControl.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                //改变状态
                loadingState = LoadingState.UP;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
            }

        });

        //小球竖直商抛之后做自由落体，相当于两次
        /**
         * 计算公式
         * h = v0 * t - 1/2 * g * t²
         * 我们将ofFloat的终点值作为t
         * v0初始速度，为0 ，可得出公式：h = 1/2 * g * t²
         * 我们默认h=50，g是重力加速度，约等于9.8，这里给10，计算出t = √10 √表示根号
         */
        freeDownControl = ValueAnimator.ofFloat(0, (float) (2 * Math.sqrt(10)));
        freeDownControl.setDuration(600);
        freeDownControl.setInterpolator(new LinearInterpolator());//插值器，匀速
        freeDownControl.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {

                float t = (float) animation.getAnimatedValue();

                freeDownDistance = (float) (10 * Math.sqrt(10) * t - 5 * t * t);

            }
        });
        freeDownControl.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                //改变状态
                loadingState = LoadingState.FREE;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                isAnimationShowing = false;//动画结束

                startAllAnimation();
            }

        });

        animatorSet = new AnimatorSet();
        animatorSet.play(downControl).before(upControl);
    }

    /**
     * 初始化attr
     *
     * @param context
     * @param attrs
     */
    private void initAttr(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.LoadingView);
        ballColor = typedArray.getColor(R.styleable.LoadingView_ball_color, Color.RED);
        lineColor = typedArray.getColor(R.styleable.LoadingView_line_color, Color.RED);
        lineWidth = typedArray.getDimensionPixelOffset(R.styleable.LoadingView_line_width, 200);
        strokeWidth = typedArray.getDimensionPixelOffset(R.styleable.LoadingView_stroke_width, 2);
        //回收
        typedArray.recycle();
    }

    /**
     * surface被创建
     *
     * @param holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isRunning = true;
        //开启线程
        new Thread(this).start();
    }

    /**
     * surface属性发生变化
     *
     * @param holder
     * @param format
     * @param width
     * @param height
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    /**
     * surface被销毁
     *
     * @param holder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
    }

    @Override
    public void run() {
        while (isRunning) {
            drawView();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 绘制小球和线，以及路径等
     */
    private void drawView() {
        try {

            if (surfaceHolder != null) {
                //获取canvas
                canvas = surfaceHolder.lockCanvas();
                //清屏
                canvas.drawColor(Color.WHITE);

                //做绘制工作
                paint.setColor(lineColor);//画笔颜色
                path.reset();//重置
                //设置起点位置
                //x：屏幕一半的宽度 - 线一半的宽度
                //y：屏幕一半的高度
                path.moveTo(getWidth() / 2 - lineWidth / 2,getHeight() / 2);
                if (loadingState == LoadingState.DOWN){
                    //下降，小球还在绳子上

                    /**
                     *贝塞尔曲线公式
                     * B(t) = (1 - t)²P0 + 2t(1 - t)P1 + t²P2 ，t∈[0,1]之间 ∈表示属于
                     *0.5 = 0.5²p0 + 0.5p1 + 0.5²p2
                     * t=0.5  中间的小球，，所以t=0.5
                     * cp[1].x = (cp[0].x + cp[2].x) / 2; 即连线中点
                     * float c0 = (1 - t) * (1 - t);  0.25
                     * float c1 = 2 * t * (1 - t);    0.5
                     * float c2 = t * t;              0.25
                     * growX = c0 * cp[0].x + c1 * cp[1].x + c2 * cp[2].x; cp[0].x表示起始点x  cp[2].x 终点x cp[1].x控制点x坐标
                     * growY = c0 * cp[0].y + c1 * cp[1].y + c2 * cp[2].y; cp[0].y表示起始点y  cp[2].y 终点y cp[1].y控制点y坐标
                     * cp[1].y = (growY - 0.5cp[0].y) * 2
                     */
                    path.rQuadTo(lineWidth/2,2 * downDistance,lineWidth,0);
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawPath(path,paint);

                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(getWidth()/2 ,getHeight() / 2 + downDistance - ballRadius - strokeWidth / 2,ballRadius,paint);//中间的球


                }else {
                    //上升以及自由落体的情况
                    path.rQuadTo(lineWidth/2,2 * (defaultDistance - upDistance),lineWidth,0);
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawPath(path,paint);

                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.FILL);

                    if (loadingState == LoadingState.FREE){
                        //自由落体
                        canvas.drawCircle(getWidth()/2 ,getHeight() / 2 - freeDownDistance - ballRadius - strokeWidth / 2,ballRadius,paint);//中间的球

                    }else {
                        //小球上升
                        canvas.drawCircle(getWidth()/2 ,getHeight() / 2 + (defaultDistance - upDistance) - ballRadius - strokeWidth / 2,ballRadius,paint);//中间的球

                    }
                }

                //绘制小球
                drawBall();

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //提交
            if (canvas != null && surfaceHolder != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }

    }

    private void drawBall() {
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(getWidth()/2 - lineWidth / 2,getHeight() / 2,10,paint);//第一个球
        canvas.drawCircle(getWidth()/2 + lineWidth / 2,getHeight() / 2,10,paint);//第二个球

    }

    public void startAllAnimation() {
        if (isAnimationShowing) {
            return;
        }
        if (animatorSet.isRunning()) {
            animatorSet.end();
            animatorSet.cancel();
        }

        //开启组合动画
        animatorSet.start();
    }

    //线震荡曲线
    //公式：f(x) = 1 - Math.exp(-3 * input) *  Math.cos(10 * input)
    class ShockInterpolator implements Interpolator{

        @Override
        public float getInterpolation(float input) {
            return (float)(1 - Math.exp(-3 * input) *  Math.cos(10 * input));
        }
    }
}
