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

private val sslBypassUltimateResourcePatch = resourcePatch(
    description = "Relaxes networkSecurityConfig and allows cleartext + user certificates.",
) {
    execute {
        runCatching {
            document("res/xml/network_security_config.xml").use { doc ->
                val root = doc.documentElement ?: return@use
                val baseConfigs = doc.getElementsByTagName("base-config")
                if (baseConfigs.length == 0) {
                    val base = doc.createElement("base-config")
                    base.setAttribute("cleartextTrafficPermitted", "true")
                    val ts = doc.createElement("trust-anchors")
                    val sys = doc.createElement("certificates")
                    sys.setAttribute("src", "system")
                    val user = doc.createElement("certificates")
                    user.setAttribute("src", "user")
                    ts.appendChild(sys)
                    ts.appendChild(user)
                    base.appendChild(ts)
                    root.appendChild(base)
                } else {
                    for (i in 0 until baseConfigs.length) {
                        val base = baseConfigs.item(i) as? Element ?: continue
                        base.setAttribute("cleartextTrafficPermitted", "true")
                        val anchors = base.getElementsByTagName("trust-anchors")
                        if (anchors.length == 0) {
                            val ts = doc.createElement("trust-anchors")
                            val sys = doc.createElement("certificates")
                            sys.setAttribute("src", "system")
                            val user = doc.createElement("certificates")
                            user.setAttribute("src", "user")
                            ts.appendChild(sys)
                            ts.appendChild(user)
                            base.appendChild(ts)
                        } else {
                            val ts = anchors.item(0) as Element
                            var hasUser = false
                            val certs = ts.getElementsByTagName("certificates")
                            for (j in 0 until certs.length) {
                                val c = certs.item(j) as? Element ?: continue
                                if (c.getAttribute("src") == "user") hasUser = true
                            }
                            if (!hasUser) {
                                val user = doc.createElement("certificates")
                                user.setAttribute("src", "user")
                                ts.appendChild(user)
                            }
                        }
                    }
                }
            }
        }

        runCatching {
            document("AndroidManifest.xml").use { doc ->
                val app = doc.getElementsByTagName("application").item(0) as? Element ?: return@use
                app.setAttribute("android:usesCleartextTraffic", "true")
            }
        }
    }
}

@Suppress("unused")
val sslBypassUltimatePatch = bytecodePatch(
    name = "SSL Bypass Ultimate",
    description = "Powerful SSL and certificate pinning bypass (OkHttp, TrustManager, Conscrypt) + trust user certs.",
    default = false,
) {
    dependsOn(sslBypassUltimateResourcePatch)

    execute {
        val pinHints = listOf(
            "checkservertrusted", "checkclienttrusted", "checktrusted",
            "certificatepinner", "certificate_pinner", "pinner",
            "sslpinning", "ssl_pinning", "pinning",
            "trustmanager", "x509trustmanager", "hostnameverifier",
            "verifypin", "checkpin", "validatepin",
        )

        fun String.hasPinHint() = pinHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val className = classDef.type
            val isPinClass =
                className.hasPinHint() ||
                    className.contains("TrustManager", true) ||
                    className.contains("CertificatePinner", true) ||
                    className.contains("HostnameVerifier", true)

            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                if (isPinClass || name.hasPinHint()) {
                    when (method.returnType) {
                        "V" -> {
                            if (name.contains("check", true) ||
                                name.contains("verify", true) ||
                                name.contains("pin", true)
                            ) {
                                method.addInstructions(0, "return-void")
                                return@forEach
                            }
                        }
                        "Z" -> {
                            method.addInstructions(0, """
                                const/4 v0, 0x1
                                return v0
                            """.trimIndent())
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (!ref.name.hasPinHint() && !ref.definingClass.hasPinHint()) {
                        return@forEachIndexed
                    }

                    when (ref.returnType) {
                        "V" -> method.replaceInstruction(index, "nop")
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x1",
                                )
                            }
                        }
                    }
                }
            }
        }

        // X509TrustManager style
        classDefForEach { classDef ->
            if (!classDef.interfaces.any {
                    it.contains("X509TrustManager") || it.contains("TrustManager")
                } && !classDef.type.contains("TrustManager")
            ) {
                return@classDefForEach
            }

            mutableClassDefBy(classDef).methods.forEach { method ->
                when {
                    method.name == "checkServerTrusted" && method.returnType == "V" ->
                        method.addInstructions(0, "return-void")
                    method.name == "checkClientTrusted" && method.returnType == "V" ->
                        method.addInstructions(0, "return-void")
                    method.name == "getAcceptedIssuers" &&
                        method.returnType == "[Ljava/security/cert/X509Certificate;" -> {
                        method.addInstructions(0, """
                            const/4 v0, 0x0
                            new-array v0, v0, [Ljava/security/cert/X509Certificate;
                            return-object v0
                        """.trimIndent())
                    }
                }
            }
        }
    }
}
