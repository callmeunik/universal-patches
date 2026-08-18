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
 * Bypasses "uninstall this app" popups.
 * Default list includes common modding / reverse tools.
 * Extra packages can still be added in options (comma-separated).
 */
@Suppress("unused")
val bypassUninstallPopupPatch = bytecodePatch(
    name = "Bypass uninstall popup",
    description = "Bypasses uninstall-another-app popups. Default: MT Manager, NP Manager, HttpCanary, Apktool M, Lucky Patcher, etc. Add more in options.",
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
            "com.network.proxy",
            "com.minhui.networkcapture",
            "com.minhui.networkcapture.pro",
            "app.greyshirts.sslcapture",
            "com.evezzon.sniffer",
            "com.reqable.android",

            // Apktool M / APK editors
            "ru.maximoff.apktool",
            "ru.maximoff.apktool.m",
            "com.gmail.heagoo.apkeditor",
            "com.gmail.heagoo.apkeditor.pro",
            "com.gmail.heagoo.apkeditor.parser",
            "raxod502.manjaro.apktool",

            // Lucky Patcher / billing
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.android.vending.billing.InAppBillingService.LCK",
            "com.android.vending.billing.InAppBillingService.LOCK",

            // Other common tools
            "com.zune.gameguardian",
            "com.gameguardian",
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "org.lsposed.manager",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "me.weishu.exp",
            "com.solohsu.android.edxp.manager",
            "org.telegram.messenger.web", // sometimes listed by mistake; remove if not needed
            "com.parallel.space.lite",
            "com.lbe.parallel",
            "com.cloneapp.parallelspace.dualspace",
            "com.excelliance.multiaccounts",
            "com.applisto.appcloner",
            "com.kunkun.appmulti",
            "bin.mt.termex",
            "com.termux",
            "com.nolanlawson.logcat",
            "com.a0soft.gphone.app2sd",
            "com.github.metacubex.clash.meta",
            "com.github.kr328.clash",
            "com.v2ray.ang",
            "com.uvstudio.haiiandroid",
        ).joinToString(","),
        title = "Extra / override package names",
        description = "Comma-separated. Defaults already include MT, NP, HttpCanary, Apktool M, Lucky Patcher, etc. Add more if needed.",
        required = false,
    )

    execute {
        val userPackages = (customPackages ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val defaultHints = listOf(
            "lucky",
            "patcher",
            "chelpus",
            "freedom",
            "hapmod",
            "modmenu",
            "gameguardian",
            "xposed",
            "lsposed",
            "magisk",
            "frida",
            "httpcanary",
            "apktool",
            "npmanager",
            "np manager",
            "mt manager",
            "bin.mt",
            "sslcapture",
            "networkcapture",
            "parallel",
            "clone",
            "dualspace",
            "appcloner",
        )

        classDefForEach { classDef ->
            val cn = classDef.type
            val looksLikeGuard =
                cn.contains("Security", true) ||
                    cn.contains("Protect", true) ||
                    cn.contains("Guard", true) ||
                    cn.contains("Detect", true) ||
                    cn.contains("Anti", true) ||
                    cn.contains("Integrity", true) ||
                    cn.contains("Check", true) ||
                    cn.contains("Splash", true) ||
                    cn.contains("MainActivity", true)

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList()

                val isDetectMethod =
                    methodName.contains("isInstalled", true) ||
                        methodName.contains("isAppInstalled", true) ||
                        methodName.contains("isPackageInstalled", true) ||
                        methodName.contains("checkInstalled", true) ||
                        methodName.contains("hasPackage", true) ||
                        methodName.contains("isLucky", true) ||
                        methodName.contains("isCheat", true) ||
                        methodName.contains("isMod", true) ||
                        methodName.contains("detectApp", true) ||
                        methodName.contains("shouldBlock", true) ||
                        methodName.contains("needUninstall", true) ||
                        methodName.contains("showUninstall", true) ||
                        methodName.contains("isPackageExist", true) ||
                        methodName.contains("packageExists", true)

                if (isDetectMethod) {
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
                        "V" -> {
                            if (methodName.contains("show", true) ||
                                methodName.contains("block", true) ||
                                methodName.contains("check", true) ||
                                methodName.contains("uninstall", true) ||
                                methodName.contains("detect", true)
                            ) {
                                method.addInstructions(0, "return-void")
                                return@forEach
                            }
                        }
                    }
                }

                if (instructions == null) return@forEach

                val stringHits = mutableListOf<String>()
                instructions.forEach {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (s != null) stringHits.add(s)
                }

                val mentionsUserPackage = userPackages.any { pkg ->
                    stringHits.any { it.equals(pkg, true) || it.contains(pkg, true) }
                }
                val mentionsUninstall = stringHits.any { s ->
                    val l = s.lowercase()
                    l.contains("uninstall") ||
                        l.contains("remove the app") ||
                        l.contains("please delete") ||
                        l.contains("detected") ||
                        defaultHints.any { h -> l.contains(h) }
                }

                val sensitive = mentionsUserPackage || mentionsUninstall

                if (sensitive) {
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
                                name.contains("install", true))
                        ) {
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0",
                                )
                            }
                        }
                    }

                    if (looksLikeGuard && method.returnType == "V") {
                        if (methodName != "onCreate" &&
                            (methodName.contains("check", true) ||
                                methodName.contains("block", true) ||
                                methodName.contains("detect", true) ||
                                methodName.contains("uninstall", true) ||
                                methodName.contains("show", true))
                        ) {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }
        }
    }
}
