package com.datecs.lineagoogle

import com.datecs.linea.LineaPro

interface LineaConnection {
    fun onLineaConnected(linea: LineaPro)
    fun onLineaDisconnected()
}
