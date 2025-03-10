package com.unciv.ui.utils

import com.unciv.models.metadata.GameSettings

/** Interface to support managing orientations
 *
 *  You can turn a mobile device on its side or upside down, and a mobile OS may or may not allow the
 *  position changes to automatically result in App orientation changes. This is about limiting that feature.
 */
interface LimitOrientationsHelper {
    /** Pass a Boolean setting as used in [allowAndroidPortrait][GameSettings.allowAndroidPortrait] to the OS.
     *  @param allow `true`: allow all orientations (follows sensor as limited by OS settings)
     *          `false`: allow only landscape orientations (both if supported, otherwise default landscape only)
     */
    fun allowPortrait(allow: Boolean)
}
