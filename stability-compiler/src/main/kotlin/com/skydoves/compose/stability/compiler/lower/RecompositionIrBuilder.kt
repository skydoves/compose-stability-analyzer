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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
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

  // FqNames for runtime classes and functions (standard mode)
  private val recompositionTrackerFqName =
    FqName("com.skydoves.compose.stability.runtime.RecompositionTracker")
  private val rememberRecompositionTrackerFqName =
    FqName("com.skydoves.compose.stability.runtime.rememberRecompositionTracker")

  // FqNames for full tracking mode
  private val runtimePackageFqName = FqName("com.skydoves.compose.stability.runtime")

  // Cached symbols (standard mode)
  private var trackerClassSymbol: IrClassSymbol? = null
  private var rememberTrackerFunctionSymbol: IrSimpleFunctionSymbol? = null
  private var trackParameterFunctionSymbol: IrSimpleFunctionSymbol? = null
  private var logIfThresholdMetFunctionSymbol: IrSimpleFunctionSymbol? = null

  // Cached symbols (full tracking mode)
  private var traceFullRecompositionSymbol: IrSimpleFunctionSymbol? = null
  private var traceDeepRecompositionSymbol: IrSimpleFunctionSymbol? = null
  private var scheduleDeepTrackingSymbol: IrSimpleFunctionSymbol? = null
  private var setScopeIdentitySymbol: IrSimpleFunctionSymbol? = null
  private var findGroupByKeySymbol: IrSimpleFunctionSymbol? = null

  // Compose Runtime symbols for deep tracking
  private var composerRecordSideEffectSymbol: IrSimpleFunctionSymbol? = null
  private var composerCompositionDataGetter: IrSimpleFunctionSymbol? = null
  private var compositionGroupIdentityGetter: IrSimpleFunctionSymbol? = null

  // Compose Runtime symbols for key() wrapper
  private var composerStartMovableGroupSymbol: IrSimpleFunctionSymbol? = null
  private var composerEndMovableGroupSymbol: IrSimpleFunctionSymbol? = null

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
   * Initialize and cache symbols for full tracking mode.
   *
   * Finds TraceFullRecomposition(String, String, String, Int, Composer).
   *
   * @return true if all required symbols were found, false otherwise.
   */
  public fun initializeFullTrackingSymbols(): Boolean {
    try {
      // Find TraceFullRecomposition top-level function
      val traceFunctions = context.referenceFunctions(
        CallableId(
          runtimePackageFqName,
          Name.identifier("TraceFullRecomposition"),
        ),
      )
      traceFullRecompositionSymbol = traceFunctions.firstOrNull() ?: return false

      // Find TraceDeepRecomposition function (direct call, for debugging)
      val deepTraceFunctions = context.referenceFunctions(
        CallableId(
          runtimePackageFqName,
          Name.identifier("TraceDeepRecomposition"),
        ),
      )
      traceDeepRecompositionSymbol = deepTraceFunctions.firstOrNull()

      // Find ScheduleDeepTracking function (uses recordSideEffect for proper timing)
      val scheduleDeepFunctions = context.referenceFunctions(
        CallableId(
          runtimePackageFqName,
          Name.identifier("ScheduleDeepTracking"),
        ),
      )
      scheduleDeepTrackingSymbol = scheduleDeepFunctions.firstOrNull()

      // Find SetScopeIdentity function
      val setIdentityFunctions = context.referenceFunctions(
        CallableId(
          runtimePackageFqName,
          Name.identifier("SetScopeIdentity"),
        ),
      )
      setScopeIdentitySymbol = setIdentityFunctions.firstOrNull()

      // Find findGroupByKey function
      val findGroupFunctions = context.referenceFunctions(
        CallableId(
          runtimePackageFqName,
          Name.identifier("findGroupByKey"),
        ),
      )
      findGroupByKeySymbol = findGroupFunctions.firstOrNull()

      // Find Compose Runtime symbols for deep tracking
      initializeComposeRuntimeSymbols()

      return true
    } catch (e: Exception) {
      return false
    }
  }

  /**
   * Initialize Compose Runtime symbols needed for deep tracking.
   */
  private fun initializeComposeRuntimeSymbols() {
    try {
      val composerFqName = FqName("androidx.compose.runtime.Composer")

      // Find Composer class
      val composerClass = context.referenceClass(ClassId.topLevel(composerFqName))?.owner
      if (composerClass != null) {
        // Find recordSideEffect method (used by SideEffect composable)
        composerRecordSideEffectSymbol = composerClass.functions.firstOrNull {
          it.name.asString() == "recordSideEffect"
        }?.symbol

        // Find compositionData property getter
        val compositionDataProp = composerClass.declarations
          .filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrProperty>()
          .firstOrNull { it.name.asString() == "compositionData" }
        composerCompositionDataGetter = compositionDataProp?.getter?.symbol

        // Find startMovableGroup and endMovableGroup for key() wrapper
        // startMovableGroup(key: Int, sourceInfo: String?)
        val startMovableCandidates = composerClass.functions.filter {
          it.name.asString() == "startMovableGroup"
        }.toList()
        // Look for the 2-parameter version (key: Int, sourceInfo: String?)
        composerStartMovableGroupSymbol = startMovableCandidates.firstOrNull { fn ->
          fn.parameters.toList().size >= 2 // may include dispatch receiver
        }?.symbol

        composerEndMovableGroupSymbol = composerClass.functions.firstOrNull {
          it.name.asString() == "endMovableGroup"
        }?.symbol
      }

      // Find CompositionGroup.identity property
      val compositionGroupFqName = FqName("androidx.compose.runtime.tooling.CompositionGroup")
      val compositionGroupClass = context.referenceClass(
        ClassId.topLevel(compositionGroupFqName),
      )?.owner
      if (compositionGroupClass != null) {
        val identityProp = compositionGroupClass.declarations
          .filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrProperty>()
          .firstOrNull { it.name.asString() == "identity" }
        compositionGroupIdentityGetter = identityProp?.getter?.symbol
      }
    } catch (_: Exception) {
      // Silently ignore initialization errors
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
   * Injects full tracking code into the given function for "full" tracking mode.
   *
   * Wraps the entire function body with key(callSiteKey) to enable scope identification,
   * then injects parameter tracking at the start and deep tracking at the end.
   *
   * Generated code:
   * ```
   * $composer.startMovableGroup(keyHash, "source")  // key() start
   *
   * TraceFullRecomposition(callSiteKey, functionName, tag, threshold, $composer,
   *   arrayOf("param1", "param2"), arrayOf(param1, param2))
   *
   * // ... original function body ...
   *
   * ScheduleDeepTracking($composer, callSiteKey, $composer.compositionData)
   * ```
   *
   * @param callSiteKey The Compose group key (Int) extracted from startRestartGroup, used for scope isolation
   */
  public fun injectFullTrackingCode(
    function: IrFunction,
    functionName: String,
    tag: String,
    threshold: Int,
    callSiteKey: Int,
  ): Boolean {
    try {
      val body = function.body as? IrBlockBody ?: return false
      val builder = DeclarationIrBuilder(context, function.symbol)
      val traceSymbol = traceFullRecompositionSymbol ?: return false

      // Find the $composer parameter
      val composerParam = function.parameters.firstOrNull {
        it.name.asString() == "\$composer"
      } ?: return false

      // Collect user-visible parameters (exclude $composer, $changed, <this>)
      val userParams = function.parameters.filter {
        val name = it.name.asString()
        !name.startsWith("$") && name != "<this>"
      }

      // Build: arrayOf("param1", "param2", ...)
      val arrayOfStringSymbol = findArrayOfFunction() ?: return false

      val stringType = context.irBuiltIns.stringType
      val anyNullableType = context.irBuiltIns.anyNType

      // Create paramNames array: arrayOf("name1", "name2", ...)
      val paramNamesArray = builder.irCall(arrayOfStringSymbol).apply {
        arguments[0] = builder.createVarargExpression(
          stringType,
          userParams.map { builder.irString(it.name.asString()) },
        )
      }

      // Create paramValues array: arrayOf(param1, param2, ...)
      val paramValuesArray = builder.irCall(arrayOfStringSymbol).apply {
        arguments[0] = builder.createVarargExpression(
          anyNullableType,
          userParams.map { builder.irGet(it) },
        )
      }

      // NOTE: We intentionally do NOT use startMovableGroup/endMovableGroup here.
      // Injecting these low-level group APIs at the IR level without proper Compose
      // compiler support causes slot table corruption and crashes. The high-level
      // key() composable handles this correctly, but generating its IR is complex.
      //
      // Instead, we:
      // 1. Insert TraceFullRecomposition at the start for parameter tracking
      // 2. Insert ScheduleDeepTracking at the end for slot data analysis
      // The deep tracking collects from the entire composition tree, which is
      // less precise but stable.

      // 1. Insert TraceFullRecomposition at the beginning
      val traceCall = builder.irCall(traceSymbol)
      traceCall.arguments[0] = builder.irInt(callSiteKey)
      traceCall.arguments[1] = builder.irString(functionName)
      traceCall.arguments[2] = builder.irString(tag)
      traceCall.arguments[3] = builder.irInt(threshold)
      traceCall.arguments[4] = builder.irGet(composerParam)
      traceCall.arguments[5] = paramNamesArray
      traceCall.arguments[6] = paramValuesArray

      body.statements.add(0, traceCall)

      // 2. Insert deep tracking at the end (uses recordSideEffect for proper timing)
      injectDeepTrackingCode(
        function = function,
        body = body,
        builder = builder,
        composerParam = composerParam,
        callSiteKey = callSiteKey,
        functionName = functionName,
      )

      return true
    } catch (_: Exception) {
      return false
    }
  }

  /**
   * Injects deep tracking code at the end of the function body.
   *
   * Uses ScheduleDeepTracking which internally calls composer.recordSideEffect
   * to ensure tracking runs after composition completes. This is important because
   * slot data may not be fully populated during the composition phase.
   *
   * Generated code:
   * ```
   * ScheduleDeepTracking($composer, callSiteKey, $composer.compositionData)
   * ```
   */
  private fun injectDeepTrackingCode(
    function: IrFunction,
    body: IrBlockBody,
    builder: DeclarationIrBuilder,
    composerParam: IrValueParameter,
    callSiteKey: Int,
    functionName: String,
  ): Boolean {
    val compositionDataGetter = composerCompositionDataGetter
    val scheduleSymbol = scheduleDeepTrackingSymbol

    if (compositionDataGetter == null || scheduleSymbol == null) {
      // Fallback to direct call if ScheduleDeepTracking not available
      val deepTraceSymbol = traceDeepRecompositionSymbol
      if (compositionDataGetter != null && deepTraceSymbol != null) {
        try {
          val getCompositionData = builder.irCall(compositionDataGetter).apply {
            arguments[0] = builder.irGet(composerParam)
          }
          val deepTraceCall = builder.irCall(deepTraceSymbol).apply {
            arguments[0] = builder.irInt(callSiteKey)
            arguments[1] = getCompositionData
          }
          body.statements.add(deepTraceCall)
          return true
        } catch (_: Exception) {
          // Silently ignore fallback errors
        }
      }
      return false
    }

    try {
      // Get compositionData: $composer.compositionData
      val getCompositionData = builder.irCall(compositionDataGetter).apply {
        arguments[0] = builder.irGet(composerParam) // dispatch receiver
      }

      // ScheduleDeepTracking($composer, callSiteKey, $composer.compositionData)
      // This uses recordSideEffect internally for proper timing
      val scheduleCall = builder.irCall(scheduleSymbol).apply {
        arguments[0] = builder.irGet(composerParam) // composer: Composer
        arguments[1] = builder.irInt(callSiteKey) // scopeId: Int (Compose group key)
        arguments[2] = getCompositionData // compositionData: CompositionData
      }

      // Add at the end of the body (before endMovableGroup if present)
      body.statements.add(scheduleCall)

      return true
    } catch (_: Exception) {
      return false
    }
  }

  /**
   * Find the kotlin.arrayOf function symbol.
   */
  private fun findArrayOfFunction(): IrSimpleFunctionSymbol? {
    return try {
      val candidates = context.referenceFunctions(
        CallableId(FqName("kotlin"), Name.identifier("arrayOf")),
      )
      candidates.firstOrNull()
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Create a vararg expression from a list of IR expressions.
   */
  private fun IrBuilderWithScope.createVarargExpression(
    elementType: org.jetbrains.kotlin.ir.types.IrType,
    elements: List<IrExpression>,
  ): IrExpression {
    return org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      type = context.irBuiltIns.arrayClass.defaultType,
      varargElementType = elementType,
    ).apply {
      elements.forEach { element ->
        this.elements.add(element)
      }
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
      origin = IrDeclarationOrigin.DEFINED,
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
