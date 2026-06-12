package me.fungames.jfortniteparse.fort.exports;

import kotlin.Lazy;
import me.fungames.jfortniteparse.fort.enums.*;
import me.fungames.jfortniteparse.ue4.assets.UProperty;
import me.fungames.jfortniteparse.ue4.assets.exports.FCurveTableRowHandle;
import me.fungames.jfortniteparse.ue4.assets.objects.FInstancedStruct;
import me.fungames.jfortniteparse.ue4.objects.FScalableFloat;
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FText;
import me.fungames.jfortniteparse.ue4.objects.core.math.FRotator;
import me.fungames.jfortniteparse.ue4.objects.core.math.FVector;
import me.fungames.jfortniteparse.ue4.assets.objects.FStructFallback;
import me.fungames.jfortniteparse.ue4.objects.gameplaytags.FGameplayTagContainer;
import me.fungames.jfortniteparse.ue4.objects.uobject.FName;
import me.fungames.jfortniteparse.ue4.objects.uobject.FPackageIndex;
import me.fungames.jfortniteparse.ue4.objects.uobject.FSoftObjectPath;

import java.util.List;

public class FortItemDefinition extends ItemDefinitionBase {
    //public MulticastInlineDelegateProperty OnItemCountChanged;
    @UProperty(skipPrevious = 1)
    public EFortRarity Rarity = EFortRarity.Uncommon;
    public EFortItemType ItemType;
    public EFortItemType PrimaryAssetIdItemTypeOverride;
    public EFortInventoryFilter FilterOverride;
    public EFortItemTier Tier;
    public EFortItemTier MaxTier;
    public EFortTemplateAccess Access;
    public Boolean bIsAccountItem;
    public Boolean bNeverPersisted;
    public Boolean bAllowMultipleStacks;
    public Boolean bAutoBalanceStacks;
    public Boolean bForceAutoPickup;
    public boolean bInventorySizeLimited = true;
    public FText ItemTypeNameOverride;
    public FText SearchTags;
    public FGameplayTagContainer GameplayTags;
    public FGameplayTagContainer AutomationTags;
    public FGameplayTagContainer SecondaryCategoryOverrideTags;
    public FGameplayTagContainer TertiaryCategoryOverrideTags;
    public FScalableFloat MaxStackSize;
    public FScalableFloat PurchaseItemLimit;
    public Float FrontendPreviewScale;
    public FSoftObjectPath /*SoftClassPath*/ TooltipClass;
    public FSoftObjectPath StatList;
    public FCurveTableRowHandle RatingLookup;
    public FSoftObjectPath WidePreviewImage;
    public FSoftObjectPath SmallPreviewImage;
    public FSoftObjectPath LargePreviewImage;
    public FSoftObjectPath DisplayAssetPath;
    public Lazy<FortItemSeriesDefinition> Series;
    public FVector FrontendPreviewPivotOffset;
    public FRotator FrontendPreviewInitialRotation;
    public FSoftObjectPath FrontendPreviewMeshOverride;
    public FSoftObjectPath FrontendPreviewSkeletalMeshOverride;
    public List<FInstancedStruct> DataList;

    /**
     * Finds a DataList component struct by name. Modern item definitions are
     * componentized: display data lives in (Fort)ItemComponentData_* instanced
     * structs instead of direct properties.
     */
    private FStructFallback dataListEntry(String structName) {
        if (DataList == null) return null;
        for (FInstancedStruct entry : DataList) {
            if (entry.getStruct() != null && structName.equals(entry.getStruct().getStructName().getText())) {
                Object structType = entry.getStruct().getStructType();
                if (structType instanceof FStructFallback) {
                    return (FStructFallback) structType;
                }
            }
        }
        return null;
    }

    /**
     * Returns GameplayTags, checking the DataList fallback where
     * tags moved to ItemComponentData_OwnedGameplayTags.
     */
    public FGameplayTagContainer getGameplayTags() {
        if (GameplayTags != null) return GameplayTags;
        FStructFallback data = dataListEntry("ItemComponentData_OwnedGameplayTags");
        return data != null ? data.getOrNull("Tags", FGameplayTagContainer.class) : null;
    }

    /**
     * Returns Rarity, checking the DataList fallback where it moved to
     * FortItemComponentData_Rarity. The direct Rarity property no longer
     * exists on current-season assets, so the field stays at its Uncommon
     * default unless this resolver is used.
     */
    public EFortRarity getRarityResolved() {
        FStructFallback data = dataListEntry("FortItemComponentData_Rarity");
        if (data != null) {
            EFortRarity rarity = data.getOrNull("Rarity", EFortRarity.class);
            if (rarity != null) return rarity;
        }
        return Rarity;
    }

    /**
     * Returns Series, checking the DataList fallback where it moved to
     * FortItemComponentData_Series.
     */
    public Lazy<FortItemSeriesDefinition> getSeriesResolved() {
        FStructFallback data = dataListEntry("FortItemComponentData_Series");
        if (data != null) {
            FPackageIndex index = data.getOrNull("Series", FPackageIndex.class);
            if (index != null && index.getOwner() != null) {
                Lazy<FortItemSeriesDefinition> series = index.getOwner().findObject(index);
                if (series != null) return series;
            }
        }
        return Series;
    }

    public FName getSet() {
        FGameplayTagContainer tags = getGameplayTags();
        return tags != null ? tags.getValue("Cosmetics.Set") : null;
    }

    public FName getSource() {
        FGameplayTagContainer tags = getGameplayTags();
        return tags != null ? tags.getValue("Cosmetics.Source") : null;
    }

    public FName getUserFacingFlags() {
        FGameplayTagContainer tags = getGameplayTags();
        return tags != null ? tags.getValue("Cosmetics.UserFacingFlags") : null;
    }

    /**
     * Returns the icon path, checking the DataList fallback where icons moved to
     * ItemComponentData_Icon / FortItemComponentData_LargeIcon.
     */
    public FSoftObjectPath getIconPathResolved(boolean large) {
        FStructFallback data = dataListEntry(large ? "FortItemComponentData_LargeIcon" : "ItemComponentData_Icon");
        if (data == null) return null;
        return data.getOrNull(large ? "LargeIcon" : "Icon", FSoftObjectPath.class);
    }
}
