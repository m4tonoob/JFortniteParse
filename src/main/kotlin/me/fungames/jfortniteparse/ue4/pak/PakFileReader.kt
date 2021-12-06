package me.fungames.jfortniteparse.ue4.pak

import me.fungames.jfortniteparse.compression.Compression
import me.fungames.jfortniteparse.encryption.aes.Aes
import me.fungames.jfortniteparse.exceptions.InvalidAesKeyException
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.pak.enums.PakVersion_Latest
import me.fungames.jfortniteparse.ue4.pak.enums.PakVersion_PathHashIndex
import me.fungames.jfortniteparse.ue4.pak.objects.FPakCompressedBlock
import me.fungames.jfortniteparse.ue4.pak.objects.FPakEntry
import me.fungames.jfortniteparse.ue4.pak.objects.FPakInfo
import me.fungames.jfortniteparse.ue4.pak.reader.FPakArchive
import me.fungames.jfortniteparse.ue4.pak.reader.FPakFileArchive
import me.fungames.jfortniteparse.ue4.reader.FByteArchive
import me.fungames.jfortniteparse.ue4.versions.VersionContainer
import me.fungames.jfortniteparse.util.INDEX_NONE
import me.fungames.jfortniteparse.util.align
import me.fungames.jfortniteparse.util.printAesKey
import me.fungames.jfortniteparse.util.printHexBinary
import mu.KotlinLogging
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.min

private typealias FPathHashIndex = Map<ULong, Int>

private typealias FPakDirectory = Map<String, Int>
private typealias FDirectoryIndex = Map<String, FPakDirectory>

class PakFileReader(val Ar: FPakArchive, val keepIndexData: Boolean = false) : Closeable {
    constructor(file: File, versions: VersionContainer = VersionContainer.DEFAULT) : this(FPakFileArchive(RandomAccessFile(file, "r"), file, versions))
    constructor(filePath: String, versions: VersionContainer = VersionContainer.DEFAULT) : this(File(filePath), versions)

    //var encodedPakEntries: ByteArray = byteArrayOf()
    //    private set

    var concurrent = false

    val fileName = Ar.fileName

    val pakInfo : FPakInfo = FPakInfo.readPakInfo(Ar)
    var aesKey : ByteArray? = null
    var aesKeyStr : String?
        get() = aesKey?.printAesKey()
        set(value) {
            aesKey = value?.let { Aes.parseKey(it) }
        }

    lateinit var mountPrefix : String

    val fileCount get() = files.size
    var encryptedFileCount = 0
        private set
    lateinit var files : List<GameFile>
        private set

    //var pathHashSeed = 0UL

    //var hasPathHashIndex = false
    lateinit var pathHashIndex : FPathHashIndex

    //var hasFullDirectoryIndex = false
    lateinit var directoryIndex : FDirectoryIndex

    val useDecryptedBuffers = !pakInfo.encryptionKeyGuid.isValid() && isEncrypted() && decryptedBuffersDir.exists()

    init {
        if (pakInfo.version > PakVersion_Latest)
            logger.warn("Pak file \"$fileName\" has unsupported version ${pakInfo.version}")
        Ar.pakInfo = pakInfo
    }

    override fun toString() = fileName

    fun isEncrypted() = pakInfo.encryptedIndex

    fun extract(gameFile: GameFile) : ByteArray {
        val result = extractBuffer(gameFile)
        return if (result.hasArray())
            result.array()
        else {
            val buf = ByteArray(result.remaining())
            result[buf]
            buf
        }
    }

    fun extractBuffer(gameFile: GameFile) : ByteBuffer {
        require(gameFile.pakFileName == fileName) { "Wrong pak file reader, required ${gameFile.pakFileName}, this is $fileName" }
        logger.debug("Extracting ${gameFile.getName()} from $fileName at ${gameFile.pos} with size ${gameFile.size}")
        // If this reader is used as a concurrent reader create a clone of the main reader to
        // provide thread safety
        val exAr = if (concurrent) Ar.clone() else Ar
        exAr.seek(gameFile.pos)
        // Pak Entry is written before the file data,
        // but its the same as the one from the index, just without a name
        val tempEntry = FPakEntry(exAr, false)
        tempEntry.compressionBlocks.forEach {
            it.compressedStart += gameFile.pos
            it.compressedEnd += gameFile.pos
        }
        when {
            gameFile.isCompressed() -> {
                logger.debug("${gameFile.getName()} is compressed with ${gameFile.compressionMethod}")
                val uncompressedBuffer = ByteArray(gameFile.uncompressedSize.toInt())
                var uncompressedBufferOff = 0
                tempEntry.compressionBlocks.forEach { block ->
                    exAr.seek(block.compressedStart)
                    var srcSize = (block.compressedEnd - block.compressedStart).toInt()
                    // Read the compressed block
                    val compressedBuffer = if (gameFile.isEncrypted) {
                        // The compressed block is encrypted, align it and then decrypt
                        if (useDecryptedBuffers) throw ParserException("Decrypting encrypted files does not work on decrypted buffers mode")
                        val key = aesKey
                            ?: throw ParserException("Decrypting a encrypted file requires an encryption key to be set")
                        srcSize = align(srcSize, Aes.BLOCK_SIZE)
                        exAr.read(srcSize).also { Aes.decryptData(it, key) }
                    } else {
                        // Read the block data
                        exAr.read(srcSize)
                    }
                    // Calculate the uncompressed size,
                    // its either just the compression block size
                    // or if its the last block its the remaining data size
                    val uncompressedSize = min(gameFile.compressionBlockSize, (gameFile.uncompressedSize - uncompressedBufferOff).toInt())
                    Compression.uncompressMemory(gameFile.compressionMethod.name, uncompressedBuffer, uncompressedBufferOff, uncompressedSize, compressedBuffer, 0, srcSize)
                    uncompressedBufferOff += gameFile.compressionBlockSize
                }
                return ByteBuffer.wrap(uncompressedBuffer)
            }
            gameFile.isEncrypted -> {
                logger.debug("${gameFile.getName()} is encrypted, decrypting")
                if (useDecryptedBuffers) throw ParserException("Decrypting encrypted files does not work on decrypted buffers mode")
                val key = aesKey
                    ?: throw ParserException("Decrypting a encrypted file requires an encryption key to be set")
                // AES is block encryption, all encrypted blocks need to be 16 bytes long,
                // fix the game file length by growing it to the next multiple of 16 bytes
                val newLength = align(gameFile.size.toInt(), Aes.BLOCK_SIZE)
                val buffer = exAr.read(newLength)
                Aes.decryptData(buffer, key)
                return ByteBuffer.wrap(buffer, 0, gameFile.size.toInt())
            }
            else -> return exAr.readBuffer(gameFile.size.toInt())
        }
    }

    fun indexCheckBytes() : ByteArray {
        Ar.seek(pakInfo.indexOffset)
        return Ar.read(128)
    }

    /**
     * Test all keys from a collection and return the working one if there is one
     */
    fun testAesKeys(keys : Iterable<ByteArray>) : ByteArray? {
        if (!isEncrypted())
            return null
        keys.forEach {
            if (testAesKey(it))
                return it
        }
        return null
    }

    /**
     * Test all keys from a collection and return the working one if there is one
     */
    @JvmName("testAesKeysStr")
    fun testAesKeys(keys : Iterable<String>) : String? {
        if (!isEncrypted())
            return null
        keys.forEach {
            if (testAesKey(it))
                return it
        }
        return null
    }

    /**
     * Test whether the given encryption key is valid by attempting to read the pak mount point and validating it
     */
    fun testAesKey(key : ByteArray) : Boolean {
        if (!isEncrypted())
            return true
        return testAesKey(indexCheckBytes(), key)
    }

    /**
     * Test whether the given encryption key is valid by attempting to read the pak mount point and validating it
     */
    fun testAesKey(key : String) = testAesKey(Aes.parseKey(key))

    private fun readIndexUpdated() : List<GameFile> {
        // Prepare primary index and decrypt if necessary
        val primaryIndexAr = readIndexData(pakInfo.indexOffset, pakInfo.indexSize, pakInfo.indexHash)

        mountPrefix = runCatching { primaryIndexAr.readString().substringAfter("../../../") }.getOrElse {
            throw InvalidAesKeyException("Given encryption key '$aesKeyStr' is not working with '$fileName'", it)
        }

        val fileCount = primaryIndexAr.readInt32()
        primaryIndexAr.skip(8) // PathHashSeed

        if (!primaryIndexAr.readBoolean())
            throw ParserException("No path hash index")

        primaryIndexAr.skip(36) // PathHashIndexOffset (long) + PathHashIndexSize (long) + PathHashIndexHash (20 bytes)

        if (!primaryIndexAr.readBoolean())
            throw ParserException("No directory index")

        val directoryIndexOffset = primaryIndexAr.readInt64()
        val directoryIndexSize = primaryIndexAr.readInt64()
        val directoryIndexHash = primaryIndexAr.read(20)

        val encodedPakEntriesSize = primaryIndexAr.readInt32()
        val encodedPakEntries = primaryIndexAr.readBuffer(encodedPakEntriesSize)

        if (primaryIndexAr.readInt32() < 0)
            throw ParserException("Corrupt pak PrimaryIndex detected!")

        val directoryIndexAr = readIndexData(directoryIndexOffset, directoryIndexSize, directoryIndexHash)
        val directoryIndex = directoryIndexAr.readTMap {
            it.readString() to it.readTMap { it2 ->
                it2.readString() to it2.readInt32()
            }
        }

        val encodedPakEntriesAr = FByteArchive(encodedPakEntries)
        val begin = encodedPakEntriesAr.pos()

        val tempMap = HashMap<String, GameFile>(fileCount)
        var finalFileCount = 0
        for ((dirName, dirContent) in directoryIndex) {
            for ((fileName, offset) in dirContent) {
                val path = dirName + fileName
                encodedPakEntriesAr.seek(begin + offset)
                val entry = readBitEntry(encodedPakEntriesAr)
                entry.name = path
                if(entry.isEncrypted)
                    encryptedFileCount++
                val gameFile = GameFile(entry, mountPrefix, this.fileName)
                tempMap[mountPrefix + path] = gameFile
                if (!path.endsWith(".uexp") && !path.endsWith(".ubulk"))
                    finalFileCount++
            }
        }

        val files = ArrayList<GameFile>(finalFileCount)
        tempMap.values.forEach {
            if (it.isUE4Package()) {
                val uexp = tempMap[it.path.substringBeforeLast(".") + ".uexp"]
                if(uexp != null)
                    it.uexp = uexp
                val ubulk = tempMap[it.path.substringBeforeLast(".") + ".ubulk"]
                if(ubulk != null)
                    it.ubulk = ubulk
                files.add(it)
            } else {
                if (!it.path.endsWith(".uexp") && !it.path.endsWith(".ubulk"))
                    files.add(it)
            }
        }
        this.files = files
        return files
    }

    private fun readBitEntry(Ar: FByteArchive): FPakEntry {
        // Grab the big bitfield value:
        // Bit 31 = Offset 32-bit safe?
        // Bit 30 = Uncompressed size 32-bit safe?
        // Bit 29 = Size 32-bit safe?
        // Bits 28-23 = Compression method
        // Bit 22 = Encrypted
        // Bits 21-6 = Compression blocks count
        // Bits 5-0 = Compression block size

        val compressionMethodIndex: UInt
        var compressionBlockSize: UInt
        val offset: Long
        val uncompressedSize: Long
        val size: Long
        val encrypted: Boolean
        val compressionBlocks: Array<FPakCompressedBlock>

        val value = Ar.readUInt32()

        val localCompressionBlockSize = if ((value and 0x3fu) == 0x3fu) {
            Ar.readUInt32()
        } else {
            // for backwards compatibility with old paks :
            (value and 0x3fu) shl 11
        }

        // Filter out the CompressionMethod.
        compressionMethodIndex = (value shr 23) and 0x3fu

        // Test for 32-bit safe values. Grab it, or memcpy the 64-bit value
        // to avoid alignment exceptions on platforms requiring 64-bit alignment
        // for 64-bit variables.
        //
        // Read the Offset.
        val isOffset32BitSafe = (value and (1u shl 31)) != 0u
        offset = if (isOffset32BitSafe) {
            Ar.readUInt32().toLong()
        } else {
            Ar.readInt64()
        }

        // Read the UncompressedSize.
        val isUncompressedSize32BitSafe = (value and (1u shl 30)) != 0u
        uncompressedSize = if (isUncompressedSize32BitSafe) {
            Ar.readUInt32().toLong()
        } else {
            Ar.readInt64()
        }

        // Fill in the Size.
        size = if (compressionMethodIndex != 0u) {
            // Size is only present if compression is applied.
            val isSize32BitSafe = (value and (1u shl 29)) != 0u
            if (isSize32BitSafe) {
                Ar.readUInt32().toLong()
            } else {
                Ar.readInt64()
            }
        } else {
            // The Size is the same thing as the UncompressedSize when
            // CompressionMethod == COMPRESS_None.
            uncompressedSize
        }

        // Filter the encrypted flag.
        encrypted = (value and (1u shl 22)) != 0u

        // This should clear out any excess CompressionBlocks that may be valid in the user's
        // passed in entry.
        val compressionBlocksCount = (value shr 6) and 0xffffu

        compressionBlocks = Array(compressionBlocksCount.toInt()) { FPakCompressedBlock(0L, 0L) }

        compressionBlockSize = 0u
        if (compressionBlocksCount > 0u) {
            compressionBlockSize = localCompressionBlockSize
            // Per the comment in Encode, if CompressionBlocksCount == 1, we use UncompressedSize for CompressionBlockSize
            if (compressionBlocksCount == 1u) {
                compressionBlockSize = uncompressedSize.toUInt()
            }
            check(compressionBlockSize != 0u)
        }

        // Compute StructSize: each file still have FPakEntry data prepended, and it should be skipped.
        val structSize = FPakEntry.getSerializedSize(pakInfo.version, compressionMethodIndex.toInt(), compressionBlocksCount.toInt()) // 73 if latest, nonzero, 1

        // Handle building of the CompressionBlocks array.
        if (compressionBlocks.size == 1 && !encrypted) {
            // If the number of CompressionBlocks is 1, we didn't store any extra information.
            // Derive what we can from the entry's file offset and size.
            val compressedBlock = compressionBlocks[0]
            compressedBlock.compressedStart = offset + structSize
            compressedBlock.compressedEnd = compressedBlock.compressedStart + size
        } else if (compressionBlocks.isNotEmpty()) {
            // Alignment of the compressed blocks
            val compressedBlockAlignment = if (encrypted) Aes.BLOCK_SIZE else 1

            // CompressedBlockOffset is the starting offset. Everything else can be derived from there.
            var compressedBlockOffset = offset + structSize
            for (compressedBlock in compressionBlocks) {
                compressedBlock.compressedStart = compressedBlockOffset
                compressedBlock.compressedEnd = (compressedBlockOffset.toUInt() + Ar.readUInt32()).toLong()
                compressedBlockOffset += align(compressedBlock.compressedEnd - compressedBlock.compressedStart, compressedBlockAlignment.toLong())
            }
        }
        return FPakEntry(pakInfo, "", offset, size, uncompressedSize, compressionMethodIndex.toInt(), compressionBlocks, encrypted, compressionBlockSize.toInt())
    }

    fun readIndex(): List<GameFile> {
        val start = System.currentTimeMillis()
        val files = if (pakInfo.version >= PakVersion_PathHashIndex) readIndexUpdated() else readIndexLegacy()

        // Print statistics
        var stats = "Pak \"%s\": %d files".format(if (Ar is FPakFileArchive) Ar.file else fileName, fileCount)
        if (encryptedFileCount != 0)
            stats += " (%d encrypted)".format(encryptedFileCount)
        if (mountPrefix.contains('/'))
            stats += ", mount point: \"%s\"".format(mountPrefix)
        logger.info(stats + ", version %d in %dms".format(pakInfo.version, System.currentTimeMillis() - start))

        return files
    }

    private fun readIndexLegacy() : List<GameFile> {
        // Prepare index and decrypt if necessary
        val indexAr = readIndexData(pakInfo.indexOffset, pakInfo.indexSize, pakInfo.indexHash)

        // Read the index
        var mountPoint = runCatching { indexAr.readString() }.getOrElse {
            throw InvalidAesKeyException("Given encryption key '$aesKeyStr' is not working with '$fileName'", it)
        }
        var badMountPoint = false
        if (!mountPoint.startsWith("../../.."))
            badMountPoint = true
        else
            mountPoint = mountPoint.replaceFirst("../../..", "")
        if (mountPoint[0] != '/' || (mountPoint.length > 1 && mountPoint[1] == '.'))
            badMountPoint = true
        if (badMountPoint) {
            logger.warn("Pak \"$fileName\" has strange mount point \"$mountPoint\", mounting to root")
            mountPoint = "/"
        }
        if (mountPoint.startsWith('/'))
            mountPoint = mountPoint.substring(1)
        this.mountPrefix = mountPoint

        val fileCount = indexAr.readInt32()
        this.encryptedFileCount = 0

        val tempMap = mutableMapOf<String, GameFile>()
        for (indexCount in 0 until fileCount) {
            val entry = FPakEntry(indexAr, true)
            val gameFile = GameFile(entry, mountPrefix, fileName)
            if (gameFile.isEncrypted)
                this.encryptedFileCount++
            tempMap[gameFile.path] = gameFile
        }

        val files = mutableListOf<GameFile>()
        tempMap.values.forEach {
            if (it.isUE4Package()) {
                val uexp = tempMap[it.path.substringBeforeLast(".") + ".uexp"]
                if(uexp != null)
                    it.uexp = uexp
                val ubulk = tempMap[it.path.substringBeforeLast(".") + ".ubulk"]
                if(ubulk != null)
                    it.ubulk = ubulk
                files.add(it)
            } else {
                if (!it.path.endsWith(".uexp") && !it.path.endsWith(".ubulk"))
                    files.add(it)
            }
        }
        this.files = files
        return this.files
    }

    inline fun readIndexData(offset: Long, length: Long, hash: ByteArray): FPakArchive {
        if (useDecryptedBuffers) {
            val Ar = Ar.createReader(File(decryptedBuffersDir, hash.printHexBinary() + ".bin").readBytes(), offset)
            Ar.pakInfo = pakInfo
            return Ar
        }

        Ar.seek(offset)
        val data = Ar.read(length.toInt())
        if (isEncrypted()) {
            val key = aesKey
                ?: throw ParserException("Reading an encrypted index requires a valid encryption key")
            Aes.decryptData(data, key)
        }

        val Ar = Ar.createReader(data, offset)
        Ar.pakInfo = pakInfo
        return Ar
    }

    companion object {
        val logger = KotlinLogging.logger("PakFile")
        val decryptedBuffersDir = File("DecryptedBuffers")

        fun isValidIndex(bytes: ByteArray): Boolean {
            val testAr = FByteArchive(bytes)
            val stringLength = testAr.readInt32()
            if (stringLength > 128 || stringLength < -128)
                return false
            // Calculate the pos of the null terminator for this string
            // Then read the null terminator byte and check whether it is actually 0
            return when {
                stringLength == 0 -> testAr.readInt8() == 0.toByte()
                stringLength < 0 -> {
                    // UTF16
                    val nullTerminatorPos = 4 - (stringLength - 1) * 2
                    testAr.seek(nullTerminatorPos)
                    testAr.readInt16() == 0.toShort()
                }
                else -> {
                    // UTF8
                    val nullTerminatorPos = 4 + stringLength - 1
                    testAr.seek(nullTerminatorPos)
                    testAr.readInt8() == 0.toByte()
                }
            }
        }

        fun testAesKey(bytes: ByteArray, key: ByteArray): Boolean {
            Aes.decryptData(bytes, key)
            return isValidIndex(bytes)
        }
    }






    /**
     * This method is following the ue reading of the index.
     * Therefore it might be more stable but it's slower
     */
    private fun readIndexInternal() : List<GameFile> {
        // Prepare primary index and decrypt if necessary
        val primaryIndexAr = readIndexData(pakInfo.indexOffset, pakInfo.indexSize, pakInfo.indexHash)

        // Read the index
        var mountPoint = runCatching { primaryIndexAr.readString() }.getOrElse {
            throw InvalidAesKeyException("Given encryption key '$aesKeyStr' is not working with '$fileName'", it)
        }
        var badMountPoint = false
        if (!mountPoint.startsWith("../../.."))
            badMountPoint = true
        else
            mountPoint = mountPoint.replaceFirst("../../..", "")
        if (mountPoint[0] != '/' || (mountPoint.length > 1 && mountPoint[1] == '.'))
            badMountPoint = true
        if (badMountPoint) {
            logger.warn("Pak \"$fileName\" has strange mount point \"$mountPoint\", mounting to root")
            mountPoint = "/"
        }
        if (mountPoint.startsWith('/'))
            mountPoint = mountPoint.substring(1)
        this.mountPrefix = mountPoint

        val fileCount = primaryIndexAr.readInt32()
        this.encryptedFileCount = 0
        val pathHashSeed = primaryIndexAr.readUInt64()

        var readerHasPathHashIndex = primaryIndexAr.readBoolean()
        var pathHashIndexOffset = INDEX_NONE.toLong()
        var pathHashIndexSize = 0L
        val pathHashIndexHash = ByteArray(20)
        if (readerHasPathHashIndex) {
            pathHashIndexOffset = primaryIndexAr.readInt64()
            pathHashIndexSize = primaryIndexAr.readInt64()
            primaryIndexAr.read(pathHashIndexHash)
            readerHasPathHashIndex = readerHasPathHashIndex && pathHashIndexOffset != INDEX_NONE.toLong()
        }
        var readerHasFullDirectoryIndex = primaryIndexAr.readBoolean()
        var fullDirectoryIndexOffset = INDEX_NONE.toLong()
        var fullDirectoryIndexSize = 0L
        val fullDirectoryIndexHash = ByteArray(20)
        if (readerHasFullDirectoryIndex) {
            fullDirectoryIndexOffset = primaryIndexAr.readInt64()
            fullDirectoryIndexSize = primaryIndexAr.readInt64()
            primaryIndexAr.read(fullDirectoryIndexHash)
            readerHasFullDirectoryIndex = readerHasFullDirectoryIndex && fullDirectoryIndexOffset != INDEX_NONE.toLong()
        }

        val encodedPakEntries = primaryIndexAr.readTArray { Ar.readInt8() }.toByteArray()

        val filesNum = primaryIndexAr.readInt32()
        if (filesNum < 0) {
            // Should not be possible for any values in the PrimaryIndex to be invalid, since we verified the index hash
            throw ParserException("Corrupt Index: Negative FilesNum $filesNum")
        }

        val tempMap = mutableMapOf<String, GameFile>()
        for (indexCount in 0 until filesNum) {
            val entry = FPakEntry(primaryIndexAr, false)
            val gameFile = GameFile(entry, mountPrefix, fileName)
            if (gameFile.isEncrypted)
                this.encryptedFileCount++
            tempMap[gameFile.path] = gameFile
        }

        // Decide which SecondaryIndex(es) to load
        val willUseFullDirectoryIndex : Boolean
        val willUsePathHashIndex : Boolean
        val readFullDirectoryIndex : Boolean
        if (readerHasPathHashIndex && readerHasFullDirectoryIndex) {
            willUseFullDirectoryIndex = /*IsPakKeepFullDirectory() seems to be false */ false
            willUsePathHashIndex = !willUseFullDirectoryIndex
            //false aswell: bool bWantToReadFullDirectoryIndex = IsPakKeepFullDirectory() || IsPakValidatePruning() || IsPakDelayPruning();
            readFullDirectoryIndex = readerHasFullDirectoryIndex // && bWantToReadFullDirectoryIndex
        } else if (readerHasPathHashIndex) {
            willUsePathHashIndex = true
            willUseFullDirectoryIndex = false
            readFullDirectoryIndex = false
        } else if (readerHasFullDirectoryIndex) {
            // We don't support creating the PathHash Index at runtime; we want to move to having only the PathHashIndex, so supporting not having it at all is not useful enough to write
            willUsePathHashIndex = false
            willUseFullDirectoryIndex = true
            readFullDirectoryIndex = true
        } else {
            throw ParserException("readerHasPathHashIndex = false and readerHasFullDirectoryIndex = false")
        }

        // Load the Secondary Index(es)
        var pathHashIndexAr: FPakArchive? = null

        if (willUsePathHashIndex) {
            if (pathHashIndexOffset < 0 || Ar.pakSize() < (pathHashIndexOffset + pathHashIndexSize) ) {
                throw ParserException("PathHashIndex out of range: ${Ar.pakSize()} < $pathHashIndexOffset + $pathHashIndexSize")
            }

            // Prepare path hash index and decrypt if necessary
            pathHashIndexAr = readIndexData(pathHashIndexOffset, pathHashIndexSize, pathHashIndexHash)
            this.pathHashIndex = pathHashIndexAr.readTMap { it.readUInt64() to it.readInt32() }
            //hasPathHashIndex = true
        }

        if (!readFullDirectoryIndex) {
            require(willUsePathHashIndex)
            directoryIndex = pathHashIndexAr!!.readTMap {
                it.readString() to it.readTMap {
                        it2 -> it2.readString() to it2.readInt32()
                }
            }
            //hasFullDirectoryIndex = false
        } else {
            if (Ar.pakSize() < (fullDirectoryIndexOffset + fullDirectoryIndexSize)) {
                throw ParserException("FullDirectoryIndex out of range: ${Ar.pakSize()} < $fullDirectoryIndexOffset + $fullDirectoryIndexSize")
            }

            val secondaryIndexAr = readIndexData(fullDirectoryIndexOffset, fullDirectoryIndexSize, fullDirectoryIndexHash)
            secondaryIndexAr.pakInfo = Ar.pakInfo
            directoryIndex = secondaryIndexAr.readTMap {
                it.readString() to it.readTMap {
                        it2 -> it2.readString() to it2.readInt32()
                }
            }
            //hasFullDirectoryIndex = true
        }

        val encodedPakEntriesAr = FByteArchive(encodedPakEntries)
        for ((dirName, dirContent) in directoryIndex) {
            for ((fileName, offset) in dirContent) {
                val path = dirName + fileName
                encodedPakEntriesAr.seek(offset)
                val entry = readBitEntry(encodedPakEntriesAr)
                entry.name = path
                if(entry.isEncrypted)
                    encryptedFileCount++
                val gameFile = GameFile(entry, mountPrefix, this.fileName)
                tempMap[mountPrefix + path] = gameFile
            }
        }

        val files = mutableListOf<GameFile>()
        tempMap.values.forEach {
            if (it.isUE4Package()) {
                val uexp = tempMap[it.path.substringBeforeLast(".") + ".uexp"]
                if(uexp != null)
                    it.uexp = uexp
                val ubulk = tempMap[it.path.substringBeforeLast(".") + ".ubulk"]
                if(ubulk != null)
                    it.ubulk = ubulk
                files.add(it)
            } else {
                if (!it.path.endsWith(".uexp") && !it.path.endsWith(".ubulk"))
                    files.add(it)
            }
        }
        this.files = files

        if (!keepIndexData) {
            //this.encodedPakEntries = byteArrayOf()
            this.directoryIndex = emptyMap()
            this.pathHashIndex = emptyMap()
        }

        return this.files
    }

    override fun close() = Ar.close()
}