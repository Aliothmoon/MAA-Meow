package com.aliothmoon.maameow.remote

import com.aliothmoon.maameow.constant.MaaFiles

/**
 * MaaCore 独立数据目录（CoreDataLocation.LOCAL_TMP）两侧共用的约定
 *
 * /data/local/tmp 是 ext4 直写，shell / root 都可读写，App 进程不可见
 * 资源由提权进程直接从 APK 解出，App 只投递热更包与用户小文件；core 写的 debug/ 导出日志时拉回
 */
object CoreDataDir {

    /** shell:shell 0771，两种后端都可写 */
    const val ROOT = "/data/local/tmp/maameow"

    const val RESOURCE_ASSET_PREFIX = "assets/${MaaFiles.ASSET_DIR_NAME}/"
    const val OVERRIDES_ASSET_ENTRY = "assets/${MaaFiles.OVERRIDES_ASSET_TASKS}"
    const val OVERRIDES_TASKS_REL = MaaFiles.OVERRIDES_ASSET_TASKS

    /** 热更包顶层目录（GitHub 归档有，镜像源可能没有），两侧过滤规则必须一致 */
    private const val HOT_UPDATE_TOP_DIR = "MaaResource-main/"
    private const val HOT_UPDATE_RESOURCE_DIR = "resource/"

    /** App 写、core 读，整体投递 */
    val USER_DATA_DIRS = listOf(MaaFiles.OVERRIDES, "copilot", "custom_infrast", MaaFiles.OPER_BOX_DATA_DIR)

    /** 热更包 zip 条目 → cache/resource 下的相对路径；不属于资源的条目返回 null；App 侧解包同样用它 */
    fun hotUpdateEntryToRelPath(entryName: String): String? {
        val name = entryName.removePrefix(HOT_UPDATE_TOP_DIR)
        if (!name.startsWith(HOT_UPDATE_RESOURCE_DIR)) return null
        return name.removePrefix(HOT_UPDATE_RESOURCE_DIR).ifEmpty { null }
    }

    /** 跨进程传来的相对路径只允许落在根目录内 */
    fun isSafeRelPath(rel: String): Boolean =
        rel.isNotBlank() && !rel.startsWith("/") && rel.split('/').none { it == ".." || it.isEmpty() }
}
