package me.fungames.jfortniteparse.ue4.objects.uobject

import me.fungames.jfortniteparse.ue4.assets.Package
import me.fungames.jfortniteparse.ue4.reader.FArchive
import me.fungames.jfortniteparse.ue4.versions.EUnrealEngineObjectUE5Version
import me.fungames.jfortniteparse.ue4.writer.FArchiveWriter

/**
 * A struct that contains a string reference to an object, either a top level asset or a subobject.
 * This can be used to make soft references to assets that are loaded on demand.
 * This is stored internally as an FName pointing to the top level asset (/package/path.assetname) and an option a string subobject path.
 * If the MetaClass metadata is applied to a FProperty with this the UI will restrict to that type of asset.
 */
open class FSoftObjectPath {
    /** Asset path, patch to a top level object in a package. This is /package/path.assetname */
    var assetPathName: FName

    /** Optional FString for subobject within an asset. This is the sub path after the : */
    var subPathString: String
    var owner: Package? = null

    constructor(Ar: FArchive) {
        assetPathName = if (Ar.ver >= EUnrealEngineObjectUE5Version.FSOFTOBJECTPATH_REMOVE_ASSET_PATH_FNAMES) {
            FName(FTopLevelAssetPath(Ar).toString())
        } else {
            Ar.readFName()
        }
        subPathString = Ar.readString() // uhhh yeah i cba rn lol TODO: https://github.com/FabianFG/CUE4Parse/commit/b9b1289fef1ab37cb3a7079a6050939b1ff80ca9
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeFName(assetPathName)
        Ar.writeString(subPathString)
    }

    constructor() : this(FName.NAME_None, "")

    /** Construct from an asset FName and subobject pair */
    constructor(assetPathName: FName, subPathString: String) {
        this.assetPathName = assetPathName
        this.subPathString = subPathString
    }

    /** Returns string representation of reference, in form /package/path.assetname[:subpath] */
    override fun toString() =
        // Most of the time there is no sub path so we can do a single string allocation
        if (subPathString.isEmpty()) {
            if (assetPathName.isNone()) "" else assetPathName.toString()
        } else {
            "$assetPathName:$subPathString"
        }

    inline fun <reified T> load() = owner?.provider?.loadObject<T>(this)
}

/**
 * A struct that contains a string reference to a class, can be used to make soft references to classes
 */
class FSoftClassPath : FSoftObjectPath {
    constructor(Ar: FArchive) : super(Ar)
    constructor() : super()
    constructor(assetPathName: FName, subPathString: String) : super(assetPathName, subPathString)
}