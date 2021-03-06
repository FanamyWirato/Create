package com.simibubi.create.content.contraptions.components.structureMovement;

import java.util.function.BiPredicate;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.simibubi.create.foundation.tileEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.MatrixStacker;

import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.Vec3d;

public class DirectionalExtenderScrollOptionSlot extends CenteredSideValueBoxTransform {

	public DirectionalExtenderScrollOptionSlot(BiPredicate<BlockState, Direction> allowedDirections) {
		super(allowedDirections);
	}

	@Override
	protected Vec3d getLocalOffset(BlockState state) {
		return super.getLocalOffset(state)
				.add(new Vec3d(state.get(BlockStateProperties.FACING).getDirectionVec()).scale(-2 / 16f));
	}

	@Override
	protected void rotate(BlockState state, MatrixStack ms) {
		if (!direction.getAxis().isHorizontal())
			MatrixStacker.of(ms).rotateY(AngleHelper.horizontalAngle(state.get(BlockStateProperties.FACING)) - 90);
		super.rotate(state, ms);
	}
}