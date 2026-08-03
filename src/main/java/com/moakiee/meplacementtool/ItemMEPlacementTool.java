package com.moakiee.meplacementtool;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IFacadeItem;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.parts.PartPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Places blocks, AE parts, facades and source fluids directly from a linked ME network. */
public class ItemMEPlacementTool extends BasePlacementToolItem implements IMenuItem {

    public ItemMEPlacementTool(Item.Properties properties) {
        super(() -> Config.mePlacementToolEnergyCapacity, properties);
    }

    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack itemStack, BlockPos pos) {
        return new PlacementToolMenuHost(player, inventorySlot, itemStack, (p, subMenu) -> p.closeContainer());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        InteractionHand hand = context.getHand();
        ItemStack tool = player.getItemInHand(hand);
        double energyCost = Config.mePlacementToolEnergyCost;
        if (!hasPower(player, energyCost, tool)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return InteractionResult.FAIL;
        }

        LinkedNetwork linked = getLinkedNetwork(tool, level, player);
        if (linked == null) {
            return InteractionResult.FAIL;
        }
        IGrid grid = linked.grid();
        IStorageService storageService = grid.getStorageService();
        MEStorage storage = storageService.getInventory();
        IActionSource source = IActionSource.ofPlayer(player, linked.accessPoint());

        CompoundTag config = WandNbt.getConfig(tool);
        int selected = WandNbt.getSelectedSlot(config);
        ItemStack target = WandNbt.readInventory(config).getStackInSlot(selected);
        if (target.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_configured_item"), true);
            return InteractionResult.FAIL;
        }

        AEFluidKey wrappedFluid = unwrapFluid(target);
        if (wrappedFluid != null) {
            return placeFluidFromNetwork(level, player, tool, context, energyCost, storage, source,
                    wrappedFluid, wrappedFluid.getFluid());
        }

        String configuredFluid = readConfiguredFluid(config, selected);
        if (configuredFluid != null) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(configuredFluid));
            if (fluid == null || fluid == Fluids.EMPTY || fluid.getBucket() == net.minecraft.world.item.Items.AIR) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                return InteractionResult.FAIL;
            }
            return placeFluidFromNetwork(level, player, tool, context, energyCost, storage, source,
                    AEFluidKey.of(fluid), fluid);
        }

        AEItemKey key = Config.findMatchingKey(storageService, target, source);
        if (key == null || storage.extract(key, 1L, Actionable.SIMULATE, source) < 1L) {
            AEItemKey craftKey = AEItemKey.of(target);
            if (craftKey != null && grid.getCraftingService().isCraftable(craftKey)) {
                openCraftingMenu(player, tool, craftKey, 1);
                return InteractionResult.sidedSuccess(false);
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing",
                    target.getHoverName()), true);
            return InteractionResult.FAIL;
        }

        if (MemoryCardHelper.hasConfiguredMemoryCard(player)) {
            var resources = MemoryCardHelper.checkResourcesForMultipleBlocks(player, grid, 1, source);
            if (!resources.sufficient) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.missing_resources",
                        resources.getMissingItemsMessage()), false);
                return InteractionResult.FAIL;
            }
        }

        long reserved = storage.extract(key, 1L, Actionable.MODULATE, source);
        if (reserved != 1L) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.failed_extract"), true);
            MEPlacementToolMod.LOGGER.error("ME placement reservation failed: player={}, key={}, extracted={}",
                    player.getGameProfile().getName(), key, reserved);
            return InteractionResult.FAIL;
        }

        PlacementResult placement;
        try {
            placement = placeReservedItem(level, player, hand, context, key);
        } catch (Throwable error) {
            refundReservedItem(storage, source, key, player, context.getClickedPos());
            MEPlacementToolMod.LOGGER.error("ME placement exception: player=" + player.getGameProfile().getName()
                    + ", key=" + key + ", pos=" + context.getClickedPos(), error);
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.FAIL;
        }

        if (!placement.placed()) {
            refundReservedItem(storage, source, key, player, context.getClickedPos());
            MEPlacementToolMod.LOGGER.info("ME placement rejected: player={}, key={}, pos={}, branch={}",
                    player.getGameProfile().getName(), key, context.getClickedPos(), placement.branch());
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.FAIL;
        }

        if (!usePower(player, energyCost, player.getItemInHand(hand))) {
            MEPlacementToolMod.LOGGER.error("ME placement succeeded but power extraction failed: player={}, key={}, pos={}",
                    player.getGameProfile().getName(), key, placement.pos());
        }
        if (MemoryCardHelper.hasConfiguredMemoryCard(player)) {
            if (placement.block()) {
                MemoryCardHelper.applyMemoryCardToBlock(player, level, placement.pos(), true, grid, source);
            } else if (placement.part() != null) {
                MemoryCardHelper.applyMemoryCardToPart(player, placement.part(), true, grid, source);
            }
        }
        return InteractionResult.sidedSuccess(false);
    }

    private PlacementResult placeReservedItem(Level level, ServerPlayer player, InteractionHand hand,
            UseOnContext context, AEItemKey key) {
        ItemStack placementStack = key.toStack(1);
        Item item = placementStack.getItem();
        ItemStack original = player.getItemInHand(hand);
        player.setItemInHand(hand, placementStack);
        try {
            if (item instanceof BlockItem blockItem) {
                BlockPlaceContext placeContext = new BlockPlaceContext(level, player, hand, placementStack,
                        new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                                context.getClickedPos(), context.isInside()));
                BlockPos position = placeContext.getClickedPos();
                InteractionResult result = blockItem.place(placeContext);
                return new PlacementResult(result.consumesAction(), position, true, null, "block");
            }
            if (item instanceof IPartItem<?> partItem) {
                var placement = PartPlacement.getPartPlacement(player, level, placementStack,
                        context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
                if (placement == null) {
                    return PlacementResult.failed("part-target");
                }
                @SuppressWarnings({ "rawtypes", "unchecked" })
                IPart part = PartPlacement.placePart(player, level, (IPartItem) partItem,
                        placementStack.getTag(), placement.pos(), placement.side());
                return new PlacementResult(part != null, placement.pos(), false, part, "part");
            }
            if (item instanceof IFacadeItem facadeItem) {
                var facade = facadeItem.createPartFromItemStack(placementStack, context.getClickedFace());
                var host = appeng.api.parts.PartHelper.getPartHost(level, context.getClickedPos());
                if (facade == null || host == null || host.getPart(null) == null
                        || !host.getFacadeContainer().canAddFacade(facade)
                        || !host.getFacadeContainer().addFacade(facade)) {
                    return PlacementResult.failed("facade-target");
                }
                host.markForSave();
                host.markForUpdate();
                return new PlacementResult(true, context.getClickedPos(), false, null, "facade");
            }
            return PlacementResult.failed("unsupported-item");
        } finally {
            player.setItemInHand(hand, original);
        }
    }

    private void refundReservedItem(MEStorage storage, IActionSource source, AEItemKey key,
            ServerPlayer player, BlockPos position) {
        long inserted = storage.insert(key, 1L, Actionable.MODULATE, source);
        if (inserted >= 1L) {
            return;
        }
        ItemStack remainder = key.toStack(1);
        if (!player.getInventory().add(remainder)) {
            player.level().addFreshEntity(new ItemEntity(player.level(), position.getX() + 0.5,
                    position.getY() + 0.5, position.getZ() + 0.5, remainder));
        }
        MEPlacementToolMod.LOGGER.error("ME placement rollback could not return item to network: player={}, key={}, pos={}",
                player.getGameProfile().getName(), key, position);
    }

    private InteractionResult placeFluidFromNetwork(Level level, ServerPlayer player, ItemStack tool,
            UseOnContext context, double energyCost, MEStorage storage, IActionSource source,
            AEFluidKey key, Fluid fluid) {
        long blockAmount = AEFluidKey.AMOUNT_BLOCK;
        if (storage.extract(key, blockAmount, Actionable.SIMULATE, source) < blockAmount) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing",
                    key.getDisplayName()), true);
            return InteractionResult.FAIL;
        }
        if (storage.extract(key, blockAmount, Actionable.MODULATE, source) != blockAmount) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.failed_extract"), true);
            return InteractionResult.FAIL;
        }

        BlockPos position = context.getClickedPos().relative(context.getClickedFace());
        boolean placed = false;
        try {
            var state = level.getBlockState(position);
            boolean replaceable = state.isAir() || state.canBeReplaced(fluid);
            boolean liquidContainer = state.getBlock() instanceof LiquidBlockContainer;
            boolean containerAccepts = liquidContainer && ((LiquidBlockContainer) state.getBlock())
                    .canPlaceLiquid(level, position, state, fluid);
            boolean validSource = fluid instanceof FlowingFluid && !key.hasTag();
            if (validSource && (replaceable || containerAccepts)) {
                if (level.dimensionType().ultraWarm() && fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                    level.playSound(null, position, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 2.6F);
                    placed = true;
                } else if (containerAccepts && fluid == Fluids.WATER) {
                    placed = ((LiquidBlockContainer) state.getBlock()).placeLiquid(level, position, state,
                            ((FlowingFluid) fluid).getSource(false));
                } else {
                    if (!state.isAir() && !state.liquid()) {
                        level.destroyBlock(position, true);
                    }
                    placed = level.setBlock(position, fluid.defaultFluidState().createLegacyBlock(),
                            net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
                }
            }
        } catch (Throwable error) {
            MEPlacementToolMod.LOGGER.error("ME fluid placement exception: player=" + player.getGameProfile().getName()
                    + ", key=" + key + ", pos=" + position, error);
        }

        if (!placed) {
            long returned = storage.insert(key, blockAmount, Actionable.MODULATE, source);
            if (returned != blockAmount) {
                MEPlacementToolMod.LOGGER.error("ME fluid rollback incomplete: player={}, key={}, returned={}, expected={}",
                        player.getGameProfile().getName(), key, returned, blockAmount);
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.FAIL;
        }

        if (!usePower(player, energyCost, tool)) {
            MEPlacementToolMod.LOGGER.error("ME fluid placement succeeded but power extraction failed: player={}, key={}, pos={}",
                    player.getGameProfile().getName(), key, position);
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Nullable
    private static AEFluidKey unwrapFluid(ItemStack target) {
        try {
            GenericStack stack = GenericStack.unwrapItemStack(target);
            return stack != null && stack.what() instanceof AEFluidKey fluidKey ? fluidKey : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static String readConfiguredFluid(CompoundTag config, int selected) {
        if (!config.contains("fluids")) {
            return null;
        }
        String value = config.getCompound("fluids").getString(Integer.toString(selected));
        return value.isBlank() ? null : value;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var hit = player.pick(5.0D, 0.0F, false);
        if (hit != null && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return new InteractionResultHolder<>(InteractionResult.PASS, stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CompoundTag config = WandNbt.getConfig(stack);
            var handler = WandNbt.readInventory(config);
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((windowId, inventory, ignored) ->
                            new WandMenu(windowId, inventory, handler, hand), Component.empty()),
                    buffer -> {
                        buffer.writeNbt(config);
                        buffer.writeEnum(hand);
                    });
        }
        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide), stack);
    }

    private record PlacementResult(boolean placed, BlockPos pos, boolean block, @Nullable IPart part, String branch) {
        private static PlacementResult failed(String branch) {
            return new PlacementResult(false, BlockPos.ZERO, false, null, branch);
        }
    }
}
