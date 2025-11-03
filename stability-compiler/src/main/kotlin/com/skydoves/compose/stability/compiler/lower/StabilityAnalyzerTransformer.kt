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
package com.skydoves.compose.stability.compiler.lower

import com.skydoves.compose.stability.compiler.StabilityInfoCollector
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isFunctionOrKFunction
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.FqName

public class StabilityAnalyzerTransformer(
  private val context: IrPluginContext,
  private val stabilityCollector: StabilityInfoCollector? = null,
) : IrElementTransformerVoidWithContext() {

  private val composableFqName = FqName("androidx.compose.runtime.Composable")
  private val stableFqName = FqName("androidx.compose.runtime.Stable")
  private val immutableFqName = FqName("androidx.compose.runtime.Immutable")
  private val traceRecompositionFqName =
    FqName("com.skydoves.compose.stability.runtime.TraceRecomposition")
  private val ignoreStabilityReportFqName =
    FqName("com.skydoves.compose.stability.runtime.IgnoreStabilityReport")

  private val irBuilder = RecompositionIrBuilder(context)
  private var irBuilderInitialized = false

  // Cycle detection for recursive types
  private val analyzingTypes = ThreadLocal.withInitial { mutableSetOf<String>() }

  override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    val functionName = declaration.name.asString()
    val fqName = declaration.kotlinFqName.asString()

    // Check if function has @TraceRecomposition annotation
    val hasTraceRecomposition = declaration.hasAnnotation(traceRecompositionFqName)

    // Only process @Composable functions
    if (!declaration.hasAnnotation(composableFqName)) {
      return super.visitFunctionNew(declaration)
    }

    // Skip stability reporting if function has @IgnoreStabilityReport annotation
    val shouldIgnoreReport = declaration.hasAnnotation(ignoreStabilityReportFqName)

    // Collect stability information if collector is available and not ignored
    if (!shouldIgnoreReport) {
      stabilityCollector?.let { collector ->
        val visibility = when {
          declaration.visibility.isPublicAPI -> "public"
          declaration.visibility.toString().contains("internal") -> "internal"
          declaration.visibility.toString().contains("private") -> "private"
          else -> "public"
        }

        val parameters = declaration.parameters
          .filter {
            val name = it.name.asString()
            !name.startsWith("$") && name != "<this>"
          }
          .map { param ->
            val renderedType = param.type.render()

            // If rendered type contains @[Composable], it's a composable function - STABLE
            val isComposableFunction =
              renderedType.contains("@[Composable]") || renderedType.contains("@Composable")

            val stability = if (isComposableFunction) {
              "STABLE"
            } else {
              analyzeParameterStability(param.type)
            }

            val reason = if (isComposableFunction) {
              "composable function type"
            } else {
              getStabilityReason(param.type, stability)
            }

            com.skydoves.compose.stability.compiler.ParameterStabilityInfo(
              name = param.name.asString(),
              type = renderedType,
              stability = stability,
              reason = reason,
            )
          }

        collector.recordComposable(
          com.skydoves.compose.stability.compiler.ComposableStabilityInfo(
            qualifiedName = fqName,
            simpleName = functionName,
            visibility = visibility,
            skippable = isSkippable(declaration, parameters),
            restartable = true, // All composables are restartable by default
            returnType = declaration.returnType.render(),
            parameters = parameters,
          ),
        )
      }
    }

    // If @TraceRecomposition is present, inject tracking code automatically
    if (hasTraceRecomposition) {
      val hasBody = declaration.body != null
      if (!hasBody) {
        return super.visitFunctionNew(declaration)
      }

      // Initialize IR builder symbols once
      if (!irBuilderInitialized) {
        irBuilderInitialized = irBuilder.initializeSymbols()
        if (!irBuilderInitialized) {
          return super.visitFunctionNew(declaration)
        }
      }

      // Extract annotation parameters
      val annotation = declaration.annotations.find { annot ->
        val annotationClass = annot.symbol.owner.parent as? IrClass
        annotationClass?.kotlinFqName == traceRecompositionFqName
      }

      var tag = ""
      var threshold = 1

      annotation?.let { annot ->
        val annotationClass = annot.symbol.owner
        val paramNameToIndex = annotationClass.parameters
          .mapIndexed { index, param ->
            param.name.asString() to index
          }
          .toMap()

        // Cover tag
        paramNameToIndex["tag"]?.let { index ->
          annot.arguments.getOrNull(index)?.let { value ->
            tag = extractConstStringValue(value) ?: ""
          }
        }

        // Cover threshold
        paramNameToIndex["threshold"]?.let { index ->
          annot.arguments.getOrNull(index)?.let { value ->
            threshold = extractConstIntValue(value) ?: 1
          }
        }
      }

      // Analyze parameter stability
      val parameterStabilities = declaration.parameters
        .filter {
          val name = it.name.asString()
          !name.startsWith("$") && name != "<this>"
        }
        .map { param ->
          val renderedType = param.type.render()

          // If rendered type contains @[Composable], it's a composable function - STABLE
          val isComposableFunction =
            renderedType.contains("@[Composable]") || renderedType.contains("@Composable")

          val stability = if (isComposableFunction) {
            ParameterStability.STABLE
          } else {
            analyzeTypeStability(param.type)
          }

          RecompositionIrBuilder.ParameterStabilityData(
            name = param.name.asString(),
            typeString = renderedType,
            parameter = param,
            stability = stability,
          )
        }

      // Inject tracking code
      val injected = irBuilder.injectTrackingCode(
        function = declaration,
        functionName = functionName,
        tag = tag,
        threshold = threshold,
        parameterStabilities = parameterStabilities,
      )
    }

    return super.visitFunctionNew(declaration)
  }

  /**
   * Analyzes type stability following the same order as K2 implementation.
   * Order matters for correctness and consistency.
   *
   * Analysis order (must match KtStabilityInferencer):
   * 1. Nullable types (MUST be first)
   * 2. Type parameters (T, E, K, V) - RUNTIME
   * 3. Function types - STABLE
   * 4. Known stable types
   * 5. @Stable/@Immutable annotations
   * 6. Primitives
   * 7. String
   * 8. Unit/Nothing
   * 9. Mutable collections - UNSTABLE
   * 10. Kotlinx immutable collections - STABLE
   * 11. Standard collections - RUNTIME
   * 12. Value classes (inline classes)
   * 13. Enums - STABLE
   * 14. @StabilityInferred - RUNTIME
   * 15. Interfaces - RUNTIME
   * 16. Abstract classes - RUNTIME
   * 17. Regular classes (with property analysis)
   */
  private fun analyzeTypeStability(type: IrType): ParameterStability {
    // 1. Nullable types - MUST be checked first to strip nullability
    if (type.isNullable()) {
      return analyzeTypeStability(type.makeNotNull())
    }

    // 2. Function types (including lambdas, suspend, @Composable) - STABLE
    if (type.isFunctionOrKFunction()) {
      return ParameterStability.STABLE
    }

    val classSymbol = type.classOrNull
    val fqName = try {
      type.classFqName?.asString()
    } catch (e: StackOverflowError) {
      return ParameterStability.RUNTIME
    }

    val typeId = fqName ?: classSymbol?.owner?.name?.asString() ?: type.render()
    val currentlyAnalyzing = analyzingTypes.get()
    if (typeId in currentlyAnalyzing) {
      return ParameterStability.RUNTIME
    }

    currentlyAnalyzing.add(typeId)
    try {
      return analyzeTypeStabilityInternal(type, classSymbol, fqName)
    } finally {
      currentlyAnalyzing.remove(typeId)
    }
  }

  /**
   * Internal implementation separated for proper cleanup.
   */
  private fun analyzeTypeStabilityInternal(
    type: IrType,
    classSymbol: org.jetbrains.kotlin.ir.symbols.IrClassSymbol?,
    fqName: String?,
  ): ParameterStability {
    // 2b. Type parameters (T, E, K, V in generics) - RUNTIME
    // If we can't resolve to a class, it's likely a type parameter
    if (classSymbol == null) {
      return ParameterStability.RUNTIME
    }

    // 4. Known stable types
    if (isKnownStableType(type)) {
      return ParameterStability.STABLE
    }

    // 5. Check for @Stable or @Immutable annotations
    if (type.hasStableAnnotation()) {
      return ParameterStability.STABLE
    }

    // 6. Primitives are always stable
    if (type.isPrimitiveType()) {
      return ParameterStability.STABLE
    }

    // 7. String is stable
    if (type.isString()) {
      return ParameterStability.STABLE
    }

    // 8. Unit and Nothing are stable
    if (type.isUnit() || type.isNothing()) {
      return ParameterStability.STABLE
    }

    // 9. Check for mutable collections (always unstable)
    if (type.isMutableCollection()) {
      return ParameterStability.UNSTABLE
    }

    // 10. Check for kotlinx immutable collections (always stable)
    if (fqName != null && fqName.startsWith("kotlinx.collections.immutable.")) {
      if (fqName.contains("Immutable") || fqName.contains("Persistent")) {
        return ParameterStability.STABLE
      }
    }

    // 11. Standard collections (List, Set, Map) - RUNTIME check needed
    if (type.isCollection()) {
      return ParameterStability.RUNTIME
    }

    // 12. Value classes (inline classes) - stability depends on underlying type
    if (classSymbol.owner.isValueClass()) {
      return analyzeValueClass(classSymbol.owner)
    }

    val clazz = classSymbol.owner

    // 13. Enum classes are always stable
    if (clazz.isEnumClassIr()) {
      return ParameterStability.STABLE
    }

    // 14. Interfaces - cannot determine (RUNTIME)
    if (clazz.isInterfaceIr()) {
      return ParameterStability.RUNTIME
    }

    // 15. Abstract classes - cannot determine (RUNTIME)
    if (clazz.modality == org.jetbrains.kotlin.descriptors.Modality.ABSTRACT) {
      return ParameterStability.RUNTIME
    }

    // 16. Regular classes - analyze properties first before checking @StabilityInferred
    val propertyStability = analyzeClassProperties(clazz)

    when (propertyStability) {
      ParameterStability.STABLE -> return ParameterStability.STABLE
      ParameterStability.UNSTABLE -> return ParameterStability.UNSTABLE
      ParameterStability.RUNTIME -> {
        // 17. Check @StabilityInferred: parameters=0 means stable, else runtime
        val stabilityInferredParams = type.getStabilityInferredParameters()
        return if (stabilityInferredParams == 0) {
          ParameterStability.STABLE
        } else {
          ParameterStability.RUNTIME
        }
      }
    }
  }

  /**
   * Analyzes class properties to determine overall stability.
   * Matches K2 implementation logic.
   */
  private fun analyzeClassProperties(clazz: IrClass): ParameterStability {
    val properties = clazz.declarations
      .filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrProperty>()

    if (properties.isEmpty()) {
      return ParameterStability.STABLE
    }

    val hasMutableProperty = properties.any { it.isVar }
    if (hasMutableProperty) {
      return ParameterStability.UNSTABLE
    }

    val propertyStabilities = properties.mapNotNull { property ->
      property.getter?.returnType?.let { analyzeTypeStability(it) }
    }

    if (propertyStabilities.any { it == ParameterStability.UNSTABLE }) {
      return ParameterStability.UNSTABLE
    }

    if (propertyStabilities.all { it == ParameterStability.STABLE }) {
      return ParameterStability.STABLE
    }

    // Mixed stability (some RUNTIME) - class needs runtime check
    return ParameterStability.RUNTIME
  }

  /**
   * Analyzes value class (inline class) stability.
   * Value classes inherit the stability of their underlying type.
   */
  private fun analyzeValueClass(clazz: IrClass): ParameterStability {
    val properties = clazz.declarations
      .filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrProperty>()

    val underlyingProperty = properties.firstOrNull()
    if (underlyingProperty != null) {
      val underlyingType = underlyingProperty.getter?.returnType
      if (underlyingType != null) {
        return analyzeTypeStability(underlyingType)
      }
    }

    return ParameterStability.RUNTIME
  }

  private fun IrType.hasStableAnnotation(): Boolean {
    val classSymbol = this.classOrNull ?: return false
    val clazz = classSymbol.owner
    return clazz.hasAnnotation(stableFqName) || clazz.hasAnnotation(immutableFqName)
  }

  private fun IrType.hasStabilityInferredAnnotation(): Boolean {
    val classSymbol = this.classOrNull ?: return false
    val clazz = classSymbol.owner
    val stabilityInferredFqName =
      FqName("androidx.compose.runtime.internal.StabilityInferred")
    return clazz.hasAnnotation(stabilityInferredFqName)
  }

  /**
   * TODO: Read @StabilityInferred parameters field without deprecated IR APIs.
   * Returns null (conservative RUNTIME) until stable API is available.
   */
  private fun IrType.getStabilityInferredParameters(): Int? {
    return null
  }

  private fun IrType.isCollection(): Boolean {
    val className = this.classFqName?.asString() ?: return false
    return className.startsWith("kotlin.collections.") &&
      (
        className.contains("List") ||
          className.contains("Set") ||
          className.contains("Map")
        )
  }

  private fun IrType.isMutableCollection(): Boolean {
    val className = this.classFqName?.asString() ?: return false
    return className.startsWith("kotlin.collections.") && className.contains("Mutable")
  }

  private fun IrClass.isValueClass(): Boolean {
    val jvmInlineFqName = FqName("kotlin.jvm.JvmInline")
    return this.hasAnnotation(jvmInlineFqName)
  }

  private fun IrClass.isEnumClassIr(): Boolean {
    return this.superTypes.any { it.classFqName?.asString() == "kotlin.Enum" }
  }

  private fun IrClass.isInterfaceIr(): Boolean {
    val hasNoConstructors = this.declarations
      .none { it is org.jetbrains.kotlin.ir.declarations.IrConstructor }

    // Also check if modality is ABSTRACT
    return hasNoConstructors && this.modality == org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
  }

  private fun isKnownStableType(type: IrType): Boolean {
    val fqName = type.classFqName?.asString() ?: return false
    return fqName in KNOWN_STABLE_TYPES
  }

  /**
   * Extract a string value from an IrConst expression using reflection.
   * Needed because IrConst.value is not directly accessible in Kotlin 2.1.0.
   */
  private fun extractConstStringValue(expr: IrExpression): String? {
    if (expr !is IrConst) {
      return null
    }
    return try {
      val fields = listOf("value", "getValue")
      for (fieldName in fields) {
        try {
          val valueField = expr.javaClass.getDeclaredField(fieldName)
          valueField.isAccessible = true
          val result = valueField.get(expr)?.toString()
          return result
        } catch (e: NoSuchFieldException) {
          continue
        }
      }
      null
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Extract an integer value from an IrConst expression using reflection.
   * Needed because IrConst.value is not directly accessible in Kotlin 2.1.0.
   */
  private fun extractConstIntValue(expr: IrExpression): Int? {
    if (expr !is IrConst) {
      return null
    }
    return try {
      val fields = listOf("value", "getValue")
      for (fieldName in fields) {
        try {
          val valueField = expr.javaClass.getDeclaredField(fieldName)
          valueField.isAccessible = true
          val result = valueField.get(expr) as? Int
          return result
        } catch (e: NoSuchFieldException) {
          continue
        }
      }
      null
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Analyze parameter stability and return as string.
   */
  private fun analyzeParameterStability(type: IrType): String {
    return when (analyzeTypeStability(type)) {
      ParameterStability.STABLE -> "STABLE"
      ParameterStability.UNSTABLE -> "UNSTABLE"
      ParameterStability.RUNTIME -> "RUNTIME"
    }
  }

  /**
   * Get the reason why a type has the given stability.
   */
  private fun getStabilityReason(type: IrType, stability: String): String? {
    return when (stability) {
      "STABLE" -> when {
        type.isPrimitiveType() -> "primitive type"
        type.isString() -> "String is immutable"
        type.isFunctionOrKFunction() -> "function type"
        type.hasStableAnnotation() -> "marked @Stable or @Immutable"
        isKnownStableType(type) -> "known stable type"
        else -> null
      }
      "UNSTABLE" -> when {
        type.isMutableCollection() -> "mutable collection"
        else -> "has mutable properties or unstable members"
      }
      "RUNTIME" -> "requires runtime check"
      else -> null
    }
  }

  /**
   * Determine if a composable function is skippable.
   * A function is skippable if all parameters are stable.
   */
  private fun isSkippable(
    declaration: IrFunction,
    parameters: List<com.skydoves.compose.stability.compiler.ParameterStabilityInfo>,
  ): Boolean {
    // If any parameter is unstable, the function is not skippable
    return parameters.all { it.stability == "STABLE" }
  }

  public companion object {
    /**
     * Set of known stable types from Compose and standard library.
     * Must match StabilityAnalysisConstants.KNOWN_STABLE_TYPES in IDEA plugin.
     */
    private val KNOWN_STABLE_TYPES: Set<String> = setOf(
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
  }
}
