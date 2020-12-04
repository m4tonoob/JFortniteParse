package me.fungames.jfortniteparse.fort.exports;

import me.fungames.jfortniteparse.ue4.assets.UStruct;
import me.fungames.jfortniteparse.ue4.assets.exports.UDataAsset;
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTag;
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTagContainer;
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex;

import java.util.List;

public class FortItemDefToItemVariantDataMapping extends UDataAsset {
    public List<ItemDefToItemVariantDataMapping> ItemDefToItemVariantDataMappings;

    @UStruct
    public static class ItemDefToItemVariantDataMapping {
        public FGameplayTagContainer ItemDefinitionTags;
        public FPackageIndex /*FortItemVariantData*/ ItemVariantData;
        public FGameplayTag ItemVariantTag;
    }
}
