package dev.glorioustr.mtzstudio.tester

import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface TesterAvailability {
    data object Available : TesterAvailability
    data class Unavailable(val reason: String) : TesterAvailability
}

interface ThemeTesterAdapter {
    fun availability(context: Context): TesterAvailability
    fun createTestIntent(context: Context, mtzUri: Uri): Intent?
}

/**
 * Safe default until a public Theme Manager action, package and MIME combination has been
 * observed and documented on supported stock devices. This module intentionally contains no
 * reflection, hidden APIs, hooks, root operations, rights generation, or result manipulation.
 */
class DisabledThemeTesterAdapter : ThemeTesterAdapter {
    override fun availability(context: Context): TesterAvailability = TesterAvailability.Unavailable(
        "Theme Manager public tester intent has not been verified for this device",
    )

    override fun createTestIntent(context: Context, mtzUri: Uri): Intent? = null
}

