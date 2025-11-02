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

/**
 * Shared constants and logic for stability analysis.
 * Used by both PSI-based and K2-based analyzers.
 */
internal object StabilityAnalysisConstants {

  /**
   * Set of primitive type FqNames (for K2).
   */
  internal val PRIMITIVE_TYPE_FQ_NAMES: Set<String> = setOf(
    "kotlin.Boolean",
    "kotlin.Byte",
    "kotlin.Short",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Float",
    "kotlin.Double",
    "kotlin.Char",
    "kotlin.String",
    "kotlin.Unit",
    "kotlin.Nothing",
  )

  /**
   * Set of known mutable collection types.
   */
  internal val MUTABLE_COLLECTION_TYPES: Set<String> = setOf(
    "kotlin.collections.MutableList",
    "kotlin.collections.MutableSet",
    "kotlin.collections.MutableMap",
    "kotlin.collections.MutableCollection",
    "kotlin.collections.MutableIterable",
    "kotlin.collections.ArrayList",
    "kotlin.collections.HashSet",
    "kotlin.collections.HashMap",
    "kotlin.collections.LinkedHashSet",
    "kotlin.collections.LinkedHashMap",
  )

  /**
   * Set of standard collection interface types (require runtime check).
   * These are interfaces and the actual implementation could be mutable.
   */
  internal val STANDARD_COLLECTION_TYPES: Set<String> = setOf(
    "kotlin.collections.List",
    "kotlin.collections.Set",
    "kotlin.collections.Map",
    "kotlin.collections.Collection",
    "kotlin.collections.Iterable",
    "kotlin.sequences.Sequence",
  )

  /**
   * Set of known stable types from Compose and standard library.
   */
  internal val KNOWN_STABLE_TYPES: Set<String> = setOf(
    // Compose UI types
    "androidx.compose.ui.Modifier",
    "androidx.compose.ui.graphics.Color",
    "androidx.compose.ui.unit.Dp",
    "androidx.compose.ui.unit.TextUnit",
    "androidx.compose.ui.unit.IntOffset",
    "androidx.compose.ui.unit.IntSize",
    "androidx.compose.ui.geometry.Offset",
    "androidx.compose.ui.geometry.Size",
    "androidx.compose.ui.unit.DpOffset",
    "androidx.compose.ui.unit.DpSize",
    "androidx.compose.ui.unit.Constraints",

    // Compose Foundation shapes
    "androidx.compose.foundation.shape.RoundedCornerShape",
    "androidx.compose.foundation.shape.CircleShape",
    "androidx.compose.foundation.shape.CutCornerShape",
    "androidx.compose.foundation.shape.CornerBasedShape",
    "androidx.compose.foundation.shape.AbsoluteRoundedCornerShape",
    "androidx.compose.foundation.shape.AbsoluteCutCornerShape",
    "androidx.compose.ui.graphics.RectangleShape",

    // Compose text value classes
    "androidx.compose.ui.text.style.TextAlign",
    "androidx.compose.ui.text.style.TextDirection",
    "androidx.compose.ui.text.style.TextDecoration",
    "androidx.compose.ui.text.style.TextOverflow",
    "androidx.compose.ui.text.style.TextIndent",
    "androidx.compose.ui.text.style.TextGeometricTransform",
    "androidx.compose.ui.text.style.BaselineShift",
    "androidx.compose.ui.text.style.LineHeightStyle",
    "androidx.compose.ui.text.font.FontStyle",
    "androidx.compose.ui.text.font.FontWeight",
    "androidx.compose.ui.text.font.FontSynthesis",
    "androidx.compose.ui.text.intl.LocaleList",

    // Compose UI unit value classes
    "androidx.compose.ui.unit.LayoutDirection",

    // Compose graphics value classes
    "androidx.compose.ui.graphics.BlendMode",
    "androidx.compose.ui.graphics.FilterQuality",
    "androidx.compose.ui.graphics.StrokeCap",
    "androidx.compose.ui.graphics.StrokeJoin",
    "androidx.compose.ui.graphics.TileMode",
    "androidx.compose.ui.graphics.PathFillType",
    "androidx.compose.ui.graphics.ClipOp",
    "androidx.compose.ui.graphics.ColorFilter",
    "androidx.compose.ui.graphics.Shadow",
    "androidx.compose.ui.graphics.drawscope.DrawStyle",

    // Kotlin standard types
    "kotlin.Pair",
    "kotlin.Triple",
    "kotlin.Result",
    "kotlin.time.Duration",
    "kotlin.ranges.IntRange",
    "kotlin.ranges.LongRange",
    "kotlin.ranges.CharRange",

    // Java types
    "java.math.BigInteger",
    "java.math.BigDecimal",
    "java.util.Locale",

    // Kotlinx immutable collections
    "kotlinx.collections.immutable.ImmutableList",
    "kotlinx.collections.immutable.ImmutableSet",
    "kotlinx.collections.immutable.ImmutableMap",
    "kotlinx.collections.immutable.PersistentList",
    "kotlinx.collections.immutable.PersistentSet",
    "kotlinx.collections.immutable.PersistentMap",

    // Guava immutable collections
    "com.google.common.collect.ImmutableList",
    "com.google.common.collect.ImmutableEnumMap",
    "com.google.common.collect.ImmutableMap",
    "com.google.common.collect.ImmutableEnumSet",
    "com.google.common.collect.ImmutableSet",

    // Dagger
    "dagger.Lazy",

    // Protobuf types
    "com.google.protobuf.GeneratedMessage",
    "com.google.protobuf.GeneratedMessageLite",
    "com.google.protobuf.MessageLite",
  )

  /**
   * Set of known stable type names (simple names for fallback matching).
   */
  internal val KNOWN_STABLE_TYPE_NAMES: Set<String> = setOf(
    // Compose UI types
    "Modifier",
    "Color",
    "Dp",
    "TextUnit",
    "IntOffset",
    "IntSize",
    "Offset",
    "Size",
    "DpOffset",
    "DpSize",
    "Constraints",

    // Compose text value classes
    "TextAlign",
    "TextDirection",
    "TextDecoration",
    "TextOverflow",
    "TextIndent",
    "TextGeometricTransform",
    "BaselineShift",
    "LineHeightStyle",
    "FontStyle",
    "FontWeight",
    "FontSynthesis",
    "LocaleList",

    // Compose graphics value classes
    "BlendMode",
    "FilterQuality",
    "StrokeCap",
    "StrokeJoin",
    "TileMode",
    "PathFillType",
    "ClipOp",
    "ColorFilter",
    "Shadow",
    "DrawStyle",
    "LayoutDirection",

    // Kotlin standard types
    "Pair",
    "Triple",
    "Result",
    "Duration",
    "IntRange",
    "LongRange",
    "CharRange",

    // Java types
    "BigInteger",
    "BigDecimal",
    "Locale",

    // Kotlinx & Guava immutable collections
    "ImmutableList",
    "ImmutableSet",
    "ImmutableMap",
    "PersistentList",
    "PersistentSet",
    "PersistentMap",

    // Protobuf types
    "GeneratedMessage",
    "GeneratedMessageLite",
    "MessageLite",
  )

  /**
   * Check if a type's simple name is a known stable type.
   */
  internal fun isKnownStableBySimpleName(simpleName: String): Boolean {
    return simpleName in KNOWN_STABLE_TYPE_NAMES
  }

  /**
   * Check if a type's FqName is a known stable type.
   */
  internal fun isKnownStable(fqName: String): Boolean {
    return fqName in KNOWN_STABLE_TYPES
  }

  /**
   * Check if a type is a mutable collection.
   */
  internal fun isMutableCollection(fqName: String): Boolean {
    return fqName in MUTABLE_COLLECTION_TYPES
  }

  /**
   * Check if a type is a standard collection (requires runtime check).
   */
  internal fun isStandardCollection(fqName: String): Boolean {
    return fqName in STANDARD_COLLECTION_TYPES
  }

  /**
   * Check if a type is a primitive.
   */
  internal fun isPrimitive(fqName: String): Boolean {
    return fqName in PRIMITIVE_TYPE_FQ_NAMES
  }

  /**
   * Check if FqName is a function type.
   */
  internal fun isFunctionType(fqName: String): Boolean {
    return fqName.startsWith("kotlin.Function") ||
      fqName.startsWith("kotlin.jvm.functions.Function") ||
      fqName.startsWith("kotlin.coroutines.SuspendFunction")
  }
}
