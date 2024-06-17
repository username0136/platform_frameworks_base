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

package com.android.wm.shell.flicker.splitscreen.benchmark

import android.tools.NavBar
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class EnterSplitScreenByDragFromShortcutBenchmark(
    override val flicker: LegacyFlickerTest
) : SplitScreenBase(flicker) {
    @Before
    fun before() {
        Assume.assumeTrue(tapl.isTablet)
    }

    @After
    fun after() {
        tapl.enableBlockTimeout(false)
    }

    protected val thisTransition: FlickerBuilder.() -> Unit = {
        setup {
            tapl.goHome()
            SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, secondaryApp.appName)
            primaryApp.launchViaIntent(wmHelper)
            tapl.enableBlockTimeout(true)
        }
        transitions {
            tapl.showTaskbarIfHidden()
            tapl.launchedAppState.taskbar
                .getAppIcon(secondaryApp.appName)
                .openDeepShortcutMenu()
                .getMenuItem("Split Screen Secondary Activity")
                .dragToSplitscreen(secondaryApp.packageName, primaryApp.packageName)
            SplitScreenUtils.waitForSplitComplete(wmHelper, primaryApp, secondaryApp)
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                // TODO(b/176061063):The 3 buttons of nav bar do not exist in the hierarchy.
                supportedNavigationModes = listOf(NavBar.MODE_GESTURAL)
            )
    }
}
