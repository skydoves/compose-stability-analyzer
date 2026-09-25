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
package com.skydoves.compose.stability.gradle

import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards issue #217: resolving the stability tasks' dependencies must not realize unrelated tasks.
 *
 * `TaskCollection.matching` takes a `Spec<Task>` over a *realized* task, so filtering the whole
 * container by name realized every task in the project. A third-party task whose creation action
 * throws then failed the build from our `dependsOn`, naming our task as the victim: AGP
 * 9.5.0-alpha06's `generate<Variant>ComposePreviewRunfiles` calls `error()` when unit tests are
 * disabled, which made whole modules unaddressable.
 */
class TaskRealizationTest {

  @Test
  fun resolvingStabilityTaskDependenciesDoesNotRealizeUnrelatedTasks() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("org.jetbrains.kotlin.jvm")
    project.plugins.apply("com.github.skydoves.compose.stability.analyzer")

    // Stands in for the AGP task whose creation action throws.
    var realized = false
    project.tasks.register("explodingTask") { realized = true }

    // configureTaskDependencies runs in afterEvaluate, which ProjectBuilder does not trigger by
    // itself. Without this the dependency set is empty and the assertion below passes vacuously.
    (project as ProjectInternal).evaluate()

    // Force the same query Gradle makes when it resolves dependsOn.
    val dump = project.tasks.named("stabilityDump").get()
    dump.taskDependencies.getDependencies(dump)

    assertTrue(
      !realized,
      "resolving stability task dependencies realized an unrelated task, which fails the build " +
        "for any third-party task whose creation action throws (issue #217)",
    )
  }

  @Test
  fun kotlinCompileTasksAreStillDependencies() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("org.jetbrains.kotlin.jvm")
    project.plugins.apply("com.github.skydoves.compose.stability.analyzer")

    (project as ProjectInternal).evaluate()

    val dump = project.tasks.named("stabilityDump").get()
    val names: Set<String> = dump.taskDependencies.getDependencies(dump).map(Task::getName).toSet()

    // Narrowing by type must not cost us the dependency the whole feature rests on.
    assertTrue(
      names.contains("compileKotlin"),
      "stabilityDump must still depend on Kotlin compilation, got: $names",
    )
    assertEquals(
      emptySet(),
      names.filterNot { it.startsWith("compile") }.toSet(),
      "only compile tasks should be dependencies, got: $names",
    )
  }
}
