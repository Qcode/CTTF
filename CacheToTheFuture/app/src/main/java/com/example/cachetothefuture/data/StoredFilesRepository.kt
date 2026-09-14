package com.example.cachetothefuture.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
data class FileMetadata(val originalUrl: String, val nickname: String, val checksum: String)

data class CachedPage(val url: String, val cachedByMe: Boolean)

@Serializable
data class OtherMetadata(val url: String, val checksum: String)

fun generateSHA256Checksum(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Log.d("CTTF", "preprocessed")
    Log.d("CTTF", input)
    Log.d("CTTF", "replaced")
    val newInput = input.replace("\n", "") + "\n"
    Log.d("CTTF", newInput)
    val toDigest = newInput.toByteArray(Charsets.UTF_8)
    val hashBytes = digest.digest(toDigest)
    return hashBytes.joinToString("") { "%02x".format(it) }
}

class StoredFilesRepository(private val manager: DownloadManager) {
    val filesStored = MutableStateFlow(emptyList<FileMetadata>())
    val othersHaveCached = MutableStateFlow(emptyList<OtherMetadata>())

    init {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val f = File(downloadsDir, "cachetothefuture")
        if (!f.isDirectory) {
            f.mkdirs()
            Log.d("CTTF", "Creating cachetothefuture directory")
            Log.d("CTTF", f.isDirectory.toString())
        }
        val fileList = mutableListOf<FileMetadata>()
        f.walk().forEach { file ->
            run {
                if (file.name != "cachetothefuture" && file.path.contains(".metadata")) {
                    fileList.add(Json.decodeFromString<FileMetadata>(file.readText()))
                }
            }
        }
        filesStored.value = fileList

        val otherCachedFile = File(downloadsDir, "cachetothefuture/othersCached.cachetothefuture")
        if (otherCachedFile.exists()) {
            val content: List<OtherMetadata> =
                Json.decodeFromString<List<OtherMetadata>>(otherCachedFile.readText())
            othersHaveCached.value = content
        }
    }

    suspend fun downloadFile(url: String, context: Context, storeFile: Boolean = true): String {
        val filteredUrl = url.replace("https://", "").replace("/", "_")
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (storeFile) {
            val filesLocation = File(downloadsDir, "cachetothefuture/${filteredUrl}_files")
            Log.d("CTTF", "isDirectory ${filesLocation.isDirectory}")
            if (!filesLocation.isDirectory) {
                filesLocation.mkdirs()
                Log.d("CTTF", "isDirectory ${filesLocation.isDirectory}")
            }
        }
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            val uri = Uri.parse(url)
            val doc: Document = Ksoup.parse(bodyString!!, "${uri.scheme}://${uri.authority}")
            var stylesheetCounter = 0
            for (link in doc.select("link")) {
                val rel = link.attribute("rel")
                if (rel != null && rel.value.contains("stylesheet")) {
                    stylesheetCounter += 1
                    val stylesheetUrl = link.absUrl("href")

                    val stylesheetDestinationFile =
                        File(
                            downloadsDir,
                            "cachetothefuture/${filteredUrl}_files/style${stylesheetCounter}.css"
                        )

                    val stylesheetUri =
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            stylesheetDestinationFile
                        )
                    Log.d("CTTF", "Content uri on save is $stylesheetUri")
                    link.removeAttr("integrity")
                    link.removeAttr("crossorigin")
                    rel.setValue("stylesheet")
                    link.attribute("href")?.setValue(stylesheetUri.toString())

                    if (storeFile) {
                        val stylesheetRequest = Request.Builder().url(stylesheetUrl).build()
                        val stylesheetResponse = client.newCall(stylesheetRequest).execute()
                        val stylesheetString = stylesheetResponse.body?.string()
                        Log.d("CTTF", stylesheetDestinationFile.path)
                        stylesheetDestinationFile.writeText(stylesheetString!!)
                    }
                }
            }
            var imageCounter = 0
            for (img in doc.select("img")) {
                val src = img.absUrl("src")
                val extension = src.substringAfterLast(".", "")
                Log.d("CTTF", src)
                if (extension !in listOf("gif", "jpg", "png", "jpeg", "svg")) {
                    continue
                }
                imageCounter += 1

                val outputFile = File(
                    downloadsDir,
                    "cachetothefuture/${filteredUrl}_files/image${imageCounter}.${extension}"
                )
                val imageUri =
                    FileProvider.getUriForFile(
                        context,
                        context.packageName + ".provider",
                        outputFile
                    )
                img.removeAttr("srcset")
                img.attribute("src")?.setValue(imageUri.toString())
                if (storeFile) {
                    val imageRequest = Request.Builder().url(src).build()
                    val imageResponse = client.newCall(imageRequest).execute()
                    val inputStream = imageResponse.body?.byteStream()
                    Log.d("CTTF", "Bytestream exists ${inputStream != null}")
                    val outputStream = FileOutputStream(outputFile)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            Log.d("CTTF", "Copying")
                            input?.copyTo(output)
                        }
                    }
                }
            }

            val checksum = generateSHA256Checksum(doc.toString())

            if (storeFile) {
                val destinationFile = File(downloadsDir, "cachetothefuture/${filteredUrl}.html")
                destinationFile.writeText(doc.toString())

                val fileMetadata = FileMetadata(url, filteredUrl + ".html", checksum)
                val metadataFile = File(downloadsDir, "cachetothefuture/${filteredUrl}.metadata")
                metadataFile.writeText(Json.encodeToString(fileMetadata))

                val newList = filesStored.value + listOf(fileMetadata)
                filesStored.value = newList
            }

            checksum
        }
    }

    fun openFile(url: String, context: Context) {
        val downloadsPath =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val htmlFile = File(downloadsPath, "cachetothefuture/" + url)
        val filesPath = File(htmlFile.path.substringBeforeLast(".") + "_files")
        Log.d("CTTF", filesPath.toString())
        val includedFiles = mutableListOf<File>()
        filesPath.walk().forEach { file ->
            if (file.name != filesPath.name) {
                includedFiles.add(file)
            }
        }
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".provider", htmlFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "text/html")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val targetPackage = intent.resolveActivity(context.packageManager)?.packageName
        if (targetPackage != null) {
            for (file in includedFiles) {
                Log.d("CTTF", "Found file $file")
                val fileUri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                context.grantUriPermission(
                    targetPackage,
                    fileUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d("CTTF", "Giving permission to $fileUri")
            }
        }

        context.startActivity(intent)
    }

    fun getFilesAsUrls(): List<CachedPage> {
        return filesStored.value.map { file ->
            CachedPage(file.originalUrl, true)
        } + othersHaveCached.value.map { url ->
            CachedPage(url.url, false)
        }
    }

    suspend fun addOtherCachedUrl(url: String, context: Context) {
        val others = othersHaveCached.value.toMutableList()

        if (others.any { it.url == url }) return
        if (filesStored.value.any { metadata -> metadata.originalUrl == url }) return

        Log.d("CTTF", "making checksum request")

        val checksum = downloadFile(url, context, false)

        Log.d("CTTF", "Finished checksum request")

        others.add(OtherMetadata(url, checksum))
        othersHaveCached.value = others
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val otherCachedFile = File(downloadsDir, "cachetothefuture/othersCached.cachetothefuture")
        otherCachedFile.writeText(Json.encodeToString(others))
    }
}