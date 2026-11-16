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

public class SalivaSplatParticle extends SpriteBillboardParticle {
    private final float maxHalf;
    private final float rot;

    protected SalivaSplatParticle(ClientWorld world, double x, double y, double z, double sizeMul, SpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites);
        this.collidesWithWorld = false;
        this.gravityStrength = 0f;
        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
        this.maxAge = 18 + this.random.nextInt(8);
        this.maxHalf = (float) (0.10 * sizeMul * (0.85 + this.random.nextFloat() * 0.3));
        this.rot = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.alpha = 0.8f;
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
        this.alpha = 0.8f * (1f - t * t);
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        Vec3d cam = camera.getPos();
        float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
        float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y);
        float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);

        float grow = MathHelper.clamp((this.age + tickDelta) / (float) this.maxAge, 0f, 1f);
        grow = 1f - (1f - grow) * (1f - grow);
        float half = maxHalf * (0.3f + 0.7f * grow);
        float c = MathHelper.cos(rot) * half, s = MathHelper.sin(rot) * half;

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
            return new SalivaSplatParticle(world, x, y, z, vx > 0 ? vx : 1.0, sprites);
        }
    }
}
