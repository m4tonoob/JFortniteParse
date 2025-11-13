package me.fungames.jfortniteparse.fort.exports.variants;

import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex;
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath;

import java.util.List;

/**
 * Variant type that redirects to external item definitions.
 * Used for Sidekick Reactions which reference CosmeticCompanionReactFXItemDefinition items.
 */
public class FortCosmeticItemDefRedirectVariant extends FortCosmeticVariant {
    public FPackageIndex ItemDefClass;
    public List<FPackageIndex> ItemsToForceShow;
    public FSoftObjectPath DefaultOptionPreviewImage;
}
