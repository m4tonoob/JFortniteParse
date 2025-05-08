package me.fungames.jfortniteparse.ue4.reader

import me.fungames.jfortniteparse.ue4.versions.VersionContainer
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

open class FByteArchive(val data: ByteBuffer, versions: VersionContainer = VersionContainer.DEFAULT) : FArchive(versions) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FByteArchive::class.java)
    }
    
    init {
        data.order(ByteOrder.LITTLE_ENDIAN)
    }

    constructor(data: ByteArray, versions: VersionContainer = VersionContainer.DEFAULT) : this(ByteBuffer.wrap(data), versions)

    override var littleEndian: Boolean
        get() = data.order() == ByteOrder.LITTLE_ENDIAN
        set(value) { data.order(if (value) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN) }

    protected var pos: Int
        get() = data.position()
        set(value) { data.position(value) }
    protected val size = data.limit()

    override fun clone(): FByteArchive {
        val clone = FByteArchive(data.duplicate())
        clone.pos = pos
        return clone
    }

    override fun seek(pos: Int) {
        //rangeCheck(pos)
        this.pos = pos
    }

    override fun skip(n: Long): Long {
        // Safely handle possible integer overflow
        val nInt = n.toInt()
        if (nInt < 0) {
            // Log a warning but don't crash
            LOGGER.warn("Attempting to skip a negative amount: $nInt, current pos: $pos, size: $size")
            return 0
        }
        
        // Ensure we don't exceed buffer limits
        val newPos = pos + nInt
        if (newPos < 0 || newPos > size) {
            // Log a warning but don't crash
            LOGGER.warn("Skip would result in invalid position: $newPos, current pos: $pos, size: $size")
            return 0
        }
        
        this.pos = newPos
        return n
    }

    override fun size() = size
    override fun pos() = pos

    override fun readBuffer(size: Int): ByteBuffer =
        data.duplicate().apply {
            order(data.order())
            limit(position() + size)
            pos += size
        }

    override fun read() =
        if (!data.hasRemaining()) {
            -1
        } else {
            data.get().toInt() and 0xFF
        }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!data.hasRemaining()) {
            return -1
        }
        val count = min(len, data.remaining())
        data.get(b, off, count)
        return count
    }

    override fun readInt8() = data.get()
    override fun readInt16() = data.short
    override fun readInt32() = data.int
    override fun readInt64() = data.long
    override fun readFloat32() = data.float
    override fun readDouble() = data.double

    override fun printError() = "FByteArchive Info: pos $pos, stopper $size"
}