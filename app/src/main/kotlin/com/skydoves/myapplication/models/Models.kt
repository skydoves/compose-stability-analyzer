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
package com.skydoves.myapplication.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.skydoves.compose.stability.runtime.StableForAnalysis

/**
 * Immutable data class - should be stable.
 * All properties are val and the class is a data class.
 */
data class StableUser(
  val name: String,
  val age: Int,
)

/**
 * Mutable data class - should be unstable.
 * Has mutable (var) properties.
 */
data class UnstableUser(
  var name: String,
  var age: Int,
)

/**
 * Explicitly marked as @Immutable - should be stable.
 */
@Immutable
data class ImmutableData(
  val text: String,
  val number: Int,
)

class NormalClass(
  val asda: String,
  val qwea: Boolean,
)

class MyClass2(
  val asd: String,
  var asdasd: Int,

)

/**
 * Explicitly marked as @Stable - should be stable.
 * Even with var properties, the annotation overrides.
 */
@Stable
class StableClass(
  private var internalCounter: Int = 0,
) {
  fun getCount(): Int = internalCounter

  // The mutation is internal and doesn't affect equality
  internal fun increment() {
    internalCounter++
  }
}

sealed class NormalSealedClass {
  data class Normal(val names: List<String>) : NormalSealedClass()
}

@Immutable
sealed class StableSealedClass {
  data class Stable(val names: List<String>) : StableSealedClass()
}

/**
 * Class with mixed stability properties.
 * Should be unstable due to mutableList.
 */
data class MixedStabilityClass(
  val id: Int, // Stable
  val name: String, // Stable
  val tags: MutableList<String>, // Unstable
)

/**
 * Class with immutable collection - should be stable.
 */
data class ImmutableCollectionClass(
  val id: Int,
  val items: List<String>, // Immutable list
)

/**
 * Sealed class hierarchy - stability depends on implementation.
 */
sealed class UserState {
  object Loading : UserState()
  data class Success(val user: StableUser) : UserState()
  data class Error(val message: String) : UserState()
}

/**
 * Custom class marked with our annotation.
 * The analyzer should recognize this as stable.
 */
@StableForAnalysis
class CustomStableClass(
  private val data: Any,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CustomStableClass) return false
    return data == other.data
  }

  override fun hashCode(): Int = data.hashCode()
}

/**
 * Generic container - stability depends on type parameter.
 */
data class Container<T>(
  val value: T,
)

/**
 * Interface - stability cannot be determined.
 */
interface UserRepository {
  suspend fun getUser(id: Int): StableUser?
}

/**
 * Implementation of interface.
 */
class UserRepositoryImpl : UserRepository {
  override suspend fun getUser(id: Int): StableUser? {
    // Mock implementation
    return StableUser("User $id", 20 + id)
  }
}
