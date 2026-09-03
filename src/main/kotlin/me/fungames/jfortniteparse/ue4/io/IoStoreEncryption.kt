package me.fungames.jfortniteparse.ue4.io

import me.fungames.jfortniteparse.ue4.reader.FArchive

/**
 * EIoEncryptionMethod — how a container's (or pak's) encrypted bytes were produced.
 * UE6.0 / Fortnite 42.10: recorded in the IoStore TOC header (TOC version 10) and the pak trailer (pak version 15).
 * Older files never carry it; they are AES-ECB if their encrypted flag is set.
 */
const val IO_ENCRYPTION_METHOD_NONE = 0
const val IO_ENCRYPTION_METHOD_AES = 1      // AES-256-ECB, one block at a time (the classic Fortnite scheme)
const val IO_ENCRYPTION_METHOD_AES_CTR = 2  // AES-256-CTR: counter block = 12-byte IV || big-endian uint32 block index

/** A 12-byte AES-CTR nonce. The TOC stores one per compression block plus one for the directory index. */
class FIoStoreEncryptionIV {
    companion object {
        const val SIZE = 12
    }

    val bytes: ByteArray

    constructor(Ar: FArchive) {
        bytes = Ar.read(SIZE)
    }

    constructor(bytes: ByteArray) {
        require(bytes.size == SIZE) { "Encryption IV must be exactly $SIZE bytes, got ${bytes.size}" }
        this.bytes = bytes
    }
}
