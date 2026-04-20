package me.ahoo.cobal.experiment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import org.junit.jupiter.api.Test

class FlowTest {
    private val state = MutableStateFlow("")

    @Test
    suspend fun test() {
        state.value = "1"
        state.value = "2"
        state.take(1).collect {
            println("Event: $it")
        }
        state.take(1).collect {
            println("Event: $it")
        }
    }
}
