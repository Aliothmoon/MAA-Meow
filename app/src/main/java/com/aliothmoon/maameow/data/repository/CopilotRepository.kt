package com.aliothmoon.maameow.data.repository

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class CopilotRepository(
    private val pathConfig: MaaPathConfig,
) {
    companion object {
        private const val TAG = "CopilotRepository"
        private const val COPILOT_DIR = "copilot"
        private const val TASK_LIST_FILE = "task_list.json"
        private const val CONFIG_FILE = "config.json"
    }

    private val copilotDir: File by lazy {
        File(pathConfig.rootDir, COPILOT_DIR).also { it.mkdirs() }
    }

    private val taskListFile: File by lazy { File(copilotDir, TASK_LIST_FILE) }
    private val configFile: File by lazy { File(copilotDir, CONFIG_FILE) }

    /**
     * 保存 copilot JSON 到文件
     * @param id 作业 ID (用于文件名)
     * @param json 作业 JSON 内容
     * @return 保存的文件绝对路径
     */
    suspend fun saveCopilotJson(id: Int, json: String): String = withContext(Dispatchers.IO) {
        val file = File(copilotDir, "${id}.json")
        file.writeText(json, Charsets.UTF_8)
        Timber.d("$TAG: 作业已保存: ${file.absolutePath}")
        file.absolutePath
    }

    /**
     * 保存 copilot JSON 到文件 (自定义文件名)
     * @param fileName 文件名 (不含路径)
     * @param json 作业 JSON 内容
     * @return 保存的文件绝对路径
     */
    suspend fun saveCopilotJsonByName(fileName: String, json: String): String = withContext(Dispatchers.IO) {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val ext = fileName.substringAfterLast(".", "json")
        val hash = Integer.toHexString(nameWithoutExt.hashCode()).takeLast(6)
        val safeName = "${nameWithoutExt}_${hash}.${ext}".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(copilotDir, safeName)
        file.writeText(json, Charsets.UTF_8)
        Timber.d("$TAG: 作业已保存: ${file.absolutePath} (原始: $fileName)")
        file.absolutePath
    }

    /**
     * 从文件路径读取 copilot JSON
     */
    suspend fun readCopilotJson(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 读取作业文件失败: $filePath")
            null
        }
    }

    /**
     * 删除 copilot JSON 文件
     */
    suspend fun deleteCopilotJson(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(filePath).delete()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 删除作业文件失败: $filePath")
            false
        }
    }

    /**
     * 清空 copilot 目录
     */
    suspend fun clearCopilotDir() = withContext(Dispatchers.IO) {
        try {
            copilotDir.listFiles()?.forEach { it.delete() }
            Timber.d("$TAG: copilot 目录已清空")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 清空 copilot 目录失败")
        }
    }

    /**
     * 获取 copilot 目录路径
     */
    fun getCopilotDir(): String = copilotDir.absolutePath

    suspend fun loadTaskList(): List<CopilotListItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (!taskListFile.exists()) {
                return@runCatching emptyList()
            }
            JsonUtils.common.decodeFromString<List<CopilotListItem>>(taskListFile.readText(Charsets.UTF_8))
        }.onFailure {
            Timber.e(it, "$TAG: 加载战斗列表失败")
        }.getOrDefault(emptyList())
    }

    suspend fun saveTaskList(items: List<CopilotListItem>) = withContext(Dispatchers.IO) {
        runCatching {
            taskListFile.writeText(JsonUtils.common.encodeToString(items), Charsets.UTF_8)
        }.onFailure {
            Timber.e(it, "$TAG: 保存战斗列表失败")
        }
    }

    suspend fun loadConfig(): CopilotConfig? = withContext(Dispatchers.IO) {
        runCatching {
            if (!configFile.exists()) {
                return@runCatching null
            }
            JsonUtils.common.decodeFromString<CopilotConfig>(configFile.readText(Charsets.UTF_8))
        }.onFailure {
            Timber.e(it, "$TAG: 加载自动战斗配置失败")
        }.getOrNull()
    }

    suspend fun saveConfig(config: CopilotConfig) = withContext(Dispatchers.IO) {
        runCatching {
            configFile.writeText(JsonUtils.common.encodeToString(config), Charsets.UTF_8)
        }.onFailure {
            Timber.e(it, "$TAG: 保存自动战斗配置失败")
        }
    }
}
