package com.goodlife.app.track

import com.goodlife.app.data.Repo
import java.io.File

/**
 * Données hors ligne d'une zone téléchargée en qualité « Complète », en plus du fond de carte : les rues (pour calculer
 * des itinéraires sans réseau) et l'altitude (pour le dénivelé). Rangées dans les fichiers de l'app (pas dans le cache,
 * qu'Android peut vider), un dossier par zone : supprimer la zone supprime tout.
 */
object OfflineData {
    private fun root(): File? = Repo.appContext?.filesDir?.let { File(it, "offline") }

    fun zoneDir(id: Long): File? = root()?.let { File(it, id.toString()) }

    @Volatile private var dirs: List<File>? = null
    private fun dirs(): List<File> = dirs ?: root()?.listFiles()?.filter { it.isDirectory }.orEmpty().also { dirs = it }

    /** À appeler quand une zone apparaît ou disparaît. */
    fun invalidate() { dirs = null }

    private fun find(name: String): File? = dirs().firstNotNullOfOrNull { d -> File(d, name).takeIf { it.exists() } }

    fun roadsName(z: Int, x: Int, y: Int) = "r${z}_${x}_$y"
    fun elevName(x: Int, y: Int) = "e12_${x}_$y"

    /** Rues d'une tuile déjà enregistrée dans une zone hors ligne (vide si la tuile n'a aucune rue), sinon null. */
    fun roads(z: Int, x: Int, y: Int): File? = find(roadsName(z, x, y))

    /** Tuile d'altitude enregistrée dans une zone hors ligne, sinon null. */
    fun elevation(x: Int, y: Int): File? = find(elevName(x, y))

    fun delete(id: Long) {
        zoneDir(id)?.deleteRecursively()
        invalidate()
    }

    fun deleteAll() {
        root()?.deleteRecursively()
        invalidate()
    }
}
