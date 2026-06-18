package me.fungames.jfortniteparse.fort.exports;

import me.fungames.jfortniteparse.ue4.objects.FScalableFloat;
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FText;

// Generic definition for all extractables (Sprites and relics). The parser fills these public
// fields by name. Name, description, rarity, and icon are inherited from FortItemDefinition.
public class ExtractableItemDefinition extends FortItemDefinition {
    public Integer DexNumber;              // Sprite collection number
    public FScalableFloat LevelUpExpCurve; // Points at the FortExtractableSpriteLevelUpRates curve
    public FText AcquisitionHintText;      // For example "Found rarely in Sprite Chests"
}
