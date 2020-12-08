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

package com.wacom.motiondump;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import android.os.Bundle;
import android.app.Activity;
import android.view.InputDevice;
import android.view.InputDevice.MotionRange;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnHoverListener;
import android.view.View.OnTouchListener;
import android.webkit.WebView;
import android.util.Log;

public class DumpActivity extends Activity implements OnGenericMotionListener, OnTouchListener, OnHoverListener {

	WebView wv;
	long callback_time[] = new long[30];
	long event_time[] = new long[30];
	long lag_time[] = new long[30];
	StringBuilder builder = new StringBuilder(200);
	int laststate = MotionEvent.ACTION_HOVER_EXIT;
	String lastmethod = "";
	
	private boolean isValidAction(int action) {
		switch (action) {
		case MotionEvent.ACTION_CANCEL: return true;
		case MotionEvent.ACTION_DOWN: return (laststate == MotionEvent.ACTION_UP || laststate == MotionEvent.ACTION_HOVER_EXIT);
		case MotionEvent.ACTION_HOVER_ENTER: return (laststate == MotionEvent.ACTION_UP || laststate == MotionEvent.ACTION_HOVER_EXIT);
		case MotionEvent.ACTION_HOVER_EXIT: return (laststate == MotionEvent.ACTION_HOVER_ENTER || laststate == MotionEvent.ACTION_HOVER_MOVE);
		case MotionEvent.ACTION_HOVER_MOVE: return (laststate == MotionEvent.ACTION_HOVER_ENTER || laststate == MotionEvent.ACTION_HOVER_MOVE);
		case MotionEvent.ACTION_MOVE: return (laststate == MotionEvent.ACTION_DOWN || laststate == MotionEvent.ACTION_MOVE);
		case MotionEvent.ACTION_UP: return (laststate == MotionEvent.ACTION_DOWN || laststate == MotionEvent.ACTION_MOVE);
		default: return true;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_dump);
		
		wv = (WebView)findViewById(R.id.log);
		wv.setOnTouchListener(this);
		wv.setOnHoverListener(this);
		wv.setOnGenericMotionListener(this);
		
		String style =
				  ".pointer { border-left: 3px solid gold; background: LightGoldenrodYellow; padding-left: 0.2em; margin: 0.5em; display: inline-block;}"
				+ "th { text-align: left; text-size: 33% }"
				+ "td { text-size: 33%; }";
		
		String content = "Touch or bring a pen in proximity.";
		
		String document = String.format("<html><head><style>%s</style></head><body><div id='content'>%s</div></body></html>", style, content);
		
		wv.loadData(document, "text/html", null);
		wv.getSettings().setJavaScriptEnabled(true);
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		setInnerHtml(wv, "content", toHtml(event, "onTouch"));
		return true;
	}

	@Override
	public boolean onHover(View v, MotionEvent event) {
		setInnerHtml(wv, "content", toHtml(event, "onHover"));
		return true;
	}
	
	@Override
	public boolean onGenericMotion(View v, MotionEvent event) {
		setInnerHtml(wv, "content", toHtml(event, "onGenericMotion"));
		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		setInnerHtml(wv, "content", toHtml(event, "onKeyDown"));
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		setInnerHtml(wv, "content", toHtml(event, "onKeyUp"));
		return super.onKeyUp(keyCode, event);
	}
	
	public String toHtml(KeyEvent event, String method) {
		Map<String,String> d = new TreeMap<String,String>();
		d.put("Device", String.format("%d (%s)", event.getDeviceId(), event.getDevice().getName()));
		d.put("Descriptor", event.getDevice().getDescriptor());
		d.put("Source", decode(event.getSource(), "SOURCE_", InputDevice.class, true));
		d.put("KeyCode", KeyEvent.keyCodeToString(event.getKeyCode()));
		d.put("ScanCode", Integer.toString(event.getScanCode()));
		d.put("Repeat", Integer.toString(event.getRepeatCount()));
		
		return String.format("<h1>%s</h1>%s", method, toHtml(d));
	}
	
	public String toHtml(MotionEvent event, String method) {
		Map<String,String> d = new TreeMap<String,String>();
		
		d.put("Device",        String.format("%d (%s)", event.getDeviceId(), event.getDevice().getName()));
		d.put("Descriptor",    event.getDevice().getDescriptor());
		d.put("Event Rate",    updateEventHz(event) + " Hz");
		d.put("Callback Rate", updateCallbackHz() + " Hz");
		d.put("Latency",       updateLatency(event));
		d.put("Pointers",      Integer.toString(event.getPointerCount()));
		
		d.put("Type",    decode(event.getAction(), "ACTION_", MotionEvent.class, false));
		d.put("Source",  decode(event.getSource(), "SOURCE_", InputDevice.class, true));
		d.put("Buttons", decode(event.getButtonState(), "BUTTON_", MotionEvent.class, true));
		
		StringBuilder b = builder;
		b.setLength(0);
		b.append(String.format("<h1>%s</h1>%s", method, toHtml(d)));
		
		for (int i = 0; i < event.getPointerCount(); i++) {
			b.append("<div class=\"pointer\">").append(getDetail(event, i)).append("</div>");
		}
		
		int action = event.getActionMasked();
		if (!isValidAction(action)) {
			Log.w("BAD EVENT", String.format("%s (%s) -> %s (%s)",
					FieldFinder.lookupFieldNames("ACTION_", MotionEvent.class, laststate, false), lastmethod,
					FieldFinder.lookupFieldNames("ACTION_", MotionEvent.class, action, false), method));
		}
		laststate = action;
		lastmethod = method;
		
		return b.toString();
	}
	
	String getDetail(MotionEvent event, int n) {
		Map<String,String> c = new TreeMap<String,String>();
		c.put("Index",   Integer.toString(n));
		c.put("ID",      Integer.toString(event.getPointerId(n)));
		c.put("Tool Type", decode(event.getToolType(n), "TOOL_TYPE_", MotionEvent.class, false));
		
		boolean isWacomHardware = event.getDevice().getName().toLowerCase().startsWith("wacom");
		
		List<Map<String,String>> history = new LinkedList<Map<String,String>>();
		for (int h = 0; h <= event.getHistorySize(); h++) {
			boolean current = h == event.getHistorySize();
			Map<String, String> d = new TreeMap<String,String>();
			
			d.put("Index", current ? "NOW" : Integer.toString(h));
			long time = current ? event.getEventTime() : event.getHistoricalEventTime(h);
			d.put("Time", Long.toString(time));
			d.put("TimeDelta", Long.toString(android.os.SystemClock.uptimeMillis() - time));
			
			for (MotionRange range : event.getDevice().getMotionRanges()) {
				int axis = range.getAxis();
				float val = current ? event.getAxisValue(axis, n) : event.getHistoricalAxisValue(axis, n, h);
				
				String key = FieldFinder.lookupFieldNames("AXIS_", MotionEvent.class, axis, false).replaceFirst("AXIS_", "");
				String value = Float.toString(val);
				
				if (isWacomHardware) {
					switch (axis) {
						case MotionEvent.AXIS_GENERIC_1:
							key = "Serial [AG1]";
							value = Integer.toString(Float.floatToIntBits(val));
							break;
						case MotionEvent.AXIS_GENERIC_2:
							key = "Function [AG2]";
							value = Integer.toString(Float.floatToIntBits(val));
							break;
						case MotionEvent.AXIS_GENERIC_3:
							key = "Twist [AG3]";
							break;
						case MotionEvent.AXIS_GENERIC_4:
							key = "Fingerwheel [AG4]";
							break; 
					}
				}
				
				d.put(key, value);
			}
			
			// Check if this historic event is otherwise-identical to the
			// previous one. If its the same (modulo the two always-different
			// fields of index and time), then don't bother printing it.
			if (history.size() > 0) {
				Map<String,String> a = history.get(history.size()-1);
				Map<String,String> b = d;
				
				String aIndex = a.remove("Index");
				String bIndex = b.remove("Index");
				String aTime  = a.remove("Time");
				String bTime  = b.remove("Time");
				
				boolean match = a.equals(b);
				
				a.put("Index", aIndex);
				b.put("Index", bIndex);
				a.put("Time", aTime);
				b.put("Time", bTime);
				
				if (match)
					continue;
			}
			
			history.add(d);
		}
		
		return String.format("<h2>Pointer</h2>%s%s", toHtml(c), toHtml(history));
	}
	
	void setInnerHtml(WebView v, String id, String html) {
		v.loadUrl(String.format("javascript:(function(){document.getElementById('%s').innerHTML='%s'})()", id, html));
	}
	
	String toHtml(Map<String,String> d) {
		String res = "<table>";
		for (String key : d.keySet()) {
			res += String.format("<tr><th>%s</th><td>%s</td></tr>", key, d.get(key));
		}
		res += "</table>";
		return res;
	}
	
	String toHtml(List<Map<String,String>> history) {
		if (history.size() < 1)
			return "";
		
		String res = "<table>";
		for (String key : history.get(0).keySet()) {
			res += String.format("<tr><th>%s</th>", key);
			for (Map<String,String> m : history) {
				res += String.format("<td>%s</td>", m.get(key));
			}
			res += "</tr>";
		}
		res += "</table>";
		return res;
	}
	
	String decode(int value, String prefix, Class c, boolean bitwise) {
		String fmt = bitwise ? "0x%X (%s)" : "%d (%s)";
		return String.format(fmt, value, FieldFinder.lookupFieldNames(prefix, c, value, bitwise));
	}
	
	void push(long[] arr, long val) {
		for (int i = 0; i < arr.length - 1; i++) {
			arr[i] = arr[i+1];
		}
		arr[arr.length - 1] = val;
	}
	
	int getHz(long[] arr) {
		float avg = (arr[arr.length - 1]- arr[0]) / (float)arr.length;
		return Math.round(1000.0f / avg);
	}
	
	int updateCallbackHz() {
		long now = System.currentTimeMillis();
		push(callback_time, now);
		return getHz(callback_time);
	}
	
	int updateEventHz(MotionEvent event) {
		for (int i = 0; i < event.getHistorySize(); i++) {
			push(event_time, event.getHistoricalEventTime(i));
		}
		return getHz(event_time);
	}
	
	String updateLatency(MotionEvent event) {
		for (int i = 0; i < event.getHistorySize(); i++) {
			long latency = android.os.SystemClock.uptimeMillis() - event.getHistoricalEventTime(i);
			push(lag_time, latency);
		}
		long avg = 0;
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long t : lag_time) {
			avg += t;
			if (t < min) { min = t; }
			if (t > max) { max = t; }
		}
		avg = Math.round((float)avg/lag_time.length);
		return String.format("%d ms (avg) %d ms (max) %d ms (min)", avg, max, min);
	}
	
	
	static class FieldFinder {
		static class CacheEntry {
			Class c;
			String prefix;
			int value;
			
			public CacheEntry(Class c, String prefix, int value) {
				this.c = c;
				this.prefix = prefix;
				this.value = value;
			}
			
			public boolean equals(Object obj) {
				if (obj == null)
					return false;
				if (obj == this)
					return true;
				if (!(obj instanceof CacheEntry))
					return false;
				
				CacheEntry e = (CacheEntry)obj;
				return this.c.equals(e.c) &&
				       this.prefix.equals(e.prefix) &&
				       this.value == e.value;
			}
			
			public int hashCode() {
				return c.hashCode() ^ prefix.hashCode() ^ value;
			}
		}
		
		static Map<CacheEntry,String> cache = new HashMap<CacheEntry,String>();
		
		static String lookupFieldNames(String prefix, Class c, int value, boolean bitwise) {
			CacheEntry e = new CacheEntry(c, prefix, value);
			String str = cache.get(e);
			if (str != null) {
				return str;
			}
			
			str = fieldToNames(prefix, c, value, bitwise);			
			cache.put(e, str);
			return str;
		}
		
		static String fieldToNames(String prefix, Class c, int value, boolean bitwise) {
			if (value == 0 && bitwise)
				return "";
			
			String str = "";
			
			for (Field f : getPrefixedConstants(c, prefix)) {
				try {
					String name = f.getName();
					int constant = f.getInt(null);
					
					if (bitwise) {
						if (!test(value, constant) || constant == 0)
							continue;
					}
					else {
						if (constant != value)
							continue;
					}
					
					str += name + " | ";
				} catch (IllegalAccessException e) {
					Log.e("DumpActivity", e.getLocalizedMessage());
				}
			}
			
			if (str.length() == 0)
				str += "???";
			else
				str = str.substring(0, str.length()-3);
			
			return str;
		}
		
		static List<Field> getConstants(Class c) {
			List<Field> fields = new LinkedList<Field>();
			
			for (Field f : c.getDeclaredFields()) {
				int mod = f.getModifiers();
				if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod) && f.getType() == int.class) {
					fields.add(f);
				}
			}
			
			return fields;
		}
		
		static List<Field> getPrefixedConstants(Class c, String prefix) {
			List<Field> fields = new LinkedList<Field>();
			for (Field f : getConstants(c)) {
				if (f.getName().startsWith(prefix))
						fields.add(f);
			}
			return fields;
		}
		
		static boolean test(int x, int y) {
			return ((x & y) == y);
		}
	}
	
}
