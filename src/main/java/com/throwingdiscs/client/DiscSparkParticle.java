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

public class DiscSparkParticle extends SpriteBillboardParticle {
    private final float halfWidth;

    protected DiscSparkParticle(ClientWorld world, double x, double y, double z,
                                double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites);
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.collidesWithWorld = true;
        this.gravityStrength = 0f;
        this.setBoundingBoxSpacing(0.02f, 0.02f);
        this.maxAge = 8 + this.random.nextInt(10);
        this.halfWidth = 0.014f + this.random.nextFloat() * 0.01f;
        this.red = 1f;
        this.green = 0.95f;
        this.blue = 0.65f;
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
        this.velocityY -= 0.035;
        double vyBefore = this.velocityY;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        if (this.onGround) {
            this.velocityY = -vyBefore * 0.35;
            this.velocityX *= 0.6;
            this.velocityZ *= 0.6;
            this.onGround = false;
        }
        this.velocityX *= 0.88;
        this.velocityY *= 0.92;
        this.velocityZ *= 0.88;

        float t = this.age / (float) this.maxAge;
        this.red = 1f;
        this.green = MathHelper.lerp(t, 0.95f, 0.32f);
        this.blue = MathHelper.lerp((float) Math.sqrt(t), 0.65f, 0.05f);
        this.alpha = 1f - t * t * t;
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        Vec3d cam = camera.getPos();
        float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
        float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y);
        float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);

        Vec3d v = new Vec3d(this.velocityX, this.velocityY, this.velocityZ);
        double speed = v.length();
        Vec3d dir = speed > 1.0E-4 ? v.multiply(1.0 / speed) : new Vec3d(0, -1, 0);
        float halfLength = MathHelper.clamp((float) speed * 1.6f, 0.03f, 0.28f);

        Vec3d toCam = new Vec3d(-px, -py, -pz);
        Vec3d side = dir.crossProduct(toCam);
        if (side.lengthSquared() < 1.0E-8) return;
        side = side.normalize();

        float lx = (float) dir.x * halfLength, ly = (float) dir.y * halfLength, lz = (float) dir.z * halfLength;
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
            return new DiscSparkParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
