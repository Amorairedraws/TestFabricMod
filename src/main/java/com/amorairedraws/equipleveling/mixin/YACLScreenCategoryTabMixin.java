package com.amorairedraws.equipleveling.mixin;

import dev.isxander.yacl3.gui.SearchFieldWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/**
 * Removes the search field from every YACL config tab. YACL 3.8.x always adds a
 * {@link SearchFieldWidget} to {@code CategoryTab#forEachChild}, and there is no
 * public API to disable it. Wrapping the child consumer filters the search field
 * out, so it is never added to the screen (and therefore never rendered or
 * interacted with).
 */
@Mixin(YACLScreen.CategoryTab.class)
public abstract class YACLScreenCategoryTabMixin {
    @ModifyVariable(method = "forEachChild", at = @At("HEAD"), argsOnly = true)
    private Consumer<ClickableWidget> equipLeveling$hideSearchField(Consumer<ClickableWidget> consumer) {
        return widget -> {
            if (!(widget instanceof SearchFieldWidget)) {
                consumer.accept(widget);
            }
        };
    }
}
