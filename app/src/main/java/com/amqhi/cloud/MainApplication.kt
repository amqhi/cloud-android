/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.amqhi.cloud.core.Core

class MainApplication : Application() {
    private var startedActivityCount = 0
    private var isFirstLaunch = true

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivityCount == 0) {
                    if (isFirstLaunch) {
                        isFirstLaunch = false
                    } else {
                        Core.onResume()
                    }
                }
                startedActivityCount++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount == 0) {
                    Core.onSuspend()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (startedActivityCount == 0 && activity.isFinishing) {
                    Core.destroy()
                }
            }
        })
    }
}