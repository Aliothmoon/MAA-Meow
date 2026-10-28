package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.domain.models.GameMetadataKey

/**
 * 一档界面元数据，整体发布
 * 读者拿到的各张表总属于同一档，[key] 为 null 表示还没加载
 */
class GameMetadata(
    val key: GameMetadataKey?,
    val characters: Map<String, CharacterInfo>,
    val operators: Map<String, CharacterInfo>,
    val nameIndex: Map<String, CharacterInfo>,
    /** 当前语言与客户端类型下的干员名集合, see WPF: DataHelper.CharacterNames */
    val characterNames: Set<String>,
    /** 各肉鸽主题的推荐开局干员，已按客户端过滤并本地化 */
    val roguelikeCoreCharacters: Map<String, List<String>>,
    /** key=标签中文名, value=(显示名, 客户端名) */
    val recruitTags: Map<String, Pair<String, String>>,
    val mapData: List<MapInfo>,
) {
    companion object {
        val EMPTY = GameMetadata(
            key = null,
            characters = emptyMap(),
            operators = emptyMap(),
            nameIndex = emptyMap(),
            characterNames = emptySet(),
            roguelikeCoreCharacters = emptyMap(),
            recruitTags = emptyMap(),
            mapData = emptyList(),
        )
    }
}
