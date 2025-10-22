package com.example.datadomeapp.models

data class BookResponse(
    val items: List<BookItem>?
)

data class BookItem(
    val id: String?,
    val volumeInfo: VolumeInfo,
    val accessInfo: AccessInfo?
)

data class VolumeInfo(
    val title: String?,
    val authors: List<String>?,
    val description: String?,
    val imageLinks: ImageLinks?,
    val previewLink: String?
)

data class ImageLinks(
    val smallThumbnail: String?,
    val thumbnail: String?
)

data class AccessInfo(
    val webReaderLink: String?,
    val pdf: PdfInfo?
)

data class PdfInfo(
    val isAvailable: Boolean?,
    val acsTokenLink: String?
)