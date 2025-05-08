package me.fungames.jfortniteparse.ue4.versions

import me.fungames.jfortniteparse.ue4.reader.FArchive

object EUnrealEngineObjectUE5Version {
    const val INITIAL_VERSION = 1000
    const val NAMES_REFERENCED_FROM_EXPORT_DATA = 1001
    const val PAYLOAD_TOC = 1002
    const val OPTIONAL_RESOURCES = 1003
    const val LARGE_WORLD_COORDINATES = 1004
    const val REMOVE_OBJECT_EXPORT_PACKAGE_GUID = 1005
    const val TRACK_OBJECT_EXPORT_IS_INHERITED = 1006
    const val FSOFTOBJECTPATH_REMOVE_ASSET_PATH_FNAMES = 1007
    const val ADD_SOFTOBJECTPATH_LIST = 1008
    const val DATA_RESOURCES = 1009
    const val SCRIPT_SERIALIZATION_OFFSET = 1010
    const val PROPERTY_TAG_EXTENSION_AND_OVERRIDABLE_SERIALIZATION = 1011
    const val PROPERTY_TAG_COMPLETE_TYPE_NAME = 1012
    const val ASSETREGISTRY_PACKAGEBUILDDEPENDENCIES = 1013
    const val METADATA_SERIALIZATION_OFFSET = 1014
    const val VERSE_CELLS = 1015
    const val PACKAGE_SAVED_HASH = 1016
    const val OS_SUB_OBJECT_SHADOW_SERIALIZATION = 1017

    const val AUTOMATIC_VERSION = OS_SUB_OBJECT_SHADOW_SERIALIZATION
}

/**
 * This object combines all of our version enums into a single easy to use structure
 * which allows us to update older version numbers independently of the newer version numbers.
 */
class FPackageFileVersion(var fileVersionUE4: Int, var fileVersionUE5: Int) {
    constructor(Ar: FArchive) : this(Ar.readInt32(), Ar.readInt32())

    /** Set all versions to the default state */
    inline fun reset() {
        fileVersionUE4 = 0
        fileVersionUE5 = 0
    }

    var value: Int
        inline get() = if (fileVersionUE5 >= EUnrealEngineObjectUE5Version.INITIAL_VERSION) fileVersionUE5 else fileVersionUE4
        inline set(value) {
            if (value >= EUnrealEngineObjectUE5Version.INITIAL_VERSION) {
                fileVersionUE5 = value
            } else {
                fileVersionUE4 = value
            }
        }

    inline operator fun compareTo(other: Int) = value - other

    companion object {
        /** Creates and returns a FPackageFileVersion based on a single EUnrealEngineObjectUEVersion and no other versions. */
        @JvmStatic inline fun createUE4Version(version: Int) = FPackageFileVersion(version, 0)
    }
}