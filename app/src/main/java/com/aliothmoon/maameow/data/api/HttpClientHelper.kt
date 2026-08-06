package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.utils.JsonUtils
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber

class HttpClientHelper(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }


    /**
     * 不要在 [headers] 里传 Accept-Encoding：那会关掉 OkHttp 的透明 gzip，
     * 服务端随之把 ETag 在强/弱之间切换，api2.maa.plus 会静默退化成每次全量下载
     */
    suspend fun get(
        url: String,
        query: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): Response {
        val request = Request.Builder().apply {
            val requestUrl = url.toHttpUrl().run {
                if (query.isEmpty()) {
                    this
                } else {
                    newBuilder().also { builder ->
                        for (it in query) {
                            builder.addQueryParameter(it.key, it.value)
                        }
                    }.build()

                }
            }
            url(requestUrl)
        }.apply { headers.forEach { (k, v) -> header(k, v) } }
            .get()
            .build()
            .also { Timber.d("GET ${it.url.host}") }
        return okHttpClient.newCall(request).await()
    }

    suspend inline fun <reified T> getEntity(
        url: String,
        query: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): T {
        return get(url, query, headers).use {
            JsonUtils.common.decodeFromString<T>(it.body.string())
        }
    }

    suspend fun post(
        url: String,
        body: String,
        query: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): Response {
        val request = Request.Builder().apply {
            val requestUrl = url.toHttpUrl().run {
                if (query.isEmpty()) {
                    this
                } else {
                    newBuilder().also { builder ->
                        for (it in query) {
                            builder.addQueryParameter(it.key, it.value)
                        }
                    }.build()

                }
            }
            url(requestUrl)
        }.apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody(JSON_MEDIA_TYPE)).build()
        return okHttpClient.newCall(request).await()
    }


    suspend fun postForm(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val formBody = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(formBody)
            .build()
        return okHttpClient.newCall(request).await()
    }

    fun rawClient(): OkHttpClient = okHttpClient

}

