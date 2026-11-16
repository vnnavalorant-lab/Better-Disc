package com.throwingdiscs.client;

import com.throwingdiscs.ModParticles;

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

public class SalivaDripParticle extends SpriteBillboardParticle {
    private final boolean droplet;
    private final int hangTicks;
    private final float size;
    private final double anchorY;
    private boolean hanging;

    protected SalivaDripParticle(ClientWorld world, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites);
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.droplet = vx * vx + vy * vy + vz * vz > 1.0E-8;
        this.collidesWithWorld = true;
        this.gravityStrength = 0f;
        this.setBoundingBoxSpacing(0.05f, 0.05f);
        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.alpha = 0.85f;
        this.anchorY = y;
        if (droplet) {
            this.hanging = false;
            this.hangTicks = 0;
            this.size = 0.022f + this.random.nextFloat() * 0.02f;
            this.maxAge = 60;
        } else {
            this.hanging = true;
            this.hangTicks = 12 + this.random.nextInt(14);
            this.size = 0.035f + this.random.nextFloat() * 0.02f;
            this.maxAge = hangTicks + 90;
        }
    }

    private float hangLength(float t) {
        return size * 2.0f * (1.0f + 3.2f * (float) Math.pow(t, 1.5));
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
        if (hanging) {
            if (this.age >= hangTicks) {
                hanging = false;
                this.setPos(this.x, anchorY - hangLength(1.0f) * 0.5, this.z);
                this.prevPosY = this.y;
                this.velocityY = -0.03;
            }
            return;
        }
        this.velocityY = Math.max(this.velocityY - 0.04, -0.7);
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.92;
        this.velocityZ *= 0.92;
        if (this.onGround) {
            this.world.addParticle(ModParticles.SALIVA_SPLAT, this.x, this.y + 0.012, this.z, droplet ? 0.6 : 1.0, 0, 0);
            this.markDead();
        }
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        Vec3d cam = camera.getPos();
        float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
        float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y);
        float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);

        float top, bottom, halfWidth;
        if (hanging) {
            float t = MathHelper.clamp((this.age + tickDelta) / (float) hangTicks, 0f, 1f);
            top = py;
            bottom = py - hangLength(t);
            halfWidth = size * (0.5f + 0.4f * t);
        } else {
            float fall = MathHelper.clamp((float) -this.velocityY, 0f, 0.7f);
            float len = size * 2.0f * (1.0f + Math.min(2.8f, fall * 7.0f));
            top = py + len * 0.55f;
            bottom = py - len * 0.45f;
            halfWidth = size * (1.0f - Math.min(0.4f, fall * 0.8f));
        }

        float len = MathHelper.sqrt(px * px + pz * pz);
        float rx, rz;
        if (len < 1.0E-4f) {
            rx = halfWidth;
            rz = 0f;
        } else {
            rx = -pz / len * halfWidth;
            rz = px / len * halfWidth;
        }

        float u0 = this.getMinU(), u1 = this.getMaxU(), v0 = this.getMinV(), v1 = this.getMaxV();
        int light = this.getBrightness(tickDelta);
        vc.vertex(px - rx, bottom, pz - rz).texture(u0, v1).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px - rx, top, pz - rz).texture(u0, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + rx, top, pz + rz).texture(u1, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + rx, bottom, pz + rz).texture(u1, v1).color(red, green, blue, alpha).light(light).next();
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
            return new SalivaDripParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
