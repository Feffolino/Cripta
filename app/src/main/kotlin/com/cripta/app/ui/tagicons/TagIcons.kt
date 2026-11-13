package com.cripta.app.ui.tagicons

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * A curated set of Fluent Emoji Flat (Microsoft) icons, coloured, on a 32-unit viewport cropped to
 * its 30 inner units. The catalog is loaded once and vectors are built on demand. Locale indexes
 * are the emoji's Unicode CLDR names and keywords. Icon names kept from before ([legacy]: the
 * Tabler names of the previous catalog, a few older Fluent ones) resolve to their closest icon.
 * See assets/tagicons/LICENSE.txt for credits.
 */
object TagIcons {
    private const val DIRECTORY = "tagicons"
    private val languages = setOf("en", "it", "ar", "de", "es", "fr", "hi", "ja", "ko", "pt", "ru", "zh")
    private val namePattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val wordSeparators = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private val latinMarks = Regex("(\\p{sc=Latin})\\p{M}+")
    private val cjk = Regex("[\\p{sc=Han}\\p{sc=Hiragana}\\p{sc=Katakana}]")
    private val lock = Mutex()
    @Volatile private var catalog: Catalog? = null
    private val lexicons = LruCache<String, Map<String, Terms>>(3)
    private val vectors = LruCache<String, ImageVector>(400)

    private data class Catalog(
        val paths: Map<String, String>,
        val legacy: Map<String, String>,
    )

    private data class Terms(
        val emojis: List<String>,
        val title: String,
        val titleWords: List<String>,
        val keywordWords: Set<String>,
    )

    private fun verifiedBody(asset: String): String? {
        val normalized = asset.replace("\r\n", "\n")
        val body = normalized.substringAfter('\n', "")
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return body.takeIf { it.isNotEmpty() && digest == normalized.substringBefore('\n') }
    }

    /** A fill "f:RRGGBB[AA]:path" (nonzero) or "e:..." (even-odd); a stroke "s:RRGGBB[AA]:width:cap:path". */
    private val partPattern = Regex("(?:[fe]:[0-9a-f]{6}(?:[0-9a-f]{2})?|s:[0-9a-f]{6}(?:[0-9a-f]{2})?:[0-9.]+:[brs]):[Mm][^:|]*")

    internal fun parseCatalog(asset: String): Map<String, String> {
        val body = verifiedBody(asset) ?: return emptyMap()
        val icons = LinkedHashMap<String, String>()
        for (line in body.trimEnd('\n').lineSequence()) {
            val name = line.substringBefore('\t')
            val paths = line.substringAfter('\t', "")
            if (!namePattern.matches(name) || paths.split('|').any { !partPattern.matches(it) } ||
                icons.put(name, paths) != null
            ) return emptyMap()
        }
        return icons
    }

    private suspend fun load(context: Context): Catalog? = catalog ?: lock.withLock {
        catalog ?: withContext(Dispatchers.IO) { readCatalog(context) }?.also { catalog = it }
    }

    private fun readCatalog(context: Context): Catalog? {
        try {
            val assets = context.applicationContext.assets
            val paths = assets.open("$DIRECTORY/fluent-emoji-flat.txt").bufferedReader().use { parseCatalog(it.readText()) }
            if (paths.isEmpty()) return null
            val legacy = mutableMapOf<String, String>()
            val aliases = assets.open("$DIRECTORY/legacy.txt").bufferedReader().use { verifiedBody(it.readText()) }
                ?: return null
            for (line in aliases.trimEnd('\n').lineSequence()) {
                val fields = line.split('\t')
                if (fields.size != 2 || !namePattern.matches(fields[0]) || !namePattern.matches(fields[1]) ||
                    legacy.put(fields[0], fields[1]) != null
                ) return null
            }
            return if (legacy.isEmpty()) null else Catalog(paths, legacy.filterValues { it in paths })
        } catch (_: IOException) {
            return null
        }
    }

    /** The first supported language in the configuration's list, as the resources pick their strings. */
    private fun language(context: Context): String {
        val locales = context.resources.configuration.locales
        return (0 until locales.size()).map { locales[it].language }.firstOrNull { it in languages } ?: "en"
    }

    private suspend fun terms(context: Context, lang: String): Map<String, Terms> =
        lexicons.get(lang) ?: lock.withLock {
            lexicons.get(lang) ?: withContext(Dispatchers.IO) {
                context.applicationContext.assets.open("$DIRECTORY/$lang.txt").bufferedReader().useLines { lines ->
                    lines.associate { line ->
                        val (name, emojis, title, keywords) = line.split('\t', limit = 4)
                        name to Terms(
                            emojis.split(' ').filter(String::isNotEmpty), title, words(title, lang),
                            keywords.split('|').flatMap { words(it, lang) }.toSet(),
                        )
                    }
                }
            }.also { lexicons.put(lang, it) }
        }

    /** Every icon name, in the catalogue's order (not sorted). */
    suspend fun names(context: Context): List<String> = load(context)?.paths?.keys?.toList().orEmpty()

    suspend fun title(context: Context, name: String): String {
        val resolved = resolveName(context, name) ?: return name.replace('-', ' ')
        return terms(context, language(context))[resolved]?.title ?: resolved.replace('-', ' ')
    }

    /**
     * A null resolver means the assets could not be validated. Leave stored names intact.
     * Names stored by the previous version are Tabler's, which share a few names with Fluent's for
     * another drawing ("mouse": the computer mouse, not the animal): [fluent] false reads
     * legacy.txt first. [fluent] true: the names come from before the Tabler icons (a database
     * from version 33, a backup without "icons"), which are Fluent's own.
     */
    internal fun migrationResolver(context: Context, fluent: Boolean = true): ((String) -> String?)? =
        (catalog ?: readCatalog(context))?.let { loaded ->
            { name -> if (fluent) resolve(loaded, name) else loaded.legacy[name] ?: name.takeIf { it in loaded.paths } }
        }

    suspend fun resolveName(context: Context, name: String): String? = load(context)?.let { resolve(it, name) }

    private fun resolve(loaded: Catalog, name: String): String? =
        name.takeIf { it in loaded.paths } ?: loaded.legacy[name]

    private fun emojiKey(emoji: String) = emoji.trim().replace("\uFE0F", "")

    private val skinTones = Regex("[\\x{1F3FB}-\\x{1F3FF}]")

    /** The drawing corresponding to a single emoji, with or without its variation selector or skin tone. */
    suspend fun forEmoji(context: Context, emoji: String): String? {
        if (load(context) == null) return null
        val key = emojiKey(emoji)
        val entries = terms(context, "en").entries
        return entries.firstOrNull { key in it.value.emojis }?.key
            ?: skinTones.replace(key, "").takeIf { it != key }?.let { plain -> entries.firstOrNull { plain in it.value.emojis }?.key }
    }

    suspend fun vector(context: Context, name: String): ImageVector? {
        val loaded = load(context) ?: return null
        val resolved = resolve(loaded, name) ?: return null
        vectors.get(resolved)?.let { return it }
        return withContext(Dispatchers.Default) {
            build(resolved, loaded.paths.getValue(resolved))
        }.also { vectors.put(resolved, it) }
    }

    /** Already built, without loading. */
    fun cached(name: String): ImageVector? = catalog?.let { resolve(it, name) }?.let(vectors::get)

    /** "RRGGBB" or "RRGGBBAA". */
    private fun color(hex: String): SolidColor {
        val value = hex.substring(0, 6).toInt(16)
        val alpha = if (hex.length == 8) hex.substring(6).toInt(16) else 0xFF
        return SolidColor(Color(value shr 16 and 0xFF, value shr 8 and 0xFF, value and 0xFF, alpha))
    }

    /**
     * The icons are drawn on 32 units with 1 or 2 of margin: the picture covers the 30 inner ones,
     * so the few that touch the edge lose almost nothing and the rest fills more of its box.
     */
    private const val CROP = 1f

    private fun build(name: String, spec: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 32f - 2 * CROP, viewportHeight = 32f - 2 * CROP,
        )
        builder.addGroup(translationX = -CROP, translationY = -CROP)
        for (part in spec.split('|')) {
            if (part[0] == 's') {
                val (_, hex, width, cap, path) = part.split(':', limit = 5)
                builder.addPath(
                    addPathNodes(path), stroke = color(hex), strokeLineWidth = width.toFloat(),
                    strokeLineCap = when (cap) { "r" -> StrokeCap.Round; "s" -> StrokeCap.Square; else -> StrokeCap.Butt },
                )
            } else {
                val (kind, hex, path) = part.split(':', limit = 3)
                builder.addPath(
                    addPathNodes(path), fill = color(hex),
                    pathFillType = if (kind == "e") PathFillType.EvenOdd else PathFillType.NonZero,
                )
            }
        }
        return builder.build()
    }

    private fun words(text: String, lang: String): List<String> =
        wordSeparators.split(
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
                .let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(latinMarks, "\$1") }
                .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
        ).filter(String::isNotEmpty).map { if (lang == "it") stem(it) else it }

    /**
     * "de", "di", "of": words of one or two Latin or Cyrillic letters are glue in a name ("Foto di
     * famiglia"), and nearly every emoji name holds some, so they never lead the search unless
     * they are all there is ("tv").
     */
    private fun withoutGlue(words: List<String>): List<String> = words.filter { word ->
        word.length > 2 || word.any { c ->
            val script = Character.UnicodeScript.of(c.code)
            script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.CYRILLIC
        }
    }.ifEmpty { words }

    /** Italian plural and gender endings, only on Latin words. */
    private fun stem(word: String) =
        if (word.length > 4 && word.all { it in 'a'..'z' }) word.trimEnd('a', 'e', 'i', 'o') else word

    private val italianSubjects by lazy {
        buildMap<String, List<String>> {
            for ((keywords, names) in IT) {
                for (word in keywords.split(' ')) {
                    val stem = stem(word)
                    put(stem, get(stem).orEmpty() + names.split(' '))
                }
            }
        }
    }

    /** Search localized names and keywords alongside English names and tags. */
    suspend fun search(context: Context, text: String, limit: Int = 200): List<String> =
        withContext(Dispatchers.Default) {
            val names = load(context)?.paths?.keys ?: return@withContext emptyList()
            val lang = language(context)
            val query = withoutGlue(words(text, lang))
            val englishQuery = withoutGlue(words(text, "en"))
            // An emoji pasted in the field is found as itself (the defaults were shown instead).
            val pasted = text.trim().takeIf { it.isNotEmpty() }?.let { forEmoji(context, it) }
            if (query.isEmpty()) return@withContext listOfNotNull(pasted)
            val english = terms(context, "en")
            val local = if (lang == "en") english else terms(context, lang)
            val subjects = if (lang == "it") query.flatMap { italianSubjects[it].orEmpty() }.distinct() else emptyList()

            names.mapNotNull { name ->
                val subject = subjects.indexOf(name)
                val score = maxOf(
                    if (subject >= 0) (120 - subject).coerceAtLeast(1) else 0,
                    score(local[name], query, lang == "ja" || lang == "zh"),
                    score(english[name], englishQuery, false),
                    match(name.split('-'), englishQuery, 94, false),
                )
                if (score == 0) null else name to score
            }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length }.thenBy { it.first })
                .take(limit).map { it.first }.let { found -> if (pasted == null) found else listOf(pasted) + (found - pasted) }
        }

    private fun score(terms: Terms?, query: List<String>, substring: Boolean): Int =
        if (terms == null) 0 else maxOf(
            match(terms.titleWords, query, 100, substring),
            match(terms.keywordWords, query, 75, substring),
        )

    private fun match(words: Collection<String>, query: List<String>, exact: Int, substring: Boolean): Int =
        query.maxOfOrNull { word ->
            when {
                word in words -> exact
                substring && cjk.containsMatchIn(word) && words.any {
                    word in it || (it.length >= 2 && cjk.containsMatchIn(it) && it in word)
                } -> exact - 10
                word.length >= 3 && words.any { it.startsWith(word) } -> exact - 25
                else -> 0
            }
        } ?: 0

    suspend fun suggest(context: Context, tagName: String, limit: Int = 12): List<String> =
        if (words(tagName, "en").isEmpty()) emptyList() else search(context, tagName, limit)
    /** Common subjects offered before typing. */
    val DEFAULTS = listOf(
        "red-heart", "star", "glowing-star", "fire", "sparkles", "crown", "gem-stone", "locked",
        "key", "eye", "camera", "video-camera", "camera-with-flash", "television", "optical-disk", "headphone",
        "microphone", "musical-notes", "house", "airplane", "umbrella", "mountain", "camping", "globe-with-meridians",
        "world-map", "candle", "confetti-ball", "wrapped-gift", "christmas-tree", "balloon", "man", "baby",
        "blossom", "four-leaf-clover", "paw-prints", "dog-face", "cat-face", "fish", "butterfly", "fork-and-knife",
        "pizza", "bread", "hot-beverage", "wine-glass", "beer-mug", "soccer-ball", "bicycle", "automobile",
        "motorcycle", "person-lifting-weights", "trophy", "closed-book", "sports-medal", "briefcase", "desktop-computer", "mobile-phone",
        "video-game", "artist-palette", "sun", "crescent-moon", "cloud", "rainbow", "droplet", "sunrise",
        "dollar-banknote", "shopping-cart", "hospital", "pill", "bookmark", "pushpin", "label", "check-mark-button",
        "warning", "prohibited", "thumbs-up", "face-with-tears-of-joy", "ghost", "alien", "robot",
    )

    /** Italian words (stems) for the subjects a vault holds, to the icons that fit them, best first. */
    private val IT = mapOf(
        "amore cuore cuori innamorato romantico" to "red-heart love-letter beating-heart",
        "bacio baci" to "kiss-mark",
        "preferito preferiti top migliori" to "star glowing-star red-heart trophy crown",
        "stella stelle" to "star glowing-star",
        "fuoco caldo hot piccante" to "fire hot-pepper",
        "famiglia parenti" to "man woman baby house",
        "bambino bambini bimbo bimba neonato figlio figlia" to "baby boy girl baby-bottle",
        "mamma madre" to "woman",
        "papa padre" to "man",
        "amici amico amica gruppo" to "busts-in-silhouette clinking-glasses handshake",
        "persona persone gente ritratto" to "bust-in-silhouette person man woman",
        "matrimonio nozze sposi sposa sposo" to "ring bouquet",
        "anniversario" to "ring red-heart bouquet",
        "compleanno festa party" to "birthday-cake candle party-popper wrapped-gift balloon",
        "regalo regali" to "wrapped-gift",
        "natale" to "christmas-tree santa-claus wrapped-gift snowman",
        "halloween" to "jack-o-lantern ghost skull",
        "capodanno" to "fireworks sparkler clinking-glasses",
        "pasqua" to "egg rabbit-face baby-chick",
        "viaggio viaggi vacanza vacanze" to "airplane luggage beach-with-umbrella world-map globe-with-meridians",
        "aereo volo aeroporto" to "airplane airplane-departure airplane-arrival",
        "mare spiaggia estate" to "beach-with-umbrella water-wave sun bikini",
        "montagna montagne neve sci" to "mountain snow-capped-mountain skier snowflake",
        "lago fiume acqua" to "droplet water-wave",
        "campeggio tenda" to "camping tent",
        "citta" to "cityscape office-building house",
        "casa" to "house house-with-garden",
        "giardino" to "house-with-garden seedling",
        "natura" to "evergreen-tree leaf-fluttering-in-wind seedling",
        "albero alberi bosco foresta" to "evergreen-tree deciduous-tree palm-tree",
        "fiore fiori" to "blossom tulip cherry-blossom sunflower",
        "rosa" to "rose",
        "pianta piante" to "seedling herb potted-plant",
        "sole" to "sun sunrise",
        "luna notte" to "crescent-moon full-moon",
        "pioggia" to "cloud-with-rain umbrella",
        "inverno freddo" to "snowflake snowman cold-face",
        "autunno" to "fallen-leaf maple-leaf",
        "primavera" to "blossom tulip",
        "cielo nuvola nuvole" to "cloud sun-behind-cloud",
        "arcobaleno" to "rainbow",
        "animale animali" to "paw-prints dog-face cat-face",
        "cane cani cucciolo" to "dog-face dog poodle paw-prints",
        "gatto gatti micio" to "cat-face cat paw-prints",
        "cavallo" to "horse-face horse",
        "uccello uccelli" to "bird feather",
        "pesce pesci" to "fish tropical-fish",
        "farfalla" to "butterfly",
        "coniglio" to "rabbit-face rabbit",
        "orso" to "bear",
        "cibo mangiare pranzo cena ristorante" to "fork-and-knife pizza pot-of-food",
        "pizza" to "pizza",
        "pasta spaghetti" to "spaghetti",
        "dolce dolci torta" to "birthday-cake cookie shortcake cupcake",
        "gelato" to "ice-cream soft-ice-cream",
        "caffe colazione" to "hot-beverage bread croissant",
        "vino" to "wine-glass",
        "birra" to "beer-mug",
        "cocktail aperitivo" to "cocktail-glass tropical-drink",
        "frutta" to "red-apple banana strawberry watermelon",
        "cucina cucinare ricetta ricette" to "cooking pot-of-food green-salad",
        "musica canzone canzoni concerto" to "musical-notes headphone microphone guitar",
        "film cinema" to "clapper-board movie-camera film-projector popcorn",
        "serie tv televisione" to "television video-camera",
        "video" to "video-camera movie-camera",
        "foto fotografia" to "camera camera-with-flash framed-picture",
        "libro libri lettura" to "closed-book books open-book",
        "scuola studio universita" to "school graduation-cap books pencil",
        "lavoro ufficio" to "briefcase office-building desktop-computer",
        "soldi denaro banca" to "money-bag dollar-banknote bank credit-card",
        "spesa shopping acquisti" to "shopping-cart shopping-bags",
        "moda vestiti" to "t-shirt dress jeans high-heeled-shoe",
        "sport palestra allenamento" to "person-lifting-weights trophy soccer-ball",
        "calcio" to "soccer-ball",
        "basket" to "basketball",
        "tennis" to "tennis",
        "bici bicicletta" to "bicycle",
        "corsa" to "person-running running-shoe",
        "nuoto piscina" to "person-swimming",
        "auto macchina" to "automobile",
        "moto" to "motorcycle",
        "treno" to "train",
        "gioco giochi videogioco" to "video-game game-die puzzle-piece",
        "arte disegno pittura" to "artist-palette paintbrush framed-picture pencil",
        "tecnologia computer" to "desktop-computer laptop keyboard",
        "telefono cellulare" to "mobile-phone telephone",
        "salute medico ospedale" to "hospital stethoscope pill",
        "medicina" to "pill",
        "bellezza trucco" to "lipstick nail-polish",
        "segreto privato" to "locked key",
        "importante urgente" to "warning red-exclamation-mark",
        "fatto completato visto" to "check-mark-button",
        "da vedere dopo" to "eyes bookmark hourglass-not-done",
        "nuovo nuovi" to "sparkles new-button",
        "vecchio vecchi ricordi ricordo" to "older-person camera-with-flash",
        "divertente ridere risate" to "face-with-tears-of-joy grinning-face",
        "triste" to "crying-face",
        "felice" to "slightly-smiling-face grinning-face",
        "arrabbiato" to "angry-face",
        "paura horror" to "ghost skull",
        "religione chiesa" to "church latin-cross",
        "fantasia magia" to "magic-wand sparkles alien",
        "spazio" to "flying-saucer rocket satellite",
        "tramonto alba" to "sunset sunrise",
        "trofeo vittoria premio" to "trophy sports-medal",
        "re regina" to "crown",
        "diamante gioiello" to "gem-stone",
        "chiave" to "key",
        "orologio tempo" to "alarm-clock hourglass-done watch",
        "calendario evento" to "calendar",
        "nota note appunti" to "memo clipboard",
        "documento documenti" to "page-facing-up file-folder clipboard",
        "cartella" to "file-folder open-file-folder",
        "posta email lettera" to "envelope",
        "mondo" to "globe-with-meridians world-map",
        "italia" to "pizza spaghetti",
        "bandiera" to "triangular-flag chequered-flag",
        "notizie" to "newspaper",
        "idea" to "light-bulb",
        "scienza" to "microscope alembic atom-symbol",
    )

}

/** Resolve a saved icon name so unknown legacy values render like an unset icon. */
@Composable
internal fun rememberResolvedTagIcon(name: String?): String? {
    if (name.isNullOrBlank()) return null
    val context = LocalContext.current
    return key(name) {
        val resolved by produceState<String?>(name) { value = TagIcons.resolveName(context, name) }
        resolved
    }
}

/** The icon drawn as an ImageVector, or null while it loads or when it is unknown. */
@Composable
fun rememberTagIcon(name: String?): ImageVector? {
    if (name.isNullOrBlank()) return null
    val context = LocalContext.current
    return key(name) {
        val icon by produceState(TagIcons.cached(name)) { value = TagIcons.vector(context, name) }
        icon
    }
}
