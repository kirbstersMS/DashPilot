package com.example.dashpilot.manager

import com.example.dashpilot.model.GigOrder

sealed class GigUiState {
    object Hidden : GigUiState()
    data class ShowingOrder(val order: GigOrder) : GigUiState()
}

