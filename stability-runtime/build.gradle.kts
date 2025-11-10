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
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.nexus.plugin)
}

kotlin {
  jvm()
  androidTarget {
    publishLibraryVariants("release")
  }

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  macosX64()
  macosArm64()

  wasmJs {
    browser()
  }

  @Suppress("OPT_IN_USAGE")
  applyHierarchyTemplate {
    common {
      withAndroidTarget()
      withJvm()
      withWasmJs()
      group("skia") {
        group("darwin") {
          group("apple") {
            group("ios") {
              withIosX64()
              withIosArm64()
              withIosSimulatorArm64()
            }
            group("macos") {
              withMacosX64()
              withMacosArm64()
            }
          }
        }
      }
    }
  }

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }

  explicitApi()
}

dependencies {
  lintPublish(project(":stability-lint")) {
    isTransitive = false
  }
}

android {
  namespace = "com.skydoves.compose.stability.runtime"
  compileSdk = 36

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}