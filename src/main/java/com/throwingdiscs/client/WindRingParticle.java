package com.throwingdiscs.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class WindRingParticle extends SpriteBillboardParticle {
    private final float startAngle;
    private final float baseAlpha;

    protected WindRingParticle(ClientWorld world, double x, double y, double z,
                               double angle, SpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites);
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
        this.maxAge = 6 + this.random.nextInt(3);
        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.baseAlpha = 0.5f;
        this.alpha = baseAlpha;
        this.startAngle = (float) angle;
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }
        float t = this.age / (float) this.maxAge;
        this.alpha = this.baseAlpha * (1f - t);
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        Vec3d cam = camera.getPos();
        float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
        float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y);
        float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);

        float t = (this.age + tickDelta) / this.maxAge;
        float half = 0.42f + 0.38f * t;
        float ang = this.startAngle + (this.age + tickDelta) * 0.55f;
        float c = MathHelper.cos(ang) * half, s = MathHelper.sin(ang) * half;

        float u0 = this.getMinU(), u1 = this.getMaxU(), v0 = this.getMinV(), v1 = this.getMaxV();
        int light = this.getBrightness(tickDelta);
        vc.vertex(px - c - s, py, pz - s + c).texture(u0, v1).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px - c + s, py, pz - s - c).texture(u0, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + c + s, py, pz + s - c).texture(u1, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + c - s, py, pz + s + c).texture(u1, v1).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + c - s, py, pz + s + c).texture(u1, v1).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + c + s, py, pz + s - c).texture(u1, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px - c + s, py, pz - s - c).texture(u0, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px - c - s, py, pz - s + c).texture(u0, v1).color(red, green, blue, alpha).light(light).next();
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new WindRingParticle(world, x, y, z, vx, sprites);
        }
    }
}
