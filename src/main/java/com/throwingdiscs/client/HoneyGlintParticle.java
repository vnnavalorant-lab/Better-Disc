package com.throwingdiscs.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

public class HoneyGlintParticle extends SpriteBillboardParticle {
    private final float baseScale;
    private final float spin;

    protected HoneyGlintParticle(ClientWorld world, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites);
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.maxAge = 14 + this.random.nextInt(14);
        this.baseScale = 0.05f + this.random.nextFloat() * 0.05f;
        this.scale = baseScale;
        this.angle = this.random.nextFloat() * (float) Math.PI;
        this.prevAngle = this.angle;
        this.spin = (this.random.nextBoolean() ? 1f : -1f) * (0.10f + this.random.nextFloat() * 0.12f);
        this.red = 1.0f;
        this.green = 0.88f;
        this.blue = 0.45f;
        this.alpha = 0f;
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
        this.x += this.velocityX;
        this.y += this.velocityY;
        this.z += this.velocityZ;
        this.velocityX *= 0.96;
        this.velocityY *= 0.96;
        this.velocityZ *= 0.96;
        this.prevAngle = this.angle;
        this.angle += spin;
        float t = this.age / (float) this.maxAge;
        this.alpha = (float) Math.sin(Math.PI * t);
    }

    @Override
    public float getSize(float tickDelta) {
        float t = (this.age + tickDelta) / this.maxAge;
        return baseScale * (0.5f + 0.9f * (float) Math.sin(Math.PI * Math.min(1f, t)));
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0; // luz máxima
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
            return new HoneyGlintParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
