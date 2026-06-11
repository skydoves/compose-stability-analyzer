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
package com.skydoves.compose.stability.idea.doctor

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.skydoves.compose.stability.idea.doctor.fixes.AddImmutableAnnotationFix
import com.skydoves.compose.stability.idea.doctor.fixes.ChangeVarToValFix
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Transform tests for the Doctor's PSI-mutating fixes (var→val and @Immutable/@Stable).
 */
class DoctorFixesTest : BasePlatformTestCase() {

  override fun tearDown() {
    try {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    } finally {
      super.tearDown()
    }
  }

  private fun ktClass(code: String, name: String): Pair<KtFile, KtClass> {
    val file = myFixture.configureByText("Test.kt", code) as KtFile
    return file to file.collectDescendantsOfType<KtClass>().first { it.name == name }
  }

  private fun varsOf(ktClass: KtClass): List<KtDeclaration> {
    val ctorVars = ktClass.primaryConstructorParameters.filter { it.hasValOrVar() && it.isMutable }
    val bodyVars = ktClass.getProperties().filter { it.isVar }
    return ctorVars + bodyVars
  }

  fun testChangeVarToVal_constructorAndBodyProperties() {
    val (file, productClass) = ktClass(
      """
      data class Product(var name: String, val id: Int) {
        var cached: String = ""
      }
      """.trimIndent(),
      "Product",
    )
    val pointers = varsOf(productClass).map {
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
    }
    val fix = ChangeVarToValFix("Product", pointers)
    assertTrue(fix.isAvailable())

    fix.apply(project)

    val text = file.text
    assertTrue(text.contains("data class Product(val name: String, val id: Int)"))
    assertTrue(text.contains("val cached: String"))
    assertFalse(text.contains("var "))
  }

  fun testChangeVarToVal_abortsOnWriteUsage() {
    TestDialogManager.setTestDialog(TestDialog.OK)
    val (file, productClass) = ktClass(
      """
      data class Product(var name: String)

      fun rename(p: Product) {
        p.name = "new"
      }
      """.trimIndent(),
      "Product",
    )
    val pointers = varsOf(productClass).map {
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
    }
    val fix = ChangeVarToValFix("Product", pointers)

    fix.apply(project)

    // Write usage exists -> the fix must NOT touch the code.
    assertTrue(file.text.contains("var name: String"))
  }

  fun testAddImmutable_insertsAnnotationAndImport() {
    val (file, productClass) = ktClass(
      """
      data class Product(val name: String)
      """.trimIndent(),
      "Product",
    )
    val pointer = SmartPointerManager.getInstance(project)
      .createSmartPsiElementPointer(productClass)
    val fix = AddImmutableAnnotationFix("Product", pointer, useStable = false)
    assertTrue(fix.isAvailable())

    fix.apply(project)

    val text = file.text
    assertTrue(text.contains("@Immutable"))
    assertTrue(text.contains("import androidx.compose.runtime.Immutable"))
  }

  fun testAddStable_whenClassKeepsVars() {
    val (file, productClass) = ktClass(
      """
      class Counter(var value: Int)
      """.trimIndent(),
      "Counter",
    )
    val pointer = SmartPointerManager.getInstance(project)
      .createSmartPsiElementPointer(productClass)
    val fix = AddImmutableAnnotationFix("Counter", pointer, useStable = true)

    fix.apply(project)

    assertTrue(file.text.contains("@Stable"))
    assertTrue(file.text.contains("import androidx.compose.runtime.Stable"))
  }

  fun testAddImmutable_unavailableWhenAlreadyAnnotated() {
    val (_, productClass) = ktClass(
      """
      import androidx.compose.runtime.Immutable
      @Immutable
      data class Product(val name: String)
      """.trimIndent(),
      "Product",
    )
    val pointer = SmartPointerManager.getInstance(project)
      .createSmartPsiElementPointer(productClass)
    val fix = AddImmutableAnnotationFix("Product", pointer, useStable = false)
    assertFalse(fix.isAvailable())
  }
}
