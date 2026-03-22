package me.fungames.jfortniteparse.ue4.assets.exports.tex

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.assets.OnlyAnnotated
import me.fungames.jfortniteparse.ue4.assets.UProperty
import me.fungames.jfortniteparse.ue4.assets.objects.FByteBulkData
import me.fungames.jfortniteparse.ue4.assets.objects.FByteBulkDataHeader
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.assets.writer.FAssetArchiveWriter
import me.fungames.jfortniteparse.ue4.objects.core.math.FIntPoint
import me.fungames.jfortniteparse.ue4.objects.engine.FStripDataFlags
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.versions.*

@OnlyAnnotated
class UTexture2D : UTexture() {
    @JvmField @UProperty var LevelIndex: Int? = null
    @JvmField @UProperty var FirstResourceMemMip: Int? = null
    @JvmField @UProperty var bTemporarilyDisableStreaming: Boolean? = null
    @JvmField @UProperty var AddressX: TextureAddress? = null
    @JvmField @UProperty var AddressY: TextureAddress? = null
    @JvmField @UProperty var ImportedSize: FIntPoint? = null
    lateinit var flag1: FStripDataFlags
    lateinit var flag2: FStripDataFlags
    var cooked: Boolean = true
    lateinit var textures: MutableMap<FTexturePlatformData, FName>

    override fun deserialize(Ar: FAssetArchive, validPos: Int) {
        super.deserialize(Ar, validPos)
        flag1 = FStripDataFlags(Ar)
        // CUE4Parse: UTexture reads editor source data here when not stripped.
        // For Fortnite IoStore textures this is 16 bytes (confirmed by hex comparison).
        // Safety: validate the bytes after the skip look correct, otherwise revert.
        if (!flag1.isEditorDataStripped()) {
            val editorPos = Ar.pos()
            Ar.skip(16)
            // Peek ahead: flag2 should have valid globalStripFlags, and cooked should be 0 or 1
            val peekPos = Ar.pos()
            Ar.readUInt8() // skip flag2.globalStripFlags
            Ar.readUInt8() // skip flag2.classStripFlags
            val peekCooked = Ar.readInt32()
            if (peekCooked == 0 || peekCooked == 1) {
                Ar.seek(peekPos) // rewind to just after skip — let normal flow read flag2+cooked
            } else {
                // Skip size was wrong — revert and let it fail the same way it did before
                LOG_JFP.warn("UTexture2D editor data skip validation failed at ${getPathName()}, reverting")
                Ar.seek(editorPos)
            }
        }
        flag2 = FStripDataFlags(Ar)
        cooked = Ar.readBoolean()
        textures = mutableMapOf()
        if (cooked) {
            // serializeMipData: controls whether mip bulk data is serialized inline.
            // Default is true (mips always inline). UE5.2+ (including Fortnite v25.10+)
            // serializes a boolean flag. CUE4Parse gates this at UE5.3, but Fortnite's
            // custom UE5 fork includes it at UE5.2.
            val serializeMipData = if (Ar.game >= GAME_UE5(2)) Ar.readBoolean() else true
            while (true) {
                val pixelFormat = Ar.readFName()
                if (pixelFormat.isNone()) break
                val skipOffset = when {
                    Ar.game >= GAME_UE5_BASE -> Ar.relativePos() + Ar.readInt64()
                    Ar.game >= GAME_UE4(20) -> Ar.readInt64()
                    else -> Ar.readInt32()
                }
                textures[FTexturePlatformData(Ar, serializeMipData)] = pixelFormat
                if (Ar.relativePos().toLong() != skipOffset) {
                    LOG_JFP.warn("Texture read incorrectly. Current relative pos ${Ar.relativePos()}, skip offset $skipOffset")
                    Ar.seekRelative(skipOffset.toInt())
                }
            }
        }
    }

    fun getFirstMip() = getFirstTexture().getFirstMip()
    fun getFirstTexture() = if (textures.isNotEmpty()) textures.keys.first() else throw IllegalStateException("No textures found in this UTexture2D")

    override fun serialize(Ar: FAssetArchiveWriter) {
        super.serialize(Ar)
        flag1.serialize(Ar)
        flag2.serialize(Ar)
        Ar.writeBoolean(cooked)
        textures.forEach { (texture, pixelFormat) ->
            Ar.writeFName(pixelFormat)
            val tempAr = Ar.setupByteArrayWriter()
            texture.serialize(tempAr)
            val textureData = tempAr.toByteArray()
            Ar.writeInt64(tempAr.relativePos().toLong() + 8) //new skip offset
            Ar.write(textureData)
        }
        Ar.writeFName(FName.getByNameMap("None", Ar.nameMap) ?: throw ParserException("NameMap must contain \"None\""))
    }
}

enum class TextureAddress {
    TA_Wrap,
    TA_Clamp,
    TA_Mirror
}

class FTexturePlatformData {
    var sizeX: Int
    var sizeY: Int
    var numSlices: Int
    var pixelFormat: String
    var firstMip: Int
    var mips: Array<FTexture2DMipMap>
    var isVirtual: Boolean = false

    constructor(Ar: FAssetArchive, serializeMipData: Boolean = true) {
        if (Ar.game >= GAME_UE5(2)) {
            // PlaceholderDerivedData: 1-byte flag (bUsingDerivedData) + 15 bytes padding = 16 total
            val bUsingDerivedData = Ar.readFlag()
            if (bUsingDerivedData) {
                LOG_JFP.warn("FTexturePlatformData with derived data is not supported, skipping 15 bytes")
            }
            Ar.skip(15)
        } else if (Ar.game >= GAME_UE5_BASE) {
            Ar.skip(16)
        }
        sizeX = Ar.readInt32()
        sizeY = Ar.readInt32()
        numSlices = Ar.readInt32()
        pixelFormat = Ar.readString()
        if (pixelFormat == "PF_BC6H_Signed") pixelFormat = "PF_BC6H"
        firstMip = Ar.readInt32()
        val mipCount = Ar.readInt32()
        mips = Array(mipCount) { FTexture2DMipMap(Ar, serializeMipData) }

        // UE5.4+: PackedData replaces the old VirtualTextures boolean.
        // It contains packed flags including isVirtual and HasCpuCopy (bit 29).
        if (Ar.game >= GAME_UE5(4)) {
            val packedData = Ar.readUInt32()
            val hasCpuCopy = (packedData.toInt() and (1 shl 29)) != 0
            if (hasCpuCopy) {
                // FSharedImage: SizeX(4) + SizeY(4) + SizeZ(4) + PixelFormat(4) + GammaSpace(1)
                Ar.skip(17)
                // RawData: TArray<byte> = int32 count + byte[count]
                val rawDataCount = Ar.readInt32()
                if (rawDataCount > 0) {
                    Ar.skip(rawDataCount.toLong())
                }
            }
        } else if (Ar.versions["VirtualTextures"]) {
            isVirtual = Ar.readBoolean()
            if (isVirtual) {
                throw ParserException("Texture is virtual, not implemented", Ar)
            }
        }
    }

    fun getFirstMip() = mips[firstMip]

    fun getFirstLoadedMip() = mips.first { it.data.isBulkDataLoaded }

    fun serialize(Ar: FAssetArchiveWriter) {
        Ar.writeInt32(sizeX)
        Ar.writeInt32(sizeY)
        Ar.writeInt32(numSlices)
        Ar.writeString(pixelFormat)
        Ar.writeInt32(firstMip)
        Ar.writeTArray(mips) { it.serialize(Ar) }
        if (Ar.versions["VirtualTextures"]) {
            Ar.writeBoolean(isVirtual)
            if (isVirtual)
                throw ParserException("Texture is virtual, not implemented", Ar)
        }
    }

    constructor(sizeX: Int, sizeY: Int, numSlices: Int, pixelFormat: String, firstMip: Int, mips: Array<FTexture2DMipMap>, isVirtual: Boolean) {
        this.sizeX = sizeX
        this.sizeY = sizeY
        this.numSlices = numSlices
        this.pixelFormat = pixelFormat
        this.firstMip = firstMip
        this.mips = mips
        this.isVirtual = isVirtual
    }
}

class FTexture2DMipMap {
    var cooked: Boolean
    var data: FByteBulkData
    var sizeX: Int
    var sizeY: Int
    var sizeZ: Int
    var derivedDataKey: String? = null

    constructor(Ar: FAssetArchive, serializeMipData: Boolean = true) {
        cooked = if (Ar.ver >= VER_UE4_TEXTURE_SOURCE_ART_REFACTOR && Ar.game < GAME_UE5_BASE) Ar.readBoolean() else Ar.isFilterEditorOnly
        if (serializeMipData) {
            data = FByteBulkData(Ar)
        } else {
            // Mip data is not serialized inline — create an empty placeholder.
            // The actual texture data lives in a streaming container or is loaded on-demand.
            data = FByteBulkData(FByteBulkDataHeader(0, 0L, 0L, 0L), ByteArray(0))
        }
        sizeX = Ar.readInt32()
        sizeY = Ar.readInt32()
        sizeZ = Ar.readInt32()
        if (Ar.ver >= VER_UE4_TEXTURE_DERIVED_DATA2 && !cooked) {
            derivedDataKey = Ar.readString()
        }
    }

    fun serialize(Ar: FAssetArchiveWriter) {
        Ar.writeBoolean(cooked)
        data.serialize(Ar)
        Ar.writeInt32(sizeX)
        Ar.writeInt32(sizeY)
        Ar.writeInt32(sizeZ)
    }

    constructor(cooked: Boolean, data: FByteBulkData, sizeX: Int, sizeY: Int, sizeZ: Int, u: String?) {
        this.cooked = cooked
        this.data = data
        this.sizeX = sizeX
        this.sizeY = sizeY
        this.sizeZ = sizeZ
    }
}