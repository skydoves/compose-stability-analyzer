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

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import java.io.File

/**
 * Environment configurator that adds Stability and Compose runtime to the compilation classpath.
 *
 * This makes runtime classes (like @TraceRecomposition, @Composable, etc.) available
 * during compilation of test data files.
 */
class StabilityRuntimeEnvironmentConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {

  private val stabilityRuntimeClasspath: List<File> by lazy {
    System.getProperty("stabilityRuntime.classpath")
      ?.split(File.pathSeparator)
      ?.map { File(it) }
      ?: emptyList()
  }

  private val composeRuntimeClasspath: List<File> by lazy {
    System.getProperty("composeRuntime.classpath")
      ?.split(File.pathSeparator)
      ?.map { File(it) }
      ?: emptyList()
  }

  override fun configureCompilerConfiguration(
    configuration: CompilerConfiguration,
    module: TestModule,
  ) {
    // Add stability-runtime to classpath (for @TraceRecomposition, etc.)
    for (file in stabilityRuntimeClasspath) {
      configuration.addJvmClasspathRoot(file)
    }

    // Add compose-runtime to classpath (for @Composable, @Stable, etc.)
    for (file in composeRuntimeClasspath) {
      configuration.addJvmClasspathRoot(file)
    }
  }
}
