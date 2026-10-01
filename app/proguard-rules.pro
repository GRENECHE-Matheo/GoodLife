# GoodLife - règles R8 (réduction de taille de la version publiée, v0.10)
#
# R8 retire le code jamais utilisé et raccourcit les noms internes. Ces règles protègent ce qui ne doit
# PAS changer, pour que l'app se comporte exactement comme avant.

# Les noms des énumérations (MealSlot, Goal, Sex, OutingType…) sont enregistrés dans les données de
# l'utilisateur (profil, repas, planning, sauvegardes) : ils doivent rester identiques d'une version à l'autre.
-keep enum com.goodlife.app.** { *; }

# Carte MapLibre : code natif (JNI) qui rappelle des classes Java par leur nom.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Lecture des codes-barres (ML Kit) : modèles et code natif.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Garder des traces d'erreur lisibles (numéros de ligne) sans exposer les noms de fichiers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
