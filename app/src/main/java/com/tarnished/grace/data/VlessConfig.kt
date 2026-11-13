package com.tarnished.grace.data

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class VlessConfig(val uri: String) {
    val uuid: String
    val host: String
    val port: Int
    val name: String
    private val query: Map<String, String>
    var connectIp: String? = null

    init {
        val parsed = Uri.parse(uri.trim())
        require(parsed.scheme == "vless")
        uuid = parsed.userInfo.orEmpty()
        host = parsed.host.orEmpty()
        port = if (parsed.port > 0) parsed.port else 443
        require(uuid.isNotEmpty() && host.isNotEmpty())
        name = parsed.fragment?.takeIf { it.isNotBlank() } ?: "$host:$port"
        val map = HashMap<String, String>()
        for (key in parsed.queryParameterNames) {
            parsed.getQueryParameter(key)?.let { map[key] = it }
        }
        query = map
    }

    private fun nz(key: String): String? = query[key]?.takeIf { it.isNotEmpty() }

    fun outbound(tag: String): JSONObject {
        val network = nz("type") ?: "tcp"
        val security = nz("security") ?: "none"
        val user = JSONObject().put("id", uuid).put("encryption", nz("encryption") ?: "none")
        nz("flow")?.let { user.put("flow", it) }
        val stream = JSONObject().put("network", network).put("security", security)
        val hostHeader = nz("host") ?: if (connectIp != null) host else ""

        when (security) {
            "tls" -> {
                val tls = JSONObject().put("serverName", nz("sni") ?: nz("host") ?: host)
                nz("fp")?.let { tls.put("fingerprint", it) }
                nz("alpn")?.let { value ->
                    val alpn = JSONArray()
                    value.split(",").forEach { alpn.put(it) }
                    tls.put("alpn", alpn)
                }
                nz("pcs")?.let { tls.put("pinnedPeerCertSha256", it) }
                nz("vcn")?.let { tls.put("verifyPeerCertByName", it) }
                stream.put("tlsSettings", tls)
            }
            "reality" -> {
                val reality = JSONObject()
                    .put("serverName", nz("sni") ?: "")
                    .put("fingerprint", nz("fp") ?: "chrome")
                    .put("publicKey", nz("pbk") ?: "")
                    .put("shortId", nz("sid") ?: "")
                    .put("spiderX", nz("spx") ?: "")
                stream.put("realitySettings", reality)
            }
        }

        when (network) {
            "ws" -> {
                val ws = JSONObject().put("path", nz("path") ?: "/")
                if (hostHeader.isNotEmpty()) ws.put("headers", JSONObject().put("Host", hostHeader))
                stream.put("wsSettings", ws)
            }
            "grpc" -> {
                stream.put(
                    "grpcSettings",
                    JSONObject().put("serviceName", nz("serviceName") ?: "").put("multiMode", nz("mode") == "multi")
                )
            }
            "httpupgrade" -> {
                stream.put(
                    "httpupgradeSettings",
                    JSONObject().put("path", nz("path") ?: "/").put("host", hostHeader)
                )
            }
            "xhttp", "splithttp" -> {
                stream.put(
                    "xhttpSettings",
                    JSONObject().put("path", nz("path") ?: "/").put("host", hostHeader).put("mode", nz("mode") ?: "auto")
                )
            }
            "tcp" -> {
                if (nz("headerType") == "http") {
                    val request = JSONObject()
                        .put("path", JSONArray().put(nz("path") ?: "/"))
                        .put("headers", JSONObject().put("Host", JSONArray().put(nz("host") ?: host)))
                    val header = JSONObject().put("type", "http").put("request", request)
                    stream.put("tcpSettings", JSONObject().put("header", header))
                }
            }
        }

        val vnext = JSONObject()
            .put("address", connectIp ?: host)
            .put("port", port)
            .put("users", JSONArray().put(user))

        return JSONObject()
            .put("tag", tag)
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
    }

    companion object {
        fun parseAll(source: String): List<VlessConfig> {
            var raw = source
            if (!raw.contains("vless://")) {
                val text = raw.trim()
                val decoded = runCatching { String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                    ?: runCatching { String(Base64.decode(text, Base64.URL_SAFE), Charsets.UTF_8) }.getOrNull()
                if (decoded != null) raw = decoded
            }
            val seen = HashSet<String>()
            val result = ArrayList<VlessConfig>()
            for (line in raw.replace("\r", "").split("\n")) {
                val link = line.trim()
                if (!link.startsWith("vless://") || !seen.add(link)) continue
                runCatching { VlessConfig(link) }.getOrNull()?.let { result.add(it) }
            }
            return result
        }
    }
}
