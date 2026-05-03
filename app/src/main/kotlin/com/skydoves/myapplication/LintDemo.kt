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
package com.skydoves.myapplication

import androidx.compose.runtime.Composable
import com.skydoves.compose.stability.runtime.TraceRecomposition

/**
 * Demonstration of the lint rule for @TraceRecomposition.
 *
 * This file shows both correct and incorrect usage.
 */

@TraceRecomposition
@Composable
fun CorrectUsage() {
  // This is valid - lint will not complain
}

// The lint rule will show an error here with quick fixes:
// 1. Add @Composable annotation
// 2. Remove @TraceRecomposition annotation
// @TraceRecomposition
fun incorrectUsage() {
  // This will trigger a lint error in Android Studio!
  // Try hovering over @TraceRecomposition to see the error message
}

@TraceRecomposition(tag = "demo", threshold = 3)
@Composable
fun CorrectWithParameters() {
  // Valid usage with annotation parameters
}
