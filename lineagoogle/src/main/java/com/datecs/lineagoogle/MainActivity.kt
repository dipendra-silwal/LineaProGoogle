package com.datecs.lineagoogle

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.datecs.barcode.Barcode
import com.datecs.barcode.Intermec
import com.datecs.barcode.Newland
import com.datecs.linea.LineaPro
import com.datecs.linea.LineaProException
import com.datecs.lineagoogle.util.MediaUtil.playSound
import com.datecs.lineagoogle.view.StatusView
import com.datecs.rfid.ContactlessCard
import com.datecs.rfid.FeliCaCard
import com.datecs.rfid.ISO14443ACard
import com.datecs.rfid.ISO15693Card
import com.datecs.rfid.RFID
import com.datecs.rfid.RFIDModule
import com.datecs.rfid.STSRICard
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.Timer
import java.util.TimerTask




private const val UPDATE_BATTERY_TIME = 3000

private const val TAG: String = "LineaGoogle"

class MainActivity : AppCompatActivity(), LineaPro.BarcodeListener, LineaPro.ButtonListener, LineaAction,
    LineaPro.JpegListener {

    private lateinit var updateHandler: Handler
    private lateinit var prefs: SharedPreferences
    private lateinit var mainFragment: MainFragment
    private lateinit var statusView: StatusView
    private lateinit var lineaManager: LineaManager

    //QA
    private var barcodeCount = 0
    private var barcodeData = ""
    private var LPGBatterySOC = 0
    private var LPGBatteryVoltage = 0
    private val filename = "ScanDataLogs.txt"
    private val filepath = "MyFileStorage"
    private var myExternalFile: File? = null
    private var data:String = "Counter,Date,Time,Pixel Battery, LPG Battery%, LPG Battery Voltage, Barcode type, BarcodeData\n"


    private var scanning = false

    private var lineaPro: LineaPro? = null

    private val updateRunnable = UpdateRunnable()

    private val executor = Executors.newSingleThreadExecutor()

    private fun interface LineaRunnable {
        @Throws(IOException::class)
        fun run(linea: LineaPro)
    }

    private val lineaConnection: LineaConnection = object : LineaConnection {
        override fun onLineaConnected(linea: LineaPro) {
            playSound(this@MainActivity, R.raw.connect)
            statusView.hide()
            linea.setBarcodeListener(this@MainActivity)
            linea.setButtonListener(this@MainActivity)
            linea.setJpegListener(this@MainActivity)
            lineaPro = linea
            updateHandler.removeCallbacksAndMessages(null)
           updateHandler.post(updateRunnable)
            initLinea()
        }

        override fun onLineaDisconnected() {
            playSound(this@MainActivity, R.raw.disconnect)
            statusView.show(R.drawable.usb_unplugged)
            mainFragment.resetBattery()
            updateHandler.removeCallbacksAndMessages(null)
        }
    }

//QA
    public fun updateBatteryOnTap() {
        // Update battery information
        runAsync({ linea: LineaPro ->
            val batteryInfo = linea.batteryInfo
            runOnUiThread { mainFragment.updateBattery(batteryInfo)
                linea.beep(80, intArrayOf(800, 150))}
         }, false)

    }

    public inner class UpdateRunnable : Runnable {
        override fun run() {
            // Update battery information
            runAsync({ linea: LineaPro ->
                val batteryInfo = linea.batteryInfo
                runOnUiThread { mainFragment.updateBattery(batteryInfo) }
            }, false)
            //updateHandler.postDelayed(this, UPDATE_BATTERY_TIME.toLong())
        }
    }

    private var resultFirmwareFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val uri = data?.data
            uri?.let {
                updateFirmware(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateHandler = Handler(mainLooper)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.add(R.id.content, MainFragment().also { mainFragment = it })
        fragmentTransaction.commit()
        statusView = findViewById<View>(R.id.status_pane) as StatusView
        statusView.hide()
        // statusView.show(R.drawable.usb_unplugged)
        lineaManager = (applicationContext as MyApplication).lineaManager
        lineaManager.setConnectionListener(lineaConnection)
        lineaManager.lineaPro?.let {
            lineaConnection.onLineaConnected(it)
        }

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        lineaManager.connect()
    }

    override fun onStop() {
        super.onStop()
        updateHandler.removeCallbacksAndMessages(null)
        lineaManager.disconnect()
    }

    public fun reconnect(){
        lineaManager.disconnect()
        lineaManager.connect()

    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        if (item.itemId == R.id.clear_log) {
            mainFragment.clearLog()
            barcodeCount = 0
        }
        return true
    }


    override fun onReadBarcode(barcode: Barcode) {
        runAsync({ linea ->
            if (prefs.getBoolean("beep_upon_scan", false)) {
                linea.beep(80, intArrayOf(2730, 150, 65000, 20, 2730, 150))
                //linea.beep(80, intArrayOf(2730, 5000, 0, 250, 1730, 5000, 0, 250, 1000, 5000,0, 250))
            }
            if (prefs.getBoolean("vibrate_upon_scan", false)) {
                linea.startVibrator(500)
            }
        }, false)
        runOnUiThread {
            barcodeData = barcode.toString()
            barcodeCount++
            mainFragment.addLog(barcodeCount.toString() + ". Barcode: (" + barcode.typeString + ") " + barcode.getDataString())
            logScanData()
        }
    }

    override fun onButtonStateChanged(index: Int, state: Boolean) {
        runOnUiThread {
//            if (state) {
//                statusView.show(R.drawable.barcode)
//            } else {
//                statusView.hide()
//            }
            if (index == 4 || index == 8 || index == 64) {
                if (state) {
                    //TODO
                    actionStartScan()
                    statusView.show(R.drawable.barcode)
                } else {
                    actionStopScan()
                    statusView.hide()
                }
            } else if (index == 16) {
                if (state) {
                    info("Left Programmable Button Pressed")
                    playSound(this@MainActivity, R.raw.left)
                    // MediaUtil.playSound(this@MainActivity, R.raw.left)
                }
            } else if (index == 32) {
                if (state) {
                    info("Right Programmable Button Pressed")
                    playSound(this@MainActivity, R.raw.right)
                    // MediaUtil.playSound(this@MainActivity, R.raw.right)
                }
            }

        }
    }

    override fun onReadJpeg(data: ByteArray) {
        if (scanning) {
            val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(data))
            runOnUiThread {
                mainFragment.updateJpeg(bitmap)
            }
        }
    }

    override fun actionResetBarcodeEngine() {
        runAsync({ linea -> linea.bcRestoreDefaultMode() }, true)
    }

    override fun actionUpdateSetting(key: String, value: String?) {
        runAsync({ linea -> updateSetting(linea, key, value) }, true)
    }

    override fun actionUpdateFirmware() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.setType("application/octet-stream")
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        resultFirmwareFileLauncher.launch(intent)
    }

    override fun actionReadTag() {

        warn("RFID Initiated. Please present RFID tags...")
        runAsync({ linea ->
            var rfidModule: RFIDModule? = null

            // Set RFID module debug output.
            RFID.setDebug(true)
            try {
                var status = false
                val latch = CountDownLatch(1)

                // Create instance of RF module.
                rfidModule = linea.rfidGetModule()

                // Register event to listen for cards presents
                rfidModule.setCardListener(RFIDModule.CardListener { card ->
                    try {
                        processTag(card)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        fail("Tag processing failed: " + e.message)
                    }
                    status = true
                    latch.countDown()
                })

                // Enable RF module.
                rfidModule.enable()

                // Gives some time to complete operation.
                latch.await(10000L, TimeUnit.MILLISECONDS)

                if (!status) {
                    warn("No card detected")
                }

                // Disable RF module as soon as with finished with it to save power.
                rfidModule.disable()
            } finally {
                rfidModule?.close()
            }
        }, true)
    }

    override fun actionTurnOff() {
        runAsync({ linea -> linea.turnOff() }, false)
    }

    override fun actionStartScan() {
        runAsync({ linea ->
            linea.bcStartScan()
        }, false)
        scanning = true
    }

    override fun actionStopScan() {
        runAsync({ linea ->
            linea.bcStopScan()
        }, false)
        scanning = false
        mainFragment.updateJpeg(null)
    }

    override fun actionReadInformation() {
        runAsync({ linea -> readInformation(linea) }, false)
    }
    //QA
    private fun getCurrentDateTime(): Date {
        return Calendar.getInstance().time
    }
    //QA
    private fun isExternalStorageReadOnly(): Boolean {
        val extStorageState = Environment.getExternalStorageState()
        Log.d("External Storage State", extStorageState)
        return extStorageState == Environment.MEDIA_MOUNTED_READ_ONLY
    }
    //QA
    private fun isExternalStorageAvailable(): Boolean {
        val extStorageState = Environment.getExternalStorageState()
        Log.d("External Storage State", extStorageState)
        return extStorageState == Environment.MEDIA_MOUNTED
    }

    //QA
    private fun logScanData() {
        val bm = applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager
        val pixelBatLevel:Int = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd,HH:mm:ss")
        val date = LocalDateTime.now()
        val formattedDate = date.format(formatter)
        runAsync({ linea: LineaPro ->
            val batteryInfo = linea.batteryInfo
            val fuelGauge = batteryInfo?.fuelgauge
            if (fuelGauge != null) {
                LPGBatterySOC = fuelGauge.stateOfCharge
                LPGBatteryVoltage = fuelGauge.voltage

            }
            else LPGBatterySOC = 0
        }, false)


        var content:String = "$barcodeCount,$formattedDate,$pixelBatLevel,$LPGBatterySOC,$LPGBatteryVoltage, $barcodeData\n"

        var file = File(getExternalFilesDir(filepath), filename)
        var fileExists = file.exists()
        //check if mountable
        if (!isExternalStorageAvailable() || isExternalStorageReadOnly()){

        }
        else{
            //append if file already there
            if(fileExists){
                try{
                    val fw = FileWriter(file, true)
                    fw.write(content)
                    fw.close()
                } catch (e: IOException){
                    e.printStackTrace()
                }
            }
            //create new file
            else{
                myExternalFile = File(getExternalFilesDir(filepath), filename)
                try {
                    val fos1 = FileOutputStream(myExternalFile)

                    fos1.write(data.toByteArray())
                    fos1.close()

                    val fw = FileWriter(file, true)
                    fw.write(content)
                    fw.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun info(text: String) {
        Log.i(TAG, text)
        runOnUiThread { mainFragment.addLog("<I>$text") }
    }

    private fun warn(text: String) {
        Log.w(TAG, text)
        runOnUiThread { mainFragment.addLog("<W>$text") }
    }

    private fun fail(text: String) {
        Log.e(TAG, text)
        runOnUiThread { mainFragment.addLog("<E>$text") }
    }

    private fun runAsync(r: LineaRunnable, showProgress: Boolean) {
        if (showProgress) {
            statusView.show(R.drawable.process)
        }

        executor.execute {
            lineaPro?.let {
                try {
                    try {
                        r.run(it)
                    } catch (e: LineaProException) {
                        val sw = StringWriter()
                        val pw = PrintWriter(sw)
                        e.printStackTrace(pw)
                        // warn("Linea error: $sw")
                        warn("Error: LineaPro Exception")
                    } catch (e: IOException) {
                        val sw = StringWriter()
                        val pw = PrintWriter(sw)
                        e.printStackTrace(pw)
                        //fail("I/O error: $sw")
                        fail("Error: IO Exception")

                    } catch (e: Exception) {
                        val sw = StringWriter()
                        val pw = PrintWriter(sw)
                        e.printStackTrace(pw)
                        // fail("Critical error: $sw")
                        fail("Error: Exception")
                    }
                } finally {
                    if (showProgress) {
                        runOnUiThread { statusView.hide() }
                    }
                }
            }
        }
    }

    private fun byteArrayToHexString(buffer: ByteArray): String {
        val tmp = CharArray(buffer.size * 3)
        var i = 0
        var j = 0
        while (i < buffer.size) {
            val a = buffer[i].toInt() and 0xff shr 4
            val b = buffer[i].toInt() and 0x0f
            tmp[j++] = (if (a < 10) '0'.code + a else 'A'.code + a - 10).toChar()
            tmp[j++] = (if (b < 10) '0'.code + b else 'A'.code + b - 10).toChar()
            tmp[j++] = ' '
            i++
        }
        return String(tmp)
    }

    private fun initLinea() {
        runAsync({ linea ->
            readInformation(linea)
            linea.bcStopScan()
            linea.bcStopBeep()
            updateSetting(linea, "scan_button")
            updateSetting(linea, "battery_charge")
            updateSetting(linea, "external_speaker")
            updateSetting(linea, "external_speaker_button")
            updateSetting(linea, "device_timeout_period")
            updateSetting(linea, "code128_symbology")
            updateSetting(linea, "barcode_scan_mode")
            updateSetting(linea, "barcode_scope_scale_mode")
        }, true)
    }

    private fun updateFirmware(uri: Uri) {
        val inputStream: InputStream?
        try {
            inputStream = contentResolver.openInputStream(uri)!!
            val firmware = ByteArray(inputStream.available())
            inputStream.read(firmware)
            inputStream.close()
            runAsync({linea ->
                linea.fwUpdate(firmware)
            },true)
        } catch (e: FileNotFoundException) {
            e.message?.let { fail(it) }
            return
        }
    }

    @Throws(IOException::class)
    private fun updateSetting(linea: LineaPro, key: String, value: String?) {
        val info = linea.getInformation()
        if ("scan_button" == key) {
            val enabled = value.toBoolean()
            linea.enableScanButton(enabled)
        } else if ("battery_charge" == key) {
            // Do not enable battery charge until device is in charging state.
            val enabled = value.toBoolean()
            linea.enableBatteryCharge(enabled)
        } else if ("external_speaker" == key) {
            if (info.hasExternalSpeaker()) {
                val enabled = value.toBoolean()
                linea.enableExternalSpeaker(enabled)
            }
        } else if ("external_speaker_button" == key) {
            if (info.hasExternalSpeaker()) {
                val enabled = value.toBoolean()
                linea.enableExternalSpeakerButton(enabled)
            }
        } else if ("device_timeout_period" == key) {
            val autoOffTimeIndex = value!!.toInt()
            if (autoOffTimeIndex == 0) { //30 sec
                linea.setAutoOffTime(true, 30000)
            } else if (autoOffTimeIndex == 1) { //60 sec
                linea.setAutoOffTime(true, 60000)
            }
            else if (autoOffTimeIndex == 2) { //5min
                linea.setAutoOffTime(true, 300000)
            }
            else if (autoOffTimeIndex == 3) { //10min
                linea.setAutoOffTime(true, 600000)
            }
            else if (autoOffTimeIndex == 4) { //30min
                linea.setAutoOffTime(true, 1800000)
            }
            else if (autoOffTimeIndex == 5) { //1 hr
                linea.setAutoOffTime(true, 3600000)
            }

        } else if ("code128_symbology" == key) {
            if (info.hasIntermecEngine()) {
                val engine = linea.bcGetEngine() as Intermec
                val enabled = value.toBoolean()
                engine.enableCode128(enabled)
                engine.saveSymbology()
            }
        } else if ("barcode_scan_mode" == key) {
            val scanMode = value!!.toInt()
            linea.bcSetMode(scanMode)
        } else if ("barcode_scope_scale_mode" == key) {
            if (info.hasNewlandEngine()) {
                val scopeScaleMode = value!!.toInt()
                val engine = linea.bcGetEngine() as Newland
                if (scopeScaleMode > 0) {
                    engine.turnOnScopeScaling(scopeScaleMode)
                } else {
                    engine.turnOffScopeScaling()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun updateSetting(linea: LineaPro, key: String) {
        val item = prefs.all[key]
        val value = item?.toString()
        value?.let { updateSetting(linea, key, it) }
    }

    @Throws(IOException::class)
    private fun readInformation(linea: LineaPro) {
        // Read linea pro information
        val info = linea.getInformation()
        val barcodeEngine = linea.bcGetEngine()
        val barcodeIdent: String = if (info.hasIntermecEngine()) {
            val ident = try {
                val engine = barcodeEngine as Intermec
                "Intermec " + engine.getIdent()
            } catch (e: IOException) {
                e.printStackTrace()
                "Intermec"
            }
            ident
        } else if (info.hasNewlandEngine()) {
            "Newland Barcode Engine"
        } else {
            "Zebra Engine"
        }
        runOnUiThread {
            mainFragment.clearLog()
            mainFragment.addLog(
                "<I>" + info.name + " " +
                        info.model + "\nFW:\t " + info.version + "\n" +
                        info.serialNumber
            )
            mainFragment.addLog("<I>$barcodeIdent")
        }
    }

    @Throws(IOException::class)
    private fun processTag(contactlessCard: ContactlessCard) {
        when (contactlessCard) {
            is ISO14443ACard -> {
                info("Detected ISO14 card")
                info("UID: " + byteArrayToHexString(contactlessCard.uid))
                when (contactlessCard.type) {
                    ContactlessCard.CARD_MIFARE_ULTRALIGHT -> {
                        info("Type: MIFARE ULTRALIGHT")
                        // Try to authenticate first with default key
                        //val key = "BREAKMEIFYOUCAN!".toByteArray()
                        //contactlessCard.ulcAuthenticate(key)

                        val address = 16

                        // Write 16 bytes to card
                        //info("Write 16 bytes to address $address")
                        val input = byteArrayOf(
                            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                            0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
                        )
                        contactlessCard.write16(address, input)

                        info("Read 16 bytes from address $address")
                        val output = contactlessCard.read16(address)
                        info("Data: " + byteArrayToHexString(output))
                    }
                    ContactlessCard.CARD_MIFARE_DESFIRE -> {
                        info("Type: MIFARE DESFIRE")
                    }
                    ContactlessCard.CARD_PAYMENT_A -> {
                        info("Type: PAYMENT_A")
                        val ats = contactlessCard.ats
                        info("ATS: " + byteArrayToHexString(ats))
                        //contactlessCard.executeAPDU(0, 0x84, 0x0, 0x0, null, 0x08)
                    }
                }
            }
            is ISO15693Card -> {
                info("Detected ISO15 card")
                info("UID: " + byteArrayToHexString(contactlessCard.uid))
                info("Block size: " + contactlessCard.blockSize)
                info("Max blocks: " + contactlessCard.maxBlocks)

                if (contactlessCard.blockSize > 0) {
                    val security = contactlessCard.getBlocksSecurityStatus(0, 16)
                    info("Security status: " + byteArrayToHexString(security))
                }
            }

            is FeliCaCard -> {
                info("Detected FeliCa card")
                info("UID: " + byteArrayToHexString(contactlessCard.uid))
            }

            is STSRICard -> {
                info("Detected STSRI card")
                info("UID: " + byteArrayToHexString(contactlessCard.uid))
                info("Block size: " + contactlessCard.blockSize)
            }

            else -> {
                info("Detected unspecified contactless card")
                info("UID: " + byteArrayToHexString(contactlessCard.uid))
            }
        }
        runAsync({ linea ->
            linea.beep(100, intArrayOf(1000, 150, 65000, 20))
        }, false)

//        // Wait silently to remove card
//        try {
//            contactlessCard.waitRemove();
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
    }
}





