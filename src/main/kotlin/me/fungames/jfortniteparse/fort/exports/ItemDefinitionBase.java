package me.fungames.jfortniteparse.fort.exports;

import me.fungames.jfortniteparse.fort.objects.ItemComponentContainer;
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FText;
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FTextHistory;

public class ItemDefinitionBase extends McpItemDefinitionBase {
    public FText ItemName;
    public FText ItemDescription;
    public FText ItemShortDescription;
    public ItemComponentContainer ComponentContainer;
    
    public boolean isItemNameBlankPlaceholder() {
        if (ItemName == null) return false;
        FTextHistory history = ItemName.getTextHistory();
        return history instanceof FTextHistory.Base
            && "Blank".equals(((FTextHistory.Base) history).getSourceString());
    }
}
