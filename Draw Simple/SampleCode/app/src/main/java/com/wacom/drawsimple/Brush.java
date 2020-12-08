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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Bitmap.Config;
import android.util.Log;

/**
 * A Brush describes how strokes are rendered. Brushes have attributes like
 * size, hardness, and color.
 * 
 * @author wacom
 */
public class Brush {
	
	Bitmap fill, outline;
	Canvas fillCanvas, outlineCanvas;
	Paint  fillPaint, outlinePaint;
	
	State last;
	int spacing, size, hardness;
	int current_radius;
	int foreground = Color.BLACK;
	
	/**
	 * Create a Brush with some basic default settings.
	 */
	public Brush() {
		this(10, 20, 20);
	}
	
	/**
	 * Create a Brush from defined settings.
	 * 
	 * @param spacing   Percent of brush radius to move before re-stamping the brush
	 * @param size      Maximum size the brush can take on
	 * @param hardness  Maximum hardness the brush can take on
	 */
	public Brush(int spacing, int size, int hardness) {
		this.spacing = spacing;
		this.size = size;
		this.hardness = hardness;
		
		this.fill = Bitmap.createBitmap(size, size, Config.ARGB_8888);
		this.fillCanvas = new Canvas(this.fill);
		this.fillPaint = new Paint();
		this.fillPaint.setStyle(Paint.Style.FILL);
		this.fillPaint.setColor(Color.BLACK);
		
		this.outline = Bitmap.createBitmap(size, size, Config.ARGB_8888);
		this.outlineCanvas = new Canvas(this.outline);
		this.outlinePaint = new Paint();
		this.outlinePaint.setStyle(Paint.Style.STROKE);
		this.outlinePaint.setColor(Color.BLACK);
	}
	
	/**
	 * Ends a filled brush stroke that is currently taking place. Calling
	 * this method prevents the end of one stroke from being automatically
	 * connected to the begining of the next (as drawFill does by default).
	 * 
	 * @see drawFill
	 */
	public void endFill() {
		this.last = null;
	}
	
	/**
	 * Update the color of the brush.
	 * 
	 * @param foreground  New brush color (e.g. 0xff000000 or Color.BLACK for black)
	 */
	public void setColor(int foreground) {
		this.foreground = foreground;
	}
	
	/**
	 * Draw the brush as a smooth stroke through all the given states.
	 * In-between states will be interpolated as necessary, matching the
	 * brush's defined spacing. The final state in the array will be
	 * remembered to let the next call to this function continue drawing
	 * the same stroke. To begin drawing a new stroke, call 'endFill'.
	 * 
	 * @param canvas  Canvas to draw into
	 * @param state   Array of states to draw the stroke along
	 * @see endFill
	 */
	public void drawFill(Canvas canvas, State[] state) {
		if (last != null)
			drawFill(canvas, last, state[0]);
		
		for (int i = 1; i < state.length; i++) {
			drawFill(canvas, state[i-1], state[i]);
		}
		
		last = state[state.length-1];
	}
	
	/**
	 * Draw the brush as a smooth stroke between the two given states.
	 * In-between states will be interpolated as necessary, matching
	 * the brush's defined spacing.
	 * 
	 * @param canvas  Canvas to draw into
	 * @param a       State to begin drawing stroke at
	 * @param b       State to end drawing stroke at
	 */
	public void drawFill(Canvas canvas, State a, State b) {
		float dist = State.distance(a,b);
		float d = 0;
		
		do {
			float frac = 0;
			if (dist != 0)
				frac = d/dist;
			
			State s = State.interpolate(a, b, frac);
			drawFill(canvas, s);
			
			d += (2 * current_radius * spacing / 100.0);
		} while (d < dist);
	}
	
	/**
	 * Stamp the currently-rendered fill onto the provided canvas.
	 * 
	 * @param canvas  Canvas to draw into
	 * @param s       State to use for drawing
	 */
	public void drawFill(Canvas canvas, State s) {
		float x = s.x - fill.getWidth()/2f;
		float y = s.y - fill.getHeight()/2f;
		
		render(foreground, Math.min(s.pressure, 1), 1);
		canvas.drawBitmap(fill, x, y, null);
	}
	
	/**
	 * Stamp the currently-rendered outline onto the provided canvas.
	 * 
	 * @param canvas  Canvas to draw into
	 * @param s       State to use for drawing
	 */
	public void drawOutline(Canvas canvas, State s) {
		float x = s.x - outline.getWidth()/2f;
		float y = s.y - outline.getHeight()/2f;
		
		render(foreground, Math.min(s.pressure, 1), 1);
		canvas.drawBitmap(outline, x, y, null);
	}
	
	/**
	 * Update the image that will be stamped by the brush with each
	 * call to drawFill and drawOutline. Settings like color and
	 * radius may be changed at any time to provide dynamic brush
	 * strokes.
	 * 
	 * @param color     Fill color to use for the brush
	 * @param radius	Radius as a fraction of maximum size
	 * @param hardness  Hardness as a fraction of maximum hardness 
	 */
	void render(int color, float radius, float hardness) {
		if (hardness < 0 || hardness > 1)
			throw new IllegalArgumentException("Hardness may only take on values between 0 and 1 (inclusive)");
		if (radius < 0 || radius > 1)
			throw new IllegalArgumentException("Radius may only take on values between 0 and 1 (inclusive)");
		
		float center = size/2f;
		radius *= size/2f;
		hardness *= this.hardness / 100.0f;
		
		current_radius = (int)Math.ceil(radius);
		
		fillCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		outlineCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		
		if (current_radius > 0) {
			RadialGradient gradient = new RadialGradient(center, center, radius,
			    new int[] {color, color, color & 0x00ffffff},
			    new float[] {0.0f, hardness, 1.0f},
			    android.graphics.Shader.TileMode.CLAMP);
			
			fillPaint.setShader(gradient);
			fillCanvas.drawCircle(center, center, radius, fillPaint);
		}
		
		outlinePaint.setColor(color);
		outlineCanvas.drawCircle(center, center, center, outlinePaint);
	}
}
