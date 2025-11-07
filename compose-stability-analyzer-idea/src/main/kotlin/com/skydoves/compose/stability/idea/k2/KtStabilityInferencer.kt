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
package com.skydoves.compose.stability.idea.k2

import com.skydoves.compose.stability.idea.StabilityAnalysisConstants
import com.skydoves.compose.stability.idea.StabilityConstants
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability

/**
 * K2 Analysis API-based stability inferencer.
 * Analyzes Kotlin types using K2 semantic analysis for accurate stability determination.
 *
 * **Important: Analysis follows the same order as StabilityAnalyzer for consistency:**
 * 0. Typealiases - expand to actual type first
 * 1. Nullable types (MUST be first)
 * 2. Type parameters (T, E, K, V) - RUNTIME/Parameter
 * 3. Function types (lambdas, suspend, @Composable) - STABLE
 * 4. User settings (ignored types, custom stable types)
 * 5. Known stable types
 * 6. Known stable by simple name
 * 7. @Stable/@Immutable annotations
 * 8. Primitives
 * 9. String
 * 10. Unit/Nothing
 * 11. Functions (fallback check)
 * 12. Mutable collections (UNSTABLE)
 * 13. Kotlinx immutable collections (STABLE)
 * 14. Standard collections (RUNTIME)
 * 15. Value classes
 * 16. Enums
 * 17. @Parcelize - check properties
 * 18. Interfaces (RUNTIME)
 * 19. Abstract classes (RUNTIME)
 * 20. Regular classes - property analysis (returns STABLE/UNSTABLE if definitive)
 * 21. @StabilityInferred (RUNTIME - only for uncertain cases)
 */
internal class KtStabilityInferencer {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  // Cycle detection for recursive types
  private val analyzingTypes = ThreadLocal.withInitial { mutableSetOf<String>() }

  /**
   * Analyzes a Kotlin type to determine its stability.
   * Main entry point for K2-based stability analysis.
   */
  context(KaSession)
  internal fun ktStabilityOf(type: KaType): KtStability {
    val originalTypeString = try {
      type.render(position = org.jetbrains.kotlin.types.Variance.INVARIANT)
    } catch (e: StackOverflowError) {
      return KtStability.Runtime(
        className = "Unknown",
        reason = "Unable to render type due to complexity",
      )
    }

    val currentlyAnalyzing = analyzingTypes.get()
    if (originalTypeString in currentlyAnalyzing) {
      return KtStability.Runtime(
        className = originalTypeString,
        reason = StabilityConstants.Messages.CIRCULAR_REFERENCE,
      )
    }

    currentlyAnalyzing.add(originalTypeString)
    try {
      return ktStabilityOfInternal(type, originalTypeString)
    } finally {
      currentlyAnalyzing.remove(originalTypeString)
    }
  }

  /**
   * Internal implementation separated for proper cleanup.
   */
  context(KaSession)
  private fun ktStabilityOfInternal(type: KaType, originalTypeString: String): KtStability {
    // 0. Expand typealiases first - resolve typealias to actual type
    // Use fullyExpandedType to get the actual underlying type
    val expandedType = type.fullyExpandedType

    // 1. Nullable types - MUST be checked first to strip nullability
    // Use KaTypeNullability enum for compatibility with Android Studio AI-243
    val nonNullableType = if (expandedType.isMarkedNullable) {
      expandedType.withNullability(KaTypeNullability.NON_NULLABLE)
    } else {
      expandedType
    }

    // 2. Check if it's a type parameter (e.g., T, E, K, V in generics)
    if (nonNullableType is org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType) {
      val paramName = nonNullableType.name.asString()
      return KtStability.Parameter(parameterName = paramName)
    }

    // 3. Check if it's a function type (including lambdas, suspend functions, @Composable functions)
    // Function types are ALWAYS stable (captured values are checked separately in Compose compiler)
    val isFunctionType = nonNullableType.isFunctionType ||
      nonNullableType.isSuspendFunctionType ||
      originalTypeString.contains("->")

    if (isFunctionType) {
      // Check if it's a @Composable function - check BOTH annotations and string representation
      val isComposable = type.annotations.any { annotation ->
        annotation.classId?.asSingleFqName()?.asString() ==
          "androidx.compose.runtime.Composable"
      } || nonNullableType.annotations.any { annotation ->
        annotation.classId?.asSingleFqName()?.asString() ==
          "androidx.compose.runtime.Composable"
      } || originalTypeString.contains("@Composable")

      val isSuspend = nonNullableType.isSuspendFunctionType ||
        originalTypeString.contains("suspend")

      return KtStability.Certain(
        stable = true,
        reason = when {
          isComposable -> StabilityConstants.Messages.COMPOSABLE_FUNCTION_STABLE
          isSuspend -> StabilityConstants.Messages.SUSPEND_FUNCTION_STABLE
          else -> StabilityConstants.Messages.FUNCTION_STABLE
        },
      )
    }

    // 4. Get the class symbol
    val classSymbol = nonNullableType.expandedSymbol as? KaClassLikeSymbol
      ?: return KtStability.Runtime(
        className = originalTypeString,
        reason = "Unable to resolve type",
      )

    // 4b. Double-check if the class symbol is actually a function type (FunctionN interface)
    // This catches cases like "@Composable ColumnScope.() -> Unit"
    val fqName = classSymbol.classId?.asSingleFqName()?.asString()
    if (fqName != null && StabilityAnalysisConstants.isFunctionType(fqName)) {
      // Check if it's a @Composable function - check BOTH original and non-nullable type
      val isComposable = type.annotations.any { annotation ->
        annotation.classId?.asSingleFqName()?.asString() ==
          "androidx.compose.runtime.Composable"
      } || nonNullableType.annotations.any { annotation ->
        annotation.classId?.asSingleFqName()?.asString() ==
          "androidx.compose.runtime.Composable"
      } || originalTypeString.contains("@Composable")

      val isSuspend = nonNullableType.isSuspendFunctionType ||
        originalTypeString.contains("suspend")

      return KtStability.Certain(
        stable = true,
        reason = when {
          isComposable -> StabilityConstants.Messages.COMPOSABLE_FUNCTION_STABLE
          isSuspend -> StabilityConstants.Messages.SUSPEND_FUNCTION_STABLE
          else -> StabilityConstants.Messages.FUNCTION_STABLE
        },
      )
    }

    // Analyze with empty tracking set
    return analyzeClassSymbol(classSymbol, emptySet())
  }

  /**
   * Recursively analyzes a class symbol to determine stability.
   * Follows the same analysis order as StabilityAnalyzer.analyzeType().
   *
   * @param declaration the class symbol to analyze
   * @param currentlyAnalyzing set of symbols being analyzed (prevents infinite recursion)
   */
  context(KaSession)
  private fun analyzeClassSymbol(
    declaration: KaClassLikeSymbol,
    currentlyAnalyzing: Set<KaClassLikeSymbol>,
  ): KtStability {
    // Check for circular references
    if (declaration in currentlyAnalyzing) {
      return KtStability.Certain(
        stable = true,
        reason = StabilityConstants.Messages.CIRCULAR_REFERENCE,
      )
    }

    val classSymbol = declaration as? KaClassSymbol
      ?: return KtStability.Unknown(
        declaration.name?.asString() ?: StabilityConstants.Strings.UNKNOWN,
      )

    val fqName = classSymbol.classId?.asSingleFqName()?.asString()
    val simpleName = classSymbol.name?.asString() ?: StabilityConstants.Strings.UNKNOWN

    // 4. Check user settings (before any analysis)
    if (shouldIgnoreType(fqName)) {
      return KtStability.Certain(
        stable = true,
        reason = "${StabilityConstants.Messages.IGNORED_BY_SETTINGS}: $fqName",
      )
    }

    // Check custom stable types from user configuration
    if (isCustomStableType(fqName)) {
      return KtStability.Certain(
        stable = true,
        reason = "${StabilityConstants.Messages.CUSTOM_STABLE_TYPE}: $fqName",
      )
    }

    // 5. Known stable types - check BEFORE value class analysis
    if (fqName != null && StabilityAnalysisConstants.isKnownStable(fqName)) {
      return KtStability.Certain(
        stable = true,
        reason = "${StabilityConstants.Messages.KNOWN_STABLE_TYPE}: $fqName",
      )
    }

    // 6. Check simple name for common Compose types (fallback for compiled libraries)
    if (StabilityAnalysisConstants.isKnownStableBySimpleName(simpleName)) {
      return KtStability.Certain(
        stable = true,
        reason = "${StabilityConstants.Messages.KNOWN_STABLE_TYPE}: $simpleName",
      )
    }

    // 7. Check for @Stable or @Immutable annotations
    if (classSymbol.hasStableAnnotation()) {
      return KtStability.Certain(
        stable = true,
        reason = StabilityConstants.Messages.STABLE_ANNOTATION,
      )
    }

    // 8. Primitives are always stable
    if (fqName != null && StabilityAnalysisConstants.isPrimitive(fqName)) {
      return KtStability.Certain(stable = true, reason = StabilityConstants.Messages.PRIMITIVE_TYPE)
    }

    // 9. String is stable
    if (fqName == "kotlin.String") {
      return KtStability.Certain(stable = true, reason = StabilityConstants.Messages.STRING_STABLE)
    }

    // 10. Unit and Nothing are stable
    if (fqName == "kotlin.Unit" || fqName == "kotlin.Nothing") {
      return KtStability.Certain(stable = true, reason = StabilityConstants.Messages.UNIT_STABLE)
    }

    // 11. Functions are stable (fallback check - should be caught in ktStabilityOf)
    if (fqName != null && StabilityAnalysisConstants.isFunctionType(fqName)) {
      return KtStability.Certain(
        stable = true,
        reason = StabilityConstants.Messages.FUNCTION_STABLE,
      )
    }

    // 12. Check for mutable collections (always unstable)
    if (fqName != null && StabilityAnalysisConstants.isMutableCollection(fqName)) {
      return KtStability.Certain(
        stable = false,
        reason = StabilityConstants.Messages.MUTABLE_COLLECTION_UNSTABLE,
      )
    }

    // 13. Check for kotlinx immutable collections (always stable)
    if (fqName != null && fqName.startsWith("kotlinx.collections.immutable.")) {
      if (fqName.contains("Immutable") || fqName.contains("Persistent")) {
        return KtStability.Certain(
          stable = true,
          reason = "Kotlinx immutable collection",
        )
      }
    }

    // 13b. Fallback check for immutable collections by simple name (for test code where FQN might not resolve)
    if (simpleName.contains("Immutable") || simpleName.contains("Persistent")) {
      // Double-check it's actually an immutable collection type
      if (simpleName in setOf(
          "ImmutableList",
          "ImmutableSet",
          "ImmutableMap",
          "ImmutableCollection",
          "PersistentList",
          "PersistentSet",
          "PersistentMap",
          "PersistentCollection",
        )
      ) {
        return KtStability.Certain(
          stable = true,
          reason = "Immutable collection (resolved by simple name)",
        )
      }
    }

    // 14. Standard collections (List, Set, Map) - RUNTIME check needed
    if (fqName != null && StabilityAnalysisConstants.isStandardCollection(fqName)) {
      return KtStability.Runtime(className = fqName)
    }

    // 15. Value classes (inline classes) - stability depends on underlying type
    if (classSymbol.isInlineClass()) {
      return analyzeValueClass(classSymbol, currentlyAnalyzing)
    }

    // 16. Enum classes are always stable
    if (classSymbol.classKind == KaClassKind.ENUM_CLASS) {
      return KtStability.Certain(stable = true, reason = StabilityConstants.Messages.ENUM_STABLE)
    }

    // 17. @Parcelize data classes - check only properties, ignore Parcelable interface
    val hasParcelize = classSymbol.annotations.any { annotation ->
      annotation.classId?.asSingleFqName()?.asString() == "kotlinx.parcelize.Parcelize"
    }
    if (hasParcelize) {
      val properties = classSymbol.declaredMemberScope.callables
        .filterIsInstance<KaPropertySymbol>()
        .toList()

      // Check for var properties
      if (properties.any { !it.isVal }) {
        return KtStability.Certain(
          stable = false,
          reason = "Has mutable (var) properties",
        )
      }

      // Check property type stability
      val allPropertiesStable = properties.all { property ->
        val propertyStability = ktStabilityOf(property.returnType)
        propertyStability.isStable()
      }

      if (allPropertiesStable) {
        return KtStability.Certain(
          stable = true,
          reason = "@Parcelize with all stable properties",
        )
      }
    }

    // 18. Interfaces - cannot determine (RUNTIME)
    if (classSymbol.classKind == KaClassKind.INTERFACE) {
      return KtStability.Runtime(
        className = fqName ?: simpleName,
        reason = "Interface type - actual implementation could be mutable",
      )
    }

    // 19. Abstract classes - cannot determine (RUNTIME)
    if (classSymbol.modality == KaSymbolModality.ABSTRACT) {
      return KtStability.Runtime(
        className = fqName ?: simpleName,
        reason = "Abstract class - actual implementation could be mutable",
      )
    }

    // 20. Regular classes - analyze properties first before checking @StabilityInferred
    val propertyStability = analyzeClassProperties(classSymbol, currentlyAnalyzing)

    return when {
      propertyStability is KtStability.Certain -> propertyStability
      else -> {
        // 20. Check @StabilityInferred: parameters=0 means stable, else runtime
        val stabilityInferredParams = classSymbol.getStabilityInferredParameters()
        when {
          stabilityInferredParams != null -> {
            if (stabilityInferredParams == 0) {
              KtStability.Certain(
                stable = true,
                reason = "Annotated with @StabilityInferred(parameters=0)",
              )
            } else {
              KtStability.Runtime(
                className = fqName ?: simpleName,
                reason = "Annotated with @StabilityInferred(parameters=$stabilityInferredParams)",
              )
            }
          }
          else -> propertyStability
        }
      }
    }
  }

  /**
   * Analyzes all properties of a class to determine overall stability.
   */
  context(KaSession)
  private fun analyzeClassProperties(
    classSymbol: KaClassSymbol,
    currentlyAnalyzing: Set<KaClassLikeSymbol>,
  ): KtStability {
    // Check superclass stability first
    val superClassStability = analyzeSuperclassStability(classSymbol, currentlyAnalyzing)

    // Get all properties from class
    val properties = classSymbol.declaredMemberScope.callables
      .filterIsInstance<KaPropertySymbol>()
      .toList()

    // If no properties, return superclass stability or stable
    if (properties.isEmpty()) {
      return when {
        superClassStability != null && !superClassStability.isStable() -> superClassStability
        else -> KtStability.Certain(
          stable = true,
          reason = StabilityConstants.Messages.NO_MUTABLE_PROPERTIES,
        )
      }
    }

    // First check for mutable properties
    val mutableProperties = properties.filter { !it.isVal }
    if (mutableProperties.isNotEmpty()) {
      val count = mutableProperties.size
      return KtStability.Certain(
        stable = false,
        reason = "Has $count mutable (var) ${if (count == 1) "property" else "properties"}",
      )
    }

    // Analyze property types
    val stabilities = mutableSetOf<KtStability>()
    val unstablePropertyTypes = mutableListOf<String>()

    for (property in properties) {
      // Analyze property type recursively
      val propertyType = property.returnType
      val propertyStability = ktStabilityOf(propertyType)

      stabilities.add(propertyStability)

      // Track unstable property types
      if (propertyStability.isUnstable()) {
        unstablePropertyTypes.add(propertyType.renderAsString())
      }
    }

    // If any property is unstable, class is unstable
    if (unstablePropertyTypes.isNotEmpty()) {
      return KtStability.Certain(
        stable = false,
        reason = "Has properties with unstable types: ${unstablePropertyTypes.joinToString(", ")}",
      )
    }

    // Combine with superclass stability
    if (superClassStability != null && !superClassStability.isStable()) {
      stabilities.add(superClassStability)
    }

    // All properties are val and stable, and superclass is stable
    if (stabilities.all { it.isStable() }) {
      return KtStability.Certain(
        stable = true,
        reason = StabilityConstants.Messages.ALL_PROPERTIES_STABLE,
      )
    }

    // Mixed stability - filter out Stable entries, keep only non-stable types
    // Stable doesn't affect the outcome, only Runtime/Parameter/Unknown matter
    val nonStableTypes = stabilities.filterNot { it.isStable() }.toSet()
    return if (nonStableTypes.isEmpty()) {
      // All were stable (shouldn't happen due to check above, but defensive)
      KtStability.Certain(
        stable = true,
        reason = StabilityConstants.Messages.ALL_PROPERTIES_STABLE,
      )
    } else {
      KtStability.Combined(nonStableTypes)
    }
  }

  /**
   * Analyzes superclass stability.
   * Returns the stability of the superclass, or null if no superclass or superclass is stable.
   */
  context(KaSession)
  private fun analyzeSuperclassStability(
    classSymbol: KaClassSymbol,
    currentlyAnalyzing: Set<KaClassLikeSymbol>,
  ): KtStability? {
    val superTypes = classSymbol.superTypes.filter { superType ->
      // Filter out Any and other common base types
      val fqName =
        superType.expandedSymbol?.classId?.asSingleFqName()?.asString()
      fqName != "kotlin.Any" && fqName != null
    }

    for (superType in superTypes) {
      val stability = ktStabilityOf(superType)

      // If superclass is unstable, propagate that
      if (stability.isUnstable()) {
        val superClassName = superType.expandedSymbol
          ?.classId?.asSingleFqName()?.asString() ?: superType.toString()
        return KtStability.Certain(
          stable = false,
          reason = "Extends unstable class $superClassName",
        )
      }

      // If superclass has runtime stability, propagate that
      if (stability is KtStability.Runtime ||
        stability is KtStability.Unknown ||
        stability is KtStability.Parameter
      ) {
        val superClassName = superType.expandedSymbol
          ?.classId?.asSingleFqName()?.asString() ?: superType.toString()
        return KtStability.Runtime(
          className = superClassName,
          reason = "Extends $superClassName which has runtime stability",
        )
      }

      // If superclass has combined stability, propagate that
      if (stability is KtStability.Combined) {
        return stability
      }
    }

    return null // All superclasses are stable or no superclasses
  }

  /**
   * Analyzes a value class to determine its stability.
   * Value classes inherit the stability of their underlying type.
   */
  context(KaSession)
  private fun analyzeValueClass(
    classSymbol: KaClassSymbol,
    currentlyAnalyzing: Set<KaClassLikeSymbol>,
  ): KtStability {
    // Value classes must have exactly one property
    val properties = classSymbol.declaredMemberScope.callables
      .filterIsInstance<KaPropertySymbol>()
      .toList()

    val underlyingProperty = properties.firstOrNull()
    if (underlyingProperty != null) {
      // Recursively analyze the underlying type
      val underlyingType = underlyingProperty.returnType
      val underlyingStability = ktStabilityOf(underlyingType)

      // Return the underlying stability with an updated reason
      val underlyingTypeName = underlyingType.renderAsString()
      val newReason = "Value class - stability inherited from underlying type ($underlyingTypeName)"

      return when (underlyingStability) {
        is KtStability.Certain -> underlyingStability.copy(reason = newReason)
        is KtStability.Runtime -> underlyingStability.copy(reason = newReason)
        is KtStability.Unknown -> underlyingStability
        is KtStability.Parameter -> underlyingStability
        is KtStability.Combined -> underlyingStability
      }
    }

    // Fallback to runtime if we can't determine
    return KtStability.Runtime(
      className = classSymbol.classId?.asSingleFqName()?.asString()
        ?: classSymbol.name?.asString() ?: StabilityConstants.Strings.UNKNOWN,
    )
  }

  /**
   * Check if a class has @Stable or @Immutable annotation.
   */
  context(KaSession)
  private fun KaClassSymbol.hasStableAnnotation(): Boolean {
    return annotations.any { annotation ->
      val fqName = annotation.classId?.asSingleFqName()?.asString()
      fqName == StabilityConstants.Annotations.STABLE_FQ ||
        fqName == StabilityConstants.Annotations.IMMUTABLE_FQ ||
        fqName == StabilityConstants.Annotations.ERROR_PRONE_IMMUTABLE_FQ ||
        fqName == StabilityConstants.Annotations.STABLE_FOR_ANALYSIS
    }
  }

  /**
   * TODO: Read @StabilityInferred parameters field using K2 Analysis API.
   * Returns null (conservative RUNTIME) until reliable API is found.
   */
  context(KaSession)
  private fun KaClassSymbol.getStabilityInferredParameters(): Int? {
    return null
  }

  /**
   * Check if type should be ignored based on user settings.
   */
  private fun shouldIgnoreType(fqName: String?): Boolean {
    if (fqName == null) return false
    if (!settings.isStabilityCheckEnabled) return true

    val ignoredPatterns = settings.getIgnoredPatternsAsRegex()
    return ignoredPatterns.any { pattern -> pattern.matches(fqName) }
  }

  /**
   * Check if type is custom stable based on user configuration.
   */
  private fun isCustomStableType(fqName: String?): Boolean {
    if (fqName == null) return false

    val customPatterns = settings.getCustomStableTypesAsRegex()
    return customPatterns.any { pattern -> pattern.matches(fqName) }
  }

  /**
   * Check if a class is a value class (inline class).
   */
  context(KaSession)
  private fun KaClassSymbol.isInlineClass(): Boolean {
    // Check for @JvmInline annotation (modern value classes)
    return annotations.any { annotation ->
      annotation.classId?.asSingleFqName()?.asString() == "kotlin.jvm.JvmInline"
    }
  }
}
