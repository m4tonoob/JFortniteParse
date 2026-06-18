package me.fungames.jfortniteparse.fort.exports;

import kotlin.Lazy;
import me.fungames.jfortniteparse.ue4.assets.UStruct;
import me.fungames.jfortniteparse.ue4.assets.exports.UObject;
import me.fungames.jfortniteparse.ue4.objects.FScalableFloat;
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FText;

import java.util.List;

public class ExtractableItemDefinition extends FortItemDefinition {
    public Integer DexNumber;              // Sprite collection number
    public FScalableFloat LevelUpExpCurve; // Points at the FortExtractableSpriteLevelUpRates curve
    public FText AcquisitionHintText;      // For example "Found rarely in Sprite Chests"
    public List<FFortItemAugment> PossibleAugments; // Style variants list their bonus boon(s) here

    // One granted boon. A style variant's perk boon lives under a /VariantBoons/ path; its boon
    // def's ItemDescription is the player facing perk text.
    @UStruct
    public static class FFortItemAugment {
        public Float Chance;
        public Lazy<UObject> Augment;
    }
}
