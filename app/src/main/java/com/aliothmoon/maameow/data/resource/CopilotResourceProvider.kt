package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置作业资源扫描器。
 */
class CopilotResourceProvider(private val pathConfig: MaaPathConfig) {

    /**
     * 作业树节点。
     * @param fullPath 叶子节点为文件绝对路径；目录节点为 null
     * @param relativePath 相对 copilot 根目录的路径（用作展开态/列表 key）
     */
    data class Node(
        val name: String,
        val fullPath: String?,
        val relativePath: String,
        val isFolder: Boolean,
        val children: List<Node> = emptyList(),
    )

    private val copilotRootDir: File
        get() = File(pathConfig.resourceDir, COPILOT_DIR)

    suspend fun loadTree(): List<Node> = withContext(Dispatchers.IO) {
        val root = copilotRootDir
        if (!root.isDirectory) emptyList() else buildLevel(root, root)
    }

    private fun buildLevel(dir: File, rootDir: File): List<Node> {
        val entries = dir.listFiles() ?: return emptyList()

        val files = entries.asSequence()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .sortedBy { it.name }
            .map { file ->
                Node(
                    name = file.name,
                    fullPath = file.absolutePath,
                    relativePath = file.relativeTo(rootDir).path,
                    isFolder = false,
                )
            }
            .toList()

        val folders = entries.asSequence()
            .filter { it.isDirectory }
            // `old` 目录排在最后，其余按名称升序
            .sortedWith(compareBy({ it.name.equals(OLD_DIR, ignoreCase = true) }, { it.name }))
            .mapNotNull { sub ->
                val children = buildLevel(sub, rootDir)
                if (children.isEmpty()) {
                    null
                } else {
                    Node(
                        name = sub.name,
                        fullPath = null,
                        relativePath = sub.relativeTo(rootDir).path,
                        isFolder = true,
                        children = children,
                    )
                }
            }
            .toList()

        return files + folders
    }

    companion object {
        private const val COPILOT_DIR = "copilot"
        private const val OLD_DIR = "old"
    }
}
