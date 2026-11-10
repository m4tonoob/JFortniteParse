package me.fungames.jfortniteparse.ue4.objects.engine

import me.fungames.jfortniteparse.ue4.assets.objects.FStructFallback
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex
import me.fungames.jfortniteparse.ue4.versions.FEditorObjectVersion
import me.fungames.jfortniteparse.ue4.versions.GAME_UE4

class FFontData {
    var localFontFaceAsset: FPackageIndex? = null
    var fontFilename: String? = null
    var hinting = EFontHinting.Default
    var loadingPolicy = EFontLoadingPolicy.LazyLoad
    var subFaceIndex = 0
    var fallbackStruct: FStructFallback? = null

    constructor(Ar: FAssetArchive) {
        if (FEditorObjectVersion.get(Ar) < FEditorObjectVersion.AddedFontFaceAssets) {
            fallbackStruct = FStructFallback(Ar, FName("FontData"))
            return
        }
        if (Ar.readBoolean()) {
            val asset = FPackageIndex(Ar)
            localFontFaceAsset = asset
            if (asset.isNull()) {
                fontFilename = Ar.readString()
                hinting = EFontHinting.values().getOrElse(Ar.readUInt8().toInt()) { EFontHinting.Default }
                loadingPolicy = EFontLoadingPolicy.values().getOrElse(Ar.readUInt8().toInt()) { EFontLoadingPolicy.LazyLoad }
            }
            subFaceIndex = if (Ar.game >= GAME_UE4(20)) Ar.readInt32() else 0
        }
    }
}

enum class EFontHinting { Default, Auto, AutoLight, Monochrome, None }

enum class EFontLoadingPolicy { LazyLoad, Stream, Inline }
