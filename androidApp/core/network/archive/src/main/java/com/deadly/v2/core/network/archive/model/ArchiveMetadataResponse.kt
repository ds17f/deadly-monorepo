package com.deadly.v2.core.network.archive.model

import com.deadly.v2.core.network.archive.model.serializer.FlexibleStringSerializer
import com.deadly.v2.core.network.archive.model.serializer.FlexibleStringListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArchiveMetadataResponse(
    @SerialName("files")
    val files: List<ArchiveFile> = emptyList(),
    
    @SerialName("metadata")
    val metadata: ArchiveMetadata? = null,
    
    @SerialName("reviews")
    val reviews: List<ArchiveReview>? = null,
    
    @SerialName("server")
    val server: String? = null,
    
    @SerialName("dir")
    val directory: String? = null,
    
    @SerialName("workable_servers")
    val workableServers: List<String>? = null
) {
    @Serializable
    data class ArchiveFile(
        @SerialName("name")
        val name: String,
        
        @SerialName("format")
        val format: String,
        
        @SerialName("size")
        val size: String? = null,
        
        @SerialName("length")
        val length: String? = null,
        
        @SerialName("title")
        val title: String? = null,
        
        @SerialName("track")
        val track: String? = null,
        
        @SerialName("artist")
        val artist: String? = null,
        
        @SerialName("album")
        val album: String? = null,
        
        @SerialName("bitrate")
        val bitrate: String? = null,
        
        @SerialName("sample_rate")
        val sampleRate: String? = null,
        
        @SerialName("md5")
        val md5: String? = null,
        
        @SerialName("crc32")
        val crc32: String? = null,
        
        @SerialName("sha1")
        val sha1: String? = null,
        
        @SerialName("mtime")
        val modifiedTime: String? = null
    )
    
    @Serializable
    data class ArchiveMetadata(
        @SerialName("identifier")
        val identifier: String,
        
        @SerialName("title")
        val title: String,
        
        @SerialName("date")
        val date: String? = null,
        
        @SerialName("venue")
        @Serializable(with = FlexibleStringSerializer::class)
        val venue: String? = null,
        
        @SerialName("coverage")
        val coverage: String? = null,
        
        @SerialName("creator")
        @Serializable(with = FlexibleStringSerializer::class)
        val creator: String? = null,
        
        @SerialName("description")
        @Serializable(with = FlexibleStringListSerializer::class)
        val description: String? = null,
        
        @SerialName("setlist")
        @Serializable(with = FlexibleStringListSerializer::class)
        val setlist: String? = null,
        
        @SerialName("source")
        @Serializable(with = FlexibleStringListSerializer::class)
        val source: String? = null,
        
        @SerialName("taper")
        @Serializable(with = FlexibleStringSerializer::class)
        val taper: String? = null,
        
        @SerialName("transferer")
        @Serializable(with = FlexibleStringSerializer::class)
        val transferer: String? = null,
        
        @SerialName("lineage")
        @Serializable(with = FlexibleStringListSerializer::class)
        val lineage: String? = null,
        
        @SerialName("notes")
        @Serializable(with = FlexibleStringListSerializer::class)
        val notes: String? = null,
        
        @SerialName("uploader")
        val uploader: String? = null,
        
        @SerialName("addeddate")
        val addedDate: String? = null,
        
        @SerialName("publicdate")
        val publicDate: String? = null,
        
        @SerialName("collection")
        @Serializable(with = FlexibleStringListSerializer::class)
        val collection: String? = null,
        
        @SerialName("subject")
        @Serializable(with = FlexibleStringListSerializer::class)
        val subject: String? = null,
        
        @SerialName("licenseurl")
        val licenseUrl: String? = null
    )
    
    @Serializable
    data class ArchiveReview(
        @SerialName("reviewtitle")
        val title: String? = null,
        
        @SerialName("reviewbody")
        val body: String? = null,
        
        @SerialName("reviewer")
        val reviewer: String? = null,
        
        @SerialName("reviewdate")
        val reviewDate: String? = null,
        
        @SerialName("stars")
        val stars: Int? = null
    )
}