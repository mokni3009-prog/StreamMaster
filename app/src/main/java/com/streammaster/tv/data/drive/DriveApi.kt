package com.streammaster.tv.data.drive

import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * SPEC §4.63 — Google Drive v3 REST API (Retrofit, no google-api-client
 * dependency). Only the small-file operations Stream Master needs:
 * list / upload / download / delete one JSON backup file.
 *
 * Auth is a plain `Authorization: Bearer <access token>` header, supplied
 * per call by [DriveClient] from [DrivePrefs]. The scope the coach must
 * grant in his Cloud Console project is `drive.file` (files created by
 * this app only — narrower and safer than full Drive access).
 *
 * NEVER upload an APK here: APKs and updates stay on GitHub Releases.
 */
interface DriveService {

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") auth: String,
        @Query("q") query: String,
        @Query("spaces") spaces: String = "drive",
        @Query("fields") fields: String = "files(id,name,mimeType,modifiedTime)",
        @Query("pageSize") pageSize: Int = 10
    ): DriveFileList

    @Streaming
    @GET
    suspend fun downloadFile(
        @Header("Authorization") auth: String,
        @Url url: String
    ): ResponseBody

    /** multipart upload: part 1 = JSON metadata, part 2 = file content. */
    @Multipart
    @POST("upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadFile(
        @Header("Authorization") auth: String,
        @Part metadata: MultipartBody.Part,
        @Part media: MultipartBody.Part
    ): DriveFile

    /** Replace the content of an existing file (media upload). */
    @PATCH("upload/drive/v3/files/{fileId}?uploadType=media")
    suspend fun updateFileContent(
        @Header("Authorization") auth: String,
        @Path("fileId") fileId: String,
        @Body body: okhttp3.RequestBody
    ): DriveFile

    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") auth: String,
        @Path("fileId") fileId: String
    ): ResponseBody
}

data class DriveFile(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("mimeType") val mimeType: String = "",
    @SerializedName("modifiedTime") val modifiedTime: String = ""
)

data class DriveFileList(
    @SerializedName("files") val files: List<DriveFile> = emptyList()
)

internal fun driveService(): DriveService = Retrofit.Builder()
    .baseUrl("https://www.googleapis.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(DriveService::class.java)

internal fun jsonMetadataPart(name: String): MultipartBody.Part {
    val json = """{"name":"$name","mimeType":"application/json"}"""
    return MultipartBody.Part.createFormData(
        "metadata",
        null,
        json.toRequestBody("application/json; charset=utf-8".toMediaType())
    )
}

internal fun jsonMediaPart(name: String, json: String): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        "media",
        name,
        json.toRequestBody("application/json; charset=utf-8".toMediaType())
    )
