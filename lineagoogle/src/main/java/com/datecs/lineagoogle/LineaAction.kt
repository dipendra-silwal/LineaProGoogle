package com.datecs.lineagoogle

interface LineaAction {
    fun actionResetBarcodeEngine()
    fun actionUpdateSetting(key: String, value: String?)
    fun actionUpdateFirmware()
    fun actionReadTag()
    fun actionTurnOff()
    fun actionStartScan()
    fun actionStopScan()
    fun actionReadInformation()
}
