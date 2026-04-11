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
import org.jetbrains.kotlin.ir.declarations.IrLocalDelegatedProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
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
  private var trackStateFunctionSymbol: IrSimpleFunctionSymbol? = null
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

      trackStateFunctionSymbol = trackerClass?.functions?.firstOrNull {
        it.name.asString() == "trackState"
      }?.symbol

      // trackState is optional - don't fail if missing (backward compat)

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
    stateVariables: List<StateVariableData> = emptyList(),
  ): Boolean {
    try {
      val body = function.body as? IrBlockBody ?: return false

      val builder = DeclarationIrBuilder(context, function.symbol)

      // 1. Create tracker variable: val _tracker = rememberRecompositionTracker(...)
      val trackerVariable = createTrackerVariable(
        builder = builder,
        function = function,
        functionName = functionName,
        tag = tag,
        threshold = threshold,
      )

      // 2. Create trackParameter calls for each parameter
      val trackParameterCalls = parameterStabilities.map { paramData ->
        createTrackParameterCall(
          builder = builder,
          trackerVariable = trackerVariable,
          paramData = paramData,
        )
      }

      // 3. Create logIfThresholdMet call
      val logCall = createLogIfThresholdMetCall(builder, trackerVariable)

      if (stateVariables.isEmpty()) {
        // Standard mode: prepend tracker + params + log, then body
        val newStatements = mutableListOf<org.jetbrains.kotlin.ir.IrStatement>()
        newStatements.add(trackerVariable)
        newStatements.addAll(trackParameterCalls)
        newStatements.add(logCall)
        newStatements.addAll(body.statements)

        body.statements.clear()
        body.statements.addAll(newStatements)
      } else {
        // State tracking mode: interleave trackState calls after
        // state variable declarations (may be nested in IrBlocks
        // due to Compose compiler lowering), and move
        // logIfThresholdMet to the end
        val stateVarSet = stateVariables.associateBy {
          it.variable
        }

        injectTrackStateRecursive(
          body.statements,
          builder,
          trackerVariable,
          stateVarSet,
        )

        val newStatements =
          mutableListOf<org.jetbrains.kotlin.ir.IrStatement>()
        newStatements.add(trackerVariable)
        newStatements.addAll(trackParameterCalls)
        newStatements.addAll(body.statements)
        newStatements.add(logCall)

        body.statements.clear()
        body.statements.addAll(newStatements)
      }

      return true
    } catch (e: Exception) {
      return false
    }
  }

  /**
   * Recursively walks IR statements and inserts trackState calls
   * after detected state variable declarations. Handles IrBlock
   * nesting from Compose compiler lowering.
   */
  private fun injectTrackStateRecursive(
    statements: MutableList<org.jetbrains.kotlin.ir.IrStatement>,
    builder: IrBuilderWithScope,
    trackerVariable: IrVariable,
    stateVarSet: Map<IrVariable, StateVariableData>,
  ) {
    var i = 0
    while (i < statements.size) {
      val stmt = statements[i]

      // Recurse into blocks
      if (stmt is IrBlock) {
        injectTrackStateRecursive(
          stmt.statements,
          builder,
          trackerVariable,
          stateVarSet,
        )
      }

      // Recurse into when branches
      if (stmt is IrWhen) {
        for (branch in stmt.branches) {
          val branchResult = branch.result
          if (branchResult is IrBlock) {
            injectTrackStateRecursive(
              branchResult.statements,
              builder,
              trackerVariable,
              stateVarSet,
            )
          }
        }
      }

      // Check if this is a detected state variable (IrVariable)
      val variable = stmt as? IrVariable
      if (variable != null && variable in stateVarSet) {
        val stateData = stateVarSet[variable]!!
        val trackStateCall = createTrackStateCall(
          builder,
          trackerVariable,
          stateData,
        )
        if (trackStateCall != null) {
          statements.add(i + 1, trackStateCall)
          i++
        }
      }

      // Check IrLocalDelegatedProperty (delegate is our key)
      val delegatedProp = stmt as? IrLocalDelegatedProperty
      if (delegatedProp != null) {
        val delegate = delegatedProp.delegate
        if (delegate in stateVarSet) {
          val stateData = stateVarSet[delegate]!!
          val trackStateCall = createTrackStateCall(
            builder,
            trackerVariable,
            stateData,
          )
          if (trackStateCall != null) {
            statements.add(i + 1, trackStateCall)
            i++
          }
        }
      }

      i++
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
   * Creates IR call to `tracker.trackState(name, type, value)`.
   *
   * For delegated state variables (var x by ...), reads the variable directly (already unwrapped).
   * For non-delegated state variables (val s = mutableStateOf()),
   * reads s.value via property getter.
   */
  private fun createTrackStateCall(
    builder: IrBuilderWithScope,
    trackerVariable: IrVariable,
    stateData: StateVariableData,
  ): IrExpression? {
    val trackStateSymbol = trackStateFunctionSymbol ?: return null

    val call = builder.irCall(trackStateSymbol)
    call.arguments[0] = builder.irGet(trackerVariable) // dispatch receiver
    call.arguments[1] = builder.irString(stateData.name) // name: String
    call.arguments[2] = builder.irString(stateData.typeString) // type: String

    if (stateData.getter != null) {
      // Use the getter from IrLocalDelegatedProperty to read
      // the unwrapped value (calls getValue on the delegate)
      val getterCall = builder.irCall(stateData.getter.symbol)
      call.arguments[3] = getterCall
    } else {
      // Fallback: read the variable directly
      call.arguments[3] = builder.irGet(stateData.variable)
    }

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

  /**
   * Data class holding state variable information for tracking.
   */
  public data class StateVariableData(
    val name: String,
    val typeString: String,
    val variable: IrVariable,
    val isDelegated: Boolean,
    val getter: IrSimpleFunction? = null,
  )
}
