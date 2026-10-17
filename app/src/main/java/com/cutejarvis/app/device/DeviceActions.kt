package com.cutejarvis.app.device

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import kotlinx.serialization.json.*
import java.time.OffsetDateTime

/** Executes a validated skill on the device through official Android intents/APIs. Never runs shell or arbitrary code. */
class DeviceActions(private val ctx: Context) {
    class Outcome(val ok: Boolean, val message: String)

    private fun launch(i: Intent, okMsg: String): Outcome {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(ctx.packageManager) == null) return Outcome(false, "No app on this phone can handle that.")
        return try { ctx.startActivity(i); Outcome(true, okMsg) } catch (e: Exception) { Outcome(false, "Couldn't start it: ${e.message}") }
    }

    fun execute(skillId: String, a: JsonObject): Outcome = try {
        when (skillId) {
            "open_url" -> {
                val url = a.str("url")
                if (!url.startsWith("http://") && !url.startsWith("https://")) Outcome(false, "Only web links are allowed.")
                else launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Opened the link.")
            }
            "open_settings" -> {
                val action = when (a.str("screen")) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                launch(Intent(action), "Opened settings.")
            }
            "set_alarm" -> launch(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, a.int("hour")).putExtra(AlarmClock.EXTRA_MINUTES, a.int("minute"))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, a["label"]?.jsonPrimitive?.contentOrNull ?: "")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                "Sent the alarm to your clock app — check it to confirm.",
            )
            "create_calendar_event" -> {
                val start = OffsetDateTime.parse(a.str("startIso")).toInstant().toEpochMilli()
                val end = a["endIso"]?.jsonPrimitive?.contentOrNull?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: (start + 3_600_000)
                launch(
                    Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, a.str("title"))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end),
                    "Opened the calendar editor — tap Save there to finish.",
                )
            }
            "start_navigation" -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.str("destination")))), "Started navigation search.")
            "share_text" -> launch(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.str("text")), null),
                "Opened the share sheet.",
            )
            "open_app" -> {
                val want = a.str("appName").trim().lowercase()
                val pm = ctx.packageManager
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val hit = pm.queryIntentActivities(main, 0).firstOrNull { it.loadLabel(pm).toString().lowercase() == want }
                    ?: pm.queryIntentActivities(main, 0).firstOrNull { it.loadLabel(pm).toString().lowercase().contains(want) }
                val i = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
                if (i == null) Outcome(false, "I couldn't find an installed app called \"${a.str("appName")}\".") else launch(i, "Opened ${hit!!.loadLabel(pm)}.")
            }
            "toggle_flashlight" -> {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                if (id == null) Outcome(false, "This phone has no flashlight.")
                else { cm.setTorchMode(id, a["on"]!!.jsonPrimitive.boolean); Outcome(true, if (a["on"]!!.jsonPrimitive.boolean) "Flashlight on." else "Flashlight off.") }
            }
            "set_volume" -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, a.int("percent") * max / 100, 0)
                val now = am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
                Outcome(true, "Media volume is now about $now%.") // read back, not assumed
            }
            else -> Outcome(false, "This phone doesn't support the skill \"$skillId\" yet.")
        }
    } catch (e: SecurityException) { Outcome(false, "Android denied permission: ${e.message}")
    } catch (e: Exception) { Outcome(false, "Failed: ${e.message}") }

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun JsonObject.int(k: String) = this[k]!!.jsonPrimitive.int
}
