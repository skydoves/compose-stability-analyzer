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

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.skydoves.compose.stability.idea.StabilityAnalysisConstants
import com.skydoves.compose.stability.idea.StabilityConstants
import com.skydoves.compose.stability.idea.containsTopLevelArrow
import com.skydoves.compose.stability.idea.settings.StabilityProjectSettingsState
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertyGetterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaType

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
internal class KtStabilityInferencer(
  private val project: Project? = null,
  private val usageSiteModule: Module? = null,
) {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  // Cycle detection for recursive types
  private val analyzingTypes = ThreadLocal.withInitial { mutableSetOf<String>() }

  /**
   * Analyzes a Kotlin type to determine its stability.
   * Main entry point for K2-based stability analysis.
   */
  internal fun KaSession.ktStabilityOf(type: KaType): KtStability {
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
  private fun KaSession.ktStabilityOfInternal(type: KaType, originalTypeString: String): KtStability {
    // 0. Expand typealiases first - resolve typealias to actual type
    // Use fullyExpandedType to get the actual underlying type
    val expandedType = type.fullyExpandedType

    // 1. Nullable types - MUST be checked first to strip nullability
    val nonNullableType = if (expandedType.isMarkedNullable) {
      withoutNullabilityReflective(expandedType)
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
      originalTypeString.containsTopLevelArrow()

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
    val symbolSimpleName = classSymbol.name?.asString()
    if ((fqName != null && StabilityAnalysisConstants.isFunctionType(fqName)) ||
      (
        fqName == null &&
          symbolSimpleName != null &&
          StabilityAnalysisConstants.isFunctionTypeBySimpleName(symbolSimpleName)
        )
    ) {
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

    // Analyze the class itself
    val classStability = analyzeClassSymbol(classSymbol, emptySet())

    // Analyze type arguments if present (e.g., UiResult<Unit> -> check if Unit is stable)
    val typeArgumentStabilities = analyzeTypeArguments(nonNullableType)

    // If class is already unstable, return it directly
    if (classStability.isUnstable()) {
      return classStability
    }

    // If any type argument is unstable, the whole type is unstable
    val unstableTypeArg = typeArgumentStabilities.find { it.isUnstable() }
    if (unstableTypeArg != null) {
      return KtStability.Certain(
        stable = false,
        reason = "Has unstable type argument: ${unstableTypeArg.getReasonString()}",
      )
    }

    // If any type argument is runtime, the whole type is runtime
    val runtimeTypeArg = typeArgumentStabilities.find { it is KtStability.Runtime }
    if (runtimeTypeArg != null) {
      return KtStability.Runtime(
        className = originalTypeString,
        reason = "Has runtime type argument: ${runtimeTypeArg.getReasonString()}",
      )
    }

    // If the class itself is not definitively stable (Runtime / Unknown / type Parameter /
    // Combined), stable type arguments must NOT promote it to stable. Keep the class's own
    // stability — e.g. a generic interface `Repo<String>` stays UNKNOWN even though String is
    // stable. (Pre-2.4.0 this only guarded Runtime; interfaces were Runtime, so it worked. Now
    // interfaces are Unknown, so the guard must cover every non-Certain case.)
    if (classStability !is KtStability.Certain) {
      return classStability
    }

    // All stable - return class stability with type args info if applicable
    val hasStableTypeArgs = typeArgumentStabilities.isNotEmpty() &&
      typeArgumentStabilities.all { it.isStable() }
    return if (hasStableTypeArgs) {
      KtStability.Certain(
        stable = true,
        reason = "${classStability.getReasonString()} (all type arguments are stable)",
      )
    } else {
      classStability
    }
  }

  /**
   * `KaPropertySymbol.isDelegatedProperty`, read reflectively.
   *
   * The property is not on every Analysis API surface the supported IDEs bundle, and binding to one
   * directly is what made 0.14.0 binary incompatible with 242, 243 and 251. When it cannot be read,
   * report false so a `var` still counts as destabilizing, which is the conservative direction.
   */
  private fun KaPropertySymbol.isDelegatedPropertyCompat(): Boolean = runCatching {
    javaClass.getMethod("isDelegatedProperty").invoke(this) as? Boolean ?: false
  }.getOrDefault(false)

  /**
   * Whether an `@StabilityInferred` bitmask says the class is known stable.
   *
   * Bits 0..n-1 mark which of the n type parameters the stability depends on; the bit at index n is
   * the "known stable" sentinel. With no type parameters that is bit 0, so 1 means stable and 0
   * means not stable. Compose only writes the sentinel when n is below 32, and a Kotlin shift masks
   * its operand to 5 bits, so a larger count has no sentinel to read.
   */
  private fun isKnownStableBitmask(symbol: KaClassSymbol, bitmask: Int): Boolean {
    val typeParameterCount = typeParameterCountReflective(symbol) ?: return false
    return typeParameterCount < 32 && ((bitmask shr typeParameterCount) and 1) == 1
  }

  /**
   * The number of type parameters on [symbol], or null when it cannot be determined.
   *
   * `KaClassSymbol.typeParameters` is not on the symbol's own interface in every Analysis API the
   * supported IDEs bundle, and binding to it directly is exactly what made 0.14.0 binary
   * incompatible with 242, 243 and 251. Read it reflectively, matching [analyzeTypeArguments], and
   * return null on failure so the caller can fall back to the conservative verdict rather than
   * mis-decode a bitmask.
   */
  private fun typeParameterCountReflective(symbol: KaClassSymbol): Int? = runCatching {
    (symbol::class.members.find { it.name == "typeParameters" }?.call(symbol) as? List<*>)?.size
  }.getOrNull()

  /**
   * Analyzes type arguments of a generic type.
   * Returns empty list if type has no type arguments.
   */
  private fun KaSession.analyzeTypeArguments(type: KaType): List<KtStability> {
    return try {
      // Use reflection to access typeArguments as it may differ between K2 versions
      val typeArgsMethod = type::class.members.find { it.name == "typeArguments" }
      if (typeArgsMethod != null) {
        @Suppress("UNCHECKED_CAST")
        val typeArgs = typeArgsMethod.call(type) as? List<*> ?: return emptyList()
        typeArgs.mapNotNull { typeArg ->
          // Each type argument is a KaTypeProjection which has a type property
          val typeProperty = typeArg?.let { arg ->
            arg::class.members.find { it.name == "type" }?.call(arg) as? KaType
          }
          typeProperty?.let { ktStabilityOf(it) }
        }
      } else {
        emptyList()
      }
    } catch (e: Exception) {
      // If we can't analyze type arguments, return empty
      emptyList()
    }
  }

  /**
   * Recursively analyzes a class symbol to determine stability.
   * Follows the same analysis order as StabilityAnalyzer.analyzeType().
   *
   * @param declaration the class symbol to analyze
   * @param currentlyAnalyzing set of symbols being analyzed (prevents infinite recursion)
   */
  private fun KaSession.analyzeClassSymbol(
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
    if (hasStableAnnotation(classSymbol)) {
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
    if ((fqName != null && StabilityAnalysisConstants.isFunctionType(fqName)) ||
      (fqName == null && StabilityAnalysisConstants.isFunctionTypeBySimpleName(simpleName))
    ) {
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
    if (isInlineClass(classSymbol)) {
      return analyzeValueClass(classSymbol, currentlyAnalyzing)
    }

    // 16. Enum classes are always stable
    if (classSymbol.classKind == KaClassKind.ENUM_CLASS) {
      return KtStability.Certain(stable = true, reason = StabilityConstants.Messages.ENUM_STABLE)
    }

    // An object is a singleton, so its identity never changes and its properties cannot
    // destabilize a parameter of its type (Stability.kt: `if (declaration.isObject) return Stable`).
    if (classSymbol.classKind == KaClassKind.OBJECT ||
      classSymbol.classKind == KaClassKind.COMPANION_OBJECT
    ) {
      return KtStability.Certain(stable = true, reason = "Object declarations are stable")
    }

    // 17. @Parcelize data classes - check only properties, ignore Parcelable interface
    val hasParcelize = classSymbol.annotations.any { annotation ->
      annotation.classId?.asSingleFqName()?.asString() == "kotlinx.parcelize.Parcelize"
    }
    if (hasParcelize) {
      val properties = classSymbol.declaredMemberScope.callables
        .filterIsInstance<KaPropertySymbol>()
        // Only state-storing members count; computed getter-only properties (no backing field)
        // are ignored, matching the Compose compiler (issue #178).
        .filterNot { it.isComputedGetterOnly() }
        .toList()

      // Check for var properties
      if (properties.any { !it.isVal && !it.isDelegatedPropertyCompat() }) {
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

    // 18. Interfaces - concrete implementation unknown (Compose 2.4.0: Unknown)
    if (classSymbol.classKind == KaClassKind.INTERFACE) {
      return KtStability.Unknown(fqName ?: simpleName)
    }

    // 19. Non-final (abstract/open) classes. As a direct parameter type the concrete subtype is
    // unknown, so they are at best UNKNOWN (Compose 2.4.0). But their fields still matter: an
    // inherited `var` or unstable-typed backing field makes the class — and any subclass —
    // unstable, so analyze the fields instead of short-circuiting to Unknown. A class explicitly
    // trusted via @Stable/@Immutable continues to regular property analysis below. Sealed classes
    // (modality SEALED) are not handled here and fall through as before (issue #178, #31).
    val isNonFinal = classSymbol.modality == KaSymbolModality.ABSTRACT ||
      classSymbol.modality == KaSymbolModality.OPEN
    if (isNonFinal) {
      val hasStabilityAnnotation = classSymbol.annotations.any { annotation ->
        val annotationFqName = annotation.classId?.asSingleFqName()?.asString()
        annotationFqName == "androidx.compose.runtime.Stable" ||
          annotationFqName == "androidx.compose.runtime.Immutable"
      }
      if (!hasStabilityAnnotation) {
        val fieldStability = analyzeClassProperties(classSymbol, currentlyAnalyzing)
        // No destabilizing state → the concrete subtype is still unknown → UNKNOWN. Otherwise
        // (a var / unstable / runtime field) propagate that verdict as-is: it holds regardless
        // of the concrete subtype, which is exactly what lets a subclass inherit the instability.
        return if (fieldStability.isStable()) {
          KtStability.Unknown(fqName ?: simpleName)
        } else {
          fieldStability
        }
      }
      // @Stable/@Immutable non-final classes continue to property analysis below.
    }

    // 19b. Cross-module types without @Stable/@Immutable/@StabilityInferred are UNSTABLE
    // Classes from other modules must be explicitly annotated to be considered stable
    // This prevents assuming stability for classes where we can't see the implementation
    // IMPORTANT: This check comes AFTER all built-in stable types (primitives, String, etc.)
    if (isFromDifferentModule(classSymbol)) {
      // Check if it has @StabilityInferred annotation
      val stabilityInferredParams = getStabilityInferredParameters(classSymbol)
      if (stabilityInferredParams == null) {
        // No @Stable, @Immutable, or @StabilityInferred annotation
        return KtStability.Certain(
          stable = false,
          reason = "External class without stability annotation",
        )
      }
      // The bitmask is not "0 means stable". Bits 0..n-1 mark which of the n type parameters the
      // stability depends on, and the bit at index n is a "known stable" sentinel; with no type
      // parameters that is bit 0, so 1 means stable and 0 means not stable. Reading it the other
      // way round reported cross-module unstable classes as stable.
      if (!isKnownStableBitmask(classSymbol, stabilityInferredParams)) {
        return KtStability.Runtime(
          className = fqName ?: simpleName,
          reason = "External class with @StabilityInferred(parameters=$stabilityInferredParams)",
        )
      }
      // Known stable per the sentinel bit: continue to other checks.
    }

    // 20. Regular classes (and sealed classes) - analyze properties first before checking @StabilityInferred
    val propertyStability = analyzeClassProperties(classSymbol, currentlyAnalyzing)

    return when {
      propertyStability is KtStability.Certain -> propertyStability
      else -> {
        // 20. Refine with @StabilityInferred, using the same sentinel rule as above. Only for
        // classes from another module: on a source class the annotation exists only after the
        // Compose plugin's own lowering, so reading it would make the verdict depend on plugin
        // ordering (issue #107), and the IDE would then disagree with stabilityDump.
        val stabilityInferredParams = getStabilityInferredParameters(classSymbol)
        when {
          stabilityInferredParams != null && isFromDifferentModule(classSymbol) -> {
            if (isKnownStableBitmask(classSymbol, stabilityInferredParams)) {
              KtStability.Certain(
                stable = true,
                reason = "Annotated with @StabilityInferred(parameters=$stabilityInferredParams)",
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
  private fun KaSession.analyzeClassProperties(
    classSymbol: KaClassSymbol,
    currentlyAnalyzing: Set<KaClassLikeSymbol>,
  ): KtStability {
    // Issue #31: Check if parent sealed class has @Immutable/@Stable
    val parentHasStabilityAnnotation = classSymbol.superTypes.any { superType ->
      val superClassSymbol = superType.expandedSymbol as? KaClassSymbol
      if (superClassSymbol != null) {
        // Check if superclass is sealed (has sealed subclasses)
        val isSealed = superClassSymbol.modality == KaSymbolModality.SEALED
        // Check if it has @Stable or @Immutable annotation
        val hasAnnotation = superClassSymbol.annotations.any { annotation ->
          val annotationFqName = annotation.classId?.asSingleFqName()?.asString()
          annotationFqName == "androidx.compose.runtime.Stable" ||
            annotationFqName == "androidx.compose.runtime.Immutable"
        }
        isSealed && hasAnnotation
      } else {
        false
      }
    }

    if (parentHasStabilityAnnotation) {
      return KtStability.Certain(
        stable = true,
        reason = "Subclass of @Immutable/@Stable sealed class",
      )
    }

    // Check superclass stability first
    val superClassStability = analyzeSuperclassStability(classSymbol, currentlyAnalyzing)

    // Get all state-storing properties from the class. Computed getter-only properties have no
    // backing field, store no state, and are ignored — matching the Compose compiler (issue #178).
    val properties = classSymbol.declaredMemberScope.callables
      .filterIsInstance<KaPropertySymbol>()
      .filterNot { it.isComputedGetterOnly() }
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

    // A delegated `var` is exempt: it has no mutable field of its own, and the Compose compiler
    // scores its delegate instead, which is what keeps `var x by mutableStateOf(...)` stable.
    val mutableProperties = properties.filter { !it.isVal && !it.isDelegatedPropertyCompat() }
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
  private fun KaSession.analyzeSuperclassStability(
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

      // If superclass has runtime stability, propagate that. A bare-Unknown super (an
      // abstract/open base with no destabilizing state) is intentionally NOT propagated —
      // a concrete subclass fully determines its inherited fields, so an uncertain-only base
      // must not taint it. This matches the Compose compiler, which drops an Unknown superclass
      // (issue #178). Real instability from an abstract/open base still propagates: it now
      // resolves to Unstable/Runtime/Combined via field analysis, handled by the branches here.
      if (stability is KtStability.Runtime ||
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
   * True for a computed getter-only property — one with an explicit (non-default) getter and no
   * backing field. Such a property stores no state and must be excluded from stability inference,
   * matching the Compose compiler, whose class inference only considers members with a backing
   * field (issue #178).
   *
   * The getter's default-ness is the primary signal: `hasBackingField` alone is unreliable for
   * body-declared properties in light / not-fully-resolved PSI contexts, whereas a synthesized
   * (default) getter reliably marks a stored `val`/`var`. Delegated properties are always kept.
   */
  private fun KaPropertySymbol.isComputedGetterOnly(): Boolean = runCatching {
    !isDelegatedProperty && !hasBackingField && (getter?.isNotDefaultReflective() == true)
  }.getOrDefault(false)

  /**
   * `KaSession.withNullability`, called reflectively because the Analysis API changed its shape.
   *
   * IDEs from 2024.2 through 2025.1 ship only `withNullability(KaType, KaTypeNullability)`, newer
   * ones add `withNullability(KaType, Boolean)`, and Kotlin 2.5 deprecates the enum at `HIDDEN`
   * level, which makes it an unresolved reference that `@Suppress` cannot reach. Binding to either
   * overload at compile time therefore breaks one end of the supported range: calling the boolean
   * one directly made the Plugin Verifier report "method not found" against 242, 243 and 251.
   * Resolving at runtime keeps a single binary working across all of them.
   *
   * Falling back to the original type is safe: every downstream use here reads the expanded class
   * symbol, the function-type flags or the annotations, none of which depend on nullability.
   */
  private fun KaSession.withoutNullabilityReflective(type: KaType): KaType = runCatching {
    val methods = javaClass.methods.filter { it.name == "withNullability" && it.parameterCount == 2 }

    methods.firstOrNull { it.parameterTypes[1] == java.lang.Boolean.TYPE }
      ?.let { return@runCatching it.invoke(this, type, false) as? KaType }

    val enumMethod = methods.firstOrNull { it.parameterTypes[1].isEnum }
      ?: return@runCatching null
    val nonNullable = enumMethod.parameterTypes[1].enumConstants
      ?.firstOrNull { (it as? Enum<*>)?.name == "NON_NULLABLE" }
      ?: return@runCatching null
    enumMethod.invoke(this, type, nonNullable) as? KaType
  }.getOrNull() ?: type

  /**
   * `KaPropertyGetterSymbol.isNotDefault`, read reflectively.
   *
   * The property was added in a newer Kotlin Analysis API than some supported IDEs ship, so a direct
   * call makes the JetBrains Plugin Verifier report a "method not found" against those older IDEs
   * (2024.2 / 2024.3 / 2025.1). Reading it reflectively keeps one plugin binary compatible across
   * the range; when the property is absent the getter is conservatively treated as default (false),
   * so the property is kept in inference rather than dropped.
   */
  private fun KaPropertyGetterSymbol.isNotDefaultReflective(): Boolean = runCatching {
    javaClass.getMethod("isNotDefault").invoke(this) as? Boolean ?: false
  }.getOrDefault(false)

  /**
   * Analyzes a value class to determine its stability.
   * Value classes inherit the stability of their underlying type.
   */
  private fun KaSession.analyzeValueClass(
    classSymbol: KaClassSymbol,
    currentlyAnalyzing: Set<KaClassLikeSymbol>,
  ): KtStability {
    // Value classes must have exactly one property
    val properties = classSymbol.declaredMemberScope.callables
      .filterIsInstance<KaPropertySymbol>()
      .filterNot { it.isComputedGetterOnly() }
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
  private companion object {
    const val STABLE_MARKER_FQ = "androidx.compose.runtime.StableMarker"
  }

  private fun KaSession.hasStableAnnotation(symbol: KaClassSymbol): Boolean =
    hasStableMarkedDescendant(symbol, mutableSetOf())

  /**
   * Mirrors the Compose compiler's `hasStableMarkedDescendant`: the marker may sit on a supertype.
   */
  private fun KaSession.hasStableMarkedDescendant(
    symbol: KaClassSymbol,
    visited: MutableSet<KaClassSymbol>,
  ): Boolean {
    if (!visited.add(symbol)) return false
    if (hasStableMarker(symbol)) return true
    return symbol.superTypes.any { superType ->
      superType.expandedSymbol?.classId?.asSingleFqName()?.asString() != "kotlin.Any" &&
        (superType.expandedSymbol as? KaClassSymbol)
          ?.let { hasStableMarkedDescendant(it, visited) } == true
    }
  }

  /**
   * Whether any annotation on [symbol] is itself annotated `@StableMarker`, or is one of the
   * external markers the Compose compiler hardcodes.
   *
   * `@Stable` and `@Immutable` are not special-cased by the compiler: they simply carry
   * `@StableMarker`. Resolving the rule rather than the two known outputs lets a project define its
   * own marker, exactly as the compiler plugin does. The explicit FqNames stay as a fast path and
   * as a fallback for when the annotation class cannot be resolved.
   */
  private fun KaSession.hasStableMarker(symbol: KaClassSymbol): Boolean =
    symbol.annotations.any { annotation ->
      val fqName = annotation.classId?.asSingleFqName()?.asString()
      if (fqName == StabilityConstants.Annotations.STABLE_FQ ||
        fqName == StabilityConstants.Annotations.IMMUTABLE_FQ ||
        fqName == StabilityConstants.Annotations.ERROR_PRONE_IMMUTABLE_FQ ||
        fqName == StabilityConstants.Annotations.STABLE_FOR_ANALYSIS
      ) {
        return@any true
      }
      val annotationClass = annotation.classId?.let { findClass(it) } ?: return@any false
      annotationClass.annotations.any { meta ->
        meta.classId?.asSingleFqName()?.asString() == STABLE_MARKER_FQ
      }
    }

  /**
   * Reads the @StabilityInferred annotation's parameters field.
   *
   * @StabilityInferred is added by the Compose compiler to classes from other modules
   * to indicate their stability:
   * The value is a bitmask, NOT a boolean. Bits 0..n-1 mark which of the n type parameters the
   * class's stability depends on, and the bit at index n is a "known stable" sentinel; for a class
   * with no type parameters that is bit 0. So `parameters = 1` means stable and `parameters = 0`
   * means not stable, which is the opposite of what this was previously read as.
   */
  private fun KaSession.getStabilityInferredParameters(symbol: KaClassSymbol): Int? {
    val stabilityInferredFqName = "androidx.compose.runtime.internal.StabilityInferred"
    val annotation = symbol.annotations.firstOrNull { annotation ->
      annotation.classId?.asSingleFqName()?.asString() == stabilityInferredFqName
    } ?: return null

    val parametersArgument = annotation.arguments.firstOrNull { arg ->
      arg.name.asString() == "parameters"
    }

    // Extract the Int value from the constant expression
    return try {
      when (val expression = parametersArgument?.expression) {
        is org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue.ConstantValue -> {
          // Get the constant value as Int
          (expression.value.value as? Int) ?: run {
            null
          }
        }

        else -> {
          null
        }
      }
    } catch (e: Exception) {
      null
    }
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
   * Uses project-level settings first, falls back to global settings.
   */
  private fun isCustomStableType(fqName: String?): Boolean {
    if (fqName == null) return false

    val customPatterns = if (project != null) {
      val projectPatterns = StabilityProjectSettingsState.getInstance(
        project,
      ).getCustomStableTypesAsRegex()
      if (projectPatterns.isNotEmpty()) projectPatterns else settings.getCustomStableTypesAsRegex()
    } else {
      settings.getCustomStableTypesAsRegex()
    }
    return customPatterns.any { pattern -> pattern.matches(fqName) }
  }

  /**
   * Check if a class is a value class (inline class).
   */
  private fun KaSession.isInlineClass(symbol: KaClassSymbol): Boolean {
    // Check for @JvmInline annotation (modern value classes)
    return symbol.annotations.any { annotation ->
      annotation.classId?.asSingleFqName()?.asString() == "kotlin.jvm.JvmInline"
    }
  }

  /**
   * Checks if a class is from a different module or external library.
   * Detects: (1) External JARs/AARs via origins, (2) Other modules via module comparison.
   */
  private fun KaSession.isFromDifferentModule(symbol: KaClassSymbol): Boolean {
    return try {
      // Check 1: External library classes (compiled JARs/AARs)
      val origin = symbol.origin
      val originName = origin.toString()
      val isFromLibrary = originName.contains("LIBRARY") && !originName.contains("SOURCE")

      if (isFromLibrary) {
        return true
      }

      // Check 2: Classes from other project modules
      val classFile = symbol.psi?.containingFile?.virtualFile
      if (classFile != null && project != null && usageSiteModule != null) {
        val classModule = ProjectFileIndex.getInstance(project).getModuleForFile(
          classFile,
        )

        if (classModule != null && classModule != usageSiteModule) {
          return true
        }
      }

      false
    } catch (e: Exception) {
      false
    }
  }
}
