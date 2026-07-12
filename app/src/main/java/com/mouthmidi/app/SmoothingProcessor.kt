package com.mouthmidi.app

class SmoothingProcessor(

    private var amount: Float = 0f

) {

    private var previous = 0f


    fun setAmount(
        value: Float
    ) {
        amount =
            value.coerceIn(
                0f,
                0.99f
            )
    }


    fun process(
        input: Float
    ): Float {

        previous =
            previous +
            (input - previous) *
            (1f - amount)

        return previous
    }


    fun reset() {

        previous = 0f

    }
}
