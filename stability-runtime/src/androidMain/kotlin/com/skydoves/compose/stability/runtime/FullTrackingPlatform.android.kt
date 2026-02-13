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
package com.skydoves.compose.stability.runtime

/**
 * Android implementation: uses reflection to extract values from state holders.
 */
internal actual fun extractComparableValuePlatform(obj: Any): Any? {
  return try {
    val cls = obj::class.java
    val valueGetter = cls.methods.firstOrNull {
      it.name == "getValue" && it.parameterCount == 0
    } ?: cls.methods.firstOrNull {
      it.name == "getIntValue" && it.parameterCount == 0
    } ?: cls.methods.firstOrNull {
      it.name == "getLongValue" && it.parameterCount == 0
    } ?: cls.methods.firstOrNull {
      it.name == "getFloatValue" && it.parameterCount == 0
    } ?: cls.methods.firstOrNull {
      it.name == "getDoubleValue" && it.parameterCount == 0
    }
    valueGetter?.invoke(obj)
  } catch (_: Throwable) {
    null
  }
}

/**
 * Android implementation: uses reflection to extract values from state holders.
 */
internal actual fun snapshotSlotValuePlatform(value: Any): Any? {
  val cls = value::class.java
  val className = cls.simpleName ?: ""

  if (className.contains("State") || className.contains("ValueHolder")) {
    try {
      val getters =
        listOf("getValue", "getIntValue", "getLongValue", "getFloatValue", "getDoubleValue")
      for (getterName in getters) {
        val getter = cls.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
        if (getter != null) {
          val extracted = getter.invoke(value)
          if (extracted != null && extracted !== value) {
            return "[$className=$extracted]"
          }
        }
      }
    } catch (_: Throwable) {
      // Fall through
    }
  }

  // For primitives, strings, and simple types, return as-is
  return when (value) {
    is Number, is Boolean, is Char, is String, is Enum<*> -> value
    // For complex objects, return their type and identity hash for reference tracking
    else -> "[$className@${value.hashCode().toString(16)}]"
  }
}
