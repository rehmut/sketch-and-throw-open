package io.github.rehmut.sketchandthrow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

private const val TICK_COUNT = 11

/** A seek bar drawn as a fader: a thin rail above a printed tick scale. */
class FaderSeekBar(context: Context, attrs: AttributeSet?) : SeekBar(context, attrs) {
    private val density = resources.displayMetrics.density
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.scale)
        strokeWidth = density
    }

    init {
        progressDrawable = context.getDrawable(R.drawable.fader_track)
        thumb = context.getDrawable(R.drawable.fader_thumb)
        splitTrack = false
        background = null
        thumbOffset = (7 * density).roundToInt()
        minimumHeight = (44 * density).roundToInt()
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val span = (width - paddingLeft - paddingRight).toFloat()
        val bottom = height - 2 * density
        for (index in 0 until TICK_COUNT) {
            val x = left + span * index / (TICK_COUNT - 1)
            val length = if (index % 5 == 0) 8 * density else 5 * density
            canvas.drawLine(x, bottom - length, x, bottom, tick)
        }
        super.onDraw(canvas)
    }
}

/**
 * Label, live value, fader and end-of-scale labels as one control. Values are
 * held as doubles and snapped to [step].
 */
class FaderControl(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val labelView: TextView
    private val valueView: TextView
    private val seekBar: FaderSeekBar
    private val lowView: TextView
    private val highView: TextView
    private var min = 0.0
    private var max = 100.0
    private var step = 1.0
    private var unit = ""

    var onValueChange: ((value: Double, fromUser: Boolean) -> Unit)? = null
    var onRelease: ((value: Double) -> Unit)? = null
    /** Overrides the default "value unit" readout. */
    var formatter: ((Double) -> String)? = null
        set(value) { field = value; showValue() }

    var value: Double
        get() = (min + seekBar.progress * step).coerceIn(min, max)
        set(newValue) {
            seekBar.progress = ((newValue.coerceIn(min, max) - min) / step).roundToInt()
            showValue()
        }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_fader, this, true)
        labelView = findViewById(R.id.faderLabel)
        valueView = findViewById(R.id.faderValue)
        seekBar = findViewById(R.id.faderBar)
        lowView = findViewById(R.id.faderLow)
        highView = findViewById(R.id.faderHigh)
        val styled = context.obtainStyledAttributes(attrs, R.styleable.FaderControl)
        try {
            labelView.text = styled.getString(R.styleable.FaderControl_faderLabel).orEmpty()
            unit = styled.getString(R.styleable.FaderControl_faderUnit).orEmpty()
            min = styled.getFloat(R.styleable.FaderControl_faderMin, 0f).toDouble()
            max = styled.getFloat(R.styleable.FaderControl_faderMax, 100f).toDouble()
            step = styled.getFloat(R.styleable.FaderControl_faderStep, 1f).toDouble()
                .coerceAtLeast(0.01)
            lowView.text = styled.getString(R.styleable.FaderControl_faderLowLabel)
                ?: formatNumber(min)
            highView.text = styled.getString(R.styleable.FaderControl_faderHighLabel)
                ?: formatNumber(max)
            seekBar.max = ((max - min) / step).roundToInt()
            value = styled.getFloat(R.styleable.FaderControl_faderValue, min.toFloat()).toDouble()
        } finally {
            styled.recycle()
        }
        seekBar.contentDescription = labelView.text
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                showValue()
                onValueChange?.invoke(value, fromUser)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) { onRelease?.invoke(value) }
        })
    }

    fun setLabel(text: CharSequence) {
        labelView.text = text
        seekBar.contentDescription = text
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        seekBar.isEnabled = enabled
        alpha = if (enabled) 1f else 0.45f
    }

    private fun showValue() {
        valueView.text = formatter?.invoke(value) ?: "${formatNumber(value)} $unit".trim()
    }

    private fun formatNumber(number: Double) =
        if (step >= 1.0) number.roundToInt().toString()
        else String.format(Locale.getDefault(), "%.1f", number)
}

/** The perforated panel of a Braun speaker: a quiet texture that carries no content. */
class GrilleView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.scale)
        alpha = 150
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pitch = 12 * density
        val radius = 1.7f * density
        val columns = ((width - pitch) / pitch).toInt()
        val rows = ((height - pitch) / pitch).toInt()
        if (columns < 1 || rows < 1) return
        val startX = (width - (columns - 1) * pitch) / 2f
        val startY = (height - (rows - 1) * pitch) / 2f
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                canvas.drawCircle(startX + column * pitch, startY + row * pitch, radius, dot)
            }
        }
    }
}
