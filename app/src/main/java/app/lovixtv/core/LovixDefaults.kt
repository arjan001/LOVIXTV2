/*
 * Default LOVIX provider credentials.
 *
 * These are the built-in provider credentials from account.json. On first launch,
 * the app uses these defaults to auto-sync movies so the user sees content immediately.
 * Users can edit these credentials or add their own source at any time.
 */
package app.lovixtv.core

import app.lovixtv.data.model.Source
import app.lovixtv.data.model.SourceKind

/**
 * Default LOVIX source configuration.
 *
 * Pre-configured with the app's built-in provider credentials so new users
 * see movies immediately on first launch without manual setup.
 */
object LovixDefaults {

    /** Default LOVIX server URL */
    const val SERVER_URL = "http://line.istreamix.com"

    /** Default Xtream username */
    const val USERNAME = "e59d95dc29"

    /** Default Xtream password */
    const val PASSWORD = "1c217ba7981a"

    /** Default source name displayed in the app */
    const val SOURCE_NAME = "LOVIX"

    /**
     * Build a default LOVIX source with built-in credentials.
     */
    fun defaultSource(): Source = Source(
        name = SOURCE_NAME,
        kind = SourceKind.LOVIX,
        url = SERVER_URL,
        username = USERNAME,
        password = PASSWORD,
    )
}
