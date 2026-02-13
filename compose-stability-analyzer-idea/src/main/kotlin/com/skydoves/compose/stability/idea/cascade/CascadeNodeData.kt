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
package com.skydoves.compose.stability.idea.cascade

import com.skydoves.compose.stability.runtime.ComposableStabilityInfo

/**
 * Represents a node in the recomposition cascade tree.
 */
public data class CascadeNode(
  val stabilityInfo: ComposableStabilityInfo,
  val filePath: String,
  val line: Int,
  val depth: Int,
  val children: List<CascadeNode>,
  val isTruncated: Boolean = false,
  val truncationReason: String? = null,
)

/**
 * Summary statistics for a cascade analysis.
 */
public data class CascadeSummary(
  val totalCount: Int,
  val skippableCount: Int,
  val unskippableCount: Int,
  val maxDepth: Int,
  val hasTruncatedBranches: Boolean,
)

/**
 * Complete result of a cascade analysis.
 */
public data class CascadeResult(
  val root: CascadeNode,
  val summary: CascadeSummary,
)

/**
 * Sealed class representing different types of nodes in the cascade tree.
 * Used as userObject for JTree DefaultMutableTreeNode.
 */
public sealed class CascadeTreeNodeData {

  /**
   * Summary header node showing blast radius counts.
   */
  public data class Summary(
    val composableName: String,
    val summary: CascadeSummary,
  ) : CascadeTreeNodeData()

  /**
   * Composable function node in the cascade tree.
   */
  public data class Composable(
    val node: CascadeNode,
  ) : CascadeTreeNodeData()

  /**
   * Truncated branch node (cycle or depth limit reached).
   */
  public data class Truncated(
    val reason: String,
  ) : CascadeTreeNodeData()

  /**
   * Empty state message node.
   */
  public data class EmptyMessage(
    val message: String,
  ) : CascadeTreeNodeData()
}
