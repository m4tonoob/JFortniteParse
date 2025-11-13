package me.fungames.jfortniteparse.fort.exports.variants;

import me.fungames.jfortniteparse.fort.objects.variants.BaseVariantDef;
import me.fungames.jfortniteparse.fort.objects.variants.ContextualAnimSceneEmoteVariantDef;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FortCosmeticContextualAnimSceneEmoteVariant extends FortCosmeticVariantBackedByArray {
    public List<ContextualAnimSceneEmoteVariantDef> ContextualAnimSceneEmoteOptions;

    @Nullable
    @Override
    public List<? extends BaseVariantDef> getVariants() {
        return ContextualAnimSceneEmoteOptions;
    }
}
