package com.skydoves.compose.stability.gradle

import java.io.File

private val ALLOWED_CHARACTERS_REGEX = Regex("[a-zA-Z_.$0-9*]*")

/**
 * Get custom stable type patterns from configuration file.
 */
internal fun getCustomStableTypesAsRegex(fileList: List<File>): List<Regex> {
  return fileList.flatMap { file ->
    if (!file.exists() || !file.isFile) {
      return@flatMap emptyList()
    }

    // Parse the configuration file
    val patterns = mutableListOf<String>()
    file.readLines().forEach { line ->
      if (!ALLOWED_CHARACTERS_REGEX.matches(line)) {
        throw IllegalArgumentException("Configuration line '$line' in file $file contains invalid characters.")
      }

      val trimmed = line.trim()
      // Skip empty lines and comments
      if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
        patterns.add(trimmed)
      }
    }

    // Convert patterns to regex
    patterns.map { pattern ->
      // Convert glob-style wildcards to regex
      pattern
        .replace(".", "\\.")
        .replace("$", "\\$")
        // Replace ** with some placeholder value that is unlikely to be in the original string
        .replace("**", "xxDOUBLEWILDCARDHERExx")
        .replace("*", "[^.]*")
        .replace("xxDOUBLEWILDCARDHERExx", ".*")
        .toRegex()
    }
  }
}
