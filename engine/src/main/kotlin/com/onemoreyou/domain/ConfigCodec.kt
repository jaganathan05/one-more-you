package com.onemoreyou.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(config: BlockerConfig): String = json.encodeToString(config)

    fun decode(raw: String): BlockerConfig = json.decodeFromString(raw)
}
