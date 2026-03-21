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

import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Helper class for building IR code to inject recomposition tracking.
 *
 * This class provides methods to generate IR statements for:
 * - Creating a RecompositionTracker instance via remember { }
 * - Calling trackParameter for each composable parameter
 * - Calling logIfThresholdMet to trigger logging
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
public class RecompositionIrBuilder(
  private val context: IrPluginContext,
) {

  // FqNames for runtime classes and functions
  private val recompositionTrackerFqName =
    FqName("com.skydoves.compose.stability.runtime.RecompositionTracker")
  private val rememberRecompositionTrackerFqName =
    FqName("com.skydoves.compose.stability.runtime.rememberRecompositionTracker")

  // Cached symbols
  private var trackerClassSymbol: IrClassSymbol? = null
  private var rememberTrackerFunctionSymbol: IrSimpleFunctionSymbol? = null
  private var trackParameterFunctionSymbol: IrSimpleFunctionSymbol? = null
  private var logIfThresholdMetFunctionSymbol: IrSimpleFunctionSymbol? = null

  /**
   * Initialize and cache symbols for runtime classes.
   * This should be called before any IR generation.
   */
  public fun initializeSymbols(): Boolean {
    try {
      // Find RecompositionTracker class
      trackerClassSymbol = context.referenceClass(
        ClassId.topLevel(recompositionTrackerFqName),
      )

      if (trackerClassSymbol == null) {
        return false
      }

      // Find rememberRecompositionTracker function
      val rememberTrackerFunctions = context.referenceFunctions(
        CallableId(
          FqName("com.skydoves.compose.stability.runtime"),
          Name.identifier("rememberRecompositionTracker"),
        ),
      )

      rememberTrackerFunctionSymbol = rememberTrackerFunctions.firstOrNull()

      if (rememberTrackerFunctionSymbol == null) {
        return false
      }

      val trackerClass = trackerClassSymbol?.owner
      trackParameterFunctionSymbol = trackerClass?.functions?.firstOrNull {
        it.name.asString() == "trackParameter"
      }?.symbol

      if (trackParameterFunctionSymbol == null) {
        return false
      }

      logIfThresholdMetFunctionSymbol = trackerClass?.functions?.firstOrNull {
        it.name.asString() == "logIfThresholdMet"
      }?.symbol

      if (logIfThresholdMetFunctionSymbol == null) {
        return false
      }

      return true
    } catch (e: Exception) {
      return false
    }
  }

  /**
   * Injects recomposition tracking code into the given function.
   *
   * Generated code:
   * ```
   * val _tracker = remember { RecompositionTracker(name, tag, threshold) }
   * _tracker.trackParameter("param1", "Type1", param1, isStable1)
   * _tracker.trackParameter("param2", "Type2", param2, isStable2)
   * _tracker.logIfThresholdMet()
   * // ... original function body
   * ```
   */
  public fun injectTrackingCode(
    function: IrFunction,
    functionName: String,
    tag: String,
    threshold: Int,
    parameterStabilities: List<ParameterStabilityData>,
  ): Boolean {
    try {
      val body = function.body as? IrBlockBody ?: return false

      val builder = DeclarationIrBuilder(context, function.symbol)

      // Create new body with tracking code prepended
      val newStatements = mutableListOf<org.jetbrains.kotlin.ir.IrStatement>()

      // 1. Create tracker variable: val _tracker = remember { ... }
      val trackerVariable = createTrackerVariable(
        builder = builder,
        function = function,
        functionName = functionName,
        tag = tag,
        threshold = threshold,
      )
      newStatements.add(trackerVariable)

      // 2. Add trackParameter calls for each parameter
      parameterStabilities.forEach { paramData ->
        val trackParameterCall = createTrackParameterCall(
          builder = builder,
          trackerVariable = trackerVariable,
          paramData = paramData,
        )
        newStatements.add(trackParameterCall)
      }

      // 3. Add logIfThresholdMet call
      val logCall = createLogIfThresholdMetCall(builder, trackerVariable)
      newStatements.add(logCall)

      // 4. Add original body statements
      newStatements.addAll(body.statements)

      // Replace body with new statements
      body.statements.clear()
      body.statements.addAll(newStatements)

      return true
    } catch (e: Exception) {
      return false
    }
  }

  /**
   * Creates an IrVariable for the tracker.
   */
  private fun createTrackerVariable(
    builder: IrBuilderWithScope,
    function: IrFunction,
    functionName: String,
    tag: String,
    threshold: Int,
  ): IrVariable {
    val rememberTrackerCall = createRememberTrackerCall(builder, functionName, tag, threshold)

    return buildVariable(
      parent = function,
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      origin = OriginCompat.DEFINED,
      name = Name.identifier("_tracker"),
      type = trackerClassSymbol!!.defaultType,
      isVar = false,
      isConst = false,
      isLateinit = false,
    ).apply {
      initializer = rememberTrackerCall
    }
  }

  /**
   * Creates IR call to rememberRecompositionTracker(...).
   *
   * This generates:
   * ```
   * rememberRecompositionTracker(functionName, tag, threshold)
   * ```
   */
  private fun createRememberTrackerCall(
    builder: IrBuilderWithScope,
    functionName: String,
    tag: String,
    threshold: Int,
  ): IrExpression {
    val rememberTrackerSymbol = rememberTrackerFunctionSymbol
      ?: error("rememberRecompositionTracker function not initialized")

    // Call rememberRecompositionTracker(functionName, tag, threshold)
    val call = builder.irCall(rememberTrackerSymbol)
    call.arguments[0] = builder.irString(functionName)
    call.arguments[1] = builder.irString(tag)
    call.arguments[2] = builder.irInt(threshold)
    return call
  }

  /**
   * Creates IR call to `tracker.trackParameter(name, type, value, isStable)`.
   */
  private fun createTrackParameterCall(
    builder: IrBuilderWithScope,
    trackerVariable: IrVariable,
    paramData: ParameterStabilityData,
  ): IrExpression {
    val trackParameterSymbol = trackParameterFunctionSymbol
      ?: error("trackParameter function symbol not initialized")

    val call = builder.irCall(trackParameterSymbol)

    // In Kotlin 2.2.21+, arguments list includes receivers + value parameters
    call.arguments[0] = builder.irGet(trackerVariable) // dispatch receiver
    call.arguments[1] = builder.irString(paramData.name) // name: String
    call.arguments[2] = builder.irString(paramData.typeString) // type: String
    call.arguments[3] = builder.irGet(paramData.parameter) // value: Any?

    val isStable = paramData.stability == ParameterStability.STABLE
    call.arguments[4] = builder.irBoolean(isStable) // isStable: Boolean

    return call
  }

  /**
   * Creates IR call to `tracker.logIfThresholdMet()`.
   */
  private fun createLogIfThresholdMetCall(
    builder: IrBuilderWithScope,
    trackerVariable: IrVariable,
  ): IrExpression {
    val logSymbol = logIfThresholdMetFunctionSymbol
      ?: error("logIfThresholdMet function symbol not initialized")

    val call = builder.irCall(logSymbol)
    // In Kotlin 2.2.21+, dispatch receiver goes in arguments[0]
    call.arguments[0] = builder.irGet(trackerVariable)
    return call
  }

  /**
   * Data class holding parameter information for tracking.
   */
  public data class ParameterStabilityData(
    val name: String,
    val typeString: String,
    val parameter: IrValueParameter,
    val stability: ParameterStability,
  )
}
