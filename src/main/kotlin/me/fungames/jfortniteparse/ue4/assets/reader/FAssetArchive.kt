package me.fungames.jfortniteparse.ue4.assets.reader

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.fileprovider.FileProvider
import me.fungames.jfortniteparse.ue4.assets.Package
import me.fungames.jfortniteparse.ue4.assets.PakPackage
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.util.PayloadType
import me.fungames.jfortniteparse.ue4.objects.uobject.EPackageFlags
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex
import me.fungames.jfortniteparse.ue4.reader.FByteArchive
import java.nio.ByteBuffer

/**
 * Binary reader for UE4 Assets
 */
open class FAssetArchive(data: ByteBuffer, val provider: FileProvider?, val pkgName: String) : FByteArchive(data) {
    constructor(data: ByteArray, provider: FileProvider?, pkgName: String) : this(ByteBuffer.wrap(data), provider, pkgName)

    // Asset Specific Fields
    lateinit var owner: Package
    protected var payloads = mutableMapOf<PayloadType, FAssetArchive>()
    var uassetSize = 0
    var uexpSize = 0
    var bulkDataStartOffset = 0
//    var hasUnversionedProperties = (owner.flags and EPackageFlags.PKG_UnversionedProperties.value) != 0

    open fun getPayload(type: PayloadType) = payloads[type] ?: FByteArchive(ByteArray(0))
    fun addPayload(type: PayloadType, payload: FAssetArchive) {
        if (payloads.containsKey(type))
            throw ParserException("Can't add a payload that is already attached of type ${type.name}")
        payloads[type] = payload
    }

    public override fun clone(): FAssetArchive {
        val c = FAssetArchive(data.duplicate(), provider, pkgName)
        c.versions = versions
        c.useUnversionedPropertySerialization = useUnversionedPropertySerialization
        c.isFilterEditorOnly = isFilterEditorOnly
        c.littleEndian = littleEndian
        c.pos = pos
        c.owner = owner
        payloads.forEach { c.payloads[it.key] = it.value }
        c.uassetSize = uassetSize
        c.uexpSize = uexpSize
        c.bulkDataStartOffset = bulkDataStartOffset
        return c
    }

    fun seekRelative(pos: Int) {
        seek(pos - uassetSize - uexpSize)
    }

    fun relativePos() = uassetSize + uexpSize + pos()
    fun toNormalPos(relativePos: Int) = relativePos - uassetSize - uexpSize
    fun toRelativePos(normalPos: Int) = normalPos + uassetSize + uexpSize

    open fun handleBadNameIndex(nameIndex: Int) {
        throw ParserException("FName could not be read, requested index $nameIndex, name map size ${(owner as PakPackage).nameMap.size}", this)
    }

    override fun readFName(): FName {
        val owner = owner as PakPackage
        val nameIndex = this.readInt32()
        val extraIndex = this.readInt32()
        if (nameIndex in owner.nameMap.indices) {
            return FName(owner.nameMap, nameIndex, extraIndex)
        }
        handleBadNameIndex(nameIndex)
        return FName()
    }

    open fun <T : UObject> readObject() = FPackageIndex(this).let {
        val out = owner.findObject<T>(it)
        if (!it.isNull() && out == null) {
            LOG_JFP.warn("$pkgName: $it not found")
        }
        out
    }

    override fun printError() = "FAssetArchive Info: pos $pos, stopper $size, package $pkgName"
}