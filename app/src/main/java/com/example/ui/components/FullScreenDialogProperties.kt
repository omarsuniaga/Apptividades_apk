package com.example.ui.components

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.ui.theme.ApptividadesThemeDefaults

/** For dialogs that cover the whole screen. Use together with [FullScreenDialogWindow]. */
val FullScreenDialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

/**
 * Makes the dialog window really cover the screen; call it inside the dialog content.
 * [FullScreenDialogProperties] alone leaves the window fitted between the system bars while
 * the content keeps the full screen height, which pushes its footer out of view. The content
 * must apply the status and navigation bar paddings itself.
 */
@Composable
fun FullScreenDialogWindow() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    // The bars are transparent now, so their icons must contrast with the dialog background.
    val isLightBackground = ApptividadesThemeDefaults.colors.ground.luminance() > 0.5f
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = isLightBackground
            isAppearanceLightNavigationBars = isLightBackground
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }
}
