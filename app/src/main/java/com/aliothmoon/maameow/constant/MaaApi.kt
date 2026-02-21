package com.aliothmoon.maameow.constant

object MaaApi {
    // 主 API 服务器
    const val MAA_API = "https://api.maa.plus/MaaAssistantArknights/api/"

    // 备用 API 服务器
    const val MAA_API_BACKUP = "https://api2.maa.plus/MaaAssistantArknights/api/"

    // 活动关卡 API 路径
    const val STAGE_ACTIVITY_API = "gui/StageActivityV2.json"

    // 任务配置 API 路径
    const val TASKS_API = "resource/tasks.json"

    /**
     * 获取全球服 tasks.json API 路径
     */
    fun getGlobalTasksApi(clientType: String): String {
        return "resource/global/${clientType}/resource/tasks.json"
    }

    val API_URLS = listOf(
        MAA_API,
        MAA_API_BACKUP
    )


    // MirrorChyan 基础地址
    const val MIRROR_CHYAN_BASE = "https://mirrorchyan.com/"

    // MirrorChyan 更新源
    const val MIRROR_CHYAN_RESOURCE = "https://mirrorchyan.com/api/resources/MaaResource/latest"

    // GitHub 资源直链
    const val GITHUB_RESOURCE =
        "https://github.com/MaaAssistantArknights/MaaResource/archive/refs/heads/main.zip"

    // ==================== App 更新 ====================

    // GitHub 仓库
    const val APP_GITHUB_OWNER = "Aliothmoon"
    const val APP_GITHUB_REPO = "MAA-Meow"

    // GitHub Release API (包含 prerelease)
    const val APP_GITHUB_RELEASES =
        "https://api.github.com/repos/$APP_GITHUB_OWNER/$APP_GITHUB_REPO/releases?per_page=1"

    // MirrorChyan App 更新源
    const val MIRROR_CHYAN_APP_RESOURCE = "https://mirrorchyan.com/api/resources/MAA-Meow/latest"

    // ==================== PRTS Plus (Copilot) ====================

    // PRTS Plus 基础地址
    const val PRTS_PLUS_BASE = "https://prts.maa.plus/"

    // 获取单个作业
    const val PRTS_COPILOT_GET = "${PRTS_PLUS_BASE}copilot/get/"

    // 获取作业集
    const val PRTS_COPILOT_SET_GET = "${PRTS_PLUS_BASE}set/get/"

    // 作业评分
    const val PRTS_COPILOT_RATING = "${PRTS_PLUS_BASE}copilot/rating"

    // PRTS Plus 网页地址 (用于在浏览器中查看作业)
    const val PRTS_PLUS_URL = "https://prts.plus"

    // 地图网页地址 (PRTS 地图工具)
    const val MAP_URL = "https://map.ark-nights.com/"

    const val BASE_SCHEDULING_SCHEMA = "https://maa.plus/docs/zh-cn/protocol/base-scheduling-schema.html"

}