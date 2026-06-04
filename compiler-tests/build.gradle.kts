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
  kotlin("jvm")
}

kotlin {
  jvmToolchain(17)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

val testCompilerVersion = libs.versions.kotlin.get()

val stabilityRuntimeClasspath: Configuration by configurations.creating {
  isTransitive = false
}

val composeRuntimeClasspath: Configuration by configurations.creating {}

dependencies {
  // Compiler test framework
  val compilerTestFramework = "org.jetbrains.kotlin:kotlin-compiler-internal-test-framework:$testCompilerVersion"
  testImplementation(compilerTestFramework)
  testImplementation("org.jetbrains.kotlin:kotlin-compiler:$testCompilerVersion")

  // Our compiler plugin and runtime
  testImplementation(project(":stability-compiler"))
  testImplementation(project(":stability-runtime"))

  // Test framework
  testImplementation(kotlin("test-junit5"))
  testImplementation(kotlin("test"))

  // Required runtime dependencies
  testRuntimeOnly(kotlin("reflect"))
  testRuntimeOnly(kotlin("script-runtime"))
  testRuntimeOnly("org.jetbrains.kotlin:kotlin-annotations-jvm:$testCompilerVersion")

  // Compose runtime for testing
  stabilityRuntimeClasspath(project(":stability-runtime"))
  val composeVersion = "1.8.1" // Match with androidx.compose.runtime version
  composeRuntimeClasspath("androidx.compose.runtime:runtime:$composeVersion")
}

val generateTests =
  tasks.register<JavaExec>("generateTests") {
    inputs
      .dir(layout.projectDirectory.dir("src/test/data"))
      .withPropertyName("testData")
      .withPathSensitivity(PathSensitivity.RELATIVE)

    outputs
      .dir(layout.projectDirectory.dir("src/test/java"))
      .withPropertyName("generatedTests")

    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.skydoves.compose.stability.compiler.tests.GenerateTestsKt")
    workingDir = rootDir

    // Larger heap size for test generation
    minHeapSize = "128m"
    maxHeapSize = "1g"

    // Larger stack size
    jvmArgs("-Xss1m")
  }

tasks.withType<Test> {
  dependsOn(stabilityRuntimeClasspath)
  dependsOn(composeRuntimeClasspath)

  inputs
    .dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)

  workingDir = rootDir

  useJUnitPlatform()

  // Set library properties for test framework
  setLibraryProperty("kotlin.minimal.stdlib.path", "kotlin-stdlib")
  setLibraryProperty("kotlin.full.stdlib.path", "kotlin-stdlib-jdk8")
  setLibraryProperty("kotlin.reflect.jar.path", "kotlin-reflect")
  setLibraryProperty("kotlin.test.jar.path", "kotlin-test")
  setLibraryProperty("kotlin.script.runtime.path", "kotlin-script-runtime")
  setLibraryProperty("kotlin.annotations.path", "kotlin-annotations-jvm")

  // Set runtime classpaths
  systemProperty("stabilityRuntime.classpath", stabilityRuntimeClasspath.asPath)
  systemProperty("composeRuntime.classpath", composeRuntimeClasspath.asPath)

  // Required properties for test framework
  systemProperty("idea.ignore.disabled.plugins", "true")
  systemProperty("idea.home.path", rootDir.absolutePath)

  // Regenerate golden FIR/IR dump files: ./gradlew :compiler-tests:test -Pupdate.test.data
  if (project.hasProperty("update.test.data")) {
    systemProperty("kotlin.test.update.test.data", "true")
  }

  // Larger memory for tests
  minHeapSize = "512m"
  maxHeapSize = "2g"
  jvmArgs("-Xss2m")
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

fun Test.setLibraryProperty(propName: String, jarName: String) {
  val path =
    project.configurations.testRuntimeClasspath
      .get()
      .files
      .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
      ?.absolutePath ?: return
  systemProperty(propName, path)
}

// Configure Spotless to exclude test data files
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  kotlin {
    targetExclude("src/test/data/**/*.kt")
  }
}
