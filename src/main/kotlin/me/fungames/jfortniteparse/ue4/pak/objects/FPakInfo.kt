package me.fungames.jfortniteparse.ue4.pak.objects

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.io.FIoStoreEncryptionIV
import me.fungames.jfortniteparse.ue4.io.IO_ENCRYPTION_METHOD_AES
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.pak.enums.*
import me.fungames.jfortniteparse.ue4.pak.reader.FPakArchive
import me.fungames.jfortniteparse.ue4.reader.FArchive
import me.fungames.jfortniteparse.ue4.reader.FByteArchive

class FPakInfo {
    companion object {
        const val PAK_MAGIC = 0x5A6F12E1u

        const val size = 4 * 2 + 8 * 2 + 20 + 1 + 16
        const val size8 = size + 4 * 32
        const val size8a = size8 + 32
        const val size9 = size8a + 1
        // UE6.0 (Fortnite v42.00) appends PakchunkIndex as an int32. Measured at size8a + 4 on shipped paks:
        // the version 9 frozen-index byte is absent, so this is not CUE4Parse's Size9a (size9 + 4).
        const val size9a = size8a + 4
        // UE6.0 (Fortnite v42.10, pak version 15) appends EncryptionMethod (uint8) + three 12-byte IVs
        // (primary index, path-hash index, full directory index). Measured at 262 bytes on shipped paks;
        // matches CUE4Parse's Size10 = Size9a + 1 + 3 * 12.
        const val size10 = size9a + 1 + 3 * 12

        val offsetsToTry =              arrayOf(size, size8, size8a, size9, size9a, size10)
        val maxNumCompressionMethods =  arrayOf(0   , 4    , 5     , 5    , 5     , 5     )

        fun readPakInfo(Ar: FPakArchive): FPakInfo {
            val pakSize = Ar.pakSize()
            var maxSize = -1
            var maxOffsetToTryIndex = -1
            for (i in offsetsToTry.size - 1 downTo 0) {
                if (pakSize - offsetsToTry[i] >= 0) {
                    maxSize = offsetsToTry[i]
                    maxOffsetToTryIndex = i + 1
                    break
                }
            }
            if (maxSize < 0)
                throw ParserException("File '${Ar.fileName} has an unknown format")
            Ar.seek(pakSize - maxSize)
            val tempAr = FByteArchive(Ar.read(maxSize))
            for (i in 0 until maxOffsetToTryIndex) {
                tempAr.seek(maxSize - offsetsToTry[i])
                try {
                    return FPakInfo(tempAr, maxNumCompressionMethods[i])
                } catch (t: Throwable) {}
            }
            throw ParserException("File '${Ar.fileName} has an unknown format")
        }
    }

    var encryptionKeyGuid: FGuid
    var encryptedIndex: Boolean
    var version: Int
    var indexOffset: Long
    var indexSize: Long
    var indexHash: ByteArray
    var compressionMethods: MutableList<String>
    var indexIsFrozen: Boolean = false
    /** Pak version 14+: chunk index from the trailer. -1 (INDEX_NONE) when the pak predates it. */
    var pakchunkIndex: Int = -1
    /** Pak version 15+: how the index and encrypted entries were encrypted. Older paks are AES-ECB by definition. */
    var encryptionMethod: Int = IO_ENCRYPTION_METHOD_AES
    var indexIv: FIoStoreEncryptionIV? = null
    var pathHashIndexIv: FIoStoreEncryptionIV? = null
    var fullDirectoryIndexIv: FIoStoreEncryptionIV? = null

    constructor(Ar: FArchive, maxNumCompressionMethods: Int = 4) {
        // New FPakInfo fields
        encryptionKeyGuid = FGuid(Ar) // PakFile_Version_EncryptionKeyGuid
        encryptedIndex = Ar.readUInt8().toInt() != 0 // Do not replace by ReadFlag

        // Old FPakInfoFields
        val magic = Ar.readUInt32()
        if (magic != PAK_MAGIC)
            throw ParserException("Invalid pak file magic", Ar)
        version = Ar.readInt32()
        indexOffset = Ar.readInt64()
        indexSize = Ar.readInt64()
        indexHash = Ar.read(20)
        if (this.version in PakVersion_FrozenIndex until PakVersion_PathHashIndex) {
            indexIsFrozen = Ar.readBoolean()
            if (indexIsFrozen) {
                LOG_JFP.warn { "Frozen PakFile Index" }
            }
        }
        compressionMethods = mutableListOf()
        if (this.version >= PakVersion_FNameBasedCompressionMethod) {
            compressionMethods.add("None")
            for (i in 0 until maxNumCompressionMethods) {
                val d = Ar.read(32)
                val str = d.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
                if (str.isBlank())
                    break
                compressionMethods.add(str)
            }
        }
        if (this.version >= PakVersion_PakchunkIndex) {
            pakchunkIndex = Ar.readInt32()
        }
        if (this.version >= PakVersion_EncryptionMethod) {
            encryptionMethod = Ar.readUInt8().toInt()
            indexIv = FIoStoreEncryptionIV(Ar)
            pathHashIndexIv = FIoStoreEncryptionIV(Ar)
            fullDirectoryIndexIv = FIoStoreEncryptionIV(Ar)
        }

        // Reset new fields to their default states when serializing older pak format.
        if (version < PakVersion_IndexEncryption) {
            encryptedIndex = false
        }
        if (version < PakVersion_EncryptionKeyGuid) {
            encryptionKeyGuid = FGuid.mainGuid
        }
    }
}