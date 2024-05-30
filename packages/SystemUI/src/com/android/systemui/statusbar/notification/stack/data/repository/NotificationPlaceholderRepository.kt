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

package com.android.systemui.statusbar.notification.stack.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * This repository contains state generated by the composable placeholders to define the position
 * and appearance of the notification stack and related visual elements
 */
@SysUISingleton
class NotificationPlaceholderRepository @Inject constructor() {

    /** The alpha of the shade in order to show brightness. */
    val alphaForBrightnessMirror = MutableStateFlow(1f)

    /**
     * The bounds of the notification shade scrim / container in the current scene.
     *
     * When `null`, clipping should not be applied to notifications.
     */
    val shadeScrimBounds = MutableStateFlow<ShadeScrimBounds?>(null)

    /** height made available to the notifications in the size-constrained mode of lock screen. */
    val constrainedAvailableSpace = MutableStateFlow(0)

    /**
     * Whether the notification stack is scrolled to the top; i.e., it cannot be scrolled down any
     * further.
     */
    val scrolledToTop = MutableStateFlow(true)
}
