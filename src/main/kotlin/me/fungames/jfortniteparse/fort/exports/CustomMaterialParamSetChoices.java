package me.fungames.jfortniteparse.fort.exports;

import me.fungames.jfortniteparse.ue4.assets.UStruct;
import me.fungames.jfortniteparse.ue4.assets.exports.UObject;
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FText;
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTag;

import java.util.List;

public class CustomMaterialParamSetChoices extends UObject {
    public List<MaterialParamSetChoice> Choices;

    @UStruct
    public static class MaterialParamSetChoice {
        public FGameplayTag CustomizationVariantTag;
        public FText DisplayName;
    }
}
