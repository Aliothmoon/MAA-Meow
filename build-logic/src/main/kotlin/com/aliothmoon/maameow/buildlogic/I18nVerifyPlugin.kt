package com.aliothmoon.maameow.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// i18n 资源一致性校验 gate
// 对比默认 values(中文源)与各译文 values-xx,发现以下问题时使构建失败:
//   - 漏翻 / 多余(key 集合不一致)
//   - 占位符 %n$X 与中文不匹配
//   - 单文件内重复定义 key
// 译文里残留中文仅告警(带白名单,见 cjkAllowed),不阻断构建
// 校验规则与 scripts/check_i18n_strings.py 保持一致;脚本另含 --clean 清理与更详细
// 报告,供本地使用;本任务挂到 preBuild,本地 assemble* 与 CI 构建均自动触发
abstract class VerifyI18nStringsTask : DefaultTask() {

    @get:InputFile
    abstract val defaultStrings: RegularFileProperty

    // values-xx/strings.xml,按目录名区分语言
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val translations: ConfigurableFileCollection

    @get:OutputFile
    abstract val stampFile: RegularFileProperty

    // Android 带序号占位符:%1$s、%2$d、%1$04d、%1$dx%2$d 等
    private val placeholderRe = Regex("""%\d+\$[-#+ 0-9.]*[a-zA-Z]""")
    // CJK 统一表意文字 + 常见中日韩标点/全角符号
    private val cjkRe = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\u3000-\\u303F\\uFF01-\\uFF60]")
    // 韩语正文会用「」等 CJK 标点,只查汉字
    private val hanRe = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]")
    // 译文里合法含中文的 key:语言自名
    private val cjkAllowed = setOf(
        "settings_language_zh",
    )

    @TaskAction
    fun verify() {
        val (zh, zhDups) = parseStrings(defaultStrings.get().asFile)
        val errors = mutableListOf<String>()
        val warns = mutableListOf<String>()
        val counts = mutableListOf("${zh.size} zh")

        if (zhDups.isNotEmpty()) errors += "values/strings.xml has duplicate key(s): ${zhDups.joinToString()}"

        for (file in translations.files.sortedBy { it.parentFile.name }) {
            val dir = file.parentFile.name
            val (tr, trDups) = parseStrings(file)
            counts += "${tr.size} ${dir.removePrefix("values-")}"
            if (trDups.isNotEmpty()) errors += "$dir/strings.xml has duplicate key(s): ${trDups.joinToString()}"

            val missing = (zh.keys - tr.keys).sorted()
            val extra = (tr.keys - zh.keys).sorted()
            if (missing.isNotEmpty())
                errors += "Missing in $dir (${missing.size} key(s) untranslated): ${missing.joinToString()}"
            if (extra.isNotEmpty())
                errors += "Extra in $dir (${extra.size} key(s) absent from default values): ${extra.joinToString()}"

            val residualRe = if (dir == "values-ko") hanRe else cjkRe
            val phMismatch = mutableListOf<String>()
            for (k in zh.keys.intersect(tr.keys).sorted()) {
                if (placeholders(zh.getValue(k)) != placeholders(tr.getValue(k))) phMismatch += k
                if (k !in cjkAllowed && residualRe.containsMatchIn(tr.getValue(k)))
                    warns += "$dir value contains Chinese (likely untranslated): $k -> ${tr.getValue(k)}"
            }
            if (phMismatch.isNotEmpty()) {
                errors += "Placeholder mismatch in $dir (${phMismatch.size} key(s)):\n" + phMismatch.joinToString("\n") { k ->
                    "    - $k\n        zh: ${zh.getValue(k)}\n        $dir: ${tr.getValue(k)}"
                }
            }
        }

        warns.forEach { logger.warn("[i18n][WARN] $it") }

        if (errors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("i18n verification failed (${errors.size} issue type(s)):")
                    errors.forEach { appendLine("  - $it") }
                    appendLine()
                    append("Fix the translations, then re-run ./gradlew :app:verifyI18nStrings")
                }
            )
        }

        logger.lifecycle("i18n verification passed: ${counts.joinToString()} (warnings: ${warns.size})")
        stampFile.get().asFile.apply {
            parentFile?.mkdirs()
            writeText("ok ${System.currentTimeMillis()}\n")
        }
    }

    private fun parseStrings(file: File): Pair<Map<String, String>, List<String>> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val nodes = doc.getElementsByTagName("string")
        val names = mutableListOf<String>()
        val map = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            if (name.isNullOrEmpty()) continue
            names.add(name)
            map[name] = el.textContent ?: ""
        }
        val dups = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        return map to dups
    }

    private fun placeholders(s: String): Map<String, Int> =
        placeholderRe.findAll(s).map { it.value }.groupingBy { it }.eachCount()
}

// 新增译文语言时同步 scripts/check_i18n_strings.py 的 TRANSLATIONS
private val TRANSLATION_LOCALES = listOf("en", "ko")

class I18nVerifyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val verifyI18nStrings = project.tasks.register(
            "verifyI18nStrings",
            VerifyI18nStringsTask::class.java,
        ) {
            description = "Verify i18n consistency between values (Chinese) and each translation locale"
            group = "verification"
            defaultStrings.set(project.layout.projectDirectory.file("src/main/res/values/strings.xml"))
            translations.from(TRANSLATION_LOCALES.map {
                project.layout.projectDirectory.file("src/main/res/values-$it/strings.xml")
            })
            stampFile.set(project.layout.buildDirectory.file("i18n-verify/verify.stamp"))
        }

        project.tasks.matching { it.name.startsWith("preBuild") }.configureEach {
            dependsOn(verifyI18nStrings)
        }
    }
}
