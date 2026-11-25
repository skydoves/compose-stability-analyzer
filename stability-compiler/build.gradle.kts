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

plugins {
  id("compose-stability.conventions")
  alias(libs.plugins.devkit.functional.test.publishing)
  alias(libs.plugins.devkit.compiler.plugin)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.nexus.plugin)
}

pluginDevKit {
  pluginPackage = "com.skydoves.compose.stability.compiler"
  componentRegistrar = "com.skydoves.compose.stability.compiler.StabilityAnalyzerPluginRegistrar"
  commandLineProcessor = "com.skydoves.compose.stability.compiler.StabilityAnalyzerCommandLineProcessor"

  generateTestsEnabled = false
}

kotlin {
  explicitApi()
  sourceSets {
    commonMain.dependencies {
      implementation(project(":stability-runtime"))
      implementation(libs.kotlinx.serialization.json)
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}
