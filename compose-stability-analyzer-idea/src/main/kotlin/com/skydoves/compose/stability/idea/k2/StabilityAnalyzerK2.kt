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

import com.intellij.openapi.roots.ProjectFileIndex
import com.skydoves.compose.stability.idea.RestartabilityRules
import com.skydoves.compose.stability.idea.StabilityConstants
import com.skydoves.compose.stability.idea.hasAnnotation
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ComposableStabilityInfo
import com.skydoves.compose.stability.runtime.ParameterStability
import com.skydoves.compose.stability.runtime.ParameterStabilityInfo
import com.skydoves.compose.stability.runtime.ReceiverKind
import com.skydoves.compose.stability.runtime.ReceiverStabilityInfo
import org.jetbrains.kotlin.analysis.api.KaSession
// Kotlin 2.5 deprecates this in favour of org.jetbrains.kotlin.analysis.api.session.analyze,
// but that package is absent from the Kotlin plugin bundled with the IDEs we compile against,
// so the move has to wait until the minimum supported IDE ships it.
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * K2 Analysis API-based stability analyzer for @Composable functions.
 *
 * This analyzer uses K2 semantic analysis for accurate type resolution,
 * providing 2-3x faster analysis and ~15% higher accuracy compared to PSI-based analysis.
 */
internal object StabilityAnalyzerK2 {

  private val settings: StabilitySettingsState
    get() = StabilitySettingsState.getInstance()

  /**
   * Analyzes a @Composable function using K2 Analysis API.
   *
   * @param function the @Composable function to analyze
   * @return stability information or null if analysis fails
   */
  internal fun analyze(function: KtNamedFunction): ComposableStabilityInfo? {
    // Check if function has @Composable annotation (fast check before K2 analysis)
    if (!function.hasAnnotation(StabilityConstants.Strings.COMPOSABLE)) {
      return null
    }

    return try {
      analyze(function) {
        analyzeWithK2Session(function)
      }
    } catch (e: LinkageError) {
      // K2 Analysis API incompatibility - fall back to PSI. LinkageError covers NoSuchMethodError
      // (a removed/changed method) and NoClassDefFoundError (a removed class), so the plugin keeps
      // working on IDEs whose Analysis API differs from the one it was built against, in either
      // direction. This is what makes an open-ended until-build safe (issue #191).
      null
    } catch (e: Exception) {
      // K2 analysis failed - caller should fall back to PSI
      null
    }
  }

  /**
   * Performs K2-based analysis within a KaSession context.
   */
  private fun KaSession.analyzeWithK2Session(function: KtNamedFunction): ComposableStabilityInfo {
    // Get function symbol
    val functionSymbol = function.symbol

    // A non-restartable composable has no restart group, so it can never be skipped, and
    // @NonSkippableComposable opts out of skipping explicitly. In either case parameter stability
    // is irrelevant, so skip the analysis. Kept in sync with the compiler's
    // StabilityAnalyzerTransformer (issue #184).
    val isRestartable = isRestartableComposable(function, functionSymbol)
    val hasNonSkippable =
      function.hasAnnotation(StabilityConstants.Strings.NON_SKIPPABLE_COMPOSABLE)
    if (!isRestartable || hasNonSkippable) {
      return ComposableStabilityInfo(
        name = function.name ?: StabilityConstants.Strings.UNKNOWN,
        fqName = functionSymbol.callableId?.asSingleFqName()?.asString()
          ?: StabilityConstants.Strings.UNKNOWN,
        isRestartable = isRestartable,
        isSkippable = false,
        isReadonly = function.hasAnnotation(StabilityConstants.Strings.READ_ONLY_COMPOSABLE),
        parameters = emptyList(),
        receivers = emptyList(),
      )
    }

    // Get the module containing this composable function (usage site)
    val usageSiteModule = ProjectFileIndex.getInstance(function.project).getModuleForFile(
      function.containingKtFile.virtualFile,
    )

    val inferencer = KtStabilityInferencer(function.project, usageSiteModule)

    // Names of value parameters declared `vararg`, read from the PSI modifier (purely syntactic
    // and reliable regardless of symbol resolution state; KaValueParameterSymbol.isVararg is not
    // always populated). A vararg compiles to an array, which is never stable, but the symbol
    // reports the element type as its returnType, so it would otherwise be read as stable (#175).
    val varargParameterNames = function.valueParameters
      .filter { it.hasModifier(KtTokens.VARARG_KEYWORD) }
      .mapNotNull { it.name }
      .toSet()

    // Analyze value parameters
    val parameters = functionSymbol.valueParameters.map { param ->
      // A vararg parameter is treated as an array to match a real Array<T> parameter (issue #175).
      val isVararg = param.name.asString() in varargParameterNames
      val stability = if (isVararg) {
        KtStability.Runtime(
          className = "kotlin.Array",
          reason = "vararg parameter (compiles to an array)",
        )
      } else {
        with(inferencer) { ktStabilityOf(param.returnType) }
      }

      ParameterStabilityInfo(
        name = param.name.asString(),
        type = if (isVararg) {
          "vararg ${param.returnType.renderAsString()}"
        } else {
          param.returnType.renderAsString()
        },
        stability = stability.toParameterStability(),
        reason = stability.getReasonString(),
      )
    }

    // Analyze receivers (extension, dispatch, context)
    val receivers = analyzeReceivers(functionSymbol, inferencer)

    // Check if all parameters AND receivers are stable
    val isNaturallySkippable =
      parameters.all { it.stability == ParameterStability.STABLE } &&
        receivers.all { it.stability == ParameterStability.STABLE }

    // In strong skipping mode, ALL composables are skippable
    val isStrongSkippingEnabled = settings.isStrongSkippingEnabled
    val isSkippable = if (isStrongSkippingEnabled) {
      true
    } else {
      isNaturallySkippable
    }

    val isSkippableInStrongSkippingMode = isStrongSkippingEnabled && !isNaturallySkippable
    // isRestartable was computed above; only restartable composables reach this point.
    val isReadonly = function.hasAnnotation(StabilityConstants.Strings.READ_ONLY_COMPOSABLE)

    return ComposableStabilityInfo(
      name = function.name ?: StabilityConstants.Strings.UNKNOWN,
      fqName = functionSymbol.callableId?.asSingleFqName()?.asString()
        ?: StabilityConstants.Strings.UNKNOWN,
      isRestartable = isRestartable,
      isSkippable = isSkippable,
      isReadonly = isReadonly,
      parameters = parameters,
      isSkippableInStrongSkippingMode = isSkippableInStrongSkippingMode,
      receivers = receivers,
    )
  }

  /**
   * Whether a @Composable is restartable — i.e. the compiler wraps it in a restart group. A
   * non-restartable composable has no restart group, so it can never be skipped and its parameter
   * stability is moot. Mirrors the compiler's StabilityAnalyzerTransformer.isRestartable so the IDE
   * verdict and the `stabilityDump` output stay in sync (issue #184). The structural half lives in
   * [RestartabilityRules]; `@ReadOnlyComposable` is deliberately not one of the rules, because the
   * Compose compiler does not consult it here.
   */
  private fun KaSession.isRestartableComposable(
    function: KtNamedFunction,
    functionSymbol: KaFunctionSymbol,
  ): Boolean {
    if (RestartabilityRules.isStructurallyNonRestartable(function)) return false
    val returnTypeFqName =
      functionSymbol.returnType.expandedSymbol?.classId?.asSingleFqName()?.asString()
    if (returnTypeFqName != "kotlin.Unit") return false
    return true
  }

  /**
   * Analyzes all receivers (extension, dispatch, context) of a function.
   */
  private fun KaSession.analyzeReceivers(
    functionSymbol: KaFunctionSymbol,
    inferencer: KtStabilityInferencer,
  ): List<ReceiverStabilityInfo> {
    val receivers = mutableListOf<ReceiverStabilityInfo>()

    // 1. Extension receiver
    @Suppress("EXPERIMENTAL_API_USAGE")
    functionSymbol.receiverParameter?.let { receiver ->
      val receiverType = receiver.returnType
      val stability = with(inferencer) { ktStabilityOf(receiverType) }

      receivers.add(
        ReceiverStabilityInfo(
          type = receiverType.renderAsString(),
          stability = stability.toParameterStability(),
          reason = stability.getReasonString(),
          receiverKind = ReceiverKind.EXTENSION,
        ),
      )
    }

    // 2. Dispatch receiver (containing class)
    @Suppress("EXPERIMENTAL_API_USAGE", "DEPRECATION")
    val dispatchReceiverType = functionSymbol.dispatchReceiverType
    if (dispatchReceiverType != null) {
      val stability = with(inferencer) { ktStabilityOf(dispatchReceiverType) }

      receivers.add(
        ReceiverStabilityInfo(
          type = dispatchReceiverType.renderAsString(),
          stability = stability.toParameterStability(),
          reason = stability.getReasonString(),
          receiverKind = ReceiverKind.DISPATCH,
        ),
      )
    }

    // 3. Context parameters (formerly context receivers). JetBrains asked us to migrate off
    // KaContextReceiver (removed in IDEA 2026.3 / Kotlin 2.4, issue #177, KT-87310) to
    // KaContextParameterSymbol. That symbol API does not exist in older IDEs (2024.2 / 2024.3), so
    // it is read reflectively (see contextParameterReturnTypes) to keep one binary compatible
    // across the supported IDE range without the plugin verifier flagging a missing symbol.
    contextParameterReturnTypes(functionSymbol).forEach { contextType ->
      val stability = with(inferencer) { ktStabilityOf(contextType) }

      receivers.add(
        ReceiverStabilityInfo(
          type = contextType.renderAsString(),
          stability = stability.toParameterStability(),
          reason = stability.getReasonString(),
          receiverKind = ReceiverKind.CONTEXT,
        ),
      )
    }

    return receivers
  }

  /**
   * Return types of [symbol]'s context parameters, read reflectively.
   *
   * `KaCallableSymbol.contextParameters` (and the `KaContextParameterSymbol` element type) only
   * exist in the Kotlin Analysis API bundled with newer IDEs; older IDEs (2024.2 / 2024.3) do not
   * ship them. A direct call makes the JetBrains Plugin Verifier report a "method/class not found"
   * against those IDEs and risks a linkage error at runtime. Reading the property reflectively keeps
   * a single plugin binary compatible across the whole supported range: IDEs that have the API get
   * context-parameter stability, and on older IDEs this returns an empty list so context parameters
   * are simply not analyzed. Each element is kept typed as its stable [KaCallableSymbol] supertype
   * to avoid referencing `KaContextParameterSymbol`.
   */
  private fun KaSession.contextParameterReturnTypes(symbol: KaFunctionSymbol): List<KaType> =
    runCatching {
      val kaCallableSymbolKt =
        Class.forName("org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbolKt")
      val getContextParameters =
        kaCallableSymbolKt.methods.first { it.name == "getContextParameters" }
      val contextParameters = when (getContextParameters.parameterCount) {
        1 -> getContextParameters.invoke(null, symbol)
        else -> getContextParameters.invoke(null, this, symbol)
      }
      (contextParameters as? List<*>).orEmpty().mapNotNull { (it as? KaCallableSymbol)?.returnType }
    }.getOrDefault(emptyList())
}
