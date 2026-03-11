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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.compose.stability.runtime.TraceRecomposition
import com.skydoves.myapplication.models.StableUser
import com.skydoves.myapplication.models.UnstableUser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

/**
 * Example screen demonstrating @TraceRecomposition usage.
 *
 * This file shows AUTOMATIC recomposition tracking via compiler IR injection.
 * Just add @TraceRecomposition annotation - tracking code is injected automatically!
 *
 * To use:
 * 1. Add @TraceRecomposition annotation to your composable
 * 2. Build the project (tracking code is automatically injected by the compiler)
 * 3. Enable logging in your Application class:
 *    ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)
 * 4. Run your app and check Logcat for "Recomposition" tag
 */
@Composable
fun RecompositionTrackingExample() {
  var counter by remember { mutableIntStateOf(0) }
  var stableUser by remember { mutableStateOf(StableUser("John", 30)) }
  var unstableUser by remember { mutableStateOf(UnstableUser("Jane", 25)) }
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .padding(16.dp)
      .verticalScroll(scrollState),
  ) {
    // Example 1: Basic tracking with default settings
    TrackedCounterDisplay(counter) {}

    Spacer(modifier = Modifier.height(16.dp))

    // Example 2: Tracking with custom tag
    TrackedUserProfile(stableUser)

    Spacer(modifier = Modifier.height(16.dp))

    // Example 3: Tracking with threshold
    TrackedUnstableUserCard(unstableUser)

    Spacer(modifier = Modifier.height(16.dp))

    // Example 4: Tracking with both tag and threshold
    TrackedMixedParameters(
      title = "Mixed Stability Example",
      count = counter,
      user = stableUser,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Buttons to trigger recompositions
    Button(onClick = { counter++ }) {
      Text("Increment Counter")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = { stableUser = StableUser("John $counter", 30 + counter) }) {
      Text("Change Stable User")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = { unstableUser.age++ }) {
      Text("Mutate Unstable User")
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Example 7: Internal state tracking (full mode)
    InternalStateTrackingDemo()

    Spacer(modifier = Modifier.height(16.dp))

    // Example 8: Auto-updating state (full mode)
    AutoUpdatingStateDemo()

    Spacer(modifier = Modifier.height(16.dp))

    // Example 9: CompositionLocal tracking
    CompositionLocalTrackingDemo()
  }
}

/**
 * Example 1: Basic recomposition tracking.
 *
 * Just add @TraceRecomposition - tracking is automatic!
 * The compiler injects tracking code during IR/FIR transformation.
 *
 * Expected Logcat output:
 * ```
 * D/Recomposition: [Recomposition #1] TrackedCounterDisplay
 * D/Recomposition:   └─ count: Int stable (0)
 * D/Recomposition: [Recomposition #2] TrackedCounterDisplay
 * D/Recomposition:   └─ count: Int changed (0 → 1)
 * ```
 */
@TraceRecomposition(threshold = 3)
@Composable
fun TrackedCounterDisplay(
  count: Int,
  content: @Composable () -> Unit,
) {
  Text(
    text = "Count: $count",
    modifier = Modifier.padding(16.dp),
  )

  content.invoke()
}

/**
 * Example 2: Tracking with custom tag for filtering.
 *
 * Use tags to group related screens or features.
 * You can filter Logcat by tag: "Recomposition" or search for "user-profile"
 *
 * Expected Logcat output:
 * ```
 * D/Recomposition: [Recomposition #1] TrackedUserProfile (tag: user-profile)
 * D/Recomposition:   └─ user: StableUser stable (StableUser@abc123)
 * D/Recomposition: [Recomposition #2] TrackedUserProfile (tag: user-profile)
 * D/Recomposition:   └─ user: StableUser changed (StableUser@abc123 → StableUser@def456)
 * ```
 */
@TraceRecomposition(tag = "user-profile")
@Composable
fun TrackedUserProfile(user: StableUser) {
  Column(modifier = Modifier.padding(16.dp)) {
    Text("User Profile")
    Text("Name: ${user.name}")
    Text("Age: ${user.age}")
  }
}

/**
 * Example 3: Tracking with threshold to reduce noise.
 *
 * This composable will only start logging after the 3rd recomposition.
 * Useful for frequently recomposing screens where you only care about
 * excessive recompositions.
 *
 * Expected Logcat output (only appears after 3rd recomposition):
 * ```
 * D/Recomposition: [Recomposition #3] TrackedUnstableUserCard
 * D/Recomposition:   ├─ user: UnstableUser unstable (UnstableUser@xyz789)
 * D/Recomposition:   └─ Unstable parameters: [user]
 * ```
 */
@TraceRecomposition(tag = "user-card", threshold = 3)
@Composable
fun TrackedUnstableUserCard(user: UnstableUser) {
  Column(modifier = Modifier.padding(16.dp)) {
    Text("Unstable User Card")
    Text("Name: ${user.name}")
    Text("Age: ${user.age}")
  }
}

/**
 * Example 4: Tracking composable with mixed parameter stability.
 *
 * Shows how different parameter types are tracked:
 * - String: stable primitive
 * - Int: stable primitive
 * - UnstableUser: unstable due to mutable properties
 *
 * Expected Logcat output:
 * ```
 * D/Recomposition: [Recomposition #5] TrackedMixedParameters (tag: mixed-example)
 * D/Recomposition:   ├─ title: String stable (Mixed Stability Example)
 * D/Recomposition:   ├─ count: Int changed (4 → 5)
 * D/Recomposition:   ├─ user: UnstableUser unstable (UnstableUser@abc)
 * D/Recomposition:   └─ Unstable parameters: [user]
 * ```
 */
@TraceRecomposition(tag = "mixed-example", threshold = 5)
@Composable
fun TrackedMixedParameters(
  title: String,
  count: Int,
  user: StableUser,
) {
  Column(modifier = Modifier.padding(16.dp)) {
    Text(title)
    Text("Count: $count")
    Text("User: ${user.name} (${user.age})")
  }
}

/**
 * Example 5: Tracking composable with lambda parameters.
 *
 * Lambdas are stable in Compose, so they won't cause recomposition
 * unless their capture changes.
 */
@TraceRecomposition(tag = "button-click")
@Composable
fun TrackedActionButton(
  text: String,
  onClick: () -> Unit,
) {
  Button(onClick = onClick) {
    Text(text)
  }
}

/**
 * Example 6: Understanding when recomposition happens.
 *
 * This composable demonstrates common scenarios:
 * - State changes trigger recomposition
 * - Unstable parameters cause recomposition
 * - Stable parameters with changed values trigger recomposition
 */
@TraceRecomposition(tag = "demo")
@Composable
fun RecompositionDemo() {
  // Local state - changes will trigger recomposition
  var localCounter by remember { mutableIntStateOf(0) }

  Column(modifier = Modifier.padding(16.dp)) {
    Text("Recomposition Demo")
    Text("Local Counter: $localCounter")

    Button(onClick = { localCounter++ }) {
      Text("This will trigger recomposition")
    }
  }
}

/**
 * Example 7: Full tracking mode - Internal state and derived state demo.
 *
 * This composable demonstrates the "full" tracking mode capability
 * to track internal state changes (remember values, derived state).
 *
 * In "standard" mode, only parameter changes are tracked.
 * In "full" mode, the compiler also tracks:
 * - mutableStateOf / mutableIntStateOf changes
 * - derivedStateOf changes
 * - Other remember slot value changes
 *
 * To enable full tracking mode, set in build.gradle.kts:
 * ```kotlin
 * composeStabilityAnalyzer {
 *     trackingMode.set("full")
 * }
 * ```
 *
 * Expected Logcat output (full mode):
 * ```
 * D/Recomposition: [Recomposition #1] InternalStateTrackingDemo (tag: internal-state)
 * D/Recomposition:   ├─ title: param stable (State Demo)
 * D/Recomposition:   ├─ intSlot:2: slot_modified changed (0 → 1)
 * D/Recomposition:   ├─ derivedValue: slot_modified changed (Derived: 0 → Derived: 2)
 * D/Recomposition:   └─ ⚡ Internal state change detected
 * ```
 */
@TraceRecomposition(tag = "internal-state")
@Composable
fun InternalStateTrackingDemo(title: String = "State Demo") {
  // Internal mutableIntStateOf - tracked in full mode
  var counter by remember { mutableIntStateOf(0) }

  // Derived state - also tracked in full mode
  val doubledValue by remember { derivedStateOf { counter * 2 } }

  // Another derived state depending on doubledValue
  val formattedValue = remember(doubledValue) { "Derived: $doubledValue" }

  Column(modifier = Modifier.padding(16.dp)) {
    Text(title)
    Text("Counter: $counter")
    Text("Doubled: $doubledValue")
    Text(formattedValue)

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = { counter++ }) {
      Text("Increment (triggers internal state change)")
    }
  }
}

/**
 * Example 8: Auto-updating internal state demo.
 *
 * Demonstrates automatic internal state updates via LaunchedEffect.
 * Useful for testing full tracking mode with continuous recompositions.
 *
 * The internal state increments every second, triggering recomposition.
 * In full tracking mode, you'll see the slot data changes in Logcat.
 */
@TraceRecomposition(tag = "auto-state")
@Composable
fun AutoUpdatingStateDemo() {
  // State that auto-increments every second
  var tick by remember { mutableIntStateOf(0) }

  // Derived states based on tick
  val isEven by remember { derivedStateOf { tick % 2 == 0 } }
  val phase = remember(tick) { if (isEven) "Even phase" else "Odd phase" }

  Column(modifier = Modifier.padding(16.dp)) {
    Text("Auto-updating State Demo")
    Text("Tick: $tick")
    Text("Is Even: $isEven")
    Text("Phase: $phase")
  }

  // Auto-increment tick every second
  LaunchedEffect(Unit) {
    while (currentCoroutineContext().isActive) {
      delay(1.seconds)
      tick++
    }
  }
}

/**
 * Example 9: CompositionLocal tracking demo.
 *
 * Tests whether full tracking mode can detect CompositionLocal value changes.
 * CompositionLocal values are provided via CompositionLocalProvider and read
 * implicitly by child composables.
 *
 * This example uses LocalTextStyle to demonstrate:
 * - Providing different TextStyle values dynamically
 * - Tracking when CompositionLocal values change
 */
@Composable
fun CompositionLocalTrackingDemo() {
  var useLargeText by remember { mutableStateOf(false) }

  val textStyle = if (useLargeText) {
    TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
  } else {
    TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
  }

  Column(modifier = Modifier.padding(16.dp)) {
    Text("CompositionLocal Tracking Demo")

    Spacer(modifier = Modifier.height(8.dp))

    // Provide different TextStyle via CompositionLocal
    CompositionLocalProvider(LocalTextStyle provides textStyle) {
      // This child reads LocalTextStyle implicitly
      TrackedTextStyleConsumer()
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = { useLargeText = !useLargeText }) {
      Text(if (useLargeText) "Switch to Small Text" else "Switch to Large Text")
    }
  }
}

/**
 * A composable that reads LocalTextStyle and displays text.
 * In full tracking mode, we want to see if CompositionLocal changes are tracked.
 */
@TraceRecomposition(tag = "text-style-consumer")
@Composable
fun TrackedTextStyleConsumer() {
  // Read the current TextStyle from CompositionLocal
  val currentStyle = LocalTextStyle.current

  Column {
    Text(
      text = "This text uses LocalTextStyle",
      style = currentStyle,
    )
    Text(
      text = "Font size: ${currentStyle.fontSize}",
      style = currentStyle,
    )
    Text(
      text = "Font weight: ${currentStyle.fontWeight}",
      style = currentStyle,
    )
  }
}

/**
 * IMPORTANT NOTES:
 *
 * 1. Always enable logging in debug builds only:
 *    ```kotlin
 *    class MyApp : Application() {
 *        override fun onCreate() {
 *            super.onCreate()
 *            ComposeStabilityAnalyzer.setEnabled(BuildConfig.DEBUG)
 *        }
 *    }
 *    ```
 *
 * 2. Tracking code is automatically injected by the compiler!
 *    Just add @TraceRecomposition annotation and build your project.
 *    No manual code needed!
 *
 * 3. Use appropriate thresholds:
 *    - threshold = 1: Log every recomposition (verbose)
 *    - threshold = 3: Log after 3 recompositions (moderate)
 *    - threshold = 10: Only log excessive recompositions (quiet)
 *
 * 4. Use tags to organize logs:
 *    - Feature-based: "auth", "profile", "checkout"
 *    - Screen-based: "home-screen", "detail-screen"
 *    - Component-based: "list-item", "card", "header"
 *
 * 5. Understanding stability:
 *    - STABLE: Won't cause recomposition unless value changes
 *      (primitives, immutable classes, lambdas)
 *    - UNSTABLE: Might cause recomposition
 *      (mutable classes, interfaces, unknown types)
 *    - RUNTIME: Stability determined at runtime
 *      (generics, complex types)
 *
 * 6. Performance impact:
 *    - Minimal in debug builds (tracking code is lightweight)
 *    - Always disable in production builds
 *    - Don't track every composable - be selective
 */
