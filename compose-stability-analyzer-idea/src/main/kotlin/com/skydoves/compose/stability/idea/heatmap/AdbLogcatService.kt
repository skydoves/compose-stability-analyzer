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
package com.skydoves.compose.stability.idea.heatmap

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Project-level service that listens to ADB logcat for recomposition events
 * and aggregates them into per-composable heatmap data.
 *
 * The service spawns an `adb logcat -s Recomposition:D` process on a daemon
 * thread, parses the output via [LogcatParser], and stores aggregated data
 * in a thread-safe [ConcurrentHashMap]. CodeVision invalidation is throttled
 * to avoid excessive UI updates.
 */
@Service(Service.Level.PROJECT)
internal class AdbLogcatService(
  private val project: Project,
) : Disposable {

  private val log = Logger.getInstance(AdbLogcatService::class.java)
  private val settings get() = StabilitySettingsState.getInstance()

  private val dataMap = ConcurrentHashMap<String, ComposableHeatmapData>()
  private val running = AtomicBoolean(false)
  private val dataVersion = AtomicLong(0)

  @Volatile
  private var lastRefreshedVersion = 0L
  private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

  @Volatile
  private var process: Process? = null

  @Volatile
  private var readerThread: Thread? = null

  val isRunning: Boolean get() = running.get()

  /** Start listening on the given device (or the default device if null). */
  fun start(deviceSerial: String? = null) {
    if (running.getAndSet(true)) return

    val adbPath = resolveAdbPath()
    if (adbPath == null) {
      log.warn("ADB not found. Set ANDROID_HOME or ensure adb is on PATH.")
      running.set(false)
      return
    }

    val command = mutableListOf(adbPath)
    if (deviceSerial != null) {
      command += listOf("-s", deviceSerial)
    }
    command += listOf("logcat", "-s", "Recomposition:D", "-T", "1")

    log.info("Starting logcat: ${command.joinToString(" ")}")

    try {
      val pb = ProcessBuilder(command)
        .redirectErrorStream(true)
      process = pb.start()
    } catch (e: Exception) {
      log.warn("Failed to start adb logcat process", e)
      running.set(false)
      return
    }

    readerThread = Thread({
      readLoop()
    }, "RecompositionHeatmap-logcat-reader").apply {
      isDaemon = true
      start()
    }

    startPeriodicRefresh()
  }

  /** Stop the logcat listener. */
  fun stop() {
    if (!running.getAndSet(false)) return
    refreshAlarm.cancelAllRequests()
    process?.destroyForcibly()
    process = null
    readerThread?.interrupt()
    readerThread = null
  }

  /** Clear all aggregated data and refresh CodeVision. */
  fun clearData() {
    dataMap.clear()
    dataVersion.incrementAndGet()
    // Force an immediate refresh on EDT
    ApplicationManager.getApplication().invokeLater {
      invalidateCodeVision()
    }
  }

  /** Get heatmap data for a composable by its simple name. */
  fun getHeatmapData(name: String): ComposableHeatmapData? = dataMap[name]

  /** Get all tracked composable names. */
  fun getAllComposableNames(): Set<String> = dataMap.keys.toSet()

  /** Check if ADB is available. */
  fun isAdbAvailable(): Boolean = resolveAdbPath() != null

  /** Get the resolved ADB path (for diagnostic display). */
  fun getAdbPath(): String? = resolveAdbPath()

  /** List connected ADB devices. */
  fun listDevices(): List<AdbDevice> {
    val adbPath = resolveAdbPath() ?: return emptyList()
    return try {
      val proc = ProcessBuilder(adbPath, "devices", "-l")
        .redirectErrorStream(true)
        .start()
      val output = proc.inputStream.bufferedReader().readText()
      proc.waitFor()
      parseDeviceList(output)
    } catch (e: Exception) {
      log.warn("Failed to list ADB devices", e)
      emptyList()
    }
  }

  override fun dispose() {
    stop()
    dataMap.clear()
  }

  // ── Internal ───────────────────────────────────────────────────────────

  private fun readLoop() {
    val proc = process ?: return
    val reader = BufferedReader(InputStreamReader(proc.inputStream))
    val parser = LogcatParser(::onEventParsed)

    try {
      var line = reader.readLine()
      while (line != null && running.get()) {
        // Raw mode output is just the message; but guard against tagged mode
        val message = stripLogcatPrefix(line)
        if (message.isNotEmpty()) {
          parser.feedLine(message)
        }
        line = reader.readLine()
      }
      parser.flush()
    } catch (_: InterruptedException) {
      // Expected on stop
    } catch (e: Exception) {
      if (running.get()) {
        log.warn("Error reading logcat stream", e)
      }
    } finally {
      running.set(false)
    }
  }

  private fun onEventParsed(event: ParsedRecompositionEvent) {
    log.info(
      "Parsed recomposition event: ${event.composableName} #${event.recompositionCount}" +
        " (${event.parameterEntries.size} params, unstable: ${event.unstableParameters})",
    )
    val maxRecent = settings.heatmapMaxRecentEvents
    dataMap.compute(event.composableName) { _, existing ->
      if (existing == null) {
        ComposableHeatmapData(
          composableName = event.composableName,
          totalRecompositionCount = 1,
          maxSingleCount = event.recompositionCount,
          recentEvents = listOf(event),
          lastSeenTimestampMs = event.timestampMs,
          changedParameters = event.parameterEntries
            .filter { it.status == ParameterStatus.CHANGED }
            .associate { it.name to 1 },
          unstableParameters = event.unstableParameters.toSet(),
        )
      } else {
        val mergedChanged = existing.changedParameters.toMutableMap()
        event.parameterEntries
          .filter { it.status == ParameterStatus.CHANGED }
          .forEach { mergedChanged[it.name] = (mergedChanged[it.name] ?: 0) + 1 }

        val recentCapped = (existing.recentEvents + event).takeLast(maxRecent)

        existing.copy(
          totalRecompositionCount = existing.totalRecompositionCount + 1,
          maxSingleCount = maxOf(existing.maxSingleCount, event.recompositionCount),
          recentEvents = recentCapped,
          lastSeenTimestampMs = event.timestampMs,
          changedParameters = mergedChanged,
          unstableParameters = existing.unstableParameters + event.unstableParameters,
        )
      }
    }

    dataVersion.incrementAndGet()
  }

  /** Starts a periodic refresh loop that checks for new data and updates CodeVision. */
  private fun startPeriodicRefresh() {
    scheduleNextRefresh()
  }

  private fun scheduleNextRefresh() {
    if (!running.get() || project.isDisposed) return
    refreshAlarm.addRequest(
      {
        val current = dataVersion.get()
        if (current != lastRefreshedVersion) {
          lastRefreshedVersion = current
          invalidateCodeVision()
        }
        scheduleNextRefresh()
      },
      REFRESH_INTERVAL_MS,
    )
  }

  private fun invalidateCodeVision() {
    if (project.isDisposed) return

    // Invalidate CodeVision provider — tells the host to re-query our provider
    try {
      val host = project.getService(CodeVisionHost::class.java)
      host?.invalidateProvider(
        CodeVisionHost.LensInvalidateSignal(
          null,
          listOf(RecompositionHeatmapProvider.PROVIDER_ID),
        ),
      )
    } catch (_: Exception) {
      // CodeVisionHost API may vary across IDE versions
    }

    // Restart daemon analysis for each open file to trigger DaemonBoundCodeVisionProvider
    val fem = FileEditorManager.getInstance(project)
    val psiManager = PsiManager.getInstance(project)
    for (vFile in fem.openFiles) {
      val psiFile = psiManager.findFile(vFile) ?: continue
      DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
    }
  }

  // ── ADB helpers ────────────────────────────────────────────────────────

  private fun resolveAdbPath(): String? {
    // 1. Try ANDROID_HOME / ANDROID_SDK_ROOT environment variables
    val sdkRoot = System.getenv("ANDROID_HOME")
      ?: System.getenv("ANDROID_SDK_ROOT")
    if (sdkRoot != null) {
      val adb = File(sdkRoot, "platform-tools${File.separator}adb")
      if (adb.exists() && adb.canExecute()) {
        return adb.absolutePath
      }
    }

    // 2. Try local.properties in the project root (sdk.dir)
    try {
      val localProps = File(project.basePath ?: "", "local.properties")
      if (localProps.exists()) {
        val props = java.util.Properties()
        localProps.inputStream().use { props.load(it) }
        val sdkDir = props.getProperty("sdk.dir")
        if (sdkDir != null) {
          val adb = File(sdkDir, "platform-tools${File.separator}adb")
          if (adb.exists() && adb.canExecute()) {
            return adb.absolutePath
          }
        }
      }
    } catch (_: Exception) {
      // Ignore and try next fallback
    }

    // 3. Try common macOS / Linux SDK locations
    val commonPaths = listOf(
      "${System.getProperty("user.home")}/Library/Android/sdk/platform-tools/adb",
      "${System.getProperty("user.home")}/Android/Sdk/platform-tools/adb",
      "/opt/homebrew/bin/adb",
      "/usr/local/bin/adb",
    )
    for (path in commonPaths) {
      val adb = File(path)
      if (adb.exists() && adb.canExecute()) {
        return adb.absolutePath
      }
    }

    // 4. Fallback: adb on PATH
    return try {
      val proc = ProcessBuilder("which", "adb")
        .redirectErrorStream(true)
        .start()
      val path = proc.inputStream.bufferedReader().readText().trim()
      proc.waitFor()
      if (path.isNotEmpty() && File(path).exists()) path else null
    } catch (_: Exception) {
      null
    }
  }

  private fun parseDeviceList(output: String): List<AdbDevice> {
    return output.lines()
      .drop(1) // skip "List of devices attached" header
      .filter { it.isNotBlank() }
      .mapNotNull { line ->
        val parts = line.trim().split("\\s+".toRegex(), limit = 2)
        if (parts.size >= 2 && parts[1] != "offline") {
          AdbDevice(serial = parts[0], description = parts.getOrElse(1) { "" })
        } else {
          null
        }
      }
  }

  companion object {
    private const val REFRESH_INTERVAL_MS = 1000L

    /**
     * Matches common logcat prefixes for the Recomposition tag:
     * - `D/Recomposition: `             (brief format)
     * - `D/Recomposition(12345): `      (brief with PID)
     * - `02-13 11:23:45.678 ... D Recomposition: `  (threadtime format)
     */
    private val LOGCAT_PREFIX_REGEX =
      """^.*?D[/\s]+Recomposition(?:\(\s*\d+\s*\))?:\s*""".toRegex()

    fun getInstance(project: Project): AdbLogcatService =
      project.getService(AdbLogcatService::class.java)

    /**
     * Strip the logcat prefix to get the raw message.
     * If no known prefix is found, returns the line as-is (raw mode).
     */
    private fun stripLogcatPrefix(line: String): String {
      return LOGCAT_PREFIX_REGEX.replaceFirst(line, "")
    }
  }
}
