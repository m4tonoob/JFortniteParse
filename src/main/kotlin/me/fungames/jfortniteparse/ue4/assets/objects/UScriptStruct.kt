package me.fungames.jfortniteparse.ue4.assets.objects

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.fort.objects.FFortActorRecord
import me.fungames.jfortniteparse.ue4.assets.objects.FProperty.ReadType
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.assets.writer.FAssetArchiveWriter
import me.fungames.jfortniteparse.ue4.objects.ai.navigation.FNavAgentSelector
import me.fungames.jfortniteparse.ue4.objects.core.math.*
import me.fungames.jfortniteparse.ue4.objects.core.misc.FDateTime
import me.fungames.jfortniteparse.ue4.objects.core.misc.FFrameNumber
import me.fungames.jfortniteparse.ue4.objects.core.misc.FFrameRate
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.engine.*
import me.fungames.jfortniteparse.ue4.objects.engine.animation.FSmartName
import me.fungames.jfortniteparse.ue4.objects.engine.curves.FRichCurveKey
import me.fungames.jfortniteparse.ue4.objects.engine.curves.FSimpleCurveKey
import me.fungames.jfortniteparse.ue4.objects.engine.gameframework.FUniqueNetIdRepl
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTagContainer
import me.fungames.jfortniteparse.ue4.objects.levelsequence.FLevelSequenceObjectReferenceMap
import me.fungames.jfortniteparse.ue4.objects.moviescene.FMovieSceneFrameRange
import me.fungames.jfortniteparse.ue4.objects.moviescene.channels.FMovieSceneFloatChannel
import me.fungames.jfortniteparse.ue4.objects.moviescene.channels.FMovieSceneFloatValue
import me.fungames.jfortniteparse.ue4.objects.moviescene.evaluation.*
import me.fungames.jfortniteparse.ue4.objects.niagara.FNiagaraVariable
import me.fungames.jfortniteparse.ue4.objects.niagara.FNiagaraVariableBase
import me.fungames.jfortniteparse.ue4.objects.niagara.FNiagaraVariableWithOffset
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftClassPath
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath

class UScriptStruct {
    val structName: FName
    var structType: Any

    constructor(Ar: FAssetArchive, typeData: PropertyType, type: ReadType = ReadType.NORMAL) {
        structName = typeData.structName
        val nz = type != ReadType.ZERO
        structType = when (structName.text) {
            "Box" -> if (nz) FBox(Ar) else FBox(FVector(0f, 0f, 0f), FVector(0f, 0f, 0f))
            "Box2D" -> if (nz) FBox2D(Ar) else FBox2D()
            "Box2f" -> if (nz) FBox2D.readFloats(Ar) else FBox2D()
            "Color" -> if (nz) FColor(Ar) else FColor()
            "ColorMaterialInput" -> FColorMaterialInput(Ar)
            "DateTime", "Timespan" -> if (nz) FDateTime(Ar) else FDateTime()
            "ExpressionInput" -> FExpressionInput(Ar)
            "FrameNumber" -> FFrameNumber(Ar)
            "GameplayTagContainer" -> if (nz) FGameplayTagContainer(Ar) else FGameplayTagContainer()
            "Guid", "GUID" -> if (nz) FGuid(Ar) else FGuid()
            "IntPoint", "Int32Point" -> if (nz) FIntPoint(Ar) else FIntPoint()
            "IntVector" -> if (nz) FIntVector(Ar) else FIntVector()
            "IntVector2", "Int32Vector2" -> if (nz) FIntVector2(Ar) else FIntVector2()
            "IntVector4" -> if (nz) TIntVector4(Ar.readInt32(), Ar.readInt32(), Ar.readInt32(), Ar.readInt32()) else TIntVector4(0, 0, 0, 0)
            "UintVector2", "Uint32Point" -> if (nz) TIntVector2(Ar.readUInt32(), Ar.readUInt32()) else TIntVector2(0u, 0u)
            "UintVector" -> if (nz) TIntVector3(Ar.readUInt32(), Ar.readUInt32(), Ar.readUInt32()) else TIntVector3(0u, 0u, 0u)
            "UintVector4", "Uint32Vector4" -> if (nz) TIntVector4(Ar.readUInt32(), Ar.readUInt32(), Ar.readUInt32(), Ar.readUInt32()) else TIntVector4(0u, 0u, 0u, 0u)
            "Int64Vector2", "Int64Point" -> if (nz) TIntVector2(Ar.readInt64(), Ar.readInt64()) else TIntVector2(0L, 0L)
            "Int64Vector" -> if (nz) TIntVector3(Ar.readInt64(), Ar.readInt64(), Ar.readInt64()) else TIntVector3(0L, 0L, 0L)
            "Int64Vector4" -> if (nz) TIntVector4(Ar.readInt64(), Ar.readInt64(), Ar.readInt64(), Ar.readInt64()) else TIntVector4(0L, 0L, 0L, 0L)
            "UInt64Vector2", "UInt64Point" -> if (nz) TIntVector2(Ar.readUInt64(), Ar.readUInt64()) else TIntVector2(0uL, 0uL)
            "UInt64Vector" -> if (nz) TIntVector3(Ar.readUInt64(), Ar.readUInt64(), Ar.readUInt64()) else TIntVector3(0uL, 0uL, 0uL)
            "UInt64Vector4" -> if (nz) TIntVector4(Ar.readUInt64(), Ar.readUInt64(), Ar.readUInt64(), Ar.readUInt64()) else TIntVector4(0uL, 0uL, 0uL, 0uL)
            // Fixed-precision math types ignore large world coordinates
            "Vector2f" -> if (nz) FVector2D(Ar.readFloat32(), Ar.readFloat32()) else FVector2D()
            "Vector3f" -> if (nz) FVector(Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32()) else FVector()
            "Vector3d" -> if (nz) FVector(Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat()) else FVector()
            "Vector4f" -> if (nz) FVector4(Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32()) else FVector4()
            "Vector4d" -> if (nz) FVector4(Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat()) else FVector4()
            "Rotator3f" -> if (nz) FRotator(Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32()) else FRotator()
            "Rotator3d" -> if (nz) FRotator(Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat()) else FRotator()
            "Quat4f" -> if (nz) FQuat(Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32()) else FQuat(0f, 0f, 0f, 0f)
            "Quat4d" -> if (nz) FQuat(Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat()) else FQuat(0f, 0f, 0f, 0f)
            "Plane4f" -> if (nz) FPlane(Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32()) else FPlane()
            "Plane4d" -> if (nz) FPlane(Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat()) else FPlane()
            "Sphere" -> if (nz) FSphere(Ar) else FSphere(0f, 0f, 0f, 0f)
            "Sphere3f" -> if (nz) FSphere(Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32(), Ar.readFloat32()) else FSphere(0f, 0f, 0f, 0f)
            "Sphere3d" -> if (nz) FSphere(Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat(), Ar.readDouble().toFloat()) else FSphere(0f, 0f, 0f, 0f)
            "PerPlatformFString" -> if (nz) FPerPlatformFString(Ar) else FPerPlatformFString(false, "")
            "PerPlatformFrameRate" -> if (nz) FPerPlatformFrameRate(Ar) else FPerPlatformFrameRate(false, FFrameRate(0, 0))
            "PerQualityLevelFloat" -> if (nz) FPerQualityLevelFloat(Ar) else FPerQualityLevelFloat(0f)
            "RemoteServerId" -> if (nz) Ar.readUInt32() else 0u
            "ZeroSizeProperty" -> Unit
            "LevelSequenceObjectReferenceMap" -> FLevelSequenceObjectReferenceMap(Ar)
            "LinearColor" -> if (nz) FLinearColor(Ar) else FLinearColor()
            "MaterialAttributesInput" -> FMaterialAttributesInput(Ar)
            "MovieSceneEvalTemplatePtr" -> FMovieSceneEvalTemplatePtr(Ar)
            "MovieSceneEvaluationFieldEntityTree" -> FMovieSceneEvaluationFieldEntityTree(Ar)
            "MovieSceneEvaluationKey" -> FMovieSceneEvaluationKey(Ar)
            "MovieSceneFloatChannel" -> FMovieSceneFloatChannel(Ar)
            "MovieSceneFloatValue" -> FMovieSceneFloatValue(Ar)
            "MovieSceneFrameRange" -> FMovieSceneFrameRange(Ar)
            "MovieSceneSegment" -> FMovieSceneSegment(Ar)
            "MovieSceneSegmentIdentifier" -> FFrameNumber(Ar)
            "MovieSceneSequenceID" -> FFrameNumber(Ar)
            "MovieSceneTrackIdentifier" -> FFrameNumber(Ar)
            "MovieSceneTrackImplementationPtr" -> FMovieSceneTrackImplementationPtr(Ar)
            "NavAgentSelector" -> FNavAgentSelector(Ar)
            "NiagaraVariable" -> FNiagaraVariable(Ar)
            "NiagaraVariableBase" -> FNiagaraVariableBase(Ar)
            "NiagaraVariableWithOffset" -> FNiagaraVariableWithOffset(Ar)
            "PerPlatformBool" -> if (nz) FPerPlatformBool(Ar) else FPerPlatformBool(false, false)
            "PerPlatformFloat" -> if (nz) FPerPlatformFloat(Ar) else FPerPlatformFloat(false, 0f)
            "PerPlatformInt" -> if (nz) FPerPlatformInt(Ar) else FPerPlatformInt(false, 0)
            "PerQualityLevelInt" -> if (nz) FPerQualityLevelInt(Ar) else FPerQualityLevelInt(0)
            "Plane" -> if (nz) FPlane(Ar) else FPlane()
            "Quat" -> FQuat(Ar)
            "RichCurveKey" -> FRichCurveKey(Ar)
            "Rotator" -> if (nz) FRotator(Ar) else FRotator()
            "ScalarMaterialInput" -> FScalarMaterialInput(Ar)
            "SectionEvaluationDataTree" -> FSectionEvaluationDataTree(Ar)
            "SimpleCurveKey" -> FSimpleCurveKey(Ar)
            "SkeletalMeshSamplingLODBuiltData" -> FWeightedRandomSampler(Ar)
            "SmartName" -> FSmartName(Ar)
            "SoftObjectPath" -> (if (nz) FSoftObjectPath(Ar) else FSoftObjectPath()).apply { owner = Ar.owner }
            "SoftClassPath" -> (if (nz) FSoftClassPath(Ar) else FSoftClassPath()).apply { owner = Ar.owner }
            "UniqueNetIdRepl" -> FUniqueNetIdRepl(Ar)
            "Vector" -> if (nz) FVector(Ar) else FVector()
            "Vector2D" -> if (nz) FVector2D(Ar) else FVector2D()
            "DeprecateSlateVector2D" -> if (nz) FVector2D(Ar.readFloat32(), Ar.readFloat32()) else FVector2D()
            "Vector2MaterialInput" -> FVector2MaterialInput(Ar)
            "Vector4" -> if (nz) FVector4(Ar) else FVector4()
            "VectorMaterialInput" -> FVectorMaterialInput(Ar)
            "Vector_NetQuantize" -> if (nz) FVector(Ar) else FVector()
            "Vector_NetQuantize10" -> if (nz) FVector(Ar) else FVector()
            "Vector_NetQuantize100" -> if (nz) FVector(Ar) else FVector()
            "Vector_NetQuantizeNormal" -> if (nz) FVector(Ar) else FVector()
            "InstancedStruct" -> FInstancedStruct(Ar)

            "FortActorRecord" -> FFortActorRecord(Ar)

            else -> {
                LOG_JFP.debug { "Using property serialization for struct $structName" }
                //TODO this should in theory map the struct fallbacks directly to their target, not implemented yet
                //For now it will be done with the getTagTypeValue method, not optimal though
                FStructFallback(Ar, typeData.structClass, structName)
            }
        }
    }

    fun serialize(Ar: FAssetArchiveWriter) {
        when (val structType = structType) {
            is FBox -> structType.serialize(Ar)
            is FBox2D -> structType.serialize(Ar)
            is FColor -> structType.serialize(Ar)
            is FColorMaterialInput -> structType.serialize(Ar)
            is FDateTime -> structType.serialize(Ar)
            is FExpressionInput -> structType.serialize(Ar)
            is FFrameNumber -> structType.serialize(Ar)
            is FGameplayTagContainer -> structType.serialize(Ar)
            is FGuid -> structType.serialize(Ar)
            is FIntPoint -> structType.serialize(Ar)
            is FIntVector -> structType.serialize(Ar)
            is FIntVector2 -> structType.serialize(Ar)
            is FLevelSequenceObjectReferenceMap -> structType.serialize(Ar)
            is FLinearColor -> structType.serialize(Ar)
            is FMaterialAttributesInput -> structType.serialize(Ar)
            is FMovieSceneEvaluationKey -> structType.serialize(Ar)
            is FMovieSceneFrameRange -> structType.serialize(Ar)
            is FMovieSceneSegment -> structType.serialize(Ar)
            is FNavAgentSelector -> structType.serialize(Ar)
            is FNiagaraVariable -> structType.serialize(Ar)
            is FNiagaraVariableBase -> structType.serialize(Ar)
            is FNiagaraVariableWithOffset -> structType.serialize(Ar)
            is FPerPlatformBool -> structType.serialize(Ar)
            is FPerPlatformFloat -> structType.serialize(Ar)
            is FPerPlatformInt -> structType.serialize(Ar)
            is FQuat -> structType.serialize(Ar)
            is FRichCurveKey -> structType.serialize(Ar)
            is FRotator -> structType.serialize(Ar)
            is FScalarMaterialInput -> structType.serialize(Ar)
            is FSectionEvaluationDataTree -> structType.serialize(Ar)
            is FSimpleCurveKey -> structType.serialize(Ar)
            is FSmartName -> structType.serialize(Ar)
            is FSoftObjectPath -> structType.serialize(Ar)
            is FStructFallback -> structType.serialize(Ar)
            is FVector -> structType.serialize(Ar)
            is FVector2D -> structType.serialize(Ar)
            is FVector2MaterialInput -> structType.serialize(Ar)
            is FVector4 -> structType.serialize(Ar)
            is FVectorMaterialInput -> structType.serialize(Ar)
            is FWeightedRandomSampler -> structType.serialize(Ar)
        }
    }

    constructor(structName: FName, structType: Any) {
        this.structName = structName
        this.structType = structType
    }
}