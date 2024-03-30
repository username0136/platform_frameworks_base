/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.keyguard.domain.interactor

import android.animation.ValueAnimator
import com.android.app.animation.Interpolators
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.KeyguardWmStateRefactor
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.KeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionModeOnCanceled
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.util.kotlin.sample
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@SysUISingleton
class FromGoneTransitionInteractor
@Inject
constructor(
    override val transitionRepository: KeyguardTransitionRepository,
    transitionInteractor: KeyguardTransitionInteractor,
    @Background private val scope: CoroutineScope,
    @Background bgDispatcher: CoroutineDispatcher,
    @Main mainDispatcher: CoroutineDispatcher,
    private val keyguardInteractor: KeyguardInteractor,
    powerInteractor: PowerInteractor,
    private val communalInteractor: CommunalInteractor,
    keyguardOcclusionInteractor: KeyguardOcclusionInteractor,
    private val biometricSettingsRepository: BiometricSettingsRepository,
) :
    TransitionInteractor(
        fromState = KeyguardState.GONE,
        transitionInteractor = transitionInteractor,
        mainDispatcher = mainDispatcher,
        bgDispatcher = bgDispatcher,
        powerInteractor = powerInteractor,
        keyguardOcclusionInteractor = keyguardOcclusionInteractor,
    ) {

    override fun start() {
        listenForGoneToAodOrDozing()
        listenForGoneToDreaming()
        listenForGoneToLockscreenOrHub()
        listenForGoneToDreamingLockscreenHosted()
    }

    fun showKeyguard() {
        scope.launch { startTransitionTo(KeyguardState.LOCKSCREEN) }
    }

    // Primarily for when the user chooses to lock down the device
    private fun listenForGoneToLockscreenOrHub() {
        if (KeyguardWmStateRefactor.isEnabled) {
            scope.launch {
                biometricSettingsRepository.isCurrentUserInLockdown
                    .distinctUntilChanged()
                    .filterRelevantKeyguardStateAnd { inLockdown -> inLockdown }
                    .sample(communalInteractor.isIdleOnCommunal, ::Pair)
                    .collect { (_, isIdleOnCommunal) ->
                        val to =
                            if (isIdleOnCommunal) {
                                KeyguardState.GLANCEABLE_HUB
                            } else {
                                KeyguardState.LOCKSCREEN
                            }
                        startTransitionTo(to, ownerReason = "User initiated lockdown")
                    }
            }
        } else {
            scope.launch {
                keyguardInteractor.isKeyguardShowing
                    .filterRelevantKeyguardStateAnd { isKeyguardShowing -> isKeyguardShowing }
                    .sample(communalInteractor.isIdleOnCommunal, ::Pair)
                    .collect { (_, isIdleOnCommunal) ->
                        val to =
                            if (isIdleOnCommunal) {
                                KeyguardState.GLANCEABLE_HUB
                            } else {
                                KeyguardState.LOCKSCREEN
                            }
                        startTransitionTo(to)
                    }
            }
        }
    }

    private fun listenForGoneToDreamingLockscreenHosted() {
        scope.launch {
            keyguardInteractor.isActiveDreamLockscreenHosted
                .filterRelevantKeyguardStateAnd { isActiveDreamLockscreenHosted ->
                    isActiveDreamLockscreenHosted
                }
                .collect { startTransitionTo(KeyguardState.DREAMING_LOCKSCREEN_HOSTED) }
        }
    }

    private fun listenForGoneToDreaming() {
        scope.launch {
            keyguardInteractor.isAbleToDream
                .sample(keyguardInteractor.isActiveDreamLockscreenHosted, ::Pair)
                .filterRelevantKeyguardStateAnd { (isAbleToDream, isActiveDreamLockscreenHosted) ->
                    isAbleToDream && !isActiveDreamLockscreenHosted
                }
                .collect { startTransitionTo(KeyguardState.DREAMING) }
        }
    }

    private fun listenForGoneToAodOrDozing() {
        scope.launch {
            listenForSleepTransition(
                modeOnCanceledFromStartedStep = { TransitionModeOnCanceled.RESET },
            )
        }
    }

    override fun getDefaultAnimatorForTransitionsToState(toState: KeyguardState): ValueAnimator {
        return ValueAnimator().apply {
            interpolator = Interpolators.LINEAR
            duration =
                when (toState) {
                    KeyguardState.DREAMING -> TO_DREAMING_DURATION
                    KeyguardState.AOD -> TO_AOD_DURATION
                    KeyguardState.DOZING -> TO_DOZING_DURATION
                    KeyguardState.LOCKSCREEN -> TO_LOCKSCREEN_DURATION
                    else -> DEFAULT_DURATION
                }.inWholeMilliseconds
        }
    }

    companion object {
        private val DEFAULT_DURATION = 500.milliseconds
        val TO_DREAMING_DURATION = 933.milliseconds
        val TO_AOD_DURATION = 1300.milliseconds
        val TO_DOZING_DURATION = 933.milliseconds
        val TO_LOCKSCREEN_DURATION = DEFAULT_DURATION
    }
}
