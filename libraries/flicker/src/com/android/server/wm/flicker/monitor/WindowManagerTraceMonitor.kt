/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm.flicker.monitor

import android.view.WindowManagerGlobal
import com.android.server.wm.flicker.io.TraceType
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import java.io.File

/** Captures [WindowManagerTrace] from WindowManager. */
open class WindowManagerTraceMonitor : TransitionMonitor() {
    private val windowManager = WindowManagerGlobal.getWindowManagerService()
    override val traceType: TraceType = TraceType.WM
    override val isEnabled: Boolean
        get() = windowManager.isWindowTraceEnabled

    override fun start() {
        windowManager.startWindowTrace()
    }

    override fun doStop(): File {
        windowManager.stopWindowTrace()
        return TRACE_DIR.resolve(TraceType.WM.fileName)
    }
}
