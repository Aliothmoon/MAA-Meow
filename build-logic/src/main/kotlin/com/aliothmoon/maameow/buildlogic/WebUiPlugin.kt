package com.aliothmoon.maameow.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

// 用 pnpm 构建 webui/ 前端；本地缺 pnpm 时跳过，App 端回落到占位页
abstract class BuildWebUiTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val projectDir: DirectoryProperty

    /** CI 上必须打进包 */
    @get:Input
    abstract val required: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    @TaskAction
    fun build() {
        val out = outputDir.get().asFile
        fs.delete { delete(out) }
        out.mkdirs()

        if (!pnpmAvailable()) {
            if (required.get()) throw GradleException("pnpm not found, the web UI must be bundled on CI")
            logger.warn("pnpm not found, skipping web UI bundle (the app will serve a placeholder page)")
            return
        }
        pnpm("install", "--frozen-lockfile")
        pnpm("run", "build", env = mapOf("WEBUI_OUT_DIR" to out.absolutePath))
    }

    private fun command(vararg args: String): List<String> =
        if (isWindows) listOf("cmd", "/c", "pnpm", *args) else listOf("pnpm", *args)

    private fun pnpmAvailable(): Boolean = try {
        execOps.exec {
            commandLine(command("--version"))
            workingDir = projectDir.get().asFile
            isIgnoreExitValue = true
            standardOutput = ByteArrayOutputStream()
            errorOutput = ByteArrayOutputStream()
        }.exitValue == 0
    } catch (e: Exception) {
        false
    }

    private fun pnpm(vararg args: String, env: Map<String, String> = emptyMap()) {
        execOps.exec {
            commandLine(command(*args))
            workingDir = projectDir.get().asFile
            environment(env)
        }
    }
}

// AGP 按变体接管生成目录，产物要套一层 webui/ 才落到 assets/webui 下
abstract class SyncWebUiAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val distDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun sync() {
        fs.sync {
            from(distDir)
            into(outputDir.dir("webui"))
        }
    }
}

class WebUiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val webuiDir = project.rootProject.layout.projectDirectory.dir("webui")
        project.tasks.register("buildWebUi", BuildWebUiTask::class.java) {
            description = "Build the web console frontend with pnpm"
            group = "build"
            projectDir.set(webuiDir)
            sources.from(
                project.fileTree(webuiDir) {
                    exclude("node_modules/**", "dist/**", "**/*.local")
                }
            )
            required.set(
                project.providers.environmentVariable("CI")
                    .map { it.equals("true", ignoreCase = true) }
                    .orElse(false)
            )
            outputDir.set(project.layout.buildDirectory.dir("webui/dist"))
        }
    }
}
