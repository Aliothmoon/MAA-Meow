package com.aliothmoon.maameow.telemetry

import java.io.IOException
import kotlin.math.roundToInt

/**
 * 失败证据：开跑时记下各日志的长度与已有的出错截图，失败后只取这之后新写的部分
 *
 * 沿用 MaaFwApp `TaskEvidence`，区别是要看的文件已知，不用走目录
 * 截图是 Core 在任务链失败或抛异常时用缓存帧存进 `interface/` 的那一张（`Assistant::working_proc`），早于失败回调
 */
internal object TaskEvidence {

    /** 开跑那一刻之前再往回带一点上下文 */
    private const val CORE_LOG_PRELUDE_BYTES = 64L * 1024

    /** App 侧日志稀疏，同样的字节数会带出几天前的无关报错 */
    private const val APP_LOG_PRELUDE_BYTES = 4L * 1024
    private const val MAX_LOG_RAW_BYTES = 1024L * 1024
    private const val MAX_LOG_FILE_BYTES = 512L * 1024

    /** 开跑时没量到长度的文件说不清哪些是这一轮写的，只带一小截 */
    private const val NO_BASELINE_TAIL_BYTES = 128L * 1024

    private const val MAX_IMAGES = 4
    private const val MAX_IMAGE_RAW_BYTES = 8L * 1024 * 1024
    private const val MAX_IMAGE_TOTAL_BYTES = 2 * 1024 * 1024

    /** 截图只用来认卡在哪个界面，短边 540 下界面文字仍看得清，体积是 720p 的四成上下 */
    private const val IMAGE_SHORT_SIDE = 540

    const val IMAGE_DIR = "interface"
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg")

    /** 上传前把截图缩到多大：短边不超过 [IMAGE_SHORT_SIDE]，只缩不放，保持比例 */
    fun scaledImageSize(width: Int, height: Int): Pair<Int, Int> {
        val shortSide = minOf(width, height)
        if (shortSide <= IMAGE_SHORT_SIDE) return width to height
        val scale = IMAGE_SHORT_SIDE.toDouble() / shortSide
        return (width * scale).roundToInt() to (height * scale).roundToInt()
    }

    /** [files] 的顺序即优先级，预算不够时先保前面的 */
    fun captureStart(store: EvidenceStore, files: List<EvidenceFile>): EvidenceStart = EvidenceStart(
        files = files,
        lengths = buildMap { files.forEach { file -> store.length(file)?.let { put(file.path, it) } } },
        images = store.list(IMAGE_DIR, core = true)?.toSet(),
    )

    /** 都取尾巴：失败的细节通常写在最后；读不了的跳过并留一条 warning */
    fun collectLogs(store: EvidenceStore, start: EvidenceStart): DiagnosticLogs {
        val warnings = mutableListOf<String>()
        val entries = mutableListOf<DiagnosticLog>()
        var selectedRawBytes = 0L
        var truncated = false
        for (file in start.files) {
            val remaining = MAX_LOG_RAW_BYTES - selectedRawBytes
            if (remaining <= 0) {
                truncated = true
                break
            }
            val previous = start.lengths[file.path]
            val length = store.length(file)
            if (length == null) {
                if (previous != null) warnings += "open_log_failed:${file.path}"
                continue
            }
            val from = when {
                length == 0L -> continue
                previous == null -> {
                    warnings += "no_baseline_tail:${file.path}"
                    (length - NO_BASELINE_TAIL_BYTES).coerceAtLeast(0)
                }
                length > previous -> {
                    val prelude = if (file.core) CORE_LOG_PRELUDE_BYTES else APP_LOG_PRELUDE_BYTES
                    (previous - prelude).coerceAtLeast(0)
                }
                length < previous -> {
                    warnings += "rotated_log_included_whole:${file.path}"
                    0L
                }
                else -> continue
            }
            val selected = length - from
            val readLength = minOf(selected, MAX_LOG_FILE_BYTES, remaining)
            val readFrom = length - readLength
            val bytes = try {
                store.read(file, readFrom, readLength.toInt())
            } catch (e: IOException) {
                warnings += "read_log_failed:${file.path}:${e.message}"
                continue
            }
            val content = String(bytes, Charsets.UTF_8).trim('\u0000').let { text ->
                // 从文件中间起读时第一行多半是半截，丢掉
                if (readFrom > 0) text.substringAfter('\n', text) else text
            }
            if (content.isBlank()) continue
            selectedRawBytes += bytes.size
            truncated = truncated || readLength < selected
            entries += DiagnosticLog(file.path, file.kind, content, bytes.size.toLong())
        }
        return DiagnosticLogs(entries, selectedRawBytes, truncated, warnings.distinct().sorted())
    }

    /** 只带开跑后新出现的；[encode] 压不了的不带 */
    fun collectImages(
        store: EvidenceStore,
        start: EvidenceStart,
        encode: (ByteArray) -> ByteArray?,
    ): AttachmentOutcome {
        // 开跑时没列到目录就分不出新旧，宁可不带
        val existing = start.images
            ?: return AttachmentOutcome.Omitted("no_baseline", "screenshot directory was unreadable at task start")
        val current = store.list(IMAGE_DIR, core = true)
            ?: return AttachmentOutcome.Omitted("unavailable", "screenshot directory is unreadable")
        val fresh = current
            .filter { it !in existing && it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
            .sorted()
            .takeLast(MAX_IMAGES)
        if (fresh.isEmpty()) return AttachmentOutcome.Omitted("no_evidence", "no screenshot was saved for this task")

        val images = mutableListOf<EvidenceImage>()
        var total = 0
        for (name in fresh) {
            val file = EvidenceFile("$IMAGE_DIR/$name", kind = "image", core = true)
            val length = store.length(file) ?: continue
            if (length > MAX_IMAGE_RAW_BYTES) continue
            val encoded = try {
                encode(store.read(file, 0, length.toInt()))
            } catch (e: IOException) {
                null
            } ?: continue
            total += encoded.size
            if (total > MAX_IMAGE_TOTAL_BYTES) {
                return AttachmentOutcome.Omitted("too_large", "screenshots exceed $MAX_IMAGE_TOTAL_BYTES bytes")
            }
            images += EvidenceImage(name.substringBeforeLast('.') + ".jpg", encoded)
        }
        if (images.isEmpty()) return AttachmentOutcome.Omitted("build_failed", "no screenshot could be read or encoded")
        return AttachmentOutcome.Attached(images)
    }
}

internal data class EvidenceFile(
    /** 相对 debug 目录的路径，也是归档名 */
    val path: String,
    val kind: String,
    /** Core 侧写的文件；数据目录独立时 App 读不到，得经提权进程取 */
    val core: Boolean,
)

internal interface EvidenceStore {
    /** 不存在或读不了返回 null */
    fun length(file: EvidenceFile): Long?

    /** 读 `[from, from + length)`，文件比量的时候短就读到多少算多少 */
    @Throws(IOException::class)
    fun read(file: EvidenceFile, from: Long, length: Int): ByteArray

    /** 目录下的文件名；目录不存在返回空，读不了返回 null */
    fun list(dir: String, core: Boolean): List<String>?
}

internal class EvidenceStart(
    val files: List<EvidenceFile>,
    /** 路径 → 开跑时的长度；当时不存在或量不到的不在表里 */
    val lengths: Map<String, Long>,
    /** 开跑前就在的出错截图文件名；null = 当时没列到 */
    val images: Set<String>?,
)

internal class DiagnosticLog(
    /** 归档名，如 `asst.log`、`gui/meow_log_20261002_082323_1.log` */
    val source: String,
    val kind: String,
    val content: String,
    val rawBytes: Long,
)

internal class DiagnosticLogs(
    val entries: List<DiagnosticLog>,
    val selectedRawBytes: Long,
    val truncated: Boolean,
    val warnings: List<String>,
)

internal class EvidenceImage(val filename: String, val bytes: ByteArray)

/** 截图附件的去向，原样写进事件的 `attachment.*` */
internal sealed interface AttachmentOutcome {
    /** 没被采样到，或者这类事件不带截图 */
    data object NotSelected : AttachmentOutcome

    class Attached(val images: List<EvidenceImage>) : AttachmentOutcome

    class Omitted(val status: String, val detail: String) : AttachmentOutcome
}

internal class Evidence(
    /** null = 没取到证（开跑快照没赶上，或取证本身出了错） */
    val logs: DiagnosticLogs?,
    val attachment: AttachmentOutcome,
)
