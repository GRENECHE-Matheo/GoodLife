package com.goodlife.app.news

import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

/** Une anecdote : titre court + explication. */
data class Anecdote(val title: String, val text: String)

data class DailyNews(val insolite: Anecdote, val discoveries: List<Anecdote>)

/**
 * « Actus du jour » : chaque jour, une anecdote insolite et deux découvertes sur l'alimentation.
 * Tout est intégré à l'app (aucun réseau, aucune IA) et choisi pour être vrai et vérifiable ;
 * les points discutés par les scientifiques sont présentés comme tels.
 */
object NewsBank {
    private val INSOLITE = listOf(
        Anecdote("Le miel qui traverse les siècles", "Grâce à son très faible taux d'eau et à son acidité, le miel se conserve presque indéfiniment. Des pots de miel retrouvés dans des tombes égyptiennes vieilles de plus de 3 000 ans étaient encore considérés comme comestibles."),
        Anecdote("La banane, un peu radioactive", "La banane contient du potassium, dont une infime partie est du potassium 40, naturellement radioactif. C'est totalement sans danger : notre corps en contient déjà autant et l'élimine en permanence."),
        Anecdote("La fraise n'est pas une baie… mais la banane oui", "Pour les botanistes, les vrais fruits de la fraise sont les petits grains à sa surface (les akènes). La banane, la tomate et même l'aubergine sont, elles, de vraies baies."),
        Anecdote("Des carottes violettes", "Les premières carottes cultivées étaient violettes ou jaunes. La carotte orange s'est imposée plus tard, notamment grâce aux sélections des maraîchers néerlandais au XVIIe siècle."),
        Anecdote("Le ketchup, un médicament ?", "Dans les années 1830, aux États-Unis, des extraits de tomate ont été vendus sous forme de pilules présentées comme un remède contre l'indigestion. La mode n'a pas duré."),
        Anecdote("La cacahuète n'est pas une noix", "La cacahuète est une légumineuse, comme les lentilles et les pois chiches. Ses gousses mûrissent… sous terre."),
        Anecdote("Pourquoi le piment brûle", "La capsaïcine du piment active les capteurs de chaleur de la bouche : le cerveau croit à une brûlure. Les oiseaux, eux, n'y sont pas sensibles, ce qui les aide à disperser les graines."),
        Anecdote("La pomme qui flotte", "Une pomme flotte sur l'eau parce qu'environ un quart de son volume est constitué d'air. C'est ce qui rend possible le jeu de la pêche aux pommes."),
        Anecdote("La noix de cajou et sa pomme", "La noix de cajou pousse au bout d'un « faux fruit », la pomme de cajou. Sa coque contient une substance irritante : c'est pour ça qu'on ne la trouve jamais vendue dans sa coque."),
        Anecdote("La vanille, une orchidée pollinisée à la main", "La vanille est le fruit d'une orchidée. En 1841, à La Réunion, un jeune esclave de 12 ans, Edmond Albius, a mis au point la technique de pollinisation à la main encore utilisée aujourd'hui."),
        Anecdote("Le chocolat blanc sans cacao… ou presque", "Le chocolat blanc ne contient pas de pâte de cacao : seulement du beurre de cacao, du sucre et du lait. C'est pour ça qu'il n'a pas le goût du chocolat noir."),
        Anecdote("Une seule plante, cinq légumes", "Chou-fleur, brocoli, chou de Bruxelles, chou kale et chou-rave appartiennent à la même espèce végétale. Les agriculteurs ont simplement sélectionné des parties différentes de la plante."),
        Anecdote("L'avocat mûrit après la cueillette", "Sur l'arbre, l'avocat reste dur. Il ne commence à mûrir qu'une fois cueilli : les producteurs peuvent donc le laisser sur l'arbre plusieurs mois."),
        Anecdote("Les pistaches qui chauffent", "En très grande quantité, les pistaches peuvent s'échauffer toutes seules et même s'enflammer. Elles sont classées marchandises dangereuses pour le transport maritime."),
        Anecdote("L'ananas qui « mange » la bouche", "L'ananas contient la broméline, une enzyme qui découpe les protéines. C'est elle qui picote la langue, et qui empêche une gelée de prendre si on y met de l'ananas cru."),
        Anecdote("La pomme de terre dans l'espace", "En 1995, la pomme de terre est devenue le premier légume cultivé dans l'espace, lors d'une mission de la navette américaine Columbia."),
        Anecdote("Le cinquième goût", "En 1908, le chimiste japonais Kikunae Ikeda a identifié l'umami, le goût savoureux du bouillon, de la tomate mûre ou du parmesan. C'est le cinquième goût, avec le sucré, le salé, l'acide et l'amer."),
        Anecdote("Pourquoi l'oignon fait pleurer", "En coupant un oignon, on casse ses cellules : elles libèrent un gaz qui irrite les yeux. Un oignon bien froid et un couteau bien aiguisé limitent les larmes."),
        Anecdote("Lait de coco ou eau de coco ?", "L'eau de coco est le liquide présent dans la noix. Le lait de coco, lui, est fabriqué en pressant la chair râpée avec de l'eau : il est beaucoup plus riche."),
        Anecdote("Des milliers de pommes différentes", "Il existe plusieurs milliers de variétés de pommes dans le monde, alors qu'on n'en trouve qu'une dizaine dans la plupart des supermarchés."),
        Anecdote("Le kiwi s'appelait « groseille de Chine »", "Originaire de Chine, le kiwi a été rebaptisé par les exportateurs néo-zélandais au milieu du XXe siècle, en clin d'œil à l'oiseau emblème de leur pays."),
        Anecdote("La baguette à l'UNESCO", "En 2022, les savoir-faire artisanaux et la culture de la baguette de pain ont été inscrits au patrimoine culturel immatériel de l'humanité par l'UNESCO."),
        Anecdote("Le repas à la française, patrimoine mondial", "Depuis 2010, le « repas gastronomique des Français » est inscrit au patrimoine culturel immatériel de l'UNESCO : un moment de partage autour de la table."),
        Anecdote("Le safran, l'épice la plus chère", "Il faut environ 150 000 fleurs de crocus, récoltées à la main, pour obtenir un kilo de safran. D'où son prix très élevé."),
        Anecdote("L'amande, cousine de la cerise", "L'amandier fait partie de la même famille que le pêcher et le cerisier. L'amande que l'on mange est la graine cachée dans le noyau de son fruit."),
        Anecdote("La pastèque, presque de l'eau", "La pastèque est composée d'environ 92 % d'eau. Le concombre fait encore mieux, avec environ 95 %."),
        Anecdote("La tomate, longtemps suspecte", "Arrivée d'Amérique au XVIe siècle, la tomate a longtemps été regardée avec méfiance en Europe, car elle est de la même famille que certaines plantes toxiques. Elle a d'abord servi de plante décorative."),
        Anecdote("Le pain rassit plus vite au frigo", "Au réfrigérateur, l'amidon du pain durcit plus vite qu'à température ambiante. Pour le garder longtemps, mieux vaut le congeler, puis le passer au four."),
        Anecdote("Les épinards de Popeye", "Les épinards contiennent du fer, mais notre corps en absorbe peu : une partie est bloquée par des composés appelés oxalates. Popeye aurait eu plus de succès avec des lentilles et un peu de vitamine C."),
        Anecdote("Le riz qui refroidit devient « résistant »", "Quand du riz ou des pommes de terre cuits refroidissent, une partie de leur amidon devient « résistant » : il se comporte un peu comme des fibres. Le réchauffer ensuite n'annule pas tout.")
    )

    private val DECOUVERTES = listOf(
        Anecdote("Lentilles + riz, le duo gagnant", "Les légumineuses et les céréales ont des protéines complémentaires : associées dans la journée (lentilles et riz, pois chiches et semoule…), elles apportent tous les acides aminés essentiels."),
        Anecdote("30 g de fibres par jour", "L'Anses recommande environ 30 g de fibres par jour pour un adulte. Fruits, légumes, légumineuses et céréales complètes sont les meilleures sources."),
        Anecdote("Manger lentement rassasie mieux", "Le signal de satiété met une vingtaine de minutes à arriver au cerveau. Poser ses couverts entre deux bouchées aide à manger à sa faim, sans excès."),
        Anecdote("Les légumes surgelés, de bons alliés", "Surgelés juste après la récolte, les légumes nature gardent bien leurs vitamines. C'est une solution pratique et souvent moins chère, surtout hors saison."),
        Anecdote("Au moins 5 fruits et légumes", "Le Programme national nutrition santé recommande au moins 5 portions de fruits et légumes par jour. Une portion, c'est à peu près le volume de son poing."),
        Anecdote("Des légumineuses deux fois par semaine", "Lentilles, pois chiches, haricots secs : le PNNS conseille d'en manger au moins deux fois par semaine. Elles apportent protéines, fibres et fer, pour un prix modeste."),
        Anecdote("La vapeur préserve les vitamines", "La vitamine C se dégrade avec la chaleur et passe dans l'eau de cuisson. Une cuisson courte à la vapeur préserve mieux les vitamines des légumes que l'ébullition."),
        Anecdote("Fer + vitamine C", "Le fer des végétaux est mieux absorbé en présence de vitamine C. Un filet de citron sur des lentilles ou des poivrons dans un plat de pois chiches font une vraie différence."),
        Anecdote("Le thé après, pas pendant", "Le thé et le café pris pendant le repas diminuent l'absorption du fer des végétaux. Si tu manques de fer, décale-les d'une heure."),
        Anecdote("Moins de 5 g de sel par jour", "L'Organisation mondiale de la santé recommande moins de 5 g de sel par jour, soit environ une cuillère à café. La plupart vient du pain, des plats préparés et de la charcuterie."),
        Anecdote("Les sucres ajoutés", "L'OMS conseille de limiter les sucres ajoutés à moins de 10 % des apports caloriques, idéalement 5 %. Une canette de soda en contient souvent déjà l'équivalent de 7 morceaux de sucre."),
        Anecdote("Le sommeil et la faim", "Des études montrent que les nuits trop courtes augmentent l'appétit, en particulier pour les aliments sucrés et gras. Bien dormir aide aussi à bien manger."),
        Anecdote("Marcher après le repas", "Des études montrent qu'une marche de 10 à 15 minutes après un repas aide à limiter la hausse du sucre dans le sang. Une bonne excuse pour faire quelques pas de plus."),
        Anecdote("Pain complet ou pain blanc ?", "Le pain complet garde l'enveloppe du grain : il contient plus de fibres, de minéraux et de vitamines que le pain blanc, et il rassasie plus longtemps."),
        Anecdote("Le poisson gras une fois par semaine", "Le PNNS recommande du poisson deux fois par semaine, dont un poisson gras (sardine, maquereau, hareng). Il apporte des oméga-3 utiles au cœur et au cerveau."),
        Anecdote("Une petite poignée de fruits à coque", "Amandes, noix, noisettes non salées : une petite poignée par jour est recommandée par le PNNS. Elles apportent de bonnes graisses, des fibres et des minéraux."),
        Anecdote("Les pâtes al dente", "Des pâtes cuites al dente font monter moins vite le sucre dans le sang que des pâtes trop cuites. En plus, elles gardent une meilleure texture."),
        Anecdote("Boire aussi en mangeant", "Une partie de l'eau dont le corps a besoin vient des aliments, surtout des fruits et légumes. Le reste vient des boissons : l'eau est la seule indispensable."),
        Anecdote("Les protéines rassasient", "À calories égales, les protéines rassasient davantage que les glucides ou les lipides. Un petit-déjeuner avec un yaourt ou un œuf tient souvent plus longtemps au corps."),
        Anecdote("Un jus n'est pas un fruit", "Un jus de fruits contient le sucre du fruit mais presque plus ses fibres. Le PNNS conseille de ne pas dépasser un petit verre par jour et de préférer les fruits entiers."),
        Anecdote("Les herbes pour moins saler", "Herbes aromatiques, épices, ail, citron : relever un plat avec eux permet de réduire le sel sans perdre en goût. Le palais s'habitue en quelques semaines."),
        Anecdote("La banane verte ou mûre", "Plus une banane mûrit, plus son amidon se transforme en sucres simples. Une banane encore un peu verte rassasie plus longtemps."),
        Anecdote("Le Nutri-Score", "Le Nutri-Score classe les aliments de A (vert) à E (rouge) selon leur qualité nutritionnelle. Il sert surtout à comparer des produits d'un même rayon."),
        Anecdote("Les fruits et légumes de saison", "En saison, fruits et légumes sont souvent moins chers, plus mûrs et plus goûteux. Un calendrier des saisons aide à remplir son panier sans se ruiner."),
        Anecdote("Le yaourt vivant", "Le yaourt est fabriqué grâce à deux ferments lactiques vivants, qui transforment une partie du lactose du lait. C'est pour ça qu'il est souvent mieux toléré que le lait."),
        Anecdote("Les œufs, une protéine complète", "L'œuf contient tous les acides aminés essentiels dans de bonnes proportions. C'est l'une des sources de protéines les moins chères."),
        Anecdote("L'eau, à volonté", "L'eau est la seule boisson indispensable. Le PNNS recommande d'en boire à volonté, pendant et entre les repas."),
        Anecdote("Mâcher, c'est déjà digérer", "La salive contient une enzyme qui commence à digérer l'amidon dès la bouche. Bien mâcher facilite la digestion et aide à sentir la satiété."),
        Anecdote("Les légumes crus et cuits", "Certains nutriments sont mieux absorbés crus (vitamine C), d'autres cuits : le lycopène de la tomate, par exemple, est plus disponible après cuisson avec un peu d'huile."),
        Anecdote("Le petit-déjeuner n'est pas obligatoire", "Il n'y a pas de règle unique : l'important est l'équilibre sur la journée. Si tu n'as pas faim le matin, une collation saine plus tard fonctionne aussi."),
        Anecdote("L'activité compte aussi", "L'OMS recommande au moins 150 minutes d'activité physique modérée par semaine pour un adulte, comme 30 minutes de marche rapide, 5 jours par semaine."),
        Anecdote("Les couleurs dans l'assiette", "Des fruits et légumes de couleurs variées apportent des vitamines et des antioxydants différents. Une assiette colorée est souvent une assiette équilibrée.")
    )

    private fun dayIndex(date: Calendar): Long {
        val c = date.clone() as Calendar
        c.timeZone = TimeZone.getDefault()
        return (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
    }

    /** Toujours le même contenu pour une journée donnée ; pas de répétition avant d'avoir tout vu. */
    fun forDay(date: Calendar = Calendar.getInstance()): DailyNews {
        val day = dayIndex(date)
        fun pick(list: List<Anecdote>, pos: Long, salt: Long): Anecdote {
            val cycle = pos / list.size
            val order = list.indices.shuffled(Random(cycle * 7919L + salt))
            return list[order[(pos % list.size).toInt()]]
        }
        return DailyNews(
            insolite = pick(INSOLITE, day, 11),
            discoveries = listOf(pick(DECOUVERTES, day * 2, 23), pick(DECOUVERTES, day * 2 + 1, 23))
        )
    }
}
