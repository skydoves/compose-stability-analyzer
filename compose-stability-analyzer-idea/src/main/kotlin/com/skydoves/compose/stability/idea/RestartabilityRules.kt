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

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * The purely structural half of the restartability rules, shared by the PSI and K2 analyzers so the
 * two cannot drift apart. Return-type handling stays on each path, because only K2 can resolve an
 * inferred expression-body type.
 *
 * Mirrors `AbstractComposeLowering.shouldBeRestartable()` in the Compose compiler and
 * `StabilityAnalyzerTransformer.isRestartable` in our compiler plugin.
 *
 * Note `@ReadOnlyComposable` is deliberately not listed: the Compose compiler does not consult it
 * when deciding restartability, so a `Unit`-returning read-only composable is restartable. The
 * familiar read-only composables are non-restartable because they return a value.
 */
internal object RestartabilityRules {

  /**
   * Whether [function] is non-restartable for a reason that does not depend on its return type.
   */
  fun isStructurallyNonRestartable(function: KtNamedFunction): Boolean {
    if (function.hasAnnotation(StabilityConstants.Strings.NON_RESTARTABLE_COMPOSABLE)) return true
    if (function.hasAnnotation(StabilityConstants.Strings.EXPLICIT_GROUPS_COMPOSABLE)) return true
    if (function.hasModifier(KtTokens.INLINE_KEYWORD)) return true
    // Abstract declarations and other bodiless functions get no restart group.
    if (!function.hasBody()) return true
    if (function.isLocal) return true
    // Restart logic makes a virtual call, so an open member of a non-final class is never
    // restartable (b/329477544). A final member of an open class still is.
    if (function.isEffectivelyOpen() && !function.isDeclaredInFinalClass()) return true
    return false
  }

  /**
   * Kotlin makes `override` and interface members with a body open unless they are marked `final`,
   * so the `open` keyword alone is not enough to detect a virtual composable.
   */
  private fun KtNamedFunction.isEffectivelyOpen(): Boolean {
    if (hasModifier(KtTokens.FINAL_KEYWORD)) return false
    if (hasModifier(KtTokens.OPEN_KEYWORD)) return true
    if (hasModifier(KtTokens.ABSTRACT_KEYWORD)) return true
    if (hasModifier(KtTokens.OVERRIDE_KEYWORD)) return true
    return (containingClassOrObject as? KtClass)?.isInterface() == true
  }

  /** A top-level function has no containing class and is therefore never virtual. */
  private fun KtNamedFunction.isDeclaredInFinalClass(): Boolean {
    val containing = containingClassOrObject ?: return true
    val klass = containing as? KtClass ?: return true // object declarations are final
    if (klass.isInterface()) return false
    return !klass.hasModifier(KtTokens.OPEN_KEYWORD) &&
      !klass.hasModifier(KtTokens.ABSTRACT_KEYWORD) &&
      !klass.hasModifier(KtTokens.SEALED_KEYWORD)
  }
}
