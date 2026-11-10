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
 * A filtered set of Tabler Icons Filled on a 24-unit viewport. The catalog is loaded once and
 * vectors are built on demand. Locale indexes use Unicode CLDR annotations and a dictionary.
 * Legacy Fluent names are resolved before lookup. See assets/tagicons/LICENSE.txt for credits.
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
        val legacyEmoji: Map<String, String>,
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

    internal fun parseCatalog(asset: String): Map<String, String> {
        val body = verifiedBody(asset) ?: return emptyMap()
        val icons = LinkedHashMap<String, String>()
        for (line in body.trimEnd('\n').lineSequence()) {
            val name = line.substringBefore('\t')
            val paths = line.substringAfter('\t', "")
            if (!namePattern.matches(name) || paths.split('|').any { part ->
                    !(part.startsWith("f:M") || part.startsWith("e:M") ||
                        part.startsWith("f:m") || part.startsWith("e:m"))
                } || icons.put(name, paths) != null
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
            val paths = assets.open("$DIRECTORY/tabler-filled.txt").bufferedReader().use { parseCatalog(it.readText()) }
            if (paths.isEmpty()) return null
            val legacy = mutableMapOf<String, String>()
            val emojis = mutableMapOf<String, String>()
            val aliases = assets.open("$DIRECTORY/legacy.txt").bufferedReader().use { verifiedBody(it.readText()) }
                ?: return null
            for (line in aliases.trimEnd('\n').lineSequence()) {
                val fields = line.split('\t')
                if (fields.size !in 2..3 || !namePattern.matches(fields[0]) || fields[1] !in paths ||
                    legacy.put(fields[0], fields[1]) != null
                ) return null
                fields.getOrNull(2)?.let { emojis.putIfAbsent(emojiKey(it), fields[1]) }
            }
            return if (legacy.isEmpty()) null else Catalog(paths, legacy, emojis)
        } catch (_: IOException) {
            return null
        }
    }

    private fun language(context: Context): String =
        context.resources.configuration.locales[0].language.takeIf { it in languages } ?: "en"

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

    /** Every icon name in alphabetical order. */
    suspend fun names(context: Context): List<String> = load(context)?.paths?.keys?.toList().orEmpty()

    suspend fun title(context: Context, name: String): String {
        val resolved = resolveName(context, name) ?: return name.replace('-', ' ')
        return terms(context, language(context))[resolved]?.title ?: resolved.replace('-', ' ')
    }

    /** A null resolver means the assets could not be validated. Leave stored names intact. */
    internal fun migrationResolver(context: Context): ((String) -> String?)? =
        (catalog ?: readCatalog(context))?.let { loaded -> { name -> resolve(loaded, name) } }

    suspend fun resolveName(context: Context, name: String): String? = load(context)?.let { resolve(it, name) }

    private fun resolve(loaded: Catalog, name: String): String? =
        name.takeIf { it in loaded.paths } ?: loaded.legacy[name]

    private fun emojiKey(emoji: String) = emoji.trim().replace("\uFE0F", "")

    /** The drawing corresponding to a single emoji, with or without its variation selector. */
    suspend fun forEmoji(context: Context, emoji: String): String? {
        val loaded = load(context) ?: return null
        val key = emojiKey(emoji)
        return terms(context, "en").entries.firstOrNull { key in it.value.emojis }?.key
            ?: loaded.legacyEmoji[key]
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

    private fun build(name: String, spec: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        )
        for (part in spec.split('|')) {
            builder.addPath(
                addPathNodes(part.drop(2)),
                fill = SolidColor(Color.Black),
                pathFillType = if (part.startsWith("e:")) PathFillType.EvenOdd else PathFillType.NonZero,
            )
        }
        return builder.build()
    }

    private fun words(text: String, lang: String): List<String> =
        wordSeparators.split(
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
                .let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(latinMarks, "\$1") }
                .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
        ).filter(String::isNotEmpty).map { if (lang == "it") stem(it) else it }

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
            val query = words(text, lang)
            val englishQuery = words(text, "en")
            if (query.isEmpty()) return@withContext DEFAULTS.filter { it in names }.take(limit)
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
                .take(limit).map { it.first }
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
                substring && cjk.containsMatchIn(word) && words.any { word in it } -> exact - 10
                word.length >= 3 && words.any { it.startsWith(word) } -> exact - 25
                else -> 0
            }
        } ?: 0

    suspend fun suggest(context: Context, tagName: String, limit: Int = 12): List<String> =
        if (words(tagName, "en").isEmpty()) emptyList() else search(context, tagName, limit)
    /** Common subjects offered before typing. */
    val DEFAULTS = listOf(
        "heart", "star", "stars", "flame", "sparkles", "crown", "diamond", "lock",
        "key", "eye", "camera", "video", "polaroid", "device-tv", "disc", "headphones",
        "microphone", "home", "plane", "umbrella", "mountain", "campfire", "globe", "world",
        "candle", "confetti", "gift", "christmas-tree", "balloon", "man", "mood-kid", "flower",
        "clover", "paw", "fish-bone", "butterfly", "tools-kitchen-2", "pizza", "bread", "mug",
        "glass-full", "beer", "ball-bowling", "bike", "car", "motorbike", "barbell", "trophy",
        "book", "award", "briefcase", "device-desktop", "device-mobile", "device-gamepad", "palette", "sun",
        "moon", "cloud", "droplet", "sunrise", "cash-banknote", "garden-cart", "hospital-circle", "pill",
        "bookmark", "pin", "label", "check", "alert-triangle", "forbid", "thumb-up", "mood-crazy-happy",
        "ghost", "alien", "message-chatbot",
    )

    private val IT = mapOf(
        "amore cuore cuori innamorato romantico" to "heart mail",
        "bacio baci" to "heart",
        "preferito preferiti top migliori" to "star stars heart trophy crown",
        "stella stelle" to "star stars",
        "fuoco caldo hot piccante" to "flame mood-wrrr heart",
        "famiglia parenti" to "man home",
        "bambino bambini bimbo bimba neonato figlio figlia" to "mood-kid bottle egg-cracked",
        "mamma madre" to "woman man",
        "papa padre" to "man",
        "amici amico amica gruppo" to "man glass-full",
        "persona persone gente ritratto" to "user man",
        "matrimonio nozze sposi sposa sposo" to "diamond cross flower",
        "anniversario" to "diamond heart flower",
        "compleanno festa party" to "candle confetti balloon gift",
        "regalo regali" to "gift",
        "natale" to "christmas-tree gift droplet",
        "halloween" to "ghost",
        "capodanno" to "confetti sparkles bottle",
        "pasqua" to "egg paw egg-cracked",
        "viaggio viaggi vacanza vacanze" to "plane briefcase umbrella world globe",
        "aereo volo aeroporto" to "plane plane-departure plane-arrival",
        "mare spiaggia estate" to "umbrella droplet sun shirt",
        "montagna montagne neve sci" to "mountain droplet",
        "lago fiume acqua" to "droplet",
        "campeggio tenda" to "campfire",
        "citta" to "home",
        "casa" to "home",
        "giardino" to "home seedling",
        "natura" to "christmas-tree leaf seedling",
        "albero alberi bosco foresta" to "christmas-tree",
        "fiore fiori" to "flower",
        "rosa" to "flower",
        "pianta piante" to "seedling",
        "sole" to "sun sunrise",
        "luna notte" to "moon",
        "pioggia" to "cloud umbrella",
        "inverno freddo" to "droplet mood-sad",
        "autunno" to "leaf",
        "primavera" to "flower",
        "cielo nuvola nuvole" to "cloud",
        "arcobaleno" to "sunrise",
        "animale animali" to "paw",
        "cane cani cucciolo" to "paw",
        "gatto gatti micio" to "paw",
        "cavallo" to "paw",
        "uccello uccelli" to "feather",
        "pesce pesci" to "fish-bone",
        "farfalla" to "butterfly",
        "coniglio" to "paw",
        "orso" to "paw",
        "cibo mangiare pranzo cena ristorante" to "tools-kitchen-2 pizza soup",
        "pizza" to "pizza",
        "pasta spaghetti" to "soup",
        "dolce dolci torta" to "cookie candle",
        "gelato" to "milk",
        "caffe colazione" to "mug bread",
        "vino" to "glass-full",
        "birra" to "beer",
        "cocktail aperitivo" to "glass-full",
        "frutta" to "apple melon",
        "cucina cucinare ricetta ricette" to "chef-hat flask",
        "musica canzone canzoni concerto" to "disc headphones microphone guitar-pick",
        "film cinema" to "video polaroid bowl",
        "serie tv televisione" to "device-tv video",
        "video" to "video polaroid",
        "foto fotografia" to "camera photo",
        "libro libri lettura" to "book",
        "scuola studio universita" to "award school book pencil",
        "lavoro ufficio" to "briefcase home device-desktop",
        "soldi denaro banca" to "cash-banknote coin",
        "spesa shopping acquisti" to "garden-cart shopping-cart",
        "moda vestiti" to "shirt hanger-2 briefcase",
        "sport palestra allenamento" to "barbell ball-bowling trophy",
        "calcio" to "ball-bowling trophy",
        "basket" to "ball-bowling",
        "tennis" to "ball-bowling",
        "bici bicicletta" to "bike",
        "corsa" to "barbell",
        "nuoto piscina" to "scuba-diving-tank eyeglass",
        "auto macchina" to "car",
        "moto" to "motorbike",
        "treno" to "train",
        "gioco giochi videogioco" to "device-gamepad dice puzzle",
        "arte disegno pittura" to "palette paint pencil photo",
        "tecnologia computer" to "device-desktop keyboard",
        "telefono cellulare" to "device-mobile phone",
        "salute medico ospedale" to "hospital-circle medical-cross pill",
        "medicina" to "pill",
        "bellezza trucco" to "paint",
        "segreto privato" to "lock key",
        "importante urgente" to "alert-triangle",
        "fatto completato visto" to "check",
        "da vedere dopo" to "eye bookmark hourglass",
        "nuovo nuovi" to "sparkles",
        "vecchio vecchi ricordi ricordo" to "hourglass polaroid photo",
        "divertente ridere risate" to "mood-crazy-happy mood-happy",
        "triste" to "mood-sad",
        "felice" to "mood-smile mood-happy",
        "arrabbiato" to "mood-angry",
        "paura horror" to "ghost",
        "religione chiesa" to "cross",
        "fantasia magia" to "alien sparkles",
        "spazio" to "ufo satellite meteor",
        "tramonto alba" to "sunset sunrise",
        "trofeo vittoria premio" to "trophy award",
        "re regina" to "crown",
        "diamante gioiello" to "diamond",
        "chiave" to "key",
        "orologio tempo" to "alarm hourglass device-watch",
        "calendario evento" to "calendar",
        "nota note appunti" to "pencil clipboard",
        "documento documenti" to "article folder clipboard",
        "cartella" to "folder folder-open",
        "posta email lettera" to "mail",
        "mondo" to "globe world",
        "italia" to "pizza soup",
        "bandiera" to "flag",
        "notizie" to "article",
        "idea" to "bulb",
        "scienza" to "microscope flask atom-2",
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
