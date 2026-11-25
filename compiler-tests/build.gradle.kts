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
  alias(libs.plugins.devkit.compiler.library)
}

kotlin {
  jvmToolchain(17)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  sourceSets {
    commonTestFixtures.dependencies {
      implementation(project(":stability-compiler"))
    }
  }
}

pluginDevKit {
  testDataLibraries {
    jvm(project(":stability-runtime"))

    val composeVersion = "1.8.1" // Match with androidx.compose.runtime version
    jvm("androidx.compose.runtime:runtime:$composeVersion")
  }
  generateTestsEnabled = true
  generateTestsClass = "com.skydoves.compose.stability.compiler.tests.GenerateTestsKt"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = "17"
  targetCompatibility = "17"
}

// Configure Spotless to exclude test data files
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  kotlin {
    targetExclude("src/commonTest/data/**/*.kt")
  }
}
