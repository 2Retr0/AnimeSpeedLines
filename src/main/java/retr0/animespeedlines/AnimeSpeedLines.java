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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnimeSpeedLines implements ClientModInitializer {
    public static final String MOD_ID = "animespeedlines";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final ManagedShaderEffect speedLines = ShaderEffectManager.getInstance().manage(
        new Identifier(MOD_ID, "shaders/post/impact_lines.json"));
    private final Uniform1f uniformSTime = speedLines.findUniform1f("STime");
    private final Uniform1f uniformWeight = speedLines.findUniform1f("Weight");
    private final Uniform1f uniformBias = speedLines.findUniform1f("BiasAngle");
    private final Uniform1f uniformBiasWeight = speedLines.findUniform1f("BiasWeight");

    private int ticks;

    private float weight = 0f;
    private float bias = 0f;
    private float biasWeight = 0f;
    private boolean shouldRender = false;

    @Override
    public void onInitializeClient() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        LOGGER.info("Hello AnimeSpeedLines!");

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (!shouldRender) return;
            speedLines.render(tickDelta);
        });

        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if (minecraftClient.isPaused()) return;
            ticks++;

            // --- Render Condition ---
            var player = MinecraftClient.getInstance().player;
            if (player == null) return;

            // Use vehicle velocity instead of player velocity if applicable
            var vehicle = player.getVehicle();
            var targetEntity = vehicle == null ? player : vehicle;
            var velocity = targetEntity.getVelocity();
            var speed = (float) velocity.lengthSquared();
            shouldRender = shouldRenderImpactLines(speed);

            if (!shouldRender) return;

            // --- Uniform Calculations ---
            var eye = player.getRotationVecClient(); // Relative to [0,0,1]
            var up = Vec3d.fromPolar(new Vec2f(player.getPitch() - 90f, player.getYaw()));
            var right = up.crossProduct(eye);

            velocity = velocity.normalize();
            var vDotEye = velocity.dotProduct(eye);
            // Project velocity onto plane described by eye normal
            var proj = velocity.subtract(eye.multiply(vDotEye));
            // Get signed angle between projected velocity and right w.r.t. eye plane
            // Source: https://stackoverflow.com/a/33920320
            var angle = (float) MathHelper.atan2(proj.crossProduct(right).dotProduct(eye), right.dotProduct(proj));

            weight = Math.min(1f, speed);
            bias = angle;
            biasWeight = 1f - MathHelper.abs((float) vDotEye);

        });

        PostWorldRenderCallbackV2.EVENT.register((matrices, camera, tickDelta, nanoTime) -> {
            if (!shouldRender) return;

            uniformSTime.set((ticks + tickDelta) / 20f);
            uniformWeight.set(weight);
            uniformBias.set(bias);
            uniformBiasWeight.set(biasWeight);
        });
    }

    private boolean shouldRenderImpactLines(float speed) {
        return speed >= 0.2f; // Roughly the speed of sprinting on ice
    }
}
