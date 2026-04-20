package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class NodeTest {
    @Test
    fun `ModelNode should hold model instance`() {
        val mockModel = Any()
        val modelNode = object : ModelNode<Any> {
            override val id: NodeId = "test-node"
            override val weight: Int = 1
            override val model: Any = mockModel
        }
        modelNode.id.assert().isEqualTo("test-node")
        modelNode.weight.assert().isEqualTo(1)
        modelNode.model.assert().isSameAs(mockModel)
    }
}