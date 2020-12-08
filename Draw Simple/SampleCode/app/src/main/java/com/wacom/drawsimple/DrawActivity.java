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

import com.wacom.drawsimple.R;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

/**
 * DrawActivity allows the user to make simple drawings. A CanvasView
 * is used to provide a canvas on which to draw, and a palate of eight
 * color swatches allows easy switching of brush color.
 * 
 * @author wacom
 */
public class DrawActivity extends Activity {
	
	static int colors[][] = {
		{R.id.color_black,   0xff000000}, {R.id.color_white,  0xffffffff},
	    {R.id.color_red,     0xffff0000}, {R.id.color_green,  0xff00ff00},
	    {R.id.color_blue,    0xff0000ff}, {R.id.color_cyan,   0xff00ffff},
	    {R.id.color_magenta, 0xffff00ff}, {R.id.color_yellow, 0xffffff00} 
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_draw);
		
		final CanvasView canvas = (CanvasView)findViewById(R.id.canvas);
		for (int color[] : colors) {
			final int value = color[1];
			View v = findViewById(color[0]);
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					canvas.setColor(value);
				}
			});
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_draw, menu);
		return true;
	}
	
	/**
	 * Have the CanvasView throw out its existing canvas and recreate a
	 * new one to draw into. The new canvas is hard-coded to be 640x480
	 * in size.
	 * 
	 * @param item
	 * @return
	 */
	public boolean onNewCanvas(MenuItem item) {
		((CanvasView)findViewById(R.id.canvas)).initBitmaps();
		return true;
	}

}
