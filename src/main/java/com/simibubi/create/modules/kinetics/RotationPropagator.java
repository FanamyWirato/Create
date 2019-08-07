package com.simibubi.create.modules.kinetics;

import java.util.LinkedList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.modules.kinetics.base.IRotate;
import com.simibubi.create.modules.kinetics.base.KineticTileEntity;
import com.simibubi.create.modules.kinetics.relays.GearboxTileEntity;
import com.simibubi.create.modules.kinetics.relays.GearshifterTileEntity;

import net.minecraft.block.BlockState;
import net.minecraft.state.IProperty;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RotationPropagator {

	/**
	 * Determines the change in rotation between two attached kinetic entities. For
	 * instance, an axis connection returns 1 while a 1-to-1 gear connection
	 * reverses the rotation and therefore returns -1.
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	private static float getRotationSpeedModifier(KineticTileEntity from, KineticTileEntity to) {
		final BlockState stateFrom = from.getBlockState();
		final BlockState stateTo = to.getBlockState();
		final IRotate definitionFrom = (IRotate) stateFrom.getBlock();
		final IRotate definitionTo = (IRotate) stateTo.getBlock();
		final BlockPos diff = to.getPos().subtract(from.getPos());
		final Direction facingFromTo = Direction.getFacingFromVector(diff.getX(), diff.getY(), diff.getZ());
		final World world = from.getWorld();
		IProperty<Axis> axisProperty = BlockStateProperties.AXIS;
		boolean connectedByAxis = definitionFrom.isAxisTowards(world, from.getPos(), stateFrom, facingFromTo)
				&& definitionTo.isAxisTowards(world, to.getPos(), stateTo, facingFromTo.getOpposite());

		// Gearbox <-> Gearbox
		if (from instanceof GearboxTileEntity && to instanceof GearboxTileEntity)
			return 0;

		// Gearbox -> Axis
		if (from instanceof GearboxTileEntity) {
			if (!connectedByAxis)
				return 0;
			if (!from.hasSource())
				return 1;

			Direction sourceFacing = from.getSourceFacing();
			if (facingFromTo.getAxis() == sourceFacing.getAxis())
				return facingFromTo == sourceFacing ? 1 : -1;
			else
				return facingFromTo.getAxisDirection() == sourceFacing.getAxisDirection() ? -1 : 1;
		}

		// Axis -> Gearbox
		if (to instanceof GearboxTileEntity) {
			if (!connectedByAxis)
				return 0;
			if (!to.hasSource())
				return 1;

			Direction sourceFacing = to.getSourceFacing();
			if (facingFromTo.getAxis() == sourceFacing.getAxis())
				return facingFromTo.getOpposite() == sourceFacing ? 1 : -1;
			else
				return facingFromTo.getAxisDirection() == sourceFacing.getAxisDirection() ? 1 : -1;
		}

		if (from instanceof GearshifterTileEntity) {
			if (!connectedByAxis)
				return 0;

			// Gearshifter -> Gearshifter
			if (to instanceof GearshifterTileEntity) {
				int fromReversed = from.hasSource() && from.getSourceFacing() != facingFromTo
						&& stateFrom.get(BlockStateProperties.POWERED) ? -1 : 1;
				int toReversed = to.hasSource() && to.getSourceFacing() != facingFromTo.getOpposite()
						&& stateTo.get(BlockStateProperties.POWERED) ? -1 : 1;
				return fromReversed * toReversed;
			}

			// Gearshifter -> Axis
			if (!from.hasSource())
				return 1;

			Direction sourceFacing = from.getSourceFacing();
			return sourceFacing == facingFromTo ? 1 : stateFrom.get(BlockStateProperties.POWERED) ? -1 : 1;
		}

		// Axis -> Gearshifter
		if (to instanceof GearshifterTileEntity) {
			if (!connectedByAxis)
				return 0;
			if (!to.hasSource())
				return 1;

			Direction sourceFacing = to.getSourceFacing();
			return sourceFacing == facingFromTo.getOpposite() ? 1 : stateTo.get(BlockStateProperties.POWERED) ? -1 : 1;
		}

		// Axis <-> Axis
		if (connectedByAxis)
			return 1;

		// Large Gear -> Gear
		if (AllBlocks.LARGE_GEAR.typeOf(from.getBlockState()) && AllBlocks.GEAR.typeOf(to.getBlockState())) {
			Axis axisFrom = stateFrom.get(axisProperty);
			if (axisFrom == stateTo.get(axisProperty)) {
				if (axisFrom.getCoordinate(diff.getX(), diff.getY(), diff.getZ()) == 0) {
					for (Axis axis : Axis.values()) {
						if (axis == axisFrom)
							continue;
						if (Math.abs(axis.getCoordinate(diff.getX(), diff.getY(), diff.getZ())) != 1)
							return 0;
					}
					return -2f;
				}
			}
		}

		// Gear -> Large Gear
		if (AllBlocks.GEAR.typeOf(from.getBlockState()) && AllBlocks.LARGE_GEAR.typeOf(to.getBlockState())) {
			Axis axisFrom = stateFrom.get(axisProperty);
			if (axisFrom == stateTo.get(axisProperty)) {
				if (axisFrom.getCoordinate(diff.getX(), diff.getY(), diff.getZ()) == 0) {
					for (Axis axis : Axis.values()) {
						if (axis == axisFrom)
							continue;
						if (Math.abs(axis.getCoordinate(diff.getX(), diff.getY(), diff.getZ())) != 1)
							return 0;
					}
					return -.5f;
				}
			}
		}

		// Gear <-> Gear
		if (definitionFrom.isGearTowards(world, from.getPos(), stateFrom, facingFromTo)
				&& definitionTo.isGearTowards(world, to.getPos(), stateTo, facingFromTo.getOpposite())) {
			if (diff.manhattanDistance(BlockPos.ZERO) != 1)
				return 0;
			if (AllBlocks.LARGE_GEAR.typeOf(to.getBlockState()))
				return 0;
			if (stateFrom.get(axisProperty) == stateTo.get(axisProperty))
				return -1;
		}

		return 0;
	}

	/**
	 * Insert the added position to the kinetic network.
	 * 
	 * @param worldIn
	 * @param pos
	 */
	public static void handleAdded(World worldIn, BlockPos pos, KineticTileEntity addedTE) {
		if (worldIn.isRemote)
			return;
		if (!worldIn.isAreaLoaded(pos, 1))
			return;

		if (addedTE.getSpeed() != 0) {
			propagateNewSource(addedTE);
			return;
		}

		for (KineticTileEntity neighbourTE : getConnectedNeighbours(addedTE)) {
			final float speedModifier = getRotationSpeedModifier(neighbourTE, addedTE);

			if (neighbourTE.getSpeed() == 0)
				continue;
			if (neighbourTE.hasSource() && neighbourTE.getSource().equals(addedTE.getPos())) {
				addedTE.setSpeed(neighbourTE.getSpeed() * speedModifier);
				addedTE.notifyBlockUpdate();
				continue;
			}

			addedTE.setSpeed(neighbourTE.getSpeed() * speedModifier);
			addedTE.setSource(neighbourTE.getPos());
			addedTE.notifyBlockUpdate();
			propagateNewSource(addedTE);
			return;
		}
	}

	/**
	 * Search for sourceless networks attached to the given entity and update them.
	 * 
	 * @param updateTE
	 */
	private static void propagateNewSource(KineticTileEntity updateTE) {
		BlockPos pos = updateTE.getPos();
		World world = updateTE.getWorld();

		for (KineticTileEntity neighbourTE : getConnectedNeighbours(updateTE)) {
			final float newSpeed = updateTE.getSpeed() * getRotationSpeedModifier(updateTE, neighbourTE);

			if ((neighbourTE.isSource())
					|| neighbourTE.hasSource() && !neighbourTE.getSource().equals(updateTE.getPos())) {
				if (neighbourTE.getSpeed() != newSpeed) {
					world.destroyBlock(pos, true);
					return;
				}
				continue;
			}

			if (neighbourTE.getSpeed() == newSpeed)
				continue;

			neighbourTE.setSpeed(newSpeed);
			neighbourTE.setSource(updateTE.getPos());
			neighbourTE.notifyBlockUpdate();
			propagateNewSource(neighbourTE);

		}
	}

	/**
	 * Remove the given entity from the network.
	 * 
	 * @param worldIn
	 * @param pos
	 * @param removedTE
	 */
	public static void handleRemoved(World worldIn, BlockPos pos, KineticTileEntity removedTE) {
		if (worldIn.isRemote)
			return;
		if (removedTE.getSpeed() == 0)
			return;

		for (BlockPos neighbourPos : getPotentialNeighbourLocations(removedTE)) {
			BlockState neighbourState = worldIn.getBlockState(neighbourPos);
			if (!(neighbourState.getBlock() instanceof IRotate))
				continue;

			final KineticTileEntity neighbourTE = (KineticTileEntity) worldIn.getTileEntity(neighbourPos);
			if (!neighbourTE.hasSource() || !neighbourTE.getSource().equals(pos) || neighbourTE.isSource())
				continue;

			propagateMissingSource(neighbourTE);
		}

	}

	/**
	 * Clear the entire subnetwork depending on the given entity and find a new
	 * source
	 * 
	 * @param updateTE
	 */
	private static void propagateMissingSource(KineticTileEntity updateTE) {
		final World world = updateTE.getWorld();

		List<KineticTileEntity> potentialNewSources = new LinkedList<>();
		List<BlockPos> frontier = new LinkedList<>();
		frontier.add(updateTE.getPos());

		while (!frontier.isEmpty()) {
			final BlockPos pos = frontier.remove(0);
			final KineticTileEntity currentTE = (KineticTileEntity) world.getTileEntity(pos);

			currentTE.removeSource();
			currentTE.notifyBlockUpdate();

			for (KineticTileEntity neighbourTE : getConnectedNeighbours(currentTE)) {
				if (neighbourTE.isSource()) {
					potentialNewSources.add(neighbourTE);
					continue;
				}

				if (!neighbourTE.hasSource())
					continue;

				if (!neighbourTE.getSource().equals(pos)) {
					potentialNewSources.add(neighbourTE);
					continue;
				}

				frontier.add(neighbourTE.getPos());
			}
		}

		for (KineticTileEntity newSource : potentialNewSources) {
			if (newSource.hasSource() || newSource.isSource()) {
				propagateNewSource(newSource);
				return;
			}
		}
	}

	private static KineticTileEntity findConnectedNeighbour(KineticTileEntity te, BlockPos neighbourPos) {
		BlockState neighbourState = te.getWorld().getBlockState(neighbourPos);
		if (!(neighbourState.getBlock() instanceof IRotate))
			return null;

		KineticTileEntity neighbour = (KineticTileEntity) te.getWorld().getTileEntity(neighbourPos);
		if (getRotationSpeedModifier(te, neighbour) == 0)
			return null;
		return neighbour;
	}

	private static List<KineticTileEntity> getConnectedNeighbours(KineticTileEntity te) {
		List<KineticTileEntity> neighbours = new LinkedList<>();
		for (BlockPos neighbourPos : getPotentialNeighbourLocations(te)) {
			final KineticTileEntity neighbourTE = findConnectedNeighbour(te, neighbourPos);
			if (neighbourTE == null)
				continue;

			neighbours.add(neighbourTE);
		}
		return neighbours;
	}

	private static List<BlockPos> getPotentialNeighbourLocations(KineticTileEntity te) {
		List<BlockPos> neighbours = new LinkedList<>();

		if (!te.getWorld().isAreaLoaded(te.getPos(), 1))
			return neighbours;

		for (Direction facing : Direction.values()) {
			neighbours.add(te.getPos().offset(facing));
		}

		// gears can interface diagonally
		BlockState blockState = te.getBlockState();
		if (AllBlocks.GEAR.typeOf(blockState) || AllBlocks.LARGE_GEAR.typeOf(blockState)) {
			Axis axis = blockState.get(BlockStateProperties.AXIS);
			BlockPos.getAllInBox(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1)).forEach(offset -> {
				if (axis.getCoordinate(offset.getX(), offset.getY(), offset.getZ()) != 0)
					return;
				if (offset.distanceSq(0, 0, 0, false) != BlockPos.ZERO.distanceSq(1, 1, 0, false))
					return;
				neighbours.add(te.getPos().add(offset));
			});
		}

		return neighbours;
	}

}