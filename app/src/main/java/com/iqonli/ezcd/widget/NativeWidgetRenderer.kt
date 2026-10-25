// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.*
import android.text.style.TextAppearanceSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownStatus
import com.iqonli.ezcd.countdown.EventCountResolver
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.OpaqueColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Only framework RemoteViews, TextViews and a shape drawable cross the launcher boundary. */
internal object NativeWidgetRenderer {
    fun resolveColor(raw: String, fallback: Int): Int = if (raw == "default") OpaqueColor.WHITE else if (raw == "auto") fallback else WidgetColor.parse(raw) ?: fallback
    fun box(e: WidgetTextElement,w: Int,h: Int): Rect {
        val width=(w*e.width/100).roundToInt().coerceAtLeast(1)
        val height=(h*e.height/100).roundToInt().coerceAtLeast(1)
        val left=(w-width)/2; val top=(h*e.top/100-height/2.0).roundToInt()
        return Rect(left,top,left+width,top+height)
    }
    fun color(e: WidgetTextElement,layout: WidgetSizeLayout,event: CountdownEvent?): Int = when(e.color) {
        "auto" -> event?.countColor ?: OpaqueColor.BLUE
        "default" -> if(e.source=="count") OpaqueColor.BLUE else OpaqueColor.foreground(resolveColor(layout.background,event?.cardColor ?: OpaqueColor.WHITE))
        else -> WidgetColor.parse(e.color) ?: OpaqueColor.BLUE
    }
    fun remoteViews(context: Context,event: CountdownEvent?,today: LocalDate,layout: WidgetSizeLayout,widthDp: Int,heightDp: Int,
        placeholderTitle: String=context.getString(R.string.widget_select_event)): RemoteViews {
        val metrics=context.resources.displayMetrics
        val w=(widthDp*metrics.density).roundToInt().coerceAtLeast(1); val h=(heightDp*metrics.density).roundToInt().coerceAtLeast(1)
        val bg=resolveColor(layout.background,event?.cardColor ?: OpaqueColor.WHITE)
        val texts=texts(context,event,today,placeholderTitle)
        return RemoteViews(context.packageName,R.layout.widget_native).apply {
            setInt(R.id.nativeBackground,"setColorFilter",bg or 0xFF000000.toInt())
            setInt(R.id.nativeBackground,"setImageAlpha",bg ushr 24)
            removeAllViews(R.id.nativeTexts)
            layout.elements.filter { it.visible }.forEach { e ->
                val text=if (e.source == "custom") e.content else texts.getValue(e.source)
                if (text.isNotEmpty()) {
                    val rect=box(e,w,h)
                    val child=RemoteViews(context.packageName,if ('\n' in text) R.layout.widget_native_multiline else R.layout.widget_native_text)
                    child.setViewPadding(R.id.nativeBox,rect.left,rect.top,w-rect.right,h-rect.bottom)
                    child.setTextViewText(R.id.nativeText,styled(context,text,e.weight))
                    child.setTextColor(R.id.nativeText,color(e,layout,event))
                    child.setInt(R.id.nativeText,"setGravity",gravity(e.alignment))
                    child.setTextViewTextSize(R.id.nativeText,TypedValue.COMPLEX_UNIT_PX,fit(context,text,e,rect.width(),rect.height()))
                    addView(R.id.nativeTexts,child)
                }
            }
            setContentDescription(R.id.widgetRoot,if (event == null) "$placeholderTitle, -" else WidgetEventContentFactory.from(event,today).description(context))
        }
    }
    internal fun texts(context: Context,event: CountdownEvent?,today: LocalDate,placeholderTitle: String=context.getString(R.string.widget_select_event)): Map<String,String> {
        val value=event?.let { EventCountResolver.resolve(it,today) }
        return mapOf("title" to (event?.title ?: placeholderTitle),"count" to (value?.magnitude?.toString() ?: "-"),
            "status" to (value?.countdownStatus?.let { com.iqonli.ezcd.ui.TextPool.status(context,it) } ?: ""),
            "date" to (value?.displayDate?.let { com.iqonli.ezcd.ui.TextPool.date(context,it) } ?: ""))
    }
    private val fontPaints=android.util.LruCache<Int,TextPaint>(64)
    @Synchronized internal fun paint(context: Context,weight: Int): TextPaint {
        val existing=fontPaints.get(weight)
        if(existing!=null) return TextPaint(existing)
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface=Typeface.SANS_SERIF
            if(Build.VERSION.SDK_INT>=29) TextAppearanceSpan(context,WidgetWeightStyles.resources[weight-1]).updateMeasureState(this)
            else typeface=Typeface.create(family(weight),if(weight in 650..849) Typeface.BOLD else Typeface.NORMAL)
        }
        fontPaints.put(weight,p);return TextPaint(p)
    }
    private fun family(weight: Int)=when { weight <= 200 -> "sans-serif-thin"; weight <= 350 -> "sans-serif-light"; weight >= 850 -> "sans-serif-black"; weight >= 500 -> "sans-serif-medium"; else -> "sans-serif" }
    internal fun styled(context: Context,text: String,weight: Int): CharSequence = SpannableString(text).apply {
        // NORMAL StyleSpan ignores weight adjustments; BOLD also fakes bold on light fonts.
        // A platform text appearance parcels the family and variable weight axis instead.
        if (Build.VERSION.SDK_INT >= 29) setSpan(TextAppearanceSpan(context,WidgetWeightStyles.resources[weight-1]),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        else {
            setSpan(TypefaceSpan(family(weight)),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (weight in 650..849) setSpan(StyleSpan(Typeface.BOLD),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    internal fun gravity(a: String): Int = (when { a.endsWith("left") -> Gravity.LEFT; a.endsWith("right") -> Gravity.RIGHT; else -> Gravity.CENTER_HORIZONTAL }) or
        (when { a.startsWith("top") -> Gravity.TOP; a.startsWith("bottom") -> Gravity.BOTTOM; else -> Gravity.CENTER_VERTICAL })
    internal fun fit(context: Context,text: String,e: WidgetTextElement,width: Int,height: Int): Float {
        val paint=paint(context,e.weight)
        fun fits(size: Float): Boolean {
            paint.textSize=size
            if ('\n' !in text) return paint.measureText(text) <= width && paint.fontMetrics.run { descent-ascent } <= height
            val l=StaticLayout.Builder.obtain(text,0,text.length,paint,width.coerceAtLeast(1)).setIncludePad(false).build()
            return l.height <= height && (0 until l.lineCount).all { l.getLineWidth(it) <= width }
        }
        var low=.1f; var high=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,e.maxSize.toFloat(),context.resources.displayMetrics)
        if(fits(high)) return high
        repeat(11) { val middle=(low+high)/2; if (fits(middle)) low=middle else high=middle }
        return low
    }
}

/** Local preview scales an actual RemoteViews tree. Guides never enter the deployed widget. */
internal class NativeWidgetPreview(context: Context): android.view.ViewGroup(context) {
    var archived=false
        set(value) { if(field!=value) { field=value;invalidate() } }
    private val archiveIcon=context.getDrawable(com.iqonli.ezcd.R.drawable.ic_action_save)!!.mutate()
    var showGuides=true
    fun clear() { bitmapToken++;pendingBitmap=null;desiredBitmap=null;shownBitmap=null;removeAllViews();textViews.clear();rendered=null;layout=null;event=null;invalidate() }
    private var logicalWidth=84; private var logicalHeight=84
    private var rendered: View?=null
    private var layout: WidgetSizeLayout?=null
    private var event: CountdownEvent?=null
    private val guide=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=1f; pathEffect=DashPathEffect(floatArrayOf(5f,3f),0f) }
    init { setTag(R.id.font_ui_skip,true);background=AlphaCheckerboard(); setWillNotDraw(false); isClickable=false; isFocusable=false }
    fun show(views: RemoteViews,event: CountdownEvent?,layout: WidgetSizeLayout,w: Int,h: Int) {
        bitmapToken++;pendingBitmap=null;desiredBitmap=null;shownBitmap=null
        this.layout=layout;this.event=event;logicalWidth=w;logicalHeight=h
        val view=views.apply(context,this)
        removeAllViews();rendered=view;addView(view);requestLayout();invalidate()
    }
    private var updatingLocal=false
    override fun requestLayout() { if(!updatingLocal) super.requestLayout() }
    private val textViews=linkedMapOf<String,android.widget.TextView>()
    private data class FitKey(val text: String,val weight: Int,val width: Int,val height: Int,val cap: Double,val density: Float)
    private val fits=object: android.util.LruCache<FitKey,Float>(512*1024) {
        override fun sizeOf(key: FitKey,value: Float)=64+key.text.length*2
    }
    private data class BitmapKey(val event: CountdownEvent?,val today: LocalDate,val doc: WidgetSizeLayout,val w: Int,val h: Int,val revision: Long)
    private data class BitmapRequest(val key: BitmapKey,val token: Int)
    private var bitmapToken=0
    private var bitmapRunning=false
    private var pendingBitmap: BitmapRequest?=null
    private var desiredBitmap: BitmapKey?=null
    private var shownBitmap: BitmapKey?=null
    private fun nextBitmap() {
        val work=pendingBitmap ?: return
        pendingBitmap=null;bitmapRunning=true
        com.iqonli.ezcd.fonts.FontIo.submit({ work.key.let { BitmapWidgetRenderer.render(context.applicationContext,it.event,it.today,it.doc,it.w,it.h) } }) { result ->
            bitmapRunning=false
            result.onSuccess { image ->
                if(work.token!=bitmapToken) image.recycle() else {
                    val view=android.widget.ImageView(context).apply { scaleType=android.widget.ImageView.ScaleType.FIT_XY;setImageBitmap(image) }
                    removeAllViews();textViews.clear();rendered=view;shownBitmap=work.key;addView(view)
                    // Complete the local layout now as well as requesting the next frame.
                    // A parent suppressing layout must not leave the new ImageView at 0 x 0.
                    if(width>0 && height>0) {
                        val d=resources.displayMetrics.density
                        view.measure(MeasureSpec.makeMeasureSpec((logicalWidth*d).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((logicalHeight*d).roundToInt(),MeasureSpec.EXACTLY))
                        layoutRendered()
                    }
                    requestLayout();postInvalidateOnAnimation()
                }
            }
            if(pendingBitmap!=null) nextBitmap()
        }
    }
    override fun onDetachedFromWindow() { bitmapToken++;pendingBitmap=null;super.onDetachedFromWindow() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        desiredBitmap?.let { if(it!=shownBitmap) { pendingBitmap=BitmapRequest(it,++bitmapToken);if(!bitmapRunning) nextBitmap() } }
    }
    fun update(event: CountdownEvent?,today: LocalDate,doc: WidgetSizeLayout,w: Int,h: Int,bitmap: Boolean=false) {
        if(bitmap) {
            val key=BitmapKey(event,today,doc,w,h,com.iqonli.ezcd.fonts.FontRuntime.revision())
            if(key==desiredBitmap && (key==shownBitmap || pendingBitmap!=null || bitmapRunning)) return
            desiredBitmap=key
            this.layout=doc;this.event=event;logicalWidth=w;logicalHeight=h
            pendingBitmap=BitmapRequest(key,++bitmapToken)
            if(!bitmapRunning) nextBitmap()
            return
        }
        bitmapToken++;pendingBitmap=null;desiredBitmap=null;shownBitmap=null
        updatingLocal=true
        try {
        this.layout=doc;this.event=event;logicalWidth=w;logicalHeight=h
        val root=(rendered as? android.widget.FrameLayout)?.takeIf { it.tag=="local-preview" } ?: android.widget.FrameLayout(context).apply {
            tag="local-preview";this@NativeWidgetPreview.removeAllViews();textViews.clear();rendered=this;this@NativeWidgetPreview.addView(this)
        }
        val bg=NativeWidgetRenderer.resolveColor(doc.background,event?.cardColor ?: OpaqueColor.WHITE)
        (root.background as? android.graphics.drawable.GradientDrawable)?.setColor(bg) ?: run {
            root.background=android.graphics.drawable.GradientDrawable().apply { setColor(bg);cornerRadius=com.iqonli.ezcd.ui.NativeUi.dp(context,6).toFloat() }
        }
        val pxW=(w*resources.displayMetrics.density).roundToInt();val pxH=(h*resources.displayMetrics.density).roundToInt()
        val content=NativeWidgetRenderer.texts(context,event,today)
        val visible=doc.elements.filter { it.visible && (if(it.source=="custom") it.content else content.getValue(it.source)).isNotEmpty() }
        val ids=visible.map { it.id }.toSet()
        textViews.keys.filter { it !in ids }.toList().forEach { root.removeView(textViews.remove(it)) }
        visible.forEachIndexed { index,e ->
            val text=if(e.source=="custom") e.content else content.getValue(e.source)
            val tv=textViews.getOrPut(e.id) { android.widget.TextView(context).apply { includeFontPadding=false;setPadding(0,0,0,0);isClickable=false;isFocusable=false;root.addView(this) } }
            val rect=NativeWidgetRenderer.box(e,pxW,pxH)
            val multiline='\n' in text
            if(tv.tag!=Triple(text,e.weight,multiline)) {
                tv.maxLines=if(multiline) Int.MAX_VALUE else 1;tv.setHorizontallyScrolling(false)
                tv.text=NativeWidgetRenderer.styled(context,text,e.weight);tv.tag=Triple(text,e.weight,multiline)
            }
            val key=FitKey(text,e.weight,rect.width(),rect.height(),e.maxSize,resources.displayMetrics.scaledDensity)
            val size=fits.get(key) ?: NativeWidgetRenderer.fit(context,text,e,rect.width(),rect.height()).also { fits.put(key,it) }
            if(tv.textSize!=size) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,size)
            val color=NativeWidgetRenderer.color(e,doc,event);if(tv.currentTextColor!=color) tv.setTextColor(color)
            val gravity=NativeWidgetRenderer.gravity(e.alignment);if(tv.gravity!=gravity) tv.gravity=gravity
            val p=tv.layoutParams as? android.widget.FrameLayout.LayoutParams
            if(p==null || p.width!=rect.width() || p.height!=rect.height() || p.leftMargin!=rect.left || p.topMargin!=rect.top) {
                tv.layoutParams=android.widget.FrameLayout.LayoutParams(rect.width(),rect.height()).apply { leftMargin=rect.left;topMargin=rect.top }
            }
            if(root.indexOfChild(tv)!=index) { root.removeView(tv);root.addView(tv,index) }
        }
        } finally { updatingLocal=false }
        if(width>0 && height>0) {
            val d=resources.displayMetrics.density
            rendered?.measure(MeasureSpec.makeMeasureSpec((logicalWidth*d).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((logicalHeight*d).roundToInt(),MeasureSpec.EXACTLY))
            layoutRendered()
        } else requestLayout()
        invalidate()
    }
    override fun onMeasure(widthSpec: Int,heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),MeasureSpec.getSize(heightSpec))
        val d=resources.displayMetrics.density
        rendered?.measure(MeasureSpec.makeMeasureSpec((logicalWidth*d).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((logicalHeight*d).roundToInt(),MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) { layoutRendered() }
    private fun layoutRendered() {
        rendered?.let { child ->
            val scale=minOf(width.toFloat()/child.measuredWidth,height.toFloat()/child.measuredHeight)
            child.pivotX=0f;child.pivotY=0f;child.scaleX=scale;child.scaleY=scale
            val x=((width-child.measuredWidth*scale)/2).roundToInt(); val y=((height-child.measuredHeight*scale)/2).roundToInt()
            child.layout(x,y,x+child.measuredWidth,y+child.measuredHeight)
        }
    }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if(archived) {
            val inset=com.iqonli.ezcd.ui.NativeUi.dp(context,5)
            val side=com.iqonli.ezcd.ui.NativeUi.dp(context,18)
            archiveIcon.setBounds(width-inset-side,inset,width-inset,inset+side);archiveIcon.draw(canvas)
        }
        if (!showGuides) return
        val child=rendered ?: return;val doc=layout ?: return
        canvas.save();canvas.translate(child.left.toFloat(),child.top.toFloat());canvas.scale(child.scaleX,child.scaleY)
        doc.elements.forEach { e -> guide.color=(NativeWidgetRenderer.color(e,doc,event) and 0xFFFFFF) or (204 shl 24);canvas.drawRect(NativeWidgetRenderer.box(e,child.width,child.height),guide) }
        canvas.restore()
    }
}
