package com.example.examplemod.util;

import com.mojang.math.Transformation;
import com.example.examplemod.item.DisplaySelection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public final class DisplayTransform {
    public static final float MAX_TRANSLATION = 128.0F;
    public static final float MAX_ROTATION = 180.0F;
    public static final float MAX_SCALE = 64.0F;
    public static final Values DEFAULT = new Values(0, 0, 0, 0, 0, 0, 1, 1, 1);

    private DisplayTransform() {
    }

    public static void initialize(Display display, BlockState state, DisplaySelection selection) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        if (display instanceof Display.BlockDisplay) {
            tag.put("block_state", NbtUtils.writeBlockState(selection.blockState(state)));
        } else if (display instanceof Display.ItemDisplay) {
            tag.put("item", selection.itemStack(state).save(new CompoundTag()));
            tag.putString("item_display", "fixed");
        } else if (display instanceof Display.TextDisplay) {
            tag.putString("text", Component.Serializer.toJson(selection.text(state)));
            tag.putInt("line_width", 10000);
        }
        tag.putFloat("width", 1.0F);
        tag.putFloat("height", 1.0F);
        putTransformation(tag, display, DEFAULT);
        display.load(tag);
    }

    public static void apply(Display display, Values values) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        putTransformation(tag, display, values);
        display.load(tag);
    }

    public static Values read(Display display) {
        Transformation transformation = readTransformation(display);
        Vector3f scale = transformation.getScale();
        Vector3f rotation = transformation.getLeftRotation()
                .mul(transformation.getRightRotation())
                .getEulerAnglesXYZ(new Vector3f());
        Vector3f center = transformation.getMatrix().transformPosition(pivot(display)).sub(baseCenter(display));

        return new Values(
                center.x, center.y, center.z,
                (float) Math.toDegrees(rotation.x),
                (float) Math.toDegrees(rotation.y),
                (float) Math.toDegrees(rotation.z),
                scale.x, scale.y, scale.z);
    }

    public static AABB visualBounds(Display display) {
        Matrix4f matrix = readTransformation(display).getMatrix();
        AABB local = localBounds(display);

        Vector3f corner = new Vector3f();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 8; index++) {
            corner.set(
                    (float) ((index & 1) == 0 ? local.minX : local.maxX),
                    (float) ((index & 2) == 0 ? local.minY : local.maxY),
                    (float) ((index & 4) == 0 ? local.minZ : local.maxZ));
            matrix.transformPosition(corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).move(display.position()).inflate(0.01D);
    }

    public static Optional<Vec3> rayIntersection(Display display, Vec3 start, Vec3 end) {
        Matrix4f matrix = readTransformation(display).getMatrix();
        float determinant = matrix.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-8F) {
            return Optional.empty();
        }

        Matrix4f inverse = new Matrix4f(matrix).invert();
        Vector3f localStart = inverse.transformPosition(new Vector3f(
                (float) (start.x - display.getX()),
                (float) (start.y - display.getY()),
                (float) (start.z - display.getZ())));
        Vector3f localEnd = inverse.transformPosition(new Vector3f(
                (float) (end.x - display.getX()),
                (float) (end.y - display.getY()),
                (float) (end.z - display.getZ())));
        Vec3 localStartVec = new Vec3(localStart.x, localStart.y, localStart.z);
        AABB bounds = localBounds(display);
        Vec3 localHit = bounds.contains(localStartVec)
                ? localStartVec
                : bounds.clip(localStartVec, new Vec3(localEnd.x, localEnd.y, localEnd.z)).orElse(null);
        if (localHit == null) {
            return Optional.empty();
        }

        Vector3f offset = matrix.transformPosition(new Vector3f(
                (float) localHit.x, (float) localHit.y, (float) localHit.z));
        return Optional.of(new Vec3(
                display.getX() + offset.x,
                display.getY() + offset.y,
                display.getZ() + offset.z));
    }

    private static AABB localBounds(Display display) {
        if (display instanceof Display.BlockDisplay) {
            return new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        }
        if (display instanceof Display.ItemDisplay) {
            return new AABB(-0.5D, -0.5D, -0.5D, 0.5D, 0.5D, 0.5D);
        }
        Vector3f pivot = pivot(display);
        return new AABB(pivot.x - 1.0D, pivot.y - 0.25D, pivot.z - 0.05D,
                pivot.x + 1.0D, pivot.y + 0.25D, pivot.z + 0.05D);
    }

    private static Transformation readTransformation(Display display) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        return Transformation.EXTENDED_CODEC.parse(NbtOps.INSTANCE, tag.get("transformation"))
                .result().orElse(Transformation.identity());
    }

    private static void putTransformation(CompoundTag tag, Display display, Values values) {
        Vector3f scale = new Vector3f(values.scaleX, values.scaleY, values.scaleZ);
        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(values.rotationX),
                (float) Math.toRadians(values.rotationY),
                (float) Math.toRadians(values.rotationZ));

        Vector3f transformedCenter = rotation.transform(pivot(display).mul(scale));
        Vector3f rawTranslation = new Vector3f(
                values.translationX, values.translationY, values.translationZ)
                .add(baseCenter(display))
                .sub(transformedCenter);
        Transformation transformation = new Transformation(rawTranslation, rotation, scale, new Quaternionf());
        tag.put("transformation", Transformation.EXTENDED_CODEC
                .encodeStart(NbtOps.INSTANCE, transformation)
                .result().orElseThrow());
    }

    private static Vector3f pivot(Display display) {
        if (display instanceof Display.BlockDisplay) {
            return new Vector3f(0.5F);
        }
        if (display instanceof Display.TextDisplay) {
            return new Vector3f(0.0125F, 0.1375F, 0.0F);
        }
        return new Vector3f();
    }

    private static Vector3f baseCenter(Display display) {
        return display instanceof Display.BlockDisplay ? new Vector3f(0.0F, 0.5F, 0.0F) : new Vector3f();
    }

    public record Values(float translationX, float translationY, float translationZ,
                         float rotationX, float rotationY, float rotationZ,
                         float scaleX, float scaleY, float scaleZ) {
        public boolean isValid() {
            return within(translationX, MAX_TRANSLATION)
                    && within(translationY, MAX_TRANSLATION)
                    && within(translationZ, MAX_TRANSLATION)
                    && within(rotationX, MAX_ROTATION)
                    && within(rotationY, MAX_ROTATION)
                    && within(rotationZ, MAX_ROTATION)
                    && positiveScale(scaleX) && positiveScale(scaleY) && positiveScale(scaleZ);
        }

        private static boolean within(float value, float limit) {
            return Float.isFinite(value) && Math.abs(value) <= limit;
        }

        private static boolean positiveScale(float value) {
            return Float.isFinite(value) && value > 0.0F && value <= MAX_SCALE;
        }
    }
}
