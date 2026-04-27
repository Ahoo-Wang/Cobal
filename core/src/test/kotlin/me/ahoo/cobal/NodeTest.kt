package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class NodeTest {

    @Test
    fun `DefaultNode should have default weight of 1`() {
        val node = DefaultNode("node-1")
        node.id.assert().isEqualTo("node-1")
        node.weight.assert().isEqualTo(1)
    }

    @Test
    fun `DefaultNode should accept custom weight`() {
        val node = DefaultNode("node-1", weight = 5)
        node.id.assert().isEqualTo("node-1")
        node.weight.assert().isEqualTo(5)
    }

    @Test
    fun `DefaultNode is a data class with equality`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-1", weight = 3)
        node1.assert().isEqualTo(node2)
    }

    @Test
    fun `DefaultModelNode should hold model instance`() {
        val model = "my-model-instance"
        val node = DefaultModelNode("node-1", weight = 2, model = model)
        node.id.assert().isEqualTo("node-1")
        node.weight.assert().isEqualTo(2)
        node.model.assert().isEqualTo(model)
    }

    @Test
    fun `DefaultModelNode should have default weight`() {
        val node = DefaultModelNode("node-1", model = "model")
        node.weight.assert().isEqualTo(1)
    }
}
