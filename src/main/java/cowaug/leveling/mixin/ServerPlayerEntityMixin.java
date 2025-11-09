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
        modifyPlayerStats(EntityAttributes.MAX_HEALTH, Identifier.of("scale_hp"), -10, 20, 5, 100, EntityAttributeModifier.Operation.ADD_VALUE, true);
        modifyPlayerStats(EntityAttributes.OXYGEN_BONUS, Identifier.of("scale_oxygen_decrease_rate"), 0, 1, 30, 100, EntityAttributeModifier.Operation.ADD_VALUE, false); // https://minecraft.wiki/w/Attribute#Oxygen_bonus

        modifyPlayerStats(EntityAttributes.MOVEMENT_SPEED, Identifier.of("scale_speed"), 0, 0.1f, 10, 100, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modifyPlayerStats(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, Identifier.of("scale_water_speed"), 0, 0.1f, 10, 100, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);

        modifyPlayerStats(EntityAttributes.MINING_EFFICIENCY, Identifier.of("scale_mining_efficiency"), 0, 0.1f, 20, 50, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modifyPlayerStats(EntityAttributes.SUBMERGED_MINING_SPEED, Identifier.of("scale_water_mining_speed"), 0, 0.1f, 20, 50, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
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
            this.hungerManager.setFoodLevel(4);
        }
    }

    @Unique
    private void modifyPlayerStats(RegistryEntry<EntityAttribute> attribute, Identifier id, float startValue, float endValue, int startLevel, int endLevel, EntityAttributeModifier.Operation operation, boolean roundToEven) {
        var shouldUpdateStats = false;
        var valuePerStep = (endValue - startValue) / (endLevel - startLevel);

        var newValue = startValue + valuePerStep * Math.max(0, this.experienceLevel - startLevel + 1);
        newValue = Math.min(newValue, endValue);

        if (roundToEven) {
            newValue = Math.round(newValue / 2) * 2;
        }

        EntityAttributeInstance attributeInstance = Objects.requireNonNull(this.getAttributeInstance(attribute));
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
