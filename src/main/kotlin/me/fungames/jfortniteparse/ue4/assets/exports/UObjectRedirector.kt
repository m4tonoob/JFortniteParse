package me.fungames.jfortniteparse.ue4.assets.exports

import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex

class UObjectRedirector : UObject() {
    lateinit var destinationObject: FPackageIndex

    override fun deserialize(Ar: FAssetArchive, validPos: Int) {
        super.deserialize(Ar, validPos)
        destinationObject = FPackageIndex(Ar)
    }
}
