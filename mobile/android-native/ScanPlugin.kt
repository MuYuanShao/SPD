package com.hospital.spd.mobile

/** Contract for the Capacitor bridge; implementation uses CameraX + bundled ML Kit model. */
data class ScanEvent(val rawCode: String, val source: String, val scannedAt: String)

interface ScanPlugin {
    fun startCamera(onResult: (ScanEvent) -> Unit, onError: (String) -> Unit)
    fun stopCamera()
}
