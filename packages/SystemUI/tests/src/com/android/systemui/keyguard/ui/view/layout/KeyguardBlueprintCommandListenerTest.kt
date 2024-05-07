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

package com.android.systemui.keyguard.ui.view.layout

import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.data.repository.KeyguardBlueprintRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.withArgCaptor
import java.io.PrintWriter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
@SmallTest
class KeyguardBlueprintCommandListenerTest : SysuiTestCase() {
    private lateinit var keyguardBlueprintCommandListener: KeyguardBlueprintCommandListener
    @Mock private lateinit var commandRegistry: CommandRegistry
    @Mock private lateinit var keyguardBlueprintRepository: KeyguardBlueprintRepository
    @Mock private lateinit var keyguardBlueprintInteractor: KeyguardBlueprintInteractor
    @Mock private lateinit var pw: PrintWriter
    private lateinit var command: () -> Command

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        keyguardBlueprintCommandListener =
            KeyguardBlueprintCommandListener(
                commandRegistry,
                keyguardBlueprintRepository,
                keyguardBlueprintInteractor,
            )
        keyguardBlueprintCommandListener.start()
        command =
            withArgCaptor<() -> Command> {
                verify(commandRegistry).registerCommand(eq("blueprint"), capture())
            }
    }

    @Test
    fun testHelp() {
        command().execute(pw, listOf("help"))
        verify(pw, atLeastOnce()).println(anyString())
        verify(keyguardBlueprintInteractor, never()).transitionToBlueprint(anyString())
    }

    @Test
    fun testBlank() {
        command().execute(pw, listOf())
        verify(pw, atLeastOnce()).println(anyString())
        verify(keyguardBlueprintInteractor, never()).transitionToBlueprint(anyString())
    }

    @Test
    fun testValidArg() {
        command().execute(pw, listOf("fake"))
        verify(keyguardBlueprintInteractor).transitionToBlueprint("fake")
    }

    @Test
    fun testValidArg_Int() {
        command().execute(pw, listOf("1"))
        verify(keyguardBlueprintInteractor).transitionToBlueprint(1)
    }
}
