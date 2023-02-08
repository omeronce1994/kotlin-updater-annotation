package com.nando.update_object.annotation.modifiers

sealed interface UpdateObjectModifier {
    sealed interface VisibilityModifier : UpdateObjectModifier {
        object Internal : VisibilityModifier
        object Private : VisibilityModifier
        object Protected : VisibilityModifier
    }
}