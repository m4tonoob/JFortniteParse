package me.fungames.jfortniteparse.ue4.registry.objects

import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.core.serialization.FCustomVersion
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.registry.reader.FAssetRegistryArchive
import me.fungames.jfortniteparse.ue4.versions.FPackageFileVersion
import org.slf4j.LoggerFactory

class FAssetPackageData// Skip safely without returning early//chunkHashes = Ar.readTMap { FIoChunkId(Ar) to Ar.read(20) }

// Safety check for extreme values
// Initialize these required properties with default values
    (Ar: FAssetRegistryArchive) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FAssetPackageData::class.java)
    }

    var packageName: FName
    var packageGuid: FGuid? = null
    var cookedHash: FMD5Hash? = null
    var importedClasses: Array<FName>? = null
    var diskSize: Long
    var fileVersionUE: FPackageFileVersion
    var fileVersionLicenseeUE = -1
    var customVersions: Array<FCustomVersion>? = null
    var flags = 0u
    var extensionText: String? = null
    var packageSavedHash: FSHAHash? = null

    init {
        val version = Ar.version
        packageName = Ar.readFName()
        diskSize = Ar.readInt64()
        fileVersionUE = FPackageFileVersion(0, 0)
        if (version < FAssetRegistryVersion.Type.PackageSavedHash) {
            packageGuid = FGuid(Ar)
        } else {
            packageSavedHash = FSHAHash(Ar)
        }
        if (version >= FAssetRegistryVersion.Type.AddedCookedMD5Hash) {
            cookedHash = FMD5Hash(Ar)
        }
        if (version >= FAssetRegistryVersion.Type.AddedChunkHashes) {
            //chunkHashes = Ar.readTMap { FIoChunkId(Ar) to Ar.read(20) }
            val numChunks = Ar.readInt32()

            // Safety check for extreme values
            if (numChunks < 0 || numChunks > 100000) {
                LOGGER.warn("Possibly corrupted chunk count detected: $numChunks, skipping chunk data")
                // Skip safely without returning early
            } else {
                val bytesToSkip = (numChunks * (12 + 20)).toLong()
                Ar.skip(bytesToSkip)
            }
        }
        if (version >= FAssetRegistryVersion.Type.WorkspaceDomain) {
            fileVersionUE = if (version >= FAssetRegistryVersion.Type.PackageFileSummaryVersionChange) {
                FPackageFileVersion(Ar)
            } else {
                val ue4Version = Ar.readInt32()
                FPackageFileVersion.createUE4Version(ue4Version)
            }
            fileVersionLicenseeUE = Ar.readInt32()
            flags = Ar.readUInt32()
            customVersions = Ar.readTArray { FCustomVersion(Ar) }
        }
        if (version >= FAssetRegistryVersion.Type.PackageImportedClasses) {
            importedClasses = Ar.readTArray { Ar.readFName() }
        }
        if (Ar.header.version >= FAssetRegistryVersion.Type.AssetPackageDataHasExtension) {
            extensionText = Ar.readString()
        }
    }
}