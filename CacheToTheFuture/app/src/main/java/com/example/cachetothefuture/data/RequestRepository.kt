package com.example.cachetothefuture.data

import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Request(val url: String, var hops: Int)

@Serializable
data class Response(val url: String, var hops: Int)

class RequestRepository {
    val requests = MutableStateFlow<List<Request>>(emptyList())

    init {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val requestsFile = File(downloadsDir, "cachetothefuture/requests.cachetothefuture")
        if (requestsFile.exists()) {
            val content: List<Request> =
                Json.decodeFromString<List<Request>>(requestsFile.readText())
            requests.value = content
        }
    }

    fun storeRequest(url: String) {
        val mutableRequests = requests.value.toMutableList()
        if (mutableRequests.any { request -> request.url == url }) {
            return
        }
        mutableRequests.add(Request(url = url, 0))
        requests.value = mutableRequests
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val otherCachedFile = File(downloadsDir, "cachetothefuture/requests.cachetothefuture")
        otherCachedFile.writeText(Json.encodeToString(mutableRequests))
    }

    fun storeRequest(request: Request) {
        val mutableRequests = requests.value.toMutableList()
        mutableRequests.add(request)
        requests.value = mutableRequests
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val otherCachedFile = File(downloadsDir, "cachetothefuture/requests.cachetothefuture")
        otherCachedFile.writeText(Json.encodeToString(mutableRequests))
    }
}