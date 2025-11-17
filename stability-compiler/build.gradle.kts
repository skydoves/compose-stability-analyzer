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

import org.gradle.jvm.tasks.Jar

plugins {
  kotlin("jvm")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.shadow)
  alias(libs.plugins.nexus.plugin)
}

kotlin {
  explicitApi()

  compilerOptions {
    freeCompilerArgs.add("-Xcontext-parameters")
  }
}

dependencies {
  compileOnly(libs.kotlin.stdlib)
  compileOnly(libs.kotlin.compiler.embeddable)
  implementation(project(":stability-runtime"))
  implementation(libs.kotlinx.serialization.json)

  testImplementation(kotlin("test"))
  testImplementation(kotlin("test-junit"))
  testImplementation(libs.kotlin.compiler.embeddable)

  testRuntimeOnly(kotlin("compiler"))
  testImplementation(kotlin("reflect"))
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.runtime)
  testImplementation(libs.junit)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.shadowJar {
  archiveClassifier.set("all")
  configurations = listOf(project.configurations.runtimeClasspath.get())

  // Don't relocate - K/Native compiler needs original package names
  // for example, relocate("kotlinx.serialization", "...")

  exclude("META-INF/maven/**")
  exclude("META-INF/*.SF")
  exclude("META-INF/*.DSA")
  exclude("META-INF/*.RSA")
}

// Make jar task produce the shadowJar content
tasks.named<Jar>("jar") {
  dependsOn(tasks.shadowJar)
  doLast {
    val shadowTask = tasks.shadowJar.get()
    val shadowJarFile = shadowTask.archiveFile.get().asFile
    val jarFile = archiveFile.get().asFile

    if (shadowJarFile.exists()) {
      shadowJarFile.copyTo(jarFile, overwrite = true)
      logger.lifecycle("Replaced ${jarFile.name} with shadowJar content")
    }
  }
}