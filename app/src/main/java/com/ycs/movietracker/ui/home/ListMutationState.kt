package com.ycs.movietracker.ui.home

sealed class ListMutationState {
    object Idle : ListMutationState()
    object Loading : ListMutationState()
    data class Error(val message: String) : ListMutationState()
    object Success : ListMutationState()
}
