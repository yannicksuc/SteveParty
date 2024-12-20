package fr.lordfinn.steveparty.utils;

import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.Collection;

public class VoxelShapeutils {

    public static VoxelShape combineAll(Collection<VoxelShape> shapes) {
        VoxelShape result = VoxelShapes.empty();
        for(VoxelShape shape : shapes) {
            result = VoxelShapes.combine(result, shape, BooleanBiFunction.OR);
        }
        return result.simplify();
    }

    public static VoxelShape[] getRotatedShapes(VoxelShape source) {
        VoxelShape shapeNorth = rotate(source, Direction.NORTH);
        VoxelShape shapeEast = rotate(source, Direction.EAST);
        VoxelShape shapeSouth = rotate(source, Direction.SOUTH);
        VoxelShape shapeWest = rotate(source, Direction.WEST);
        return new VoxelShape[] { shapeSouth, shapeWest, shapeNorth, shapeEast };
    }

    public static VoxelShape rotate(VoxelShape source, Direction direction) {
        double[] adjustedValues = adjustValues(direction, source.getMin(Direction.Axis.X), source.getMin(Direction.Axis.Z), source.getMax(Direction.Axis.X), source.getMax(Direction.Axis.Z));
        return VoxelShapes.cuboid(adjustedValues[0], source.getMin(Direction.Axis.Y), adjustedValues[1], adjustedValues[2], source.getMax(Direction.Axis.Y), adjustedValues[3]);
    }

    public static double[] adjustValues(Direction direction, double minX, double minZ, double maxX, double maxZ)
    {
        switch(direction)
        {
            case WEST:
                double var_temp_1 = minX;
                minX = 1.0F - maxX;
                double var_temp_2 = minZ;
                minZ = 1.0F - maxZ;
                maxX = 1.0F - var_temp_1;
                maxZ = 1.0F - var_temp_2;
                break;
            case NORTH:
                double var_temp_3 = minX;
                minX = minZ;
                minZ = 1.0F - maxX;
                maxX = maxZ;
                maxZ = 1.0F - var_temp_3;
                break;
            case SOUTH:
                double var_temp_4 = minX;
                minX = 1.0F - maxZ;
                double var_temp_5 = minZ;
                minZ = var_temp_4;
                double var_temp_6 = maxX;
                maxX = 1.0F - var_temp_5;
                maxZ = var_temp_6;
                break;
            default:
                break;
        }
        return new double[]{minX, minZ, maxX, maxZ};
    }

    public static Box[] shapeToBoxes(VoxelShape shape) {
        ArrayList<Box> result = new ArrayList<>();
        shape.forEachBox((x1, y1, z1, x2, y2, z2)->{
            result.add(new Box(x1,y1,z1,x2,y2,z2));
        });
        return result.toArray(new Box[0]);
    }

    public static VoxelShape[] rotationsOf(VoxelShape shape) {
        return rotationsOf(shapeToBoxes(shape));
    }

    public static VoxelShape[] rotationsOf(Box... boxes) {
        VoxelShape[] result = new VoxelShape[4];
        result[0] = shape(boxes);              //north
        result[1] = shape(rotate(180, boxes)); //south
        result[2] = shape(rotate(90, boxes));  //west
        result[3] = shape(rotate(270, boxes)); //east

        return result;
    }

    public static VoxelShape shape(Box... boxes) {
        VoxelShape result = VoxelShapes.empty();
        for(Box box : boxes) {
            result = VoxelShapes.union(result, VoxelShapes.cuboid(box));
        }
        return result;
    }

    public static Box[] rotate(float amount, Box[] sources) {
        Box[] result = new Box[sources.length];
        for(int i=0; i<result.length; i++) {
            result[i] = rotateHorizontal(sources[i], amount);
        }
        return result;
    }

    public static Box rotateHorizontal(Box template, float amount) {
        //These first two are enough for orthogonal rotations
        MutableVec2d a = new MutableVec2d(template.minX, template.minZ).rotate(amount);
        MutableVec2d b = new MutableVec2d(template.maxX, template.maxZ).rotate(amount);
        //These cover odd angles
        MutableVec2d c = new MutableVec2d(template.minX, template.maxZ).rotate(amount);
        MutableVec2d d = new MutableVec2d(template.maxX, template.minZ).rotate(amount);

        double x1 = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        double z1 = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        double x2 = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        double z2 = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));

        return new Box(x1, template.minY, z1, x2, template.maxY, z2);
    }

    public static class MutableVec2d {
        public double x;
        public double y;

        public MutableVec2d(double x, double y) {
            this.x = x;
            this.y = y;
        }

        /** Rotates around the Y axis at (0.5, 0, 0.5) */
        public MutableVec2d rotate(double amount) {
            amount *= Math.PI/180.0; //Convert amount to radians because it's definitely in degrees
            double tx = x-0.5;
            double ty = y-0.5;

            double theta = Math.atan2(ty, tx);
            double r = Math.sqrt(tx*tx+ty*ty);

            x = r * Math.cos(theta-amount); x+=0.5;
            y = r * Math.sin(theta-amount); y+=0.5;

            return this;
        }
    }
}
