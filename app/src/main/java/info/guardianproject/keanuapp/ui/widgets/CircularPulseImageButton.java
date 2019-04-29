package info.guardianproject.keanuapp.ui.widgets;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.util.TypedValue;

import info.guardianproject.keanuapp.R;

public class CircularPulseImageButton extends AppCompatImageButton {
    private Paint paint = new Paint();
    private int lineWidth;
    private int color;
    private int durationExpand;
    private int durationGap;
    private long animationStep;
    private boolean animating;

    public CircularPulseImageButton(Context context) {
        super(context);
        init(null);
    }

    public CircularPulseImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CircularPulseImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setWillNotDraw(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);

        color = Color.parseColor("#ffffffff");
        lineWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CircularPulseImageButton);
            if (a != null) {
                lineWidth = a.getDimensionPixelSize(R.styleable.CircularPulseImageButton_lineWidth, lineWidth);
                color = a.getColor(R.styleable.CircularPulseImageButton_color, color);
                durationExpand = a.getInteger(R.styleable.CircularPulseImageButton_durationExpand, 1000);
                durationGap = a.getInteger(R.styleable.CircularPulseImageButton_durationGap, 700);
                a.recycle();
            }
        }
    }

    public boolean isAnimating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w, h);
        paint.setShader(new RadialGradient(w/2.0f, h/2.0f,size / 2.0f,
                new int[] { color, Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))}, new float[] { getMinimumWidth() / size, 1.0f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isAnimating()) {
            long drawingTime = getDrawingTime();

            // Initialize
            if (animationStep == 0) {
                animationStep = drawingTime;
            }

            // Don't draw pulses that are already expanded
            while (animationStep + durationExpand < drawingTime) {
                animationStep += durationGap;
            }

            long circleTime = animationStep;
            while (circleTime < drawingTime) {

                float fraction = (float) (getDrawingTime() - circleTime) / (float) durationExpand;

                int maxPos = Math.min(getWidth(), getHeight()) - lineWidth;
                int minPos = getMinimumWidth() + lineWidth;

                float maxLineWidth = lineWidth;
                float minLineWidth = (float) getMinimumWidth() / (float) Math.min(getWidth(), getHeight()) * lineWidth;

                float size = minPos + fraction * (maxPos - minPos);
                float width = minLineWidth + fraction * (maxLineWidth - minLineWidth);
                paint.setStrokeWidth(width);
                canvas.drawCircle(getWidth() / 2.0f, getHeight() / 2.0f, size / 2.0f, paint);

                circleTime += durationGap;
            }
            invalidate();
        }
    }
}