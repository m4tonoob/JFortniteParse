package me.fungames.jfortniteparse.fort.exports.variants;

import me.fungames.jfortniteparse.fort.objects.variants.BaseVariantDef;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FortCosmeticMorphTargetVariant extends FortCosmeticVariantBackedByArray {
    public List<BaseVariantDef> MorphTargetOptions;

    @Nullable
    @Override
    public List<? extends BaseVariantDef> getVariants() {
        return MorphTargetOptions;
    }
}
