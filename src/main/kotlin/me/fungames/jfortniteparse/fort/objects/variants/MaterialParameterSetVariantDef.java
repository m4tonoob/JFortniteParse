package me.fungames.jfortniteparse.fort.objects.variants;

import kotlin.Lazy;
import me.fungames.jfortniteparse.fort.exports.CustomMaterialParamSetChoices;
import me.fungames.jfortniteparse.ue4.assets.UStruct;
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTag;

@UStruct
public class MaterialParameterSetVariantDef extends BaseVariantDef {
    public FGameplayTag DefaultActiveVariantTag;
    public Lazy<CustomMaterialParamSetChoices> MaterialParameterSetChoices;
}
