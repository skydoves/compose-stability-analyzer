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

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Annotates a parameter's class with `@Immutable` (no mutable state) or `@Stable` (has vars,
 * promises change notification). This is a PROMISE to the compiler, not a verification — the
 * confirmation dialog carries that caveat.
 */
internal class AddImmutableAnnotationFix(
  private val className: String,
  private val classPointer: SmartPsiElementPointer<KtClass>,
  private val useStable: Boolean,
) : DoctorFix {

  private val annotationName: String = if (useStable) "Stable" else "Immutable"

  override val title: String = "Annotate $className with @$annotationName"

  override val previewText: String =
    "@$annotationName is a promise to the Compose compiler, not a verification — " +
      "make sure $className really ${if (useStable) "notifies composition of changes" else "never changes"}."

  override fun isAvailable(): Boolean = runReadAction {
    val ktClass = classPointer.element
    ktClass != null &&
      ktClass.isValid &&
      ktClass.containingFile?.isWritable == true &&
      ktClass.annotationEntries.none {
        it.shortName?.asString() == "Stable" || it.shortName?.asString() == "Immutable"
      }
  }

  override fun apply(project: Project) {
    val ktClass = runReadAction { classPointer.element } ?: return
    val file = ktClass.containingFile as? KtFile ?: return

    WriteCommandAction.runWriteCommandAction(project, title, null, {
      val factory = KtPsiFactory(project)
      val annotation = factory.createAnnotationEntry("@$annotationName")
      val modifierList = ktClass.modifierList
      if (modifierList != null) {
        modifierList.addBefore(annotation, modifierList.firstChild)
      } else {
        ktClass.addAnnotationEntry(annotation)
      }
      addImportIfMissing(factory, file, "androidx.compose.runtime.$annotationName")
    }, file)
  }
}

/** Adds an import for [fqName] unless the file already imports it. */
internal fun addImportIfMissing(factory: KtPsiFactory, file: KtFile, fqName: String) {
  val alreadyImported = file.importDirectives.any {
    it.importedFqName?.asString() == fqName
  }
  if (alreadyImported) return
  val importList = file.importList ?: return
  importList.add(factory.createImportDirective(ImportPath(FqName(fqName), false)))
}
