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

import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin

/**
 * Compatibility wrapper for [IrDeclarationOrigin] constants.
 *
 * In Kotlin 2.3.20, the types of origin constants like [IrDeclarationOrigin.DEFINED] changed
 * from `IrDeclarationOriginImpl` to `IrDeclarationOrigin`, causing [NoSuchMethodError] when
 * compiled against an older Kotlin version. This object uses reflection-based lookups to
 * access these constants safely across Kotlin versions.
 */
internal object OriginCompat {

  private fun getConstant(name: String): Lazy<IrDeclarationOrigin> = lazy {
    IrDeclarationOrigin.Companion::class.java
      .getDeclaredMethod("get$name")
      .invoke(IrDeclarationOrigin.Companion) as IrDeclarationOrigin
  }

  val DEFINED: IrDeclarationOrigin by getConstant("DEFINED")
  val IR_EXTERNAL_DECLARATION_STUB: IrDeclarationOrigin by getConstant(
    "IR_EXTERNAL_DECLARATION_STUB",
  )
  val IR_EXTERNAL_JAVA_DECLARATION_STUB: IrDeclarationOrigin by getConstant(
    "IR_EXTERNAL_JAVA_DECLARATION_STUB",
  )
}
