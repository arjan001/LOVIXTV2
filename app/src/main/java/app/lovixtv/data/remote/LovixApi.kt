/*
 * LOVIX Api Client
 *
 * Speaks the LVX website API (api.php / adult.php) to fetch the same movie
 * catalogue that the web player uses. The LVX API returns JSON with actions:
 *   - bootstrap:  All movies (up to 60 per page), tags, people, genres
 *   - filter:     Filtered movies by tag/person/genre/studio
 *   - movie:      Single movie detail by ID
 *
 * Stream URLs are built using the Xtream Codes format stored in the Source
 * credentials: SERVER/movie/USERNAME/PASSWORD/ID.EXT
 */
package app.lovixtv.data.remote

import app.lovixtv.data.model.Category
import app.lovixtv.data.model.Movie
import app.lovixtv.data.model.Source
import app.lovixtv.data.model.StreamKind
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class LovixApi(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /** Thrown when the LVX server returns an error or the response is malformed. */
    class LovixException(message: String, cause: Throwable? = null) : IOException(message, cause)

    // ── Bootstrap: fetch all movies, tags, people, genres ─────────────

    /**
     * Fetches the full bootstrap payload from the LVX server.
     * Returns a triple: (movies, categories, people list)
     *
     * The categories are derived from the genres array returned by the API.
     * People are returned as a list of name strings for metadata.
     */
    suspend fun bootstrap(source: Source): BootstrapResult = withContext(Dispatchers.IO) {
        val baseUrl = source.url.trimEnd('/')

        // Try adult.php first (main site), fall back to api.php
        val apiPaths = listOf("adult.php", "api.php")
        var lastError: Throwable? = null

        for (apiPath in apiPaths) {
            val apiUrl = "$baseUrl/$apiPath"
            runCatching {
                val body = getJson(apiUrl, mapOf("action" to "bootstrap"))
                val moviesObj = body.jsonObjectOrNull ?: throw LovixException("Invalid bootstrap response")

                // Parse movies array
                val movies = parseMovies(moviesObj, source)

                // Parse genres into categories
                val categories = parseGenres(moviesObj, source.id)

                // Parse people
                val people = parsePeople(moviesObj)

                // Parse tags
                val tags = parseTags(moviesObj)

                BootstrapResult(
                    movies = movies,
                    categories = categories,
                    people = people,
                    tags = tags,
                )
            }.onSuccess { return@withContext it }
                .onFailure { lastError = it }
        }

        throw lastError ?: LovixException("Could not connect to LVX server")
    }

    /**
     * Fetches a single movie detail by ID.
     */
    suspend fun movieDetail(source: Source, movieId: String): Movie? = withContext(Dispatchers.IO) {
        val baseUrl = source.url.trimEnd('/')
        val apiUrl = "$baseUrl/api.php"

        runCatching {
            val body = getJson(apiUrl, mapOf("action" to "movie", "id" to movieId))
            val movieObj = body.jsonObjectOrNull?.get("movie")?.jsonObjectOrNull
                ?: throw LovixException("Movie not found")

            parseSingleMovie(movieObj, source)
        }.getOrNull()
    }

    /**
     * Fetches filtered movies by genre.
     */
    suspend fun moviesByGenre(source: Source, genre: String): List<Movie> = withContext(Dispatchers.IO) {
        val baseUrl = source.url.trimEnd('/')
        val apiUrl = "$baseUrl/api.php"

        val filters = listOf(mapOf("type" to "genre", "value" to genre))
        val filtersJson = json.encodeToString(List<Map<String, String>>.serializer(), filters)

        runCatching {
            val body = getJson(apiUrl, mapOf("action" to "filter", "filters" to filtersJson))
            val moviesObj = body.jsonObjectOrNull ?: throw LovixException("Invalid filter response")
            parseMovies(moviesObj, source)
        }.getOrDefault(emptyList())
    }

    // ── Stream URL Building ────────────────────────────────────────────

    /**
     * Builds the Xtream Codes stream URL for a movie.
     * Format: SERVER/movie/USERNAME/PASSWORD/ID.EXT
     */
    fun buildStreamUrl(source: Source, movieId: String, extension: String? = null): String {
        val server = source.url.trimEnd('/')
        val username = source.username ?: ""
        val password = source.password ?: ""
        val ext = extension ?: "mp4"
        return "$server/movie/$username/$password/$movieId.$ext"
    }

    // ── JSON Parsing Helpers ───────────────────────────────────────────

    private fun getJson(url: String, params: Map<String, String>): JsonObject {
        val httpUrl = url.toHttpUrlOrNull()
            ?: throw LovixException("Invalid URL: $url")

        val fullUrl = httpUrl.newBuilder().apply {
            for ((key, value) in params) {
                addQueryParameter(key, value)
            }
        }.build()

        val request = Request.Builder()
            .url(fullUrl)
            .addHeader("User-Agent", sourceUserAgent())
            .build()

        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw LovixException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw LovixException("Empty response from server")
            json.decodeFromString(JsonElement.serializer(), body).jsonObjectOrNull
                ?: throw LovixException("Invalid JSON response")
        }
    }

    private fun sourceUserAgent(): String = "LOVIX/1.0 (Android)"

    private fun parseMovies(moviesObj: JsonObject, source: Source): List<Movie> {
        val moviesArray = moviesObj["movies"]?.jsonArray ?: return emptyList()

        return moviesArray.mapNotNull { element ->
            val obj = element.jsonObjectOrNull ?: return@mapNotNull null
            val id = obj["id"]?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            Movie(
                sourceId = source.id,
                streamId = id,
                name = obj["clean_title"]?.contentOrNull
                    ?: obj["title"]?.contentOrNull
                    ?: obj["name"]?.contentOrNull
                    ?: id,
                categoryId = obj["genre"]?.jsonArrayOrNull?.firstOrNull()?.jsonPrimitive?.contentOrNull,
                posterUrl = obj["poster"]?.contentOrNull?.takeIf { it.isNotBlank() },
                rating = obj["rating"]?.toDoubleOrNull(),
                year = obj["year"]?.toIntOrNull(),
                plot = obj["description"]?.contentOrNull ?: obj["plot"]?.contentOrNull,
                durationSeconds = obj["duration_secs"]?.toIntOrNull()
                    ?: obj["duration"]?.toIntOrNull(),
                containerExtension = obj["container_extension"]?.contentOrNull
                    ?.takeIf { it.isNotBlank() },
                streamUrl = buildStreamUrl(source, id, obj["container_extension"]?.contentOrNull),
                addedMillis = 0L,
                backdropUrl = obj["backdrop"]?.contentOrNull?.takeIf { it.isNotBlank() },
                cast = joinPeopleList(obj["people"]),
                genre = joinGenreList(obj["genre"]),
            )
        }
    }

    private fun parseSingleMovie(movieObj: JsonObject, source: Source): Movie? {
        val id = movieObj["id"]?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null

        return Movie(
            sourceId = source.id,
            streamId = id,
            name = movieObj["clean_title"]?.contentOrNull
                ?: movieObj["title"]?.contentOrNull
                ?: movieObj["name"]?.contentOrNull
                ?: id,
            categoryId = movieObj["genre"]?.jsonArrayOrNull?.firstOrNull()?.jsonPrimitive?.contentOrNull,
            posterUrl = movieObj["poster"]?.contentOrNull?.takeIf { it.isNotBlank() },
            rating = movieObj["rating"]?.toDoubleOrNull(),
            year = movieObj["year"]?.toIntOrNull(),
            plot = movieObj["description"]?.contentOrNull ?: movieObj["plot"]?.contentOrNull,
            durationSeconds = movieObj["duration_secs"]?.toIntOrNull()
                ?: movieObj["duration"]?.toIntOrNull(),
            containerExtension = movieObj["container_extension"]?.contentOrNull
                ?.takeIf { it.isNotBlank() },
            streamUrl = buildStreamUrl(source, id, movieObj["container_extension"]?.contentOrNull),
            addedMillis = 0L,
            backdropUrl = movieObj["backdrop"]?.contentOrNull?.takeIf { it.isNotBlank() },
            cast = joinPeopleList(movieObj["people"]),
            genre = joinGenreList(movieObj["genre"]),
        )
    }

    private fun parseGenres(moviesObj: JsonObject, sourceId: Long): List<Category> {
        val genresArray = moviesObj["genres"]?.jsonArray ?: return emptyList()
        val genreList = mutableListOf<Category>()

        genresArray.forEachIndexed { index, element ->
            val name = element.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@forEachIndexed
            genreList.add(
                Category(
                    id = name.lowercase().replace(Regex("\\s+"), "_"),
                    sourceId = sourceId,
                    name = name,
                    kind = StreamKind.MOVIE,
                    sortIndex = index,
                )
            )
        }

        return genreList
    }

    private fun parsePeople(moviesObj: JsonObject): List<String> {
        val peopleArray = moviesObj["people"]?.jsonArray ?: return emptyList()
        return peopleArray.mapNotNull { element ->
            when (element) {
                is JsonObject -> element["name"]?.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    private fun parseTags(moviesObj: JsonObject): List<String> {
        val tagsArray = moviesObj["tags"]?.jsonArray ?: return emptyList()
        return tagsArray.mapNotNull { element ->
            when (element) {
                is JsonObject -> element["name"]?.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    // ── Extension Functions ────────────────────────────────────────────

    private fun joinPeopleList(element: JsonElement?): String {
        val array = (element as? JsonArray) ?: return ""
        return array.mapNotNull {
            when (it) {
                is JsonObject -> it["name"]?.contentOrNull
                is JsonPrimitive -> it.contentOrNull
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString(", ")
    }

    private fun joinGenreList(element: JsonElement?): String {
        val array = (element as? JsonArray) ?: return ""
        return array.mapNotNull { it.jsonPrimitive?.contentOrNull }
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private val JsonElement?.jsonObjectOrNull: JsonObject?
        get() = when (this) {
            is JsonObject -> this
            else -> null
        }

    private val JsonElement?.jsonArrayOrNull: JsonArray?
        get() = when (this) {
            is JsonArray -> this
            else -> null
        }

    private val JsonElement?.contentOrNull: String?
        get() = when (this) {
            is JsonPrimitive -> contentOrNull
            else -> null
        }

    private val JsonElement?.jsonPrimitive: JsonPrimitive?
        get() = when (this) {
            is JsonPrimitive -> this
            else -> null
        }

    private fun JsonElement?.toDoubleOrNull(): Double? =
        (this as? JsonPrimitive)?.toDoubleOrNull()

    private fun JsonElement?.toIntOrNull(): Int? =
        (this as? JsonPrimitive)?.toIntOrNull()
}

/** Result of a bootstrap call containing movies, categories, people, and tags. */
data class BootstrapResult(
    val movies: List<Movie>,
    val categories: List<Category>,
    val people: List<String>,
    val tags: List<String>,
)
