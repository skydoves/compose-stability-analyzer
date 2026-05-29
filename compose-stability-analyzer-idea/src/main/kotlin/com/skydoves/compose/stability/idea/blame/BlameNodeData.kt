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
package com.skydoves.compose.stability.idea.blame

/** Where a parameter's argument value came from at a specific call site (best-effort, static). */
internal data class ArgumentOrigin(
  val paramName: String,
  val expressionText: String,
  val origin: String,
)

/**
 * A node in the upstream "blame" tree: a composable that (transitively) calls the target. The root
 * is the target itself; each child is a caller, annotated with the arguments it passes downstream.
 */
internal data class BlameNode(
  val name: String,
  val fqName: String,
  val filePath: String,
  val line: Int,
  val depth: Int,
  /** Arguments this composable passes to the callee toward the target (empty for the root). */
  val passedArguments: List<ArgumentOrigin>,
  val callSiteFilePath: String?,
  val callSiteLine: Int,
  val children: List<BlameNode>,
  val isTruncated: Boolean = false,
  val truncationReason: String? = null,
)

internal data class BlameResult(
  val root: BlameNode,
  val callerCount: Int,
  val maxDepth: Int,
)

/** Sealed userObject types for the Blame JTree. */
internal sealed class BlameTreeNodeData {
  data class Summary(
    val composableName: String,
    val callerCount: Int,
    val maxDepth: Int,
  ) : BlameTreeNodeData()

  data class Caller(val node: BlameNode) : BlameTreeNodeData()

  data class Argument(val origin: ArgumentOrigin) : BlameTreeNodeData()

  data class Truncated(val reason: String) : BlameTreeNodeData()

  data class EmptyMessage(val message: String) : BlameTreeNodeData()
}
