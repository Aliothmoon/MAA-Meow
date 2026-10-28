package com.aliothmoon.maameow.manager

import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.remote.RemoteServiceImpl

/** Shizuku 唯一路径：newProcess 拉起自研 starter，见 [ShizukuSpawner] */
object ShizukuProcessServiceConnector : ProcessServiceConnectorBackend(ShizukuSpawner) {

    override val backend = RemoteBackend.SHIZUKU
    override val eventPrefix = "SHIZUKU"
    override val processNameSuffix = "shizuku_service"
    override val serviceClass: Class<*> = RemoteServiceImpl::class.java
    override val logFileName = "shizuku_launch_debug.log"

    // 进程侧失败由 alive 轮询秒级暴露，超时只兜"进程活着但不回投"；实测全链 <1s，留 10 倍余量
    override val spawnTimeoutMs: Long = 10_000L
}
