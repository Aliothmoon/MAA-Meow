package com.aliothmoon.maameow.utils.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Immutable
sealed interface UiText {
    data object Empty : UiText

    data class Dynamic(
        val value: String,
    ) : UiText

    data class Resource(
        @param:StringRes val resId: Int,
        val args: List<Any?> = emptyList(),
    ) : UiText

    data class Joined(
        val parts: List<UiText>,
        val separator: UiText = Empty,
    ) : UiText
}

fun uiTextOf(@StringRes resId: Int, vararg args: Any?): UiText =
    UiText.Resource(resId = resId, args = args.toList())

fun uiTextDynamic(value: String?): UiText =
    if (value.isNullOrBlank()) {
        UiText.Empty
    } else {
        UiText.Dynamic(value)
    }

fun uiTextJoin(vararg parts: UiText, separator: UiText = UiText.Empty): UiText =
    UiText.Joined(parts = parts.filterNot { it is UiText.Empty }, separator = separator)

fun uiTextLines(vararg lines: UiText): UiText =
    uiTextJoin(*lines, separator = UiText.Dynamic("\n"))

fun UiText?.resolve(context: Context): String {
    return when (this) {
        null,
        UiText.Empty -> ""

        is UiText.Dynamic -> value

        is UiText.Resource -> {
            val resolvedArgs = args.map { arg ->
                when (arg) {
                    is UiText -> arg.resolve(context)
                    else -> arg
                }
            }.toTypedArray()
            context.getString(resId, *resolvedArgs)
        }

        is UiText.Joined -> {
            val resolvedSeparator = separator.resolve(context)
            parts.joinToString(separator = resolvedSeparator) { it.resolve(context) }
        }
    }
}

@Composable
fun UiText?.asString(): String {
    LocalConfiguration.current
    val context = LocalContext.current
    return resolve(context)
}

