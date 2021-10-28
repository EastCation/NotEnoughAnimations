package dev.tr7zw.notenoughanimations.logic;

import java.util.HashSet;
import java.util.Set;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tr7zw.notenoughanimations.NEAnimationsLoader;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public class ArmTransformer {

	private Set<Item> compatibleMaps = new HashSet<>();
	private Set<Item> holdingItems = new HashSet<>();
	
	private boolean doneLatebind = false;
	private final Minecraft mc = Minecraft.getInstance();
	private int frameId = 0; //ok to overflow, just used to keep track of what has been updated this frame
	
	public void updateArms(Player entity, PlayerModel<AbstractClientPlayer> model, float tick, CallbackInfo info) {
        if(!doneLatebind)lateBind();
        if(mc.level == null) { // We are in a main menu or something
            return;
        }
        if(entity instanceof Player && entity.getPose() == Pose.SWIMMING) { // Crawling/Swimming has its own animations and messing with it screws stuff up
            return;
        }
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        applyAnimations(entity, model, HumanoidArm.RIGHT, rightHanded ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, tick);
        applyAnimations(entity, model, HumanoidArm.LEFT, !rightHanded ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, tick);
        
        
        if(NEAnimationsLoader.config.enableAnimationSmoothing && entity instanceof PlayerData) {
            PlayerData data = (PlayerData) entity;
            if(model.leftArm.xRot == 0f && model.leftArm.zRot == -0.1f && model.leftArm.yRot == 0f) {
                return; //this is rendering the first person hand, don't try to use that for interpolation
            }
            float[] last = data.getLastRotations();
            boolean differentFrame = !data.isUpdated(frameId);
            long timePassed = System.currentTimeMillis() - data.lastUpdate();
            if(timePassed < 1)
                timePassed = 1;
            interpolate(model.leftArm, last, 0, timePassed, differentFrame, NEAnimationsLoader.config.animationSmoothingSpeed);
            interpolate(model.rightArm, last, 3, timePassed, differentFrame, NEAnimationsLoader.config.animationSmoothingSpeed);
            data.setUpdated(frameId);
        }
	}
	
	public void nextFrame() {
	    frameId++;
	}
	
	private void interpolate(ModelPart model, float[] last, int offset, long timePassed, boolean differentFrame, float speed) {
		if(!differentFrame) { //Rerendering the place in the same frame
			model.xRot = (last[offset]);
			model.yRot = (last[offset+1]);
			model.zRot = (last[offset+2]);
			return;
		}
		if(timePassed > 200) { //Don't try to interpolate states older than 200ms
			last[offset] = model.xRot;
			last[offset+1] = model.yRot;
			last[offset+2] = model.zRot;
			cleanInvalidData(last, offset);
			return;
		}
		float amount = ((1f/(1000f/timePassed)))*speed;
		if(amount > 1)amount = 1;
		last[offset] = last[offset]+((model.xRot-last[offset])*amount);
		last[offset+1] = last[offset+1]+((wrapDegrees(model.yRot)-wrapDegrees(last[offset+1]))*amount);
		last[offset+2] = last[offset+2]+((model.zRot-last[offset+2])*amount);
		cleanInvalidData(last, offset);
		model.xRot = (last[offset]);
		model.yRot = (last[offset+1]);
		model.zRot = (last[offset+2]);
	}
	
	/**
	 * When using a quickcharge 5 crossbow it is able to cause NaN values to show up because of how broken it is.
	 * 
	 * @param data
	 * @param offset
	 */
	private void cleanInvalidData(float[] data, int offset) {
	    if(Float.isNaN(data[offset]))data[offset] = 0;
	    if(Float.isNaN(data[offset+1]))data[offset+1] = 0;
	    if(Float.isNaN(data[offset+2]))data[offset+2] = 0;
	}
	
	private void applyAnimations(Player livingEntity, PlayerModel<AbstractClientPlayer> model, HumanoidArm arm, InteractionHand hand, float tick) {
		ItemStack itemInHand = livingEntity.getItemInHand(hand);
		ItemStack itemInOtherHand = livingEntity
				.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		// passive animations
		if (holdingItems.contains(itemInHand.getItem())) {
			applyArmTransforms(model, arm, -(Mth.lerp(-1f * (livingEntity.getXRot() - 90f) / 180f, 1f, 1.5f)), -0.2f, 0.3f);
		}
		if(NEAnimationsLoader.config.enableInWorldMapRendering) {
    		if ((compatibleMaps.contains(itemInHand.getItem()) && itemInOtherHand.isEmpty() && hand == InteractionHand.MAIN_HAND)
    				|| compatibleMaps.contains(itemInOtherHand.getItem()) && itemInHand.isEmpty() && hand == InteractionHand.OFF_HAND) {
    			applyArmTransforms(model, arm, -(Mth.lerp(-1f * (livingEntity.getXRot() - 90f) / 180f, 0.5f, 1.5f)), -0.4f,
    					0.3f);
    		}else if(compatibleMaps.contains(itemInHand.getItem()) && hand == InteractionHand.MAIN_HAND) {
    			applyArmTransforms(model, arm, -(Mth.lerp(-1f * (livingEntity.getXRot() - 90f) / 180f, 0.5f, 1.5f)), 0f,
    					0.3f);
    		}
    		if(compatibleMaps.contains(itemInHand.getItem()) && hand == InteractionHand.OFF_HAND) {
    			applyArmTransforms(model, arm, -(Mth.lerp(-1f * (livingEntity.getXRot() - 90f) / 180f, 0.5f, 1.5f)), 0f,
    					0.3f);
    		}
		}
		if(livingEntity.isSleeping()) {
		    if(NEAnimationsLoader.config.freezeArmsInBed) {
		        applyArmTransforms(model, arm, 0, 0f, 0f);
		    }
			return; // Dont try to apply more
		}
		// Stop here if the hands are doing something
		
		if(livingEntity.getUsedItemHand() == hand && livingEntity.getUseItemRemainingTicks() > 0) {
			UseAnim action = itemInHand.getUseAnimation();
			if(action == UseAnim.BLOCK || action == UseAnim.SPEAR || action == UseAnim.BOW || action == UseAnim.CROSSBOW) {
				return;// stop
			}
		}
		// active animations
		if(livingEntity.isPassenger()) {
			if(livingEntity.getVehicle() instanceof Boat && NEAnimationsLoader.config.enableRowBoatAnimation) {
			    Boat boat = (Boat) livingEntity.getVehicle();
				float paddle = boat.getRowingTime(arm == HumanoidArm.LEFT ? 0 : 1, tick);
				applyArmTransforms(model, arm, -1.1f -Mth.sin(paddle) * 0.3f, 0.2f, 0.3f);
			}
			if(livingEntity.getVehicle() instanceof AbstractHorse && NEAnimationsLoader.config.enableHorseAnimation) {
			    AbstractHorse horse = (AbstractHorse) livingEntity.getVehicle();
			    float rotation = -Mth.cos(horse.animationPosition * 0.3f);
				rotation *= 0.1;
				applyArmTransforms(model, arm, -1.1f -rotation, -0.2f, 0.3f);
			}
		}
		if(livingEntity.onClimbable() && NEAnimationsLoader.config.enableLadderAnimation) {
			float rotation = -Mth.cos((float) (livingEntity.getY()*2));
			rotation *= 0.3;
			if(arm == HumanoidArm.LEFT)rotation *= -1;
			applyArmTransforms(model, arm, -1.1f -rotation, -0.2f, 0.3f);
		}
		if (livingEntity.getUsedItemHand() == hand && livingEntity.getUseItemRemainingTicks() > 0  && NEAnimationsLoader.config.enableEatDrinkAnimation) {
		    UseAnim action = itemInHand.getUseAnimation();
			// Eating/Drinking
			if (action == UseAnim.EAT || action == UseAnim.DRINK) {
				applyArmTransforms(model, arm, -(Mth.lerp(-1f * (livingEntity.getXRot() - 90f) / 180f, 1f, 2f))
						+ Mth.sin(tick * 1.5f) * 0.1f, -0.3f, 0.3f);
			}
		}
	}
	
	private void lateBind() {
	       Item invalid = Registry.ITEM.get(new ResourceLocation("minecraft", "air"));
	        for(String itemId : NEAnimationsLoader.config.holdingItems) {
	            try {
	                Item item = Registry.ITEM.get(new ResourceLocation(itemId.split(":")[0], itemId.split(":")[1]));
	                if(invalid != item)
	                    holdingItems.add(item);
	            }catch(Exception ex) {
	                System.err.println("Unknown item to add to the holding list: " + itemId);
	            }
	        }
	        compatibleMaps.add(Registry.ITEM.get(new ResourceLocation("minecraft", "filled_map")));
	        Item antiqueAtlas = Registry.ITEM.get(new ResourceLocation("antiqueatlas", "antique_atlas"));
	        if(invalid != antiqueAtlas) {
	            compatibleMaps.add(antiqueAtlas);
	            NEAnimationsLoader.LOGGER.info("Added AntiqueAtlas support to Not Enough Animations!");
	        }
	    doneLatebind = true;
	}

	private void applyArmTransforms(PlayerModel<AbstractClientPlayer> model, HumanoidArm arm, float pitch, float yaw, float roll) {
		ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		part.xRot = pitch;
		part.yRot = yaw;
		if (arm == HumanoidArm.LEFT) // Just mirror yaw for the left hand
			part.yRot *= -1;
		part.zRot = roll;
		if (arm == HumanoidArm.LEFT)
			part.zRot *= -1;
	}
	
	private float wrapDegrees(float f) {
		float g = f % 6.28318512f;
		if (g >= 3.14159256f) {
			g -= 6.28318512f;
		}
		if (g < -3.14159256f) {
			g += 6.28318512f;
		}
		return g;
	}

}