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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ReceiverStabilityInfoTest {

  @Test
  fun testReceiverStabilityInfo_extensionReceiver() {
    val receiver = ReceiverStabilityInfo(
      type = "ColumnScope",
      stability = ParameterStability.STABLE,
      reason = "Compose scope",
      receiverKind = ReceiverKind.EXTENSION,
    )

    assertEquals("ColumnScope", receiver.type)
    assertEquals(ParameterStability.STABLE, receiver.stability)
    assertEquals("Compose scope", receiver.reason)
    assertEquals(ReceiverKind.EXTENSION, receiver.receiverKind)
  }

  @Test
  fun testReceiverStabilityInfo_dispatchReceiver() {
    val receiver = ReceiverStabilityInfo(
      type = "MyClass",
      stability = ParameterStability.STABLE,
      reason = "class receiver",
      receiverKind = ReceiverKind.DISPATCH,
    )

    assertEquals("MyClass", receiver.type)
    assertEquals(ReceiverKind.DISPATCH, receiver.receiverKind)
  }

  @Test
  fun testReceiverStabilityInfo_contextReceiver() {
    val receiver = ReceiverStabilityInfo(
      type = "MyContext",
      stability = ParameterStability.UNSTABLE,
      reason = "has mutable state",
      receiverKind = ReceiverKind.CONTEXT,
    )

    assertEquals("MyContext", receiver.type)
    assertEquals(ParameterStability.UNSTABLE, receiver.stability)
    assertEquals(ReceiverKind.CONTEXT, receiver.receiverKind)
  }

  @Test
  fun testReceiverStabilityInfo_withoutReason() {
    val receiver = ReceiverStabilityInfo(
      type = "SimpleScope",
      stability = ParameterStability.STABLE,
      reason = null,
      receiverKind = ReceiverKind.EXTENSION,
    )

    assertEquals("SimpleScope", receiver.type)
    assertNull(receiver.reason)
  }

  @Test
  fun testReceiverStabilityInfo_unstable() {
    val receiver = ReceiverStabilityInfo(
      type = "MutableScope",
      stability = ParameterStability.UNSTABLE,
      reason = "has var properties",
      receiverKind = ReceiverKind.EXTENSION,
    )

    assertEquals(ParameterStability.UNSTABLE, receiver.stability)
    assertEquals("has var properties", receiver.reason)
  }

  @Test
  fun testReceiverStabilityInfo_runtime() {
    val receiver = ReceiverStabilityInfo(
      type = "GenericScope<T>",
      stability = ParameterStability.RUNTIME,
      reason = "generic type parameter",
      receiverKind = ReceiverKind.EXTENSION,
    )

    assertEquals(ParameterStability.RUNTIME, receiver.stability)
    assertEquals("generic type parameter", receiver.reason)
  }

  @Test
  fun testReceiverStabilityInfo_equality() {
    val receiver1 = ReceiverStabilityInfo(
      type = "ColumnScope",
      stability = ParameterStability.STABLE,
      reason = "test",
      receiverKind = ReceiverKind.EXTENSION,
    )

    val receiver2 = ReceiverStabilityInfo(
      type = "ColumnScope",
      stability = ParameterStability.STABLE,
      reason = "test",
      receiverKind = ReceiverKind.EXTENSION,
    )

    assertEquals(receiver1, receiver2)
    assertEquals(receiver1.hashCode(), receiver2.hashCode())
  }

  @Test
  fun testReceiverStabilityInfo_inequality_byType() {
    val receiver1 = ReceiverStabilityInfo(
      type = "ColumnScope",
      stability = ParameterStability.STABLE,
      receiverKind = ReceiverKind.EXTENSION,
    )

    val receiver2 = ReceiverStabilityInfo(
      type = "RowScope",
      stability = ParameterStability.STABLE,
      receiverKind = ReceiverKind.EXTENSION,
    )

    assertNotEquals(receiver1, receiver2)
  }

  @Test
  fun testReceiverStabilityInfo_inequality_byKind() {
    val receiver1 = ReceiverStabilityInfo(
      type = "MyScope",
      stability = ParameterStability.STABLE,
      receiverKind = ReceiverKind.EXTENSION,
    )

    val receiver2 = ReceiverStabilityInfo(
      type = "MyScope",
      stability = ParameterStability.STABLE,
      receiverKind = ReceiverKind.DISPATCH,
    )

    assertNotEquals(receiver1, receiver2)
  }

  @Test
  fun testReceiverStabilityInfo_inequality_byStability() {
    val receiver1 = ReceiverStabilityInfo(
      type = "MyScope",
      stability = ParameterStability.STABLE,
      receiverKind = ReceiverKind.EXTENSION,
    )

    val receiver2 = ReceiverStabilityInfo(
      type = "MyScope",
      stability = ParameterStability.UNSTABLE,
      receiverKind = ReceiverKind.EXTENSION,
    )

    assertNotEquals(receiver1, receiver2)
  }

  @Test
  fun testReceiverStabilityInfo_copy() {
    val original = ReceiverStabilityInfo(
      type = "ColumnScope",
      stability = ParameterStability.STABLE,
      reason = "original",
      receiverKind = ReceiverKind.EXTENSION,
    )

    val copy = original.copy(reason = "modified")

    assertEquals("ColumnScope", copy.type)
    assertEquals(ParameterStability.STABLE, copy.stability)
    assertEquals("modified", copy.reason)
    assertEquals(ReceiverKind.EXTENSION, copy.receiverKind)
    assertNotEquals(original, copy)
  }

  @Test
  fun testReceiverKind_values() {
    assertEquals(3, ReceiverKind.entries.size)
    assertEquals(ReceiverKind.EXTENSION, ReceiverKind.valueOf("EXTENSION"))
    assertEquals(ReceiverKind.DISPATCH, ReceiverKind.valueOf("DISPATCH"))
    assertEquals(ReceiverKind.CONTEXT, ReceiverKind.valueOf("CONTEXT"))
  }

  @Test
  fun testParameterStability_values() {
    assertEquals(4, ParameterStability.entries.size)
    assertEquals(ParameterStability.STABLE, ParameterStability.valueOf("STABLE"))
    assertEquals(ParameterStability.UNSTABLE, ParameterStability.valueOf("UNSTABLE"))
    assertEquals(ParameterStability.RUNTIME, ParameterStability.valueOf("RUNTIME"))
    assertEquals(ParameterStability.UNKNOWN, ParameterStability.valueOf("UNKNOWN"))
  }
}
