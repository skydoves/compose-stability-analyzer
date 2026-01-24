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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    `kotlin-dsl`
    alias(libs.plugins.nexus.plugin)
}

group = project.property("GROUP") as String
version = project.property("VERSION_NAME") as String

kotlin {
  explicitApi()
}

dependencies {
  compileOnly(kotlin("gradle-plugin", version = libs.versions.kotlin.get()))
  implementation(libs.android.gradleApi)

  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit"))
}

tasks.withType<KotlinCompile> {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
  }
}

gradlePlugin {
  plugins {
    create("stabilityAnalyzer") {
      id = "com.github.skydoves.compose.stability.analyzer"
      displayName = "Compose Stability Analyzer"
      description = "Analyzes stability of Compose functions during compilation"
      implementationClass = "com.skydoves.compose.stability.gradle.StabilityAnalyzerGradlePlugin"
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}
