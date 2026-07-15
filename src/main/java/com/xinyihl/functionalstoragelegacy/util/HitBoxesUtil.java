package com.xinyihl.functionalstoragelegacy.util;

import com.xinyihl.functionalstoragelegacy.common.block.DrawerAttachment;
import com.xinyihl.functionalstoragelegacy.common.block.DrawerFaceLayout;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.*;

public class HitBoxesUtil {
    private static final double DEFAULT_REACH_DISTANCE = 5.0D;
    private static final double HIT_EPSILON = 1.0E-3D;
    private static final EnumMap<DrawerFaceLayout, List<AxisAlignedBB>> BASE_HIT_BOXES = new EnumMap<>(DrawerFaceLayout.class);

    static {
        BASE_HIT_BOXES.put(DrawerFaceLayout.X_1, Collections.singletonList(
                new AxisAlignedBB(1 / 16D, 1 / 16D, 0, 15 / 16D, 15 / 16D, 1 / 16D)
        ));
        BASE_HIT_BOXES.put(DrawerFaceLayout.X_2, Arrays.asList(
                new AxisAlignedBB(1 / 16D, 9 / 16D, 0, 15 / 16D, 15 / 16D, 1 / 16D),
                new AxisAlignedBB(1 / 16D, 1 / 16D, 0, 15 / 16D, 7 / 16D, 1 / 16D)
        ));
        BASE_HIT_BOXES.put(DrawerFaceLayout.X_3, Arrays.asList(
                new AxisAlignedBB(1 / 16D, 9 / 16D, 0, 15 / 16D, 15 / 16D, 1 / 16D),
                new AxisAlignedBB(9 / 16D, 1 / 16D, 0, 15 / 16D, 7 / 16D, 1 / 16D),
                new AxisAlignedBB(1 / 16D, 1 / 16D, 0, 7 / 16D, 7 / 16D, 1 / 16D)
        ));
        BASE_HIT_BOXES.put(DrawerFaceLayout.X_4, Arrays.asList(
                new AxisAlignedBB(9 / 16D, 9 / 16D, 0, 15 / 16D, 15 / 16D, 1 / 16D),
                new AxisAlignedBB(1 / 16D, 9 / 16D, 0, 7 / 16D, 15 / 16D, 1 / 16D),
                new AxisAlignedBB(9 / 16D, 1 / 16D, 0, 15 / 16D, 7 / 16D, 1 / 16D),
                new AxisAlignedBB(1 / 16D, 1 / 16D, 0, 7 / 16D, 7 / 16D, 1 / 16D)
        ));
    }

    public static int getHorizontalFacingIndex(EnumFacing facing) {
        switch (facing) {
            case NORTH:
                return 0;
            case EAST:
                return 1;
            case SOUTH:
                return 2;
            case WEST:
            default:
                return 3;
        }
    }

    public static EnumFacing getPlacementFacingFromRay(@Nullable EntityLivingBase placer) {
        if (placer == null) {
            return EnumFacing.NORTH;
        }
        Vec3d lookVec = placer.getLookVec();
        return EnumFacing.getFacingFromVector((float) -lookVec.x, (float) -lookVec.y, (float) -lookVec.z);
    }

    public static DrawerAttachment getAttachmentForPlacement(EnumFacing placementFacing) {
        if (placementFacing == EnumFacing.UP) {
            return DrawerAttachment.FLOOR;
        }
        if (placementFacing == EnumFacing.DOWN) {
            return DrawerAttachment.CEILING;
        }
        return DrawerAttachment.WALL;
    }

    public static EnumFacing getHorizontalFacingForPlacement(DrawerAttachment attachment, EnumFacing placementFacing, @Nullable EntityLivingBase placer) {
        if (attachment == DrawerAttachment.WALL && placementFacing.getAxis().isHorizontal()) {
            return placementFacing;
        }
        EnumFacing playerHorizontalFacing = placer != null ? placer.getHorizontalFacing() : EnumFacing.NORTH;
        if (attachment == DrawerAttachment.FLOOR) {
            return playerHorizontalFacing.getOpposite();
        }
        if (attachment == DrawerAttachment.CEILING) {
            return playerHorizontalFacing.getOpposite();
        }
        return playerHorizontalFacing;
    }

    public static double getPlayerReachDistance(EntityPlayer player) {
        if (player == null) {
            return DEFAULT_REACH_DISTANCE;
        }
        if (player.getEntityAttribute(EntityPlayer.REACH_DISTANCE) != null) {
            return player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        }
        return DEFAULT_REACH_DISTANCE;
    }

    public static boolean containsHitVec(AxisAlignedBB hitBox, Vec3d localHitVec) {
        return localHitVec.x >= hitBox.minX - HIT_EPSILON && localHitVec.x <= hitBox.maxX + HIT_EPSILON
                && localHitVec.y >= hitBox.minY - HIT_EPSILON && localHitVec.y <= hitBox.maxY + HIT_EPSILON
                && localHitVec.z >= hitBox.minZ - HIT_EPSILON && localHitVec.z <= hitBox.maxZ + HIT_EPSILON;
    }

    public static List<AxisAlignedBB> buildHitBoxes(DrawerFaceLayout layout, DrawerAttachment attachment, EnumFacing horizontalFacing) {
        List<AxisAlignedBB> baseHitBoxes = BASE_HIT_BOXES.get(layout);
        if (baseHitBoxes == null || baseHitBoxes.isEmpty()) {
            return Collections.emptyList();
        }

        EnumFacing frontFacing = getFrontFacingForPlacement(attachment, horizontalFacing);
        EnumFacing upDirection = getUpDirectionForFace(attachment, horizontalFacing);
        EnumFacing leftDirection = getLeftDirection(frontFacing, upDirection);
        EnumFacing inwardDirection = frontFacing.getOpposite();

        List<AxisAlignedBB> transformedHitBoxes = new ArrayList<>(baseHitBoxes.size());
        for (AxisAlignedBB baseHitBox : baseHitBoxes) {
            transformedHitBoxes.add(transformHitBox(baseHitBox, leftDirection, upDirection, inwardDirection));
        }
        return Collections.unmodifiableList(transformedHitBoxes);
    }

    private static EnumFacing getFrontFacingForPlacement(DrawerAttachment attachment, EnumFacing horizontalFacing) {
        if (attachment == DrawerAttachment.FLOOR) {
            return EnumFacing.UP;
        }
        if (attachment == DrawerAttachment.CEILING) {
            return EnumFacing.DOWN;
        }
        return horizontalFacing;
    }

    private static EnumFacing getUpDirectionForFace(DrawerAttachment attachment, EnumFacing horizontalFacing) {
        if (attachment == DrawerAttachment.FLOOR) {
            return horizontalFacing.getOpposite();
        }
        if (attachment == DrawerAttachment.CEILING) {
            return horizontalFacing;
        }
        return EnumFacing.UP;
    }

    private static EnumFacing getLeftDirection(EnumFacing frontFacing, EnumFacing upDirection) {
        int crossX = frontFacing.getYOffset() * upDirection.getZOffset() - frontFacing.getZOffset() * upDirection.getYOffset();
        int crossY = frontFacing.getZOffset() * upDirection.getXOffset() - frontFacing.getXOffset() * upDirection.getZOffset();
        int crossZ = frontFacing.getXOffset() * upDirection.getYOffset() - frontFacing.getYOffset() * upDirection.getXOffset();
        return EnumFacing.getFacingFromVector(crossX, crossY, crossZ);
    }

    private static AxisAlignedBB transformHitBox(AxisAlignedBB localHitBox, EnumFacing leftDirection, EnumFacing upDirection, EnumFacing inwardDirection) {
        Vec3d[] corners = new Vec3d[]{
                transformPoint(localHitBox.minX, localHitBox.minY, localHitBox.minZ, leftDirection, upDirection, inwardDirection),
                transformPoint(localHitBox.minX, localHitBox.minY, localHitBox.maxZ, leftDirection, upDirection, inwardDirection),
                transformPoint(localHitBox.minX, localHitBox.maxY, localHitBox.minZ, leftDirection, upDirection, inwardDirection),
                transformPoint(localHitBox.minX, localHitBox.maxY, localHitBox.maxZ, leftDirection, upDirection, inwardDirection),
                transformPoint(localHitBox.maxX, localHitBox.minY, localHitBox.minZ, leftDirection, upDirection, inwardDirection),
                transformPoint(localHitBox.maxX, localHitBox.minY, localHitBox.maxZ, leftDirection, upDirection, inwardDirection),
                transformPoint(localHitBox.maxX, localHitBox.maxY, localHitBox.minZ, leftDirection, upDirection, inwardDirection),
                transformPoint(localHitBox.maxX, localHitBox.maxY, localHitBox.maxZ, leftDirection, upDirection, inwardDirection)
        };

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Vec3d corner : corners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }

        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3d transformPoint(double localX, double localY, double localZ, EnumFacing leftDirection, EnumFacing upDirection, EnumFacing inwardDirection) {
        double originX = leftDirection.getXOffset() < 0 || upDirection.getXOffset() < 0 || inwardDirection.getXOffset() < 0 ? 1.0D : 0.0D;
        double originY = leftDirection.getYOffset() < 0 || upDirection.getYOffset() < 0 || inwardDirection.getYOffset() < 0 ? 1.0D : 0.0D;
        double originZ = leftDirection.getZOffset() < 0 || upDirection.getZOffset() < 0 || inwardDirection.getZOffset() < 0 ? 1.0D : 0.0D;

        return new Vec3d(
                originX + localX * leftDirection.getXOffset() + localY * upDirection.getXOffset() + localZ * inwardDirection.getXOffset(),
                originY + localX * leftDirection.getYOffset() + localY * upDirection.getYOffset() + localZ * inwardDirection.getYOffset(),
                originZ + localX * leftDirection.getZOffset() + localY * upDirection.getZOffset() + localZ * inwardDirection.getZOffset()
        );
    }
}
