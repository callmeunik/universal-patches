package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private val analyticsKeywords = listOf(
    "com.google.firebase.analytics",
    "com.google.android.gms.analytics",
    "com.google.android.gms.measurement",
    "com.google.firebase.messaging",
    "com.google.firebase.crashlytics",
    "com.google.firebase.perf",
    "com.google.firebase.iid",
    "com.google.android.gms.ads.identifier",
    "com.appsflyer",
    "com.adjust",
    "com.kochava",
    "com.singular",
    "io.branch",
    "com.flurry",
    "com.mixpanel",
    "com.amplitude",
    "com.segment",
    "FirebaseAnalytics",
    "FirebaseCrashlytics",
    "MeasurementService",
    "AnalyticsService",
    "Crashlytics",
)

private fun String.isAnalyticsRelated(): Boolean =
    analyticsKeywords.any { contains(it, ignoreCase = true) }

private val disableFirebaseResourcePatch = resourcePatch(
    description = "Removes Firebase / analytics components from the manifest.",
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            listOf("activity", "service", "receiver", "provider").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    if (el.getAttribute("android:name").isAnalyticsRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }

            val meta = doc.getElementsByTagName("meta-data")
            val metaRemove = mutableListOf<org.w3c.dom.Node>()
            for (i in 0 until meta.length) {
                val el = meta.item(i) as? Element ?: continue
                val n = el.getAttribute("android:name")
                val v = el.getAttribute("android:value")
                if (n.isAnalyticsRelated() || v.isAnalyticsRelated() ||
                    n.contains("firebase", true) ||
                    n.contains("crashlytics", true) ||
                    n.contains("analytics", true)
                ) {
                    metaRemove.add(el)
                }
            }
            metaRemove.forEach { it.parentNode?.removeChild(it) }
        }
    }
}

@Suppress("unused")
val disableFirebaseAnalyticsPatch = bytecodePatch(
    name = "Disable Firebase / Analytics",
    description = "Removes Firebase/analytics components and disables common logEvent / track calls.",
    default = false,
) {
    dependsOn(disableFirebaseResourcePatch)

    execute {
        classDefForEach { classDef ->
            val isAnalyticsClass = classDef.type.isAnalyticsRelated()

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList()

                if (isAnalyticsClass) {
                    when {
                        method.returnType == "V" &&
                            (methodName.contains("log", true) ||
                                methodName.contains("track", true) ||
                                methodName.contains("record", true) ||
                                methodName.contains("send", true) ||
                                methodName.contains("setUser", true) ||
                                methodName.contains("setAnalytics", true) ||
                                methodName.contains("logEvent", true) ||
                                methodName.contains("report", true) ||
                                methodName == "onStart" ||
                                methodName == "onCreate") -> {
                            // Don't always kill onCreate of everything — only event-like names
                            if (methodName.contains("log", true) ||
                                methodName.contains("track", true) ||
                                methodName.contains("record", true) ||
                                methodName.contains("send", true) ||
                                methodName.contains("logEvent", true) ||
                                methodName.contains("report", true) ||
                                methodName.contains("setUser", true)
                            ) {
                                method.addInstructions(0, "return-void")
                                return@forEach
                            }
                        }

                        method.returnType == "Z" &&
                            (methodName.contains("isEnabled", true) ||
                                methodName.contains("isAnalytics", true)) -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name

                    val isLogCall =
                        name.equals("logEvent", true) ||
                            name.equals("log", true) ||
                            name.equals("track", true) ||
                            name.equals("trackEvent", true) ||
                            name.equals("recordEvent", true) ||
                            name.equals("sendEvent", true) ||
                            (def.isAnalyticsRelated() && name.contains("log", true))

                    if (isLogCall && reference.returnType == "V") {
                        method.replaceInstruction(index, "nop")
                    }

                    // FirebaseAnalytics.getInstance still returns object; event methods nop'd above
                    if (name.equals("setAnalyticsCollectionEnabled", true)) {
                        // force disabled if boolean arg path is complex — nop call
                        method.replaceInstruction(index, "nop")
                    }
                }
            }
        }
    }
}
