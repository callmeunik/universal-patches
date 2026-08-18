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

@Suppress("unused")
val bypassUninstallPopupPatch = bytecodePatch(
    name = "Bypass uninstall popup",
    description = "Aggressively blocks uninstall-another-app popups and force-close on detect.",
    default = false,
) {
    val customPackages by stringOption(
        key = "hiddenPackages",
        default = listOf(
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "bin.mt.filemanager",
            "com.np.manager",
            "com.npmanager",
            "cn.wq.npmanager",
            "com.guoshi.httpcanary",
            "com.guoshi.httpcanary.premium",
            "com.minhui.networkcapture",
            "com.minhui.networkcapture.pro",
            "ru.maximoff.apktool",
            "ru.maximoff.apktool.m",
            "com.gmail.heagoo.apkeditor",
            "com.gmail.heagoo.apkeditor.pro",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.android.vending.billing.InAppBillingService.LCK",
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "org.lsposed.manager",
            "com.termux",
            "com.parallel.space.lite",
            "com.lbe.parallel",
            "com.applisto.appcloner",
        ).joinToString(","),
        title = "Extra package names",
        description = "Comma-separated extra packages to treat as not installed.",
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

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                // Collect strings in this method
                val strings = instructions.mapNotNull {
                    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                }
                val text = strings.joinToString("\n").lowercase()

                val mentionsUserPkg = userPackages.any { pkg ->
                    strings.any { it.equals(pkg, true) || it.contains(pkg, true) }
                }
                val mentionsBlockText = blockHints.any { text.contains(it) }
                val sensitive = mentionsUserPkg || mentionsBlockText

                // ---------- 1) Detection method names → always safe ----------
                val detectName =
                    methodName.contains("isInstalled", true) ||
                        methodName.contains("isAppInstalled", true) ||
                        methodName.contains("isPackageInstalled", true) ||
                        methodName.contains("packageInstalled", true) ||
                        methodName.contains("checkInstalled", true) ||
                        methodName.contains("hasPackage", true) ||
                        methodName.contains("isLucky", true) ||
                        methodName.contains("isCheat", true) ||
                        methodName.contains("isMod", true) ||
                        methodName.contains("detectApp", true) ||
                        methodName.contains("detectTool", true) ||
                        methodName.contains("shouldBlock", true) ||
                        methodName.contains("needUninstall", true) ||
                        methodName.contains("showUninstall", true) ||
                        methodName.contains("isPackageExist", true) ||
                        methodName.contains("packageExists", true) ||
                        methodName.contains("checkTools", true) ||
                        methodName.contains("checkEnv", true) ||
                        methodName.contains("securityCheck", true) ||
                        methodName.contains("antiCheat", true) ||
                        methodName.contains("antiMod", true)

                if (detectName) {
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

                if (!sensitive) return@forEach

                // ---------- 2) Sensitive methods: kill dialog / exit / finish ----------
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType
                    val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction

                    // Dialog / AlertDialog show → nop
                    if ((def.contains("AlertDialog") ||
                            def.contains("Dialog;") ||
                            def.contains("DialogBuilder") ||
                            def.contains("MaterialAlertDialog") ||
                            def.contains("androidx/appcompat/app/AlertDialog")
                            ) &&
                        (name == "show" || name == "create")
                    ) {
                        method.replaceInstruction(index, "nop")
                    }

                    // Toast about uninstall — optional nop show
                    if (def == "Landroid/widget/Toast;" && name == "show") {
                        method.replaceInstruction(index, "nop")
                    }

                    // Activity.finish / finishAffinity → nop (stop close)
                    if ((def == "Landroid/app/Activity;" ||
                            def.contains("Activity;")
                            ) &&
                        (name == "finish" ||
                            name == "finishAffinity" ||
                            name == "finishAndRemoveTask")
                    ) {
                        method.replaceInstruction(index, "nop")
                    }

                    // System.exit / killProcess → nop
                    if (def == "Ljava/lang/System;" && name == "exit") {
                        method.replaceInstruction(index, "nop")
                    }
                    if (def == "Landroid/os/Process;" &&
                        (name == "killProcess" || name == "myPid")
                    ) {
                        // only kill killProcess invoke
                        if (name == "killProcess") {
                            method.replaceInstruction(index, "nop")
                        }
                    }

                    // Boolean results in sensitive method → false
                    if (ret == "Z" &&
                        (name == "exists" ||
                            name == "contains" ||
                            name.startsWith("is") ||
                            name.contains("install", true) ||
                            name.contains("detect", true))
                    ) {
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }

                    // getPackageInfo / getApplicationInfo in sensitive method:
                    // cannot easily throw; boolean paths above cover many apps
                }

                // ---------- 3) Whole void check/show method in sensitive context ----------
                if (method.returnType == "V" &&
                    methodName != "onCreate" &&
                    methodName != "onResume" &&
                    methodName != "onStart" &&
                    (methodName.contains("check", true) ||
                        methodName.contains("block", true) ||
                        methodName.contains("detect", true) ||
                        methodName.contains("uninstall", true) ||
                        methodName.contains("show", true) ||
                        methodName.contains("warn", true) ||
                        methodName.contains("secure", true) ||
                        methodName.contains("guard", true))
                ) {
                    method.addInstructions(0, "return-void")
                }
            }
        }
    }
}
