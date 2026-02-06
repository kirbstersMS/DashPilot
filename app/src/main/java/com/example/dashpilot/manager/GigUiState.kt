package com.example.dashpilot.manager

import com.example.dashpilot.model.GigOrder

sealed class GigUiState {
    object Hidden : GigUiState()
    object Stabilizing : GigUiState() // Optional: Useful if you want to show a loader
    data class ShowingOrder(val order: GigOrder) : GigUiState()
}

sealed class GigSideEffect {
    object RequestRescan : GigSideEffect()
}