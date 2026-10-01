package com.goodlife.app.news

import android.util.Xml
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.net.USER_AGENT
import com.goodlife.app.net.networkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** Une vraie actu : titre, court extrait, lien vers l'article complet chez la source. */
data class FeedItem(
    val title: String, val summary: String, val url: String, val source: String, val time: Long,
    val kind: String = FOOD,    // food | sport | insolite
    val theme: String = kind,   // food | sport (thème réel, même pour une actu insolite)
    val public: Boolean = false // organisme public : informations publiques réutilisables (résumé IA possible)
) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("summary", summary).put("url", url)
        .put("source", source).put("time", time).put("kind", kind).put("public", public)

    companion object {
        const val FOOD = "food"
        const val SPORT = "sport"
        const val HEALTH = "health"
        const val INSOLITE = "insolite"
        fun fromJson(o: JSONObject) = FeedItem(
            o.optString("title"), o.optString("summary"), o.optString("url"), o.optString("source"), o.optLong("time"),
            o.optString("kind", FOOD).ifBlank { FOOD }, public = o.optBoolean("public", false)
        )
    }
}

/**
 * Les actus du jour, pour tout le monde (sans clé ni IA) : les flux RSS publics de sources reconnues sur
 * l'alimentation, la santé et le sport. Seuls le titre, un court extrait et le lien sont affichés ; l'article
 * s'ouvre chez la source. Les sujets anxiogènes (décès, maladies graves, troubles alimentaires…) sont écartés.
 * Chaque actu n'est montrée qu'une fois : l'app retient les liens déjà proposés.
 */
object NewsFeed {
    /**
     * [kind] : thème imposé par la source (null = source générale, filtrée par mots-clés).
     * [press] : éditeur de presse. Droits voisins (art. L218-2 et L211-3-1 du code de la propriété intellectuelle) :
     * seuls le titre et le lien sont repris, sans extrait. Les organismes publics gardent un court extrait.
     */
    private class Source(
        val name: String, val url: String, val kind: String?,
        val press: Boolean = true,
        val insoliteOnly: Boolean = false   // flux trop large : on n'y prend que les actus insolites
    )

    private val SOURCES = listOf(
        Source("franceinfo", "https://www.francetvinfo.fr/sante/alimentation.rss", FeedItem.FOOD),
        Source("Sciences et Avenir", "https://www.sciencesetavenir.fr/nutrition/rss.xml", FeedItem.FOOD),
        Source("franceinfo Sport", "https://www.francetvinfo.fr/sports.rss", FeedItem.SPORT),
        Source("Futura Santé", "https://www.futura-sciences.com/rss/sante/actualites.xml", null),
        Source("Futura", "https://www.futura-sciences.com/rss/actualites.xml", null, insoliteOnly = true),
        Source("Sciences et Avenir", "https://www.sciencesetavenir.fr/sante/rss.xml", null),
        Source("Anses", "https://www.anses.fr/fr/rss.xml", null, press = false),
        Source("Santé publique France", "https://www.santepubliquefrance.fr/rss/news/1008", null, press = false)
    )

    /** Seulement l'alimentation et le sport (le texte est entouré d'espaces pour les mots courts). */
    private val FOOD_WORDS = listOf(
        "aliment", "nutrition", "nutriti", "manger", " mange", "repas", "cuisine", "cuisin", "recette", "fruit", "légume",
        "sucre", "sucré", " sel ", "salé", "protéine", "fibres", "vitamine", "boisson", "petit-déjeuner", "régime",
        "céréale", "viande", "poisson", " lait ", "laitier", "fromage", " pain", " café", " thé ", "chocolat", " eau ",
        "obésité", "gastronomi", "restaurant", " chef ", "épice", " miel", "œuf", " riz ", " pâtes", "pomme", "tomate"
    )
    private val SPORT_WORDS = listOf(
        "sport", "activité physique", "sédentar", " marche ", "course à pied", "running", "marathon", " vélo", "cyclis",
        "muscle", "musculation", "exercice", "entraînement", "natation", "football", "rugby", "tennis", "athlétisme",
        "olympique", " jo ", "handball", "basket", "randonnée", "fitness", "yoga", "athlète"
    )

    /** Santé et bien-être (sans les sujets anxiogènes, déjà écartés). */
    private val HEALTH_WORDS = listOf(
        "santé", "bien-être", "sommeil", "dormir", "stress", "prévention", "hydrat", "posture", "écran", "respiration",
        "méditation", "fatigue", "énergie", "moral", "dos ", "cœur", "coeur", "tension", "vitamine", "soleil"
    )

    /** Une actu insolite : étonnante, surprenante, un record… */
    private val ODD_WORDS = listOf(
        "insolite", "étonnant", "surprenant", "bizarre", "curieux", "curieuse", "inattendu", "étrange",
        "saviez-vous", "improbable", "drôle de", "insoupçonn"
    )

    /** Écartés partout : ce qui inquiète sans aider, les offres d'emploi et les annonces administratives. */
    private val EXCLUDE = listOf(
        "décès", "deces", "mort", "tué", "meurtre", "suicide", "cancer", "tumeur", "intoxication", "bactérie tueuse",
        "boulimie", "anorexie", "tca", "trouble alimentaire", "troubles alimentaires", "épidémie", "virus", "vaccin",
        "(h/f)", "h/f", "stagiaire", "recrute", "rupture", "nommé", "nomination", "alcool", "drogue", "cannabis",
        "tabac", "guerre", "attentat", "procès", "mis en examen", "dopage", "agression", "violence", "plainte",
        "condamn", "accident",
        // Publicité déguisée (bons plans des sites d'actu)
        "offre", "promo", "bon plan", "bons plans", "soldes", "réduction", "prix cassé", "vente flash", "black friday",
        "amazon", "cdiscount", "aliexpress", "vevor", "€ ", "euros seulement", "à saisir", "dernières heures", "offert", "vpn", "abonnement", "forfait",
        // Avis administratifs (autorisations de produits, décisions réglementaires) : pas des actus grand public
        "conclusions de l", "autorisation", "changement mineur", "demande de modification", "phytopharmaceutique",
        "biocide", "médicament vétérinaire", "marché public", "appel à candidatures", "appel à projets", "consultation publique"
    )

    private const val KEY = "news_feed"
    /** Article sans date dans son flux. */
    const val NO_DATE = -1L

    private const val VERSION = 8   // change quand les règles de choix changent (les actus du jour sont alors rechoisies)
    private const val PER_DAY = 3      // actus alimentation / sport, en plus de l'actu insolite si on en trouve une
    private const val MAX_AGE_MS = 8L * 86_400_000L

    /** Actus déjà choisies aujourd'hui (sans réseau), ou null s'il faut les charger. */
    /** Thèmes d'actus choisis (réglage), sans l'anecdote du jour qui vient de la banque intégrée. */
    fun themes(): Set<String> = Repo.settings.value.newsThemes.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun cached(): List<FeedItem>? {
        val st = state()
        if (st.optString("day") != localDay(0) || st.optInt("v") != VERSION || st.optString("themes") != themes().sorted().joinToString(",")) return null
        return st.optJSONArray("items")?.let { a -> (0 until a.length()).map { FeedItem.fromJson(a.getJSONObject(it)) } }
    }

    private fun state(): JSONObject = runCatching { JSONObject(Repo.getExtra(KEY) ?: "{}") }.getOrElse { JSONObject() }

    /** Charge les flux et choisit les actus du jour (jamais déjà montrées). Garde le choix toute la journée. */
    suspend fun today(): List<FeedItem> {
        cached()?.let { return it }
        val wanted = themes()
        val topics = listOf(FeedItem.FOOD, FeedItem.SPORT, FeedItem.HEALTH).filter { it in wanted }
        if (topics.isEmpty() && FeedItem.INSOLITE !in wanted) return emptyList()
        val all = coroutineScope {
            SOURCES.map { s -> async(Dispatchers.IO) { runCatching { fetch(s) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
        }
        if (all.isEmpty()) throw IOException("Impossible de charger les actus pour le moment. Vérifie ta connexion et réessaie.")
        val st = state()
        val seen = st.optJSONArray("seen")?.let { a -> (0 until a.length()).map { a.optString(it) } }?.toMutableList() ?: mutableListOf()
        val now = System.currentTimeMillis()
        val fresh = all.asSequence()
            .filter { it.url !in seen && (it.time == NO_DATE || it.time in (now - MAX_AGE_MS)..(now + 86_400_000L)) }
            .distinctBy { it.title.lowercase() }
            .sortedByDescending { it.time }
            .toList()
        val picked = mutableListOf<FeedItem>()
        // L'insolite : un vrai article publié aujourd'hui ou hier, sinon rien
        val sinceYesterday = Repo.dayBounds(-1).first
        if (FeedItem.INSOLITE in wanted) fresh.firstOrNull { it.kind == FeedItem.INSOLITE && it.time >= sinceYesterday }?.let { picked += it }
        // Le reste doit être un thème normal (les insolites du flux « insolite seulement » ne servent qu'ici)
        val regularPool = fresh.filter { it.kind != FeedItem.INSOLITE || it.source != "Futura" }
        // Puis 2 actus sur l'alimentation et 1 sur le sport, en variant les sources
        fun has(it: FeedItem) = picked.any { p -> p.url == it.url }
        fun regular() = picked.count { it.kind != FeedItem.INSOLITE }
        fun take(theme: String, n: Int) {
            val pool = regularPool.filter { it.theme == theme && !has(it) }
            val bySource = pool.groupBy { it.source }.values.mapNotNull { it.firstOrNull() }.sortedByDescending { it.time }
            (bySource + pool).distinctBy { it.url }.take(n).forEach { picked += it.copy(kind = it.theme) }
        }
        // Les thèmes choisis se partagent les places (alimentation d'abord), puis on complète avec ces mêmes thèmes
        var slot = 0
        while (regular() < PER_DAY && topics.isNotEmpty() && slot < PER_DAY * 3) {
            take(topics[slot % topics.size], 1); slot++
        }
        regularPool.forEach { if (regular() < PER_DAY && !has(it) && it.theme in topics) picked += it.copy(kind = it.theme) }
        seen += picked.map { it.url }
        Repo.putExtra(KEY, JSONObject()
            .put("day", localDay(0)).put("v", VERSION).put("themes", wanted.sorted().joinToString(","))
            .put("items", JSONArray().apply { picked.forEach { put(it.toJson()) } })
            .put("seen", JSONArray(seen.takeLast(400)))
            .toString())
        return picked
    }

    /** Sites dont les articles peuvent être résumés (informations publiques d'établissements publics). */
    private val PUBLIC_HOSTS = setOf("www.anses.fr", "www.santepubliquefrance.fr")

    class NotReusable(message: String) : IOException(message)

    /**
     * Texte d'un article d'organisme public, pour le résumer avec l'IA (réutilisation d'informations publiques,
     * code des relations entre le public et l'administration, art. L321-1 et suivants : source citée, sens respecté).
     * Refusé si l'article est republié d'un autre média (licence Creative Commons, droits d'un tiers).
     */
    suspend fun articleText(url: String): String = withContext(Dispatchers.IO) {
        val u = URL(url)
        if (u.protocol != "https" || u.host !in PUBLIC_HOSTS) throw NotReusable("Résumé possible seulement pour l'Anses et Santé publique France.")
        val conn = (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html")
        }
        val page = try {
            if (conn.responseCode !in 200..299) throw IOException("Article indisponible (${conn.responseCode}).")
            conn.inputStream.use { it.readBytes().take(2_000_000).toByteArray() }.toString(Charsets.UTF_8)
        } catch (e: NotReusable) {
            throw e
        } catch (e: IOException) {
            throw networkError(u.host, e)
        } finally {
            conn.disconnect()
        }
        val main = Regex("<main\\b.*?</main>", RegexOption.DOT_MATCHES_ALL).find(page)?.value
            ?: Regex("<article\\b.*?</article>", RegexOption.DOT_MATCHES_ALL).find(page)?.value ?: page
        val body = main.replace(Regex("<(script|style|nav|header|footer|aside|form)\\b.*?</\\1>", RegexOption.DOT_MATCHES_ALL), " ")
        val paragraphs = Regex("<(p|li|h2|h3)\\b[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(body)
            .map { clean(it.groupValues[2]) }.filter { it.length > 30 }.toList()
        val text = paragraphs.joinToString("\n")
        val lower = text.lowercase()
        if ("creative commons" in lower || "republié à partir de" in lower || "the conversation" in lower) {
            throw NotReusable("Cet article est republié d'un autre média sous une licence qui ne permet pas de le modifier : lis-le directement chez la source.")
        }
        if (text.length < 200) throw IOException("Impossible de lire cet article. Ouvre-le chez la source.")
        text.take(9000)
    }

    /** Thème de l'actu, ou null si elle ne parle ni d'alimentation ni de sport. */
    private fun kindOf(s: Source, text: String, title: String): String? {
        // Sources générales : le sujet doit être dans le titre (sinon trop d'articles hors sujet)
        val where = if (s.kind == null) title else text
        val food = FOOD_WORDS.any { it in where }
        val sport = SPORT_WORDS.any { it in where }
        val health = HEALTH_WORDS.any { it in where }
        val topic = s.kind ?: when {
            food -> FeedItem.FOOD
            sport -> FeedItem.SPORT
            health -> FeedItem.HEALTH
            else -> return null
        }
        // L'actu insolite parle toujours de nourriture ou de sport
        // Mot insolite dans le titre (pas dans une citation) ; pour le flux sport, il faut le mot « insolite » lui-même
        val odd = if (s.kind == FeedItem.SPORT) "insolite" in title
                  else ODD_WORDS.any { it in title.replace(Regex("«[^»]*»|\"[^\"]*\""), " ") }
        return if (odd && topic != FeedItem.HEALTH) FeedItem.INSOLITE + ":" + topic else topic
    }
    private fun excluded(text: String): Boolean = EXCLUDE.any { it in text }

    private fun fetch(s: Source): List<FeedItem> {
        val u = URL(s.url)
        val conn = (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            // L'encodage est lu dans l'en-tête XML (certains flux sont en ISO-8859-1)
            return conn.inputStream.use { parse(it, s) }
        } catch (e: IOException) {
            throw networkError(u.host, e)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(input: java.io.InputStream, s: Source): List<FeedItem> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        val out = mutableListOf<FeedItem>()
        var inItem = false
        var undated = 0
        var title = ""; var desc = ""; var link = ""; var date = ""
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < 40) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "item", "entry" -> { inItem = true; title = ""; desc = ""; link = ""; date = "" }
                    "title" -> if (inItem) title = p.nextText()
                    "description", "summary" -> if (inItem) desc = p.nextText()
                    "link" -> if (inItem && link.isBlank()) {
                        val href = p.getAttributeValue(null, "href")
                        val rel = p.getAttributeValue(null, "rel")
                        if (rel == null || rel == "alternate") link = href ?: p.nextText()
                    }
                    "pubDate", "published", "updated", "dc:date" -> if (inItem && date.isBlank()) date = p.nextText()
                }
                XmlPullParser.END_TAG -> if (p.name == "item" || p.name == "entry") {
                    inItem = false
                    val t = clean(title)
                    val d = clean(desc)
                    val text = " ${t.lowercase()} ${d.lowercase()} "
                    val time = parseDate(date)
                    val kind = kindOf(s, text, " ${t.lowercase()} ")
                    // Sans date : seulement les 5 premiers articles du flux (les plus récents)
                    val dated = time > 0 || (date.isBlank() && undated++ < 5)
                    val keep = !s.insoliteOnly || kind?.startsWith(FeedItem.INSOLITE) == true
                    if (t.isNotBlank() && link.startsWith("https://") && dated && keep && !excluded(text) && kind != null) {
                        out += FeedItem(
                            t.take(160), if (s.press) "" else shorten(d), link.trim(), s.name, if (time > 0) time else NO_DATE,
                            kind.substringBefore(':'), kind.substringAfter(':'), public = !s.press
                        )
                    }
                }
            }
            event = p.next()
        }
        return out
    }

    /** Texte brut : sans HTML ni espaces en trop. */
    private fun clean(s: String): String = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY)
        .toString().replace('￼', ' ').replace(Regex("\\s+"), " ").trim()

    /** Un court extrait seulement (le texte complet reste chez la source). */
    private fun shorten(s: String): String {
        if (s.length <= 180) return s
        val cut = s.take(180)
        return cut.substring(0, cut.lastIndexOf(' ').takeIf { it > 100 } ?: cut.length).trimEnd(',', ';', ':') + "…"
    }

    private fun parseDate(raw: String): Long {
        val s = raw.replace("<![CDATA[", "").replace("]]>", "").trim()
        if (s.isBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (f in formats) {
            runCatching {
                val fmt = SimpleDateFormat(f, Locale.US)
                if (f.endsWith("'Z'")) fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return fmt.parse(s)!!.time
            }
        }
        return 0L
    }
}
