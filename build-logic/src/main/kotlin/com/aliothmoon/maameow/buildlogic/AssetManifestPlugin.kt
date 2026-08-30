package com.aliothmoon.maameow.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

// 生成 MaaResource 目录内全部文件的 JSON 清单
abstract class GenerateAssetManifestTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val sourceDir: DirectoryProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @get:Input
    abstract val assetSourceDir: Property<String>

    @TaskAction
    fun generate() {
        val source = sourceDir.orNull?.asFile
        val manifest = manifestFile.get().asFile

        manifest.parentFile?.mkdirs()

        val files = if (source?.exists() == true) {
            listFilesRecursively(source, "")
                .map { "${assetSourceDir.get()}/$it" }
                .sorted()
        } else {
            emptyList()
        }

        val jsonContent = """{"files":[${files.joinToString(",") { "\"$it\"" }}]}"""
        manifest.writeText(jsonContent)
        logger.lifecycle("Generated asset manifest: ${files.size} files")
    }

    private fun listFilesRecursively(dir: File, basePath: String): List<String> {
        val result = mutableListOf<String>()
        dir.listFiles()?.forEach { file ->
            val relativePath = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
            if (file.isDirectory) {
                result.addAll(listFilesRecursively(file, relativePath))
            } else {
                result.add(relativePath)
            }
        }
        return result
    }
}

class AssetManifestPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val generateAssetManifest = project.tasks.register(
            "generateAssetManifest",
            GenerateAssetManifestTask::class.java,
        ) {
            description = "Generate assets file manifest"
            group = "build"

            val assetsDir = project.layout.projectDirectory.dir("src/main/assets")
            val assetSourceDirName = "MaaSync/MaaResource"
            // 检查 MaaSync/MaaResource 目录
            doFirst {
                val targetDir = File(assetsDir.asFile, assetSourceDirName)
                if (!targetDir.exists()) {
                    logger.lifecycle("Creating directory: ${targetDir.absolutePath}")
                    targetDir.mkdirs()
                } else {
                    logger.lifecycle("Directory already exists: ${targetDir.absolutePath}")
                }
            }

            assetSourceDir.set(assetSourceDirName)
            sourceDir.set(assetsDir.dir(assetSourceDirName))
            manifestFile.set(assetsDir.file("MaaSync/asset_manifest.json"))
        }

        project.tasks.matching { it.name.startsWith("preBuild") }.configureEach {
            dependsOn(generateAssetManifest)
        }
    }
}
