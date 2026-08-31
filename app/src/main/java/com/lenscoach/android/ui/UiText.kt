package com.lenscoach.android.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

data class UiText(
    @StringRes val res: Int,
    val args: List<Any> = emptyList(),
) {
    fun resolve(context: Context): String {
        return if (args.isEmpty()) {
            context.getString(res)
        } else {
            context.getString(res, *args.toTypedArray())
        }
    }
}

@Composable
fun UiText.asString(): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(res, args, configuration) { resolve(context) }
}
