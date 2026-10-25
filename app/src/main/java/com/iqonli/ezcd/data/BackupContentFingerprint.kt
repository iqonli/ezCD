// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** JSON object order and whitespace do not change the content of a backup. */
internal object BackupContentFingerprint {
    fun of(destination: String,payload: String): String {
        val canonical=canonical(JSONObject(payload))
        return MessageDigest.getInstance("SHA-256").digest((destination+"\u0000"+canonical).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun canonical(value: Any?): String = when(value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") { JSONObject.quote(it)+":"+canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",","[","]") { canonical(value.get(it)) }
        null,JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
