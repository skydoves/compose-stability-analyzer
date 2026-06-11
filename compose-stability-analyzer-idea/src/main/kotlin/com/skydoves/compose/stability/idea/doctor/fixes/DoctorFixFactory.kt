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
package com.skydoves.compose.stability.idea.doctor.fixes

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.SmartPointerManager
import com.skydoves.compose.stability.idea.StabilityAnalysisConstants
import com.skydoves.compose.stability.idea.blame.BlameAnalyzer
import com.skydoves.compose.stability.idea.isComposable
import com.skydoves.compose.stability.idea.reality.RealityGrade
import com.skydoves.compose.stability.idea.settings.StabilityProjectSettingsState
import com.skydoves.compose.stability.idea.settings.StabilitySettingsState
import com.skydoves.compose.stability.runtime.ParameterStability
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

/**
 * Builds the [DoctorFix]es for one problematic parameter of a composable. MUST be called inside
 * a read action (resolution-heavy); the produced fixes hold smart pointers and are applied later
 * on the EDT.
 */
internal object DoctorFixFactory {

  private const val MAX_HOIST_KEYS = 5
  private const val MAX_HOIST_CALL_SITES = 3

  /**
   * Builds fixes for [paramName] of [function]:
   * - var→val and @Immutable/@Stable when the parameter's class lives in project sources.
   * - stability-config entry when the type's fqName is known (library types included).
   * - remember-hoist at call sites for SILENT_WASTE params whose argument is a safe expression.
   */
  fun buildFixes(
    function: KtNamedFunction,
    paramName: String,
    paramStability: ParameterStability,
    realityGrade: RealityGrade?,
  ): List<DoctorFix> {
    val fixes = mutableListOf<DoctorFix>()
    val project = function.project
    val pointerManager = SmartPointerManager.getInstance(project)

    val param = function.valueParameters.find { it.name == paramName }
    val resolvedClass = param?.let { resolveParameterClass(it) }

    if (resolvedClass != null) {
      val className = resolvedClass.name ?: "type"
      val inProject = runCatching {
        val vFile = resolvedClass.containingFile?.virtualFile
        vFile != null && ProjectFileIndex.getInstance(project).isInContent(vFile) &&
          resolvedClass.containingFile.isWritable
      }.getOrDefault(false)

      if (inProject) {
        val vars = mutableVarDeclarations(resolvedClass)
        if (vars.isNotEmpty()) {
          fixes += ChangeVarToValFix(
            className = className,
            varPointers = vars.map { pointerManager.createSmartPsiElementPointer(it) },
          )
        }
        val hasStabilityAnnotation = resolvedClass.annotationEntries.any {
          it.shortName?.asString() == "Stable" || it.shortName?.asString() == "Immutable"
        }
        if (!hasStabilityAnnotation) {
          fixes += AddImmutableAnnotationFix(
            className = className,
            classPointer = pointerManager.createSmartPsiElementPointer(resolvedClass),
            // @Immutable on a class that keeps vars would be a lie; offer @Stable instead.
            useStable = vars.isNotEmpty(),
          )
        }
      }

      // Config-file entry works for both project and library types, as long as we know the
      // fqName, it is not already covered by the configured patterns, and it is not a
      // platform/known-stable type (suggesting "add kotlin.collections.List to the config"
      // would be noise at best and misleading at worst).
      val typeFqName = resolvedClass.fqName?.asString()
      if (typeFqName != null &&
        !isPlatformType(typeFqName) &&
        !StabilityAnalysisConstants.isKnownStable(typeFqName) &&
        !isCoveredByConfig(project, typeFqName)
      ) {
        fixes += AddToStabilityConfigFix(
          typeFqName = typeFqName,
          configFilePath = resolveConfigPath(project),
        )
      }
    }

    // Remember-hoist for parameters with OBSERVED silent waste (equals-equal values arriving
    // as fresh instances), and — as an estimated hint — for unstable params with no runtime
    // data yet. The transformation is identity-preserving either way; the conservative
    // applicability rules keep the static offering safe.
    if (realityGrade == RealityGrade.SILENT_WASTE ||
      (realityGrade == null && paramStability == ParameterStability.UNSTABLE)
    ) {
      fixes += buildRememberHoistFixes(function, paramName, pointerManager)
    }

    return fixes
  }

  // ── Type class resolution ─────────────────────────────────────────────────

  /** Resolves the parameter's declared type to its KtClass, K2-safe (never throws). */
  private fun resolveParameterClass(param: KtParameter): KtClass? {
    return runCatching {
      var typeElement = param.typeReference?.typeElement
      if (typeElement is KtNullableType) typeElement = typeElement.innerType
      val userType = typeElement as? KtUserType ?: return null
      userType.referenceExpression?.resolveMainReference() as? KtClass
    }.getOrNull()
  }

  /** All `var` declarations of a class: mutable primary-constructor params + body properties. */
  private fun mutableVarDeclarations(ktClass: KtClass): List<KtDeclaration> {
    val constructorVars = ktClass.primaryConstructorParameters
      .filter { it.hasValOrVar() && it.isMutable }
    val bodyVars = ktClass.getProperties().filter { it.isVar }
    return constructorVars + bodyVars
  }

  private fun resolveConfigPath(project: com.intellij.openapi.project.Project): String? {
    val projectPath = StabilityProjectSettingsState.getInstance(project).stabilityConfigurationPath
    if (projectPath.isNotEmpty()) return projectPath
    val globalPath = StabilitySettingsState.getInstance().stabilityConfigurationPath
    return globalPath.ifEmpty { null }
  }

  /** Platform/SDK namespaces whose stability is owned by the runtime, not the user's config. */
  private fun isPlatformType(typeFqName: String): Boolean =
    PLATFORM_PREFIXES.any { typeFqName.startsWith(it) }

  private val PLATFORM_PREFIXES = listOf(
    "kotlin.",
    "kotlinx.",
    "java.",
    "javax.",
    "android.",
    "androidx.",
  )

  private fun isCoveredByConfig(
    project: com.intellij.openapi.project.Project,
    typeFqName: String,
  ): Boolean {
    val patterns = StabilityProjectSettingsState.getInstance(project).getCustomStableTypesAsRegex()
      .ifEmpty { StabilitySettingsState.getInstance().getCustomStableTypesAsRegex() }
    return patterns.any { it.matches(typeFqName) }
  }

  // ── Remember-hoist applicability ─────────────────────────────────────────

  private fun buildRememberHoistFixes(
    function: KtNamedFunction,
    paramName: String,
    pointerManager: SmartPointerManager,
  ): List<DoctorFix> {
    val params = function.valueParameters.mapNotNull { it.name }
    val paramIndex = params.indexOf(paramName)
    if (paramIndex < 0) return emptyList()

    return runCatching {
      BlameAnalyzer.findCallers(function)
        .take(MAX_HOIST_CALL_SITES)
        .mapNotNull { (caller, callExpr) ->
          val argExpr = argumentFor(callExpr, paramName, paramIndex) ?: return@mapNotNull null
          val keys = rememberHoistApplicability(caller, argExpr) ?: return@mapNotNull null
          RememberHoistFix(
            callerName = caller.name ?: "caller",
            paramName = paramName,
            argumentPointer = pointerManager.createSmartPsiElementPointer(argExpr),
            originalExpressionText = argExpr.text,
            keys = keys,
          )
        }
    }.getOrDefault(emptyList())
  }

  private fun argumentFor(
    callExpr: KtCallExpression,
    paramName: String,
    paramIndex: Int,
  ): KtExpression? {
    val named = callExpr.valueArguments.find {
      it.getArgumentName()?.asName?.asString() == paramName
    }
    if (named != null) return named.getArgumentExpression()
    // Positional: only trust the index when no earlier named arguments shift positions.
    val positional = callExpr.valueArguments.getOrNull(paramIndex) ?: return null
    if (positional.isNamed()) return null
    return positional.getArgumentExpression()
  }

  /**
   * The conservative safety rules for wrapping [argExpr] in `remember { ... }`. Returns the
   * remember keys when ALL rules hold, or null when the transformation cannot be proven safe:
   *
   * 1. The argument is evaluated directly in composition — no lambda or local function between
   *    it and the caller's body (a `remember` inside a non-composable lambda won't compile, and
   *    inline-composable proof isn't worth the complexity).
   * 2. The expression is a genuine computation: not already remembered, not a bare reference,
   *    not a constant, not a lambda (lambda hoisting has different semantics).
   * 3. No composable call inside (illegal inside `remember`); unresolved callees reject.
   * 4. Every free reference resolves to a caller value parameter, an earlier local `val`,
   *    a top-level/object `val` without a custom getter, an enum entry, an object, or a
   *    non-composable function. Any `var`, custom getter, `this` usage, or unresolved
   *    reference rejects.
   * 5. Keys = the caller-parameter and local-val inputs, at most [MAX_HOIST_KEYS].
   */
  internal fun rememberHoistApplicability(
    caller: KtNamedFunction,
    argExpr: KtExpression,
  ): List<String>? {
    // Rule 1: directly in composition.
    var parent = argExpr.parent
    while (parent != null && parent != caller) {
      if (parent is KtLambdaExpression) return null
      if (parent is KtNamedFunction && parent != caller) return null
      parent = parent.parent
    }
    if (parent == null) return null

    // Rule 2: a computation worth remembering.
    if (argExpr is KtLambdaExpression) return null
    if (argExpr is KtConstantExpression) return null
    if (argExpr is KtStringTemplateExpression && argExpr.entries.isEmpty()) return null
    if (argExpr is KtNameReferenceExpression) return null
    val calleeName = (argExpr as? KtCallExpression)?.calleeExpression?.text
    if (calleeName in setOf("remember", "rememberSaveable", "derivedStateOf")) return null

    // Rule 3: no composable calls inside.
    val calls = argExpr.collectDescendantsOfType<KtCallExpression>() +
      listOfNotNull(argExpr as? KtCallExpression)
    for (call in calls) {
      val callee = (call.calleeExpression as? KtNameReferenceExpression)
        ?.let { runCatching { it.resolveMainReference() }.getOrNull() }
      when (callee) {
        is KtNamedFunction -> if (callee.isComposable()) return null
        is KtClass -> Unit // constructor call — fine
        null -> return null // unresolved — cannot prove safety
        else -> Unit
      }
    }

    // Rule 4 + 5: free references.
    if (argExpr.collectDescendantsOfType<KtThisExpression>().isNotEmpty()) return null
    val keys = linkedSetOf<String>()
    val references = argExpr.collectDescendantsOfType<KtNameReferenceExpression>()
    for (ref in references) {
      // Skip callee names of calls — rule 3 already vetted them.
      val parentCall = ref.parent as? KtCallExpression
      if (parentCall?.calleeExpression == ref) continue

      val resolved = runCatching { ref.resolveMainReference() }.getOrNull() ?: return null
      // Skip declarations nested inside the expression itself (e.g. lambda params).
      if (argExpr.isAncestor(resolved, strict = true)) continue

      when (resolved) {
        is KtParameter -> {
          // Caller value parameter (incl. constructor params of the caller's class is NOT ok —
          // require it to be one of the caller function's own parameters).
          if (resolved.ownerFunction == caller) {
            keys += ref.getReferencedName()
          } else {
            return null
          }
        }

        is KtProperty -> {
          if (resolved.isVar) return null
          if (resolved.getter != null) return null
          if (resolved.isLocal) {
            // Earlier local val in the same caller body.
            if (caller.isAncestor(resolved, strict = true) &&
              resolved.textOffset < argExpr.textOffset
            ) {
              keys += ref.getReferencedName()
            } else {
              return null
            }
          } else {
            // Top-level or object-level val without custom getter: stable global, not a key.
            Unit
          }
        }

        is KtEnumEntry, is KtObjectDeclaration -> Unit
        is KtNamedFunction -> if (resolved.isComposable()) return null
        is KtClass -> Unit
        else -> return null
      }
    }

    if (keys.size > MAX_HOIST_KEYS) return null
    return keys.toList()
  }
}
