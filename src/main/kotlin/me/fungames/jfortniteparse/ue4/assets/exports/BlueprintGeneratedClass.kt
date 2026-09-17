package me.fungames.jfortniteparse.ue4.assets.exports

import me.fungames.jfortniteparse.ue4.assets.UStruct
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.versions.FFortniteMainBranchObjectVersion

@UStruct
class FPointerToUberGraphFrame

open class UBlueprintGeneratedClass : UBlueprintGeneratedClass_Properties() {
    var editorTags: Map<FName, String>? = null

    override fun deserialize(Ar: FAssetArchive, validPos: Int) {
        super.deserialize(Ar, validPos)
        if (FFortniteMainBranchObjectVersion.get(Ar) >= FFortniteMainBranchObjectVersion.BPGCCookedEditorTags && validPos - Ar.pos() > 4) {
            editorTags = Ar.readTMap { Ar.readFName() to Ar.readString() }
        }
    }
}
