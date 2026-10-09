package com.goodlife.app.dex

import com.goodlife.app.i18n.t

/** Nom de la fonction, à un seul endroit pour pouvoir le changer facilement. */
const val DEX_NAME = "Nutridex"

enum class DexCategory(val label: String) {
    FRUITS(t("Fruits")),
    LEGUMES(t("Légumes")),
    LEGUMINEUSES(t("Légumineuses")),
    FECULENTS(t("Céréales et féculents")),
    PROTEINES(t("Viandes, poissons, œufs")),
    LAITIERS(t("Produits laitiers")),
    GRAINES(t("Fruits à coque et graines")),
    PETIT_DEJ(t("Petit-déjeuner")),
    PLATS_FR(t("Plats français")),
    PLATS_MONDE(t("Plats du monde"))
}

/** Une entrée du Nutridex : [id] stable (ne jamais le changer), nom affiché, émoji pour la silhouette. */
data class DexEntry(val number: Int, val id: String, val name: String, val emoji: String, val category: DexCategory)

/**
 * Catalogue du Nutridex : aliments et plats plutôt bons pour la santé (pas de bonbons, sodas ou fritures),
 * pour donner envie de varier. Un aliment se débloque quand l'IA le reconnaît sur une photo de repas.
 *
 * IMPORTANT : la position de chaque entrée sert à coder le Nutridex dans la carte partagée entre amis
 * (un bit par entrée). Ne jamais supprimer, déplacer ni insérer une entrée au milieu : pour en ajouter,
 * mettre un nouveau bloc c(...) tout à la fin de RAW (la catégorie peut être n'importe laquelle).
 */
object Nutridex {
    private fun c(cat: DexCategory, vararg items: Pair<String, String>) = items.map { Triple(it.first, it.second, cat) }

    private val RAW: List<Triple<String, String, DexCategory>> =
        c(DexCategory.FRUITS,
            "Pomme" to "🍎", "Poire" to "🍐", "Orange" to "🍊", "Clémentine" to "🍊", "Citron" to "🍋",
            "Banane" to "🍌", "Pastèque" to "🍉", "Melon" to "🍈", "Raisin" to "🍇", "Fraise" to "🍓",
            "Framboise" to "🍓", "Cerise" to "🍒", "Pêche" to "🍑", "Abricot" to "🍑", "Mangue" to "🥭",
            "Ananas" to "🍍", "Noix de coco" to "🥥", "Kiwi" to "🥝", "Avocat" to "🥑", "Figue" to "🍐",
            "Grenade" to "🍎", "Prune" to "🍒", "Myrtilles" to "🍇"
        ) + c(DexCategory.LEGUMES,
            "Tomate" to "🍅", "Aubergine" to "🍆", "Brocoli" to "🥦", "Chou-fleur" to "🥦", "Salade verte" to "🥬",
            "Épinards" to "🥬", "Concombre" to "🥒", "Courgette" to "🥒", "Poivron" to "🌶️", "Maïs" to "🌽",
            "Carotte" to "🥕", "Ail" to "🧄", "Oignon" to "🧅", "Pomme de terre" to "🥔", "Patate douce" to "🍠",
            "Champignons" to "🍄", "Potiron" to "🎃", "Haricots verts" to "🥒", "Petits pois" to "🟢",
            "Poireau" to "🥬", "Betterave" to "🟣", "Radis" to "🔴", "Asperges" to "🥒", "Artichaut" to "🥬",
            "Chou" to "🥬", "Fenouil" to "🥬"
        ) + c(DexCategory.LEGUMINEUSES,
            "Lentilles" to "🟤", "Pois chiches" to "🟡", "Haricots rouges" to "🔴", "Haricots blancs" to "⚪",
            "Fèves" to "🟢", "Edamame" to "🟢", "Tofu" to "⬜", "Cacahuètes" to "🥜"
        ) + c(DexCategory.FECULENTS,
            "Riz" to "🍚", "Riz complet" to "🍚", "Pâtes" to "🍝", "Pâtes complètes" to "🍝", "Pain complet" to "🍞",
            "Baguette" to "🥖", "Pain de seigle" to "🍞", "Quinoa" to "🍚", "Boulgour" to "🍚", "Semoule" to "🍚",
            "Sarrasin" to "🍚", "Flocons d'avoine" to "🥣", "Galette de riz" to "🍘", "Tortilla de blé" to "🌯"
        ) + c(DexCategory.PROTEINES,
            "Œuf" to "🥚", "Poulet" to "🍗", "Dinde" to "🍗", "Bœuf" to "🥩", "Porc" to "🥩", "Jambon blanc" to "🍖",
            "Saumon" to "🐟", "Thon" to "🐟", "Sardines" to "🐟", "Maquereau" to "🐟", "Cabillaud" to "🐟",
            "Crevettes" to "🍤", "Moules" to "🦪", "Calamar" to "🦑", "Crabe" to "🦀"
        ) + c(DexCategory.LAITIERS,
            "Lait" to "🥛", "Yaourt nature" to "🥛", "Fromage blanc" to "🥛", "Skyr" to "🥛", "Kéfir" to "🥛",
            "Comté" to "🧀", "Mozzarella" to "🧀", "Chèvre" to "🧀", "Feta" to "🧀", "Emmental" to "🧀"
        ) + c(DexCategory.GRAINES,
            "Amandes" to "🌰", "Noix" to "🌰", "Noisettes" to "🌰", "Noix de cajou" to "🥜", "Pistaches" to "🥜",
            "Graines de chia" to "⚫", "Graines de courge" to "🎃", "Graines de tournesol" to "🌻", "Sésame" to "⚪"
        ) + c(DexCategory.PETIT_DEJ,
            "Porridge" to "🥣", "Muesli" to "🥣", "Granola maison" to "🥣", "Tartine complète" to "🍞",
            "Avocado toast" to "🥑", "Œufs brouillés" to "🍳", "Smoothie" to "🥤", "Pancakes" to "🥞",
            "Fromage blanc aux fruits" to "🍓", "Overnight oats" to "🥣"
        ) + c(DexCategory.PLATS_FR,
            "Ratatouille" to "🍆", "Soupe de légumes" to "🍲", "Velouté de potiron" to "🍲", "Pot-au-feu" to "🍲",
            "Salade niçoise" to "🥗", "Taboulé" to "🥗", "Poulet rôti" to "🍗", "Poisson en papillote" to "🐟",
            "Gratin de légumes" to "🥘", "Omelette" to "🍳", "Galette de sarrasin" to "🥞", "Hachis parmentier" to "🥘",
            "Bœuf bourguignon" to "🍲", "Bouillabaisse" to "🍲", "Salade de chèvre chaud" to "🥗", "Quiche aux légumes" to "🥧",
            "Lentilles aux carottes" to "🍲", "Blanquette" to "🍲", "Crudités" to "🥗", "Endives au jambon" to "🥘"
        ) + c(DexCategory.PLATS_MONDE,
            "Sushi" to "🍣", "Onigiri" to "🍙", "Poke bowl" to "🥗", "Curry" to "🍛", "Dahl" to "🍛",
            "Chili" to "🌶️", "Paella" to "🥘", "Couscous" to "🥘", "Tajine" to "🥘", "Falafel" to "🧆",
            "Houmous" to "🥙", "Pad thaï" to "🍜", "Ramen" to "🍜", "Phở" to "🍜", "Bibimbap" to "🍚",
            "Tacos" to "🌮", "Burrito" to "🌯", "Gaspacho" to "🍅", "Minestrone" to "🍲", "Risotto" to "🍚",
            "Lasagnes" to "🍝", "Shakshuka" to "🍳", "Raviolis vapeur" to "🥟", "Salade grecque" to "🥗",
            "Wok de légumes" to "🥡", "Buddha bowl" to "🥗"
        )

    private fun slug(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace("œ", "oe").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    val ENTRIES: List<DexEntry> = RAW.mapIndexed { i, (name, emoji, cat) -> DexEntry(i + 1, slug(name), t(name), emoji, cat) /* id tiré du nom français : stable dans toutes les langues */ }
    private val BY_ID = ENTRIES.associateBy { it.id }

    fun byId(id: String): DexEntry? = BY_ID[id]

    /** Liste compacte envoyée à l'IA pour qu'elle choisisse les entrées visibles sur la photo. */
    fun promptList(): String = ENTRIES.joinToString(", ") { "${it.id}=${it.name}" }

    // Motif de chaque entrée, des noms les plus longs aux plus courts (« pomme de terre » avant « pomme »)
    private val PATTERNS: List<Pair<String, Regex>> = RAW.map { it.first }.map { name ->
        val words = slug(name).split('-')
        // Pluriel toléré (« tomates », « noix »), mais un nom au pluriel exige son pluriel (« pâtes » ≠ « pâte de cacao »)
        val body = words.joinToString("\\s+") { w -> if (w.endsWith("s") || w.endsWith("x")) w else "${w}[sx]?" }
        slug(name) to Regex("(?<![a-z0-9])$body(?![a-z0-9])")
    }.sortedByDescending { it.first.length }

    /**
     * Entrées du Nutridex présentes dans un produit emballé (code-barres) : d'après son nom et ses premiers
     * ingrédients (les principaux), sans les traces éventuelles. Au plus 4, calculé sur le téléphone, sans IA.
     */
    fun fromProduct(productName: String, ingredients: String): List<String> {
        val main = ingredients.lowercase()
            .substringBefore("peut contenir").substringBefore("traces")
            .split(',', ';', '(', ')', '[', ']').map { it.trim() }.filter { it.isNotBlank() }.take(6)
            .joinToString(" , ")
        var text = " " + java.text.Normalizer.normalize("$productName , $main".lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace("œ", "oe").replace(Regex("[^a-z0-9]+"), " ") + " "
        val found = mutableListOf<String>()
        for ((id, re) in PATTERNS) {
            if (found.size >= 4) break
            val m = re.find(text) ?: continue
            found += id
            // Le passage trouvé est effacé : « pomme de terre » ne débloque pas aussi « pomme »
            text = text.replace(re, " ")
            if (m.value.isEmpty()) break
        }
        return found.filter { byId(it) != null }
    }
}
