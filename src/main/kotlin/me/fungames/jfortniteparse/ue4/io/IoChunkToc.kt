package me.fungames.jfortniteparse.ue4.io

import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.reader.FArchive
import me.fungames.jfortniteparse.ue4.reader.FByteArchive
import java.io.File

/**
 * On-demand TOC version. Each version adds new fields to the format.
 * Parsed from .uondemandtoc and .iochunktoc files.
 */
enum class EOnDemandTocVersion(val value: UInt) {
    Invalid(0u),
    Initial(1u),
    UTocHash(2u),
    BlockHash32(3u),
    NoRawHash(4u),
    Meta(5u),
    ContainerId(6u),
    AdditionalFiles(7u),
    TagSets(8u),
    ContainerFlags(9u),
    TocFlags(10u),
    HostGroupName(11u),
    ContainerHeader(12u);

    companion object {
        fun fromValue(v: UInt) = values().find { it.value == v } ?: Invalid
    }
}

/**
 * On-demand TOC flags (present when version >= TocFlags).
 */
enum class EOnDemandTocFlags(val value: UInt) {
    None(0u);

    companion object {
        fun fromValue(v: UInt) = values().find { it.value == v } ?: None
    }
}

/**
 * Per-container flags indicating download/mount status.
 */
class EOnDemandContainerFlags(val value: UByte) {
    val isPendingEncryptionKey get() = (value.toInt() and (1 shl 0)) != 0
    val isMounted get() = (value.toInt() and (1 shl 1)) != 0
    val isStreamOnDemand get() = (value.toInt() and (1 shl 2)) != 0
    val isInstallOnDemand get() = (value.toInt() and (1 shl 3)) != 0
    val isEncrypted get() = (value.toInt() and (1 shl 4)) != 0
    val isWithSoftReferences get() = (value.toInt() and (1 shl 5)) != 0
    val isPendingHostGroup get() = (value.toInt() and (1 shl 6)) != 0
}

/**
 * Header of an on-demand TOC file (.uondemandtoc / .iochunktoc).
 *
 * The magic is the ASCII string "ondemand" stored as a little-endian uint64.
 */
class FOnDemandTocHeader {
    companion object {
        const val EXPECTED_MAGIC = 0x6f6e64656d616e64uL // "ondemand" in ASCII
    }

    var version: EOnDemandTocVersion
    var flags: EOnDemandTocFlags = EOnDemandTocFlags.None
    var blockSize: UInt
    var compressionFormat: String
    var chunksDirectory: String
    var hostGroupName: String = ""

    constructor(Ar: FArchive) {
        val magic = Ar.readUInt64()
        if (magic != EXPECTED_MAGIC) {
            throw ParserException("Invalid on-demand TOC magic: 0x%016X (expected 0x%016X)".format(magic.toLong(), EXPECTED_MAGIC.toLong()), Ar)
        }

        version = EOnDemandTocVersion.fromValue(Ar.readUInt32())

        if (version >= EOnDemandTocVersion.TocFlags) {
            flags = EOnDemandTocFlags.fromValue(Ar.readUInt32())
        }

        blockSize = Ar.readUInt32()
        compressionFormat = Ar.readString()
        chunksDirectory = Ar.readString()

        if (version >= EOnDemandTocVersion.HostGroupName) {
            hostGroupName = Ar.readString()
        }
    }
}

/**
 * Build metadata embedded in the on-demand TOC (present when version >= Meta).
 */
class FTocMeta {
    var epochTimestamp: Long
    var buildVersion: String
    var targetPlatform: String

    constructor(Ar: FArchive) {
        epochTimestamp = Ar.readInt64()
        buildVersion = Ar.readString()
        targetPlatform = Ar.readString()
    }
}

/**
 * A single on-demand chunk entry. Describes one asset chunk within a container.
 *
 * Matches the C# struct layout: Hash(20) + ChunkId(12) + RawSize(8) + EncodedSize(8)
 * + BlockOffset(4) + BlockCount(4) = 56 bytes per entry.
 */
class FOnDemandTocEntry {
    var hash: ByteArray       // 20-byte SHA hash
    var chunkId: FIoChunkId   // 12-byte chunk identifier
    var rawSize: ULong        // uncompressed size
    var encodedSize: ULong    // compressed/encoded size
    var blockOffset: UInt     // offset into the block arrays
    var blockCount: UInt      // number of compression blocks

    constructor(Ar: FArchive) {
        hash = Ar.read(20)
        chunkId = FIoChunkId(Ar)
        rawSize = Ar.readUInt64()
        encodedSize = Ar.readUInt64()
        blockOffset = Ar.readUInt32()
        blockCount = Ar.readUInt32()
    }
}

/**
 * Describes one on-demand container and all its chunks.
 * The key field is [header] (version >= ContainerHeader) which contains the
 * serialized FIoContainerHeader data that FPackageStore needs.
 */
class FOnDemandTocContainerEntry {
    var containerId: FIoContainerId = FIoContainerId()
    var containerName: String
    var encryptionKeyGuid: String
    var entries: Array<FOnDemandTocEntry>
    var blockSizes: UIntArray
    var blockHashes: UIntArray
    var utocHash: ByteArray    // 20-byte SHA hash of the .utoc file
    var containerFlags: EOnDemandContainerFlags = EOnDemandContainerFlags(0u)
    var header: ByteArray = ByteArray(0) // serialized container header data

    constructor(Ar: FArchive, version: EOnDemandTocVersion) {
        if (version >= EOnDemandTocVersion.ContainerId) {
            containerId = FIoContainerId(Ar)
        }

        containerName = Ar.readString()
        encryptionKeyGuid = Ar.readString()

        // Entries array: count + flat structs
        val entryCount = Ar.readInt32()
        entries = Array(entryCount) { FOnDemandTocEntry(Ar) }

        // Block sizes: count + uint32 values
        val blockSizeCount = Ar.readInt32()
        blockSizes = UIntArray(blockSizeCount) { Ar.readUInt32() }

        // Block hashes: count + uint32 values (FIoBlockHash = uint32)
        val blockHashCount = Ar.readInt32()
        blockHashes = UIntArray(blockHashCount) { Ar.readUInt32() }

        // UToc hash: 20-byte SHA hash (read flat, no length prefix)
        utocHash = Ar.read(20)

        if (version >= EOnDemandTocVersion.ContainerFlags) {
            containerFlags = EOnDemandContainerFlags(Ar.readUInt8())
        }

        // Container header bytes: the serialized FIoContainerHeader data.
        // This is what FPackageStore uses instead of reading from the .ucas file.
        if (version >= EOnDemandTocVersion.ContainerHeader) {
            val headerSize = Ar.readInt32()
            header = if (headerSize > 0) Ar.read(headerSize) else ByteArray(0)
        }
    }
}

/**
 * Additional file metadata (present when version >= AdditionalFiles).
 */
class FOnDemandTocAdditionalFile {
    var hash: ByteArray
    var filename: String
    var fileSize: ULong

    constructor(Ar: FArchive) {
        hash = Ar.read(20)
        filename = Ar.readString()
        fileSize = Ar.readUInt64()
    }
}

/**
 * Tag set package list (used with TagSets).
 */
class FOnDemandTocTagSetPackageList {
    var containerIndex: UInt
    var packageIndices: UIntArray

    constructor(Ar: FArchive) {
        containerIndex = Ar.readUInt32()
        val count = Ar.readInt32()
        packageIndices = UIntArray(count) { Ar.readUInt32() }
    }
}

/**
 * A tag set grouping packages across containers (present when version >= TagSets).
 */
class FOnDemandTocTagSet {
    var tag: String
    var packages: Array<FOnDemandTocTagSetPackageList>

    constructor(Ar: FArchive) {
        tag = Ar.readString()
        val count = Ar.readInt32()
        packages = Array(count) { FOnDemandTocTagSetPackageList(Ar) }
    }
}

/**
 * The main on-demand TOC file parser.
 *
 * Parses .uondemandtoc and .iochunktoc files, which describe containers
 * whose assets are streamed on-demand (e.g. Fortnite cosmetics).
 *
 * The critical data is [containers] — each entry includes a [header] field
 * containing the serialized FIoContainerHeader. FPackageStore uses this header
 * instead of reading the ContainerHeader chunk from the .ucas file, which
 * may not exist for on-demand containers.
 */
class IoChunkToc {
    val header: FOnDemandTocHeader
    var meta: FTocMeta? = null
    val containers: Array<FOnDemandTocContainerEntry>
    var additionalFiles: Array<FOnDemandTocAdditionalFile> = emptyArray()
    var tagSets: Array<FOnDemandTocTagSet> = emptyArray()

    constructor(file: File) : this(FByteArchive(file.readBytes()))

    constructor(bytes: ByteArray) : this(FByteArchive(bytes))

    constructor(Ar: FArchive) {
        header = FOnDemandTocHeader(Ar)

        if (header.version >= EOnDemandTocVersion.Meta) {
            meta = FTocMeta(Ar)
        }

        val containerCount = Ar.readInt32()
        containers = Array(containerCount) { FOnDemandTocContainerEntry(Ar, header.version) }

        if (header.version >= EOnDemandTocVersion.AdditionalFiles) {
            val additionalFileCount = Ar.readInt32()
            additionalFiles = Array(additionalFileCount) { FOnDemandTocAdditionalFile(Ar) }
        }

        if (header.version >= EOnDemandTocVersion.TagSets) {
            val tagSetCount = Ar.readInt32()
            tagSets = Array(tagSetCount) { FOnDemandTocTagSet(Ar) }
        }
    }

    /**
     * Find a container entry by name (e.g. "pakchunk0ondemand-WindowsClient").
     */
    fun findContainer(name: String): FOnDemandTocContainerEntry? {
        return containers.firstOrNull { it.containerName.equals(name, ignoreCase = true) }
    }

    /**
     * Find a container entry by container ID.
     */
    fun findContainer(containerId: FIoContainerId): FOnDemandTocContainerEntry? {
        return containers.firstOrNull { it.containerId == containerId }
    }
}
