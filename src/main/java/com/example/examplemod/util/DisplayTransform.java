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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        Vector3f pivot = pivot(display);
        Vector3f min;
        Vector3f max;
        if (display instanceof Display.BlockDisplay) {
            min = new Vector3f();
            max = new Vector3f(1.0F);
        } else if (display instanceof Display.ItemDisplay) {
            min = new Vector3f(-0.5F);
            max = new Vector3f(0.5F);
        } else {
            min = new Vector3f(pivot).add(-1.0F, -0.25F, -0.05F);
            max = new Vector3f(pivot).add(1.0F, 0.25F, 0.05F);
        }

        Vector3f corner = new Vector3f();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 8; index++) {
            corner.set(
                    (index & 1) == 0 ? min.x : max.x,
                    (index & 2) == 0 ? min.y : max.y,
                    (index & 4) == 0 ? min.z : max.z);
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
