package me.fungames.jfortniteparse.fort.exports.variants;

import me.fungames.jfortniteparse.fort.objects.variants.BaseVariantDef;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FortCosmeticAdditivePoseVariant extends FortCosmeticVariantBackedByArray {
    public List<BaseVariantDef> AdditivePoseOptions;

    @Nullable
    @Override
    public List<? extends BaseVariantDef> getVariants() {
        return AdditivePoseOptions;
    }
}
