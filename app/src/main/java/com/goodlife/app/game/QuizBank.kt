package com.goodlife.app.game

import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

/** Question : la première réponse de [answers] est la bonne (l'ordre est mélangé à l'affichage). */
data class QuizQuestion(val question: String, val answers: List<String>, val explanation: String)

data class DailyQuestion(val question: String, val options: List<String>, val correctIndex: Int, val explanation: String)

object QuizBank {
    const val PER_DAY = 5
    const val PASS = 4

    private val Q = listOf(
        QuizQuestion("Combien de kcal apporte 1 g de lipides (graisses) ?", listOf("9 kcal", "4 kcal", "7 kcal", "12 kcal"),
            "Les lipides apportent 9 kcal par gramme, plus du double des glucides et des protéines."),
        QuizQuestion("Combien de kcal apporte 1 g de protéines ?", listOf("4 kcal", "9 kcal", "2 kcal", "7 kcal"),
            "Protéines et glucides apportent environ 4 kcal par gramme."),
        QuizQuestion("Combien de kcal apporte 1 g d'alcool ?", listOf("7 kcal", "0 kcal", "4 kcal", "2 kcal"),
            "L'alcool apporte 7 kcal par gramme, sans aucun nutriment utile."),
        QuizQuestion("Lequel de ces aliments contient le plus de protéines pour 100 g ?", listOf("Blanc de poulet cuit", "Riz cuit", "Pomme", "Carotte"),
            "Le blanc de poulet cuit contient environ 30 g de protéines pour 100 g, contre moins de 3 g pour le riz cuit."),
        QuizQuestion("Quelle vitamine la peau fabrique-t-elle grâce au soleil ?", listOf("Vitamine D", "Vitamine C", "Vitamine B12", "Vitamine K"),
            "Les rayons UVB permettent à la peau de produire de la vitamine D, utile aux os."),
        QuizQuestion("Lequel de ces fruits est très riche en vitamine C ?", listOf("Kiwi", "Banane", "Poire", "Datte"),
            "Un kiwi couvre à lui seul une grande partie des besoins quotidiens en vitamine C."),
        QuizQuestion("Combien de portions de fruits et légumes par jour recommande-t-on en France ?", listOf("Au moins 5", "1", "2", "10 minimum"),
            "La recommandation officielle est d'au moins 5 portions de fruits et légumes par jour."),
        QuizQuestion("Quel minéral est surtout apporté par les produits laitiers ?", listOf("Calcium", "Fer", "Iode", "Zinc"),
            "Les produits laitiers sont une source majeure de calcium."),
        QuizQuestion("Quel aliment contient du fer « héminique », le mieux absorbé ?", listOf("Viande rouge", "Épinards", "Lentilles", "Pain complet"),
            "Le fer des viandes et poissons (héminique) est bien mieux absorbé que celui des végétaux."),
        QuizQuestion("Pour mieux absorber le fer des lentilles, on les associe à…", listOf("Une source de vitamine C", "Du thé", "Du café", "Du lait"),
            "La vitamine C (poivron, agrumes…) améliore l'absorption du fer végétal ; thé et café la freinent."),
        QuizQuestion("Quelle boisson gêne l'absorption du fer si on la boit pendant le repas ?", listOf("Le thé", "L'eau", "Le jus d'orange", "L'eau gazeuse"),
            "Les tanins du thé réduisent l'absorption du fer végétal : mieux vaut le boire à distance des repas."),
        QuizQuestion("Combien de boisson (surtout de l'eau) conseille-t-on environ par jour à un adulte ?", listOf("Environ 1,5 L", "0,3 L", "5 L", "Aucune, l'alimentation suffit"),
            "Environ 1,5 L de boisson par jour est recommandé, plus en cas de chaleur ou de sport."),
        QuizQuestion("Combien de kcal apporte environ un œuf moyen ?", listOf("Environ 75 kcal", "Environ 15 kcal", "Environ 250 kcal", "Environ 400 kcal"),
            "Un œuf moyen (50 à 60 g) apporte environ 70 à 80 kcal et 6 à 7 g de protéines."),
        QuizQuestion("Lequel de ces aliments a l'index glycémique le plus élevé ?", listOf("Pain blanc", "Lentilles", "Pomme", "Yaourt nature"),
            "Le pain blanc fait monter la glycémie rapidement ; lentilles et pommes beaucoup moins."),
        QuizQuestion("Quels poissons sont les plus riches en oméga-3 ?", listOf("Les poissons gras (sardine, saumon, maquereau)", "Les poissons panés", "Le cabillaud", "Le surimi"),
            "Les poissons gras sont la meilleure source d'oméga-3 à longue chaîne."),
        QuizQuestion("Quelle quantité maximale de sel par jour l'OMS recommande-t-elle ?", listOf("Moins de 5 g", "Moins de 15 g", "Moins de 25 g", "Pas de limite"),
            "L'OMS recommande moins de 5 g de sel par jour, soit environ une cuillère à café."),
        QuizQuestion("Comment s'appelle le sucre naturellement présent dans le lait ?", listOf("Lactose", "Fructose", "Saccharose", "Maltose"),
            "Le lactose est le sucre du lait ; certaines personnes le digèrent mal."),
        QuizQuestion("Dans quelle céréale trouve-t-on du gluten ?", listOf("Le blé", "Le riz", "Le maïs", "Le quinoa"),
            "Le gluten est présent dans le blé, l'orge, le seigle ; pas dans le riz, le maïs ou le quinoa."),
        QuizQuestion("Lequel est le plus calorique pour 100 g ?", listOf("Huile d'olive", "Beurre", "Chocolat noir", "Emmental"),
            "Une huile est 100 % de lipides : environ 900 kcal pour 100 g, contre environ 740 pour le beurre."),
        QuizQuestion("Combien de sucre contient environ une canette de soda classique (33 cl) ?", listOf("Environ 35 g", "Environ 3 g", "Environ 10 g", "Environ 100 g"),
            "Une canette de soda classique contient environ 35 g de sucre, soit à peu près 7 morceaux."),
        QuizQuestion("Le bêta-carotène des carottes est transformé par le corps en…", listOf("Vitamine A", "Vitamine C", "Vitamine D", "Vitamine B9"),
            "Le bêta-carotène est un précurseur de la vitamine A, importante pour la vision."),
        QuizQuestion("À quoi servent surtout les protéines ?", listOf("Construire et réparer les muscles et les tissus", "Donner du goût sucré", "Stocker l'eau", "Remplacer les vitamines"),
            "Les protéines sont les briques du corps : muscles, peau, enzymes, anticorps…"),
        QuizQuestion("L'avocat est surtout riche en…", listOf("Bonnes graisses (mono-insaturées)", "Sucres rapides", "Protéines", "Vitamine B12"),
            "L'avocat contient surtout des graisses mono-insaturées, comme l'huile d'olive."),
        QuizQuestion("Lequel n'est PAS une légumineuse ?", listOf("Le riz", "Les lentilles", "Les pois chiches", "Les haricots rouges"),
            "Le riz est une céréale ; lentilles, pois chiches et haricots sont des légumineuses."),
        QuizQuestion("Quel fruit est une bonne source de potassium ?", listOf("La banane", "La fraise", "Le citron", "La pastèque"),
            "La banane est connue pour son apport en potassium, utile aux muscles."),
        QuizQuestion("Quelle vitamine est presque uniquement apportée par les produits animaux ?", listOf("Vitamine B12", "Vitamine C", "Vitamine K", "Vitamine E"),
            "La vitamine B12 vient des produits animaux : les personnes végétaliennes doivent se supplémenter."),
        QuizQuestion("Le quinoa est…", listOf("Une graine riche en protéines", "Une céréale sans protéines", "Un légume vert", "Une algue"),
            "Le quinoa est une graine (pseudo-céréale) plutôt riche en protéines et sans gluten."),
        QuizQuestion("Quel mode de cuisson préserve le mieux les vitamines des légumes ?", listOf("La vapeur", "La friture", "L'ébullition longue", "Le micro-ondes à pleine puissance 30 min"),
            "La cuisson vapeur limite la perte des vitamines qui partent dans l'eau de cuisson."),
        QuizQuestion("Lequel est un aliment ultra-transformé ?", listOf("Nuggets industriels", "Pomme", "Lentilles sèches", "Œuf"),
            "Les produits ultra-transformés contiennent souvent additifs et ingrédients industriels ; mieux vaut les limiter."),
        QuizQuestion("Combien de temps faut-il environ pour ressentir la satiété après avoir commencé à manger ?", listOf("Environ 20 minutes", "10 secondes", "2 minutes", "2 heures"),
            "Le signal de satiété met environ 20 minutes à arriver : manger lentement aide à mieux s'écouter."),
        QuizQuestion("À combien de kcal correspond environ 1 kg de graisse corporelle ?", listOf("Environ 7 700 kcal", "Environ 1 000 kcal", "Environ 3 000 kcal", "Environ 20 000 kcal"),
            "On estime souvent 1 kg de graisse corporelle à environ 7 700 kcal : les changements durables se font progressivement."),
        QuizQuestion("Qu'est-ce qui contient le plus de vitamine C pour 100 g ?", listOf("Le poivron rouge", "L'orange", "La pomme", "Le concombre"),
            "Le poivron rouge contient environ deux fois plus de vitamine C que l'orange."),
        QuizQuestion("Quel fromage est le plus riche en calcium ?", listOf("Le parmesan", "Le fromage blanc", "La mozzarella", "Le fromage frais à tartiner"),
            "Les fromages à pâte dure comme le parmesan sont les plus riches en calcium."),
        QuizQuestion("Quelle association donne des protéines végétales complètes ?", listOf("Céréales + légumineuses", "Fruits + légumes", "Huile + vinaigre", "Pain + confiture"),
            "Associer céréales et légumineuses (riz + lentilles, semoule + pois chiches) complète les acides aminés."),
        QuizQuestion("Le miel est principalement composé de…", listOf("Sucres", "Protéines", "Graisses", "Fibres"),
            "Le miel est surtout du glucose et du fructose : c'est un sucre, à consommer avec modération."),
        QuizQuestion("Quel pain apporte le plus de fibres ?", listOf("Le pain complet", "La baguette blanche", "La brioche", "Le pain de mie blanc"),
            "Le pain complet garde l'enveloppe du grain, riche en fibres."),
        QuizQuestion("Qu'est-ce qui est préférable pour la santé ?", listOf("Le fruit entier", "Le jus de fruit", "Le sirop de fruit", "Le soda au fruit"),
            "Le fruit entier garde ses fibres et rassasie plus que le jus."),
        QuizQuestion("Quelle quantité maximale de viande rouge par semaine est recommandée en France ?", listOf("Environ 500 g", "Environ 100 g", "Environ 1,5 kg", "Pas de limite"),
            "Il est conseillé de ne pas dépasser environ 500 g de viande rouge par semaine."),
        QuizQuestion("Combien de charcuterie par semaine au maximum est conseillé en France ?", listOf("Environ 150 g", "Environ 1 kg", "Environ 600 g", "Pas de limite"),
            "La charcuterie est à limiter à environ 150 g par semaine (sel, graisses, nitrites)."),
        QuizQuestion("Combien de fois par semaine est-il conseillé de manger du poisson ?", listOf("2 fois, dont 1 poisson gras", "1 fois par mois", "Tous les repas", "Jamais"),
            "Deux portions de poisson par semaine, dont un poisson gras, sont recommandées."),
        QuizQuestion("Combien de fois par semaine conseille-t-on de manger des légumineuses ?", listOf("Au moins 2 fois", "Jamais", "1 fois par an", "Uniquement le dimanche"),
            "Lentilles, pois chiches et haricots sont conseillés au moins deux fois par semaine."),
        QuizQuestion("Quelle portion de fruits à coque (noix, amandes…) est conseillée ?", listOf("Une petite poignée par jour", "Un paquet de 500 g par jour", "Jamais", "Une noix par an"),
            "Une petite poignée par jour de fruits à coque non salés est bénéfique."),
        QuizQuestion("Combien de produits laitiers par jour sont conseillés à un adulte en France ?", listOf("2", "0", "6", "10"),
            "La recommandation actuelle pour un adulte est de 2 produits laitiers par jour."),
        QuizQuestion("Quel est le carburant principal du cerveau ?", listOf("Le glucose", "Les protéines", "Le sel", "Les fibres"),
            "Le cerveau utilise surtout du glucose, apporté notamment par les féculents."),
        QuizQuestion("Combien de kcal apporte environ une banane moyenne ?", listOf("Environ 90 à 100 kcal", "Environ 10 kcal", "Environ 300 kcal", "Environ 500 kcal"),
            "Une banane moyenne apporte environ 90 à 100 kcal, surtout sous forme de glucides."),
        QuizQuestion("Combien de kcal apportent environ 100 g de riz blanc cuit ?", listOf("Environ 130 kcal", "Environ 30 kcal", "Environ 350 kcal", "Environ 600 kcal"),
            "Cru, le riz fait environ 350 kcal pour 100 g ; cuit, il absorbe de l'eau et descend à environ 130 kcal."),
        QuizQuestion("Avec quoi fabrique-t-on le tofu ?", listOf("Le soja", "Le riz", "Le lait de vache", "Le blé"),
            "Le tofu est fait à partir de lait de soja caillé : c'est une bonne source de protéines végétales."),
        QuizQuestion("Quel est l'ingrédient de base du houmous ?", listOf("Les pois chiches", "Les lentilles", "Les haricots verts", "Les petits pois"),
            "Le houmous est une purée de pois chiches, avec tahini, citron et huile d'olive."),
        QuizQuestion("Botaniquement, la tomate est…", listOf("Un fruit", "Une racine", "Une feuille", "Une céréale"),
            "La tomate est botaniquement un fruit, même si on la cuisine comme un légume."),
        QuizQuestion("Lequel de ces aliments est fermenté ?", listOf("Le yaourt", "Le riz blanc", "La pomme", "L'huile d'olive"),
            "Le yaourt est obtenu par fermentation du lait grâce à des bactéries lactiques."),
        QuizQuestion("Les légumes verts à feuilles (épinards, chou kale) sont riches en…", listOf("Vitamine K", "Vitamine B12", "Vitamine D", "Caféine"),
            "La vitamine K, utile à la coagulation, est abondante dans les légumes verts à feuilles."),
        QuizQuestion("Quelle part environ du corps d'un adulte est constituée d'eau ?", listOf("Environ 60 %", "Environ 10 %", "Environ 30 %", "Environ 95 %"),
            "Le corps d'un adulte contient environ 60 % d'eau : bien s'hydrater est essentiel."),
        QuizQuestion("Lequel de ces aliments contient souvent beaucoup de sel « caché » ?", listOf("La charcuterie", "La pomme", "Le riz nature", "Le yaourt nature"),
            "Charcuterie, pain, plats préparés et fromages sont les principales sources de sel caché."),
        QuizQuestion("Quel apport en protéines est conseillé environ par jour à un adulte en bonne santé ?", listOf("Environ 0,8 g par kg de poids", "Environ 0,1 g par kg", "Environ 5 g par kg", "Environ 10 g par kg"),
            "La référence pour un adulte est d'environ 0,8 g de protéines par kg de poids corporel et par jour."),
        QuizQuestion("Par rapport au lait de vache, la boisson à l'avoine est naturellement…", listOf("Moins riche en protéines", "Plus riche en protéines", "Plus riche en calcium naturel", "Identique"),
            "Les boissons à l'avoine contiennent peu de protéines ; le calcium y est souvent ajouté."),
        QuizQuestion("Le chocolat noir à 70 % de cacao, comparé au chocolat au lait, contient…", listOf("Plus de cacao et moins de sucre", "Plus de sucre", "Plus de lait", "Aucune calorie"),
            "Le chocolat noir contient plus de cacao et moins de sucre, mais reste calorique."),
        QuizQuestion("Lesquels sont des féculents ?", listOf("Pâtes, riz, pommes de terre", "Tomates, concombres", "Pommes, poires", "Yaourts, fromages"),
            "Les féculents (pâtes, riz, pain, pommes de terre…) apportent l'énergie des glucides complexes."),
        QuizQuestion("Les fibres alimentaires aident surtout…", listOf("Le transit et la satiété", "À bronzer", "À dormir moins", "À augmenter le sel"),
            "Les fibres favorisent un bon transit et prolongent la sensation de satiété."),
        QuizQuestion("Quelle graine est particulièrement riche en oméga-3 végétaux ?", listOf("Les graines de lin", "Les graines de tournesol grillées salées", "Le riz", "Le maïs"),
            "Les graines de lin (moulues) et l'huile de colza sont riches en oméga-3 d'origine végétale."),
        QuizQuestion("Un petit-déjeuner équilibré peut contenir…", listOf("Pain complet, yaourt et un fruit", "Soda et bonbons", "Uniquement du café", "Chips et biscuits"),
            "Un féculent complet, un produit laitier et un fruit forment une base équilibrée.")
    )

    private fun dayIndex(date: Calendar): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH))
        }
        return utc.timeInMillis / 86_400_000L
    }

    /**
     * Quiz du jour : 5 questions différentes chaque jour. On parcourt la banque dans un ordre mélangé ;
     * à chaque tour complet, un nouveau mélange est utilisé, donc les journées ne se répètent jamais à l'identique.
     */
    fun forDay(date: Calendar = Calendar.getInstance()): List<DailyQuestion> {
        val n = Q.size
        val day = dayIndex(date)
        val start = day * PER_DAY
        val random = Random(day * 7919L + 17)
        return (0 until PER_DAY).map { i ->
            val pos = start + i
            val cycle = pos / n
            val order = Q.indices.shuffled(Random(cycle * 104729L + 31))
            val q = Q[order[(pos % n).toInt()]]
            val opts = q.answers.shuffled(random)
            DailyQuestion(q.question, opts, opts.indexOf(q.answers[0]), q.explanation)
        }
    }

    val size: Int get() = Q.size
}
