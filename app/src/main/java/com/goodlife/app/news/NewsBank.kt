package com.goodlife.app.news

import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

/** Une anecdote : titre court + explication. */
data class Anecdote(val title: String, val text: String)

data class DailyNews(val insolite: Anecdote, val discoveries: List<Anecdote>)

/**
 * « Actus du jour » : chaque jour, une anecdote insolite et une découverte sur l'alimentation et le sport.
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
        Anecdote("Le riz qui refroidit devient « résistant »", "Quand du riz ou des pommes de terre cuits refroidissent, une partie de leur amidon devient « résistant » : il se comporte un peu comme des fibres. Le réchauffer ensuite n'annule pas tout."),
        // Ajoutées en v0.9
        Anecdote("Cher comme poivre", "Au Moyen Âge, le poivre était si précieux qu'il servait parfois à payer des taxes ou des loyers. L'expression « cher comme poivre » vient de là."),
        Anecdote("Le croissant vient d'Autriche", "Le croissant descend du kipferl, une viennoiserie autrichienne. C'est en France, au XIXe siècle puis au début du XXe, qu'il est devenu la pâte feuilletée au beurre qu'on connaît."),
        Anecdote("Le brocoli, un bouquet de fleurs", "Quand on mange du brocoli, on mange en réalité des boutons de fleurs qui n'ont pas encore éclos. Si on le laisse pousser, il se couvre de petites fleurs jaunes."),
        Anecdote("Les câpres, des boutons de fleurs", "Les câpres sont les boutons floraux du câprier, cueillis avant d'éclore puis conservés dans le vinaigre ou le sel."),
        Anecdote("Le clou de girofle, une fleur séchée", "Le clou de girofle est le bouton de fleur séché du giroflier. Sa forme de petit clou lui a donné son nom."),
        Anecdote("Le cornichon, un petit concombre", "Le cornichon appartient à la même espèce que le concombre. On choisit des variétés adaptées et on les cueille très jeunes."),
        Anecdote("Le poivron vert n'est pas mûr", "La plupart des poivrons verts sont simplement cueillis avant maturité. En mûrissant, ils deviennent jaunes, orange ou rouges, et plus sucrés."),
        Anecdote("Le citron vert n'est pas un citron pas mûr", "Le citron vert (la lime) est une autre espèce d'agrume que le citron jaune. Il reste vert même bien mûr."),
        Anecdote("Les champignons, cousins des animaux", "Génétiquement, les champignons sont plus proches des animaux que des plantes. Ils ne font pas de photosynthèse et forment un règne à part."),
        Anecdote("Des chiens chercheurs de truffes", "On cherchait autrefois les truffes avec des cochons, attirés par leur odeur. Aujourd'hui, on préfère les chiens : ils ne mangent pas la truffe une fois trouvée."),
        Anecdote("Le roquefort, premier fromage protégé", "En 1925, le roquefort est devenu le premier fromage français protégé par une appellation d'origine. Il s'affine dans les caves du Combalou, à Roquefort-sur-Soulzon."),
        Anecdote("La boîte du camembert", "Vers 1890, l'invention de la boîte ronde en bois de peuplier a permis au camembert de voyager loin de la Normandie sans s'abîmer."),
        Anecdote("Le sandwich et le comte", "Le sandwich tient son nom de John Montagu, 4e comte de Sandwich, au XVIIIe siècle. Selon la légende, il voulait manger sans quitter sa table de jeu."),
        Anecdote("Le chocolat, d'abord une boisson", "Chez les Mayas et les Aztèques, le cacao se buvait, souvent amer et épicé. Chez les Aztèques, les fèves de cacao servaient même de monnaie."),
        Anecdote("Le sucre de betterave et Napoléon", "Privée du sucre de canne des colonies par le blocus continental, la France de Napoléon a encouragé la production de sucre de betterave, mise au point en 1812."),
        Anecdote("Parmentier et la pomme de terre", "Au XVIIIe siècle, le pharmacien Antoine Parmentier a fait la promotion de la pomme de terre en France, alors qu'elle était surtout jugée bonne pour les animaux."),
        Anecdote("La fraise et monsieur Frézier", "En 1714, un officier au nom prédestiné, Amédée-François Frézier, a rapporté du Chili des plants de fraisier. Croisés avec une fraise d'Amérique du Nord, ils ont donné nos grosses fraises."),
        Anecdote("Les trésors venus d'Amérique", "Tomate, pomme de terre, maïs, haricot, courge, piment, cacao et vanille viennent tous du continent américain. Ils sont arrivés en Europe après 1492."),
        Anecdote("Le chat et le lait", "Beaucoup de chats adultes digèrent mal le lactose du lait de vache. Contrairement à l'image habituelle, le lait n'est pas une bonne boisson pour eux."),
        Anecdote("Les carottes et la vision de nuit", "Pendant la Seconde Guerre mondiale, les Britanniques ont fait croire que leurs pilotes voyaient la nuit grâce aux carottes, pour cacher l'existence du radar."),
        Anecdote("Le goût passe par le nez", "Une grande partie de ce qu'on appelle le goût vient en fait de l'odorat : les arômes remontent vers le nez pendant qu'on mâche. Nez bouché, tout semble fade."),
        Anecdote("La carte de la langue est fausse", "On a longtemps dit que chaque zone de la langue sentait un seul goût. C'est faux : tous les goûts sont perçus sur toute la langue."),
        Anecdote("Le faux wasabi", "Hors du Japon, le « wasabi » servi avec les sushis est très souvent du raifort mélangé à de la moutarde et à du colorant vert. Le vrai wasabi est une racine rare et chère."),
        Anecdote("Le surimi, du poisson blanc", "Le surimi n'est pas du crabe : il est fabriqué à partir de chair de poisson blanc, comme le colin, aromatisée et mise en forme."),
        Anecdote("Deux épices dans un seul fruit", "La noix de muscade et le macis viennent du même fruit : la muscade est la graine, le macis est la fine enveloppe rouge qui l'entoure."),
        Anecdote("La cannelle, une écorce", "La cannelle est l'écorce intérieure du cannelier. En séchant, elle s'enroule toute seule en petits bâtons."),
        Anecdote("Le gingembre, une tige sous terre", "Le gingembre n'est pas une racine mais un rhizome, c'est-à-dire une tige qui pousse sous la terre."),
        Anecdote("Le quinoa n'est pas une céréale", "Le quinoa est une « pseudo-céréale » : il est de la famille des épinards et des betteraves, pas de celle du blé."),
        Anecdote("Le blé noir n'est pas du blé", "Le sarrasin, ou blé noir, des galettes bretonnes n'est pas du blé : c'est une plante de la famille de l'oseille, naturellement sans gluten."),
        Anecdote("Des millions de fleurs pour un pot", "Pour produire un kilo de miel, les abeilles d'une ruche doivent visiter plusieurs millions de fleurs."),
        Anecdote("Le comté et les montbéliardes", "Le comté AOP est fabriqué avec le lait de vaches de races montbéliarde ou simmental française, nourries à l'herbe et au foin."),
        Anecdote("Le fruit géant", "Le jacquier produit le plus gros fruit qui pousse sur un arbre : il peut dépasser 30 kilos."),
        Anecdote("Le durian interdit dans le métro", "À Singapour, il est interdit d'emporter un durian dans le métro. Ce fruit, adoré par beaucoup, a une odeur très forte."),
        Anecdote("La pomme de terre verte", "Les parties vertes des pommes de terre contiennent de la solanine, une substance toxique. Il faut les enlever, ou jeter la pomme de terre si elle est très verte."),
        Anecdote("Le riz nourrit la moitié du monde", "Le riz est l'aliment de base de plus de la moitié de la population mondiale, surtout en Asie."),
        Anecdote("Œuf brun ou œuf blanc ?", "La couleur de la coquille dépend de la race de la poule, pas de la qualité de l'œuf. Nutritionnellement, ils sont identiques."),
        Anecdote("Le pop-corn qui explose", "Chaque grain de maïs contient un peu d'eau. En chauffant, elle se transforme en vapeur, la pression monte et l'enveloppe finit par éclater."),
        Anecdote("Le romanesco, un chou mathématique", "Le chou romanesco forme des spirales de petites pointes qui ressemblent chacune au chou entier. Le nombre de ses spirales suit la suite de Fibonacci."),
        Anecdote("L'ananas ne pousse pas dans les arbres", "L'ananas pousse au cœur d'une plante basse, au ras du sol. Il faut environ un an et demi à deux ans pour obtenir un fruit."),
        Anecdote("Le bananier, une herbe géante", "Le bananier n'est pas un arbre : il n'a pas de bois. C'est une plante herbacée géante, parmi les plus grandes du monde."),
        Anecdote("Des bananes presque clones", "La banane Cavendish, la plus vendue au monde, se multiplie par rejets : les plants sont presque identiques. Sa devancière, la Gros Michel, a été décimée par une maladie au XXe siècle."),
        Anecdote("La framboise, un fruit en grappe", "Une framboise est faite de dizaines de petites boules, chacune avec sa propre graine. C'est un fruit composé."),
        Anecdote("Le chewing-gum avalé", "Non, un chewing-gum avalé ne reste pas sept ans dans le ventre. Le corps ne le digère pas, mais il est éliminé normalement en quelques jours."),
        Anecdote("La moutarde de Dijon voyage", "« Moutarde de Dijon » désigne une recette, pas une origine. Une grande partie des graines utilisées vient du Canada, même si la culture renaît en Bourgogne."),
        Anecdote("La tarte à l'envers", "Selon la légende, la tarte Tatin est née d'une erreur des sœurs Tatin, à Lamotte-Beuvron, à la fin du XIXe siècle : une tarte aux pommes cuite à l'envers."),
        Anecdote("La madeleine de Proust", "Dans « Du côté de chez Swann » (1913), une madeleine trempée dans du thé réveille chez le narrateur ses souvenirs d'enfance. L'expression est restée."),
        Anecdote("Le restaurant, d'abord un bouillon", "Au XVIIIe siècle, un « restaurant » désignait un bouillon censé redonner des forces. Les premiers établissements parisiens qui en servaient ont donné leur nom aux restaurants."),
        Anecdote("Le pain de tradition française", "Depuis 1993, la loi réserve le nom « pain de tradition française » à un pain fait de farine, d'eau, de sel et de levure ou levain, sans aucun additif."),
        Anecdote("Le bleu du fromage", "Les veines bleues du roquefort ou du bleu d'Auvergne sont un champignon comestible, le Penicillium roqueforti, cousin de celui de la pénicilline."),
        Anecdote("La pénicilline et le melon", "En 1943, aux États-Unis, une souche très productive de pénicilline a été trouvée sur un melon moisi acheté au marché. Elle a permis d'en produire en grande quantité."),
        Anecdote("L'inventeur de la conserve", "Au début du XIXe siècle, le Français Nicolas Appert a inventé la conservation des aliments par la chaleur dans des récipients fermés. On parle encore d'appertisation."),
        Anecdote("La pasteurisation, d'abord pour le vin", "Dans les années 1860, Louis Pasteur a montré qu'un chauffage modéré évitait au vin de s'abîmer. La méthode a ensuite été appliquée au lait."),
        Anecdote("La fleur de sel", "La fleur de sel est la fine couche de cristaux qui se forme à la surface des marais salants, par temps chaud et venteux. Elle est récoltée à la main."),
        Anecdote("Le plus vieux pain du monde", "Des archéologues ont retrouvé en Jordanie des restes de pain vieux d'environ 14 400 ans, cuits avant même les débuts de l'agriculture."),
        Anecdote("Un fromage de 3 600 ans", "Du fromage vieux d'environ 3 600 ans a été découvert avec des momies dans le désert du Taklamakan, en Chine."),
        Anecdote("L'échelle des piments", "La force des piments se mesure sur l'échelle de Scoville, inventée en 1912 par le pharmacien américain Wilbur Scoville."),
        Anecdote("Le lait qui déborde", "En chauffant, le lait forme une peau de protéines à sa surface. Elle retient la vapeur, la mousse gonfle… et déborde."),
        Anecdote("L'eau bout moins chaud en montagne", "Au sommet du mont Blanc, l'eau bout vers 85 °C au lieu de 100 °C, car la pression de l'air est plus faible. Les pâtes y cuisent plus lentement."),
        Anecdote("Le sel ne fait pas bouillir plus vite", "Saler l'eau augmente très légèrement sa température d'ébullition. Ça ne la fait pas bouillir plus vite : on sale pour le goût."),
        Anecdote("Les fruits qui font mûrir les autres", "Les pommes et les bananes dégagent de l'éthylène, un gaz qui accélère le mûrissement. Un avocat dur mûrit plus vite à côté d'une banane."),
        Anecdote("Le cassis bat l'orange", "Le cassis contient environ trois à quatre fois plus de vitamine C que l'orange."),
        Anecdote("Le poivron rouge, roi de la vitamine C", "À poids égal, le poivron rouge contient environ trois fois plus de vitamine C que l'orange."),
        Anecdote("Le curcuma aime le poivre", "La pipérine du poivre noir aide beaucoup le corps à absorber la curcumine du curcuma. Les recettes de curry les associent souvent."),
        Anecdote("Le café est une graine", "Le grain de café est la graine d'un petit fruit rouge, appelé cerise de café. On le torréfie pour lui donner son goût."),
        Anecdote("Thé vert, thé noir : une seule plante", "Thé vert et thé noir viennent de la même plante, le théier. La différence vient de l'oxydation des feuilles après la cueillette."),
        Anecdote("La noix de coco n'est pas une noix", "Pour les botanistes, la noix de coco est une drupe, comme la pêche ou la cerise : un noyau dur entouré d'une enveloppe."),
        Anecdote("L'olive crue est immangeable", "Cueillie sur l'arbre, l'olive est terriblement amère. Il faut la tremper plusieurs semaines dans l'eau ou la saumure pour la rendre bonne."),
        Anecdote("Olive verte, olive noire", "Olives vertes et noires peuvent venir du même arbre : les noires ont simplement été cueillies plus mûres."),
        Anecdote("5 kilos d'olives pour un litre", "Il faut en moyenne environ 5 kilos d'olives pour obtenir un litre d'huile d'olive."),
        Anecdote("Sucre de canne ou de betterave ?", "Une fois raffinés, le sucre de canne et le sucre de betterave sont la même molécule : du saccharose. Impossible de les distinguer au goût."),
        Anecdote("Salaire et sel", "Le mot « salaire » vient du latin salarium, lié au sel, précieux dans l'Antiquité. L'origine exacte de ce lien est encore discutée par les historiens."),
        Anecdote("Copains de pain", "« Copain » et « compagnon » viennent du latin cum panis : celui avec qui on partage le pain."),
        Anecdote("Des mots aztèques dans l'assiette", "« Tomate », « avocat » et « chocolat » viennent de la langue des Aztèques, le nahuatl : tomatl, ahuacatl, et un mot proche de xocolatl."),
        Anecdote("Le dessert, après avoir desservi", "Le mot « dessert » vient de « desservir » : c'est ce que l'on servait une fois la table débarrassée des plats principaux."),
        Anecdote("Déjeuner, c'est rompre le jeûne", "« Déjeuner » vient du latin populaire disjejunare, « rompre le jeûne ». C'est aussi le sens de l'anglais breakfast."),
        Anecdote("Le mythe des 10 000 pas", "L'objectif des 10 000 pas vient d'une publicité japonaise pour un podomètre, en 1965. Les études montrent des bienfaits nets dès 6 000 à 8 000 pas par jour."),
        Anecdote("Les muscles ne deviennent pas du gras", "Un muscle ne se transforme jamais en graisse : ce sont deux tissus différents. Quand on arrête le sport, le muscle fond et la graisse peut augmenter, séparément."),
        Anecdote("La sueur ne sent rien", "La sueur fraîche est presque sans odeur. L'odeur vient des bactéries de la peau qui la décomposent."),
        Anecdote("Plus grand le matin", "On est environ un centimètre plus grand le matin que le soir : pendant la journée, les disques entre les vertèbres se tassent un peu."),
        Anecdote("Le cerveau, gros mangeur", "Au repos, le cerveau utilise environ 20 % de l'énergie du corps, alors qu'il ne pèse qu'environ 2 % de notre poids."),
        Anecdote("Plus de 600 muscles", "Le corps humain compte plus de 600 muscles. Le plus volumineux est le grand fessier."),
        Anecdote("Pourquoi 42,195 km ?", "La distance du marathon a été fixée aux Jeux olympiques de Londres en 1908, pour partir du château de Windsor et arriver devant la loge royale."),
        Anecdote("Le premier Tour de France", "Le premier Tour de France a eu lieu en 1903. Il ne comptait que six étapes, dont certaines de plus de 400 kilomètres."),
        Anecdote("Gymnase, un mot grec", "Le mot « gymnase » vient du grec gymnos, « nu » : dans la Grèce antique, les athlètes s'entraînaient sans vêtements."),
        Anecdote("Les astronautes grandissent", "Dans l'espace, sans gravité, la colonne vertébrale se détend : les astronautes gagnent quelques centimètres, qu'ils reperdent au retour sur Terre.")
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
        Anecdote("Les couleurs dans l'assiette", "Des fruits et légumes de couleurs variées apportent des vitamines et des antioxydants différents. Une assiette colorée est souvent une assiette équilibrée."),
        // Ajoutées en v0.9
        Anecdote("Pas de miel avant 1 an", "Le miel peut contenir des spores responsables du botulisme infantile. Il est déconseillé d'en donner aux bébés de moins d'un an, même dans une préparation."),
        Anecdote("Pamplemousse et médicaments", "Le jus de pamplemousse peut changer l'effet de certains médicaments. Si tu prends un traitement, demande à ton médecin ou à ton pharmacien."),
        Anecdote("La réglisse avec modération", "En grande quantité, la réglisse peut faire monter la tension artérielle. Bonbons ou boissons à la réglisse : de temps en temps, pas tous les jours en grosse quantité."),
        Anecdote("Carottes cuites avec un filet d'huile", "Le bêta-carotène des carottes est mieux absorbé quand elles sont cuites et accompagnées d'un peu de matière grasse."),
        Anecdote("La tomate cuite", "Le lycopène, le pigment rouge de la tomate, est mieux absorbé quand la tomate est cuite, par exemple en sauce avec un filet d'huile d'olive."),
        Anecdote("Une ou deux noix du Brésil suffisent", "Une ou deux noix du Brésil apportent environ les besoins d'une journée en sélénium. Inutile d'en manger beaucoup : trop de sélénium n'est pas bon non plus."),
        Anecdote("Le sel iodé", "L'iode est ajouté au sel pour éviter les carences, qui touchent la thyroïde. Quand tu sales, choisis du sel iodé… mais sale peu."),
        Anecdote("Le code des œufs", "Sur la coquille, le premier chiffre indique l'élevage : 0 = bio, 1 = plein air, 2 = au sol, 3 = en cage."),
        Anecdote("L'œuf frais coule", "Plonge un œuf dans un verre d'eau : très frais, il reste couché au fond ; plus vieux, il se redresse ; s'il flotte, mieux vaut ne pas le manger."),
        Anecdote("Ne lave pas tes œufs", "En Europe, les œufs ne sont pas lavés : une fine couche naturelle protège la coquille. Les laver avant de les ranger l'abîme."),
        Anecdote("Les restes au frais, vite", "Mets les restes au réfrigérateur rapidement, dans les deux heures après la cuisson, et mange-les dans les deux ou trois jours."),
        Anecdote("Décongeler au frigo", "Décongèle viandes et poissons au réfrigérateur plutôt que sur le plan de travail : les microbes se multiplient vite à température ambiante."),
        Anecdote("Ne pas recongeler cru", "Un aliment cru décongelé ne se recongèle pas tel quel. Une fois cuisiné, en revanche, il peut repartir au congélateur."),
        Anecdote("DLC ou DDM ?", "« À consommer jusqu'au » est une date limite de sécurité. « À consommer de préférence avant » indique une qualité optimale : après, le produit reste souvent bon s'il a été bien conservé."),
        Anecdote("Le frigo à 4 °C", "La zone la plus froide du réfrigérateur doit rester entre 0 et 4 °C : c'est là que vont viandes, poissons et plats cuisinés."),
        Anecdote("Faire tremper les légumineuses", "Faire tremper pois chiches ou haricots secs une nuit réduit leur temps de cuisson et les rend plus faciles à digérer."),
        Anecdote("Les haricots rouges bien cuits", "Les haricots rouges crus ou mal cuits contiennent une substance toxique. Ils doivent bouillir franchement avant d'être mijotés."),
        Anecdote("L'eau du robinet", "En France, l'eau du robinet est l'un des aliments les plus contrôlés. Elle est bien moins chère que l'eau en bouteille et évite des déchets plastiques."),
        Anecdote("Le sucre des sodas", "Une canette de soda de 33 cl contient en moyenne environ 35 g de sucre, soit à peu près 7 morceaux."),
        Anecdote("La viande rouge : 500 g par semaine", "Les recommandations françaises conseillent de ne pas dépasser environ 500 g de viande rouge par semaine (bœuf, porc, agneau…) et de varier avec la volaille, le poisson et les légumineuses."),
        Anecdote("La charcuterie : 150 g par semaine", "Les recommandations françaises conseillent de limiter la charcuterie à environ 150 g par semaine, soit à peu près trois tranches de jambon blanc."),
        Anecdote("Deux produits laitiers par jour", "Pour les adultes, les recommandations françaises conseillent deux produits laitiers par jour : un yaourt, un verre de lait, une portion de fromage…"),
        Anecdote("Du complet chaque jour", "Pain complet, riz complet, pâtes complètes : les recommandations conseillent des féculents complets au moins une fois par jour, pour leurs fibres."),
        Anecdote("30 minutes par jour", "Bouger au moins 30 minutes par jour à une intensité modérée (marche rapide, vélo…) est bon pour le cœur, le moral et le sommeil."),
        Anecdote("Se lever toutes les 2 heures", "Rester assis longtemps n'est pas bon, même si on fait du sport. Lève-toi et bouge quelques minutes au moins toutes les deux heures."),
        Anecdote("Renforcer ses muscles", "En plus de l'activité quotidienne, il est conseillé de faire du renforcement musculaire au moins deux fois par semaine : squats, pompes, gainage…"),
        Anecdote("S'échauffer d'abord", "Quelques minutes d'échauffement préparent les muscles et les articulations, et réduisent le risque de blessure."),
        Anecdote("Boire pendant l'effort", "Pendant un effort long ou par forte chaleur, bois régulièrement par petites gorgées, sans attendre d'avoir soif."),
        Anecdote("Le sport et le sommeil", "Une activité physique régulière aide à mieux dormir. Évite simplement un effort très intense juste avant d'aller te coucher."),
        Anecdote("Le café, pas trop tard", "La caféine agit plusieurs heures après une tasse. Pour bien dormir, mieux vaut éviter le café en fin d'après-midi et le soir."),
        Anecdote("Les boissons énergisantes", "Riches en caféine et en sucre, les boissons énergisantes sont déconseillées pendant le sport et chez les adolescents."),
        Anecdote("Les compléments alimentaires", "Avec une alimentation variée, les compléments alimentaires sont rarement utiles. Demande l'avis d'un médecin ou d'un pharmacien avant d'en prendre."),
        Anecdote("La vitamine D et le soleil", "La peau fabrique de la vitamine D au soleil. En hiver, on en trouve aussi dans les poissons gras et les œufs."),
        Anecdote("Le calcium ailleurs que dans le lait", "Le calcium se trouve aussi dans certaines eaux minérales, les choux, les amandes, les sardines avec leurs arêtes ou le tofu."),
        Anecdote("Plus de fibres, plus d'eau", "Si tu manges plus de fibres, augmente-les progressivement et bois suffisamment d'eau : ton ventre te dira merci."),
        Anecdote("Les huiles à varier", "L'huile de colza et l'huile de noix apportent des oméga-3. Varie avec l'huile d'olive selon les usages."),
        Anecdote("Cuisiner maison", "Cuisiner soi-même, même simplement, permet de choisir la quantité de sel, de sucre et de matières grasses."),
        Anecdote("La liste des ingrédients", "Sur un emballage, les ingrédients sont classés du plus présent au moins présent. Si le sucre est en tête, c'est l'ingrédient principal."),
        Anecdote("Les aliments ultra-transformés", "Les recommandations françaises conseillent de limiter les aliments ultra-transformés, souvent reconnaissables à leur longue liste d'ingrédients et d'additifs."),
        Anecdote("Les courses, le ventre plein", "Faire ses courses en ayant faim pousse souvent à acheter plus, et plus de produits gras ou sucrés. Une liste aide aussi beaucoup."),
        Anecdote("L'assiette équilibrée", "Un repère simple : une moitié d'assiette de légumes, un quart de féculents et un quart de protéines (viande, poisson, œufs, légumineuses)."),
        Anecdote("Faim ou envie ?", "Avant de grignoter, demande-toi si tu as vraiment faim, ou si tu t'ennuies. Un verre d'eau et quelques minutes suffisent parfois à faire passer l'envie."),
        Anecdote("Les fruits secs, une petite poignée", "Abricots secs, pruneaux ou raisins secs apportent des fibres, mais leur sucre est concentré : une petite poignée suffit."),
        Anecdote("Le chocolat noir", "Un chocolat noir à au moins 70 % de cacao est moins sucré que le chocolat au lait. À savourer avec modération."),
        Anecdote("Les conserves, pratiques aussi", "Les légumes et légumineuses en conserve sont pratiques et nourrissants. Rince les légumineuses pour enlever une partie du sel."),
        Anecdote("Laver fruits et légumes", "Lave les fruits et légumes à l'eau claire avant de les manger, même bio, et même si tu les épluches."),
        Anecdote("Le riz cuit, au frais", "Le riz cuit ne doit pas rester des heures à température ambiante. Mets-le vite au réfrigérateur s'il en reste."),
        Anecdote("Deux planches à découper", "Utilise une planche pour la viande crue et une autre pour les légumes crus, ou lave-la bien entre les deux."),
        Anecdote("Le steak haché bien cuit", "Pour les enfants et les personnes fragiles, le steak haché doit être cuit à cœur, sans rose à l'intérieur."),
        Anecdote("Le sel caché", "En France, le pain, les fromages, la charcuterie et les plats préparés sont parmi les premières sources de sel, bien avant la salière."),
        Anecdote("Le sucre caché", "Ketchup, sauces, céréales du petit-déjeuner, yaourts aromatisés : le sucre se cache dans beaucoup de produits, même salés."),
        Anecdote("L'escalier, une salle de sport gratuite", "Prendre l'escalier plutôt que l'ascenseur est un petit effort qui compte vraiment dans l'activité de la journée.")
    )

    private fun dayIndex(date: Calendar): Long {
        val c = date.clone() as Calendar
        c.timeZone = TimeZone.getDefault()
        return (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
    }

    /** Ancien tirage (avant la v0.9), gardé seulement pour ne pas remontrer ce qui a déjà été vu. */
    private fun legacyForDay(date: Calendar): List<String> {
        val day = dayIndex(date)
        fun pick(list: List<Anecdote>, pos: Long, salt: Long): Anecdote {
            val cycle = pos / list.size
            val order = list.indices.shuffled(Random(cycle * 7919L + salt))
            return list[order[(pos % list.size).toInt()]]
        }
        val oldIns = INSOLITE.take(30)
        val oldDec = DECOUVERTES.take(32)
        return listOf(pick(oldIns, day, 11).title, pick(oldDec, day * 2, 23).title, pick(oldDec, day * 2 + 1, 23).title)
    }

    private const val KEY = "news_bank"

    /**
     * Les actus du jour : une anecdote insolite et une découverte, jamais déjà lues. L'app retient ce qui a été
     * montré (chiffré sur le téléphone) ; quand tout a été lu, seules les plus anciennes peuvent revenir.
     * Le contenu reste le même toute la journée.
     */
    @Synchronized
    fun today(): DailyNews {
        val day = localDay(0)
        val st = runCatching { JSONObject(Repo.getExtra(KEY) ?: "{}") }.getOrElse { JSONObject() }
        if (st.optString("day") == day) {
            val ins = INSOLITE.firstOrNull { it.title == st.optString("ins") }
            val dec = DECOUVERTES.firstOrNull { it.title == st.optString("dec") }
            if (ins != null && dec != null) return DailyNews(ins, listOf(dec))
        }
        val seenI = st.optJSONArray("seenI")?.let { a -> (0 until a.length()).map { a.optString(it) } }?.toMutableList() ?: mutableListOf()
        val seenD = st.optJSONArray("seenD")?.let { a -> (0 until a.length()).map { a.optString(it) } }?.toMutableList() ?: mutableListOf()
        if (!st.has("seenI")) {
            // Première fois avec la mémoire : ce que l'ancien tirage a montré ces 30 derniers jours compte comme lu
            for (d in 1..30) {
                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }
                val (i, d1, d2) = legacyForDay(c)
                if (i !in seenI) seenI += i
                if (d1 !in seenD) seenD += d1
                if (d2 !in seenD) seenD += d2
            }
        }
        val rnd = Random(dayIndex(Calendar.getInstance()) * 131 + seenI.size)
        val ins = pickUnseen(INSOLITE, seenI, rnd)
        val dec = pickUnseen(DECOUVERTES, seenD, rnd)
        Repo.putExtra(KEY, JSONObject()
            .put("day", day).put("ins", ins.title).put("dec", dec.title)
            .put("seenI", JSONArray(seenI)).put("seenD", JSONArray(seenD))
            .toString())
        return DailyNews(ins, listOf(dec))
    }

    private fun pickUnseen(list: List<Anecdote>, seen: MutableList<String>, rnd: Random): Anecdote {
        var fresh = list.filter { it.title !in seen }
        if (fresh.isEmpty()) {
            // Tout a été lu : on repart, sans reprendre la moitié la plus récente
            val keep = seen.takeLast(list.size / 2)
            seen.clear(); seen.addAll(keep)
            fresh = list.filter { it.title !in seen }
        }
        val a = fresh[rnd.nextInt(fresh.size)]
        seen.remove(a.title); seen += a.title
        return a
    }

    /** Nombre d'anecdotes pas encore lues (pour l'écran des actus). */
    fun unreadCount(): Int {
        val st = runCatching { JSONObject(Repo.getExtra(KEY) ?: "{}") }.getOrElse { JSONObject() }
        val seen = mutableSetOf<String>()
        st.optJSONArray("seenI")?.let { a -> (0 until a.length()).forEach { seen += a.optString(it) } }
        st.optJSONArray("seenD")?.let { a -> (0 until a.length()).forEach { seen += a.optString(it) } }
        return (INSOLITE + DECOUVERTES).count { it.title !in seen }
    }
}
