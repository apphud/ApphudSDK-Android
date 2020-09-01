package ru.rosbank.mbdg.myapplication.tasks

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import ru.rosbank.mbdg.myapplication.ApphudLog
import java.io.IOException

fun advertisingId(context: Context): String? = try {
    ApphudLog.log("start load advertisingId")
    val id = AdvertisingIdClient.getAdvertisingIdInfo(context).id
    ApphudLog.log("success load advertisingId: $id")
    id
} catch (e: IOException) {
    ApphudLog.log("finish load advertisingId $e")
    null
} catch (e: GooglePlayServicesNotAvailableException) {
    ApphudLog.log("finish load advertisingId $e")
    null
} catch (e: GooglePlayServicesRepairableException) {
    ApphudLog.log("finish load advertisingId $e")
    null
}