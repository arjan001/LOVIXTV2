/*
 * Default LOVIX provider configuration.
 *
 * Lists the built-in providers from account.json. On first launch the user picks a provider
 * and enters their username and password. The server URL is filled in automatically.
 */
package app.lovixtv.core

import app.lovixtv.data.model.Source
import app.lovixtv.data.model.SourceKind

/**
 * A built-in LOVIX provider with a display name and server URL.
 */
data class LovixProvider(
    val name: String,
    val serverUrl: String,
)

/**
 * LOVIX default configuration — all built-in providers and helper methods.
 */
object LovixDefaults {

    /** The three built-in LOVIX providers from account.json. */
    val providers: List<LovixProvider> = listOf(
        LovixProvider("LOVIX (Main)", "http://line.istreamix.com"),
        LovixProvider("LOVIX (Backup 1)", "http://vpn.new4k-tx.site"),
        LovixProvider("LOVIX (Backup 2)", "http://vpn.istreamix.com"),
    )

    /** Default source name displayed in the app. */
    const val SOURCE_NAME = "LOVIX"

    /**
     * Build a LOVIX source for the given provider with the supplied credentials.
     */
    fun sourceFor(provider: LovixProvider, username: String, password: String): Source = Source(
        name = SOURCE_NAME,
        kind = SourceKind.LOVIX,
        url = provider.serverUrl,
        username = username,
        password = password,
    )

    /**
     * Build a LOVIX source for the first (default) provider.
     * Used for auto-sync on first launch.
     */
    fun defaultSource(): Source = Source(
        name = SOURCE_NAME,
        kind = SourceKind.LOVIX,
        url = providers[0].serverUrl,
        username = "",
        password = "",
    )
}
