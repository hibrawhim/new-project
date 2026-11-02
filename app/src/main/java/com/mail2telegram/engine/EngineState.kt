package com.mail2telegram.engine

import kotlinx.coroutines.flow.MutableStateFlow

object EngineState {
    val running = MutableStateFlow(false)
    val paused = MutableStateFlow(false)
    val inFlight = MutableStateFlow(0)
}
