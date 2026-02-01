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
package com.skydoves.compose.stability.idea

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for StabilityAnalysisConstants utility functions and constants.
 */
class StabilityAnalysisConstantsTest {

  @Test
  fun testIsPrimitive_kotlinPrimitives() {
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Int"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Boolean"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.String"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Long"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Double"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Float"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Char"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Byte"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Short"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Unit"))
    assertTrue(StabilityAnalysisConstants.isPrimitive("kotlin.Nothing"))
  }

  @Test
  fun testIsPrimitive_nonPrimitives() {
    assertFalse(StabilityAnalysisConstants.isPrimitive("kotlin.collections.List"))
    assertFalse(StabilityAnalysisConstants.isPrimitive("com.example.User"))
    assertFalse(StabilityAnalysisConstants.isPrimitive("kotlin.Any"))
    assertFalse(StabilityAnalysisConstants.isPrimitive("java.lang.String"))
  }

  @Test
  fun testIsKnownStable_composeTypes() {
    assertTrue(StabilityAnalysisConstants.isKnownStable("androidx.compose.ui.Modifier"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("androidx.compose.ui.graphics.Color"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("androidx.compose.ui.unit.Dp"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("androidx.compose.ui.unit.TextUnit"))
  }

  @Test
  fun testIsKnownStable_immutableCollections() {
    assertTrue(
      StabilityAnalysisConstants.isKnownStable("kotlinx.collections.immutable.ImmutableList"),
    )
    assertTrue(
      StabilityAnalysisConstants.isKnownStable("kotlinx.collections.immutable.ImmutableSet"),
    )
    assertTrue(
      StabilityAnalysisConstants.isKnownStable("kotlinx.collections.immutable.ImmutableMap"),
    )
    assertTrue(StabilityAnalysisConstants.isKnownStable("com.google.common.collect.ImmutableList"))
  }

  @Test
  fun testIsKnownStable_kotlinStandardTypes() {
    assertTrue(StabilityAnalysisConstants.isKnownStable("kotlin.Pair"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("kotlin.Triple"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("kotlin.time.Duration"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("kotlin.ranges.IntRange"))
  }

  @Test
  fun testIsKnownStable_javaTypes() {
    assertTrue(StabilityAnalysisConstants.isKnownStable("java.math.BigInteger"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("java.math.BigDecimal"))
    assertTrue(StabilityAnalysisConstants.isKnownStable("java.util.Locale"))
  }

  @Test
  fun testIsKnownStable_unstableTypes() {
    assertFalse(StabilityAnalysisConstants.isKnownStable("kotlin.collections.MutableList"))
    assertFalse(StabilityAnalysisConstants.isKnownStable("com.example.MutableUser"))
    assertFalse(StabilityAnalysisConstants.isKnownStable("java.util.ArrayList"))
  }

  @Test
  fun testIsKnownStableBySimpleName_composeTypes() {
    assertTrue(StabilityAnalysisConstants.isKnownStableBySimpleName("Modifier"))
    assertTrue(StabilityAnalysisConstants.isKnownStableBySimpleName("Color"))
    assertTrue(StabilityAnalysisConstants.isKnownStableBySimpleName("Dp"))
    assertTrue(StabilityAnalysisConstants.isKnownStableBySimpleName("TextUnit"))
  }

  @Test
  fun testIsKnownStableBySimpleName_kotlinTypes() {
    assertTrue(StabilityAnalysisConstants.isKnownStableBySimpleName("Pair"))
    assertTrue(StabilityAnalysisConstants.isKnownStableBySimpleName("Triple"))
    assertTrue(StabilityAnalysisConstants.isKnownStableBySimpleName("Duration"))
  }

  @Test
  fun testIsKnownStableBySimpleName_unstableTypes() {
    assertFalse(StabilityAnalysisConstants.isKnownStableBySimpleName("MutableList"))
    assertFalse(StabilityAnalysisConstants.isKnownStableBySimpleName("ArrayList"))
    assertFalse(StabilityAnalysisConstants.isKnownStableBySimpleName("User"))
  }

  @Test
  fun testIsMutableCollection_mutableCollections() {
    assertTrue(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.MutableList"))
    assertTrue(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.MutableSet"))
    assertTrue(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.MutableMap"))
    assertTrue(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.ArrayList"))
    assertTrue(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.HashMap"))
    assertTrue(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.HashSet"))
  }

  @Test
  fun testIsMutableCollection_immutableCollections() {
    assertFalse(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.List"))
    assertFalse(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.Set"))
    assertFalse(StabilityAnalysisConstants.isMutableCollection("kotlin.collections.Map"))
    assertFalse(
      StabilityAnalysisConstants.isMutableCollection("kotlinx.collections.immutable.ImmutableList"),
    )
  }

  @Test
  fun testIsStandardCollection_standardCollections() {
    assertTrue(StabilityAnalysisConstants.isStandardCollection("kotlin.collections.List"))
    assertTrue(StabilityAnalysisConstants.isStandardCollection("kotlin.collections.Set"))
    assertTrue(StabilityAnalysisConstants.isStandardCollection("kotlin.collections.Map"))
    assertTrue(StabilityAnalysisConstants.isStandardCollection("kotlin.collections.Collection"))
    assertTrue(StabilityAnalysisConstants.isStandardCollection("kotlin.collections.Iterable"))
    assertTrue(StabilityAnalysisConstants.isStandardCollection("kotlin.sequences.Sequence"))
  }

  @Test
  fun testIsStandardCollection_nonStandardCollections() {
    assertFalse(StabilityAnalysisConstants.isStandardCollection("kotlin.collections.MutableList"))
    assertFalse(
      StabilityAnalysisConstants.isStandardCollection(
        "kotlinx.collections.immutable.ImmutableList",
      ),
    )
    assertFalse(StabilityAnalysisConstants.isStandardCollection("java.util.List"))
  }

  @Test
  fun testIsFunctionType_kotlinFunctions() {
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.Function0"))
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.Function1"))
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.Function2"))
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.Function10"))
  }

  @Test
  fun testIsFunctionType_jvmFunctions() {
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.jvm.functions.Function0"))
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.jvm.functions.Function1"))
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.jvm.functions.Function2"))
  }

  @Test
  fun testIsFunctionType_suspendFunctions() {
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.coroutines.SuspendFunction0"))
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.coroutines.SuspendFunction1"))
    assertTrue(StabilityAnalysisConstants.isFunctionType("kotlin.coroutines.SuspendFunction2"))
  }

  @Test
  fun testIsFunctionType_nonFunctionTypes() {
    assertFalse(StabilityAnalysisConstants.isFunctionType("kotlin.Int"))
    assertFalse(StabilityAnalysisConstants.isFunctionType("kotlin.String"))
    assertFalse(StabilityAnalysisConstants.isFunctionType("com.example.Function"))
  }

  @Test
  fun testPrimitiveTypeFqNames_containsAllKotlinPrimitives() {
    val primitives = StabilityAnalysisConstants.PRIMITIVE_TYPE_FQ_NAMES

    assertTrue(primitives.contains("kotlin.Boolean"))
    assertTrue(primitives.contains("kotlin.Byte"))
    assertTrue(primitives.contains("kotlin.Short"))
    assertTrue(primitives.contains("kotlin.Int"))
    assertTrue(primitives.contains("kotlin.Long"))
    assertTrue(primitives.contains("kotlin.Float"))
    assertTrue(primitives.contains("kotlin.Double"))
    assertTrue(primitives.contains("kotlin.Char"))
    assertTrue(primitives.contains("kotlin.String"))
    assertTrue(primitives.contains("kotlin.Unit"))
    assertTrue(primitives.contains("kotlin.Nothing"))
  }

  @Test
  fun testMutableCollectionTypes_containsCommonMutableTypes() {
    val mutableTypes = StabilityAnalysisConstants.MUTABLE_COLLECTION_TYPES

    assertTrue(mutableTypes.contains("kotlin.collections.MutableList"))
    assertTrue(mutableTypes.contains("kotlin.collections.MutableSet"))
    assertTrue(mutableTypes.contains("kotlin.collections.MutableMap"))
    assertTrue(mutableTypes.contains("kotlin.collections.ArrayList"))
    assertTrue(mutableTypes.contains("kotlin.collections.HashMap"))
  }

  @Test
  fun testStandardCollectionTypes_containsImmutableInterfaces() {
    val standardTypes = StabilityAnalysisConstants.STANDARD_COLLECTION_TYPES

    assertTrue(standardTypes.contains("kotlin.collections.List"))
    assertTrue(standardTypes.contains("kotlin.collections.Set"))
    assertTrue(standardTypes.contains("kotlin.collections.Map"))
    assertTrue(standardTypes.contains("kotlin.collections.Collection"))
    assertTrue(standardTypes.contains("kotlin.sequences.Sequence"))
  }

  @Test
  fun testKnownStableTypes_includesComposeTypes() {
    val stableTypes = StabilityAnalysisConstants.KNOWN_STABLE_TYPES

    assertTrue(stableTypes.contains("androidx.compose.ui.Modifier"))
    assertTrue(stableTypes.contains("androidx.compose.ui.graphics.Color"))
    assertTrue(stableTypes.contains("androidx.compose.ui.unit.Dp"))
  }

  @Test
  fun testKnownStableTypes_includesImmutableCollections() {
    val stableTypes = StabilityAnalysisConstants.KNOWN_STABLE_TYPES

    assertTrue(stableTypes.contains("kotlinx.collections.immutable.ImmutableList"))
    assertTrue(stableTypes.contains("com.google.common.collect.ImmutableList"))
  }

  @Test
  fun testKnownStableTypeNames_matchesKnownStableTypes() {
    // Verify that simple names match their FQ counterparts
    assertTrue(StabilityAnalysisConstants.KNOWN_STABLE_TYPE_NAMES.contains("Modifier"))
    assertTrue(StabilityAnalysisConstants.KNOWN_STABLE_TYPE_NAMES.contains("Color"))
    assertTrue(StabilityAnalysisConstants.KNOWN_STABLE_TYPE_NAMES.contains("Dp"))
    assertTrue(StabilityAnalysisConstants.KNOWN_STABLE_TYPE_NAMES.contains("ImmutableList"))
    assertTrue(StabilityAnalysisConstants.KNOWN_STABLE_TYPE_NAMES.contains("Pair"))
    assertTrue(StabilityAnalysisConstants.KNOWN_STABLE_TYPE_NAMES.contains("Triple"))
  }

  @Test
  fun testIsFunctionTypeBySimpleName_kotlinFunctionInterfaces() {
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Function0"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Function1"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Function2"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Function10"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Function99"))
  }

  @Test
  fun testIsFunctionTypeBySimpleName_kotlinSuspendFunctionInterfaces() {
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("SuspendFunction0"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("SuspendFunction1"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("SuspendFunction2"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("SuspendFunction10"))
  }

  @Test
  fun testIsFunctionTypeBySimpleName_nonFunctionSimpleNames() {
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Function"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("FunctionX"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Function-1"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("SuspendFunction"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("SuspendFunctionX"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("KFunction"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("Int"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("ComposableAction"))
  }

  @Test
  fun testIsFunctionType_composeComposableFunctions() {
    assertTrue(
      StabilityAnalysisConstants.isFunctionType(
        "androidx.compose.runtime.internal.ComposableFunction0",
      ),
    )
    assertTrue(
      StabilityAnalysisConstants.isFunctionType(
        "androidx.compose.runtime.internal.ComposableFunction1",
      ),
    )
    assertTrue(
      StabilityAnalysisConstants.isFunctionType(
        "androidx.compose.runtime.internal.ComposableFunction2",
      ),
    )
  }

  @Test
  fun testIsFunctionTypeBySimpleName_composeComposableFunctions() {
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("ComposableFunction0"))
    assertTrue(StabilityAnalysisConstants.isFunctionTypeBySimpleName("ComposableFunction2"))
    assertFalse(StabilityAnalysisConstants.isFunctionTypeBySimpleName("ComposableFunction"))
  }
}
