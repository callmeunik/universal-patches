package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Bypass uninstall popup — fixed:
 * - No replaceInstruction("nop") → no Collection is empty crash
 * - Null implementation guard
 * - Custom package list still supported
 * - Detect methods → false / return-void
 */
@Suppress("unused")
val bypassUninstallPopupPatch = bytecodePatch(
    name = "Bypass uninstall popup",
    description = "Blocks uninstall-another-app popups. Default tools: MT, NP, HttpCanary, Apktool, Lucky Patcher, etc.",
    default = false,
) {
    val customPackages by stringOption(
        key = "hiddenPackages",
        default = listOf(
            // MT Manager
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "bin.mt.filemanager",
            // NP Manager
            "com.np.manager",
            "com.npmanager",
            "cn.wq.npmanager",
            // HttpCanary / capture
            "com.guoshi.httpcanary",
            "com.guoshi.httpcanary.premium",
            "com.minhui.networkcapture",
            "com.minhui.networkcapture.pro",
            // Apktool / editors
            "ru.maximoff.apktool",
            "ru.maximoff.apktool.m",
            "com.gmail.heagoo.apkeditor",
            "com.gmail.heagoo.apkeditor.pro",
            // Lucky Patcher
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.android.vending.billing.InAppBillingService.LCK",
            // Other
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "org.lsposed.manager",
            "com.termux",
            "com.parallel.space.lite",
            "com.lbe.parallel",
            "com.applisto.appcloner",
        ).joinToString(","),
        title = "Extra package names",
        description = "Comma-separated packages to treat as NOT installed.",
        required = false,
    )

    execute {
        val userPackages = (customPackages ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val blockHints = listOf(
            "uninstall", "un-install", "please delete", "please remove",
            "remove the app", "detected", "not allowed", "forbidden",
            "lucky", "patcher", "chelpus", "httpcanary", "apktool",
            "npmanager", "mt manager", "bin.mt", "magisk", "xposed",
            "lsposed", "frida", "gameguardian", "mod menu", "cheat",
        )

        val detectNameHints = listOf(
            "isinstalled", "isappinstalled", "ispackageinstalled",
            "checkinstalled", "haspackage", "islucky", "ischeat", "ismod",
            "detectapp", "detecttool", "shouldblock", "needuninstall",
            "showuninstall", "ispackageexist", "packageexists",
            "checktools", "checkenv", "securitycheck", "anticheat", "antimod",
        )

        fun String.hasDetectName(): Boolean {
            val lower = lowercase().replace("_", "")
            return detectNameHints.any { lower.contains(it) }
        }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                // ERROR-FREE: skip null implementation
                if (method.instructionsOrNull == null) return@forEach

                val methodName = method.name
                val instructions = method.instructionsOrNull!!.toList()

                // ---- 1) Detection method names → safe result ----
                if (methodName.hasDetectName()) {
                    when (method.returnType) {
                        "Z" -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }
                        "I" -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                    }
                }

                // ---- 2) Collect strings in method ----
                val strings = instructions.mapNotNull {
                    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                }
                val text = strings.joinToString("\n").lowercase()

                val mentionsUserPkg = userPackages.any { pkg ->
                    strings.any { it.equals(pkg, true) || it.contains(pkg, true) }
                }
                val mentionsBlockText = blockHints.any { text.contains(it) }
                val sensitive = mentionsUserPkg || mentionsBlockText

                if (!sensitive) return@forEach

                // ---- 3) Sensitive method: force boolean results false (NO nop) ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val name = reference.name
                    val ret = reference.returnType
                    val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction

                    if (ret == "Z" &&
                        (name == "exists" ||
                            name == "contains" ||
                            name.startsWith("is") ||
                            name.contains("install", true) ||
                            name.contains("detect", true) ||
                            name.contains("found", true))
                    ) {
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }
                }

                // ---- 4) Sensitive void check/show/block methods → return-void ----
                // Avoid killing Activity lifecycle (onCreate/onResume/onStart)
                if (method.returnType == "V" &&
                    methodName != "onCreate" &&
                    methodName != "onResume" &&
                    methodName != "onStart" &&
                    methodName != "onPause" &&
                    methodName != "onDestroy" &&
                    (methodName.contains("check", true) ||
                        methodName.contains("block", true) ||
                        methodName.contains("detect", true) ||
                        methodName.contains("uninstall", true) ||
                        methodName.contains("show", true) ||
                        methodName.contains("warn", true) ||
                        methodName.contains("secure", true) ||
                        methodName.contains("guard", true) ||
                        methodName.contains("dialog", true) ||
                        methodName.contains("popup", true) ||
                        methodName.contains("alert", true))
                ) {
                    method.addInstructions(0, "return-void")
                }
            }
        }
    }
}
