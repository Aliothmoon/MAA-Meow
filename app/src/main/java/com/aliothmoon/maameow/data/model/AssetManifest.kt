package com.aliothmoon.maameow.data.model

import kotlinx.serialization.Serializable


@Serializable
data class AssetManifest(val files: List<String>)
