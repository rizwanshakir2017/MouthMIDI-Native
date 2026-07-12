package com.mouthmidi.app

data class MouthMidiSettings(

    var jawClosedCalibration: Float = 0.01f,

    var jawOpenCalibration: Float = 0.80f,

    var minCC: Int = 0,

    var maxCC: Int = 127,

    var midiCC: Int = 1,

    var midiChannel: Int = 1,

    var smoothing: Float = 0f
)
