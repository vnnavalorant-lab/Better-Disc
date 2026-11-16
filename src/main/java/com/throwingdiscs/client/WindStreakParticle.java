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

public class WindStreakParticle extends SpriteBillboardParticle {
    private final Vec3d dir;
    private final float halfLength;
    private final float halfWidth;
    private final float baseAlpha;

    protected WindStreakParticle(ClientWorld world, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.setSprite(sprites);
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.maxAge = 8 + this.random.nextInt(5);
        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.baseAlpha = 0.55f + this.random.nextFloat() * 0.25f;
        this.alpha = baseAlpha;
        this.halfLength = 0.45f + this.random.nextFloat() * 0.25f;
        this.halfWidth = 0.035f + this.random.nextFloat() * 0.02f;
        Vec3d v = new Vec3d(vx, vy, vz);
        this.dir = v.lengthSquared() > 1.0E-8 ? v.normalize() : new Vec3d(0, 0, 1);
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
        float t = this.age / (float) this.maxAge;
        this.alpha = this.baseAlpha * (1f - t) * (1f - t);
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        Vec3d cam = camera.getPos();
        float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
        float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y);
        float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);

        Vec3d toCam = new Vec3d(-px, -py, -pz);
        Vec3d side = this.dir.crossProduct(toCam);
        if (side.lengthSquared() < 1.0E-8) return;
        side = side.normalize();

        float lx = (float) this.dir.x * halfLength, ly = (float) this.dir.y * halfLength, lz = (float) this.dir.z * halfLength;
        float wx = (float) side.x * halfWidth, wy = (float) side.y * halfWidth, wz = (float) side.z * halfWidth;

        float u0 = this.getMinU(), u1 = this.getMaxU(), v0 = this.getMinV(), v1 = this.getMaxV();
        int light = this.getBrightness(tickDelta);
        vc.vertex(px - lx - wx, py - ly - wy, pz - lz - wz).texture(u0, v1).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px - lx + wx, py - ly + wy, pz - lz + wz).texture(u0, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + lx + wx, py + ly + wy, pz + lz + wz).texture(u1, v0).color(red, green, blue, alpha).light(light).next();
        vc.vertex(px + lx - wx, py + ly - wy, pz + lz - wz).texture(u1, v1).color(red, green, blue, alpha).light(light).next();
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
            return new WindStreakParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
