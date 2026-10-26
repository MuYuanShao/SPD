package com.hospital.spd.mobile

/** WorkManager contract: wake only on validated network and delegate ordered sync to the WebView queue. */
class SyncWorker {
    fun constraintsDescription() = "NetworkType.CONNECTED; no automatic business submit"
    fun execute() = "query result -> validate -> notify UI for confirmation -> submit same operationId"
}
