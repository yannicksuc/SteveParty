public static VoxelShape makeShape() {
	return VoxelShapes.union(
		VoxelShapes.cuboid(0, 0, 0.125, 1, 0.875, 1),
		VoxelShapes.cuboid(0, 0.875, 0, 0.25, 1, 1),
		VoxelShapes.cuboid(0.25, 0.875, 0, 0.5, 1, 1),
		VoxelShapes.cuboid(0.5, 0.875, 0, 0.75, 1, 1),
		VoxelShapes.cuboid(0.75, 0.875, 0, 1, 1, 1),
		VoxelShapes.cuboid(0, 0.6875, 0, 0.25, 0.875, 0),
		VoxelShapes.cuboid(0.25, 0.6875, 0, 0.5, 0.875, 0),
		VoxelShapes.cuboid(0.5, 0.6875, 0, 0.75, 0.875, 0),
		VoxelShapes.cuboid(0.75, 0.6875, 0, 1, 0.875, 0)
	);
}