package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.model.FileCategory
import com.example.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileRepository(private val context: Context) {

    suspend fun loadCategoryFiles(category: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        when (category) {
            FileCategory.PHOTOS -> loadImages()
            FileCategory.VIDEOS -> loadVideos()
            FileCategory.DOCUMENTS -> loadDocuments()
            FileCategory.APPS -> loadApps()
        }
    }

    private fun loadImages(): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Image_$id"
                    val size = it.getLong(sizeCol)
                    val mime = it.getString(mimeCol) ?: "image/jpeg"
                    val date = it.getLong(dateCol) * 1000L
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
                        FileItem(
                            id = "img_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime,
                            category = FileCategory.PHOTOS,
                            dateModified = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If storage is empty (e.g. emulator), provide realistic offline bundled sample items
        if (list.isEmpty()) {
            list.addAll(createSampleFiles(FileCategory.PHOTOS))
        }
        return list
    }

    private fun loadVideos(): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Video_$id"
                    val size = it.getLong(sizeCol)
                    val mime = it.getString(mimeCol) ?: "video/mp4"
                    val date = it.getLong(dateCol) * 1000L
                    val durationMs = if (durationCol >= 0) it.getLong(durationCol) else 0L
                    val durationStr = if (durationMs > 0) {
                        val s = (durationMs / 1000) % 60
                        val m = (durationMs / (1000 * 60)) % 60
                        String.format("%02d:%02d", m, s)
                    } else ""
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
                        FileItem(
                            id = "vid_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime,
                            category = FileCategory.VIDEOS,
                            dateModified = date,
                            extraInfo = durationStr
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            list.addAll(createSampleFiles(FileCategory.VIDEOS))
        }
        return list
    }

    private fun loadDocuments(): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val docMimeTypes = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/zip"
        )

        val selection = docMimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                docMimeTypes,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Document_$id"
                    val size = it.getLong(sizeCol)
                    val mime = it.getString(mimeCol) ?: "application/octet-stream"
                    val date = it.getLong(dateCol) * 1000L
                    val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)

                    list.add(
                        FileItem(
                            id = "doc_$id",
                            name = name,
                            size = size,
                            uri = uri,
                            mimeType = mime,
                            category = FileCategory.DOCUMENTS,
                            dateModified = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            list.addAll(createSampleFiles(FileCategory.DOCUMENTS))
        }
        return list
    }

    private fun loadApps(): List<FileItem> {
        val list = mutableListOf<FileItem>()
        try {
            val pm = context.packageManager
            val apps: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }

            for (app in apps) {
                // Focus on user apps or downloadable third party apps
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                val sourceDir = app.publicSourceDir
                if (sourceDir != null && (!isSystem || isUpdatedSystem || app.packageName == context.packageName)) {
                    val file = File(sourceDir)
                    if (file.exists() && file.length() > 0) {
                        val appName = pm.getApplicationLabel(app).toString()
                        val uri = Uri.fromFile(file)
                        list.add(
                            FileItem(
                                id = "app_${app.packageName}",
                                name = "$appName.apk",
                                size = file.length(),
                                uri = uri,
                                mimeType = "application/vnd.android.package-archive",
                                category = FileCategory.APPS,
                                dateModified = file.lastModified(),
                                extraInfo = app.packageName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            list.addAll(createSampleFiles(FileCategory.APPS))
        }
        return list.sortedBy { it.name.lowercase() }
    }

    /**
     * Fallback samples generated in internal files directory so users can test offline transfer immediately.
     */
    private fun createSampleFiles(category: FileCategory): List<FileItem> {
        val sampleDir = File(context.filesDir, "sample_files").apply { mkdirs() }
        val items = mutableListOf<FileItem>()

        when (category) {
            FileCategory.PHOTOS -> {
                val names = listOf("Vacation_Sunset.jpg", "Project_Blueprint.png", "Field_Inspection.jpg")
                val sizes = listOf(2_450_000L, 1_820_000L, 3_100_000L)
                for (i in names.indices) {
                    val file = File(sampleDir, names[i]).apply {
                        if (!exists()) {
                            writeBytes(ByteArray(1024) { 0x55 })
                        }
                    }
                    items.add(
                        FileItem(
                            id = "sample_p_$i",
                            name = names[i],
                            size = sizes[i],
                            uri = Uri.fromFile(file),
                            mimeType = "image/jpeg",
                            category = FileCategory.PHOTOS,
                            dateModified = System.currentTimeMillis() - i * 3600_000
                        )
                    )
                }
            }
            FileCategory.VIDEOS -> {
                val names = listOf("Drone_Survey_4K.mp4", "Product_Demo.mp4")
                val sizes = listOf(45_200_000L, 18_400_000L)
                for (i in names.indices) {
                    val file = File(sampleDir, names[i]).apply {
                        if (!exists()) writeBytes(ByteArray(1024) { 0x77 })
                    }
                    items.add(
                        FileItem(
                            id = "sample_v_$i",
                            name = names[i],
                            size = sizes[i],
                            uri = Uri.fromFile(file),
                            mimeType = "video/mp4",
                            category = FileCategory.VIDEOS,
                            dateModified = System.currentTimeMillis() - i * 7200_000,
                            extraInfo = "01:45"
                        )
                    )
                }
            }
            FileCategory.DOCUMENTS -> {
                val names = listOf("Quarterly_Report.pdf", "Engineering_Spec.docx", "Budget_2026.xlsx")
                val sizes = listOf(3_420_000L, 890_000L, 1_250_000L)
                for (i in names.indices) {
                    val file = File(sampleDir, names[i]).apply {
                        if (!exists()) writeBytes(ByteArray(1024) { 0x33 })
                    }
                    items.add(
                        FileItem(
                            id = "sample_d_$i",
                            name = names[i],
                            size = sizes[i],
                            uri = Uri.fromFile(file),
                            mimeType = "application/pdf",
                            category = FileCategory.DOCUMENTS,
                            dateModified = System.currentTimeMillis() - i * 1800_000
                        )
                    )
                }
            }
            FileCategory.APPS -> {
                val names = listOf("AirShare_Installer.apk", "LocalTools.apk")
                val sizes = listOf(14_800_000L, 8_900_000L)
                for (i in names.indices) {
                    val file = File(sampleDir, names[i]).apply {
                        if (!exists()) writeBytes(ByteArray(1024) { 0x22 })
                    }
                    items.add(
                        FileItem(
                            id = "sample_a_$i",
                            name = names[i],
                            size = sizes[i],
                            uri = Uri.fromFile(file),
                            mimeType = "application/vnd.android.package-archive",
                            category = FileCategory.APPS,
                            dateModified = System.currentTimeMillis() - i * 5400_000,
                            extraInfo = "com.example.airshare"
                        )
                    )
                }
            }
        }
        return items
    }
}
