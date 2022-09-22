package com.kevinthegreat.organizableplayscreens.mixin;

import com.kevinthegreat.organizableplayscreens.FolderEntry;
import com.kevinthegreat.organizableplayscreens.MultiplayerServerListWidgetAccessor;
import com.kevinthegreat.organizableplayscreens.screen.EditFolderScreen;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    @Shadow
    private ServerInfo selectedEntry;
    @Shadow
    public MultiplayerServerListWidget serverListWidget;
    @Shadow
    private ButtonWidget buttonJoin;
    @Shadow
    private ButtonWidget buttonEdit;
    @Shadow
    private ButtonWidget buttonDelete;

    @Shadow
    public abstract void select(MultiplayerServerListWidget.Entry entry);

    @Shadow
    @Final
    private Screen parent;
    public MultiplayerServerListWidgetAccessor serverListWidgetAccessor;
    private ButtonWidget organizableplayscreens_buttonCancel;
    private ButtonWidget organizableplayscreens_buttonMoveEntryBack;
    @Nullable
    private FolderEntry organizableplayscreens_newFolder;

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget;setServers(Lnet/minecraft/client/option/ServerList;)V", shift = At.Shift.AFTER))
    private void organizableplayscreens_loadFile(CallbackInfo ci) {
        serverListWidgetAccessor = (MultiplayerServerListWidgetAccessor) serverListWidget;
        serverListWidgetAccessor.organizableplayscreens_loadFile();
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerScreen;updateButtonActivationStates()V"))
    private void organizableplayscreens_addButtons(CallbackInfo ci) {
        addDrawableChild(new ButtonWidget(8, 8, 20, 20, Text.of("←"), buttonWidget -> {
            if (!serverListWidgetAccessor.organizableplayscreens_setCurrentFolderToParent()) {
                client.setScreen(parent);
            }
        }));
        organizableplayscreens_buttonMoveEntryBack = addDrawableChild(new ButtonWidget(36, 8, 20, 20, Text.of("←+"), buttonWidget -> {
            if (!serverListWidgetAccessor.organizableplayscreens_isRootFolder()) {
                MultiplayerServerListWidget.Entry entry = serverListWidget.getSelectedOrNull();
                if (entry != null) {
                    FolderEntry parentFolder = serverListWidgetAccessor.organizableplayscreens_getCurrentFolder().getParent();
                    if (entry instanceof FolderEntry folderEntry) {
                        folderEntry.setParent(parentFolder);
                    }
                    parentFolder.getEntries().add(entry);
                    serverListWidgetAccessor.organizableplayscreens_getCurrentEntries().remove(entry);
                    serverListWidgetAccessor.organizableplayscreens_updateAndSave();
                }
            }
        }, new ButtonWidget.TooltipSupplier() {
            private static final Text MOVE_ENTRY_BACK_TOOLTIP = Text.translatable("organizableplayscreens:folder.moveBack");

            @Override
            public void onTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
                if (button.isHovered()) {
                    renderOrderedTooltip(matrices, textRenderer.wrapLines(MOVE_ENTRY_BACK_TOOLTIP, width / 2), mouseX, mouseY);
                }
            }

            @Override
            public void supply(Consumer<Text> consumer) {
                consumer.accept(MOVE_ENTRY_BACK_TOOLTIP);
            }
        }));
        addDrawableChild(new ButtonWidget(width - 28, 8, 20, 20, Text.of("+"), buttonWidget -> {
            organizableplayscreens_newFolder = new FolderEntry((MultiplayerScreen) (Object) this, serverListWidgetAccessor.organizableplayscreens_getCurrentFolder());
            client.setScreen(new EditFolderScreen(this::organizableplayscreens_addFolder, organizableplayscreens_newFolder, true));
            select(organizableplayscreens_newFolder);
        }));
    }

    @Inject(method = "method_19915", at = @At(value = "RETURN"))
    private void organizableplayscreens_modifyEditButton(ButtonWidget buttonWidget, CallbackInfo ci) {
        if (serverListWidget.getSelectedOrNull() instanceof FolderEntry folderEntry) {
            client.setScreen(new EditFolderScreen(this::organizableplayscreens_editFolder, folderEntry, false));
        }
    }

    @Inject(method = "method_19914", at = @At(value = "RETURN"))
    private void organizableplayscreens_modifyDeleteButton(ButtonWidget buttonWidget, CallbackInfo ci) {
        if (serverListWidget.getSelectedOrNull() instanceof FolderEntry folderEntry) {
            client.setScreen(new ConfirmScreen(this::organizableplayscreens_deleteFolder, Text.translatable("organizableplayscreens:folder.deleteFolderQuestion"), Text.translatable("organizableplayscreens:folder.deleteFolderWarning", folderEntry.getName()), Text.translatable("selectServer.deleteButton"), ScreenTexts.CANCEL));
        }
    }

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerScreen;addDrawableChild(Lnet/minecraft/client/gui/Element;)Lnet/minecraft/client/gui/Element;", ordinal = 6))
    private <T extends Element & Drawable & Selectable> T organizableplayscreens_setCancelButton(MultiplayerScreen instance, T element) {
        organizableplayscreens_buttonCancel = addDrawableChild((ButtonWidget) element);
        return element;
    }

    @Inject(method = "method_19912", at = @At(value = "HEAD"), cancellable = true)
    private void organizableplayscreens_modifyCancelButton(ButtonWidget buttonWidget, CallbackInfo ci) {
        if (serverListWidgetAccessor.organizableplayscreens_setCurrentFolderToParent()) {
            ci.cancel();
        }
    }

    private void organizableplayscreens_addFolder(boolean confirmedAction) {
        if (confirmedAction) {
            serverListWidgetAccessor.organizableplayscreens_getCurrentEntries().add(organizableplayscreens_newFolder);
            serverListWidgetAccessor.organizableplayscreens_updateAndSave();
            organizableplayscreens_newFolder = null;
        }
        client.setScreen(this);
    }

    private void organizableplayscreens_editFolder(boolean confirmedAction) {
        client.setScreen(this);
    }

    private void organizableplayscreens_deleteFolder(boolean confirmedAction) {
        if (confirmedAction) {
            serverListWidgetAccessor.organizableplayscreens_getCurrentEntries().remove(serverListWidget.getSelectedOrNull());
            serverListWidget.setSelected(null);
            serverListWidgetAccessor.organizableplayscreens_updateAndSave();
        }
        client.setScreen(this);
    }

    @Inject(method = "addEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget;setServers(Lnet/minecraft/client/option/ServerList;)V", shift = At.Shift.AFTER))
    private void organizableplayscreens_addServer(boolean confirmedAction, CallbackInfo ci) {
        serverListWidgetAccessor.organizableplayscreens_getCurrentEntries().add(serverListWidget.new ServerEntry((MultiplayerScreen) (Object) this, selectedEntry));
        serverListWidgetAccessor.organizableplayscreens_updateAndSave();
    }

    @Inject(method = "editEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget;setServers(Lnet/minecraft/client/option/ServerList;)V", shift = At.Shift.AFTER))
    private void organizableplayscreens_editServer(boolean confirmedAction, CallbackInfo ci) {
        serverListWidgetAccessor.organizableplayscreens_getCurrentEntries().set(serverListWidgetAccessor.organizableplayscreens_getCurrentEntries().indexOf(serverListWidget.getSelectedOrNull()), serverListWidget.new ServerEntry((MultiplayerScreen) (Object) this, selectedEntry));
        serverListWidgetAccessor.organizableplayscreens_updateAndSave();
    }

    @Inject(method = "removeEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget;setSelected(Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget$Entry;)V"))
    private void organizableplayscreens_removeServer(boolean confirmedAction, CallbackInfo ci) {
        serverListWidgetAccessor.organizableplayscreens_getCurrentEntries().remove(serverListWidget.getSelectedOrNull());
        serverListWidgetAccessor.organizableplayscreens_updateAndSave();
    }

    @Inject(method = "connect()V", at = @At(value = "RETURN"))
    private void organizableplayscreens_openFolder(CallbackInfo ci) {
        if (serverListWidget.getSelectedOrNull() instanceof FolderEntry folderEntry) {
            serverListWidgetAccessor.organizableplayscreens_setCurrentFolder(folderEntry);
        }
    }

    @Inject(method = "keyPressed", at = @At(value = "HEAD"), cancellable = true)
    private void organizableplayscreens_keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !shouldCloseOnEsc() && serverListWidgetAccessor.organizableplayscreens_setCurrentFolderToParent()) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerScreen;drawCenteredText(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", shift = At.Shift.AFTER))
    private void organizableplayscreens_renderPath(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        drawCenteredText(matrices, textRenderer, serverListWidgetAccessor.organizableplayscreens_getCurrentPath(), width / 2, 6, 0xa0a0a0);
    }

    @Inject(method = "updateButtonActivationStates", at = @At(value = "RETURN"))
    private void organizableplayscreens_updateButtonActivationStates(CallbackInfo ci) {
        MultiplayerServerListWidget.Entry selectedEntry = serverListWidget.getSelectedOrNull();
        if (selectedEntry instanceof MultiplayerServerListWidget.ServerEntry) {
            buttonJoin.setMessage(Text.translatable("selectServer.select"));
        } else if (selectedEntry instanceof FolderEntry) {
            buttonJoin.setMessage(Text.translatable("organizableplayscreens:folder.openFolder"));
            buttonJoin.active = true;
            buttonEdit.active = true;
            buttonDelete.active = true;
        }
        organizableplayscreens_buttonCancel.setMessage(serverListWidgetAccessor.organizableplayscreens_isRootFolder() ? ScreenTexts.CANCEL : ScreenTexts.BACK);
        organizableplayscreens_buttonMoveEntryBack.active = !serverListWidgetAccessor.organizableplayscreens_isRootFolder() && serverListWidget.getSelectedOrNull() != null;
        for (MultiplayerServerListWidget.Entry entry : serverListWidgetAccessor.organizableplayscreens_getCurrentEntries()) {
            if (entry instanceof FolderEntry folderEntry) {
                folderEntry.updateButtonActivationStates();
            }
        }
    }

    @Override
    public void removed() {
        serverListWidgetAccessor.organizableplayscreens_saveFile();
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return serverListWidgetAccessor.organizableplayscreens_isRootFolder();
    }
}
