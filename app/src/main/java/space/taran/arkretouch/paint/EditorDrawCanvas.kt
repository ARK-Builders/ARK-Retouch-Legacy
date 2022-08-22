package space.taran.arkretouch.paint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import space.taran.arkretouch.R

class EditorDrawCanvas(context: Context, attrs: AttributeSet) :
    View(context, attrs) {
    private var onDrawHistoryListener: OnDrawHistoryListener? = null
    private var rectNew: Rect? = null
    private var offsetY: Float = 0f
    private var mCurX = 0f
    private var mCurY = 0f
    private var mStartX = 0f
    private var mStartY = 0f
    private var mColor = 0
    private var mWasMultitouch = false

    private var mPaths = LinkedHashMap<Path, PaintOptions>()
    private var mPaint = Paint()
    private var mPath = Path()
    private var mPaintOptions = PaintOptions()

    private var backgroundBitmap: Bitmap? = null
    private var mCropSelectionPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.BEVEL
        strokeCap = Paint.Cap.SQUARE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val MIN_ZOOM = 1.0f
    private val MAX_ZOOM = 4.0f
    private val NONE = 0
    private val DRAG = 1
    private val ZOOM = 2
    private var mode: Int = NONE
    private var oldDist = 1f
    private val mid = PointF()
    private val start = PointF()
    init {
        mColor = ContextCompat.getColor(context, R.color.color_primary)
        mPaint.apply {
            color = mColor
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 40f
            isAntiAlias = true
        }
    }

    interface OnDrawHistoryListener {
        fun onDrawHistoryChanged(isDraw: Boolean)
    }

    fun setOnDrawHistoryListener(onDrawHistoryListener: OnDrawHistoryListener) {
        this.onDrawHistoryListener = onDrawHistoryListener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()

        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap!!, 0f, 0f, null)
        }

        for ((key, value) in mPaths) {
            changePaint(value)
            canvas.drawPath(key, mPaint)
        }

        changePaint(mPaintOptions)
        canvas.drawPath(mPath, mPaint)
        rectNew?.let {
            canvas.drawRect(
                it.left.toFloat(), it.top.toFloat(),
                it.right.toFloat(),
                it.bottom.toFloat(), mCropSelectionPaint
            )
            canvas.clipOutRect(
                it.left.toFloat(), it.top.toFloat(),
                it.right.toFloat(),
                it.bottom.toFloat()
            )
            canvas.drawColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.crop_image_view_background,
                    resources.newTheme()
                )
            )
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mWasMultitouch = false
                mStartX = x
                mStartY = y
                actionDown(x, y)
                start.set(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_UP -> mode = NONE
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !mWasMultitouch) {
                    actionMove(x, y)
                } else if (mode == ZOOM && event.pointerCount == 2) {
                    val newDist1 = spacing(event)
                    if (newDist1 > 10f) {
                        var scale = newDist1 / oldDist * scaleX
                        if (scale > MAX_ZOOM) scale =
                            MAX_ZOOM else if (scale < MIN_ZOOM) scale = MIN_ZOOM
                        scaleX = scale
                        scaleY = scale
                        translationX += (event.x - start.x)
                        translationY += (event.y - start.y)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!mWasMultitouch)
                    actionUp()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mWasMultitouch = true
                resetPath()
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    midPoint(mid, event)
                    mode = ZOOM
                }
            }
        }

        invalidate()
        return true
    }

    private fun resetPath() {
        mPath.reset()
        mCurX = 0f
        mCurY = 0f
        mStartX = 0f
        mStartY = 0f
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toInt().toFloat()
    }

    private fun actionDown(x: Float, y: Float) {
        mPath.reset()
        mPath.moveTo(x, y)
        mCurX = x
        mCurY = y
    }

    private fun actionMove(x: Float, y: Float) {
        mPath.quadTo(mCurX, mCurY, (x + mCurX) / 2, (y + mCurY) / 2)
        mCurX = x
        mCurY = y
    }

    private fun actionUp() {
        if (!mWasMultitouch) {
            mPath.lineTo(mCurX, mCurY)

            // draw a dot on click
            if (mStartX == mCurX && mStartY == mCurY) {
                mPath.lineTo(mCurX, mCurY + 2)
                mPath.lineTo(mCurX + 1, mCurY + 2)
                mPath.lineTo(mCurX + 1, mCurY)
            }
        }

        mPaths[mPath] = mPaintOptions
        mPath = Path()
        mPaintOptions = PaintOptions(
            color = mPaintOptions.color,
            alpha = mPaintOptions.alpha,
            strokeWidth = mPaintOptions.strokeWidth
        )

        onDrawHistoryListener?.onDrawHistoryChanged(isCanvasChanged())
    }

    private fun changePaint(paintOptions: PaintOptions) {
        mPaint.color = paintOptions.color
        mPaint.strokeWidth = paintOptions.strokeWidth
        mPaint.alpha = paintOptions.alpha
    }

    fun updateColor(newColor: Int) {
        mPaintOptions.color = newColor
    }

    fun updateBrushSize(newBrushSize: Int) {
        mPaintOptions.strokeWidth =
            resources.getDimension(R.dimen.full_brush_size) * (newBrushSize / 100f)
    }


    fun updateBackgroundBitmap(bitmap: Bitmap) {
        backgroundBitmap = bitmap
        offsetY = (height - bitmap.height) / 2f
        invalidate()
    }

    fun clear() {
        mPaths.clear()
        backgroundBitmap?.eraseColor(Color.WHITE)
        layoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT
        layoutParams.height = RelativeLayout.LayoutParams.MATCH_PARENT
        requestLayout()
    }

    fun getBitmap(): Bitmap? {
        if (backgroundBitmap == null) {
            return null
        }
        val rect = this.rectNew
        drawRect()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        draw(canvas)
        drawRect(rect)
        return bitmap
    }

    fun undo() {
        if (mPaths.isEmpty()) {
            return
        }

        val lastKey = mPaths.keys.lastOrNull()
        mPaths.remove(lastKey)
        invalidate()
    }

    fun drawRect(rect: Rect? = null) {
        this.rectNew = rect
        invalidate()
    }

    fun getCropImage(): Bitmap? {
        val bitmap = getBitmap()
        return rectNew?.let { rect ->
            val left = rect.left
            val top = rect.top
            val rectWidth = rect.width()
            val rectHeight = rect.height()
            bitmap?.let {
                Bitmap.createBitmap(
                    it,
                    left,
                    top,
                    rectWidth,
                    rectHeight
                )
            }
        } ?: bitmap
    }

    fun isCanvasChanged(): Boolean {
        return mPaths.isNotEmpty()
    }

    fun updateAlpha(alpha: Int) {
        mPaintOptions.alpha = alpha
    }
}
