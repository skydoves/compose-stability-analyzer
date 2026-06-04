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
package com.skydoves.compose.stability.compiler.tests

import org.jetbrains.kotlin.platform.wasm.WasmTarget
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import java.io.File

/**
 * Provides paths to Kotlin standard libraries by searching the test runtime classpath.
 *
 * This is necessary because the compiler test framework needs to know where to find
 * the standard library JARs (kotlin-stdlib, kotlin-reflect, etc.) when compiling test files.
 */
object ClasspathBasedStandardLibrariesPathProvider : KotlinStandardLibrariesPathProvider {

  private val jars =
    System.getProperty("java.class.path")
      .split(File.pathSeparator)
      .dropLastWhile(String::isEmpty)
      .map(::File)
      .associateBy { file ->
        // Extract the artifact name from filenames like "kotlin-stdlib-2.2.21.jar"
        // or "kotlin-stdlib-jdk8-2.2.21.jar"
        val name = file.name
        when {
          name.matches("""kotlin-stdlib-jdk8-[\d.]+\.jar""".toRegex()) -> "kotlin-stdlib-jdk8"
          name.matches("""kotlin-stdlib-jdk7-[\d.]+\.jar""".toRegex()) -> "kotlin-stdlib-jdk7"
          name.matches("""kotlin-stdlib-[\d.]+\.jar""".toRegex()) -> "kotlin-stdlib"
          name.matches("""kotlin-reflect-[\d.]+\.jar""".toRegex()) -> "kotlin-reflect"
          name.matches("""kotlin-test-[\d.]+\.jar""".toRegex()) -> "kotlin-test"
          name.matches("""kotlin-script-runtime-[\d.]+\.jar""".toRegex()) -> "kotlin-script-runtime"
          name.matches(
            """kotlin-annotations-jvm-[\d.]+\.jar""".toRegex(),
          ) -> "kotlin-annotations-jvm"
          else -> name
        }
      }

  private fun getFile(name: String): File = jars[name]
    ?: error(
      "Jar $name not found in classpath. Available jars:\n" +
        jars.keys.sorted().joinToString("\n"),
    )

  override fun runtimeJarForTests(): File = getFile("kotlin-stdlib")

  override fun runtimeJarForTestsWithJdk8(): File = getFile("kotlin-stdlib-jdk8")

  override fun minimalRuntimeJarForTests(): File = getFile("kotlin-stdlib")

  override fun reflectJarForTests(): File = getFile("kotlin-reflect")

  override fun kotlinTestJarForTests(): File = getFile("kotlin-test")

  override fun scriptRuntimeJarForTests(): File = getFile("kotlin-script-runtime")

  override fun jvmAnnotationsForTests(): File = getFile("kotlin-annotations-jvm")

  override fun getAnnotationsJar(): File = getFile("kotlin-annotations-jvm")

  override fun fullJsStdlib(): File = getFile("kotlin-stdlib-js")

  override fun defaultJsStdlib(): File = getFile("kotlin-stdlib-js")

  override fun kotlinTestJsKLib(): File = getFile("kotlin-test-js")

  override fun fullWasmStdlib(target: WasmTarget): File = TODO("Not needed for JVM-only tests")

  override fun kotlinTestWasmKLib(target: WasmTarget): File = TODO("Not needed for JVM-only tests")

  override fun scriptingPluginFilesForTests(): Collection<File> {
    TODO("Not needed for JVM-only tests")
  }

  override fun commonStdlibForTests(): File = getFile("kotlin-stdlib")

  override fun webStdlibForTests(): File = getFile("kotlin-stdlib")
}
