package com.throwingdiscs.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

public class DiscFlashParticle extends SpriteBillboardParticle {
    private final float baseScale;

    protected DiscFlashParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites);
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
        this.maxAge = 5;
        this.baseScale = 0.55f;
        this.scale = baseScale;
        this.angle = this.random.nextFloat() * (float) Math.PI;
        this.prevAngle = this.angle;
        this.red = 1f;
        this.green = 0.92f;
        this.blue = 0.62f;
        this.alpha = 1f;
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
        this.prevAngle = this.angle;
        this.angle += 0.25f;
        this.alpha = 1f - this.age / (float) this.maxAge;
    }

    @Override
    public float getSize(float tickDelta) {
        float t = (this.age + tickDelta) / this.maxAge;
        return baseScale * (0.35f + 0.9f * t);
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0;
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
            return new DiscFlashParticle(world, x, y, z, sprites);
        }
    }
}
