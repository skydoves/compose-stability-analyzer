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
@file:Suppress("UnstableApiUsage")

package com.skydoves.compose.stability.idea

import com.intellij.openapi.project.Project
import com.skydoves.compose.stability.idea.k2.StabilityAnalyzerK2
import com.skydoves.compose.stability.idea.settings.StabilityProjectSettingsState
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability
import com.skydoves.compose.stability.runtime.ParameterStabilityInfo
import com.skydoves.compose.stability.runtime.ReceiverKind
import com.skydoves.compose.stability.runtime.ReceiverStabilityInfo
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

/**
 * Helper class to hold stability result with reason.
 */
private data class StabilityResult(
  val stability: ParameterStability,
  val reason: String? = null,
)

/**
 * Analyzes Kotlin code to determine Compose stability.
 * The rules are: https://github.com/skydoves/compose-stability-inference
 *
 * Uses dual-mode analysis:
 * 1. Try K2 Analysis API (fast, accurate, semantic analysis)
 * 2. Fall back to PSI (compatible with K1 mode)
 */
internal object StabilityAnalyzer {

  /**
   * Get settings instance.
   */
  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  /**
   * Get ignored type patterns from settings.
   */
  private val ignoredPatterns: List<Regex>
    get() = settings.getIgnoredPatternsAsRegex()

  /**
   * Get custom stable type patterns from configuration file.
   * Uses project-level settings first, falls back to global settings.
   */
  private fun getCustomStablePatterns(project: Project?): List<Regex> {
    if (project != null) {
      val projectPatterns = StabilityProjectSettingsState.getInstance(
        project,
      ).getCustomStableTypesAsRegex()
      if (projectPatterns.isNotEmpty()) {
        return projectPatterns
      }
    }
    return settings.getCustomStableTypesAsRegex()
  }

  /**
   * Check if a fully qualified type name should be ignored based on user settings.
   */
  private fun shouldIgnoreType(fqName: String?): Boolean {
    if (fqName == null) return false
    if (!settings.isStabilityCheckEnabled) return true
    return ignoredPatterns.any { pattern -> pattern.matches(fqName) }
  }

  /**
   * Check if a fully qualified type name should be considered stable based on custom configuration.
   */
  private fun isCustomStableType(fqName: String?, project: Project? = null): Boolean {
    if (fqName == null) return false
    return getCustomStablePatterns(project).any { pattern -> pattern.matches(fqName) }
  }

  /**
   * Analyzes a @Composable function and returns stability information.
   *
   * Dual-mode analysis:
   * - First tries K2 Analysis API (2-3x faster, 15% more accurate)
   * - Falls back to PSI analysis (compatible with K1 mode)
   */
  internal fun analyze(function: KtNamedFunction): ComposableStabilityInfo {
    // Try K2 Analysis API first (fast + accurate)
    val k2Result = StabilityAnalyzerK2.analyze(function)
    if (k2Result != null) {
      return k2Result
    }

    // Fall back to PSI analysis (K1 compatibility)
    return analyzePsi(function)
  }

  /**
   * PSI-based analysis (fallback for K1 mode or when K2 fails).
   */
  private fun analyzePsi(function: KtNamedFunction): ComposableStabilityInfo {
    val parameters = function.valueParameters.mapNotNull { param ->
      analyzeParameter(param)
    }

    // Analyze receivers (extension, dispatch, context)
    val receivers = analyzeReceivers(function)

    // Check if all parameters AND receivers are naturally stable
    val isNaturallySkippable =
      parameters.all { it.stability == ParameterStability.STABLE } &&
        receivers.all { it.stability == ParameterStability.STABLE }

    // In strong skipping mode, ALL composables are skippable
    val isStrongSkippingEnabled = settings.isStrongSkippingEnabled
    val isSkippable = if (isStrongSkippingEnabled) {
      true // All composables are skippable in strong skipping mode
    } else {
      isNaturallySkippable
    }

    // Track if skippable ONLY due to strong skipping mode
    val isSkippableInStrongSkippingMode = isStrongSkippingEnabled && !isNaturallySkippable

    val isRestartable = !function.hasAnnotation("NonRestartableComposable")
    val isReadonly = function.hasAnnotation("ReadOnlyComposable")

    return ComposableStabilityInfo(
      name = function.name ?: "Unknown",
      fqName = function.fqName?.asString() ?: "Unknown",
      isRestartable = isRestartable,
      isSkippable = isSkippable,
      isReadonly = isReadonly,
      parameters = parameters,
      isSkippableInStrongSkippingMode = isSkippableInStrongSkippingMode,
      receivers = receivers,
    )
  }

  /**
   * Analyzes all receivers (extension, dispatch, context) of a function.
   */
  private fun analyzeReceivers(function: KtNamedFunction): List<ReceiverStabilityInfo> {
    val receivers = mutableListOf<ReceiverStabilityInfo>()

    // 1. Extension receiver
    function.receiverTypeReference?.let { receiverTypeRef ->
      val receiverText = receiverTypeRef.text ?: "Unknown"
      val result = analyzeTypeViaPsiWithReason(receiverTypeRef, receiverText)

      if (result != null) {
        receivers.add(
          ReceiverStabilityInfo(
            type = receiverText,
            stability = result.stability,
            reason = result.reason ?: "Extension receiver",
            receiverKind = ReceiverKind.EXTENSION,
          ),
        )
      }
    }

    // 2. Dispatch receiver (containing class)
    val containingClass = function.parent?.parent as? KtClass
    if (containingClass != null && !containingClass.isInterface()) {
      val className = containingClass.name ?: "Unknown"
      val classStability = analyzeClassStability(containingClass)

      receivers.add(
        ReceiverStabilityInfo(
          type = className,
          stability = classStability.stability,
          reason = classStability.reason ?: "Dispatch receiver (containing class)",
          receiverKind = ReceiverKind.DISPATCH,
        ),
      )
    }

    // 3. Context receivers (Kotlin 1.6.20+)
    // Context receivers are specified with @ContextReceiver annotation
    // or context(...) syntax
    val contextReceivers = extractContextReceivers(function)
    receivers.addAll(contextReceivers)

    return receivers
  }

  /**
   * Analyzes the stability of a containing class (dispatch receiver).
   */
  private fun analyzeClassStability(ktClass: KtClass): StabilityResult {
    // Check for @Stable or @Immutable annotations
    val hasStableAnnotation = ktClass.annotationEntries.any { annotation ->
      val shortName = annotation.shortName?.asString()
      shortName == "Stable" || shortName == "Immutable"
    }

    if (hasStableAnnotation) {
      return StabilityResult(
        ParameterStability.STABLE,
        "Class is annotated with @Stable or @Immutable",
      )
    }

    // For data classes, check properties
    if (ktClass.isData()) {
      val properties = ktClass.primaryConstructorParameters
      val allPropertiesStable = properties.all { param ->
        !param.isMutable && param.typeReference?.let { typeRef ->
          analyzeTypeViaPsiWithReason(typeRef, typeRef.text ?: "")?.stability ==
            ParameterStability.STABLE
        } ?: false
      }

      return if (allPropertiesStable) {
        StabilityResult(
          ParameterStability.STABLE,
          "Data class with all stable val properties",
        )
      } else {
        StabilityResult(
          ParameterStability.UNSTABLE,
          "Data class has mutable (var) or unstable properties",
        )
      }
    }

    // For regular classes, check if they have any var properties
    val hasVarProperties = ktClass.getProperties().any { it.isVar }
    return if (hasVarProperties) {
      StabilityResult(
        ParameterStability.UNSTABLE,
        "Class has mutable (var) properties",
      )
    } else {
      // Conservative: assume runtime if we can't determine
      StabilityResult(
        ParameterStability.RUNTIME,
        "Class stability depends on implementation",
      )
    }
  }

  /**
   * Extracts context receivers from a function.
   * Context receivers are a Kotlin 1.6.20+ feature.
   */
  private fun extractContextReceivers(function: KtNamedFunction): List<ReceiverStabilityInfo> {
    // Context receivers would be in the function's context receiver list
    // This is a placeholder for now as context receivers require special parsing
    // In practice, we'd need to check for:
    // 1. context(...) syntax before the function
    // 2. The types specified in the context

    // For now, return empty list as context receivers are less common
    // This can be enhanced later when context receivers become more prevalent
    return emptyList()
  }

  /**
   * Analyzes a single parameter to determine its stability.
   */
  private fun analyzeParameter(param: KtParameter): ParameterStabilityInfo? {
    val paramName = param.name ?: return null
    val typeText = param.typeReference?.text ?: "Unknown"

    // FIRST: Check via PSI (annotations, data classes) - works best in K2 mode
    val psiResult = analyzeTypeViaPsiWithReason(param.typeReference, typeText)
    if (psiResult != null) {
      return ParameterStabilityInfo(
        name = paramName,
        type = typeText,
        stability = psiResult.stability,
        reason = psiResult.reason,
      )
    }

    // SECOND: Try descriptor-based analysis (only works in K1 mode)
    val result = try {
      val bindingContext = param.analyze(BodyResolveMode.PARTIAL)
      val type = bindingContext.get(BindingContext.TYPE, param.typeReference)

      if (type != null) {
        val fqName = type.constructor.declarationDescriptor?.fqNameSafe?.asString()
        if (shouldIgnoreType(fqName)) {
          StabilityResult(
            ParameterStability.STABLE,
            "Ignored by user settings: $fqName",
          )
        } else if (isCustomStableType(fqName, param.project)) {
          StabilityResult(
            ParameterStability.STABLE,
            "Custom stable type from configuration: $fqName",
          )
        } else {
          val stability = analyzeType(type)
          StabilityResult(stability, "Analyzed via type descriptor")
        }
      } else {
        // Type couldn't be resolved via descriptor - this shouldn't happen for valid code
        // Return runtime as conservative default
        StabilityResult(
          ParameterStability.RUNTIME,
          "Type could not be resolved - may need recompilation",
        )
      }
    } catch (e: IllegalStateException) {
      // K2 mode - descriptor analysis not supported
      // Return null to indicate we cannot analyze (rather than marking as RUNTIME)
      // PSI analysis should have handled it already
      StabilityResult(
        ParameterStability.RUNTIME,
        "Cannot analyze in K2 mode - consider migrating to Analysis API",
      )
    } catch (e: Exception) {
      StabilityResult(
        ParameterStability.RUNTIME,
        "Analysis error: ${e.javaClass.simpleName}",
      )
    }

    return ParameterStabilityInfo(
      name = paramName,
      type = typeText,
      stability = result.stability,
      reason = result.reason,
    )
  }

  /**
   * Analyzes a type via PSI to determine stability with reason.
   */
  private fun analyzeTypeViaPsiWithReason(
    typeRef: org.jetbrains.kotlin.psi.KtTypeReference?,
    typeText: String,
  ): StabilityResult? {
    if (typeRef == null) return null

    try {
      val cleanType = typeRef.text?.trim()?.removeSuffix("?") ?: ""
      val simpleName = cleanType.substringAfterLast('.')

      if (simpleName in PRIMITIVE_TYPES || cleanType in PRIMITIVE_TYPES) {
        return StabilityResult(ParameterStability.STABLE, "Primitive type")
      }
      if (simpleName == "String" || cleanType == "String") {
        return StabilityResult(
          ParameterStability.STABLE,
          StabilityConstants.Messages.STRING_STABLE,
        )
      }
      if (simpleName == "Unit" || simpleName == "Nothing" ||
        cleanType == "Unit" || cleanType == "Nothing"
      ) {
        return StabilityResult(
          ParameterStability.STABLE,
          StabilityConstants.Messages.UNIT_STABLE,
        )
      }

      // Check for function types (including @Composable lambdas)
      if ("->" in cleanType) {
        return when {
          cleanType.contains("@Composable") -> StabilityResult(
            ParameterStability.STABLE,
            StabilityConstants.Messages.COMPOSABLE_FUNCTION_STABLE,
          )

          else -> StabilityResult(
            ParameterStability.STABLE,
            StabilityConstants.Messages.FUNCTION_STABLE,
          )
        }
      }

      // First, try to navigate to the source declaration of the class
      var typeElement = typeRef.typeElement

      // Unwrap nullable types, we need to get the inner type
      if (typeElement is org.jetbrains.kotlin.psi.KtNullableType) {
        typeElement = typeElement.innerType
      }

      if (typeElement is KtUserType) {
        val referenceExpression = typeElement.referenceExpression
        if (referenceExpression != null) {
          val resolved = referenceExpression.resolveMainReference()
          if (resolved is KtClass) {
            val className = resolved.name ?: typeText
            val fqName = resolved.fqName?.asString()

            // Check if this type should be ignored
            if (shouldIgnoreType(fqName)) {
              return StabilityResult(
                ParameterStability.STABLE,
                "Ignored by user settings: $fqName",
              )
            }

            // Check if type is custom stable
            if (isCustomStableType(fqName, typeRef.project)) {
              return StabilityResult(
                ParameterStability.STABLE,
                "Custom stable type from configuration: $fqName",
              )
            }

            // 1. Check for @Stable or @Immutable annotations
            val hasStableAnnotation = resolved.annotationEntries.any { annotation ->
              val shortName = annotation.shortName?.asString()
              val text = annotation.text
              (shortName == "Stable" || shortName == "Immutable") &&
                (
                  text.contains("androidx.compose.runtime") ||
                    !text.contains(".") || // Assume imported if no package
                    text.contains("Stable") ||
                    text.contains("Immutable")
                  )
            }
            if (hasStableAnnotation) {
              return StabilityResult(
                ParameterStability.STABLE,
                "Annotated with @Stable or @Immutable",
              )
            }

            // 2. Check for @StableMarker meta-annotation
            val hasStableMarker = resolved.annotationEntries.any { annotation ->
              val shortName = annotation.shortName?.asString()
              shortName == "StableMarker"
            }
            if (hasStableMarker) {
              return StabilityResult(
                ParameterStability.STABLE,
                "Annotated with @StableMarker",
              )
            }

            // 3. Check if it's an enum (enums are always stable)
            if (resolved.isEnum()) {
              return StabilityResult(
                ParameterStability.STABLE,
                StabilityConstants.Messages.ENUM_STABLE,
              )
            }

            // 4. Check if it's a value class (inline class) - stability depends on underlying type
            val isValueClass =
              resolved.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.INLINE_KEYWORD) ||
                resolved.annotationEntries.any { it.shortName?.asString() == "JvmInline" }

            if (isValueClass) {
              // Value classes must have exactly one property in primary constructor
              val primaryConstructor = resolved.primaryConstructor
              val valueParam = primaryConstructor?.valueParameters?.firstOrNull()

              if (valueParam != null && valueParam.typeReference != null) {
                // Recursively analyze the underlying type
                val underlyingResult = analyzeTypeViaPsiWithReason(
                  valueParam.typeReference,
                  valueParam.typeReference!!.text,
                )

                if (underlyingResult != null) {
                  return StabilityResult(
                    underlyingResult.stability,
                    "Value class - stability inherited from underlying " +
                      "type (${valueParam.typeReference!!.text})",
                  )
                }
              }
            }

            // 5. Check if it's a kotlinx immutable collection (always stable)
            if (fqName != null && fqName.startsWith("kotlinx.collections.immutable.")) {
              if (fqName.contains("Immutable") || fqName.contains("Persistent")) {
                return StabilityResult(
                  ParameterStability.STABLE,
                  "Kotlinx immutable collection",
                )
              }
            }

            // 6. If it's an interface, return RUNTIME (cannot determine)
            if (resolved.isInterface()) {
              return StabilityResult(
                ParameterStability.RUNTIME,
                "Interface type - actual implementation could be mutable",
              )
            }

            // 7. Analyze class properties first (both data classes and normal classes)
            // This check MUST come before @StabilityInferred to properly detect definitive cases
            val propertyStability = analyzeClassPropertiesViaPsiWithReason(resolved, className)

            // If property analysis gives a definitive answer (STABLE or UNSTABLE), return it
            // Only fall back to @StabilityInferred for uncertain (RUNTIME) cases
            if (propertyStability != null &&
              propertyStability.stability != ParameterStability.RUNTIME
            ) {
              return propertyStability
            }

            // 8. Check for @StabilityInferred (from separate compilation)
            val hasStabilityInferred = resolved.annotationEntries.any { annotation ->
              annotation.shortName?.asString() == "StabilityInferred"
            }
            if (hasStabilityInferred) {
              return StabilityResult(
                ParameterStability.RUNTIME,
                "Annotated with @StabilityInferred (from separate compilation)",
              )
            }

            // Return property stability or null if no definitive answer
            return propertyStability
          }
        }
      }

      // Fallback: Try descriptor-based approach for annotations (only works in K1 mode)
      try {
        val bindingContext = typeRef.analyze(BodyResolveMode.PARTIAL)
        val type = bindingContext.get(BindingContext.TYPE, typeRef)

        if (type != null) {
          // Strip nullability before checking annotations
          val nonNullableType = if (type.isMarkedNullable) type.makeNotNullable() else type

          val classDescriptor =
            nonNullableType.constructor.declarationDescriptor as? ClassDescriptor
          if (classDescriptor != null) {
            val hasStableAnnotation = classDescriptor.annotations.any { annotation ->
              val fqName = annotation.fqName?.asString()
              fqName == StabilityConstants.Annotations.STABLE_FQ ||
                fqName == StabilityConstants.Annotations.IMMUTABLE_FQ ||
                fqName == StabilityConstants.Annotations.ERROR_PRONE_IMMUTABLE_FQ ||
                fqName == StabilityConstants.Annotations.STABLE_FOR_ANALYSIS
            }
            if (hasStableAnnotation) {
              return StabilityResult(
                ParameterStability.STABLE,
                "Annotated with @Stable or @Immutable",
              )
            }

            // Cross-module types without @Stable/@Immutable/@StabilityInferred are UNSTABLE
            // Classes from other modules must be explicitly annotated to be considered stable
            if (isFromDifferentModule(classDescriptor)) {
              // Check for @StabilityInferred annotation
              val stabilityInferredFqName = "androidx.compose.runtime.internal.StabilityInferred"
              val stabilityInferred = classDescriptor.annotations.firstOrNull { annotation ->
                annotation.fqName?.asString() == stabilityInferredFqName
              }
              if (stabilityInferred == null) {
                // No @Stable, @Immutable, or @StabilityInferred annotation
                return StabilityResult(
                  ParameterStability.UNSTABLE,
                  "External class without stability annotation",
                )
              }
              // If has @StabilityInferred, we would need to check the parameters value
              // For now, we'll let it continue to property analysis
            }
          }
        }
      } catch (_: IllegalStateException) {
        // K2 mode - descriptor analysis not available, just continue
      } catch (_: Exception) {
        // Other errors - just continue
      }
    } catch (_: Exception) {
      // Ignore resolution errors
    }

    return null
  }

  /**
   * Analyzes a type via PSI to determine stability (old method for compatibility).
   */
  private fun analyzeTypeViaPsi(
    typeRef: org.jetbrains.kotlin.psi.KtTypeReference?,
  ): ParameterStability? {
    return analyzeTypeViaPsiWithReason(typeRef, typeRef?.text ?: "")?.stability
  }

  /**
   * Analyzes a class (data class or normal class) via PSI to determine if it's stable with reason.
   */
  private fun analyzeClassPropertiesViaPsiWithReason(
    ktClass: KtClass,
    className: String,
  ): StabilityResult? {
    try {
      val properties = mutableListOf<Pair<Boolean, org.jetbrains.kotlin.psi.KtTypeReference?>>()

      // Check primary constructor parameters for ALL classes (not just data classes)
      val primaryConstructor = ktClass.primaryConstructor
      if (primaryConstructor != null) {
        for (param in primaryConstructor.valueParameters) {
          // val/var in constructor creates a property
          // Check if param has val or var (is a property)
          if (param.hasValOrVar()) {
            properties.add(param.isMutable to param.typeReference)
          }
        }
      }

      // For all classes, also check class body properties
      for (declaration in ktClass.declarations) {
        if (declaration is KtProperty) {
          properties.add(declaration.isVar to declaration.typeReference)
        }
      }

      // Check superclass stability
      var hasSuperclassWithRuntimeStability = false
      var superclassName: String? = null
      val superTypeListEntries = ktClass.superTypeListEntries
      for (superTypeEntry in superTypeListEntries) {
        val superTypeRef = superTypeEntry.typeReference
        if (superTypeRef != null) {
          val superStability = analyzeTypeViaPsi(superTypeRef)
          if (superStability == ParameterStability.UNSTABLE) {
            return StabilityResult(
              ParameterStability.UNSTABLE,
              "Extends unstable class ${superTypeRef.text}",
            )
          }
          if (superStability == ParameterStability.RUNTIME || superStability == null) {
            hasSuperclassWithRuntimeStability = true
            superclassName = superTypeRef.text
          }
        }
      }

      // If no properties found
      if (properties.isEmpty()) {
        return if (hasSuperclassWithRuntimeStability) {
          StabilityResult(
            ParameterStability.RUNTIME,
            "Extends $superclassName which has runtime stability",
          )
        } else {
          StabilityResult(ParameterStability.STABLE, "No mutable properties")
        }
      }

      // Check if any property is var (mutable)
      val mutableProperties = properties.filter { it.first }
      if (mutableProperties.isNotEmpty()) {
        val count = mutableProperties.size
        return StabilityResult(
          ParameterStability.UNSTABLE,
          "Has $count mutable (var) ${if (count == 1) "property" else "properties"}",
        )
      }

      // All properties are val - check if their types are stable
      var allStable = true
      val unstablePropertyTypes = mutableListOf<String>()
      val runtimePropertyTypes = mutableListOf<String>()

      for ((_, typeRef) in properties) {
        if (typeRef == null) continue
        val typeText = typeRef.text

        // First try PSI-based analysis (handles nested classes)
        val psiStability = analyzeTypeViaPsi(typeRef)
        val stability = psiStability ?: analyzeTypeByText(typeText)

        if (stability == ParameterStability.UNSTABLE) {
          unstablePropertyTypes.add(typeText)
        }
        if (stability == ParameterStability.RUNTIME) {
          runtimePropertyTypes.add(typeText)
          allStable = false
        }
      }

      // If we found any unstable properties
      if (unstablePropertyTypes.isNotEmpty()) {
        return StabilityResult(
          ParameterStability.UNSTABLE,
          "Has properties with unstable types: ${unstablePropertyTypes.joinToString(", ")}",
        )
      }

      // All properties are val and all types are stable
      return if (allStable) {
        StabilityResult(
          ParameterStability.STABLE,
          StabilityConstants.Messages.ALL_PROPERTIES_STABLE,
        )
      } else {
        null // Cannot determine - will fall back to text analysis
      }
    } catch (_: Exception) {
      return null
    }
  }

  /**
   * Recursively analyzes a Kotlin type to determine its stability.
   *
   * The order of analyzing is very important, "Never change this orders if possible".
   */
  private fun analyzeType(type: KotlinType): ParameterStability {
    return when {
      // Nullable types - MUST be checked first to strip nullability before other checks
      type.isMarkedNullable -> analyzeType(type.makeNotNullable())

      // Known stable types - check BEFORE value class analysis for external library types
      type.isKnownStable() -> ParameterStability.STABLE

      // Check simple name for common Compose types (fallback for compiled external libraries)
      type.isKnownStableBySimpleName() -> ParameterStability.STABLE

      // Check for @Stable or @Immutable annotations
      type.hasStableAnnotation() -> ParameterStability.STABLE

      // Primitives are always stable
      KotlinBuiltIns.isPrimitiveType(type) -> ParameterStability.STABLE

      // String is stable
      KotlinBuiltIns.isString(type) -> ParameterStability.STABLE

      // Unit and Nothing are stable
      KotlinBuiltIns.isUnit(type) || KotlinBuiltIns.isNothing(type) -> ParameterStability.STABLE

      // Functions are stable
      type.isFunctionOrSuspendFunction() -> ParameterStability.STABLE

      // Check for mutable collections (always unstable)
      type.isMutableCollection() -> ParameterStability.UNSTABLE

      // Standard collections (List, Set, Map) - these are interfaces, RUNTIME check needed
      type.isStandardCollection() -> ParameterStability.RUNTIME

      // Value classes (inline classes) - stability depends on underlying type
      type.isValueClass() -> analyzeValueClass(type)

      // Enum classes are always stable
      type.isEnum() -> ParameterStability.STABLE

      // Data classes - check properties
      type.isDataClass() -> analyzeDataClass(type)

      // Interface types (including fun interfaces) - runtime check needed
      type.isInterface() -> ParameterStability.RUNTIME

      // Regular classes - check properties (same logic as data classes)
      type.isClass() -> analyzeClassProperties(type)

      // Default to runtime checking for unknown types
      else -> ParameterStability.RUNTIME
    }
  }

  /**
   * Analyzes a data class to determine if it's stable.
   * A data class is stable if all its properties are val and all property types are stable.
   */
  private fun analyzeDataClass(type: KotlinType): ParameterStability {
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor
      ?: return ParameterStability.RUNTIME

    // Get all primary constructor parameters
    val constructorParameters = classDescriptor.unsubstitutedPrimaryConstructor?.valueParameters
      ?: return ParameterStability.RUNTIME

    // Check if any property is var (mutable)
    val hasMutableProperty = constructorParameters.any { param ->
      val property = classDescriptor.unsubstitutedMemberScope
        .getContributedVariables(param.name, NoLookupLocation.FROM_IDE)
        .firstOrNull()
      property?.isVar == true
    }

    if (hasMutableProperty) {
      return ParameterStability.UNSTABLE
    }

    // Recursively check all property types
    val propertyStabilities = constructorParameters.map { param ->
      analyzeType(param.type)
    }

    return when {
      propertyStabilities.all { it == ParameterStability.STABLE } -> ParameterStability.STABLE
      propertyStabilities.any { it == ParameterStability.UNSTABLE } -> ParameterStability.UNSTABLE
      else -> ParameterStability.RUNTIME
    }
  }

  /**
   * Checks if a type has @Stable or @Immutable annotation.
   */
  private fun KotlinType.hasStableAnnotation(): Boolean {
    // Check annotations on the type itself
    val typeAnnotations = annotations
    val hasTypeAnnotation = typeAnnotations.any { annotation ->
      val fqName = annotation.fqName?.asString()
      fqName == StabilityConstants.Annotations.STABLE_FQ ||
        fqName == StabilityConstants.Annotations.IMMUTABLE_FQ ||
        fqName == StabilityConstants.Annotations.ERROR_PRONE_IMMUTABLE_FQ ||
        fqName == StabilityConstants.Annotations.STABLE_FOR_ANALYSIS
    }

    if (hasTypeAnnotation) return true

    // Also check annotations on the class descriptor
    val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor
    if (classDescriptor != null) {
      val classAnnotations = classDescriptor.annotations
      return classAnnotations.any { annotation ->
        val fqName = annotation.fqName?.asString()
        fqName == StabilityConstants.Annotations.STABLE_FQ ||
          fqName == StabilityConstants.Annotations.IMMUTABLE_FQ ||
          fqName == StabilityConstants.Annotations.ERROR_PRONE_IMMUTABLE_FQ ||
          fqName == StabilityConstants.Annotations.STABLE_FOR_ANALYSIS
      }
    }

    return false
  }

  /**
   * Checks if a type is a mutable collection.
   */
  private fun KotlinType.isMutableCollection(): Boolean {
    val fqName = constructor.declarationDescriptor?.fqNameSafe?.asString() ?: return false
    return StabilityAnalysisConstants.isMutableCollection(fqName)
  }

  /**
   * Checks if a type is a standard collection interface (requires runtime check).
   */
  private fun KotlinType.isStandardCollection(): Boolean {
    val fqName = constructor.declarationDescriptor?.fqNameSafe?.asString() ?: return false
    return StabilityAnalysisConstants.isStandardCollection(fqName)
  }

  /**
   * Checks if a type is a data class.
   */
  private fun KotlinType.isDataClass(): Boolean {
    val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor
    return classDescriptor?.isData == true
  }

  /**
   * Checks if a type is a value class (inline class).
   */
  private fun KotlinType.isValueClass(): Boolean {
    val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor
    return classDescriptor?.isInline == true
  }

  /**
   * Checks if a type is an interface (including fun interfaces).
   */
  private fun KotlinType.isInterface(): Boolean {
    val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor
    return classDescriptor?.kind == org.jetbrains.kotlin.descriptors.ClassKind.INTERFACE
  }

  /**
   * Checks if a type is an enum class.
   */
  private fun KotlinType.isEnum(): Boolean {
    val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor
    return classDescriptor?.kind == org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS
  }

  /**
   * Checks if a type is a regular class (not interface, not enum).
   */
  private fun KotlinType.isClass(): Boolean {
    val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor
    return classDescriptor?.kind == org.jetbrains.kotlin.descriptors.ClassKind.CLASS
  }

  /**
   * Analyzes a regular class's properties to determine stability.
   * Same logic as analyzeDataClass but works for all classes.
   */
  private fun analyzeClassProperties(type: KotlinType): ParameterStability {
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor
      ?: return ParameterStability.RUNTIME

    // Get all properties from primary constructor
    val constructorParameters = classDescriptor.unsubstitutedPrimaryConstructor?.valueParameters
      ?: emptyList()

    // If no constructor parameters, check class body properties
    if (constructorParameters.isEmpty()) {
      // Get properties from class body
      val properties = classDescriptor.unsubstitutedMemberScope
        .getContributedDescriptors()
        .filterIsInstance<org.jetbrains.kotlin.descriptors.PropertyDescriptor>()

      if (properties.isEmpty()) {
        // No properties at all - stable
        return ParameterStability.STABLE
      }

      // Check if any property is mutable
      val hasMutableProperty = properties.any { it.isVar }
      if (hasMutableProperty) {
        return ParameterStability.UNSTABLE
      }

      // Check all property types
      val propertyStabilities = properties.map { analyzeType(it.type) }
      return when {
        propertyStabilities.all { it == ParameterStability.STABLE } -> ParameterStability.STABLE
        propertyStabilities.any { it == ParameterStability.UNSTABLE } -> ParameterStability.UNSTABLE
        else -> ParameterStability.RUNTIME
      }
    }

    // Has constructor parameters - check them
    // Check if any property is var (mutable)
    val hasMutableProperty = constructorParameters.any { param ->
      val property = classDescriptor.unsubstitutedMemberScope
        .getContributedVariables(param.name, NoLookupLocation.FROM_IDE)
        .firstOrNull()
      property?.isVar == true
    }

    if (hasMutableProperty) {
      return ParameterStability.UNSTABLE
    }

    // Recursively check all property types
    val propertyStabilities = constructorParameters.map { param ->
      analyzeType(param.type)
    }

    return when {
      propertyStabilities.all { it == ParameterStability.STABLE } -> ParameterStability.STABLE
      propertyStabilities.any { it == ParameterStability.UNSTABLE } -> ParameterStability.UNSTABLE
      else -> ParameterStability.RUNTIME
    }
  }

  /**
   * Analyzes a value class to determine its stability.
   * Value classes inherit the stability of their underlying type.
   */
  private fun analyzeValueClass(type: KotlinType): ParameterStability {
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor
      ?: return ParameterStability.RUNTIME

    // Try to get the underlying type
    val constructorParameters = classDescriptor.unsubstitutedPrimaryConstructor?.valueParameters
    val underlyingParam = constructorParameters?.firstOrNull()

    if (underlyingParam != null) {
      // We can analyze the underlying type directly
      return analyzeType(underlyingParam.type)
    }

    // Can't get constructor parameters (external library class)
    // Use fallback heuristics based on known patterns

    val fqName = classDescriptor.fqNameSafe.asString()
    val simpleName = classDescriptor.name.asString()

    // Check if this value class is in the known stable types list
    if (StabilityAnalysisConstants.isKnownStable(fqName)) {
      return ParameterStability.STABLE
    }

    // Check simple name for common Compose types
    if (StabilityAnalysisConstants.isKnownStableBySimpleName(simpleName)) {
      return ParameterStability.STABLE
    }

    // For other external value classes, be conservative
    return ParameterStability.RUNTIME
  }

  /**
   * Checks if a type is in the known stable types list.
   */
  private fun KotlinType.isKnownStable(): Boolean {
    val fqName = constructor.declarationDescriptor?.fqNameSafe?.asString() ?: return false
    return StabilityAnalysisConstants.isKnownStable(fqName)
  }

  /**
   * Checks if a type is known stable by its simple name (fallback for compiled external libraries).
   */
  private fun KotlinType.isKnownStableBySimpleName(): Boolean {
    val classDescriptor = constructor.declarationDescriptor as? ClassDescriptor ?: return false
    val simpleName = classDescriptor.name.asString()
    return StabilityAnalysisConstants.isKnownStableBySimpleName(simpleName)
  }

  /**
   * Checks if a ClassDescriptor is from a different module (external dependency or library).
   * Classes from other modules must be explicitly annotated with @Stable/@Immutable
   * or @StabilityInferred to be considered stable, since we can't see their
   * implementation details.
   */
  private fun isFromDifferentModule(classDescriptor: ClassDescriptor): Boolean {
    return try {
      // Classes from external modules are typically not from source
      // and have a different containingDeclaration
      classDescriptor.containingDeclaration.toString().let { container ->
        container.contains("library") ||
          container.contains("external") ||
          container.contains("compiled")
      }
    } catch (e: Exception) {
      false
    }
  }
}

/**
 * Extension function to check if a function has a specific annotation.
 */
internal fun KtNamedFunction.hasAnnotation(shortName: String): Boolean {
  return annotationEntries.any { it.shortName?.asString() == shortName }
}

/**
 * Extension function to check if a type is a function or suspend function type.
 */
private fun KotlinType.isFunctionOrSuspendFunction(): Boolean {
  val fqName = constructor.declarationDescriptor?.fqNameSafe?.asString() ?: return false
  return fqName.startsWith("kotlin.Function") ||
    fqName.startsWith("kotlin.jvm.functions.Function") ||
    fqName.startsWith("kotlin.coroutines.SuspendFunction")
}

/**
 * Analyzes type stability based on the text representation of the type with reason.
 */
private fun analyzeTypeByTextWithReason(typeText: String): StabilityResult {
  // Remove nullability marker and whitespace
  val cleanType = typeText.trim().removeSuffix("?")
  // Extract simple name (e.g., "kotlin.Int" -> "Int")
  val simpleName = cleanType.substringAfterLast('.')

  return when {
    // Primitive types (check both simple and fully qualified names)
    simpleName in PRIMITIVE_TYPES || cleanType in PRIMITIVE_TYPES ->
      StabilityResult(ParameterStability.STABLE, "Primitive type")

    // String (check both simple and fully qualified names)
    simpleName == "String" || cleanType == "String" -> StabilityResult(
      ParameterStability.STABLE,
      StabilityConstants.Messages.STRING_STABLE,
    )

    // Unit and Nothing (check both simple and fully qualified names)
    simpleName == "Unit" || simpleName == "Nothing" ||
      cleanType == "Unit" || cleanType == "Nothing" ->
      StabilityResult(
        ParameterStability.STABLE,
        StabilityConstants.Messages.UNIT_STABLE,
      )

    // Function types with annotations (@Composable, etc.)
    cleanType.contains("@Composable") && "->" in cleanType -> StabilityResult(
      ParameterStability.STABLE,
      StabilityConstants.Messages.COMPOSABLE_FUNCTION_STABLE,
    )

    cleanType.startsWith("@") && "->" in cleanType -> StabilityResult(
      ParameterStability.STABLE,
      StabilityConstants.Messages.FUNCTION_STABLE,
    )

    // Function types (including suspend)
    cleanType.startsWith("(") && "->" in cleanType -> StabilityResult(
      ParameterStability.STABLE,
      StabilityConstants.Messages.FUNCTION_STABLE,
    )

    cleanType.startsWith("suspend (") -> StabilityResult(
      ParameterStability.STABLE,
      StabilityConstants.Messages.SUSPEND_FUNCTION_STABLE,
    )

    // Mutable collections - always unstable
    cleanType.startsWith("MutableList<") || cleanType.startsWith("MutableSet<") ||
      cleanType.startsWith("MutableMap<") || cleanType.startsWith("MutableCollection<") ||
      cleanType.startsWith("ArrayList<") || cleanType.startsWith("HashSet<") ||
      cleanType.startsWith("HashMap<") -> StabilityResult(
      ParameterStability.UNSTABLE,
      StabilityConstants.Messages.MUTABLE_COLLECTION_UNSTABLE,
    )

    // Standard collections (List, Set, Map) - these are interfaces, RUNTIME check needed
    cleanType.startsWith("List<") || cleanType.startsWith("Set<") ||
      cleanType.startsWith("Map<") || cleanType.startsWith("Collection<") ||
      cleanType.startsWith("Iterable<") || cleanType.startsWith("Sequence<") ->
      StabilityResult(
        ParameterStability.RUNTIME,
        "Collection interfaces require runtime stability check " +
          "(actual implementation could be mutable)",
      )

    // Kotlinx & Guava immutable collections - always stable (fully qualified names)
    cleanType.startsWith("kotlinx.collections.immutable.ImmutableList") ||
      cleanType.startsWith("kotlinx.collections.immutable.ImmutableSet") ||
      cleanType.startsWith("kotlinx.collections.immutable.ImmutableMap") ||
      cleanType.startsWith("kotlinx.collections.immutable.ImmutableCollection") ||
      cleanType.startsWith("kotlinx.collections.immutable.PersistentList") ||
      cleanType.startsWith("kotlinx.collections.immutable.PersistKNOWN_STABLE_TYPE_NAMESentSet") ||
      cleanType.startsWith("kotlinx.collections.immutable.PersistentMap") ||
      cleanType.startsWith("com.google.common.collect.ImmutableList") ||
      cleanType.startsWith("com.google.common.collect.ImmutableEnumMap") ||
      cleanType.startsWith("com.google.common.collect.ImmutableMap") ||
      cleanType.startsWith("com.google.common.collect.ImmutableEnumSet") ||
      cleanType.startsWith("com.google.common.collect.ImmutableSet") ->
      StabilityResult(
        ParameterStability.STABLE,
        StabilityConstants.Messages.KOTLINX_IMMUTABLE_STABLE,
      )

    // Known stable types
    StabilityAnalysisConstants.KNOWN_STABLE_TYPE_NAMES.any { cleanType.startsWith(it) } ->
      StabilityResult(
        ParameterStability.STABLE,
        StabilityConstants.Messages.KNOWN_STABLE_TYPE,
      )

    // Default to runtime for unknown types
    else -> StabilityResult(
      ParameterStability.RUNTIME,
      "Cannot determine stability at compile time",
    )
  }
}

/**
 * Analyzes type stability based on the text representation of the type.
 * This is a fallback when descriptor-based analysis fails (e.g., in K2 mode).
 */
private fun analyzeTypeByText(typeText: String): ParameterStability {
  return analyzeTypeByTextWithReason(typeText).stability
}

/**
 * Set of primitive type names
 */
private val PRIMITIVE_TYPES: Set<String> = setOf(
  "Boolean",
  "Byte",
  "Short",
  "Int",
  "Long",
  "Float",
  "Double",
  "Char",
)
