// Asset Manifest Generation Plugin
// Generates a JSON manifest of all files in the MaaResource directory

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

val generateAssetManifest by tasks.registering(GenerateAssetManifestTask::class) {
    description = "Generate assets file manifest"
    group = "build"

    val assetsDir = layout.projectDirectory.dir("src/main/assets")
    val assetSourceDirName = "MaaSync/MaaResource"
    //检查 MaaSync/MaaResource 目录
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

tasks.matching { it.name.startsWith("preBuild") }.configureEach {
    dependsOn(generateAssetManifest)
}
