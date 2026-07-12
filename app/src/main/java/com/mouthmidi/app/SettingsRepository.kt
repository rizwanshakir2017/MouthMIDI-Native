package com.mouthmidi.app

import android.content.Context

class SettingsRepository(
    context: Context
) {

    private val prefs =
        context.getSharedPreferences(
            "mouthmidi_settings",
            Context.MODE_PRIVATE
        )


    fun load(): MouthMidiSettings {

        return MouthMidiSettings(

            jawClosedCalibration =
                prefs.getFloat(
                    "jawClosedCalibration",
                    0.01f
                ),

            jawOpenCalibration =
                prefs.getFloat(
                    "jawOpenCalibration",
                    0.80f
                ),

            minCC =
                prefs.getInt(
                    "minCC",
                    0
                ),

            maxCC =
                prefs.getInt(
                    "maxCC",
                    127
                ),

            midiCC =
                prefs.getInt(
                    "midiCC",
                    1
                ),

            midiChannel =
                prefs.getInt(
                    "midiChannel",
                    1
                ),

            smoothing =
                prefs.getFloat(
                    "smoothing",
                    0f
                )
        )
    }


    fun save(settings: MouthMidiSettings) {

        prefs.edit()

            .putFloat(
                "jawClosedCalibration",
                settings.jawClosedCalibration
            )

            .putFloat(
                "jawOpenCalibration",
                settings.jawOpenCalibration
            )

            .putInt(
                "minCC",
                settings.minCC
            )

            .putInt(
                "maxCC",
                settings.maxCC
            )

            .putInt(
                "midiCC",
                settings.midiCC
            )

            .putInt(
                "midiChannel",
                settings.midiChannel
            )

            .putFloat(
                "smoothing",
                settings.smoothing
            )

            .apply()
    }
}
