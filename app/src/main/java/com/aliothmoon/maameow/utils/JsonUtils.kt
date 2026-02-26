package com.aliothmoon.maameow.utils

import kotlinx.serialization.json.Json

object JsonUtils {
    val common = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        decodeEnumsCaseInsensitive = true
    }
}