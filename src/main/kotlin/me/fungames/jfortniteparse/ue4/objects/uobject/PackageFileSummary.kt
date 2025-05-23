package me.fungames.jfortniteparse.ue4.objects.uobject

import me.fungames.jfortniteparse.ue4.assets.objects.FCompressedChunk
import me.fungames.jfortniteparse.ue4.objects.core.misc.FEngineVersion
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.core.serialization.FCustomVersion
import me.fungames.jfortniteparse.ue4.reader.FArchive
import me.fungames.jfortniteparse.ue4.registry.objects.FSHAHash
import me.fungames.jfortniteparse.ue4.versions.*
import me.fungames.jfortniteparse.ue4.versions.VER_UE4_ADDED_PACKAGE_OWNER
import me.fungames.jfortniteparse.ue4.versions.VER_UE4_ADDED_PACKAGE_SUMMARY_LOCALIZATION_ID
import me.fungames.jfortniteparse.ue4.versions.VER_UE4_NON_OUTER_PACKAGE_IMPORT
import me.fungames.jfortniteparse.ue4.writer.FArchiveWriter

/**
 * Revision data for an Unreal package file.
 */
class FGenerationInfo {
    /**
     * Number of exports in the linker's ExportMap for this generation.
     */
    var exportCount: Int

    /**
     * Number of names in the linker's NameMap for this generation.
     */
    var nameCount: Int

    constructor(Ar: FArchive) {
        exportCount = Ar.readInt32()
        nameCount = Ar.readInt32()
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeInt32(exportCount)
        Ar.writeInt32(nameCount)
    }

    constructor(exportCount: Int, nameCount: Int) {
        this.exportCount = exportCount
        this.nameCount = nameCount
    }
}

/**
 * A "table of contents" for an Unreal package file.  Stored at the top of the file.
 */
class FPackageFileSummary {
    val PACKAGE_FILE_TAG: UInt = 0x9E2A83C1U
    val PACKAGE_FILE_TAG_SWAPPED: UInt = 0xC1832A9EU
    val PACKAGE_FILE_TAG_ACE7: UInt = 0x37454341U
    val PACKAGE_FILE_TAG_ONE: UInt = 0x00656E6FU // SOD2
    val PACKAGE_FILE_TAG_NTE: UInt = 0xD5A8D56EU

    var tag: UInt
    var legacyFileVersion: Int
    var legacyUE3Version: Int
    var fileVersionUE4: Int
    var fileVersionLicenseUE4: Int
    var customVersionContainer: Array<FCustomVersion>
    var totalHeaderSize: Int = 0
    var packageName: String
    var packageFlags: UInt
    var nameCount: Int
    var nameOffset: Int
    var softObjectPathsCount: Int = 0
    var softObjectPathsOffset: Int = 0
    var localizationId: String? = null
    var gatherableTextDataCount: Int
    var gatherableTextDataOffset: Int
    var metaDataOffset: Int = 0
    var exportCount: Int
    var exportOffset: Int
    var importCount: Int
    var importOffset: Int
    var cellExportCount: Int = 0
    var cellExportOffset: Int = 0
    var cellImportCount: Int = 0
    var cellImportOffset: Int = 0
    var dependsOffset: Int
    var softPackageReferencesCount: Int
    var softPackageReferencesOffset: Int
    var searchableNamesOffset: Int
    var thumbnailTableOffset: Int
    var savedHash: FSHAHash? = null
    var guid: FGuid? = null
    var persistentGuid: FGuid? = null
    var generations: Array<FGenerationInfo>
    var savedByEngineVersion: FEngineVersion
    var compatibleWithEngineVersion: FEngineVersion
    var compressionFlags: UInt
    var compressedChunks: Array<FCompressedChunk>
    var packageSource: UInt
    var unversioned: Boolean = false
    var additionalPackagesToCook: Array<String>
    var assetRegistryDataOffset: Int
    var bulkDataStartOffset: Int
    var worldTileInfoDataOffset: Int
    var chunkIds: Array<Int>
    var preloadDependencyCount: Int
    var preloadDependencyOffset: Int
    var namesReferencedFromExportDataCount: Int = 0
    var payloadTocOffet: Long = 0
    var dataResourceOffset: Int = 0

    constructor(Ar: FArchive) {
        tag = Ar.readUInt32()
        legacyFileVersion = Ar.readInt32()
        legacyUE3Version = Ar.readInt32()
        fileVersionUE4 = Ar.readInt32()
        fileVersionLicenseUE4 = Ar.readInt32()
        if (legacyFileVersion <= -8) {
            // TODO: test this shit? maybe just skip past it? idk, but has been commited to CUE4 for 4 years, and was fine, so ig skip
            fileVersionUE4 = Ar.readInt32()
        }
        if (fileVersionUE4 >= EUnrealEngineObjectUE5Version.PACKAGE_SAVED_HASH) {
            savedHash = FSHAHash(Ar)
            totalHeaderSize = Ar.readInt32()
        }
        customVersionContainer = Ar.readTArray { FCustomVersion(Ar) }
        if (fileVersionUE4 < EUnrealEngineObjectUE5Version.PACKAGE_SAVED_HASH) {
            totalHeaderSize = Ar.readInt32()
        }
        packageName = Ar.readString()
        packageFlags = Ar.readUInt32()
        if (packageFlags.toInt() and EPackageFlags.PKG_FilterEditorOnly.value != 0) {
            Ar.isFilterEditorOnly = true
        }
        nameCount = Ar.readInt32()
        nameOffset = Ar.readInt32()
        if (!Ar.isFilterEditorOnly) {
            if (fileVersionUE4 >= VER_UE4_ADDED_PACKAGE_SUMMARY_LOCALIZATION_ID) {
                localizationId = Ar.readString()
            }
        }
        gatherableTextDataCount = Ar.readInt32()
        gatherableTextDataOffset = Ar.readInt32()
        exportCount = Ar.readInt32()
        exportOffset = Ar.readInt32()
        importCount = Ar.readInt32()
        importOffset = Ar.readInt32()

        if (fileVersionUE4 >= EUnrealEngineObjectUE5Version.VERSE_CELLS) {
            cellExportCount = Ar.readInt32()
            cellExportOffset = Ar.readInt32()
            cellImportCount = Ar.readInt32()
            cellImportOffset = Ar.readInt32()
        }

        if (fileVersionUE4 >= EUnrealEngineObjectUE5Version.METADATA_SERIALIZATION_OFFSET) {
            metaDataOffset = Ar.readInt32()
        }

        dependsOffset = Ar.readInt32()
        softPackageReferencesCount = Ar.readInt32()
        softPackageReferencesOffset = Ar.readInt32()
        searchableNamesOffset = Ar.readInt32()
        thumbnailTableOffset = Ar.readInt32()
        if (fileVersionUE4 < EUnrealEngineObjectUE5Version.PACKAGE_SAVED_HASH) {
            guid = FGuid(Ar)
        }
        if (Ar.game == GAME_VALORANT) {
            Ar.skip(8)
        }
        if (!Ar.isFilterEditorOnly) {
            if (fileVersionUE4 >= VER_UE4_ADDED_PACKAGE_OWNER) {
                val persistentGuid = FGuid(Ar)
            }
            if (fileVersionUE4 in VER_UE4_ADDED_PACKAGE_OWNER until VER_UE4_NON_OUTER_PACKAGE_IMPORT) {
                val ownerPersistentGuid = FGuid(Ar)
            }
        }
        generations = Ar.readTArray { FGenerationInfo(Ar) }
        savedByEngineVersion = FEngineVersion(Ar)
        compatibleWithEngineVersion = FEngineVersion(Ar)
        compressionFlags = Ar.readUInt32()
        compressedChunks = Ar.readTArray { FCompressedChunk(Ar) }
        packageSource = Ar.readUInt32()
        additionalPackagesToCook = Ar.readTArray { Ar.readString() }
        assetRegistryDataOffset = Ar.readInt32()
        bulkDataStartOffset = Ar.readInt32()
        worldTileInfoDataOffset = Ar.readInt32()
        chunkIds = Ar.readTArray { Ar.readInt32() }
        preloadDependencyCount = Ar.readInt32()
        preloadDependencyOffset = Ar.readInt32()
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeUInt32(tag)
        Ar.writeInt32(legacyFileVersion)
        Ar.writeInt32(legacyUE3Version)
        Ar.writeInt32(fileVersionUE4)
        Ar.writeInt32(fileVersionLicenseUE4)
        Ar.writeTArray(customVersionContainer) { it.serialize(Ar) }
        Ar.writeInt32(totalHeaderSize)
        Ar.writeString(packageName)
        Ar.writeUInt32(packageFlags)
        Ar.writeInt32(nameCount)
        Ar.writeInt32(nameOffset)
        Ar.writeInt32(gatherableTextDataCount)
        Ar.writeInt32(gatherableTextDataOffset)
        Ar.writeInt32(exportCount)
        Ar.writeInt32(exportOffset)
        Ar.writeInt32(importCount)
        Ar.writeInt32(importOffset)
        Ar.writeInt32(dependsOffset)
        Ar.writeInt32(softPackageReferencesCount)
        Ar.writeInt32(softPackageReferencesOffset)
        Ar.writeInt32(searchableNamesOffset)
        Ar.writeInt32(thumbnailTableOffset)
        guid?.serialize(Ar)
        Ar.writeTArray(generations) { it.serialize(Ar) }
        savedByEngineVersion.serialize(Ar)
        compatibleWithEngineVersion.serialize(Ar)
        Ar.writeUInt32(compressionFlags)
        Ar.writeTArray(compressedChunks) { it.serialize(Ar) }
        Ar.writeUInt32(packageSource)
        Ar.writeTArray(additionalPackagesToCook) { Ar.writeString(it) }
        Ar.writeInt32(assetRegistryDataOffset)
        Ar.writeInt32(bulkDataStartOffset)
        Ar.writeInt32(worldTileInfoDataOffset)
        Ar.writeTArray(chunkIds) { Ar.writeInt32(it) }
        Ar.writeInt32(preloadDependencyCount)
        Ar.writeInt32(preloadDependencyOffset)
    }

    constructor(
        tag: UInt,
        legacyFileVersion: Int,
        legacyUE3Version: Int,
        fileVersionUE4: Int,
        fileVersionLicenseUE4: Int,
        customVersionContainer: Array<FCustomVersion>,
        totalHeaderSize: Int,
        folderName: String,
        packageFlags: UInt,
        nameCount: Int,
        nameOffset: Int,
        gatherableTextDataCount: Int,
        gatherableTextDataOffset: Int,
        exportCount: Int,
        exportOffset: Int,
        importCount: Int,
        importOffset: Int,
        dependsOffset: Int,
        softPackageReferencesCount: Int,
        softPackageReferencesOffset: Int,
        searchableNamesOffset: Int,
        thumbnailTableOffset: Int,
        guid: FGuid,
        generations: Array<FGenerationInfo>,
        savedByEngineVersion: FEngineVersion,
        compatibleWithEngineVersion: FEngineVersion,
        compressionFlags: UInt,
        compressedChunks: Array<FCompressedChunk>,
        packageSource: UInt,
        additionalPackagesToCook: Array<String>,
        assetRegistryDataOffset: Int,
        bulkDataStartOffset: Int,
        worldTileInfoDataOffset: Int,
        chunkIds: Array<Int>,
        preloadDependencyCount: Int,
        preloadDependencyOffset: Int
    ) {
        this.tag = tag
        this.legacyFileVersion = legacyFileVersion
        this.legacyUE3Version = legacyUE3Version
        this.fileVersionUE4 = fileVersionUE4
        this.fileVersionLicenseUE4 = fileVersionLicenseUE4
        this.customVersionContainer = customVersionContainer
        this.totalHeaderSize = totalHeaderSize
        this.packageName = folderName
        this.packageFlags = packageFlags
        this.nameCount = nameCount
        this.nameOffset = nameOffset
        this.gatherableTextDataCount = gatherableTextDataCount
        this.gatherableTextDataOffset = gatherableTextDataOffset
        this.exportCount = exportCount
        this.exportOffset = exportOffset
        this.importCount = importCount
        this.importOffset = importOffset
        this.dependsOffset = dependsOffset
        this.softPackageReferencesCount = softPackageReferencesCount
        this.softPackageReferencesOffset = softPackageReferencesOffset
        this.searchableNamesOffset = searchableNamesOffset
        this.thumbnailTableOffset = thumbnailTableOffset
        this.guid = guid
        this.generations = generations
        this.savedByEngineVersion = savedByEngineVersion
        this.compatibleWithEngineVersion = compatibleWithEngineVersion
        this.compressionFlags = compressionFlags
        this.compressedChunks = compressedChunks
        this.packageSource = packageSource
        this.additionalPackagesToCook = additionalPackagesToCook
        this.assetRegistryDataOffset = assetRegistryDataOffset
        this.bulkDataStartOffset = bulkDataStartOffset
        this.worldTileInfoDataOffset = worldTileInfoDataOffset
        this.chunkIds = chunkIds
        this.preloadDependencyCount = preloadDependencyCount
        this.preloadDependencyOffset = preloadDependencyOffset
    }
}