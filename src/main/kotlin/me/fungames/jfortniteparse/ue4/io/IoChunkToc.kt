package me.fungames.jfortniteparse.ue4.io

import me.fungames.jfortniteparse.LOG_JFP
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

    /** Construct from pre-parsed values (used by V2 format parser). */
    constructor(
        chunksDirectory: String,
        compressionFormat: String,
        version: EOnDemandTocVersion = EOnDemandTocVersion.ContainerHeader,
        blockSize: UInt = 65536u
    ) {
        this.version = version
        this.blockSize = blockSize
        this.compressionFormat = compressionFormat
        this.chunksDirectory = chunksDirectory
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

    constructor(epochTimestamp: Long, buildVersion: String, targetPlatform: String) {
        this.epochTimestamp = epochTimestamp
        this.buildVersion = buildVersion
        this.targetPlatform = targetPlatform
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

    /** V1 constructor: 56 bytes per entry */
    constructor(Ar: FArchive) {
        hash = Ar.read(20)
        chunkId = FIoChunkId(Ar)
        rawSize = Ar.readUInt64()
        encodedSize = Ar.readUInt64()
        blockOffset = Ar.readUInt32()
        blockCount = Ar.readUInt32()
    }

    /** Constructor from pre-parsed values (used by V2 SoA parser) */
    constructor(hash: ByteArray, chunkId: FIoChunkId, rawSize: ULong, encodedSize: ULong, blockOffset: UInt, blockCount: UInt) {
        this.hash = hash
        this.chunkId = chunkId
        this.rawSize = rawSize
        this.encodedSize = encodedSize
        this.blockOffset = blockOffset
        this.blockCount = blockCount
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

    /** Construct from pre-parsed values (used by V2 format parser). */
    constructor(
        containerId: FIoContainerId,
        containerName: String,
        encryptionKeyGuid: String,
        entries: Array<FOnDemandTocEntry>,
        blockSizes: UIntArray,
        blockHashes: UIntArray,
        utocHash: ByteArray,
        containerFlags: EOnDemandContainerFlags,
        header: ByteArray
    ) {
        this.containerId = containerId
        this.containerName = containerName
        this.encryptionKeyGuid = encryptionKeyGuid
        this.entries = entries
        this.blockSizes = blockSizes
        this.blockHashes = blockHashes
        this.utocHash = utocHash
        this.containerFlags = containerFlags
        this.header = header
    }

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

    constructor(file: File) : this(file.readBytes())

    constructor(bytes: ByteArray) {
        val magic = String(bytes, 0, 16, Charsets.US_ASCII)
        if (magic == "UE ON-DEMAND TOC") {
            // New v2 compact format (Fortnite v40.00+)
            val parsed = IoChunkTocV2(bytes)
            header = parsed.header
            meta = parsed.meta
            containers = parsed.containers
        } else {
            // Old format — use sequential archive reader
            val Ar = FByteArchive(bytes)
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

/**
 * Parser for the new "UE ON-DEMAND TOC" format (Fortnite v40.00+).
 *
 * Based on the C# reference implementation (FOnDemandIoStoreTocParser.cs).
 *
 * Layout:
 *   [0x00]  16-byte magic "UE ON-DEMAND TOC"
 *   [0x10]  Header (112 bytes): version, epoch, 5 StringEntry pairs, stringTableLength, containerCount, padding
 *   [0x80]  String table (stringTableLength bytes, UTF-8, referenced by StringEntry offsets)
 *   [0x80 + stringTableLength]  Container entries (containerCount × 128 bytes each)
 *   [after entries]  Data section (per-container: payload + containerHeader)
 *   [last 16 bytes]  Footer magic "UE ON-DEMAND TOC"
 *
 * Each container's data section has two parts:
 *   payload (dataSize - containerHeaderSize bytes):
 *     FIoChunkId[chunkCount]              (12 bytes each)
 *     CurrentChunkEntry[chunkCount]       (36 bytes each: Hash20 + RawSize4 + EncodedSize4 + OffsetOrSize4 + CountOrHash4)
 *     uint32[blockCount] sharedBlockSizes
 *     uint32[blockCount] sharedBlockHashes
 *     TagSetEntry[tagSetCount]            (16 bytes each, skipped)
 *     uint32[tagSetIndicesCount]          (skipped)
 *   containerHeader (containerHeaderSize bytes) — at the END
 */
class IoChunkTocV2(private val data: ByteArray) {
    val header: FOnDemandTocHeader
    val meta: FTocMeta
    val containers: Array<FOnDemandTocContainerEntry>

    private fun readU16(off: Int): Int = (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)
    private fun readU32(off: Int): Int = (data[off].toInt() and 0xFF) or
            ((data[off + 1].toInt() and 0xFF) shl 8) or
            ((data[off + 2].toInt() and 0xFF) shl 16) or
            ((data[off + 3].toInt() and 0xFF) shl 24)
    private fun readU32U(off: Int): UInt = readU32(off).toUInt()
    private fun readU32L(off: Int): Long = readU32(off).toLong() and 0xFFFFFFFFL
    private fun readI64(off: Int): Long = readU32L(off) or (readU32L(off + 4) shl 32)

    companion object {
        private const val SIGNATURE_SIZE = 16
        private const val HEADER_SIZE = 128       // signature + header fields = 0x80
        private const val CONTAINER_ENTRY_SIZE = 128
        private const val CHUNK_ENTRY_SIZE = 36   // Hash(20) + RawSize(4) + EncodedSize(4) + OffsetOrSize(4) + CountOrHash(4)
        private const val TAG_SET_ENTRY_SIZE = 16
        private const val BLOCK_INFO_HAS_OFFSET_MASK = 0x80000000u
    }

    init {
        // Validate footer signature
        if (data.size >= SIGNATURE_SIZE) {
            val footer = String(data, data.size - SIGNATURE_SIZE, SIGNATURE_SIZE, Charsets.US_ASCII)
            if (footer != "UE ON-DEMAND TOC") {
                LOG_JFP.warn("V2 TOC: Invalid footer signature")
            }
        }

        // --- Header (offsets 0x10 - 0x7F) ---
        // 0x10: ushort majorVersion, 0x12: ushort minorVersion, 0x14: uint32 padding
        // 0x18: int64 epochTimestamp
        // 0x20: StringEntry buildVersion    {offset(4), length(4)}
        // 0x28: StringEntry targetPlatform  {offset(4), length(4)}
        // 0x30: StringEntry chunksDirectory {offset(4), length(4)}
        // 0x38: StringEntry hostGroupName   {offset(4), length(4)}
        // 0x40: StringEntry compression     {offset(4), length(4)}
        // 0x48: int32 stringTableLength
        // 0x4C: int32 containerCount
        // 0x50-0x7F: 48 bytes padding
        val epochTimestamp = readI64(0x18)
        val buildVersionOff = readU32(0x20);  val buildVersionLen = readU32(0x24)
        val platformOff = readU32(0x28);      val platformLen = readU32(0x2C)
        val chunksDirOff = readU32(0x30);     val chunksDirLen = readU32(0x34)
        val hostGroupOff = readU32(0x38);     val hostGroupLen = readU32(0x3C)
        val compressionOff = readU32(0x40);   val compressionLen = readU32(0x44)
        val stringTableLength = readU32(0x48)
        val containerCount = readU32(0x4C)

        // --- String table at 0x80 ---
        val strBase = HEADER_SIZE
        fun str(off: Int, len: Int) = if (len > 0) String(data, strBase + off, len, Charsets.UTF_8) else ""

        val buildVersion = str(buildVersionOff, buildVersionLen)
        val targetPlatform = str(platformOff, platformLen)
        val chunksDirectory = str(chunksDirOff, chunksDirLen)
        val hostGroupName = str(hostGroupOff, hostGroupLen)
        val compressionFormat = str(compressionOff, compressionLen)

        meta = FTocMeta(epochTimestamp, buildVersion, targetPlatform)

        // --- Container entries immediately after string table ---
        val containerEntriesStart = strBase + stringTableLength
        val dataSectionStart = containerEntriesStart + containerCount * CONTAINER_ENTRY_SIZE

        var headerBlockSize = 0u

        containers = Array(containerCount) { i ->
            val base = containerEntriesStart + i * CONTAINER_ENTRY_SIZE

            // Container entry layout (128 bytes):
            //  +0:  FGuid encryptionKeyGuid (16 bytes)
            // +16:  FIoContainerId containerId (8 bytes)
            // +24:  StringEntry containerName {offset(4), length(4)}
            // +32:  int containerHeaderSize
            // +36:  int dataOffset (into data section)
            // +40:  int dataSize
            // +44:  int chunkCount
            // +48:  int blockCount
            // +52:  uint blockSize
            // +56:  int tagSetCount
            // +60:  int tagSetIndicesCount
            // +64:  FSHAHash utocHash (20 bytes)
            // +84:  uint containerFlags
            // +88:  padding (40 bytes)
            val containerId = FIoContainerId(readI64(base + 16).toULong())
            val nameOffset = readU32(base + 24)
            val nameLength = readU32(base + 28)
            val containerName = str(nameOffset, nameLength)
            val containerHeaderSize = readU32(base + 32)
            val dataOffset = readU32(base + 36)
            val dataSize = readU32(base + 40)
            val chunkCount = readU32(base + 44)
            val blockCount = readU32(base + 48)
            val blockSize = readU32U(base + 52)
            val tagSetCount = readU32(base + 56)
            val tagSetIndicesCount = readU32(base + 60)
            val utocHash = data.copyOfRange(base + 64, base + 84)
            val containerFlags = readU32U(base + 84)

            if (blockSize != 0u) headerBlockSize = blockSize

            if (dataSize < containerHeaderSize) {
                LOG_JFP.warn("V2 TOC: Container '{}' has invalid size (data={} < header={})", containerName, dataSize, containerHeaderSize)
                return@Array FOnDemandTocContainerEntry(
                    containerId, containerName, "", emptyArray(), UIntArray(0), UIntArray(0),
                    utocHash, EOnDemandContainerFlags(0u), ByteArray(0)
                )
            }

            // --- Per-container data section ---
            // Payload comes first, then container header at the end
            val containerDataStart = dataSectionStart + dataOffset
            val payloadSize = dataSize - containerHeaderSize

            try {
                var off = containerDataStart

                // 1) FIoChunkId[chunkCount] — 12 bytes each
                val chunkIds = Array(chunkCount) {
                    val ar = FByteArchive(data.copyOfRange(off, off + 12))
                    off += 12
                    FIoChunkId(ar)
                }

                // 2) CurrentChunkEntry[chunkCount] — 36 bytes each
                data class RawChunkEntry(val hash: ByteArray, val rawSize: UInt, val encodedSize: UInt, val offsetOrSize: UInt, val countOrHash: UInt)
                val rawEntries = Array(chunkCount) {
                    val hash = data.copyOfRange(off, off + 20); off += 20
                    val rawSize = readU32U(off); off += 4
                    val encodedSize = readU32U(off); off += 4
                    val offsetOrSize = readU32U(off); off += 4
                    val countOrHash = readU32U(off); off += 4
                    RawChunkEntry(hash, rawSize, encodedSize, offsetOrSize, countOrHash)
                }

                // 3) Shared block sizes and hashes
                val sharedBlockSizes = UIntArray(blockCount) { readU32U(off).also { off += 4 } }
                val sharedBlockHashes = UIntArray(blockCount) { readU32U(off).also { off += 4 } }

                // 4) Skip tag sets and tag set indices (not needed for asset loading)
                // off += tagSetCount * TAG_SET_ENTRY_SIZE + tagSetIndicesCount * 4

                // 5) Resolve block info per-chunk (matching C# OffsetOrSize/CountOrHash logic)
                val legacyBlockSizes = mutableListOf<UInt>()
                val legacyBlockHashes = mutableListOf<UInt>()

                val entries = Array(chunkCount) { idx ->
                    val ce = rawEntries[idx]
                    val blockOffset = legacyBlockSizes.size.toUInt()
                    val hasOffset = (ce.offsetOrSize and BLOCK_INFO_HAS_OFFSET_MASK) != 0u
                    val blockInfoValue = ce.offsetOrSize and BLOCK_INFO_HAS_OFFSET_MASK.inv()

                    val blockEntryCount: UInt
                    if (hasOffset) {
                        // References a range in the shared block arrays
                        blockEntryCount = ce.countOrHash
                        if (blockEntryCount != 0u) {
                            val sharedOffset = blockInfoValue.toInt()
                            val sharedCount = blockEntryCount.toInt()
                            for (bi in 0 until sharedCount) {
                                legacyBlockSizes.add(sharedBlockSizes[sharedOffset + bi])
                                legacyBlockHashes.add(sharedBlockHashes[sharedOffset + bi])
                            }
                        }
                    } else {
                        // Inline single block: size in blockInfoValue, hash in countOrHash
                        blockEntryCount = if (blockInfoValue == 0u) 0u else 1u
                        if (blockEntryCount != 0u) {
                            legacyBlockSizes.add(blockInfoValue)
                            legacyBlockHashes.add(ce.countOrHash)
                        }
                    }

                    FOnDemandTocEntry(
                        ce.hash, chunkIds[idx],
                        ce.rawSize.toULong(), ce.encodedSize.toULong(),
                        blockOffset, blockEntryCount
                    )
                }

                // 6) Container header at the END of the data block
                val headerStart = containerDataStart + payloadSize
                val headerBytes = if (containerHeaderSize > 0 && headerStart + containerHeaderSize <= data.size) {
                    data.copyOfRange(headerStart, headerStart + containerHeaderSize)
                } else ByteArray(0)

                // 7) Convert V2 container flags (bit0=Install, bit1=Stream) to JFP bit positions
                var jfpFlags: UByte = 0u
                if (containerFlags and 1u != 0u) jfpFlags = (jfpFlags.toInt() or (1 shl 3)).toUByte() // InstallOnDemand -> bit 3
                if (containerFlags and 2u != 0u) jfpFlags = (jfpFlags.toInt() or (1 shl 2)).toUByte() // StreamOnDemand -> bit 2

                FOnDemandTocContainerEntry(
                    containerId = containerId,
                    containerName = containerName,
                    encryptionKeyGuid = "",
                    entries = entries,
                    blockSizes = legacyBlockSizes.toUIntArray(),
                    blockHashes = legacyBlockHashes.toUIntArray(),
                    utocHash = utocHash,
                    containerFlags = EOnDemandContainerFlags(jfpFlags),
                    header = headerBytes
                )
            } catch (e: Exception) {
                LOG_JFP.warn("V2 TOC: Failed to parse container '{}': {}", containerName, e.message)
                FOnDemandTocContainerEntry(
                    containerId, containerName, "", emptyArray(), UIntArray(0), UIntArray(0),
                    utocHash, EOnDemandContainerFlags(0u), ByteArray(0)
                )
            }
        }

        header = FOnDemandTocHeader(
            chunksDirectory = chunksDirectory,
            compressionFormat = compressionFormat,
            version = EOnDemandTocVersion.ContainerHeader,
            blockSize = headerBlockSize
        )
        header.hostGroupName = hostGroupName
    }
}
