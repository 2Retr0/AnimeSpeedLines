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

    private boolean renderingBlit = true;

    private final ManagedShaderEffect speedLines = ShaderEffectManager.getInstance().manage(
        new Identifier(MOD_ID, "shaders/post/impact_lines.json"));
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
        LOGGER.info("Hello AnimeSpeedLines!");

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (renderingBlit) {
                // make opacity lower at night
                speedLines.render(tickDelta);
            }
        });

//        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
//            if (world.isClient) {
//                renderingBlit = !renderingBlit;
//                // color.set((float) Math.random(), (float) Math.random(), (float) Math.random(), 1.0f);
//            }
//            LOGGER.error(String.valueOf(renderingBlit));
//            return ActionResult.PASS;
//        });

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

            var velocity = player.getVelocity().normalize();

            if (vehicle != null) {
                velocity = vehicle.getVelocity().normalize();
            }
            var eye = player.getRotationVecClient().normalize();
            var up = new Vec3d(0, 1, 0);
            var right = up.crossProduct(eye);

            // TODO: Not super happy with the angle bias solution since certain scenarios (e.g., looking down) provide
            //  lines that move in an unnatural direction
            // Project velocity onto plane described by eye normal
            var proj = velocity.subtract(eye.multiply(velocity.dotProduct(eye)));
            // Get signed angle between projected velocity and right w.r.t. eye plane
            // Source: https://stackoverflow.com/a/33920320
            var bias = Math.atan2(proj.crossProduct(right).dotProduct(eye), right.dotProduct(proj));
            var biasWeight = 1f - Math.abs(velocity.dotProduct(eye));

            uniformBias.set((float) bias);
            uniformBiasWeight.set((float) biasWeight);
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

        return projVec.subtract(planeVec.multiply(projVec.dotProduct(planeVec)));
    }
}
