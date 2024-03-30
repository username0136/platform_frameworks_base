/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.domain.startable

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.shade.ShadeExpansionChangeEvent
import com.android.systemui.shade.ShadeExpansionListener
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShadeStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val shadeInteractor = kosmos.shadeInteractor
    private val sceneInteractor = kosmos.sceneInteractor
    private val shadeExpansionStateManager = kosmos.shadeExpansionStateManager
    private val deviceEntryRepository = kosmos.fakeDeviceEntryRepository
    private val deviceUnlockedInteractor = kosmos.deviceUnlockedInteractor
    private val fakeConfigurationRepository = kosmos.fakeConfigurationRepository
    private val fakeSceneDataSource = kosmos.fakeSceneDataSource

    private val underTest = kosmos.shadeStartable

    @Test
    fun hydrateShadeMode() =
        testScope.runTest {
            overrideResource(R.bool.config_use_split_notification_shade, false)
            val shadeMode by collectLastValue(shadeInteractor.shadeMode)

            underTest.start()
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            overrideResource(R.bool.config_use_split_notification_shade, true)
            fakeConfigurationRepository.onAnyConfigurationChange()
            assertThat(shadeMode).isEqualTo(ShadeMode.Split)

            overrideResource(R.bool.config_use_split_notification_shade, false)
            fakeConfigurationRepository.onAnyConfigurationChange()
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    @EnableSceneContainer
    fun hydrateShadeExpansionStateManager() =
        testScope.runTest {
            val expansionListener = mock<ShadeExpansionListener>()
            var latestChangeEvent: ShadeExpansionChangeEvent? = null
            whenever(expansionListener.onPanelExpansionChanged(any())).thenAnswer {
                latestChangeEvent = it.arguments[0] as ShadeExpansionChangeEvent
                Unit
            }
            shadeExpansionStateManager.addExpansionListener(expansionListener)

            underTest.start()

            setUnlocked(true)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Gone)
                )
            sceneInteractor.setTransitionState(transitionState)

            changeScene(Scenes.Gone, transitionState)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            assertThat(latestChangeEvent)
                .isEqualTo(
                    ShadeExpansionChangeEvent(
                        fraction = 0f,
                        expanded = false,
                        tracking = false,
                    )
                )

            changeScene(Scenes.Shade, transitionState) { progress ->
                assertThat(latestChangeEvent?.fraction).isEqualTo(progress)
            }
        }

    private fun TestScope.setUnlocked(isUnlocked: Boolean) {
        val isDeviceUnlocked by collectLastValue(deviceUnlockedInteractor.isDeviceUnlocked)
        deviceEntryRepository.setUnlocked(isUnlocked)
        runCurrent()

        assertThat(isDeviceUnlocked).isEqualTo(isUnlocked)
    }

    private fun TestScope.changeScene(
        toScene: SceneKey,
        transitionState: MutableStateFlow<ObservableTransitionState>,
        assertDuringProgress: ((progress: Float) -> Unit) = {},
    ) {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val progressFlow = MutableStateFlow(0f)
        transitionState.value =
            ObservableTransitionState.Transition(
                fromScene = checkNotNull(currentScene),
                toScene = toScene,
                progress = progressFlow,
                isInitiatedByUserInput = true,
                isUserInputOngoing = flowOf(true),
            )
        runCurrent()
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 0.2f
        runCurrent()
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 0.6f
        runCurrent()
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 1f
        runCurrent()
        assertDuringProgress(progressFlow.value)

        transitionState.value = ObservableTransitionState.Idle(toScene)
        fakeSceneDataSource.changeScene(toScene)
        runCurrent()
        assertDuringProgress(progressFlow.value)

        assertThat(currentScene).isEqualTo(toScene)
    }
}
