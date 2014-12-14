/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.dualquo.te.carplayer;

import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;

/**
 * Framework methods only in Froyo or above go here.
 */
public class CompatFroyo {
	public static void registerMediaButtonEventReceiver(AudioManager manager, ComponentName receiver)
	{
		manager.registerMediaButtonEventReceiver(receiver);
	}

	public static void unregisterMediaButtonEventReceiver(AudioManager manager, ComponentName receiver)
	{
		manager.unregisterMediaButtonEventReceiver(receiver);
	}

	public static void dataChanged(Context context)
	{
		new BackupManager(context).dataChanged();
	}
}
