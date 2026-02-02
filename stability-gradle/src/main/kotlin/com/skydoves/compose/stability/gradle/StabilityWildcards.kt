package com.skydoves.compose.stability.gradle

import java.io.File

/**
 * Get custom stable type patterns from configuration file.
 */
internal fun getCustomStableTypesAsRegex(fileList: List<File>): List<Regex> {
  return try {
    fileList.flatMap { file ->
      if (!file.exists() || !file.isFile) {
        return@flatMap emptyList()
      }

      // Parse the configuration file
      val patterns = mutableListOf<String>()
      file.readLines().forEach { line ->
        val trimmed = line.trim()
        // Skip empty lines and comments
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
          patterns.add(trimmed)
        }
      }

      // Convert patterns to regex
      patterns.mapNotNull { pattern ->
        try {
          // Convert glob-style wildcards to regex
          pattern
            .replace(".", "\\.")
            // Replace ** with some placeholder value that is unlikely to be in the original string
            .replace("**", "xxDOUBLEWILDCARDHERExx")
            .replace("*", "[^.]*")
            .replace("xxDOUBLEWILDCARDHERExx", ".*")
            .toRegex()
        } catch (e: Exception) {
          null // Skip invalid patterns
        }
      }
    }
  } catch (e: Exception) {
    emptyList()
  }
}
