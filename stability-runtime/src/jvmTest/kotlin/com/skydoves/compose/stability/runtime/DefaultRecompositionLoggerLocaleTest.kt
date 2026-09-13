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

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The recomposition log line is a wire protocol: the IDE plugin's `LogcatParser` reads the duration
 * with a regex that expects a dot decimal separator. `String.format` without an explicit locale uses
 * `Locale.getDefault()`, so on a comma-decimal device (de, fr, pt-BR, ru, tr, id, ...) the duration
 * was emitted as `(1,20ms)` and silently parsed back as `0.0`, emptying heatmap timings and
 * collapsing the Stability Doctor's measured waste to its 1ms floor.
 *
 * This never reproduced for a maintainer on an en- or ko-locale machine, which is why it shipped.
 */
class DefaultRecompositionLoggerLocaleTest {

  @Test
  fun durationUsesDotDecimalSeparatorUnderACommaDecimalLocale() {
    val originalLocale = Locale.getDefault()
    val originalOut = System.out
    try {
      Locale.setDefault(Locale.GERMANY)
      val buffer = ByteArrayOutputStream()
      System.setOut(PrintStream(buffer, true))

      DefaultRecompositionLogger().log(
        RecompositionEvent(
          composableName = "UserProfile",
          tag = "",
          recompositionCount = 2,
          parameterChanges = emptyList(),
          unstableParameters = emptyList(),
          durationNanos = 1_200_000L,
        ),
      )

      System.setOut(originalOut)
      val header = buffer.toString().lineSequence().first()
      assertTrue(
        header.contains("(1.20ms)"),
        "duration must use a dot decimal separator regardless of the default locale, got: $header",
      )
    } finally {
      System.setOut(originalOut)
      Locale.setDefault(originalLocale)
    }
  }
}
