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
  kotlin("jvm")
  alias(libs.plugins.nexus.plugin)
}

kotlin {
  explicitApi()
}

configurations {
  // Ensure only the lint module JAR is published, not transitive dependencies
  compileOnly {
    isTransitive = false
  }
}

dependencies {
  // Android Lint API
  compileOnly(libs.lint.api)
  compileOnly(libs.lint.checks)

  // Test dependencies
  testImplementation(libs.lint)
  testImplementation(libs.lint.tests)
  testImplementation(libs.junit)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

tasks.jar {
  manifest {
    attributes("Lint-Registry-v2" to "com.skydoves.compose.stability.lint.StabilityIssueRegistry")
  }
}
