package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.output.Response
import dev.langchain4j.model.scoring.ScoringModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class LoadBalancedScoringModelTest {

    @Test
    fun `scoreAll should delegate to underlying model`() {
        val model = mockk<ScoringModel>()
        val segments = listOf(TextSegment.from("hello"))
        val query = "hi"
        val expectedResponse = mockk<Response<List<Double>>>()

        every { model.scoreAll(any<List<TextSegment>>(), any<String>()) } returns expectedResponse

        val lb = loadBalancer<ScoringModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedScoringModel(lb)

        val result = balancedModel.scoreAll(segments, query)
        result.assert().isEqualTo(expectedResponse)
        verify { model.scoreAll(any<List<TextSegment>>(), any<String>()) }
    }

    @Test
    fun `scoreAll should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<ScoringModel>()
        val segments = listOf(TextSegment.from("hello"))
        val query = "hi"

        every { model.scoreAll(any<List<TextSegment>>(), any<String>()) } throws RuntimeException("fail")

        val lb = loadBalancer<ScoringModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedScoringModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.scoreAll(segments, query)
        }
    }
}
