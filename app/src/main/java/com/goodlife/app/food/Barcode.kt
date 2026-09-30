package com.goodlife.app.food

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Lecture de code-barres avec ML Kit, modèle intégré à l'app : l'image est analysée sur le téléphone,
 * rien n'est envoyé. Seuls les formats alimentaires (EAN/UPC) sont recherchés.
 */
suspend fun detectFoodBarcode(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
    )
    scanner.process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { list ->
            val code = list.firstNotNullOfOrNull { b -> b.rawValue?.takeIf { v -> v.isNotEmpty() && v.all { it.isDigit() } } }
            if (cont.isActive) cont.resume(code)
            scanner.close()
        }
        .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
            scanner.close()
        }
    cont.invokeOnCancellation { runCatching { scanner.close() } }
}
