package com.aliothmoon.maameow.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// i18n 资源一致性校验 gate
// 对比默认 values(中文源)与 values-en(英文),发现以下问题时使构建失败:
//   - 英文漏翻 / 英文多余(key 集合不一致)
//   - 占位符 %n$X 中英不匹配
//   - 单文件内重复定义 key
// 英文里残留中文仅告警(带白名单,见 cjkAllowed),不阻断构建
// 校验规则与 scripts/check_i18n_strings.py 保持一致;脚本另含 --clean 清理与更详细
// 报告,供本地使用;本任务挂到 preBuild,本地 assemble* 与 CI 构建均自动触发
abstract class VerifyI18nStringsTask : DefaultTask() {

    @get:InputFile
    abstract val defaultStrings: RegularFileProperty

    @get:InputFile
    abstract val enStrings: RegularFileProperty

    @get:OutputFile
    abstract val stampFile: RegularFileProperty

    // Android 带序号占位符:%1$s、%2$d、%1$04d、%1$dx%2$d 等
    private val placeholderRe = Regex("""%\d+\$[-#+ 0-9.]*[a-zA-Z]""")
    // CJK 统一表意文字 + 常见中日韩标点/全角符号
    private val cjkRe = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\u3000-\\u303F\\uFF01-\\uFF60]")
    // 英文文案里合法含中文的 key:语言自名
    private val cjkAllowed = setOf(
        "settings_language_zh",
    )

    @TaskAction
    fun verify() {
        val (zh, zhDups) = parseStrings(defaultStrings.get().asFile)
        val (en, enDups) = parseStrings(enStrings.get().asFile)
        val errors = mutableListOf<String>()
        val warns = mutableListOf<String>()

        if (zhDups.isNotEmpty()) errors += "values/strings.xml has duplicate key(s): ${zhDups.joinToString()}"
        if (enDups.isNotEmpty()) errors += "values-en/strings.xml has duplicate key(s): ${enDups.joinToString()}"

        val missingInEn = (zh.keys - en.keys).sorted()
        val extraInEn = (en.keys - zh.keys).sorted()
        if (missingInEn.isNotEmpty())
            errors += "Missing in values-en (${missingInEn.size} key(s) untranslated): ${missingInEn.joinToString()}"
        if (extraInEn.isNotEmpty())
            errors += "Extra in values-en (${extraInEn.size} key(s) absent from default values): ${extraInEn.joinToString()}"

        val phMismatch = mutableListOf<String>()
        for (k in zh.keys.intersect(en.keys).sorted()) {
            if (placeholders(zh.getValue(k)) != placeholders(en.getValue(k))) phMismatch += k
            if (k !in cjkAllowed && cjkRe.containsMatchIn(en.getValue(k)))
                warns += "values-en value contains CJK (likely untranslated): $k -> ${en.getValue(k)}"
        }
        if (phMismatch.isNotEmpty()) {
            errors += "Placeholder mismatch (${phMismatch.size} key(s)):\n" + phMismatch.joinToString("\n") { k ->
                "    - $k\n        zh: ${zh.getValue(k)}\n        en: ${en.getValue(k)}"
            }
        }

        warns.forEach { logger.warn("[i18n][WARN] $it") }

        if (errors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("i18n verification failed (${errors.size} issue type(s)):")
                    errors.forEach { appendLine("  - $it") }
                    appendLine()
                    append("Fix values-en, then re-run ./gradlew :app:verifyI18nStrings")
                }
            )
        }

        logger.lifecycle("i18n verification passed: ${zh.size} zh, ${en.size} en (warnings: ${warns.size})")
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

class I18nVerifyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val verifyI18nStrings = project.tasks.register(
            "verifyI18nStrings",
            VerifyI18nStringsTask::class.java,
        ) {
            description = "Verify i18n consistency between values (Chinese) and values-en (English)"
            group = "verification"
            defaultStrings.set(project.layout.projectDirectory.file("src/main/res/values/strings.xml"))
            enStrings.set(project.layout.projectDirectory.file("src/main/res/values-en/strings.xml"))
            stampFile.set(project.layout.buildDirectory.file("i18n-verify/verify.stamp"))
        }

        project.tasks.matching { it.name.startsWith("preBuild") }.configureEach {
            dependsOn(verifyI18nStrings)
        }
    }
}
