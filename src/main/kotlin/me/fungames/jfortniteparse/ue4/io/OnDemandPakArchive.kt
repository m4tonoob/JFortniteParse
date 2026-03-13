package me.fungames.jfortniteparse.ue4.io

import me.fungames.jfortniteparse.ue4.pak.reader.FPakArchive
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps a UCAS physical offset to a CDN chunk hash.
 * Each on-demand chunk's compression blocks are contiguous in the virtual UCAS,
 * so we only need the start offset to identify which .iochunk to download.
 */
class CdnChunkMapping(
    val ucasStartOffset: Long,
    val hashHex: String
)

/**
 * A virtual FPakArchive that downloads .iochunk data from Epic's CDN
 * instead of reading from a local .ucas file.
 *
 * Used for on-demand IoStore containers (e.g. pakchunk100iad) where the
 * UCAS doesn't exist locally. The existing FIoStoreReaderImpl.read() method
 * calls seek() + read() on this archive, and we transparently serve data
 * from CDN-downloaded .iochunk files.
 *
 * Each .iochunk on CDN contains the raw compressed block data for one chunk,
 * matching the byte layout that would exist in the UCAS at that chunk's offset range.
 */
class OnDemandPakArchive(
    private val mappings: List<CdnChunkMapping>,
    private val cdnBase: String,
    private val chunksDirectory: String,
    private val cache: ConcurrentHashMap<String, ByteArray>
) : FPakArchive("on-demand") {

    companion object {
        private val logger = LoggerFactory.getLogger(OnDemandPakArchive::class.java)
    }

    override var littleEndian = true
    private var currentPos: Long = 0

    override fun clone(): OnDemandPakArchive {
        val clone = OnDemandPakArchive(mappings, cdnBase, chunksDirectory, cache)
        clone.currentPos = currentPos
        return clone
    }

    override fun seek(pos: Long) {
        currentPos = pos
    }

    override fun read(): Int {
        val buf = ByteArray(1)
        read(buf, 0, 1)
        return buf[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val mapping = findMapping(currentPos)
            ?: throw FIoStatusException(EIoErrorCode.ReadError,
                "No CDN chunk mapping for UCAS offset $currentPos")

        val data = cache.getOrPut(mapping.hashHex) {
            logger.info("Downloading on-demand chunk {} from CDN...", mapping.hashHex)
            downloadChunk(mapping.hashHex)
        }

        val localOffset = (currentPos - mapping.ucasStartOffset).toInt()
        if (localOffset < 0 || localOffset + len > data.size) {
            throw FIoStatusException(EIoErrorCode.ReadError,
                "CDN chunk read out of bounds: offset=$localOffset len=$len chunkSize=${data.size} hash=${mapping.hashHex}")
        }
        System.arraycopy(data, localOffset, b, off, len)
        currentPos += len
        return len
    }

    override fun read(b: ByteArray): Int = read(b, 0, b.size)

    override fun skip(n: Long): Long {
        currentPos += n
        return n
    }

    override fun pakSize(): Long = Long.MAX_VALUE
    override fun pakPos(): Long = currentPos

    override fun close() {}

    /**
     * Binary search for the mapping whose ucasStartOffset is the largest
     * value that doesn't exceed the given offset. This identifies which
     * .iochunk file contains the data at this UCAS position.
     */
    private fun findMapping(offset: Long): CdnChunkMapping? {
        var lo = 0
        var hi = mappings.size - 1
        var result: CdnChunkMapping? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (mappings[mid].ucasStartOffset <= offset) {
                result = mappings[mid]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    /**
     * Downloads a .iochunk file from Epic's CDN.
     * URL pattern: {cdnBase}/{chunksDirectory}/chunks/{hash[0:2]}/{hash}.iochunk
     */
    private fun downloadChunk(hashHex: String): ByteArray {
        val hashPrefix = hashHex.substring(0, 2)
        val url = "$cdnBase/$chunksDirectory/chunks/$hashPrefix/$hashHex.iochunk"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != 200) {
                throw FIoStatusException(EIoErrorCode.ReadError,
                    "CDN download failed: HTTP ${connection.responseCode} for $url")
            }
            return connection.inputStream.readBytes()
        } finally {
            connection.disconnect()
        }
    }
}
