package me.fungames.jfortniteparse.ue4.assets.objects

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex
import me.fungames.jfortniteparse.ue4.versions.FInstancedStructCustomVersion

class FInstancedStruct {
	companion object {
		private val NAME_StructProperty = FName("StructProperty")
	}

	var struct: UScriptStruct? = null
		private set

	constructor(Ar: FAssetArchive) {
		if (FInstancedStructCustomVersion.get(Ar) < FInstancedStructCustomVersion.CustomVersionAdded) {
			val version = Ar.readUInt8()
		}

		val structType = FPackageIndex(Ar)
		val serialSize = Ar.readInt32()
		val savedPos = Ar.pos()

		if (structType.isNull()) {
			Ar.seek(savedPos + serialSize)
			return
		}

		try {
			struct = UScriptStruct(Ar, PropertyType(NAME_StructProperty).apply {
				structName = structType.name
				structClass = Ar.provider?.mappingsProvider?.let { lazy { it.getStruct(structName) } }
			})
		} catch (e: ParserException) {
			LOG_JFP.warn("Failed to read FInstancedStruct of type ${structType.name}, skipping it", e)
		} finally {
			// serialSize gives the exact byte length of the inner struct, so always
			// realign to its end. This lets a single unparseable instanced struct be
			// skipped without corrupting the rest of the package (mirrors CUE4Parse)
			Ar.seek(savedPos + serialSize)
		}
	}

	constructor(struct: UScriptStruct?) {
		this.struct = struct
	}
}