package me.fungames.jfortniteparse.encryption.aes

import me.fungames.jfortniteparse.exceptions.InvalidAesKeyException
import me.fungames.jfortniteparse.util.parseHexBinary
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Aes {
    const val BLOCK_SIZE = 16

    fun parseKey(key: String): ByteArray {
        val data = if (key.startsWith("0x"))
            key.substring(2).parseHexBinary()
        else
            key.parseHexBinary()
        if (data.size != 32)
            throw InvalidAesKeyException("Given AES key is not properly formatted, needs to be exactly 32 bytes long")
        return data
    }

    inline fun encryptData(contents: ByteArray, keyBytes: ByteArray) {
        encryptData(contents, 0, contents.size, keyBytes)
    }

    fun encryptData(contents: ByteArray, offBytes: Int, numBytes: Int, keyBytes: ByteArray) {
        Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            doFinal(contents, offBytes, numBytes, contents, offBytes)
        }
    }

    inline fun decryptData(contents: ByteArray, keyBytes: ByteArray) {
        decryptData(contents, 0, contents.size, keyBytes)
    }

    fun decryptData(contents: ByteArray, offBytes: Int, numBytes: Int, keyBytes: ByteArray) {
        Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            doFinal(contents, offBytes, numBytes, contents, offBytes)
        }
    }

    /**
     * Unreal's AES-CTR stream cipher (UE6.0, Fortnite 42.10+), applied in place. Encrypt and decrypt are the same
     * operation. The counter block is the 12-byte [iv] followed by a big-endian uint32 block index starting at
     * [initialBlockIndex] — which is exactly the JDK's CTR mode over a 16-byte IV, so we let it do the counting.
     * [numBytes] need not be a multiple of 16: the last keystream block is simply truncated.
     */
    fun cryptCtr(contents: ByteArray, offBytes: Int, numBytes: Int, keyBytes: ByteArray, iv: ByteArray, initialBlockIndex: Int = 0) {
        require(iv.size == CTR_IV_SIZE) { "Unreal AES-CTR IVs must be $CTR_IV_SIZE bytes, got ${iv.size}" }
        if (numBytes == 0) return
        val counter = ByteArray(BLOCK_SIZE)
        System.arraycopy(iv, 0, counter, 0, CTR_IV_SIZE)
        counter[12] = (initialBlockIndex ushr 24).toByte()
        counter[13] = (initialBlockIndex ushr 16).toByte()
        counter[14] = (initialBlockIndex ushr 8).toByte()
        counter[15] = initialBlockIndex.toByte()
        Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(counter))
            doFinal(contents, offBytes, numBytes, contents, offBytes)
        }
    }

    const val CTR_IV_SIZE = 12
}