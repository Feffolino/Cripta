package com.cripta.app.ui.tagicons

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * The icons a tag can carry: Microsoft's Fluent Emoji, high-contrast set (MIT, see
 * assets/tagicons/LICENSE.txt), one colour, drawn in the tag's text colour. Stored as path data in
 * assets/tagicons (name, then the shapes: "f:" filled, "e:" filled even-odd, "s<w>:" stroked), read
 * once on first use; each icon is built when first drawn.
 *
 * Names are the English emoji names ("red-heart"); the search and the suggestions also take Italian
 * words through [IT].
 */
object TagIcons {
    private const val ASSET = "tagicons/fluent-emoji-high-contrast.txt"
    private val lock = Mutex()
    @Volatile private var specs: Map<String, String>? = null
    private val built = LruCache<String, ImageVector>(400)

    /** Every icon name, in the file's (alphabetical) order. */
    suspend fun names(context: Context): List<String> = load(context).keys.toList()

    private suspend fun load(context: Context): Map<String, String> = specs ?: lock.withLock {
        specs ?: withContext(Dispatchers.IO) {
            val map = LinkedHashMap<String, String>(1700)
            context.applicationContext.assets.open(ASSET).bufferedReader().useLines { lines ->
                lines.forEach { l ->
                    val tab = l.indexOf('\t')
                    if (tab > 0) map[l.substring(0, tab)] = l.substring(tab + 1)
                }
            }
            map
        }.also { specs = it }
    }

    suspend fun vector(context: Context, name: String): ImageVector? {
        built.get(name)?.let { return it }
        val spec = load(context)[name] ?: return null
        return withContext(Dispatchers.Default) { build(name, spec) }.also { built.put(name, it) }
    }

    /** Already built (no loading): for places that draw many at once and can wait a frame. */
    fun cached(name: String): ImageVector? = built.get(name)

    private fun build(name: String, spec: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 32f, viewportHeight = 32f)
        for (part in spec.split('|')) {
            val colon = part.indexOf(':')
            if (colon < 1) continue
            val kind = part.substring(0, colon)
            val nodes = runCatching { addPathNodes(part.substring(colon + 1)) }.getOrNull() ?: continue
            when {
                kind.startsWith("s") -> b.addPath(nodes, stroke = SolidColor(Color.Black),
                    strokeLineWidth = kind.drop(1).toFloatOrNull() ?: 1f, strokeLineJoin = StrokeJoin.Round, strokeLineCap = StrokeCap.Round)
                else -> b.addPath(nodes, fill = SolidColor(Color.Black),
                    pathFillType = if (kind == "e") PathFillType.EvenOdd else PathFillType.NonZero)
            }
        }
        return b.build()
    }

    /** Lower case, no accents, letters and digits only (words split on anything else). */
    private fun words(s: String): List<String> =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /** "gatti" and "gatto" meet at "gatt": Italian plural / gender endings dropped. */
    private fun stem(w: String) = if (w.length > 4) w.trimEnd('a', 'e', 'i', 'o') else w

    private val itIndex: Map<String, List<String>> by lazy {
        val m = HashMap<String, MutableList<String>>()
        for ((k, v) in IT) for (key in k.split(' ')) m.getOrPut(stem(key)) { ArrayList() } += v.split(' ')
        m
    }

    /**
     * Icons for [text], best first: names sharing its words (English), then those its Italian words
     * lead to. Empty text: [DEFAULTS].
     */
    suspend fun search(context: Context, text: String, limit: Int = 200): List<String> {
        val all = load(context)
        val ws = words(text).filter { it.length >= 2 }
        if (ws.isEmpty()) return DEFAULTS.filter { it in all }
        val score = HashMap<String, Int>()
        fun add(name: String, s: Int) { if (name in all) score.merge(name, s, ::maxOf) }
        for (name in all.keys) {
            val parts = name.split('-')
            for (w in ws) {
                when {
                    parts.contains(w) -> add(name, 90 - parts.size)
                    w.length >= 3 && parts.any { it.startsWith(w) } -> add(name, 60 - parts.size)
                    w.length >= 4 && name.contains(w) -> add(name, 40 - parts.size)
                }
            }
        }
        for (w in ws) {
            val st = stem(w)
            val hits = itIndex[st] ?: itIndex.entries.firstOrNull { (k, _) -> k.length >= 4 && st.length >= 4 && (k.startsWith(st) || st.startsWith(k)) }?.value
            hits?.forEachIndexed { i, target ->
                // A whole name ("red-heart") or a word that names several ("heart").
                if (target in all) add(target, 100 - i)
                else for (name in all.keys) if (name.split('-').contains(target)) add(name, 70 - i - name.count { it == '-' })
            }
        }
        return score.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
            .map { it.key }.take(limit)
    }

    /** A few icons fitting the tag's name, for the editor's first row. */
    suspend fun suggest(context: Context, tagName: String, limit: Int = 12): List<String> =
        if (words(tagName).isEmpty()) emptyList() else search(context, tagName, limit)

    /** Offered before anything is typed: common subjects of a private library. */
    val DEFAULTS = listOf(
        "red-heart", "star", "glowing-star", "fire", "sparkles", "crown", "gem-stone", "locked", "key", "eyes",
        "camera", "movie-camera", "film-frames", "clapper-board", "television", "musical-note", "headphone", "microphone",
        "house", "airplane", "beach-with-umbrella", "mountain", "camping", "globe-showing-europe-africa", "world-map",
        "birthday-cake", "party-popper", "wrapped-gift", "christmas-tree", "jack-o-lantern", "balloon",
        "people-hugging", "baby", "kiss-mark", "ring", "bouquet", "rose", "sunflower", "four-leaf-clover",
        "dog-face", "cat-face", "paw-prints", "horse-face", "fish", "butterfly",
        "fork-and-knife", "pizza", "hamburger", "hot-beverage", "wine-glass", "beer-mug", "cocktail-glass",
        "soccer-ball", "basketball", "bicycle", "automobile", "motorcycle", "flexed-biceps", "trophy",
        "books", "graduation-cap", "briefcase", "laptop", "mobile-phone", "video-game", "artist-palette",
        "sun", "crescent-moon", "cloud", "snowflake", "rainbow", "water-wave", "evergreen-tree",
        "money-bag", "shopping-bags", "hospital", "pill", "bookmark", "pushpin", "label", "check-mark-button",
        "warning", "prohibited", "hundred-points", "thumbs-up", "smiling-face-with-heart-eyes", "face-with-tears-of-joy",
        "ghost", "skull", "alien", "robot", "unicorn", "dragon",
    )

    /**
     * Italian word(s) -> icon names or English name words, most fitting first. Words sharing a stem
     * ("gatto", "gatti", "gatta") meet through [stem].
     */
    private val IT: Map<String, String> = mapOf(
        "amore cuore cuori innamorato romantico" to "red-heart heart-with-arrow growing-heart beating-heart two-hearts love-letter heart",
        "bacio baci" to "kiss-mark kissing-face face-blowing-a-kiss kiss",
        "preferito preferiti top migliori" to "star glowing-star red-heart trophy crown",
        "stella stelle" to "star glowing-star shooting-star dizzy",
        "fuoco caldo hot piccante" to "fire hot-pepper hot-face heart-on-fire",
        "famiglia parenti" to "people-hugging house-with-garden houses busts-in-silhouette",
        "bambino bambini bimbo bimba neonato figlio figlia" to "baby baby-bottle child boy girl baby-chick",
        "mamma madre" to "woman breast-feeding people-hugging",
        "papa padre" to "man people-hugging",
        "amici amico amica gruppo" to "busts-in-silhouette people-hugging handshake clinking-glasses",
        "persona persone gente ritratto" to "bust-in-silhouette busts-in-silhouette person",
        "matrimonio nozze sposi sposa sposo" to "ring wedding bouquet person-with-veil church",
        "anniversario" to "ring two-hearts bouquet",
        "compleanno festa party" to "birthday-cake party-popper balloon wrapped-gift confetti-ball",
        "regalo regali" to "wrapped-gift",
        "natale" to "christmas-tree santa-claus wrapped-gift snowman",
        "halloween" to "jack-o-lantern ghost bat",
        "capodanno" to "fireworks sparkler bottle-with-popping-cork",
        "pasqua" to "egg rabbit-face hatching-chick",
        "viaggio viaggi vacanza vacanze" to "airplane luggage beach-with-umbrella world-map globe-showing-europe-africa",
        "aereo volo aeroporto" to "airplane airplane-departure airplane-arrival",
        "mare spiaggia estate" to "beach-with-umbrella water-wave sun bikini desert-island",
        "montagna montagne neve sci" to "mountain snow-capped-mountain skier snowflake",
        "lago fiume acqua" to "water-wave droplet",
        "campeggio tenda" to "camping",
        "citta" to "cityscape cityscape-at-dusk classical-building",
        "casa" to "house house-with-garden houses",
        "giardino" to "house-with-garden seedling potted-plant",
        "natura" to "evergreen-tree deciduous-tree leaf-fluttering-in-wind herb",
        "albero alberi bosco foresta" to "evergreen-tree deciduous-tree",
        "fiore fiori" to "rose tulip sunflower blossom cherry-blossom bouquet hibiscus",
        "rosa" to "rose",
        "pianta piante" to "potted-plant seedling herb",
        "sole" to "sun sun-with-face sunrise",
        "luna notte" to "crescent-moon full-moon night-with-stars",
        "pioggia" to "cloud-with-rain umbrella-with-rain-drops",
        "inverno freddo" to "snowflake snowman cold-face",
        "autunno" to "fallen-leaf maple-leaf",
        "primavera" to "cherry-blossom tulip blossom",
        "cielo nuvola nuvole" to "cloud sun-behind-cloud",
        "arcobaleno" to "rainbow",
        "animale animali" to "paw-prints dog-face cat-face",
        "cane cani cucciolo" to "dog-face dog guide-dog poodle paw-prints",
        "gatto gatti micio" to "cat-face cat black-cat",
        "cavallo" to "horse-face horse",
        "uccello uccelli" to "bird eagle dove parrot",
        "pesce pesci" to "fish tropical-fish",
        "farfalla" to "butterfly",
        "coniglio" to "rabbit-face rabbit",
        "orso" to "bear teddy-bear",
        "cibo mangiare pranzo cena ristorante" to "fork-and-knife fork-and-knife-with-plate pizza spaghetti",
        "pizza" to "pizza",
        "pasta spaghetti" to "spaghetti",
        "dolce dolci torta" to "shortcake birthday-cake cupcake doughnut cookie",
        "gelato" to "ice-cream soft-ice-cream",
        "caffe colazione" to "hot-beverage croissant",
        "vino" to "wine-glass",
        "birra" to "beer-mug clinking-beer-mugs",
        "cocktail aperitivo" to "cocktail-glass tropical-drink",
        "frutta" to "red-apple banana grapes strawberry",
        "cucina cucinare ricetta ricette" to "cooking cook pot-of-food",
        "musica canzone canzoni concerto" to "musical-note musical-notes headphone microphone guitar",
        "film cinema" to "movie-camera clapper-board film-frames cinema popcorn",
        "serie tv televisione" to "television clapper-board",
        "video" to "movie-camera video-camera film-frames",
        "foto fotografia" to "camera camera-with-flash framed-picture",
        "libro libri lettura" to "books open-book closed-book",
        "scuola studio universita" to "graduation-cap school books pencil",
        "lavoro ufficio" to "briefcase office-building laptop",
        "soldi denaro banca" to "money-bag dollar-banknote euro-banknote bank coin",
        "spesa shopping acquisti" to "shopping-bags shopping-cart",
        "moda vestiti" to "dress t-shirt high-heeled-shoe handbag",
        "sport palestra allenamento" to "flexed-biceps person-lifting-weights soccer-ball trophy",
        "calcio" to "soccer-ball goal-net",
        "basket" to "basketball",
        "tennis" to "tennis",
        "bici bicicletta" to "bicycle",
        "corsa" to "running-shoe person-running",
        "nuoto piscina" to "person-swimming goggles",
        "auto macchina" to "automobile racing-car",
        "moto" to "motorcycle",
        "treno" to "locomotive high-speed-train",
        "gioco giochi videogioco" to "video-game joystick game-die puzzle-piece",
        "arte disegno pittura" to "artist-palette paintbrush crayon framed-picture",
        "tecnologia computer" to "laptop desktop-computer keyboard",
        "telefono cellulare" to "mobile-phone telephone",
        "salute medico ospedale" to "hospital stethoscope pill syringe",
        "medicina" to "pill syringe",
        "bellezza trucco" to "lipstick nail-polish",
        "segreto privato" to "locked locked-with-key key shushing-face",
        "importante urgente" to "warning double-exclamation-mark red-exclamation-mark",
        "fatto completato visto" to "check-mark-button check-mark",
        "da vedere dopo" to "eyes bookmark hourglass-not-done",
        "nuovo nuovi" to "new-button sparkles",
        "vecchio vecchi ricordi ricordo" to "hourglass-done film-frames framed-picture",
        "divertente ridere risate" to "face-with-tears-of-joy rolling-on-the-floor-laughing grinning-face",
        "triste" to "crying-face loudly-crying-face disappointed-face",
        "felice" to "smiling-face-with-smiling-eyes grinning-face beaming-face-with-smiling-eyes",
        "arrabbiato" to "angry-face pouting-face",
        "paura horror" to "face-screaming-in-fear ghost skull",
        "religione chiesa" to "church latin-cross",
        "fantasia magia" to "unicorn crystal-ball magic-wand dragon",
        "spazio" to "rocket ringed-planet milky-way astronaut",
        "tramonto alba" to "sunset sunrise sunrise-over-mountains",
        "trofeo vittoria premio" to "trophy 1st-place-medal sports-medal",
        "re regina" to "crown",
        "diamante gioiello" to "gem-stone ring",
        "chiave" to "key old-key",
        "orologio tempo" to "alarm-clock hourglass-done watch",
        "calendario evento" to "calendar spiral-calendar",
        "nota note appunti" to "memo spiral-notepad pencil",
        "documento documenti" to "page-facing-up file-folder clipboard",
        "cartella" to "file-folder open-file-folder",
        "posta email lettera" to "envelope e-mail love-letter",
        "mondo" to "globe-showing-europe-africa world-map globe-with-meridians",
        "italia" to "pizza spaghetti",
        "bandiera" to "chequered-flag triangular-flag",
        "notizie" to "newspaper",
        "idea" to "light-bulb",
        "scienza" to "microscope alembic dna atom-symbol",
    )
}

/** The icon [name] drawn as an [ImageVector], or null while it loads (or for no icon). */
@Composable
fun rememberTagIcon(name: String?): ImageVector? {
    if (name.isNullOrBlank()) return null
    val context = LocalContext.current
    val icon by produceState(TagIcons.cached(name), name) { value = TagIcons.vector(context, name) }
    return icon
}
