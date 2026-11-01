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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
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
    private double lastSyncedTemp = ROOM_TEMPERATURE;

    // Bufor ciepła zapewniający bezwładność cieplną (wygładza skoki temperatury od uderzeń neutronów)
    private double heatBuffer = 0.0;

    private final FluidTank waterTank = new FluidTank(32_000) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final IChemicalTank steamTank = BasicChemicalTank.create(32_000, this::setChanged);

    public EnrichedUraniumBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ENRICHED_URANIUM_REACTOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnrichedUraniumBlockEntity blockEntity) {
        if (level == null || level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        double previousTemperature = blockEntity.temperature;

        // 1. Płynne przekazywanie ciepła z bufora rozszczepienia do pręta (10% na tick)
        if (blockEntity.heatBuffer > 0.001) {
            double transferredHeat = blockEntity.heatBuffer * 0.10;
            blockEntity.temperature += transferredHeat;
            blockEntity.heatBuffer -= transferredHeat;
        } else {
            blockEntity.heatBuffer = 0.0;
        }

        // 2. Wypchnij parę przed operacjami
        blockEntity.pushSteam(serverLevel, pos);

        // 3. Wyrównaj wodę, temperaturę i prześlij parę w górę kolumny
        blockEntity.balanceVerticalChannel(serverLevel, pos);

        // 4. Wypchnij parę otrzymaną z dolnych bloków
        blockEntity.pushSteam(serverLevel, pos);

        // 5. Płynne gotowanie wody i chłodzenie
        blockEntity.handleBoiling(serverLevel, pos);

        // 6. Wypchnij nowo wyprodukowaną parę
        blockEntity.pushSteam(serverLevel, pos);

        // Rzadkie samorzutne rozszczepienie (spontaneous fission)
        if (serverLevel.random.nextFloat() < 0.002f) {
            blockEntity.emitNeutron(serverLevel);
        }

        // Pasywne chłodzenie do otoczenia
        if (blockEntity.temperature > ROOM_TEMPERATURE) {
            blockEntity.temperature = Math.max(
                    ROOM_TEMPERATURE,
                    blockEntity.temperature - 0.05
            );
        }

        if (Math.abs(blockEntity.temperature - previousTemperature) > 0.001) {
            blockEntity.setChanged();
        }

        double maxTemp = Config.EXPLOSION_TEMPERATURE.get();
        if (blockEntity.temperature >= maxTemp) {
            blockEntity.explode(serverLevel, pos);
            return;
        }

        if (blockEntity.temperature > 600.0 && level.random.nextFloat() < 0.3f) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    2, 0.1, 0.1, 0.1, 0.02);
        }

        blockEntity.checkTemperatureSync(serverLevel);
    }

    private void balanceVerticalChannel(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos.above()) instanceof EnrichedUraniumBlockEntity above)) {
            return;
        }

        // PRZESYŁ PARY W GÓRĘ
        if (!steamTank.isEmpty()) {
            long available = steamTank.getStack().getAmount();

            ChemicalStack offered = new ChemicalStack(
                    MekanismChemicals.STEAM,
                    available
            );

            ChemicalStack remainder = above.steamTank.insert(
                    offered,
                    Action.EXECUTE,
                    AutomationType.INTERNAL
            );

            long moved = available - remainder.getAmount();

            if (moved > 0) {
                steamTank.extract(
                        moved,
                        Action.EXECUTE,
                        AutomationType.INTERNAL
                );
            }
        }

        // WYRÓWNYWANIE WODY
        int diffWater = waterTank.getFluidAmount() - above.waterTank.getFluidAmount();

        if (Math.abs(diffWater) > 2) {
            int transfer = Math.abs(diffWater) / 2;

            if (diffWater > 0) {
                int filled = above.waterTank.fill(
                        new FluidStack(Fluids.WATER, transfer),
                        IFluidHandler.FluidAction.EXECUTE
                );
                waterTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            } else {
                int filled = waterTank.fill(
                        new FluidStack(Fluids.WATER, transfer),
                        IFluidHandler.FluidAction.EXECUTE
                );
                above.waterTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        // PŁYNNE PRZEWODZENIE CIEPŁA W KOLUMNIE
        double tempDiff = temperature - above.temperature;

        if (Math.abs(tempDiff) > 0.1) {
            double delta = tempDiff * 0.15;

            temperature -= delta;
            above.temperature += delta;

            setChanged();
            above.setChanged();
        }
    }

    public void receiveNeutron(double energyEv, ServerLevel level) {
        double heatGenerated = (energyEv > 100_000) ? 10.0 : 22.0;
        this.heatBuffer += heatGenerated;

        int newNeutrons = 2 + level.random.nextInt(2);
        for (int i = 0; i < newNeutrons; i++) {
            emitNeutron(level);
        }
        setChanged();
    }

    private void emitNeutron(ServerLevel level) {
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

    private void handleBoiling(ServerLevel level, BlockPos pos) {
        final double BOILING_POINT = 373.15;
        final double COOLING_PER_WATER_MB = 2.0;
        final int MAX_WATER_PER_TICK = 400;
        final double WATER_COOLING_FACTOR = 0.20;

        int steamRatio = Math.max(1, Config.STEAM_PER_WATER_MB.get());

        if (waterTank.isEmpty() || temperature <= BOILING_POINT) {
            return;
        }

        double excessHeat = temperature - BOILING_POINT;
        double heatToTransfer = excessHeat * WATER_COOLING_FACTOR;
        int desiredWaterToBoil = (int) Math.ceil(heatToTransfer / COOLING_PER_WATER_MB);

        int maxWaterToBoil = Math.min(
                waterTank.getFluidAmount(),
                Math.min(MAX_WATER_PER_TICK, desiredWaterToBoil)
        );

        if (maxWaterToBoil <= 0) {
            return;
        }

        long requestedSteam = (long) maxWaterToBoil * steamRatio;

        ChemicalStack simulatedRemainder = steamTank.insert(
                new ChemicalStack(MekanismChemicals.STEAM, requestedSteam),
                Action.SIMULATE,
                AutomationType.INTERNAL
        );

        long acceptedSteam = Math.max(0, requestedSteam - simulatedRemainder.getAmount());

        int actualWaterToBoil = (int) Math.min(
                maxWaterToBoil,
                acceptedSteam / steamRatio
        );

        if (actualWaterToBoil <= 0) {
            return;
        }

        FluidStack drainedWater = waterTank.drain(
                actualWaterToBoil,
                IFluidHandler.FluidAction.EXECUTE
        );

        if (drainedWater.isEmpty()) {
            return;
        }

        long actualSteamToInsert = (long) drainedWater.getAmount() * steamRatio;
        steamTank.insert(
                new ChemicalStack(MekanismChemicals.STEAM, actualSteamToInsert),
                Action.EXECUTE,
                AutomationType.INTERNAL
        );

        double actualCooling = Math.min(
                heatToTransfer,
                drainedWater.getAmount() * COOLING_PER_WATER_MB
        );

        temperature = Math.max(BOILING_POINT, temperature - actualCooling);

        if (level.random.nextFloat() < 0.25f) {
            level.sendParticles(
                    ParticleTypes.BUBBLE_POP,
                    pos.getX() + 0.5,
                    pos.getY() + 1.1,
                    pos.getZ() + 0.5,
                    2,
                    0.1, 0.1, 0.1,
                    0.05
            );
        }

        setChanged();
    }

    private void pushSteam(ServerLevel level, BlockPos pos) {
        if (steamTank.isEmpty()) {
            return;
        }

        for (Direction dir : Direction.values()) {
            if (steamTank.isEmpty()) {
                break;
            }

            BlockPos neighborPos = pos.relative(dir);

            if (dir.getAxis().isVertical()
                    && level.getBlockEntity(neighborPos) instanceof EnrichedUraniumBlockEntity) {
                continue;
            }

            IChemicalHandler neighborHandler = level.getCapability(
                    Capabilities.CHEMICAL.block(),
                    neighborPos,
                    dir.getOpposite()
            );

            if (neighborHandler == null) {
                continue;
            }

            long available = steamTank.getStack().getAmount();
            if (available <= 0) {
                break;
            }

            ChemicalStack offeredStack = new ChemicalStack(
                    MekanismChemicals.STEAM,
                    available
            );

            ChemicalStack remainder = neighborHandler.insertChemical(
                    offeredStack,
                    Action.EXECUTE
            );

            long inserted = available - remainder.getAmount();

            if (inserted > 0) {
                steamTank.extract(
                        inserted,
                        Action.EXECUTE,
                        AutomationType.INTERNAL
                );
            }
        }
    }

    private void explode(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);

        // 1. Wystrzelenie odłamków bloków i rozgrzanej magmy (korium) w powietrze PRZED zniszczeniem terenu
        launchExplosionDebris(level, pos);

        // 2. Potężne efekty dźwiękowe słyszalne z dużej odległości
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 16.0f, 0.5f);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 20.0f, 0.6f);
        level.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 16.0f, 0.5f);

        // 3. Błysk początkowy i gigantyczne cząsteczki wybuchu w epicentrum
        level.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5, 10, 2.0, 2.0, 2.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 25, 6.0, 4.0, 6.0, 0.0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, pos.getX() + 0.5, pos.getY() + 4.0, pos.getZ() + 0.5, 15, 3.0, 6.0, 3.0, 0.0);
        level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 250, 5.0, 4.0, 5.0, 0.5);

        // 4. Wielopunktowa eksplozja tworząca głęboki i szeroki krater
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 45.0f, true, Level.ExplosionInteraction.TNT);
        level.explode(null, pos.getX() + 8.5, pos.getY() - 2.0, pos.getZ() + 0.5, 30.0f, true, Level.ExplosionInteraction.TNT);
        level.explode(null, pos.getX() - 8.5, pos.getY() - 2.0, pos.getZ() + 0.5, 30.0f, true, Level.ExplosionInteraction.TNT);
        level.explode(null, pos.getX() + 0.5, pos.getY() - 2.0, pos.getZ() + 8.5, 30.0f, true, Level.ExplosionInteraction.TNT);
        level.explode(null, pos.getX() + 0.5, pos.getY() - 2.0, pos.getZ() - 8.5, 30.0f, true, Level.ExplosionInteraction.TNT);

        // 5. Fala uderzeniowa i grzyb atomowy
        NuclearShockwaveManager.create(level, Vec3.atCenterOf(pos));

        // 6. Emisja neutronów i promieniowania gamma
        int newNeutrons = 3000 + level.random.nextInt(3000);
        for (int i = 0; i < newNeutrons; i++) {
            emitNeutron(level);
        }

        for (int i = 0; i < 60; i++) {
            Vec3 dir = new Vec3(
                    level.random.nextDouble() - 0.5,
                    level.random.nextDouble() - 0.5,
                    level.random.nextDouble() - 0.5
            ).normalize().scale(0.5);

            NuclearParticle radiation = new NuclearParticle(
                    NuclearParticle.Type.GAMMA,
                    Vec3.atCenterOf(pos),
                    dir,
                    80,
                    pos
            );

            NuclearParticleManager.add(level, radiation);
        }
    }

    /**
     * Wyrywa bloki wokół reaktora i wystrzeliwuje je jako fizyczne bloki (FallingBlockEntity)
     * lecące po trajektorii balistycznej we wszystkich kierunkach.
     */
    private void launchExplosionDebris(ServerLevel level, BlockPos center) {
        var random = level.random;
        int debrisCount = 85;

        for (int i = 0; i < debrisCount; i++) {
            int dx = random.nextInt(17) - 8;
            int dy = random.nextInt(11) - 4;
            int dz = random.nextInt(17) - 8;

            BlockPos samplePos = center.offset(dx, dy, dz);
            BlockState state = level.getBlockState(samplePos);

            // Część odłamków zamieniamy w rozżarzoną magmę / stopione korium i czarny bazalt
            BlockState flyingState;
            float roll = random.nextFloat();
            if (roll < 0.25f) {
                flyingState = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (roll < 0.45f) {
                flyingState = Blocks.BLACKSTONE.defaultBlockState();
            } else if (!state.isAir() && state.getDestroySpeed(level, samplePos) >= 0 && !(state.getBlock() instanceof EnrichedUraniumBlock)) {
                flyingState = state;
            } else {
                flyingState = Blocks.COBBLESTONE.defaultBlockState();
            }

            // Tworzymy odlatujący blok nieco ponad epicentrum, aby nie zablokował się w ścianie
            BlockPos spawnPos = center.offset(
                    dx / 2,
                    Math.max(2, dy + 3),
                    dz / 2
            );

            FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, spawnPos, flyingState);
            fallingBlock.dropItem = false; // Nie zaśmieca świata tysiącami itemów
            fallingBlock.setHurtsEntities(3.0f, 60); // Spadający gruz zadaje obrażenia!

            // Kierunek wystrzału na zewnątrz i wysoko w górę
            double angle = random.nextDouble() * Math.PI * 2.0;
            double horizontalSpeed = 0.4 + random.nextDouble() * 1.8;
            double verticalSpeed = 0.9 + random.nextDouble() * 1.9;

            fallingBlock.setDeltaMovement(
                    Math.cos(angle) * horizontalSpeed,
                    verticalSpeed,
                    Math.sin(angle) * horizontalSpeed
            );
            fallingBlock.hurtMarked = true;

            // Dodatkowe cząsteczki gruzu
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, flyingState),
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                    6, 0.5, 0.5, 0.5, 0.2
            );
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
        tag.putDouble("HeatBuffer", this.heatBuffer);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("SteamTank", steamTank.serializeNBT(registries));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Temperature")) {
            this.temperature = tag.getDouble("Temperature");
        }
        if (tag.contains("HeatBuffer")) {
            this.heatBuffer = tag.getDouble("HeatBuffer");
        }
        if (tag.contains("WaterTank")) {
            waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        }
        if (tag.contains("SteamTank")) {
            steamTank.deserializeNBT(registries, tag.getCompound("SteamTank"));
        }
    }

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