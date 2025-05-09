package me.fungames.jfortniteparse.ue4.assets

import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.Gson
import me.fungames.jfortniteparse.GFatalObjectSerializationErrors
import me.fungames.jfortniteparse.LOG_STREAMING
import me.fungames.jfortniteparse.fileprovider.FileProvider
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.exports.UScriptStruct
import me.fungames.jfortniteparse.ue4.assets.exports.UStruct
import me.fungames.jfortniteparse.ue4.assets.reader.FExportArchive
import me.fungames.jfortniteparse.ue4.io.*
import me.fungames.jfortniteparse.ue4.locres.Locres
import me.fungames.jfortniteparse.ue4.objects.uobject.EPackageFlags
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageId
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex
import me.fungames.jfortniteparse.ue4.objects.uobject.serialization.FMappedName
import me.fungames.jfortniteparse.ue4.objects.uobject.serialization.FNameMap
import me.fungames.jfortniteparse.ue4.reader.FByteArchive
import me.fungames.jfortniteparse.ue4.versions.EUnrealEngineObjectUE5Version
import me.fungames.jfortniteparse.ue4.versions.GAME_UE5
import me.fungames.jfortniteparse.ue4.versions.GAME_UE5_BASE
import me.fungames.jfortniteparse.ue4.versions.VersionContainer
import me.fungames.jfortniteparse.util.get
import java.nio.ByteBuffer

/**
 * Linker for I/O Store packages
 */
class IoPackage : Package {
    val packageId: FPackageId
    val globalPackageStore: FPackageStore
    val nameMap: FNameMap
    var bulkDataMap: Array<FBulkDataMapEntry>? = null
        private set
    var importedPublicExportHashes: Array<Long>? = null
        private set
    val importMap: Array<FPackageObjectIndex>
    val exportMap: Array<FExportMapEntry>
    var exportBundleHeaders: Array<FExportBundleHeader>? = null
        private set
    val exportBundleEntries: Array<FExportBundleEntry>
    val importedPackages: Lazy<List<IoPackage?>>
    override val exportsLazy: List<Lazy<UObject>>
    var bulkDataStartOffset = 0
        private set

    constructor(uasset: ByteArray,
                packageId: FPackageId,
                storeEntry: FFilePackageStoreEntry,
                globalPackageStore: FPackageStore,
                provider: FileProvider,
                versions: VersionContainer = provider.versions) : super("", provider, versions) {
        this.packageId = packageId
        this.globalPackageStore = globalPackageStore
        val Ar = FByteArchive(uasset, versions)

        val cookedHeaderSize: Int
        val allExportDataOffset: Int

        if (versions.game >= GAME_UE5_BASE) {
            val summary = FZenPackageSummary(Ar)
            if (summary.bHasVersioningInfo) {
                val versioningInfo = FZenPackageVersioningInfo(Ar)
                if (!versions.explicitVer) {
                    versions.ver = versioningInfo.packageVersion.value
                    versions.customVersions = versioningInfo.customVersions.toList()
                }
            }
            cookedHeaderSize = summary.cookedHeaderSize.toInt()

            if (!summary.bHasVersioningInfo && versions.ver >= EUnrealEngineObjectUE5Version.VERSE_CELLS) {
                Ar.skip(8)
            }

            var cellOffsets = FZenPackageCellOffsets(Ar)

            // Name map
            nameMap = FNameMap()
            nameMap.load(Ar, FMappedName.EType.Package)

            val diskPackageName = nameMap.getName(summary.name)
            fileName = diskPackageName.text
            packageFlags = summary.packageFlags.toInt()
            name = fileName

            if (versions.game >= GAME_UE5(2) || versions.ver >= EUnrealEngineObjectUE5Version.DATA_RESOURCES) {
                if (versions.game >= GAME_UE5(4)) {
                    val pad = Ar.readInt64()
                    Ar.skip(pad)
                }
                val bulkDataMapSize = Ar.readUInt64()
                if (versions.game != GAME_UE5(2) || bulkDataMapSize < 65535u) { // Fortnite moment
                    val bulkDataMapCount = bulkDataMapSize.toInt() / FBulkDataMapEntry.SIZE
                    bulkDataMap = Array(bulkDataMapCount) { FBulkDataMapEntry(Ar) }
                }
            }

            // Imported public export hashes
            Ar.seek(summary.importedPublicExportHashesOffset)
            importedPublicExportHashes = Array((summary.importMapOffset - summary.importedPublicExportHashesOffset) / 8) { Ar.readInt64() }

            // Import map
            Ar.seek(summary.importMapOffset)
            val importCount = (summary.exportMapOffset - summary.importMapOffset) / 8
            importMap = Array(importCount) { FPackageObjectIndex(Ar) }

            // Export map
            Ar.seek(summary.exportMapOffset)
            val exportCount = (summary.exportBundleEntriesOffset - summary.exportMapOffset) / FExportMapEntry.SIZE
            exportMap = Array(exportCount) { FExportMapEntry(Ar) }
            exportsLazy = (arrayOfNulls<Lazy<UObject>>(exportCount) as Array<Lazy<UObject>>).toMutableList()

            Ar.seek(cellOffsets.cellImportMapOffset)

            // Export bundle entries
//            Ar.seek(summary.exportBundleEntriesOffset)
            exportBundleEntries = Array(exportCount * 2) { FExportBundleEntry(Ar) }

            if (Ar.game < GAME_UE5(2)) {
                // Export bundle headers
                Ar.seek(summary.graphDataOffset)
                exportBundleHeaders = Array(storeEntry.exportBundleCount) { FExportBundleHeader(Ar) }
            }

            allExportDataOffset = summary.headerSize.toInt()
        } else {
            val summary = FPackageSummary(Ar)
            cookedHeaderSize = summary.cookedHeaderSize.toInt()

            // Name map
            nameMap = FNameMap()
            if (summary.nameMapNamesSize > 0) {
                val nameMapNamesData = FByteArchive(ByteBuffer.wrap(uasset, summary.nameMapNamesOffset, summary.nameMapNamesSize))
                val nameMapHashesData = FByteArchive(ByteBuffer.wrap(uasset, summary.nameMapHashesOffset, summary.nameMapHashesSize))
                nameMap.load(nameMapNamesData, nameMapHashesData, FMappedName.EType.Package)
            }

            val diskPackageName = nameMap.getName(summary.name)
            fileName = diskPackageName.text
            packageFlags = summary.packageFlags.toInt()
            name = fileName

            // Import map
            Ar.seek(summary.importMapOffset)
            val importCount = (summary.exportMapOffset - summary.importMapOffset) / 8
            importMap = Array(importCount) { FPackageObjectIndex(Ar) }

            // Export map
            Ar.seek(summary.exportMapOffset)
            val exportCount = storeEntry.exportCount //(summary.exportBundlesOffset - summary.exportMapOffset) / FExportMapEntry.SIZE
            exportMap = Array(exportCount) { FExportMapEntry(Ar) }
            exportsLazy = (arrayOfNulls<Lazy<UObject>>(exportCount) as Array<Lazy<UObject>>).toMutableList()

            // Export bundles
            Ar.seek(summary.exportBundlesOffset)
            exportBundleHeaders = Array(storeEntry.exportBundleCount) { FExportBundleHeader(Ar) }
            exportBundleEntries = Array(exportCount * 2) { FExportBundleEntry(Ar) }

            allExportDataOffset = summary.graphDataOffset + summary.graphDataSize
        }

        // Preload dependencies
        val importedPackageIds = storeEntry.importedPackages
        importedPackages = lazy { importedPackageIds.map { provider.loadGameFile(it) } }

        // Populate lazy exports
        fun processEntry(entry: FExportBundleEntry, pos: Int, newPositioning: Boolean): Int {
            if (entry.commandType != FExportBundleEntry.EExportCommandType.ExportCommandType_Serialize) {
                return 0
            }
            val export = exportMap[entry.localExportIndex]
            exportsLazy[entry.localExportIndex] = lazy {
                // Create
                val objectName = nameMap.getName(export.objectName)
                val obj = constructExport(resolveObjectIndex(export.classIndex)?.getObject()?.value as UStruct?)
                obj.name = objectName.text
                obj.outer = (resolveObjectIndex(export.outerIndex) as? ResolvedExportObject)?.exportObject?.value ?: this
                obj.template = resolveObjectIndex(export.templateIndex)
                obj.flags = export.objectFlags.toInt()

                // Serialize
                val Ar = FExportArchive(ByteBuffer.wrap(uasset), obj, this)
                Ar.useUnversionedPropertySerialization = (packageFlags and EPackageFlags.PKG_UnversionedProperties.value) != 0
                Ar.uassetSize = if (newPositioning) cookedHeaderSize - allExportDataOffset else export.cookedSerialOffset.toInt() - pos
                Ar.bulkDataStartOffset = bulkDataStartOffset
                Ar.seek(pos)
                val validPos = Ar.pos() + export.cookedSerialSize.toInt()
                try {
                    obj.deserialize(Ar, validPos)
                    if (validPos != Ar.pos()) {
                        LOG_STREAMING.warn { "Did not read ${obj.exportType} correctly, ${validPos - Ar.pos()} bytes remaining (${obj.getPathName()})" }
                    } else {
                        LOG_STREAMING.debug { "Successfully read ${obj.exportType} at $pos with size ${export.cookedSerialSize}" }
                    }
                } catch (e: Throwable) {
                    if (GFatalObjectSerializationErrors) {
                        throw e
                    } else {
                        LOG_STREAMING.error(e) { "Could not read ${obj.exportType} correctly" }
                    }
                }
                obj
            }
            return export.cookedSerialSize.toInt()
        }

        if (exportBundleHeaders != null) {
            var currentExportDataOffset = allExportDataOffset
            for (exportBundle in exportBundleHeaders!!) {
                for (i in 0u until exportBundle.entryCount) {
                    val entry = exportBundleEntries[exportBundle.firstEntryIndex + i]
                    currentExportDataOffset += processEntry(entry, currentExportDataOffset, false)
                }
            }
            bulkDataStartOffset = currentExportDataOffset
        } else { // UE5.2+
            for (entry in exportBundleEntries) {
                processEntry(entry, allExportDataOffset + exportMap[entry.localExportIndex].cookedSerialOffset.toInt(), true)
            }
        }
        //logger.info { "Successfully parsed package : $name" }
    }

    // region Object resolvers
    override fun <T : UObject> findObject(index: FPackageIndex?) = when {
        index == null || index.isNull() -> null
        index.isImport() -> importMap.getOrNull(index.toImport())?.let { resolveObjectIndex(it) }?.getObject()
        else -> exportsLazy.getOrNull(index.toExport())
    } as Lazy<T>?

    override fun findObjectByName(objectName: String, className: String?): Lazy<UObject>? {
        val exportIndex = exportMap.indexOfFirst {
            nameMap.getName(it.objectName).text.equals(objectName, true) && (className == null || resolveObjectIndex(it.classIndex)?.getName()?.text == className)
        }
        return if (exportIndex != -1) exportsLazy[exportIndex] else null
    }

    override fun findObjectMinimal(index: FPackageIndex?) = when {
        index == null || index.isNull() -> null
        index.isImport() -> importMap.getOrNull(index.toImport())?.let { resolveObjectIndex(it) }
        else -> ResolvedExportObject(index.toExport(), this)
    }

    fun resolveObjectIndex(index: FPackageObjectIndex?): ResolvedObject? {
        if (index == null || index.isNull()) {
            return null
        }
        when {
            index.isExport() -> return ResolvedExportObject(index.toExport().toInt(), this@IoPackage)
            index.isScriptImport() -> {
                val scriptObjectEntry = globalPackageStore.scriptObjectEntriesMap[index]
                if (scriptObjectEntry != null) {
                    return ResolvedScriptObject(scriptObjectEntry, this@IoPackage)
                }
            }
            index.isPackageImport() -> {
                val localProvider = provider
                if (localProvider != null) {
                    val localImportedPublicExportHashes = importedPublicExportHashes
                    if (localImportedPublicExportHashes != null) {
                        val packageImportRef = index.toPackageImportRef()
                        val pkg = importedPackages.value.getOrNull(packageImportRef.importedPackageIndex.toInt())
                        pkg?.exportMap?.forEachIndexed { exportIndex, exportMapEntry ->
                            if (exportMapEntry.publicExportHash == localImportedPublicExportHashes[packageImportRef.importedPublicExportHashIndex]) {
                                return ResolvedExportObject(exportIndex, pkg)
                            }
                        }
                    } else {
                        for (pkg in importedPackages.value) {
                            pkg?.exportMap?.forEachIndexed { exportIndex, exportMapEntry ->
                                if (exportMapEntry.globalImportIndex == index) {
                                    return ResolvedExportObject(exportIndex, pkg)
                                }
                            }
                        }
                    }
                }
            }
        }
        LOG_STREAMING.warn("Missing %s import 0x%016X for package %s".format(
            if (index.isScriptImport()) "script" else "package",
            index.value().toLong(),
            fileName
        ))
        return null
    }

    class ResolvedExportObject(exportIndex: Int, pkg: IoPackage) : ResolvedObject(pkg, exportIndex) {
        val exportMapEntry = pkg.exportMap[exportIndex]
        val exportObject = pkg.exportsLazy[exportIndex]
        override fun getName() = (pkg as IoPackage).nameMap.getName(exportMapEntry.objectName)
        override fun getOuter() = (pkg as IoPackage).resolveObjectIndex(exportMapEntry.outerIndex) ?: ResolvedLoadedObject(pkg)
        override fun getClazz() = (pkg as IoPackage).resolveObjectIndex(exportMapEntry.classIndex)
        override fun getSuper() = (pkg as IoPackage).resolveObjectIndex(exportMapEntry.superIndex)
        override fun getObject() = exportObject
    }

    class ResolvedScriptObject(val scriptImport: FScriptObjectEntry, pkg: IoPackage) : ResolvedObject(pkg) {
        override fun getName() = scriptImport.objectName.toName()
        override fun getOuter() = (pkg as IoPackage).resolveObjectIndex(scriptImport.outerIndex)
        // This means we'll have UScriptStruct's shown as UClass which is wrong.
        // Unfortunately because the mappings format does not distinguish between classes and structs, there's no other way around :(
        override fun getClazz() = ResolvedLoadedObject(UScriptStruct(FName("Class")))
        override fun getObject() = lazy {
            val name = getName()
            pkg.provider?.mappingsProvider?.getStruct(name) ?: pkg.provider?.mappingsProvider?.getEnum(name)
        }
    }
    // endregion

    override fun toJson(context: Gson, locres: Locres?) = jsonObject(
        "import_map" to gson.toJsonTree(importMap),
        "export_map" to gson.toJsonTree(exportMap),
        "export_properties" to gson.toJsonTree(exports.map {
            it.toJson(gson, locres)
        })
    )

    /*fun dumpHeaderToJson(): JsonObject {
        val gson = gson.newBuilder().registerTypeAdapter(jsonSerializer<FMappedName> { JsonPrimitive(nameMap.getNameOrNull(it.src)?.text) }).create()
        return JsonObject().apply {
            add("summary", gson.toJsonTree(summary))
            add("nameMap", gson.toJsonTree(nameMap))
            add("importMap", gson.toJsonTree(importMap))
            add("exportMap", gson.toJsonTree(exportMap))
            add("exportBundleHeaders", gson.toJsonTree(exportBundleHeaders))
            add("exportBundleEntries", gson.toJsonTree(exportBundleEntries))
            add("graphData", gson.toJsonTree(graphData))
        }
    }*/
}