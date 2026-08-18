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

/**
 * Layer 1: Network security config — allow user CAs / cleartext where possible
 */
private val sslBypassResourcePatch = resourcePatch(
    description = "Relaxes networkSecurityConfig to trust user certificates.",
) {
    execute {
        // Try to patch network_security_config if present
        runCatching {
            document("res/xml/network_security_config.xml").use { doc ->
                val root = doc.documentElement ?: return@use
                // Ensure base-config trusts user certs
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

        // Also set usesCleartextTraffic on application
        runCatching {
            document("AndroidManifest.xml").use { doc ->
                val app = doc.getElementsByTagName("application").item(0) as? Element ?: return@use
                app.setAttribute("android:usesCleartextTraffic", "true")
                // If no networkSecurityConfig, leave as is; resource patch above handles xml if exists
            }
        }
    }
}

/**
 * Layer 2: Bytecode SSL / pinning bypass
 */
@Suppress("unused")
val sslBypassPatch = bytecodePatch(
    name = "SSL bypass",
    description = "Powerful SSL and certificate pinning bypass for common libraries (OkHttp, TrustManager, etc.).",
    default = false,
) {
    dependsOn(sslBypassResourcePatch)

    execute {
        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val className = classDef.type
                val instructions = method.instructionsOrNull?.toList()

                // ---- A) Neutralize known pinning / trust methods inside their classes ----
                val isSslRelatedClass =
                    className.contains("TrustManager", true) ||
                        className.contains("CertificatePinner", true) ||
                        className.contains("PinningTrustManager", true) ||
                        className.contains("SSLContext", true) ||
                        className.contains("NetworkSecurityTrustManager", true) ||
                        className.contains("OkHostnameVerifier", true) ||
                        className.contains("HostnameVerifier", true) ||
                        className.contains("X509TrustManager", true)

                if (isSslRelatedClass && instructions != null) {
                    when {
                        // HostnameVerifier.verify → always true
                        methodName == "verify" && method.returnType == "Z" -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x1
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }

                        // check / checkServerTrusted → return-void
                        (methodName == "checkServerTrusted" ||
                            methodName == "checkClientTrusted" ||
                            methodName == "check") && method.returnType == "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }

                        // CertificatePinner.findMatchingPins / check → empty / return
                        methodName.contains("check", true) &&
                            className.contains("CertificatePinner", true) -> {
                            if (method.returnType == "V") {
                                method.addInstructions(0, "return-void")
                                return@forEach
                            }
                        }
                    }
                }

                if (instructions == null) return@forEach

                // ---- B) Patch call-sites ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType

                    // HostnameVerifier.verify(...) → force true after move-result
                    if ((def.contains("HostnameVerifier") || name == "verify") &&
                        ret == "Z" &&
                        (def.contains("javax/net/ssl") ||
                            def.contains("HostnameVerifier") ||
                            def.contains("OkHostnameVerifier"))
                    ) {
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x1",
                            )
                        }
                    }

                    // TrustManager checkServerTrusted → ignore (void)
                    if (name == "checkServerTrusted" || name == "checkClientTrusted") {
                        // leave invoke; method body neutered when class matched above
                    }

                    // CertificatePinner.check
                    if (def.contains("CertificatePinner") && name == "check" && ret == "V") {
                        method.replaceInstruction(index, "nop")
                    }

                    // SSLContext.init — hard to fully replace safely; skip
                }
            }
        }

        // ---- C) Extra: X509TrustManager empty implementation style methods ----
        classDefForEach { classDef ->
            if (!classDef.interfaces.any {
                    it.contains("X509TrustManager") || it.contains("TrustManager")
                } &&
                !classDef.type.contains("TrustManager")
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
                        method.addInstructions(
                            0,
                            """
                                const/4 v0, 0x0
                                new-array v0, v0, [Ljava/security/cert/X509Certificate;
                                return-object v0
                            """.trimIndent(),
                        )
                    }
                }
            }
        }
    }
}
