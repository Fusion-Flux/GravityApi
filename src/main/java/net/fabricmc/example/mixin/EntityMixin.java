package net.fabricmc.example.mixin;


import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.CallbackI;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow public World world;

    @Shadow public boolean noClip;

    @Shadow public abstract void setPosition(double x, double y, double z);

    @Shadow public abstract double getX();

    @Shadow public abstract double getY();

    @Shadow public abstract double getZ();

    @Shadow public boolean wasOnFire;

    @Shadow public abstract boolean isOnFire();

    @Shadow protected abstract Vec3d adjustMovementForPiston(Vec3d movement);

    @Shadow protected Vec3d movementMultiplier;

    @Shadow public abstract void setVelocity(Vec3d velocity);

    @Shadow protected abstract Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type);

    @Shadow protected abstract Vec3d adjustMovementForCollisions(Vec3d movement);

    @Shadow public boolean horizontalCollision;

    @Shadow public boolean verticalCollision;

    @Shadow protected boolean onGround;

    @Shadow public abstract boolean isRemoved();

    @Shadow protected abstract void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition);

    @Shadow public abstract void setVelocity(double x, double y, double z);

    @Shadow public abstract Vec3d getVelocity();

    @Shadow public abstract boolean bypassesSteppingEffects();

    @Shadow protected abstract Entity.MoveEffect getMoveEffect();

    @Shadow public abstract boolean hasVehicle();

    @Shadow public float field_28627;

    @Shadow public float horizontalSpeed;

    @Shadow public float distanceTraveled;

    @Shadow private float nextStepSoundDistance;

    @Shadow protected abstract float calculateNextStepSoundDistance();

    @Shadow public abstract boolean isTouchingWater();

    @Shadow public abstract boolean hasPassengers();

    @Shadow @Nullable public abstract Entity getPrimaryPassenger();

    @Shadow protected abstract void playSwimSound(float volume);

    @Shadow public abstract void emitGameEvent(GameEvent event);

    @Shadow protected abstract void playAmethystChimeSound(BlockState state);

    @Shadow protected abstract void playStepSound(BlockPos pos, BlockState state);

    @Shadow protected abstract void addAirTravelEffects();

    @Shadow protected abstract void tryCheckBlockCollision();

    @Shadow protected abstract float getVelocityMultiplier();

    @Shadow public abstract Box getBoundingBox();

    @Shadow private int fireTicks;

    @Shadow public abstract void setFireTicks(int ticks);

    @Shadow protected abstract int getBurningDuration();

    @Shadow public boolean inPowderSnow;

    @Shadow public abstract boolean isWet();

    @Shadow protected abstract void playExtinguishSound();

    @Shadow private Vec3d pos;

/**
 * @author
 */
@Overwrite
    public void move(MovementType movementType, Vec3d movement) {
    movement= new Vec3d(movement.x,movement.z,-movement.y); //this is new
        if (this.noClip) {
            this.setPosition(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
        } else {
            this.wasOnFire = this.isOnFire();
            if (movementType == MovementType.PISTON) {
                movement = this.adjustMovementForPiston(movement);
                if (movement.equals(Vec3d.ZERO)) {
                    return;
                }
            }

            this.world.getProfiler().push("move");
            if (this.movementMultiplier.lengthSquared() > 1.0E-7D) {
                movement = movement.multiply(this.movementMultiplier);
                this.movementMultiplier = Vec3d.ZERO;
                this.setVelocity(Vec3d.ZERO);
            }

            movement = this.adjustMovementForSneaking(movement, movementType);
            Vec3d vec3d = this.adjustMovementForCollisions(movement);
            if (vec3d.lengthSquared() > 1.0E-7D) {
                this.setPosition(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z);
            }

            this.world.getProfiler().pop();
            this.world.getProfiler().push("rest");
            this.horizontalCollision = !MathHelper.approximatelyEquals(movement.x, vec3d.x) || !MathHelper.approximatelyEquals(movement.y, vec3d.y); //This is modified
            this.verticalCollision = -movement.z != -vec3d.z; //This is modified
            this.onGround = this.verticalCollision && -movement.z < 0.0D; //This is modified
            BlockPos blockPos = this.getLandingPos();
            BlockState blockState = this.world.getBlockState(blockPos);
            this.fall(vec3d.z, this.onGround, blockState, blockPos); //This is modified
            if (this.isRemoved()) {
                this.world.getProfiler().pop();
            } else {
                Vec3d vec3d2 = this.getVelocity();
                if (movement.x != vec3d.x) {
                    this.setVelocity(0.0D, vec3d2.y, vec3d2.z);
                }

                if (movement.y != vec3d.y) { //This is modified
                    this.setVelocity(vec3d2.x, 0.0D, vec3d2.z);
                }

                Block block = blockState.getBlock();

                if (-movement.z != -vec3d.z) { //This is modified
                    block.onEntityLand(this.world, (Entity) (Object) this);
                }

                if (this.onGround && !this.bypassesSteppingEffects()) {
                    block.onSteppedOn(this.world, blockPos, blockState, (Entity) (Object) this);
                }

                Entity.MoveEffect moveEffect = this.getMoveEffect();
                if (moveEffect.hasAny() && !this.hasVehicle()) {
                    double d = vec3d.x;
                    double e = vec3d.y;
                    double f = vec3d.z;
                    this.field_28627 = (float)((double)this.field_28627 + vec3d.length() * 0.6D);
                    if (!blockState.isIn(BlockTags.CLIMBABLE) && !blockState.isOf(Blocks.POWDER_SNOW)) {
                        e = 0.0D;
                    }

                    this.horizontalSpeed += (float)vec3d.horizontalLength() * 0.6F;
                    this.distanceTraveled += (float)Math.sqrt(d * d + e * e + f * f) * 0.6F;
                    if (this.distanceTraveled > this.nextStepSoundDistance && !blockState.isAir()) {
                        this.nextStepSoundDistance = this.calculateNextStepSoundDistance();
                        if (this.isTouchingWater()) {
                            if (moveEffect.playsSounds()) {
                                Entity entity = this.hasPassengers() && this.getPrimaryPassenger() != null ? this.getPrimaryPassenger() : (Entity) (Object) this;
                                float g = entity == (Entity) (Object) this ? 0.35F : 0.4F;
                                Vec3d vec3d3 = entity.getVelocity();
                                float h = Math.min(1.0F, (float)Math.sqrt(vec3d3.x * vec3d3.x * 0.20000000298023224D + vec3d3.y * vec3d3.y + vec3d3.z * vec3d3.z * 0.20000000298023224D) * g);
                                this.playSwimSound(h);
                            }

                            if (moveEffect.emitsGameEvents()) {
                                this.emitGameEvent(GameEvent.SWIM);
                            }
                        } else {
                            if (moveEffect.playsSounds()) {
                                this.playAmethystChimeSound(blockState);
                                this.playStepSound(blockPos, blockState);
                            }

                            if (moveEffect.emitsGameEvents() && !blockState.isIn(BlockTags.OCCLUDES_VIBRATION_SIGNALS)) {
                                this.emitGameEvent(GameEvent.STEP);
                            }
                        }
                    } else if (blockState.isAir()) {
                        this.addAirTravelEffects();
                    }
                }

                this.tryCheckBlockCollision();
                float i = this.getVelocityMultiplier();
                this.setVelocity(this.getVelocity().multiply((double)i, 1.0D, (double)i));
                if (this.world.getStatesInBoxIfLoaded(this.getBoundingBox().contract(1.0E-6D)).noneMatch((state) -> {
                    return state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA);
                })) {
                    if (this.fireTicks <= 0) {
                        this.setFireTicks(-this.getBurningDuration());
                    }

                    if (this.wasOnFire && (this.inPowderSnow || this.isWet())) {
                        this.playExtinguishSound();
                    }
                }

                if (this.isOnFire() && (this.inPowderSnow || this.isWet())) {
                    this.setFireTicks(-this.getBurningDuration());
                }

                this.world.getProfiler().pop();
            }
        }
    }

/**
 * @author
 */
@Overwrite
    public BlockPos getLandingPos() {
        int i = MathHelper.floor(this.pos.x);
        int j = MathHelper.floor(this.pos.y);
        int k = MathHelper.floor(this.pos.z- 0.20000000298023224D);
        BlockPos blockPos = new BlockPos(i, j, k);
        if (this.world.getBlockState(blockPos).isAir()) {
            BlockPos blockPos2 = blockPos.down();
            BlockState blockState = this.world.getBlockState(blockPos2);
            if (blockState.isIn(BlockTags.FENCES) || blockState.isIn(BlockTags.WALLS) || blockState.getBlock() instanceof FenceGateBlock) {
                return blockPos2;
            }
        }

        return blockPos;
    }



}
