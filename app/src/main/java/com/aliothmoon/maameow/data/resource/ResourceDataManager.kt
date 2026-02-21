package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.File

class ResourceDataManager(
    val pathConfig: MaaPathConfig
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _characters = MutableStateFlow<Map<String, CharacterInfo>>(emptyMap())
    private val _nameIndex = MutableStateFlow<Map<String, CharacterInfo>>(emptyMap())
    private val _roguelikeCoreCharacters = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val _operators = MutableStateFlow<Map<String, CharacterInfo>>(emptyMap())
    private val _recruitTags = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    private val _mapData = MutableStateFlow<List<MapInfo>>(emptyList())

    val characters: StateFlow<Map<String, CharacterInfo>> = _characters.asStateFlow()
    val operators: StateFlow<Map<String, CharacterInfo>> = _operators.asStateFlow()
    val recruitTags: StateFlow<Map<String, Pair<String, String>>> = _recruitTags.asStateFlow()
    val mapData: StateFlow<List<MapInfo>> = _mapData.asStateFlow()

    companion object {
        private const val BATTLE_DATA_FILE = "battle_data.json"
        private const val ROGUELIKE_DIR = "roguelike"
        private const val RECRUITMENT_FILE = "recruitment.json"
        private const val MAP_DATA_FILE = "Arknights-Tile-Pos/overview.json"

        // 虚拟干员 (预备干员、肉鸽临时干员、阿米娅变体)
        private val VIRTUAL_OPERATORS = setOf(
            "char_504_rguard", // 预备干员-近战
            "char_505_rcast",  // 预备干员-术师
            "char_506_rmedic", // 预备干员-后勤
            "char_507_rsnipe", // 预备干员-狙击
            "char_508_aguard", // Sharp
            "char_509_acast",  // Pith
            "char_510_amedic", // Touch
            "char_511_asnipe", // Stormeye
            "char_512_aprot",  // 暮落
            "char_513_apionr", // 郁金香
            "char_514_rdfend", // 预备干员-重装

            // 因为 core 是通过名字来判断的，所以下面干员中如果有和上面重名的不会用到，不过也加上了
            "char_600_cpione", // 预备干员-先锋 4★
            "char_601_cguard", // 预备干员-近卫 4★
            "char_602_cdfend", // 预备干员-重装 4★
            "char_603_csnipe", // 预备干员-狙击 4★
            "char_604_ccast",  // 预备干员-术师 4★
            "char_605_cmedic", // 预备干员-医疗 4★
            "char_606_csuppo", // 预备干员-辅助 4★
            "char_607_cspec",  // 预备干员-特种 4★
            "char_608_acpion", // 郁金香 6★
            "char_609_acguad", // Sharp 6★
            "char_610_acfend", // Mechanist 6★
            "char_611_acnipe", // Stormeye 6★
            "char_612_accast", // Pith 6★
            "char_613_acmedc", // Touch 6★
            "char_614_acsupo", // Raidian 6★
            "char_615_acspec", // Misery 6★
            "char_616_pithst", // 盟约·辅助干员
            "char_617_sharp2", // 领主·Sharp

            "char_1001_amiya2", // 阿米娅-WARRIOR
            "char_1037_amiya3", // 阿米娅-MEDIC
        )

        // 语言代码 → 资源子目录
        val CLIENT_DIRECTORY_MAPPER = mapOf(
            "zh-cn" to "",
            "zh-tw" to "txwy",
            "en-us" to "YoStarEN",
            "ja-jp" to "YoStarJP",
            "ko-kr" to "YoStarKR"
        )

        // 客户端类型 → 语言代码
        val CLIENT_LANGUAGE_MAPPER = mapOf(
            "Official" to "zh-cn",
            "Bilibili" to "zh-cn",
            "txwy" to "zh-tw",
            "YoStarEN" to "en-us",
            "YoStarJP" to "ja-jp",
            "YoStarKR" to "ko-kr"
        )
    }

    suspend fun load() {
        withContext(Dispatchers.IO) {
            val characters = doLoadCharactersFromFile()
            _characters.value = characters
            _operators.value = characters.filter { (id, info) ->
                info.isOperator && id !in VIRTUAL_OPERATORS
            }
            _nameIndex.value = doBuildNameIndex(characters)
            _roguelikeCoreCharacters.value = doLoadRoguelikeThemes()
            _recruitTags.value = doLoadRecruitTags()
            _mapData.value = doLoadMapData()
        }
    }

    fun isValidCharacterName(name: String): Boolean {
        if (name.isBlank()) return true
        return getCharacterByNameOrAlias(name) != null
    }

    fun getCharacterByNameOrAlias(name: String): CharacterInfo? {
        if (name.isBlank()) return null
        return _nameIndex.value[name.lowercase()]
    }

    fun getCharacterById(id: String): CharacterInfo? {
        return _characters.value[id]
    }

    fun getCharacterByCodeName(codeName: String): CharacterInfo? {
        if (codeName.isBlank()) return null
        val lowerCode = codeName.lowercase()
        return _characters.value.values.firstOrNull { it.codeName == lowerCode }
    }

    /**
     * 获取干员的本地化名称
     * @param language 语言代码: zh-cn, zh-tw, en-us, ja-jp, ko-kr
     */
    fun getLocalizedCharacterName(characterName: String?, language: String = "zh-cn"): String? {
        if (characterName.isNullOrBlank()) return null
        val info = getCharacterByNameOrAlias(characterName) ?: return characterName
        return getLocalizedCharacterName(info, language)
    }

    fun getLocalizedCharacterName(info: CharacterInfo, language: String = "zh-cn"): String? {
        return when (language) {
            "zh-cn" -> info.name
            "zh-tw" -> info.nameTw ?: info.name
            "en-us" -> info.nameEn ?: info.name
            "ja-jp" -> info.nameJp ?: info.name
            "ko-kr" -> info.nameKr ?: info.name
            else -> info.name
        }
    }

    /**
     * 判断干员在指定客户端是否可用 (是否已实装)
     * @param clientType 客户端类型或语言代码
     */
    fun isCharacterAvailableInClient(character: CharacterInfo?, clientType: String): Boolean {
        if (character == null) return false
        return when (clientType) {
            "zh-tw", "txwy" -> !character.nameTwUnavailable
            "en-us", "YoStarEN" -> !character.nameEnUnavailable
            "ja-jp", "YoStarJP" -> !character.nameJpUnavailable
            "ko-kr", "YoStarKR" -> !character.nameKrUnavailable
            else -> true // 国服默认全部可用
        }
    }

    fun isCharacterAvailableInClient(characterName: String, clientType: String): Boolean {
        val character = getCharacterByNameOrAlias(characterName)
        return isCharacterAvailableInClient(character, clientType)
    }

    fun search(query: String, limit: Int = 20): List<String> {
        if (query.isBlank()) return emptyList()

        val q = query.lowercase()
        val index = _nameIndex.value

        // 先找精确匹配，再找包含匹配
        val exactMatch = index[q]?.name
        val containsMatches =
            index.entries.filter { it.key.contains(q) && it.key != q }
                .map { it.value.name }
                .distinct()
                .take(limit - if (exactMatch != null) 1 else 0)

        return if (exactMatch != null) {
            listOf(exactMatch) + containsMatches
        } else {
            containsMatches
        }
    }

    fun getRoguelikeCoreCharList(theme: String): List<String> {
        return _roguelikeCoreCharacters.value[theme] ?: emptyList()
    }

    /**
     * 查找地图信息 (按 Code/Name/StageId/LevelId 匹配)
     */
    fun findMap(mapId: String): MapInfo? {
        if (mapId.isBlank()) return null
        val maps = _mapData.value
        return maps.firstOrNull { it.code == mapId }
            ?: maps.firstOrNull { it.name == mapId }
            ?: maps.firstOrNull { it.stageId == mapId }
            ?: maps.firstOrNull { it.levelId == mapId }
    }

    // ---- 数据加载 ----

    private fun doLoadCharactersFromFile(): Map<String, CharacterInfo> {
        try {
            val file = File(pathConfig.resourceDir, BATTLE_DATA_FILE)
            if (!file.exists()) {
                Timber.w("$BATTLE_DATA_FILE 不存在: ${file.absolutePath}")
                return emptyMap()
            }

            val content = file.readText()
            return doParseBattleDataJson(content)
        } catch (e: Exception) {
            Timber.e(e, "加载 battle_data.json 失败")
            return emptyMap()
        }
    }

    private fun doLoadRoguelikeThemes(clientType: String = "Official"): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        try {
            val dir = File(pathConfig.resourceDir, ROGUELIKE_DIR)
            if (!dir.isDirectory) return emptyMap()

            val language = CLIENT_LANGUAGE_MAPPER[clientType] ?: "zh-cn"

            dir.listFiles()?.filter { it.isDirectory }?.forEach { theme ->
                val file = File(theme, RECRUITMENT_FILE)
                if (file.exists()) {
                    try {
                        val content = file.readText()
                        val rawNames = doParseRecruitmentJson(content)
                        // 过滤: 干员在当前客户端可用 + 获取本地化名称
                        val filtered = rawNames.mapNotNull { name ->
                            val info = getCharacterByNameOrAlias(name)
                                ?: return@mapNotNull name
                            if (!isCharacterAvailableInClient(info, clientType)) {
                                return@mapNotNull null
                            }
                            getLocalizedCharacterName(info, language) ?: name
                        }.sorted()
                        result[theme.name] = filtered
                        Timber.d("doLoadRoguelikeThemes ${theme.name}: ${filtered.size} characters")
                    } catch (e: Exception) {
                        Timber.w(e, "doLoadRoguelikeThemes error: ${theme.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "doLoadRoguelikeThemes error")
        }
        return result
    }

    /**
     * 加载公招标签
     * @param clientType 客户端类型 (决定客户端侧标签语言)
     * @param displayLanguage 显示语言 (决定界面展示标签语言)
     * @return Map<标签中文名, Pair<显示名, 客户端名>>
     */
    private fun doLoadRecruitTags(
        clientType: String = "Official",
        displayLanguage: String = "zh-cn"
    ): Map<String, Pair<String, String>> {
        try {
            // 客户端标签路径
            val clientSubPath = when (clientType) {
                "", "Official", "Bilibili" -> ""
                else -> "global/$clientType/resource"
            }
            // 显示语言标签路径
            val displaySubPath = when (displayLanguage) {
                "zh-tw", "en-us", "ja-jp", "ko-kr" -> {
                    val dir = CLIENT_DIRECTORY_MAPPER[displayLanguage] ?: ""
                    if (dir.isNotEmpty()) "global/$dir/resource" else ""
                }

                else -> "" // zh-cn: 根目录
            }

            val clientFile = if (clientSubPath.isEmpty()) {
                File(pathConfig.resourceDir, RECRUITMENT_FILE)
            } else {
                File(pathConfig.resourceDir, "$clientSubPath/$RECRUITMENT_FILE")
            }
            val clientTags = doParseRecruitTags(clientFile)

            val displayTags = if (displaySubPath == clientSubPath) {
                clientTags
            } else {
                val displayFile = if (displaySubPath.isEmpty()) {
                    File(pathConfig.resourceDir, RECRUITMENT_FILE)
                } else {
                    File(pathConfig.resourceDir, "$displaySubPath/$RECRUITMENT_FILE")
                }
                doParseRecruitTags(displayFile)
            }

            // 合并: key=标签中文名, value=(显示名, 客户端名)
            return clientTags.mapNotNull { (key, clientName) ->
                if (clientName.isBlank()) return@mapNotNull null
                val displayName = displayTags[key] ?: clientName
                key to (displayName to clientName)
            }.toMap()
        } catch (e: Exception) {
            Timber.e(e, "加载公招标签失败")
            return emptyMap()
        }
    }

    private fun doLoadMapData(): List<MapInfo> {
        try {
            val file = File(pathConfig.resourceDir, MAP_DATA_FILE)
            if (!file.exists()) {
                Timber.w("地图数据文件不存在: ${file.absolutePath}")
                return emptyList()
            }

            val content = file.readText()
            val mapObj = json.parseToJsonElement(content).jsonObject
            return mapObj.values.mapNotNull { element ->
                try {
                    json.decodeFromJsonElement<MapInfo>(element)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "加载地图数据失败")
            return emptyList()
        }
    }

    // ---- 解析方法 ----

    private fun doBuildNameIndex(characters: Map<String, CharacterInfo>): Map<String, CharacterInfo> {
        val index = mutableMapOf<String, CharacterInfo>()

        for (character in characters.values) {
            // 使用 putIfAbsent: 重名时保留先出现的角色 (与 WPF TryAdd 行为一致)
            character.name.takeIf { it.isNotBlank() }?.let {
                index.putIfAbsent(it.lowercase(), character)
            }
            character.nameEn?.takeIf { it.isNotBlank() }?.let {
                index.putIfAbsent(it.lowercase(), character)
            }
            character.nameJp?.takeIf { it.isNotBlank() }?.let {
                index.putIfAbsent(it.lowercase(), character)
            }
            character.nameKr?.takeIf { it.isNotBlank() }?.let {
                index.putIfAbsent(it.lowercase(), character)
            }
            character.nameTw?.takeIf { it.isNotBlank() }?.let {
                index.putIfAbsent(it.lowercase(), character)
            }
        }

        return index
    }

    private fun doParseBattleDataJson(content: String): Map<String, CharacterInfo> {
        val obj = json.parseToJsonElement(content).jsonObject
        val chars = obj["chars"]?.jsonObject ?: return emptyMap()

        return chars.mapNotNull { (id, element) ->
            try {
                val info = json.decodeFromJsonElement<CharacterInfo>(element).copy(id = id)
                id to info
            } catch (e: Exception) {
                Timber.w(e, "doParseBattleDataJson error: $id")
                null
            }
        }.toMap()
    }

    private fun doParseRecruitmentJson(content: String): List<String> {
        val characters = mutableSetOf<String>()

        try {
            val obj = json.parseToJsonElement(content).jsonObject
            val priority = obj["priority"]?.jsonArray ?: return emptyList()
            for (item in priority) {
                val opers = item.jsonObject["opers"]?.jsonArray ?: continue

                for (operItem in opers) {
                    val operObj = operItem.jsonObject
                    val isStart = operObj["is_start"]?.jsonPrimitive?.boolean ?: false

                    if (isStart) {
                        val name = operObj["name"]?.jsonPrimitive?.contentOrNull
                        if (!name.isNullOrBlank()) {
                            characters.add(name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "parseRecruitmentJson error")
        }

        return characters.toList().sorted()
    }

    private fun doParseRecruitTags(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        try {
            val content = file.readText()
            val obj = json.parseToJsonElement(content).jsonObject
            val tags = obj["tags"]?.jsonObject ?: return emptyMap()
            return tags.entries.associate { (key, value) ->
                key to (value.jsonPrimitive.contentOrNull ?: key)
            }
        } catch (e: Exception) {
            Timber.w(e, "解析公招标签失败: ${file.absolutePath}")
            return emptyMap()
        }
    }
}
