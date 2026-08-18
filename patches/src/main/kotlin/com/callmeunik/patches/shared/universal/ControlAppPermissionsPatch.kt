package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Removes selected dangerous / sensitive permissions from the app manifest.
 * Enable only the toggles for permissions you want to STRIP.
 * Permissions you leave OFF in options will remain in the app.
 */
@Suppress("unused")
val controlAppPermissionsPatch = resourcePatch(
    name = "Control app permissions",
    description = "Removes selected permissions from the app. Turn ON only what you want to disable/remove.",
    default = false,
) {
    // Turn ON = remove that permission from app
    val removeInternet by booleanOption("permRemoveInternet", false, title = "Remove INTERNET")
    val removeCamera by booleanOption("permRemoveCamera", false, title = "Remove CAMERA")
    val removeRecordAudio by booleanOption("permRemoveRecordAudio", false, title = "Remove RECORD_AUDIO")
    val removeLocation by booleanOption("permRemoveLocation", false, title = "Remove LOCATION (fine/coarse/background)")
    val removeContacts by booleanOption("permRemoveContacts", false, title = "Remove READ/WRITE CONTACTS")
    val removeSms by booleanOption("permRemoveSms", false, title = "Remove SMS / MMS")
    val removePhone by booleanOption("permRemovePhone", false, title = "Remove PHONE / CALL state")
    val removeStorage by booleanOption("permRemoveStorage", false, title = "Remove STORAGE / media files")
    val removeCalendar by booleanOption("permRemoveCalendar", false, title = "Remove CALENDAR")
    val removeSensors by booleanOption("permRemoveSensors", false, title = "Remove BODY_SENSORS")
    val removeBluetooth by booleanOption("permRemoveBluetooth", false, title = "Remove BLUETOOTH connect/scan")
    val removeNotifications by booleanOption("permRemoveNotifications", false, title = "Remove POST_NOTIFICATIONS")
    val removeAdId by booleanOption("permRemoveAdId", true, title = "Remove AD_ID / Ad services")
    val removeActivityRecognition by booleanOption("permRemoveActivityRecognition", false, title = "Remove ACTIVITY_RECOGNITION")
    val removeInstallPackages by booleanOption("permRemoveInstallPackages", true, title = "Remove REQUEST_INSTALL_PACKAGES")
    val removeSystemAlert by booleanOption("permRemoveSystemAlert", false, title = "Remove SYSTEM_ALERT_WINDOW")
    val removeQueryPackages by booleanOption("permRemoveQueryAllPackages", false, title = "Remove QUERY_ALL_PACKAGES")

    execute {
        val toStrip = mutableSetOf<String>()

        if (removeInternet == true) {
            toStrip += "android.permission.INTERNET"
            toStrip += "android.permission.ACCESS_NETWORK_STATE"
            toStrip += "android.permission.ACCESS_WIFI_STATE"
            toStrip += "android.permission.CHANGE_NETWORK_STATE"
            toStrip += "android.permission.CHANGE_WIFI_STATE"
        }
        if (removeCamera == true) {
            toStrip += "android.permission.CAMERA"
        }
        if (removeRecordAudio == true) {
            toStrip += "android.permission.RECORD_AUDIO"
            toStrip += "android.permission.CAPTURE_AUDIO_OUTPUT"
            toStrip += "android.permission.MODIFY_AUDIO_SETTINGS"
        }
        if (removeLocation == true) {
            toStrip += "android.permission.ACCESS_FINE_LOCATION"
            toStrip += "android.permission.ACCESS_COARSE_LOCATION"
            toStrip += "android.permission.ACCESS_BACKGROUND_LOCATION"
            toStrip += "android.permission.ACCESS_MEDIA_LOCATION"
            toStrip += "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS"
        }
        if (removeContacts == true) {
            toStrip += "android.permission.READ_CONTACTS"
            toStrip += "android.permission.WRITE_CONTACTS"
            toStrip += "android.permission.GET_ACCOUNTS"
        }
        if (removeSms == true) {
            toStrip += "android.permission.READ_SMS"
            toStrip += "android.permission.SEND_SMS"
            toStrip += "android.permission.RECEIVE_SMS"
            toStrip += "android.permission.RECEIVE_MMS"
            toStrip += "android.permission.RECEIVE_WAP_PUSH"
        }
        if (removePhone == true) {
            toStrip += "android.permission.READ_PHONE_STATE"
            toStrip += "android.permission.READ_PHONE_NUMBERS"
            toStrip += "android.permission.CALL_PHONE"
            toStrip += "android.permission.ANSWER_PHONE_CALLS"
            toStrip += "android.permission.READ_CALL_LOG"
            toStrip += "android.permission.WRITE_CALL_LOG"
            toStrip += "android.permission.PROCESS_OUTGOING_CALLS"
        }
        if (removeStorage == true) {
            toStrip += "android.permission.READ_EXTERNAL_STORAGE"
            toStrip += "android.permission.WRITE_EXTERNAL_STORAGE"
            toStrip += "android.permission.MANAGE_EXTERNAL_STORAGE"
            toStrip += "android.permission.READ_MEDIA_IMAGES"
            toStrip += "android.permission.READ_MEDIA_VIDEO"
            toStrip += "android.permission.READ_MEDIA_AUDIO"
            toStrip += "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        }
        if (removeCalendar == true) {
            toStrip += "android.permission.READ_CALENDAR"
            toStrip += "android.permission.WRITE_CALENDAR"
        }
        if (removeSensors == true) {
            toStrip += "android.permission.BODY_SENSORS"
            toStrip += "android.permission.BODY_SENSORS_BACKGROUND"
        }
        if (removeBluetooth == true) {
            toStrip += "android.permission.BLUETOOTH"
            toStrip += "android.permission.BLUETOOTH_ADMIN"
            toStrip += "android.permission.BLUETOOTH_CONNECT"
            toStrip += "android.permission.BLUETOOTH_SCAN"
            toStrip += "android.permission.BLUETOOTH_ADVERTISE"
        }
        if (removeNotifications == true) {
            toStrip += "android.permission.POST_NOTIFICATIONS"
        }
        if (removeAdId == true) {
            toStrip += "com.google.android.gms.permission.AD_ID"
            toStrip += "android.permission.ACCESS_ADSERVICES_AD_ID"
            toStrip += "android.permission.ACCESS_ADSERVICES_ATTRIBUTION"
            toStrip += "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE"
            toStrip += "android.permission.ACCESS_ADSERVICES_TOPICS"
        }
        if (removeActivityRecognition == true) {
            toStrip += "android.permission.ACTIVITY_RECOGNITION"
        }
        if (removeInstallPackages == true) {
            toStrip += "android.permission.REQUEST_INSTALL_PACKAGES"
            toStrip += "android.permission.REQUEST_DELETE_PACKAGES"
        }
        if (removeSystemAlert == true) {
            toStrip += "android.permission.SYSTEM_ALERT_WINDOW"
        }
        if (removeQueryPackages == true) {
            toStrip += "android.permission.QUERY_ALL_PACKAGES"
        }

        if (toStrip.isEmpty()) return@execute

        document("AndroidManifest.xml").use { doc ->
            listOf("uses-permission", "uses-permission-sdk-23").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    val name = el.getAttribute("android:name")
                    if (name in toStrip) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }
        }
    }
}
