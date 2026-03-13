package me.fungames.jfortniteparse.fileprovider

import me.fungames.jfortniteparse.ue4.assets.mappings.ReflectionTypeMappingsProvider
import me.fungames.jfortniteparse.ue4.assets.mappings.TypeMappingsProvider
import me.fungames.jfortniteparse.ue4.io.*
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.pak.GameFile
import me.fungames.jfortniteparse.ue4.pak.PakFileReader
import me.fungames.jfortniteparse.ue4.reader.FByteArchive
import me.fungames.jfortniteparse.ue4.versions.Ue4Version
import me.fungames.jfortniteparse.ue4.versions.VersionContainer
import me.fungames.jfortniteparse.ue4.vfs.AbstractAesVfsReader
import java.io.Closeable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

open class DefaultFileProvider : PakFileProvider, Closeable {
    companion object {
        const val ON_DEMAND_CDN_BASE = "https://epicgames-download1.akamaized.net"
    }

    val folder: File
    final override var versions: VersionContainer
    private val localFiles = mutableMapOf<String, File>()
    override val files = ConcurrentHashMap<String, GameFile>()
    override val unloadedPaks = CopyOnWriteArrayList<AbstractAesVfsReader>()
    override val requiredKeys = CopyOnWriteArrayList<FGuid>()
    override val keys = ConcurrentHashMap<FGuid, ByteArray>()
    override val mountedPaks = CopyOnWriteArrayList<AbstractAesVfsReader>()
    private val onDemandTocs = mutableListOf<IoChunkToc>()
    /** On-demand containers that have no local IoStore reader (e.g. streaming-only content) */
    val unlinkedOnDemandContainers = mutableListOf<FOnDemandTocContainerEntry>()

    @JvmOverloads
    constructor(folder: File, versions: VersionContainer = VersionContainer.DEFAULT) {
        this.folder = folder
        this.versions = versions
        scanFiles(folder)
        linkOnDemandContainers()
        mountOnDemandReaders()
    }

    @JvmOverloads
    constructor(folder: File, game: Ue4Version, mappingsProvider: TypeMappingsProvider = ReflectionTypeMappingsProvider()) : this(folder, VersionContainer(game.game)) {
        this.mappingsProvider = mappingsProvider
    }

    private fun scanFiles(folder: File) {
        for (file in folder.listFiles() ?: emptyArray()) {
            if (file.isDirectory) {
                scanFiles(file)
            } else if (file.isFile) {
                registerFile(file)
            }
        }
    }

    private fun registerFile(file: File) {
        val ext = file.extension.toLowerCase()
        if (ext == "pak") {
            try {
                val reader = PakFileReader(file, versions)
                reader.customEncryption = customEncryption
                if (reader.isEncrypted()) {
                    requiredKeys.addIfAbsent(reader.encryptionKeyGuid)
                }
                unloadedPaks.add(reader)
            } catch (e: Exception) {
                logger.error("Failed to open pak file \"${file.path}\"", e)
            }
        } else if (ext == "utoc") {
            val path = file.path.substringBeforeLast('.')
            try {
                val reader = FIoStoreReaderImpl(path, ioStoreTocReadOptions, versions)
                reader.customEncryption = customEncryption
                if (reader.isEncrypted()) {
                    requiredKeys.addIfAbsent(reader.encryptionKeyGuid)
                }
                unloadedPaks.add(reader)
            } catch (e: Exception) {
                logger.error("Failed to open IoStore environment \"$path\"", e)
            }
        } else if (ext == "uondemandtoc" || ext == "iochunktoc") {
            try {
                val toc = IoChunkToc(file)
                onDemandTocs.add(toc)
                logger.info("Loaded on-demand TOC \"${file.name}\" with ${toc.containers.size} containers")
                logger.info("  chunksDirectory='${toc.header.chunksDirectory}' compression='${toc.header.compressionFormat}' version=${toc.header.version}")
                if (toc.meta != null) logger.info("  buildVersion='${toc.meta!!.buildVersion}' platform='${toc.meta!!.targetPlatform}'")
                for (c in toc.containers) {
                    if (c.entries.isNotEmpty()) {
                        val sample = c.entries[0]
                        val hashHex = sample.hash.joinToString("") { "%02x".format(it) }
                        logger.info("  container '${c.containerName}': sample entry hash=$hashHex chunkId=${sample.chunkId} rawSize=${sample.rawSize} encodedSize=${sample.encodedSize}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to parse on-demand TOC \"${file.path}\"", e)
            }
        } else {
            var gamePath = file.absolutePath.substringAfter(folder.absolutePath)
            if (gamePath.startsWith('\\') || gamePath.startsWith('/'))
                gamePath = gamePath.substring(1)
            gamePath = gamePath.replace('\\', '/')
            localFiles[gamePath.toLowerCase()] = file
        }
    }

    /**
     * After scanning, links on-demand TOC container entries to their
     * matching IoStore readers by container name. This provides the readers
     * with the embedded container header data they need for FPackageStore.
     */
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

    /**
     * For each unlinked on-demand container, downloads its UTOC from the CDN
     * and creates an FIoStoreReaderImpl backed by a CDN archive.
     *
     * This is the correct approach (matching CUE4Parse): the UTOC contains
     * the compression block metadata needed for proper decompression. The
     * OnDemandPakArchive transparently downloads .iochunk files from CDN
     * when the reader's read() method needs container data.
     *
     * The created readers are added to unloadedPaks and will be mounted
     * when AES keys are submitted, just like local IoStore readers.
     */
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
                    // First parse: build the UCAS offset -> CDN hash mappings
                    val mappings = buildCdnMappings(utocBytes, container.entries)

                    // Second parse: create the IoStore reader with CDN-backed archive
                    val utocArchive = FByteArchive(utocBytes, versions)
                    val cache = ConcurrentHashMap<String, ByteArray>()
                    val path = "on-demand/${container.containerName}"

                    val reader = FIoStoreReaderImpl(utocArchive, path, { _ ->
                        OnDemandPakArchive(mappings, ON_DEMAND_CDN_BASE, toc.header.chunksDirectory, cache)
                    }, ioStoreTocReadOptions)

                    reader.customEncryption = customEncryption
                    reader.onDemandContainer = container

                    if (reader.isEncrypted()) {
                        // Encrypted: add to unloadedPaks, will be mounted when key is submitted
                        requiredKeys.addIfAbsent(reader.encryptionKeyGuid)
                        unloadedPaks.add(reader)
                    } else {
                        // Not encrypted: mount immediately so saveChunk() can find it
                        mountedPaks.add(reader)
                    }
                    toRemove.add(container)

                    logger.info("Created on-demand IoStore reader for '${container.containerName}': ${mappings.size} CDN chunks, encrypted=${reader.isEncrypted()}")
                } catch (e: Exception) {
                    logger.error("Failed to create on-demand reader for '${container.containerName}': ${e.message}")
                }
            }
        }

        // Remove containers that now have proper readers,
        // so FPackageStore doesn't double-load their headers
        unlinkedOnDemandContainers.removeAll(toRemove)
    }

    /**
     * Builds a sorted list of UCAS offset -> CDN hash mappings.
     *
     * For each on-demand entry, looks up its chunkId in the UTOC to find which
     * compression block it starts at, then records the physical UCAS offset of
     * that block alongside the CDN hash for downloading.
     */
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

    private fun fetchUrl(url: String): ByteArray {
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

    override fun saveGameFile(filePath: String): ByteArray? {
        val res = super.saveGameFile(filePath)
        if (res != null)
            return res
        val path = fixPath(filePath)
        var file = localFiles[path]
        if (file == null) {
            val justName = path.substringAfterLast('/')
            file = localFiles[justName]
        }
        if (file == null && path.startsWith("Game/", ignoreCase = true)) {
            file = localFiles.filterKeys {
                if (it.contains("Game/", ignoreCase = true))
                    it.substringAfter("game/") == path.substringAfter("game/")
                else
                    false
            }.values.firstOrNull()
        }
        return file?.readBytes()
    }

    override fun close() {
        files.clear()
        unloadedPaks.forEach { it.close() }
        unloadedPaks.clear()
        mountedPaks.forEach { it.close() }
        mountedPaks.clear()
        keys.clear()
        requiredKeys.clear()
        globalDataLoaded = false
    }
}
