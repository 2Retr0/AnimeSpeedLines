package retr0.animespeedlines;

import ladysnake.satin.api.event.PostWorldRenderCallbackV2;
import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import ladysnake.satin.api.managed.uniform.Uniform1f;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnimeSpeedLines implements ClientModInitializer {
    public static final String MOD_ID = "animespeedlines";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private boolean renderingBlit = false;

    private final ManagedShaderEffect speedLines = ShaderEffectManager.getInstance().manage(
        new Identifier(MOD_ID, "shaders/post/speed_lines.json"));
    private final Uniform1f uniformSTime = speedLines.findUniform1f("STime");
    private final Uniform1f uniformWeight = speedLines.findUniform1f("Weight");
    private final Uniform1f uniformBias = speedLines.findUniform1f("BiasAngle");
    private final Uniform1f uniformBiasWeight = speedLines.findUniform1f("BiasWeight");

    private int ticks;

    @Override
    public void onInitializeClient() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        LOGGER.info("Hello Fabric world!");

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (renderingBlit) {
                // make opacity lower at night
                speedLines.render(tickDelta);
            }
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) {
                renderingBlit = !renderingBlit;
                // color.set((float) Math.random(), (float) Math.random(), (float) Math.random(), 1.0f);
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if (!minecraftClient.isPaused()) ticks++;
        });


        PostWorldRenderCallbackV2.EVENT.register((matrices, camera, tickDelta, nanoTime) -> {
            uniformSTime.set((ticks + tickDelta) / 20f);
            var player = MinecraftClient.getInstance().player;
            var vSquared = (float) MinecraftClient.getInstance().player.getVelocity().lengthSquared();
            var vehicle = player.getVehicle();
            if (vehicle != null) {
                vSquared = (float) vehicle.getVelocity().lengthSquared();
            }

            vSquared = Math.min(1f, vSquared);
            uniformWeight.set(vSquared);

            var velocity = player.getVelocity();

            if (vehicle != null) {
                velocity = vehicle.getVelocity();
            }
            var eyeVec = player.getRotationVecClient();

            var proj = projectOntoPlane(velocity, eyeVec);

            double x = proj.x, y = proj.y, z = proj.z;

            var vecX = new Vec3d(1d, 0d, 0d).rotateY(player.getYaw() * (MathHelper.PI / 180f));
            // https://www.wolframalpha.com/input?i2d=true&i=Divide%5B-Divide%5Ba*d%2Bb*e%2Bc*f%2Csqrt%5C%2840%29%5C%2840%29Power%5Ba%2C2%5D%2BPower%5Bb%2C2%5D%2BPower%5Bc%2C2%5D%5C%2841%29%5C%2840%29Power%5Bd%2C2%5D%2BPower%5Be%2C2%5D%2BPower%5Bf%2C2%5D%5C%2841%29%5C%2841%29%5D%2Csqrt%5C%2840%291-Power%5B%5C%2840%29Divide%5Ba*d%2Bb*e%2Bc*f%2Csqrt%5C%2840%29%5C%2840%29Power%5Ba%2C2%5D%2BPower%5Bb%2C2%5D%2BPower%5Bc%2C2%5D%5C%2841%29%5C%2840%29Power%5Bd%2C2%5D%2BPower%5Be%2C2%5D%2BPower%5Bf%2C2%5D%5C%2841%29%5C%2841%29%5D%5C%2841%29%2C2%5D%5C%2841%29%5D
            var sim = velocity.normalize().dotProduct(eyeVec);
            var biasWeight = 1f - Math.abs(sim);
            var bias = MathHelper.sign(sim) * Math.acos(proj.dotProduct(vecX) / Math.sqrt(proj.lengthSquared() * vecX.lengthSquared())) + MathHelper.PI;

            uniformBias.set((float) bias);
            uniformBiasWeight.set((float) biasWeight);

            LOGGER.info("angle: " +  velocity.normalize().dotProduct(eyeVec));
            // asin(proj.y / sqrt(proj.length())) simplified
            // LOGGER.info("angle: " +  MathHelper.atan2(y, Math.sqrt(x * x + z * z)) + "");
        });
    }



    /**
     * Projects a vector onto a plane described by its normal vector.
     * @param projVec The vector to be projected.
     * @param planeVec The normal vector describing the target plane.
     * @return The projected vector on the plane.
     */
    Vec3d projectOntoPlane(Vec3d projVec, Vec3d planeVec) {
        var a = planeVec.dotProduct(projVec);
        var b = planeVec.dotProduct(planeVec);

        return projVec.subtract(planeVec.multiply(a / b));
    }
}
