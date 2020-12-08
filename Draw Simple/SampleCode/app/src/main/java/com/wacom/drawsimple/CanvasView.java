/**
 * Copyright (c) 2013, 2020 Wacom Technology Corp.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.wacom.drawsimple;

import java.util.HashMap;
import java.util.Map;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.View.OnHoverListener;

/**
 * CanvasView provides a View that can be drawn on by consuming touch,
 * pen, and other MotionEvents. Each device is assigned its own Brush.
 * 
 * @author wacom
 */
public class CanvasView extends View implements OnTouchListener, OnHoverListener {

	Map<Integer,Brush> brushes = new HashMap<Integer,Brush>();
	Brush brush;
	
	Matrix transform, inverse; // Transform between view-space and bitmap-space
	
	Bitmap checker;
	Bitmap layer;         // Layer containing the drawing
	Bitmap overlay;       // Overlay for fill "shadow"
	Canvas layerCanvas;   // Canvas for drawing into 'layer'
	Canvas overlayCanvas; // Canvas for drawing into 'overlay'
	
	PointF grab;
	
	/**
	 * Create a new CanvasView. Note that this constructor does not
	 * initialize the bitmaps. Be sure that one of the two "initBitmaps"
	 * functions is called before "onDraw" is called.
	 * 
	 * @param context
	 * @param attrs
	 */
	public CanvasView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOnTouchListener(this);
		setOnHoverListener(this);
	}
	
	/**
	 * This method is called "when this view should assign a size and
	 * position to all of its children". We use this chance to initialize
	 * the bitmaps based on the view's now-known size.
	 */
	public void onLayout(boolean changed, int left, int top, int right, int bottom) {
		initBitmaps();
	}
	
	/**
	 * This method is called whenever Android requires us to redraw
	 * ourselves. We blit each of the three bitmaps to the provided
	 * canvas in bottom-up order, transforming them by the current
	 * viewport transformation.
	 * 
	 * @param canvas
	 */
	@Override
	public void onDraw(Canvas canvas) {
		canvas.drawBitmap(checker, transform, null);
		canvas.drawBitmap(layer, transform, null);
		canvas.drawBitmap(overlay, transform, null);
	}
	
	/**
	 * This method is called whenever Android has a touch event for us
	 * to process. This occurs whenever a finger or stylus is in contact
	 * with the screen.
	 * 
	 * We respond to these events by attempting to move the viewport,
	 * and drawing both the fill and outline.
	 * 
	 * @param view
	 * @param event
	 */
	@Override
	public boolean onTouch(View view, MotionEvent event) {
		boolean handled = false;
		
		changeTool(event);
		
		if (moveViewport(event))
			return true;
		
		handled |= drawFill(event);
		handled |= drawOutline(event);
		return handled;
	}
	
	/**
	 * This method is called whenever Android has a hover event for us
	 * to process. An active stylus will send hover events while it is
	 * in range of the sensor, but not in contact.
	 * 
	 * We respond to these events by attempting to move the viewport
	 * and redrawing the brush outline.
	 * 
	 * @param v
	 * @param event
	 */
	@Override
	public boolean onHover(View v, MotionEvent event) {
		changeTool(event);
		
		if (moveViewport(event))
			return true;
		
		return drawOutline(event);
	}
	
	/**
	 * Change the color of the active brush.
	 * 
	 * @param color  New brush color (e.g. 0xff000000 or Color.BLACK for black)
	 */
	public void setColor(int color) {
		if (brush == null)
			return;
		
		brush.setColor(color);
	}
	
	/**
	 * Attempt to draw the brush fill to the layer bitmap. This fill will
	 * be drawn so long as a touch is occurring. Once the touch ends, the
	 * active brush will be signaled to stop drawing the stroke, in
	 * preparation for the next stroke.
	 * 
	 * The raw MotionEvent data is transformed from being View-relative to
	 * being viewport-relative.
	 * 
	 * @param event  Event to attempt to use for drawing the fill
	 * @return       'true' if the event is used to draw the fill
	 */
	protected boolean drawFill(MotionEvent event) {
		State states[] = State.getStates(event);
		transformState(states, inverse);
		
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:
				brush.drawFill(layerCanvas, states);
				break;
			
			case MotionEvent.ACTION_UP:
				brush.drawFill(layerCanvas, states);
				/* fall-through */
			
			case MotionEvent.ACTION_CANCEL:
				brush.endFill();
				break;
			
			default:
				return false;
		}
		
		invalidate();
		return true;
	}
	
	/**
	 * Attempt to draw the brush outline as an overlay. This outline will
	 * be drawn so long as a touch is occurring or the tool is hovering.
	 * To ensure old outlines do not persist on the overlay bitmap, each
	 * successful call results in it being cleared and redrawn.
	 * 
	 * The raw MotionEvent data is transformed from being view-relative
	 * to being viewport-relative.
	 * 
	 * @param event  Event to attempt to use for drawing the outline
	 * @return       'true' if the event is used to draw the outline
	 */
	protected boolean drawOutline(MotionEvent event) {
		State states[] = {new State(event)};
		transformState(states, inverse);
		
		overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_MOVE:
			case MotionEvent.ACTION_HOVER_ENTER:
			case MotionEvent.ACTION_HOVER_MOVE:
				brush.drawOutline(overlayCanvas, states[0]);
				break;
			
			default:
				return false;
		}
		invalidate();
		return true;
	}
	
	/**
	 * Attempt to move the bitmaps (the "viewport") around the CanvasView.
	 * While a non-primary button is pressed, the canvas can be dragged
	 * around.
	 * 
	 * @param event  Event to attempt to move the viewport with
	 * @return       'true' if the event is used to move the viewport
	 */
	protected boolean moveViewport(MotionEvent event) {
		// XXX: Comment out the entirety of this 'if' statement if
		//building for API level < 14. Canvas dragging will not be
		//supported.
		if (event.getButtonState() != 0 &&
			event.getButtonState() != MotionEvent.BUTTON_PRIMARY) {
			float x = event.getX();
			float y = event.getY();
			
			if (grab != null) {
				transform.postTranslate(x - grab.x, y - grab.y);
				transform.invert(inverse);
			}
			grab = new PointF(x, y);
			invalidate();
			return true;
		}
		
		grab = null;
		return false;
	}
	
	/**
	 * Switch the currently-active tool to the tool associated with the
	 * provided event. Each MotionEvent stores the ID of the device which
	 * generated it, as well as the tool type. This information is combined
	 * into a single key and used to search the "brushes" hashmap.
	 * 
	 * @param event  Event to locate new tool with
	 */
	protected void changeTool(MotionEvent event) {
		int key = event.getDeviceId();
		
		// XXX: Comment out this line if building for API level < 14.
		key |= (event.getToolType(0) << 8);
		
		brush = brushes.get(key);
		
		if (brush == null) {
			brush = new Brush();
			brushes.put(key, brush);
		}
	}
	
	/**
	 * Transform an array of States by a given matrix. This is used to
	 * relocate the coordinates from being relative to the View (as
	 * contained in MotionEvents) to being relative to the bitmaps
	 * being drawn on. 
	 * 
	 * @param states     Array of States to transform
	 * @param transform  Matrix transformation to apply to each State
	 */
	protected void transformState(State[] states, Matrix transform) {
		for (int i = 0; i < states.length; i++) {
			float loc[] = {states[i].x, states[i].y};
			transform.mapPoints(loc);
			states[i].x = loc[0];
			states[i].y = loc[1];
		}
	}
	
	/**
	 * Initialize the various bitmaps that are blited to the screen.
	 * This method uses the current view size to determine appropriate
	 * dimensions.
	 */
	protected void initBitmaps() {
		int viewWidth  = getWidth();
		int viewHeight = getHeight();
		int canvasWidth  = Math.round(0.85f * viewWidth);
		int canvasHeight = Math.round(0.85f * viewHeight);
		
		initBitmaps(canvasWidth, canvasHeight);
		transform.postTranslate((viewWidth - canvasWidth)/2, (viewHeight - canvasHeight)/2);
		transform.invert(inverse);
	}
	
	/**
	 * Initialize the various bitmaps that are blited to the screen.
	 * In addition to initializing the bitmap that is drawn to, this
	 * also initializes the "checker" bitmap for visualizing the alpha
	 * channel and the "overlay" bitmap for visualizing the current
	 * tool location.
	 * 
	 * @param w  Width of the bitmap to draw on
	 * @param h  Height of the bitmap to draw on
	 */
	protected void initBitmaps(int w, int h) {
		if (w <= 0) { w = 1; }
		if (h <= 0) { h = 1; }
		
		checker = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
		Paint p = new Paint();
		p.setShader(new BitmapShader(
				BitmapFactory.decodeResource(getResources(), R.drawable.checker),
				Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
		new Canvas(checker).drawRect(0, 0, w, h, p);
		
		layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		layerCanvas = new Canvas(layer);
		
		overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		overlayCanvas = new Canvas(overlay);
		
		transform = new Matrix();
		inverse = new Matrix();
	}
}
