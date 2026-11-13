package com.tarnished.grace.data

enum class Phase { OFF, STARTING, ON }

data class NodeView(
    val name: String,
    val latency: Int?,
    val alive: Boolean,
    val active: Boolean
)

data class UiState(
    val phase: Phase = Phase.OFF,
    val nodes: List<NodeView> = emptyList(),
    val activeName: String? = null,
    val activeMs: Int? = null,
    val allDead: Boolean = false,
    val message: String? = null
)
