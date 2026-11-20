package com.adyen.issuing.mobile.provisioning.exampleapp

import android.app.Activity
import java.lang.ref.WeakReference

/** Shared with the retained ViewModel, without retaining a destroyed Activity across rotation. */
class ResumedActivityProvider {
    private var resumedActivity = WeakReference<Activity>(null)

    fun onResume(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    fun onPause(activity: Activity) {
        if (resumedActivity.get() === activity) resumedActivity.clear()
    }

    fun requireActivity(): Activity = checkNotNull(resumedActivity.get()) {
        "No resumed provisioning Activity"
    }
}
