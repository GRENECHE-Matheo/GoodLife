package com.goodlife.app.social

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.goodlife.app.MainActivity
import com.goodlife.app.R
import com.goodlife.app.data.Repo
import java.util.UUID

/**
 * StreetPass : quand deux téléphones GoodLife se croisent (quelques mètres), chacun lit la carte de l'autre
 * en Bluetooth basse consommation. Réservé à Android 12+ (pas besoin de la localisation), désactivé par défaut,
 * seulement si le profil est public. Android impose une notification tant que c'est actif.
 * Économie de batterie : émission et recherche en mode « basse consommation », une seule connexion à la fois,
 * et chaque téléphone n'est relu qu'une fois par heure au plus.
 */
object StreetPass {
    val SERVICE: UUID = UUID.fromString("7c3a6f1e-5d2b-4c8a-9e41-0a6b8d2f3c11")
    val CARD: UUID = UUID.fromString("7c3a6f1e-5d2b-4c8a-9e41-0a6b8d2f3c12")
    const val CHANNEL = "streetpass"

    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    /** Croiser des inconnus : réservé aux adultes. */
    fun allowedForAge(): Boolean = (Repo.profile.value?.age ?: 0) >= 18

    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else emptyArray()

    fun hasPermissions(context: Context): Boolean = permissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Démarre ou arrête le service selon les réglages (appelé au démarrage de l'app et quand ils changent). */
    fun sync(context: Context) {
        val s = Repo.settings.value
        val want = supported(context) && allowedForAge() && s.streetPass && Social.canShare() && hasPermissions(context)
        val intent = Intent(context, StreetPassService::class.java)
        if (want) runCatching { ContextCompat.startForegroundService(context, intent) }
        else context.stopService(intent)
    }
}

@SuppressLint("MissingPermission") // permissions vérifiées avant le démarrage (StreetPass.sync)
class StreetPassService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var server: BluetoothGattServer? = null
    private var card: ByteArray = ByteArray(0)
    private val lastRead = HashMap<String, Long>()       // adresse (aléatoire, change régulièrement) → dernière lecture
    private val queue = ArrayDeque<BluetoothDevice>()
    private var connecting: BluetoothGatt? = null
    private var met = 0

    private val manager get() = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Repo.init(applicationContext)
        startAsForeground()
        if (!StreetPass.hasPermissions(this) || manager.adapter?.isEnabled != true || !Social.canShare()) {
            stopSelf(); return
        }
        refreshCard()
        openServer()
        advertise()
        scan()
        // La carte (niveau, série…) est reconstruite toutes les 15 min
        handler.postDelayed(object : Runnable {
            override fun run() { refreshCard(); handler.postDelayed(this, 15 * 60_000L) }
        }, 15 * 60_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(StreetPass.CHANNEL, "StreetPass", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indique que StreetPass est actif"
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, StreetPass.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("StreetPass actif")
            .setContentText(if (met == 0) "À l'affût d'autres joueurs GoodLife" else "$met rencontre(s) depuis l'activation")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(1, n)
    }

    private fun refreshCard() {
        card = Social.mySignedCard() ?: run { stopSelf(); ByteArray(0) }
    }

    // ---------- Serveur : les autres lisent ma carte ----------
    private fun openServer() {
        server = manager.openGattServer(this, object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, c: BluetoothGattCharacteristic) {
                val value = card
                if (c.uuid != StreetPass.CARD || offset > value.size) {
                    server?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                    return
                }
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
            }
        })
        val service = BluetoothGattService(StreetPass.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(StreetPass.CARD, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
        )
        server?.addService(service)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

    private fun advertise() {
        manager.adapter?.bluetoothLeAdvertiser?.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)   // portée de quelques mètres : « se croiser »
                .setConnectable(true)
                .build(),
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(StreetPass.SERVICE)).setIncludeDeviceName(false).build(),
            advertiseCallback
        )
    }

    // ---------- Recherche : je lis la carte des autres ----------
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Tout se passe sur le fil principal : pas d'accès concurrent à la file et à la connexion en cours
            handler.post {
                val now = System.currentTimeMillis()
                val addr = result.device.address
                if (now - (lastRead[addr] ?: 0L) < 3_600_000L) return@post
                if (queue.any { it.address == addr } || queue.size > 10) return@post
                lastRead[addr] = now
                if (lastRead.size > 500) lastRead.entries.removeAll { now - it.value > 3_600_000L }
                queue.addLast(result.device)
                next()
            }
        }
    }

    private fun scan() {
        manager.adapter?.bluetoothLeScanner?.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(StreetPass.SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
            scanCallback
        )
    }

    /** Une seule connexion à la fois, 15 s maximum. */
    private fun next() {
        if (connecting != null) return
        val device = queue.removeFirstOrNull() ?: return
        val gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices() else finish(g)
            }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val c = g.getService(StreetPass.SERVICE)?.getCharacteristic(StreetPass.CARD)
                if (c == null || !g.readCharacteristic(c)) finish(g)
            }
            @Deprecated("API < 33")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                @Suppress("DEPRECATION") onRead(g, c.value, status)
            }
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                onRead(g, value, status)
            }
        }, BluetoothDevice.TRANSPORT_LE)
        connecting = gatt
        handler.postDelayed({ if (connecting === gatt) finish(gatt) }, 15_000)
    }

    private fun onRead(g: BluetoothGatt, value: ByteArray?, status: Int) = handler.post {
        if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
            val e = Social.receive(value, "street")
            if (e != null && (e.result == Repo.Received.NEW_ENCOUNTER || e.result == Repo.Received.SEEN_AGAIN)) {
                met++
                startAsForeground()   // met à jour le compteur de la notification
            }
        }
        finish(g)
    }

    private fun finish(g: BluetoothGatt) {
        handler.post {
            runCatching { g.disconnect(); g.close() }
            if (connecting === g) {
                connecting = null
                next()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { manager.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        runCatching { manager.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        runCatching { connecting?.close() }
        runCatching { server?.close() }
        super.onDestroy()
    }
}
