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

import android.util.Log;
import android.view.MotionEvent;

/**
 * State represents the tool state at a single point in time. Objects of
 * this class provide a snapshot of tool location, pressure, etc. Data
 * may be constructed directly from a MotionEvent's current or historic
 * values, or interpolated between other states.
 * 
 * @author wacom
 */
public class State {
	
	long time;
	float x, y, pressure, size;
	
	/**
	 * Obtain an array of States, one for each historic and current data
	 * point contained in the provided MotionEvent.
	 * 
	 * @param event  The event to create all the States from
	 * @return       An array of states, one for each point in time
	 */
	static State[] getStates(MotionEvent event) {
		int n = event.getHistorySize();
		State[] states = new State[n+1];
		
		for (int i = 0; i < n; i++) {
			states[i] = new State(event, i);
		}
		states[n] = new State(event);
		
		return states;
	}
	
	/**
	 * Obtain a State which is linearly interpolated between two others.
	 * The degree of interpolation is specified by 'frac'. As 'frac' is
	 * increased, the interpolated State will resemble 'a' less and 'b'
	 * more.
	 * 
	 * @param a     The "first" state
	 * @param b     The "second" state
	 * @param frac  The fraction to use in interpolation, between 0 and 1
	 * @return      A linear interpolation of states 'a' and 'b'
	 */
	static State interpolate(State a, State b, float frac) {
		return new State(
			(long)interpolate(a.time, b.time, frac),
			interpolate(a.x, b.x, frac),
			interpolate(a.y, b.y, frac),
			interpolate(a.pressure, b.pressure, frac),
			interpolate(a.size, b.size, frac)
		);
	}
	
	private static float interpolate(float a, float b, float frac) {
		return ((b-a)*frac) + a;
	}

	static float distance(State a, State b) {
		return (float)Math.sqrt(Math.pow((b.x - a.x), 2) + Math.pow((b.y - a.y), 2));
	}
	
	State(long time, float x, float y, float pressure, float size) {
		this.time = time;
		this.x = x;
		this.y = y;
		this.pressure = pressure;
		this.size = size;
	}
	
	/**
	 * Obtain a State from the given MotionEvent's history buffer.
	 * 
	 * @param e    Event to use as the data source
	 * @param pos  Index for each call to 'getHistoricalFoo'
	 */
	State(MotionEvent e, int pos) {
		this(
		    e.getHistoricalEventTime(pos),
		    e.getHistoricalX(pos),
		    e.getHistoricalY(pos),
		    e.getHistoricalPressure(pos),
		    e.getHistoricalSize(pos)
		);
	}
	
	/**
	 * Obtain a State from the given MotionEvent's most-current data.
	 * 
	 * @param e  Event to use as the data source.
	 */
	State(MotionEvent e) {
		this(
		    e.getEventTime(),
		    e.getX(),
		    e.getY(),
		    e.getPressure(),
		    e.getSize()
		);
	}
	
	public String toString() {
		return String.format("State(%d, %f, %f, %f, %f)",
		    time, x, y, pressure, size);
	}
}
