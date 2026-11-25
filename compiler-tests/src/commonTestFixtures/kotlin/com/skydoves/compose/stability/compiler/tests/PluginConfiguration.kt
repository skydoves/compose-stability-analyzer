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

import com.skydoves.compose.stability.compiler.StabilityAnalyzerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder

/**
 * Configures the Stability Analyzer plugin for testing.
 *
 * This extension function is called from base test classes to set up
 * the plugin with appropriate configurators and services.
 */
fun TestConfigurationBuilder.configurePlugin() {
  // Register the compiler plugin
  configurePlugin(StabilityAnalyzerPluginRegistrar())
  useConfigurators(::StabilityTestConfigurator)
}
