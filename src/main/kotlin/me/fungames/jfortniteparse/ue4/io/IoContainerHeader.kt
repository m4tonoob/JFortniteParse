package me.fungames.jfortniteparse.ue4.io

import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageId
import me.fungames.jfortniteparse.ue4.objects.uobject.serialization.FMappedName
import me.fungames.jfortniteparse.ue4.objects.uobject.serialization.FNameMap
import me.fungames.jfortniteparse.ue4.reader.FArchive
import me.fungames.jfortniteparse.ue4.versions.GAME_UE5_BASE

typealias FSourceToLocalizedPackageIdMap = Array<FIoContainerHeaderPackageRedirect>
typealias FCulturePackageMap = Map<String, FSourceToLocalizedPackageIdMap>

/** File based package store entry */
class FFilePackageStoreEntry {
    var exportCount = 0
    var exportBundleCount = 0
    var importedPackages: Array<FPackageId>
    //var shaderMapHashes: Array<ByteArray>

    constructor(Ar: FArchive, version: Int) {
        if (version >= EIoContainerHeaderVersion.Initial) {
            if (version < EIoContainerHeaderVersion.NoExportInfo) {
                exportCount = Ar.readInt32()
                exportBundleCount = Ar.readInt32()
            }
            importedPackages = Ar.readCArrayView { FPackageId(Ar) }
            Ar.skip(8) //shaderMapHashes = Ar.readCArrayView { Ar.read(20) }
        } else {
            Ar.skip(8) // exportBundlesSize
            exportCount = Ar.readInt32()
            exportBundleCount = Ar.readInt32()
            Ar.skip(8) // loadOrder + pad
            importedPackages = Ar.readCArrayView { FPackageId(Ar) }
        }
    }

    private inline fun <reified T> FArchive.readCArrayView(init: (FArchive) -> T): Array<T> {
        val initialPos = pos()
        val arrayNum = readInt32()
        val offsetToDataFromThis = readInt32()
        if (arrayNum <= 0) {
            return emptyArray()
        }
        val continuePos = pos()
        seek(initialPos + offsetToDataFromThis)
        val result = Array(arrayNum) { init(this) }
        seek(continuePos)
        return result
    }
}

class FIoContainerHeaderPackageRedirect {
    var sourcePackageId: FPackageId
    var targetPackageId: FPackageId
    var sourcePackageName: FMappedName?

    constructor(Ar: FArchive, version: Int) {
        sourcePackageId = FPackageId(Ar)
        targetPackageId = FPackageId(Ar)
        sourcePackageName = if (version >= EIoContainerHeaderVersion.Initial) FMappedName(Ar) else null
    }
}

class FIoContainerHeaderSoftPackageReferences {
    var packageIds: Array<FPackageId>
    var packageIndecies: Array<Byte>
    var bContainsSoftPackageReferences: Boolean

    constructor(Ar: FArchive) {
        bContainsSoftPackageReferences = Ar.readBoolean()
        if (bContainsSoftPackageReferences) {
            packageIds = Ar.readTArray { FPackageId(Ar) }
            packageIndecies = Ar.readTArray { Ar.readInt8() }
        } else {
            packageIds = emptyArray()
            packageIndecies = emptyArray()
        }
    }
}

class FIoContainerHeaderSerialInfo {
    var offset: Long
    var size: Long

    constructor(Ar: FArchive) {
        offset = Ar.readInt64()
        size = Ar.readInt64()
    }
}

class FIoContainerHeaderLocalizedPackage {
    var sourcePackageId: FPackageId
    var sourcePackageName: FMappedName?

    constructor(Ar: FArchive) {
        sourcePackageId = FPackageId(Ar)
        sourcePackageName = FMappedName(Ar)
    }
}

object EIoContainerHeaderVersion {
    const val BeforeVersionWasAdded = -1 // Custom constant to indicate pre-UE5 data
    const val Initial = 0
    const val LocalizedPackages = 1
    const val OptionalSegmentPackages = 2
    const val NoExportInfo = 3
    const val SoftPackageReferences = 4
    const val SoftPackageReferencesOffset = 5

    const val Latest = LocalizedPackages
}

class FIoContainerHeader {
    companion object {
        const val SIGNATURE = 0x496f436e
    }

    var containerId: FIoContainerId
    var packageIds: Array<FPackageId>
    var storeEntries: Array<FFilePackageStoreEntry>
    var optionalSegmentPackageIds: Array<FPackageId>? = null
    var optionalSegmentStoreEntries: Array<FFilePackageStoreEntry>? = null
    val redirectsNameMap = FNameMap()
    var localizedPackages: Array<FIoContainerHeaderLocalizedPackage>? = null
    var culturePackageMap: FCulturePackageMap? = null
    var packageRedirects: Array<FIoContainerHeaderPackageRedirect>
    var softPackageReferences: FIoContainerHeaderSoftPackageReferences? = null
    var softPackageReferencesSerialInfo: FIoContainerHeaderSerialInfo? = null

    constructor(Ar: FArchive) {
        var version = if (Ar.game >= GAME_UE5_BASE) EIoContainerHeaderVersion.Initial else EIoContainerHeaderVersion.BeforeVersionWasAdded
        if (version == EIoContainerHeaderVersion.Initial) {
            val signature = Ar.readInt32()
            if (signature != SIGNATURE) {
                throw IllegalStateException("Invalid container header signature: 0x%08X != 0x%08X".format(signature, SIGNATURE))
            }
            version = Ar.readInt32()
        }
        containerId = FIoContainerId(Ar)
        if (version < EIoContainerHeaderVersion.OptionalSegmentPackages) {
            val packageCount = Ar.readUInt32()
        }
        if (version == EIoContainerHeaderVersion.BeforeVersionWasAdded) {
            val names = Ar.read(Ar.readInt32())
            val nameHashes = Ar.read(Ar.readInt32())
            if (names.isNotEmpty()) {
                redirectsNameMap.load(names, nameHashes, FMappedName.EType.Container)
            }
        }
        packageIds = Ar.readTArray { FPackageId(Ar) }
        val storeEntriesNum = Ar.readInt32()
        val storeEntriesEnd = Ar.pos() + storeEntriesNum
        storeEntries = Array(packageIds.size) { FFilePackageStoreEntry(Ar, version) }
        Ar.seek(storeEntriesEnd)
        if (version >= EIoContainerHeaderVersion.OptionalSegmentPackages) {
            optionalSegmentPackageIds = Ar.readTArray { FPackageId(Ar) }
            val optionalSegmentStoreEntriesNum = Ar.readInt32()
            val optionalSegmentStoreEntriesEnd = Ar.pos() + optionalSegmentStoreEntriesNum
            optionalSegmentStoreEntries = Array(optionalSegmentPackageIds!!.size) { FFilePackageStoreEntry(Ar, version) }
            Ar.seek(optionalSegmentStoreEntriesEnd)
        }
        if (version >= EIoContainerHeaderVersion.Initial) {
            redirectsNameMap.load(Ar, FMappedName.EType.Container)
        }
        if (version >= EIoContainerHeaderVersion.LocalizedPackages) {
            localizedPackages = Ar.readTArray { FIoContainerHeaderLocalizedPackage(Ar) }
        } else {
            culturePackageMap = Ar.readTMap { Ar.readString() to Ar.readTArray { FIoContainerHeaderPackageRedirect(Ar, version) } }
        }
        packageRedirects = Ar.readTArray { FIoContainerHeaderPackageRedirect(Ar, version) }
        if (version == EIoContainerHeaderVersion.SoftPackageReferences) {
            softPackageReferences = FIoContainerHeaderSoftPackageReferences(Ar)
        } else if (version >= EIoContainerHeaderVersion.SoftPackageReferencesOffset) {
            softPackageReferencesSerialInfo = FIoContainerHeaderSerialInfo(Ar)
        }
    }
}