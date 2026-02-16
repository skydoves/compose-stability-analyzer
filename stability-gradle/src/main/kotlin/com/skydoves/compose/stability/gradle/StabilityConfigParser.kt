package com.skydoves.compose.stability.gradle

/*
 * Copyright 2023 The Android Open Source Project
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

import java.io.File

internal const val STABILITY_WILDCARD_SINGLE = '*'
internal const val STABILITY_WILDCARD_MULTI = "**"
internal const val STABILITY_GENERIC_OPEN = '<'
internal const val STABILITY_GENERIC_CLOSE = '>'
internal const val STABILITY_GENERIC_INCLUDE = "*"
internal const val STABILITY_GENERIC_EXCLUDE = "_"
internal const val STABILITY_GENERIC_SEPARATOR = ","
internal const val STABILITY_PACKAGE_SEPARATOR = '.'

// Based on the https://cs.android.com/android-studio/kotlin/+/master:plugins/compose/compiler-hosted/src/main/java/androidx/compose/compiler/plugins/kotlin/analysis/StabilityConfigParser.kt?q=error%20parsing%20stability%20configuration%20file
internal interface StabilityConfigParser {
  val stableTypeMatchers: Set<FqNameMatcher>

  companion object {
    fun fromFile(filepath: String?): StabilityConfigParser {
      if (filepath == null) return StabilityConfigParserImpl(emptyList())

      val confFile = File(filepath)
      return StabilityConfigParserImpl(confFile.readLines())
    }

    fun fromLines(lines: List<String>): StabilityConfigParser {
      return StabilityConfigParserImpl(lines)
    }
  }
}

private const val COMMENT_DELIMITER = "//"

private class StabilityConfigParserImpl(
  lines: List<String>,
) : StabilityConfigParser {
  override val stableTypeMatchers: Set<FqNameMatcher>

  init {
    val matchers: MutableSet<FqNameMatcher> = mutableSetOf()

    lines.forEachIndexed { index, line ->
      val l = line.trim()
      if (!l.startsWith(COMMENT_DELIMITER) && !l.isBlank()) {
        if (l.contains(COMMENT_DELIMITER)) { // com.foo.bar //comment
          error(
            errorMessage(
              line,
              index,
              "Comments are only supported at the start of a line."
            )
          )
        }
        try {
          matchers.add(FqNameMatcher(l))
        } catch (exception: IllegalStateException) {
          error(
            errorMessage(line, index, exception.message ?: "")
          )
        }
      }
    }

    stableTypeMatchers = matchers.toSet()
  }

  fun errorMessage(line: String, lineNumber: Int, message: String): String {
    return """
            Error parsing stability configuration file on line $lineNumber.
            $message
            $line
        """.trimIndent()
  }
}

internal class FqNameMatcher(val pattern: String) {
  /**
   * A key for storing this matcher.
   */
  val key: String

  /**
   * Mask for generic type inclusion in stability calculation
   */
  val mask: Int

  private val regex: Regex?

  init {
    val matchResult = validPatternMatcher.matchEntire(pattern)
      ?: error("$pattern is not a valid pattern")

    val regexPatternBuilder = StringBuilder()
    val keyBuilder = StringBuilder()
    var hasWildcard = false

    var index = 0
    var hitGenericOpener = false
    while (index < pattern.length && !hitGenericOpener) {
      when (val c = pattern[index]) {
        STABILITY_WILDCARD_SINGLE -> {
          hasWildcard = true
          if (pattern.getOrNull(index + 1) == STABILITY_WILDCARD_SINGLE) {
            regexPatternBuilder.append(PATTERN_MULTI_WILD)
            index++ // Skip a char to take the multi
          } else {
            regexPatternBuilder.append(PATTERN_SINGLE_WILD)
          }
        }
        STABILITY_PACKAGE_SEPARATOR -> {
          if (hasWildcard) {
            regexPatternBuilder.append(PATTERN_PACKAGE_SEGMENT)
          } else {
            keyBuilder.append(STABILITY_PACKAGE_SEPARATOR)
          }
        }
        STABILITY_GENERIC_OPEN -> {
          hitGenericOpener = true
        }
        else -> {
          if (hasWildcard) {
            regexPatternBuilder.append(c)
          } else {
            keyBuilder.append(c)
          }
        }
      }

      index++
    }

    // Pre-alloc regex for pattern having a wildcard at the end of the string
    // because it should be common.
    regex = if (regexPatternBuilder.isNotEmpty()) {
      when (val regexPattern = regexPatternBuilder.toString()) {
        singleWildcardSuffix.pattern -> singleWildcardSuffix
        multiWildcardSuffix.pattern -> multiWildcardSuffix
        else -> Regex(regexPattern)
      }
    } else {
      null
    }

    val genericMask = matchResult.groups["genericmask"]
    if (genericMask == null) {
      key = keyBuilder.toString()
      mask = 0.inv()
    } else {
      mask = genericMask.value
        .split(STABILITY_GENERIC_SEPARATOR)
        .map { if (it == STABILITY_GENERIC_INCLUDE) 1 else 0 }
        .reduceIndexed { i, acc, flag ->
          acc or (flag shl i)
        }

      key = keyBuilder.subSequence(0, genericMask.range.first - 1).toString()
    }
  }

  fun matches(name: String?): Boolean {
    if (pattern == STABILITY_WILDCARD_MULTI) return true


    val nameStr = name?.removeGenerics() ?: return false
    if (key.length > nameStr.length) return false

    val suffix = nameStr.substring(key.length)
    return when {
      regex != null -> nameStr.startsWith(key) && regex.matches(suffix)
      else -> key == nameStr
    }
  }

  private fun String.removeGenerics(): String {
    val genericsStart = indexOf("<")
    if (genericsStart < 0) {
      return this
    }

    return substring(0, genericsStart)
  }

  override fun equals(other: Any?): Boolean {
    val otherMatcher = other as? FqNameMatcher ?: return false
    return this.pattern == otherMatcher.pattern
  }

  override fun hashCode(): Int {
    return pattern.hashCode()
  }

  companion object {
    private const val PATTERN_SINGLE_WILD = "\\w+"
    private const val PATTERN_MULTI_WILD = "[\\w\\.]+"
    private const val PATTERN_PACKAGE_SEGMENT = "\\."

    private val validPatternMatcher =
      Regex(
        "((\\w+\\*{0,2}|\\*{1,2})\\.)*" +
          "((\\w+(<?(?<genericmask>([*|_],)*[*|_])>)+)|(\\w+\\*{0,2}|\\*{1,2}))"
      )
    private val singleWildcardSuffix = Regex(PATTERN_SINGLE_WILD)
    private val multiWildcardSuffix = Regex(PATTERN_MULTI_WILD)
  }
}
