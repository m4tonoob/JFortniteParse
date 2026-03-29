package me.fungames.jfortniteparse.ue4.assets.objects

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.compression.Compression
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.assets.enums.EBulkDataFlags.*
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.assets.util.PayloadType
import me.fungames.jfortniteparse.ue4.assets.writer.FAssetArchiveWriter

open class FByteBulkData {
    var header: FByteBulkDataHeader
    var data: ByteArray
    val isBulkDataLoaded get() = data.isNotEmpty()

    constructor(Ar: FAssetArchive) {
        header = FByteBulkDataHeader(Ar)
        val bulkDataFlags = header.bulkDataFlags
        // sizeOnDisk is the compressed size (or uncompressed size when not compressed).
        // elementCount is always the uncompressed size.
        val readBuffer = ByteArray(header.sizeOnDisk.toInt())
        data = ByteArray(header.elementCount.toInt())
        when {
            header.elementCount == 0L -> return // Nothing to do here
            BULKDATA_Unused.check(bulkDataFlags) -> {
                LOG_JFP.warn("Bulk with no data")
                return
            }
            BULKDATA_ForceInlinePayload.check(bulkDataFlags) -> {
                LOG_JFP.debug("bulk data in .uexp file (Force Inline Payload) (flags=$bulkDataFlags, pos=${header.offsetInFile}, size=${header.sizeOnDisk})")
                Ar.read(readBuffer)
            }
            BULKDATA_PayloadInSeperateFile.check(bulkDataFlags) -> {
                LOG_JFP.debug("bulk data in .ubulk file (Payload In Seperate File) (flags=$bulkDataFlags, pos=${header.offsetInFile}, size=${header.sizeOnDisk})")
                val ubulkAr = Ar.getPayload(when {
                    BULKDATA_OptionalPayload.check(bulkDataFlags) -> PayloadType.UPTNL
                    BULKDATA_MemoryMappedPayload.check(bulkDataFlags) -> PayloadType.M_UBULK
                    else -> PayloadType.UBULK
                })
                if (ubulkAr.size() > 0) {
                    ubulkAr.seek(header.offsetInFile.toInt())
                    ubulkAr.read(readBuffer)
                } else { // bulk data file not found
                    data = ByteArray(0)
                    return
                }
            }
            BULKDATA_PayloadAtEndOfFile.check(bulkDataFlags) -> {
                //stored in same file, but at different position
                //save archive position
                val savePos = Ar.pos()
                if (header.offsetInFile.toInt() + header.sizeOnDisk <= Ar.size()) {
                    Ar.seek(header.offsetInFile.toInt())
                    Ar.read(readBuffer)
                } else {
                    throw ParserException("Failed to read PayloadAtEndOfFile, ${header.offsetInFile} is out of range", Ar)
                }
                Ar.seek(savePos)
            }
        }
        if (BULKDATA_SerializeCompressedZLIB.check(bulkDataFlags)) {
            Compression.uncompressMemory("Zlib", data, readBuffer)
        } else {
            data = readBuffer
        }
    }

    fun serialize(Ar: FAssetArchiveWriter) {
        val bulkDataFlags = header.bulkDataFlags
        when {
            BULKDATA_Unused.check(bulkDataFlags) -> {
                header.serialize(Ar)
            }
            BULKDATA_ForceInlinePayload.check(bulkDataFlags) -> {
                header.offsetInFile = (Ar.relativePos() + 28).toLong()
                header.elementCount = data.size.toLong()
                header.sizeOnDisk = data.size.toLong()
                header.serialize(Ar)
                Ar.write(data)
            }
            BULKDATA_PayloadInSeperateFile.check(bulkDataFlags) -> {
                val ubulkAr = Ar.getPayload(PayloadType.UBULK)
                header.offsetInFile = ubulkAr.relativePos().toLong()
                header.elementCount = data.size.toLong()
                header.sizeOnDisk = data.size.toLong()
                header.serialize(Ar)
                ubulkAr.write(data)
            }
            BULKDATA_OptionalPayload.check(bulkDataFlags) -> {
                val ubulkAr = Ar.getPayload(PayloadType.UPTNL)
                header.offsetInFile = ubulkAr.relativePos().toLong()
                header.elementCount = data.size.toLong()
                header.sizeOnDisk = data.size.toLong()
                header.serialize(Ar)
                ubulkAr.write(data)
            }
            else -> throw ParserException("Unsupported BulkData type $bulkDataFlags")
        }
    }

    constructor(header: FByteBulkDataHeader, data: ByteArray) {
        this.header = header
        this.data = data
    }
}