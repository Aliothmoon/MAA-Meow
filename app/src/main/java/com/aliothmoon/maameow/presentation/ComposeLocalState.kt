package com.aliothmoon.maameow.presentation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.dokar.sonner.ToasterState

val LocalFloatingWindowContext = compositionLocalOf { false }


val LocalToaster = staticCompositionLocalOf<ToasterState> {
    error("LocalToaster not provided.")
}
