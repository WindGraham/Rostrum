package com.rostrum.core.plugin.ipc.serialization

import com.rostrum.extension.api.FileInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer

/**
 * FileInfo 的 JSON 序列化器
 * 
 * 用于 IPC 通信中的 FileInfo 序列化和反序列化
 */
object FileInfoSerializer : KSerializer<FileInfo> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FileInfo", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: FileInfo) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject = buildJsonObject {
            put("uri", value.uri)
            put("name", value.name)
            put("path", value.path)
            put("size", value.size)
            put("lastModified", value.lastModified)
            put("isDirectory", value.isDirectory)
            put("isFile", value.isFile)
            value.mimeType?.let { put("mimeType", it) }
            value.extension?.let { put("extension", it) }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }
    
    override fun deserialize(decoder: Decoder): FileInfo {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        
        return FileInfo(
            uri = jsonObject["uri"]!!.jsonPrimitive.content,
            name = jsonObject["name"]!!.jsonPrimitive.content,
            path = jsonObject["path"]!!.jsonPrimitive.content,
            size = jsonObject["size"]!!.jsonPrimitive.long,
            lastModified = jsonObject["lastModified"]!!.jsonPrimitive.long,
            isDirectory = jsonObject["isDirectory"]!!.jsonPrimitive.boolean,
            isFile = jsonObject["isFile"]!!.jsonPrimitive.boolean,
            mimeType = jsonObject["mimeType"]?.jsonPrimitive?.content,
            extension = jsonObject["extension"]?.jsonPrimitive?.content
        )
    }
}

/**
 * FileInfo 序列化工具函数
 */
object FileInfoSerialization {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    /**
     * 序列化 FileInfo 为 JSON 字符串
     */
    fun serialize(fileInfo: FileInfo): String {
        return json.encodeToString(FileInfoSerializer, fileInfo)
    }
    
    /**
     * 反序列化 JSON 字符串为 FileInfo
     */
    fun deserialize(jsonString: String): FileInfo? {
        return try {
            json.decodeFromString(FileInfoSerializer, jsonString)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 序列化 FileInfo 列表为 JSON 字符串
     */
    fun serializeList(fileInfos: List<FileInfo>): String {
        return json.encodeToString(
            ListSerializer(FileInfoSerializer),
            fileInfos
        )
    }
    
    /**
     * 反序列化 JSON 字符串为 FileInfo 列表
     */
    fun deserializeList(jsonString: String): List<FileInfo> {
        return try {
            json.decodeFromString(
                ListSerializer(FileInfoSerializer),
                jsonString
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

