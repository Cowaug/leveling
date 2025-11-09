package cowaug.leveling.mixin;

import com.mojang.authlib.GameProfile;
import cowaug.leveling.helper.Helper;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@SuppressWarnings("unused")
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity {
    public ServerPlayerEntityMixin(World world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    @Inject(method = "playerTick", at = @At("HEAD"))
    public void onPlayerTickModifyStats(CallbackInfo ci) {
        modifyPlayerStats(EntityAttributes.MAX_HEALTH, Identifier.of("healthScale"), -10, 1, 1, -5, EntityAttributeModifier.Operation.ADD_VALUE);
        modifyPlayerStats(EntityAttributes.ARMOR, Identifier.of("armorScale"), 0, 1, 2, -30, EntityAttributeModifier.Operation.ADD_VALUE);
        modifyPlayerStats(EntityAttributes.OXYGEN_BONUS, Identifier.of("oxygenScale"), 0, 0.1f, 2, -10, EntityAttributeModifier.Operation.ADD_VALUE); // https://minecraft.wiki/w/Attribute#Oxygen_bonus

        modifyPlayerStats(EntityAttributes.MOVEMENT_SPEED, Identifier.of("speedScale"), 0, 1, 2, -20, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifyPlayerStats(EntityAttributes.SUBMERGED_MINING_SPEED, Identifier.of("waterMiningScale"), 0, 1, 2, -20, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    @Redirect(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;drop(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;)V"))
    protected void onDeathDropNothing(ServerPlayerEntity instance, ServerWorld serverWorld, DamageSource damageSource) {
        // skip the inventory & exp drop
    }

    @Inject(method = "copyFrom", at = @At("RETURN"))
    public void copyFromWithInvKeep(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayerEntityMixin oldPlayerEntity = Helper.CastFrom(oldPlayer);

        if (!alive) {
            // only keep inventory
            this.getInventory().clone(oldPlayer.getInventory());
            this.hungerManager.setFoodLevel(0);
        }
    }

    @Unique
    private void modifyPlayerStats(RegistryEntry<EntityAttribute> attribute, Identifier id, float initValue, float increaseValue, int levelPerModify, int levelOffset, EntityAttributeModifier.Operation operation) {
        var shouldUpdateStats = false;
        EntityAttributeInstance attributeInstance = Objects.requireNonNull(this.getAttributeInstance(attribute));
        var newValue = initValue + Math.max(0, (int) ((this.experienceLevel + levelOffset) / levelPerModify));

        if (attributeInstance.hasModifier(id)) {
            var modifier = Objects.requireNonNull(attributeInstance.getModifier(id));
            var oldValue = modifier.value();

            shouldUpdateStats = oldValue != newValue;
        } else {
            shouldUpdateStats = true;
        }

        if (shouldUpdateStats) {
            attributeInstance.removeModifier(id);
            EntityAttributeModifier modifier = new EntityAttributeModifier(id, newValue, operation);
            attributeInstance.addPersistentModifier(modifier);
        }
    }
}
