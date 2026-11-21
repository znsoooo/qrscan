package com.znsoooo.qrscan;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * 自定义根布局：在 measure 阶段一次性完成高度分配，保证子 View 总高度严格等于可用高度。
 * 优先级：按钮行、字数行固定不变 → 文本框（最小 90dp）→ 摄像头区域（可压得比宽度矮）。
 * 摄像头画面仍按正方形裁切：TextureView 保持正方形边长，容器变矮时底部由父容器裁剪。
 */
public class AdaptiveLayout extends LinearLayout {
    public AdaptiveLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int availW = widthSize - getPaddingLeft() - getPaddingRight();
        int availH = heightSize - getPaddingTop() - getPaddingBottom();

        View preview = findViewById(R.id.previewContainer);
        View scroll = findViewById(R.id.scrollResult);
        View count = findViewById(R.id.countRow);
        View buttons = findViewById(R.id.buttonRow);
        View texture = findViewById(R.id.preview);

        if (heightMode == MeasureSpec.UNSPECIFIED || availW <= 0 || availH <= 0
                || preview == null || scroll == null || count == null || buttons == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // 1) 先测量固定高度的两行，拿到它们的实际高度
        int childW = MeasureSpec.makeMeasureSpec(availW, MeasureSpec.EXACTLY);
        measureChild(count, childW, heightMeasureSpec);
        measureChild(buttons, childW, heightMeasureSpec);

        LayoutParams lc = (LayoutParams) count.getLayoutParams();
        LayoutParams lb = (LayoutParams) buttons.getLayoutParams();
        LayoutParams lp = (LayoutParams) preview.getLayoutParams();
        LayoutParams ls = (LayoutParams) scroll.getLayoutParams();

        int fixed = count.getMeasuredHeight() + lc.topMargin + lc.bottomMargin
                + buttons.getMeasuredHeight() + lb.topMargin + lb.bottomMargin
                + lp.topMargin + lp.bottomMargin
                + ls.topMargin + ls.bottomMargin;

        int rest = availH - fixed;
        if (rest < 0) rest = 0;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int scrollMin = Math.round(90 * dm.density);
        int square = availW;

        int previewH;
        int scrollH;
        if (rest - square >= scrollMin) {
            // 空间充足：摄像头保持正方形，文本框吃掉剩余空间
            previewH = square;
            scrollH = rest - square;
        } else {
            // 空间不足：文本框压到最小（最低 90dp），其余给摄像头
            scrollH = Math.min(scrollMin, rest);
            previewH = rest - scrollH;
        }

        // 2) 给预览容器和文本框设定精确高度（父类据此 measure，子 View 总和 = 可用高度，不溢出）
        lp.height = previewH;
        ls.height = scrollH;

        // 3) TextureView 始终保持正方形边长；容器变矮时底部被 FrameLayout 的 clipChildren 裁掉
        ViewGroup.LayoutParams lt = texture.getLayoutParams();
        lt.width = availW;
        lt.height = square;

        // 4) 交给父类按新的 LayoutParams 完成最终 measure
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
