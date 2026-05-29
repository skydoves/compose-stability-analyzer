/*
 * Designed and developed by 2025 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skydoves.compose.stability.runtime

import androidx.compose.runtime.snapshots.Snapshot
import java.util.Collections
import java.util.WeakHashMap

/**
 * Identity-keyed, weakly-held map of `StateObject` → most recent write-site. Weak keys so tracked
 * state objects can be garbage-collected; synchronized because the write observer fires on the
 * writing thread while reads happen on the composition thread.
 */
private val writeSites: MutableMap<Any, String> =
  Collections.synchronizedMap(WeakHashMap())

@Volatile
private var installed = false

internal actual fun installStateWriteTracker() {
  if (installed) return
  installed = true
  try {
    // Fires on the FIRST write to each state object per apply cycle, synchronously on the writing
    // thread — so the stack trace at this point IS the mutation call site.
    Snapshot.registerGlobalWriteObserver { stateObject ->
      if (ComposeStabilityAnalyzer.isEnabled() && ComposeStabilityAnalyzer.isStateTracingActive()) {
        val site = captureWriteSite()
        if (site != null) {
          writeSites[stateObject] = site
        }
      }
    }
  } catch (_: Throwable) {
    // Compose runtime missing/incompatible (e.g. the plugin applied to a non-Compose module).
    // Disable write-site capture silently; nothing else depends on it.
  }
}

internal actual fun writeSiteFor(state: Any?): String? {
  if (state == null) return null
  return writeSites[state]
}

/** Best-effort: first non-framework frame, formatted as `method (File.kt:line)`. */
private fun captureWriteSite(): String? {
  return try {
    val frame = Throwable().stackTrace.firstOrNull { f ->
      val cn = f.className
      !cn.startsWith("kotlin.") &&
        !cn.startsWith("kotlinx.") &&
        !cn.startsWith("androidx.") &&
        !cn.startsWith("java.") &&
        !cn.startsWith("jdk.") &&
        !cn.startsWith("android.") &&
        !cn.startsWith("com.skydoves.compose.stability.runtime.")
    } ?: return null
    val file = frame.fileName
    val line = frame.lineNumber
    if (file != null && line > 0) "${frame.methodName} ($file:$line)" else frame.methodName
  } catch (_: Throwable) {
    null
  }
}
