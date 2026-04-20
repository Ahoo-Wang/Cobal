package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.audio.AudioTranscriptionModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class AudioTranscriptionModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: AudioTranscriptionModel
) : ModelNode<AudioTranscriptionModel>