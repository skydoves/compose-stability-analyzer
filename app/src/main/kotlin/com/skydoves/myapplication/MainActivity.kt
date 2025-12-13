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
package com.skydoves.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.skydoves.myapplication.model.ThirdPartyModel
import com.skydoves.myapplication.model.ThirdPartyModelStable
import com.skydoves.myapplication.models.ImmutableData
import com.skydoves.myapplication.models.MyClass2
import com.skydoves.myapplication.models.NormalClass
import com.skydoves.myapplication.models.NormalSealedClass
import com.skydoves.myapplication.models.StableSealedClass
import com.skydoves.myapplication.models.StableUser
import com.skydoves.myapplication.models.UnstableUser
import kotlinx.collections.immutable.ImmutableList

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          RecompositionTrackingExample()
        }
      }
    }
  }
}

private val String.Companion.ComposableValue
  @Composable
  get() = "ComposableValue"

data class UiResult<T>(
  val data: T,
  val isPending: Boolean,
)

@Composable
fun MyComposable(
  uiResult: UiResult<Unit>,
  uiResult2: UiResult<UnstableUser>,
  uiResult4: UiResult<List<String>>,
) {
}

/**
 * Main screen composable.
 * This should be skippable as it has no parameters.
 */
@Composable
fun MainScreen() {
  var counter by remember { mutableStateOf(0) }
  val stableUser = remember { StableUser("John Doe", 30) }
  val unstableUser = remember { UnstableUser("Jane Doe", 25) }
  val immutableData = remember { ImmutableData("Test", 42) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Test different stability scenarios
    CounterDisplay(MainViewModel())

    Spacer(modifier = Modifier.height(16.dp))

    StableUserCard(stableUser)

    Spacer(modifier = Modifier.height(16.dp))

    UnstableUserCard(unstableUser)

    Spacer(modifier = Modifier.height(16.dp))

    ImmutableDataDisplay(immutableData)

    Spacer(modifier = Modifier.height(16.dp))

    MutableListDisplay(mutableListOf("Item 1", "Item 2"))

    Spacer(modifier = Modifier.height(32.dp))

    Button(onClick = { counter++ }) {
      Text("Increment Counter")
    }
  }
}

/**
 * Display counter value.
 * Should be skippable - Int is a stable primitive.
 */
@Composable
fun CounterDisplay(count: MainViewModel) {
  Card {
    Text(
      text = "Count: $count",
      modifier = Modifier.padding(16.dp),
    )
  }
}

/**
 * Display stable user.
 * Should be skippable - StableUser is immutable.
 */
@Composable
fun StableUserCard(user: StableUser) {
  Card {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Stable User")
      Text("Name: ${user.name}")
      Text("Age: ${user.age}")
    }
  }
}

@Composable
fun ThirdPartyCard(
  thirdPartyModel: ThirdPartyModel,
  thirdPartyModelStable: ThirdPartyModelStable,
) {
  Card {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Stable User")
      Text("Name: ${thirdPartyModel.name}")
      Text("Age: ${thirdPartyModelStable.count}")
    }
  }
}

@Composable
fun Card(
  modifier: Modifier = Modifier,
  shape: Shape = CardDefaults.shape,
  colors: CardColors = CardDefaults.cardColors(),
  elevation: CardElevation = CardDefaults.cardElevation(),
  func: @Composable () -> UnstableUser,
  func2: suspend () -> UnstableUser,
  func3: suspend () -> StableUser,
  content: @Composable ColumnScope.() -> Unit,
) {
}

@Composable
fun Icon(
  title: String,
  painter: Painter,
  users: List<StableUser>,
  normalSealedClass: NormalSealedClass.Normal,
  stableSealedClass: StableSealedClass.Stable,
  elevation: CardElevation = CardDefaults.cardElevation(),
  unstableUser: UnstableUser,
) {
  title
  painter
  users
  elevation
  unstableUser
}

/**
 * Display unstable user.
 * Should NOT be skippable - UnstableUser has mutable properties.
 */
@Composable
fun UnstableUserCard(user: UnstableUser) {
  Card {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Unstable User")
      Text("Name: ${user.name}")
      Text("Age: ${user.age}")
    }
  }
}

/**
 * Display immutable data.
 * Should be skippable - ImmutableData is marked with @Immutable.
 */
@Composable
fun ImmutableDataDisplay(data: ImmutableData) {
  Card {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Immutable Data")
      Text("Text: ${data.text}")
      Text("Number: ${data.number}")
    }
  }
}

@Composable
fun UnstableUser.Test(stableUser2: StableUser) {
}

@Composable
fun Test(
  myClass2: MyClass2,
  normalClass: NormalClass,
  immutableList: ImmutableList<String>,
) {
}

@Composable
fun Test2(myClass2: List<String>) {
}

@Composable
fun Test3(myClass2: StringBuilder) {
}

@Composable
fun Test4(count: ViewModel) {
}

@Composable
fun Test6(count: Set<String>) {
}

@Composable
fun Test7(onClick: () -> Unit) {
}

@Composable
fun Test8(items: ImmutableList<String>) {
}

@Composable
fun MutableListDisplay(items: MutableList<String>) {
  Card {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Mutable List")
      items.forEach { item ->
        Text("• $item")
      }
    }
  }
}

@Composable
fun Test(name: String, king: Float) {
}

/**
 * Display with multiple parameters of different stability.
 * Should NOT be skippable due to the unstable list parameter.
 */
@Composable
fun MixedStabilityDisplay(
  title: String,
  count: Int,
  items: List<String>,
  items2: MutableList<String>,
  items3: UnstableUser,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.padding(16.dp)) {
    Text(title)
    Text("Count: $count")
    items.forEach { item ->
      Text("• $item")
    }
  }
}

sealed class MySealed {

  class Child : MySealed()

  class Child2 : MySealed()

  data class Child3(val stableUser: StableUser) : MySealed()

  data class Child4(val unstableUser: UnstableUser) : MySealed()
}

@JvmInline
value class TestValueClass(val test: String)

/**
 * Generic composable with type parameter.
 * Stability depends on the type parameter T.
 */
@Composable
fun <T> GenericDisplay(
  item: T,
  fontWeight4: FontWeight,
  mySealed: MySealed,
  child2: MySealed.Child2,
  child3: MySealed.Child3,
  child4: MySealed.Child4,
  child: MySealed.Child,
  fontWeight: FontWeight,
  fontWeight2: FontWeight?,
  values: kotlin.String,
  value: Int?,
  testValueClass: TestValueClass,
  textAlign: TextAlign,
  textAlign2: TextAlign?,
  displayText: (T) -> String,
) {
  Card {
    Text(
      text = displayText(item),
      modifier = Modifier.padding(16.dp),
    )
  }
}

/**
 * Composable with lambda parameter.
 * Should be skippable - lambdas are stable.
 */
@Composable
fun ActionButton(
  text: String,
  onClick: () -> Unit,
) {
  Button(onClick = onClick) {
    Text(text)
  }
}

// ============================================================================
// Preview Composables - Excluded from Stability Validation
// ============================================================================

/**
 * Example: Preview composable excluded from stability reports.
 * These are only used in Android Studio previews, not in production.
 */
@com.skydoves.compose.stability.runtime.IgnoreStabilityReport
@Preview(showBackground = true)
@Composable
fun StableUserCardPreview() {
  StableUserCard(StableUser("Preview User", 25))
}

/**
 * Example: Debug composable excluded from stability reports.
 */
@Preview
@Composable
fun DebugInfoPanelPreview() {
  Card {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Debug Information", fontWeight = FontWeight.Bold)
      Text("This composable is only in debug builds")
      Text("Excluded from stability validation")
    }
  }
}
// Force recompile
