package me.fungames.jfortniteparse.fort.exports;

import me.fungames.jfortniteparse.ue4.assets.UStruct;
import me.fungames.jfortniteparse.ue4.assets.exports.UPrimaryDataAsset;
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTag;
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath;

import java.util.List;

public class AthenaItemShopOfferDisplayData extends UPrimaryDataAsset {
    public List<ContextualPresentation> ContextualPresentations;

    @UStruct
    public static class ContextualPresentation {
        public FGameplayTag ProductTag;
        public FSoftObjectPath RenderImage;
        public FSoftObjectPath OverrideImageMaterial;
    }
}
