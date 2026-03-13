package me.fungames.jfortniteparse.fileprovider

import kotlinx.coroutines.*
import me.fungames.jfortniteparse.encryption.aes.Aes
import me.fungames.jfortniteparse.exceptions.InvalidAesKeyException
import me.fungames.jfortniteparse.ue4.assets.IoPackage
import me.fungames.jfortniteparse.ue4.io.*
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageId
import me.fungames.jfortniteparse.ue4.pak.GameFile
import me.fungames.jfortniteparse.ue4.reader.FByteArchive
import me.fungames.jfortniteparse.ue4.versions.GAME_UE5_BASE
import me.fungames.jfortniteparse.ue4.vfs.AbstractAesVfsReader
import me.fungames.jfortniteparse.util.printAesKey
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

abstract class PakFileProvider : AbstractFileProvider(), CoroutineScope {
    companion object {
        const val ON_DEMAND_CDN_BASE = "https://epicgames-download1.akamaized.net"
    }

    private val job = Job()
    override val coroutineContext = job + Dispatchers.IO

    protected abstract val unloadedPaks: MutableList<AbstractAesVfsReader>
    protected abstract val mountedPaks: MutableList<AbstractAesVfsReader>
    protected abstract val requiredKeys: MutableList<FGuid>
    protected abstract val keys: MutableMap<FGuid, ByteArray>
    protected val mountListeners = mutableListOf<PakMountListener>()
    open val globalPackageStore = lazy { FPackageStore(this) }
    var customEncryption: AbstractAesVfsReader.CustomEncryption? = null

    // On-demand CDN support
    protected val onDemandTocs = mutableListOf<IoChunkToc>()
    val unlinkedOnDemandContainers = mutableListOf<FOnDemandTocContainerEntry>()
    open fun keys(): Map<FGuid, ByteArray> = keys
    fun keysStr(): Map<FGuid, String> = keys.mapValues { it.value.printAesKey() }
    open fun requiredKeys(): List<FGuid> = requiredKeys
    open fun unloadedPaks(): List<AbstractAesVfsReader> = unloadedPaks
    open fun mountedPaks(): List<AbstractAesVfsReader> = mountedPaks
    fun submitKey(guid: FGuid, key: String) = submitKeysStr(mapOf(guid to key))
    fun submitKeysStr(keys: Map<FGuid, String>) = submitKeys(keys.mapValues { Aes.parseKey(it.value) })
    fun submitKey(guid: FGuid, key: ByteArray) = submitKeys(mapOf(guid to key))
    open fun submitKeys(keys: Map<FGuid, ByteArray>) = runBlocking { submitKeysAsync(keys).await() }

    open fun unloadedPaksByGuid(guid: FGuid) = unloadedPaks.filter { it.encryptionKeyGuid == guid }

    open fun submitKeysAsync(newKeys: Map<FGuid, ByteArray>): Deferred<Int> {
        val countNewMounts = AtomicInteger()
        val tasks = mutableListOf<Deferred<Result<AbstractAesVfsReader>>>()
        for ((guid, key) in newKeys) {
            if (guid !in requiredKeys)
                continue
            for (reader in unloadedPaksByGuid(guid)) tasks += async {
                runCatching {
                    reader.aesKey = key
                    keys[guid] = key
                    mount(reader)
                    unloadedPaks.remove(reader)
                    requiredKeys.remove(guid)
                    countNewMounts.getAndIncrement()
                    reader
                }.onFailure {
                    if (it is InvalidAesKeyException)
                        keys.remove(guid)
                    else
                        logger.warn(it) { "Uncaught exception while loading ${reader.name.substringAfterLast('/')}" }
                }
            }
        }
        return async {
            tasks.awaitAll()
            countNewMounts.get()
        }
    }

    protected open fun mount(reader: AbstractAesVfsReader) {
        val gameFiles = reader.readIndex()
        for (gameFile in gameFiles) {
            val fullPath = gameFile.path.toLowerCase()
            files[fullPath] = gameFile
            // Register virtual path alias for Game Feature plugin content.
            // Files indexed as "fortnitegame/plugins/gamefeatures/brcosmetics/content/ui/..."
            // also need to be findable as "brcosmetics/ui/..." (the UE virtual mount path).
            val gfMarker = "/plugins/gamefeatures/"
            val gfIdx = fullPath.indexOf(gfMarker)
            if (gfIdx >= 0) {
                val afterGF = fullPath.substring(gfIdx + gfMarker.length) // "brcosmetics/content/ui/..."
                val contentIdx = afterGF.indexOf("/content/")
                if (contentIdx >= 0) {
                    val featureName = afterGF.substring(0, contentIdx) // "brcosmetics"
                    val remainder = afterGF.substring(contentIdx + "/content".length) // "/ui/..."
                    val virtualPath = featureName + remainder // "brcosmetics/ui/..."
                    files[virtualPath] = gameFile
                }
            }
        }
        mountedPaks.add(reader)
        if (reader is FIoStoreReaderImpl) {
            if (reader.name == "global") {
                globalDataLoaded = true
            }
            if (globalPackageStore.isInitialized()) {
                globalPackageStore.value.onContainerMounted(reader)
            }
        }
        mountListeners.forEach { it.onMount(reader) }
    }

    /**
     * Registers a parsed on-demand TOC. Call this for each .uondemandtoc/.iochunktoc file found.
     * After registering all TOCs, call processOnDemandContainers() to link and mount them.
     */
    fun registerOnDemandToc(toc: IoChunkToc) {
        onDemandTocs.add(toc)
        logger.info("Loaded on-demand TOC with ${toc.containers.size} containers")
        logger.info("  chunksDirectory='${toc.header.chunksDirectory}' compression='${toc.header.compressionFormat}' version=${toc.header.version}")
        if (toc.meta != null) logger.info("  buildVersion='${toc.meta!!.buildVersion}' platform='${toc.meta!!.targetPlatform}'")
        for (c in toc.containers) {
            if (c.entries.isNotEmpty()) {
                val sample = c.entries[0]
                val hashHex = sample.hash.joinToString("") { "%02x".format(it) }
                logger.info("  container '${c.containerName}': sample entry hash=$hashHex chunkId=${sample.chunkId} rawSize=${sample.rawSize} encodedSize=${sample.encodedSize}")
            }
        }
    }

    /**
     * Links on-demand TOC containers to IoStore readers, then creates CDN-backed
     * readers for any unlinked containers. Call after all TOCs are registered and
     * all local paks/IoStores have been scanned.
     */
    fun processOnDemandContainers() {
        linkOnDemandContainers()
        mountOnDemandReaders()
    }

    private fun linkOnDemandContainers() {
        if (onDemandTocs.isEmpty()) return

        val ioStoreReaders = unloadedPaks.filterIsInstance<FIoStoreReaderImpl>()
        logger.info("On-demand linking: ${ioStoreReaders.size} IoStore readers, ${onDemandTocs.size} TOC files")
        var linked = 0
        for (toc in onDemandTocs) {
            for (containerEntry in toc.containers) {
                logger.info("On-demand container '${containerEntry.containerName}': header=${containerEntry.header.size} bytes, entries=${containerEntry.entries.size}")
                if (containerEntry.header.isEmpty()) {
                    logger.warn("Skipping container '${containerEntry.containerName}' — header is empty")
                    continue
                }
                val reader = ioStoreReaders.firstOrNull { reader ->
                    val readerName = reader.name.substringAfterLast('/').substringAfterLast('\\')
                    readerName.equals(containerEntry.containerName, ignoreCase = true)
                }
                if (reader != null) {
                    reader.onDemandContainer = containerEntry
                    linked++
                    logger.info("Linked '${containerEntry.containerName}' to reader '${reader.name}' (header=${containerEntry.header.size} bytes)")
                } else {
                    if (containerEntry.header.isNotEmpty()) {
                        unlinkedOnDemandContainers.add(containerEntry)
                        logger.info("Stored unlinked on-demand container '${containerEntry.containerName}' (header=${containerEntry.header.size} bytes) for standalone header loading")
                    } else {
                        logger.warn("No IoStore reader found for on-demand container '${containerEntry.containerName}'")
                    }
                }
            }
        }
        logger.info("On-demand linking complete: $linked containers linked")
    }

    private fun mountOnDemandReaders() {
        if (unlinkedOnDemandContainers.isEmpty()) return

        val toRemove = mutableListOf<FOnDemandTocContainerEntry>()

        for (toc in onDemandTocs) {
            for (container in toc.containers) {
                if (container !in unlinkedOnDemandContainers) continue
                if (container.entries.isEmpty()) continue

                val utocHashHex = container.utocHash.joinToString("") { "%02x".format(it) }
                val utocUrl = "$ON_DEMAND_CDN_BASE/${toc.header.chunksDirectory}/$utocHashHex.utoc"

                logger.info("Downloading on-demand UTOC for '${container.containerName}' from CDN...")

                val utocBytes: ByteArray
                try {
                    utocBytes = fetchUrl(utocUrl)
                    logger.info("Downloaded UTOC for '${container.containerName}': ${utocBytes.size} bytes")
                } catch (e: Exception) {
                    logger.error("Failed to download UTOC for '${container.containerName}': ${e.message}")
                    continue
                }

                try {
                    val mappings = buildCdnMappings(utocBytes, container.entries)
                    val utocArchive = FByteArchive(utocBytes, versions)
                    val cache = ConcurrentHashMap<String, ByteArray>()
                    val path = "on-demand/${container.containerName}"

                    val reader = FIoStoreReaderImpl(utocArchive, path, { _ ->
                        OnDemandPakArchive(mappings, ON_DEMAND_CDN_BASE, toc.header.chunksDirectory, cache)
                    }, ioStoreTocReadOptions)

                    reader.customEncryption = customEncryption
                    reader.onDemandContainer = container

                    if (reader.isEncrypted()) {
                        if (reader.encryptionKeyGuid !in requiredKeys) requiredKeys.add(reader.encryptionKeyGuid)
                        unloadedPaks.add(reader)
                    } else {
                        mountedPaks.add(reader)
                    }
                    toRemove.add(container)

                    logger.info("Created on-demand IoStore reader for '${container.containerName}': ${mappings.size} CDN chunks, encrypted=${reader.isEncrypted()}")
                } catch (e: Exception) {
                    logger.error("Failed to create on-demand reader for '${container.containerName}': ${e.message}")
                }
            }
        }

        unlinkedOnDemandContainers.removeAll(toRemove)
    }

    private fun buildCdnMappings(utocBytes: ByteArray, entries: Array<FOnDemandTocEntry>): List<CdnChunkMapping> {
        val ar = FByteArchive(utocBytes, versions)
        val tocResource = FIoStoreTocResource(ar, 0)

        val compressionBlockSize = tocResource.header.compressionBlockSize
        val mappings = mutableListOf<CdnChunkMapping>()

        for (entry in entries) {
            val tocEntryIndex = tocResource.getTocEntryIndex(entry.chunkId)
            if (tocEntryIndex == -1) continue

            val offsetAndLength = tocResource.chunkOffsetLengths[tocEntryIndex]
            val firstBlockIndex = (offsetAndLength.offset / compressionBlockSize).toInt()
            val ucasStartOffset = tocResource.compressionBlocks[firstBlockIndex].offset.toLong()

            val hashHex = entry.hash.joinToString("") { "%02x".format(it) }
            mappings.add(CdnChunkMapping(ucasStartOffset, hashHex))
        }

        mappings.sortBy { it.ucasStartOffset }
        return mappings
    }

    protected fun fetchUrl(url: String): ByteArray {
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

    override fun loadGameFile(packageId: FPackageId): IoPackage? = runCatching {
        val storeEntry = globalPackageStore.value.findStoreEntry(packageId)
            ?: return null
        val chunkType = if (game >= GAME_UE5_BASE) EIoChunkType5.ExportBundleData else EIoChunkType.ExportBundleData
        val chunkId = FIoChunkId(packageId.value(), 0u, chunkType)
        val ioBuffer = saveChunk(chunkId)
        return IoPackage(ioBuffer, packageId, storeEntry, globalPackageStore.value, this, versions)
    }.onFailure { logger.error(it) { "Failed to load package with id 0x%016X".format(packageId.value().toLong()) } }.getOrNull()

    override fun saveGameFile(filePath: String): ByteArray? {
        val path = fixPath(filePath)
        val gameFile = findGameFile(path)
        return gameFile?.let { saveGameFile(it) }
    }

    override fun saveGameFile(file: GameFile): ByteArray {
        if (file.ioChunkId != null && file.ioStoreReader != null)
            return file.ioStoreReader.read(file.ioChunkId)
        val reader = mountedPaks.firstOrNull { it.name == file.pakFileName } ?: throw IllegalArgumentException("Couldn't find any possible pak file readers")
        return reader.extract(file)
    }

    override fun saveChunk(chunkId: FIoChunkId): ByteArray {
        for (reader in mountedPaks) {
            if (reader !is FIoStoreReaderImpl) continue
            try {
                return reader.read(chunkId)
            } catch (e: FIoStatusException) {
                if (e.status.errorCode != EIoErrorCode.NotFound) {
                    throw e
                }
            }
        }
        throw IllegalArgumentException("Couldn't find any possible I/O store readers")
    }

    fun addOnMountListener(listener: PakMountListener) {
        mountListeners.add(listener)
    }

    fun removeOnMountListener(listener: PakMountListener) {
        mountListeners.remove(listener)
    }

    fun interface PakMountListener {
        fun onMount(reader: AbstractAesVfsReader)
    }
}