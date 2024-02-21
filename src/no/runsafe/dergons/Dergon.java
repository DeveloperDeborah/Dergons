package no.runsafe.dergons;

import net.minecraft.server.v1_12_R1.*;
import no.runsafe.framework.api.ILocation;
import no.runsafe.framework.api.IWorld;
import no.runsafe.framework.api.player.IPlayer;
import no.runsafe.framework.internal.wrapper.ObjectUnwrapper;
import no.runsafe.framework.internal.wrapper.ObjectWrapper;
import no.runsafe.framework.minecraft.Buff;
import no.runsafe.framework.minecraft.Item;
import no.runsafe.framework.minecraft.Sound;
import no.runsafe.framework.minecraft.WorldEffect;
import no.runsafe.framework.minecraft.enchantment.RunsafeEnchantment;
import no.runsafe.framework.minecraft.entity.RunsafeFallingBlock;
import no.runsafe.framework.minecraft.entity.ProjectileEntity;
import no.runsafe.framework.minecraft.item.meta.RunsafeMeta;
import org.bukkit.util.Vector;

import java.util.*;

import static java.lang.Math.*;

/*
 * Names of obfuscated variables in various spigot versions:
 * Type                 v1_12_R1
 * EntityLiving.Class:
 * protected int        bi       Position rotation increment.
 */

public class Dergon extends EntityInsentient implements IComplex, IMonster
{
	public Dergon(IWorld world, DergonHandler handler, ILocation targetLocation, int dergonID)
	{
		super(ObjectUnwrapper.getMinecraft(world));
		Dergons.Debugger.debugInfo("Spawning dergon with UUID: " + getUniqueID());

		this.children = new EntityComplexPart[]
		{
			this.dergonHead = new EntityComplexPart(this, "head", 6.0F, 6.0F),
			this.dergonBody = new EntityComplexPart(this, "body", 8.0F, 8.0F),
			this.dergonTailSection0 = new EntityComplexPart(this, "tail", 4.0F, 4.0F),
			this.dergonTailSection1 = new EntityComplexPart(this, "tail", 4.0F, 4.0F),
			this.dergonTailSection2 = new EntityComplexPart(this, "tail", 4.0F, 4.0F),
			this.dergonWingLeft = new EntityComplexPart(this, "wing", 4.0F, 4.0F),
			this.dergonWingRight = new EntityComplexPart(this, "wing", 4.0F, 4.0F)
		};
		this.setHealth(this.getMaxHealth());
		this.setSize(16, 8);
		this.noclip = true;
		this.fireProof = true;
		this.persistent = true;
		this.dergonWorld = world;
		this.handler = handler;
		this.spawnLocation = targetLocation;
		this.dergonID = dergonID;

		this.handler.createBossBar(this.dergonID);
	}

	/**
	 * Bukkit likes when custom mobs have this constructor. Have it not do anything.
	 * Called when re-loading a dergon that was allowed to unload naturally or spawned with the summon command.
	 */
	@SuppressWarnings("unused")
	public Dergon(World bukkitWorld)
	{
		super(null);
		children = null;
		dergonHead = null;
		dergonBody = null;
		dergonWingRight = null;
		dergonWingLeft = null;
		dergonTailSection0 = null;
		dergonTailSection1 = null;
		dergonTailSection2 = null;
		handler = null;
		dergonWorld = null;
		die();

		Dergons.Debugger.debugInfo("Rogue Dergon attempted to spawn with UUID: " + getUniqueID() + ". Exterminating.");
	}

	/**
	 * Selects new player target.
	 */
	private void updateCurrentTarget()
	{
		ILocation dergonLocation = dergonWorld.getLocation(locX, locY, locZ);

		if (dergonLocation != null && flyOffLocation != null && random.nextFloat() == 0.1F)
			return;
		else
			flyOffLocation = null;

		// Check if we have any close players, if we do, fly away.
		if (dergonLocation != null && !dergonLocation.getPlayersInRange(10).isEmpty())
		{
			attemptPlayerPickup();

			targetEntity = null;
			targetX = locX + random.nextInt(200) - 100;
			targetY = random.nextInt(100) + 70; // Somewhere above 70 to prevent floor clipping.
			targetZ = locZ + random.nextInt(200) - 100;
			flyOffLocation = dergonWorld.getLocation(targetX, targetY, targetZ); // Store the target fly-off location.
			return;
		}
		else
		{
			// Grab all players in 200 blocks.
			List<IPlayer> players;
			if (spawnLocation != null)
				players = spawnLocation.getPlayersInRange(200);
			else
				players = getLocation().getPlayersInRange(200);

			List<IPlayer> targets = new ArrayList<>(0);

			for (IPlayer player : players)
			{
				// Skip the player if we're vanished, in creative mode, or in spectator mode.
				if (isInvalidTarget(player) || isRidingPlayer(player.getName()))
					continue;

				ILocation playerLocation = player.getLocation();

				if (spawnLocation != null) // If the player is greater than 50 blocks from the spawning location, we can target them.
				{
					if (playerLocation != null && playerLocation.distance(spawnLocation) > 50)
						targets.add(player);
				}
				else
					targets.add(player);
			}

			if (!targets.isEmpty())
			{
				// Target a random player in 200 blocks.
				targetEntity = players.get(random.nextInt(players.size()));
				return;
			}
		}

		if (spawnLocation != null)
		{
			// Send the dergon back to the start point.
			targetX = spawnLocation.getX();
			targetY = spawnLocation.getY();
			targetZ = spawnLocation.getZ();
		}
		else
		{
			targetX = locX;
			targetY = locY + 20;
			targetZ = locZ;
		}

		targetEntity = null;
	}

	private void attemptPlayerPickup()
	{
		if (ridingPlayer != null)
			return;

		List<IPlayer> closePlayers = getLocation().getPlayersInRange(10);
		IPlayer unluckyChum = closePlayers.get(random.nextInt(closePlayers.size()));

		if (isInvalidTarget(unluckyChum))
			return;

		// Always pick up a player if they're wearing an elytra.
		RunsafeMeta chestPlate = unluckyChum.getChestplate();
		if (chestPlate != null && chestPlate.is(Item.Transportation.Elytra))
		{
			// Crumple their elytra
			chestPlate.setDurability((short) (chestPlate.getDurability() + 100));
			unluckyChum.setChestplate(chestPlate);
			// Do additional damage
			unluckyChum.damage(Config.getPickupDamage());
			unluckyChum.addBuff(Buff.Combat.Blindness.duration(10));
			unluckyChum.sendColouredMessage(Config.Message.getDergonElytraPickup());
		}
		else if (!(random.nextFloat() < 0.3F)) // If they're not flying and get lucky, avoid picking them up.
			return;

		EntityHuman rawChum = ObjectUnwrapper.getMinecraft(unluckyChum);
		if (rawChum == null)
			return;

		unluckyChum.damage(Config.getPickupDamage());
		rawChum.startRiding(this);
		ridingPlayer = unluckyChum;
		handler.handleDergonMount(ridingPlayer);
	}

	/**
	 * Update method for Dergons.
	 * Names of this function in various spigot versions:
	 * v1_12_R1: n
	 */
	@Override
	public void n()
	{
		// Throw a player off its back if we're high up.
		int highestYBlock = dergonWorld.getHighestBlockYAt((int)locX, (int)locZ);
		if (ridingPlayer != null && ((locY >= highestYBlock + 25) // check for being high up.
			|| (highestYBlock - 50 > locY && locY > 90))) // deal with situation where there's a high ceiling.
		{
			ridingPlayer.leaveVehicle();
			ridingPlayer = null;
		}
		if (world != null)
			handler.updateBossBarHealth(dergonID, getHealth(), getMaxHealth());

		if (getHealth() <= 0.0F) // Check if the dragon is dead.
			return;

		// Handle despawn timer
		if (idleTicks >= Config.getDespawnTime())
		{
			handler.killDergon(dergonID);
			return;
		}
		else if (targetEntity == null)
			idleTicks++;
		else idleTicks = 0;

		// Wing particles
		ILocation leftLocation = dergonWorld.getLocation(dergonWingLeft.locX, dergonWingLeft.locY, dergonWingLeft.locZ);
		ILocation rightLocation = dergonWorld.getLocation(dergonWingRight.locX, dergonWingRight.locY, dergonWingRight.locZ);
		Objects.requireNonNull(leftLocation).playEffect(WorldEffect.FLAME, 0, 1, 50);
		Objects.requireNonNull(rightLocation).playEffect(WorldEffect.FLAME, 0, 1, 50);

		// Handle randomized dergon attacks
		ILocation dergonHeadLocation = dergonWorld.getLocation(dergonHead.locX, dergonHead.locY - 1, dergonHead.locZ);
		if (targetEntity != null && dergonHeadLocation != null && dergonWorld.isWorld(targetEntity.getWorld()))
		{
			if (random.nextFloat() < 0.2F)
				((RunsafeFallingBlock) dergonWorld.spawnFallingBlock(dergonHeadLocation, Item.Unavailable.Fire)).setDropItem(false);

			if (random.nextInt(60) == 1)
			{
				Vector velocity = Objects.requireNonNull(targetEntity.getLocation())
					.toVector().subtract(dergonHeadLocation.toVector()).normalize();
				ProjectileEntity.DragonFireball.spawn(dergonHeadLocation).setVelocity(velocity);
			}
		}

		yaw = (float) trimDegrees(yaw);
		if (positionBufferIndex < 0) // Load up the position buffer if and only if the dergon was just spawned.
		{
			for (int i = 0; i < positionBuffer.length; ++i)
			{
				positionBuffer[i][0] = yaw;
				positionBuffer[i][1] = locY;
			}
		}

		if (++positionBufferIndex == positionBuffer.length)
			positionBufferIndex = 0;

		positionBuffer[positionBufferIndex][0] = yaw;
		positionBuffer[positionBufferIndex][1] = locY;

		// Get target position relative to Dergon
		double targetPosX = targetX - locX;
		double targetPosY = targetY - locY;
		double targetPosZ = targetZ - locZ;
		double targetDistanceSquared = targetPosX * targetPosX + targetPosY * targetPosY + targetPosZ * targetPosZ;
		if (targetEntity != null && targetEntity.getLocation() != null)
		{
			ILocation targetPlayerLocation = targetEntity.getLocation();
			targetX = targetPlayerLocation.getX();
			targetZ = targetPlayerLocation.getZ();
			double xDistanceToTarget = targetX - locX;
			double zDistanceToTarget = targetZ - locZ;
			double distanceToTarget = sqrt(xDistanceToTarget * xDistanceToTarget + zDistanceToTarget * zDistanceToTarget);
			double ascendDistance = 0.4000000059604645D + distanceToTarget / 80.0D - 1.0D;

			if (ascendDistance > 10.0D)
				ascendDistance = 10.0D;

			targetY = targetPlayerLocation.getY() + ascendDistance;
		}
		else
		{
			targetX += random.nextGaussian() * 2.0D;
			targetZ += random.nextGaussian() * 2.0D;
		}

		if (targetDistanceSquared < 100.0D
			|| targetDistanceSquared > 22500.0D
			|| positionChanged
			|| !getLocation().getBlock().isAir()
		)
			updateCurrentTarget();

		targetPosY /= sqrt(targetPosX * targetPosX + targetPosZ * targetPosZ);
		final float Y_LIMIT = 0.6F;
		if (targetPosY < (-Y_LIMIT))
			targetPosY = (-Y_LIMIT);

		if (targetPosY > Y_LIMIT)
			targetPosY = Y_LIMIT;

		motY += targetPosY * 0.10000000149011612D;
		yaw = (float) trimDegrees(yaw);
		double targetDirection = 180.0D - toDegrees(atan2(targetPosX, targetPosZ));
		double targetHeadingDifference = trimDegrees(targetDirection - yaw);

		if (targetHeadingDifference > 50.0D)
			targetHeadingDifference = 50.0D;

		if (targetHeadingDifference < -50.0D)
			targetHeadingDifference = -50.0D;

		Vec3D relativeTargetCoordinates = new Vec3D(
			targetX - locX,
			targetY - locY,
			targetZ - locZ
		).a();// .a() -> Normalize values
		Vec3D direction = new Vec3D(
			sin(toRadians(yaw)),
			motY,
			(-cos(toRadians(yaw)))
		).a();// .a() -> Normalize values
		float f3 = (float) (direction.b(relativeTargetCoordinates) + 0.5D) / 1.5F;

		if (f3 < 0.0F)
			f3 = 0.0F;

		randomYawVelocity *= 0.8F;
		float movementSpeedStart = (float) sqrt(motX * motX + motZ * motZ) + 1.0F;
		double movementSpeedTrimmed = sqrt(motX * motX + motZ * motZ) + 1.0D;

		if (movementSpeedTrimmed > 40.0D)
			movementSpeedTrimmed = 40.0D;

		randomYawVelocity += (float) (targetHeadingDifference * (0.699999988079071D / movementSpeedTrimmed / (double) movementSpeedStart));
		yaw += randomYawVelocity * 0.1F;
		float f2 = (float) (2.0D / (movementSpeedTrimmed + 1.0D));
		float frictionDampener = 0.06F;

		// Move relative. (strafe, up, forward, friction) From Entity.class
		b(0.0F, 0.0F, -1.0F, frictionDampener * (f3 * f2 + (1.0F - f2)));
		move(EnumMoveType.SELF, motX, motY, motZ);

		Vec3D movementVector = new Vec3D(motX, motY, motZ).a();
		float lateralVelocityModifier = (float) (movementVector.b(direction) + 1.0D) / 2.0F;

		lateralVelocityModifier = 0.8F + 0.15F * lateralVelocityModifier;
		motX *= lateralVelocityModifier;
		motZ *= lateralVelocityModifier;
		motY *= 0.9100000262260437D;

		dergonHead.width = dergonHead.length = 3.0F;
		dergonTailSection0.width = dergonTailSection0.length = 2.0F;
		dergonTailSection1.width = dergonTailSection1.length = 2.0F;
		dergonTailSection2.width = dergonTailSection2.length = 2.0F;
		dergonBody.length = 3.0F;
		dergonBody.width = 5.0F;
		dergonWingRight.length = dergonWingLeft.length = 2.0F;
		dergonWingRight.width = dergonWingLeft.width = 4.0F;

		float f1 = (float) toRadians((
			getMovementOffset(5)[1]
			- getMovementOffset(10)[1]
		) * 10.0F);
		float cosF1 = (float) cos(f1);
		float sinF1 = (float) -sin(f1);
		float yawRad = (float) toRadians(yaw);
		float sinYaw = (float) sin(yawRad);
		float cosYaw = (float) cos(yawRad);

		incrementHitBoxLocation(
			dergonBody,
			(sinYaw / 2),
			0,
			-(cosYaw / 2)
		);

		incrementHitBoxLocation(
			dergonWingRight,
			(cosYaw * 4.5),
			1,
			(sinYaw * 4.5)
		);

		incrementHitBoxLocation(
			dergonWingLeft,
			-(cosYaw * 4.5),
			1,
			-(sinYaw * 4.5)
		);

		if (hurtTicks == 0)
		{
			launchEntities(world.getEntities(this, dergonWingRight.getBoundingBox().grow(4.0D, 4.0D, 4.0D)));
			launchEntities(world.getEntities(this, dergonWingLeft.getBoundingBox().grow(4.0D, 4.0D, 4.0D)));
			hitEntities(world.getEntities(this, dergonHead.getBoundingBox().grow(1.0D, 1.0D, 1.0D)));
		}

		double[] olderPosition = getMovementOffset(5);
		double currentAltitude = getMovementOffset(0)[1];

		float xHeadDirectionIncremented = (float) sin(toRadians(yaw) - bi * 0.01F);
		float zHeadDirectionIncremented = (float) cos(toRadians(yaw) - bi * 0.01F);

		incrementHitBoxLocation(
			dergonHead,
			(xHeadDirectionIncremented * 5.5 * cosF1),
			(currentAltitude - olderPosition[1]) + (sinF1 * 5.5),
			-(zHeadDirectionIncremented * 5.5 * cosF1)
		);

		//Move the tail
		for (int tailNumber = 0; tailNumber < 3; ++tailNumber)
		{
			EntityComplexPart tailSection = null;

			switch (tailNumber)
			{
				case 0: tailSection = dergonTailSection0; break;
				case 1: tailSection = dergonTailSection1; break;
				case 2: tailSection = dergonTailSection2; break;
			}

			double[] oldPosition = getMovementOffset(12 + tailNumber * 2);
			float tailDirection = (float) toRadians(yaw + trimDegrees(oldPosition[0] - olderPosition[0]));
			final float ONE_POINT_FIVE = 1.5F;
			float movementMultiplier = (tailNumber + 1) * 2.0F; // 2, 4, 6

			incrementHitBoxLocation(
				tailSection,
				-(sinYaw * ONE_POINT_FIVE + sin(tailDirection) * movementMultiplier) * cosF1,
				(oldPosition[1] - olderPosition[1]) - ((movementMultiplier + ONE_POINT_FIVE) * sinF1) + 1.5D,
				(cosYaw * ONE_POINT_FIVE + cos(tailDirection) * movementMultiplier) * cosF1
			);
		}
	}

	/**
	 * Damages the dergon based on a specific body part.
	 * Also recalculates the dergon's target location.
	 * Names of this function in various spigot versions:
	 * v1_8_R3 - v1_12_R1: a
	 * @param bodyPart Part of the dergon hit.
	 * @param damageSource Source of the damage.
	 * @param damageValue Amount of damage.
	 * @return True if damage is dealt.
	 */
	@Override
	public boolean a(EntityComplexPart bodyPart, DamageSource damageSource, float damageValue)
	{
		Entity bukkitAttacker = damageSource.getEntity();
		IPlayer attacker = null;
		if (bukkitAttacker instanceof EntityHuman)
			attacker = ObjectWrapper.convert(((EntityHuman) bukkitAttacker).getBukkitEntity());

		// Check if the player is attacking with a punch bow
		if (usingPunchBow(attacker))
		{
			attacker.setVelocity(new Vector(4, 4, 4));
			attacker.addBuff(Buff.Combat.Blindness.duration(15));
			attacker.sendColouredMessage(Config.Message.getDergonPunchback());
			return false;
		}

		// Recalculate target location
		double yawRadian = toRadians(yaw);
		double xDirection = sin(yawRadian);
		double zDirection = cos(yawRadian);
		targetX = locX + ((random.nextDouble() - 0.5) * 2) + (xDirection * 5);
		targetY = locY + (random.nextDouble() * 3) + 1;
		targetZ = locZ + ((random.nextDouble() - 0.5) * 2) - (zDirection * 5);
		targetEntity = null;

		// Only apply damage if the source is a player or an explosion.
		if (attacker != null || damageSource.isExplosion())
		{
			// Do more damage for headshots
			if(bodyPart != dergonHead)
				damageValue = (damageValue / 4) + 1;
			damageEntity(damageSource, damageValue);
		}

		// Spawn in some creatures to help defend the dergon
		if (attacker != null && random.nextFloat() < (Config.getVexChance() / 100))
		{
			ILocation attackerLocation = attacker.getLocation();
			if (attackerLocation == null)
				return true;

			attackerLocation.playSound(Sound.Creature.Ghast.Scream, 1, 0.5F);
			dergonWorld.spawnCreature(attackerLocation, "Vex");
		}

		return true;
	}

	private boolean usingPunchBow(IPlayer player)
	{
		if (player == null)
			return false;

		RunsafeMeta checkItem = player.getItemInMainHand();
		if (checkItem != null && checkItem.is(Item.Combat.Bow))
		{
			for (Map.Entry<RunsafeEnchantment, Integer> enchantment : checkItem.getEnchantments().entrySet())
				if (enchantment.getKey().getName().equals("ARROW_KNOCKBACK"))
					return true;
		}
		checkItem = player.getItemInOffHand();
		if (checkItem != null && checkItem.is(Item.Combat.Bow))
		{
			for (Map.Entry<RunsafeEnchantment, Integer> enchantment : checkItem.getEnchantments().entrySet())
				if (!enchantment.getKey().getName().equals("ARROW_KNOCKBACK"))
					return true;
		}
		return false;
	}

	/**
	 * Launches entities a short distance.
	 * @param list Entities to launch
	 */
	private void launchEntities(List<Entity> list)
	{
		double bodyPosX = (dergonBody.getBoundingBox().a + dergonBody.getBoundingBox().d) / 2.0D;
		double bodyPosZ = (dergonBody.getBoundingBox().c + dergonBody.getBoundingBox().f) / 2.0D;

		for (Entity entity : list)
		{
			if (!(entity instanceof EntityLiving))
				continue;

			double xDistance = entity.locX - bodyPosX;
			double zDistance = entity.locZ - bodyPosZ;
			double distanceSquared = xDistance * xDistance + zDistance * zDistance;

			entity.f( // Add velocity (f in 1.12)
				xDistance / distanceSquared * 4.0D,
				0.20000000298023224D,
				zDistance / distanceSquared * 4.0D
			);
		}
	}

	/**
	 * Attack list of EntityLiving.
	 * @param list Entities to hit
	 */
	private void hitEntities(List<Entity> list)
	{
		for (Entity entity : list)
			if (entity instanceof EntityLiving)
				entity.damageEntity(DamageSource.mobAttack(this), Config.getBaseDamage());
	}

	/**
	 * Trims down a degree value to between -180 and 180.
	 * @param degreeValue Number to trim.
	 * @return Trimmed degree value.
	 */
	private double trimDegrees(double degreeValue)
	{
		degreeValue %= 360.0D;

		if(degreeValue >= 180.0D)
			return degreeValue - 360.0D;

		if(degreeValue < -180.0D)
			return degreeValue + 360.0D;

		return degreeValue;
	}

	/**
	 * Gets a movement offset. Useful for calculating trailing tail and neck positions.
	 * @param bufferIndexOffset Offset for the ring buffer.
	 * @return A double [2] array with movement offsets.
	 * [0] = yaw offset, [1] = y offset
	 */
	private double[] getMovementOffset(int bufferIndexOffset)
	{
		int j = positionBufferIndex - bufferIndexOffset & 63;
		double[] movementOffset = new double[2];
		// Set yaw offset
		movementOffset[0] = positionBuffer[j][0];
		// Set y offset.
		movementOffset[1] = positionBuffer[j][1];

		return movementOffset;
	}

	/**
	 * Damage the dergon.
	 * Overrides method in {@link EntityLiving}
	 * Names of this function in various spigot versions:
	 * v1_12_R1: damageEntity0
	 * @param source damage source
	 * @param damageValue Amount of damage
	 * @return True if damaged, false if not damaged.
	 */
	@Override
	protected boolean damageEntity0(DamageSource source, float damageValue)
	{
		Entity bukkitAttacker = source.getEntity();
		if (bukkitAttacker == null || (!(bukkitAttacker instanceof EntityHuman) && !source.isExplosion()))
			return false;

		IPlayer attacker = null;
		if (bukkitAttacker instanceof EntityHuman)
			attacker = ObjectWrapper.convert(((EntityHuman) bukkitAttacker).getBukkitEntity());
		// Check if the player is attacking with a punch bow
		if (usingPunchBow(attacker))
		{
			attacker.setVelocity(new Vector(4, 4, 4));
			attacker.addBuff(Buff.Combat.Blindness.duration(15));
			attacker.sendColouredMessage(Config.Message.getDergonPunchback());
			return false;
		}

		if (ridingPlayer == null || !isRidingPlayer(bukkitAttacker.getName()))
			return super.damageEntity0(source, handler.handleDergonDamage(this, source, damageValue));

		return false;
	}

	/**
	 * Handles dergon death ticks.
	 * Overrides method in EntityEnderDragon which overrides method in EntityLiving
	 * Names of this function in various spigot versions:
	 * v1_12_R1: bO
	 */
	@Override
	protected void bO()
	{
		if (dead)
			return;

		// Increment death ticks.
		this.deathTicks++;

		// Make explosion particles for every step of the death animation.
		world.addParticle(
			EnumParticle.EXPLOSION_HUGE,
			locX + (random.nextFloat() - 0.5) * 8,
			locY + (random.nextFloat() - 0.5) * 4 + 2,
			locZ + (random.nextFloat() - 0.5) * 8,
			0, 0, 0
		);

		// Make explosion particles when the dergon is almost dead.
		if (this.deathTicks >= 180 && this.deathTicks <= 200)
			world.addParticle(
				EnumParticle.EXPLOSION_HUGE,
				locX + (random.nextFloat() - 0.5) * 8,
				locY + (random.nextFloat() - 0.5) * 4 + 2,
				locZ + (random.nextFloat() - 0.5) * 8,
				0, 0, 0
			);

		// Play the death sound as the death animation starts.
		if (this.deathTicks == 1)
			getLocation().playSound(
				Sound.Creature.EnderDragon.Death, 32.0F, 1.0F
			);

		// When animation is finished, slay the dergon.
		if(this.deathTicks == 200)
		{
			die();
			handler.handleDergonDeath(this, false);
		}
	}

	/**
	 * Gets all hit-boxes.
	 * Overrides method in Entity.class.
	 * Names of this method in various spigot versions:
	 * v_12_R1: bb
	 * @return All hit-boxes.
	 */
	@Override
	public Entity[] bb()
	{
		return this.children;
	}

	/**
	 * Gets the current world.
	 * Required by the IComplex interface.
	 * Method name stays the same up to v1_12_R1.
	 * @return The current world.
	 */
	@Override
	public World a()
	{
		return world;
	}

	/**
	 * Plays the idle sound.
	 * Names of this method in different spigot versions:
	 * v1_12_R1: F
	 * @return null
	 */
	@Override
	protected SoundEffect F()
	{
		getLocation().playSound(Sound.Creature.EnderDragon.Growl, 5, 1);
		return null;
	}

	/**
	 * Plays the hurt sound.
	 * Names of this method in various spigot versions:
	 * v1_12_R1 d(DamageSource), returns SoundEffect
	 * @return null
	 */
	@Override
	protected SoundEffect d(DamageSource damageSource)
	{
		getLocation().playSound(Sound.Creature.EnderDragon.Hit, 5, 1);
		return null;
	}

	/**
	 * Gets the world the dergon is in.
	 * @return World the dergon is in.
	 */
	public IWorld getDergonWorld()
	{
		return dergonWorld;
	}

	public IPlayer getCurrentTarget()
	{
		return targetEntity;
	}

	public ILocation getTargetFlyToLocation()
	{
		return dergonWorld.getLocation(targetX, targetY, targetZ);
	}

	public ILocation getLocation()
	{
		return dergonWorld.getLocation(locX, locY, locZ);
	}

	/**
	 * Increments the hit-box location of a dergon's body part.
	 * @param bodyPart Part to change the location of.
	 * @param xIncrement How far to move in the X direction.
	 * @param yIncrement How far to move in the Y direction.
	 * @param zIncrement How far to move in the Z direction.
	 */
	private void incrementHitBoxLocation(EntityComplexPart bodyPart, double xIncrement, double yIncrement, double zIncrement)
	{
		bodyPart.B_(); //t_() means on update. Behavior changes slightly in 1.12 (B_())
		bodyPart.setPositionRotation(
			locX + xIncrement, locY + yIncrement, locZ + zIncrement, 0, 0
		);
	}

	/**
	 * Checks if player should be targeted.
	 * Will not return true if player is vanished, dead, in creative, or in spectator mode.
	 * @param player Person to consider targeting.
	 * @return True if they can be targeted.
	 */
	private boolean isInvalidTarget(IPlayer player)
	{
		return player.isVanished()
			|| player.isDead()
			|| !player.isSurvivalist();
	}

	private boolean isRidingPlayer(String playerName)
	{
		return ridingPlayer != null && ridingPlayer.getName().equals(playerName);
	}

	public int getDergonID()
	{
		return dergonID;
	}

	public ILocation getSpawnLocation()
	{
		return spawnLocation;
	}

	/*
	 * Dergon bodily appendages.
	 * Only their hit-boxes.
	 */
	private final EntityComplexPart[] children;
	private final EntityComplexPart dergonHead;
	private final EntityComplexPart dergonBody;
	private final EntityComplexPart dergonWingRight;
	private final EntityComplexPart dergonWingLeft;
	private final EntityComplexPart dergonTailSection0;
	private final EntityComplexPart dergonTailSection1;
	private final EntityComplexPart dergonTailSection2;

	// Target coordinates to fly to.
	private double targetX = 0;
	private double targetY = 100;
	private double targetZ = 0;

	// Store the dergon's last 64 vertical and yaw positions.
	private final double[][] positionBuffer = new double[64][2];
	private int positionBufferIndex = -1;

 	private float randomYawVelocity = 0;
	private int deathTicks = 0;
	private int idleTicks = 0;
	private IPlayer targetEntity;
	private final DergonHandler handler;
	private ILocation spawnLocation;
	private ILocation flyOffLocation;
	private final IWorld dergonWorld;
	private final Random random = new Random();
	private IPlayer ridingPlayer = null;
	private int dergonID;
}
