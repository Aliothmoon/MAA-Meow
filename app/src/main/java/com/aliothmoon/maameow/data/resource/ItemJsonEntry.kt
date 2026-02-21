package com.aliothmoon.maameow.data.resource

import kotlinx.serialization.Serializable

/**
 * see item_index.json
 */
@Serializable
data class ItemJsonEntry(
    val name: String,
    val icon: String = "",
    val classifyType: String? = null,
    val sortId: Int = 0,
    val description: String? = null,
    val usage: String? = null
)
