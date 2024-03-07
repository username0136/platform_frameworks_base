/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs.tiles.impl.uimodenight.domain.model

import java.time.LocalTime

/**
 * UiModeNight tile model. Quick Settings tile for: Night Mode / Dark Theme / Dark Mode.
 *
 * @param isNightMode is true when the NightMode is enabled;
 */
data class UiModeNightTileModel(
    val uiMode: Int,
    val isNightMode: Boolean,
    val isPowerSave: Boolean,
    val isLocationEnabled: Boolean,
    val nightModeCustomType: Int,
    val is24HourFormat: Boolean,
    val customNightModeEnd: LocalTime,
    val customNightModeStart: LocalTime
)
