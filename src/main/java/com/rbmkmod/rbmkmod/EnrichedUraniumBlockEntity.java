package com.rbmkmod.rbmkmod;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EnrichedUraniumBlockEntity extends BlockEntity implements IMekanismChemicalHandler {
    public static final double ROOM_TEMPERATURE = 295.15;
    public static final double EXPLOSION_THRESHOLD = 2773.15;

    private double temperature = ROOM_TEMPERATURE;

    private final FluidTank waterTank = new FluidTank(10_000) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final IChemicalTank steamTank = BasicChemicalTank.create(10_000, this::setChanged);

    public EnrichedUraniumBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ENRICHED_URANIUM_REACTOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnrichedUraniumBlockEntity blockEntity) {
        if (level == null || level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        blockEntity.pushSteam(serverLevel, pos); // Najpierw próbujemy wypchnąć parę do sieci
        blockEntity.handleBoiling(serverLevel, pos); // Dopiero potem gotujemy wodę

        if (blockEntity.temperature > ROOM_TEMPERATURE) {
            blockEntity.temperature = Math.max(ROOM_TEMPERATURE, blockEntity.temperature - 0.1);
        }

        if (blockEntity.temperature >= EXPLOSION_THRESHOLD) {
            blockEntity.explode(serverLevel, pos);
            return;
        }

        if (blockEntity.temperature > 600.0 && level.random.nextFloat() < 0.3f) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    2, 0.1, 0.1, 0.1, 0.02);
        }
    }

    public void receiveNeutron(double energyEv, ServerLevel level) {
        double heatGenerated = (energyEv > 100_000) ? 15.0 : 35.0;
        this.temperature += heatGenerated;

        int newNeutrons = 2 + level.random.nextInt(2);
        for (int i = 0; i < newNeutrons; i++) {
            Vec3 dir = new Vec3(
                    level.random.nextDouble() - 0.5,
                    level.random.nextDouble() - 0.5,
                    level.random.nextDouble() - 0.5
            ).normalize().scale(0.5);

            Vec3 spawnPos = Vec3.atCenterOf(this.worldPosition);
            NuclearParticle particle = new NuclearParticle(
                    NuclearParticle.Type.NEUTRON,
                    spawnPos,
                    dir,
                    150,
                    this.worldPosition
            );
            NuclearParticleManager.add(level, particle);
        }
        setChanged();
    }

    private void handleBoiling(ServerLevel level, BlockPos pos) {
        if (this.temperature >= 373.15 && !waterTank.isEmpty()) {
            int maxWaterToBoil = (int) Math.min(waterTank.getFluidAmount(), Math.min(20, (this.temperature - 373.15) / 10));
            if (maxWaterToBoil > 0) {
                // Symulujemy dodanie pary, aby sprawdzić ile wolnego miejsca ma zbiornik
                long requestedSteam = maxWaterToBoil * 10L;
                ChemicalStack potentialSteam = new ChemicalStack(MekanismChemicals.STEAM, requestedSteam);

                ChemicalStack remainder = steamTank.insert(potentialSteam, Action.SIMULATE, AutomationType.INTERNAL);
                long acceptedSteam = requestedSteam - remainder.getAmount();

                // Obliczamy ile wody przeliczy się na faktycznie przyjętą parę
                int actualWaterToBoil = (int) (acceptedSteam / 10);

                if (actualWaterToBoil > 0) {
                    waterTank.drain(actualWaterToBoil, IFluidHandler.FluidAction.EXECUTE);

                    ChemicalStack steamToInsert = new ChemicalStack(MekanismChemicals.STEAM, actualWaterToBoil * 10L);
                    steamTank.insert(steamToInsert, Action.EXECUTE, AutomationType.INTERNAL);

                    // Odpowiadające chłodzenie tylko dla faktycznie odparowanej wody
                    this.temperature -= actualWaterToBoil * 0.5;

                    level.sendParticles(ParticleTypes.BUBBLE_POP,
                            pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                            2, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }

        // Zewnętrzne chłodzenie bloku bezpośrednią wodą stojącą obok
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getFluidState().is(Fluids.WATER) || neighborState.is(Blocks.WATER)) {
                if (this.temperature > 373.15) {
                    this.temperature = Math.max(ROOM_TEMPERATURE, this.temperature - 2.5);
                    level.sendParticles(ParticleTypes.BUBBLE_POP,
                            neighborPos.getX() + 0.5, neighborPos.getY() + 0.5, neighborPos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }

        setChanged();
    }

    private void pushSteam(ServerLevel level, BlockPos pos) {
        if (steamTank.isEmpty()) return;

        for (Direction dir : Direction.values()) {
            if (steamTank.isEmpty()) break;

            BlockPos neighborPos = pos.relative(dir);
            IChemicalHandler neighborHandler = level.getCapability(Capabilities.CHEMICAL.block(), neighborPos, dir.getOpposite());

            if (neighborHandler != null) {
                long amountToPush = Math.min(steamTank.getStack().getAmount(), 1000);
                ChemicalStack stackToPush = new ChemicalStack(MekanismChemicals.STEAM, amountToPush);

                ChemicalStack remainder = neighborHandler.insertChemical(stackToPush, Action.EXECUTE);

                long inserted = amountToPush - remainder.getAmount();
                if (inserted > 0) {
                    steamTank.extract(inserted, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
    }

    private void explode(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                6.0f, true, Level.ExplosionInteraction.TNT);

        for (int i = 0; i < 20; i++) {
            Vec3 dir = new Vec3(
                    level.random.nextDouble() - 0.5,
                    level.random.nextDouble() - 0.5,
                    level.random.nextDouble() - 0.5
            ).normalize().scale(0.5);

            NuclearParticle radiation = new NuclearParticle(
                    NuclearParticle.Type.GAMMA,
                    Vec3.atCenterOf(pos),
                    dir,
                    40,
                    pos
            );
            radiation.show(level);
        }
    }

    @Override
    public List<IChemicalTank> getChemicalTanks(@Nullable Direction side) {
        return List.of(steamTank);
    }

    @Override
    public void onContentsChanged() {
        setChanged();
    }

    public FluidTank getWaterTank() {
        return waterTank;
    }

    public IChemicalTank getSteamTank() {
        return steamTank;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temp) {
        this.temperature = temp;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Temperature", this.temperature);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("SteamTank", steamTank.serializeNBT(registries));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Temperature")) {
            this.temperature = tag.getDouble("Temperature");
        }
        if (tag.contains("WaterTank")) {
            waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        }
        if (tag.contains("SteamTank")) {
            steamTank.deserializeNBT(registries, tag.getCompound("SteamTank"));
        }
    }

    private double lastSyncedTemp = ROOM_TEMPERATURE;

    // Dodaj to wywołanie na końcu metody serverTick:
    private void checkTemperatureSync(ServerLevel level) {
        if (Math.abs(this.temperature - this.lastSyncedTemp) >= 0.5) {
            this.lastSyncedTemp = this.temperature;
            level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}