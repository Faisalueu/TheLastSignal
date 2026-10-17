package com.lastsignal.entity.client;

import com.lastsignal.entity.EntityProfiles;
import com.lastsignal.entity.HorrorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * One model class, 17 different bodies: geometry is built from each creature's profile (head/body/limb sizes,
 * quadruped or biped, extra part) so every creature has its own proportions and silhouette. Animation is
 * driven by the SYNCED server AI state, so what you see always matches what the creature is actually doing.
 */
public class ProfileModel extends EntityModel<HorrorEntity> {
    private final ModelPart root, head, body, extra, a0, a1, l0, l1;
    private final boolean quad;
    private final float bodyH, bodyTopY;
    private final String id;

    public ProfileModel(ModelPart root, String id, boolean quad, float bodyH, float bodyTopY) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.extra = root.hasChild("extra") ? root.getChild("extra") : null;
        this.a0 = root.getChild("arm_r");
        this.a1 = root.getChild("arm_l");
        this.l0 = root.getChild("leg_r");
        this.l1 = root.getChild("leg_l");
        this.quad = quad; this.bodyH = bodyH; this.bodyTopY = bodyTopY; this.id = id;
    }

    public static float bodyTop(EntityProfiles.Profile p) {
        int legH = p.quad() ? Math.max(p.leg()[1], p.arm()[1]) : p.leg()[1];
        return 24 - legH - p.body()[1];
    }

    public static LayerDefinition createLayer(EntityProfiles.Profile p) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        int[] h = p.head(), b = p.body(), a = p.arm(), l = p.leg();
        float top = bodyTop(p);
        if (!p.quad()) {
            r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(p.uvHead()[0], p.uvHead()[1])
                    .addBox(-h[0] / 2f, -h[1], -h[2] / 2f, h[0], h[1], h[2]), PartPose.offset(0, top, 0));
            r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(p.uvBody()[0], p.uvBody()[1])
                    .addBox(-b[0] / 2f, 0, -b[2] / 2f, b[0], b[1], b[2]), PartPose.offset(0, top, 0));
            float ax = b[0] / 2f + a[0] / 2f;
            CubeListBuilder arm = CubeListBuilder.create().texOffs(p.uvArm()[0], p.uvArm()[1]).addBox(-a[0] / 2f, 0, -a[0] / 2f, a[0], a[1], a[0]);
            r.addOrReplaceChild("arm_r", arm, PartPose.offset(-ax, top + 1, 0));
            r.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(p.uvArm()[0], p.uvArm()[1]).mirror()
                    .addBox(-a[0] / 2f, 0, -a[0] / 2f, a[0], a[1], a[0]), PartPose.offset(ax, top + 1, 0));
            float lx = Math.max(l[0] / 2f, b[0] / 4f);
            CubeListBuilder leg = CubeListBuilder.create().texOffs(p.uvLeg()[0], p.uvLeg()[1]).addBox(-l[0] / 2f, 0, -l[0] / 2f, l[0], l[1], l[0]);
            r.addOrReplaceChild("leg_r", leg, PartPose.offset(-lx, 24 - l[1], 0));
            r.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(p.uvLeg()[0], p.uvLeg()[1]).mirror()
                    .addBox(-l[0] / 2f, 0, -l[0] / 2f, l[0], l[1], l[0]), PartPose.offset(lx, 24 - l[1], 0));
        } else {
            // quadruped / crawler: long body along Z, head forward, front limbs = "arm", hind limbs = "leg"
            r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(p.uvBody()[0], p.uvBody()[1])
                    .addBox(-b[0] / 2f, 0, -b[2] / 2f, b[0], b[1], b[2]), PartPose.offset(0, top, 0));
            r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(p.uvHead()[0], p.uvHead()[1])
                    .addBox(-h[0] / 2f, -h[1] / 2f, -h[2], h[0], h[1], h[2]), PartPose.offset(0, top + b[1] * 0.35f, -b[2] / 2f));
            float x = b[0] / 2f + a[0] / 2f;
            CubeListBuilder front = CubeListBuilder.create().texOffs(p.uvArm()[0], p.uvArm()[1]).addBox(-a[0] / 2f, 0, -a[0] / 2f, a[0], a[1], a[0]);
            CubeListBuilder hind = CubeListBuilder.create().texOffs(p.uvLeg()[0], p.uvLeg()[1]).addBox(-l[0] / 2f, 0, -l[0] / 2f, l[0], l[1], l[0]);
            float zf = -b[2] / 2f + a[0], zh = b[2] / 2f - l[0];
            r.addOrReplaceChild("arm_r", front, PartPose.offset(-x, 24 - a[1], zf));
            r.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(p.uvArm()[0], p.uvArm()[1]).mirror()
                    .addBox(-a[0] / 2f, 0, -a[0] / 2f, a[0], a[1], a[0]), PartPose.offset(x, 24 - a[1], zf));
            r.addOrReplaceChild("leg_r", hind, PartPose.offset(-x, 24 - l[1], zh));
            r.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(p.uvLeg()[0], p.uvLeg()[1]).mirror()
                    .addBox(-l[0] / 2f, 0, -l[0] / 2f, l[0], l[1], l[0]), PartPose.offset(x, 24 - l[1], zh));
        }
        if (p.extra() != null) {
            int[] e = p.extra();   // w,h,d, ox, oy, oz : pivot (ox, bodyTop, oz); box starts at y = oy
            r.addOrReplaceChild("extra", CubeListBuilder.create().texOffs(p.uvExtra()[0], p.uvExtra()[1])
                    .addBox(-e[0] / 2f, e[4], -e[2] / 2f, e[0], e[1], e[2]), PartPose.offset(e[3], top, e[5]));
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void setupAnim(HorrorEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        for (ModelPart p : new ModelPart[]{head, body, a0, a1, l0, l1}) p.resetPose();
        if (extra != null) extra.resetPose();

        float swing = Mth.cos(limbSwing * 0.6662f) * 1.3f * limbSwingAmount;
        float breathe = Mth.sin(age * 0.08f) * 0.03f;
        HorrorEntity.State st = e.getHorrorState();

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        // locomotion
        l0.xRot = swing; l1.xRot = -swing;
        a0.xRot = -swing * 0.8f; a1.xRot = swing * 0.8f;
        if (quad) { a0.xRot = swing; a1.xRot = -swing; l0.xRot = -swing; l1.xRot = swing; }

        // idle breathing (everything alive moves a little)
        body.yRot = 0; body.xRot = breathe;
        a0.zRot = 0.04f + breathe; a1.zRot = -0.04f - breathe;
        head.zRot = id.equals("crawler") || id.equals("unfinished") ? 0.45f : Mth.sin(age * 0.05f) * 0.04f; // "unnaturally angled"

        switch (st) {
            case WATCHING -> { a0.xRot = a1.xRot = 0; head.xRot += Mth.sin(age * 0.03f) * 0.05f; }
            case STALKING -> { body.xRot = quad ? 0 : 0.35f; head.xRot += 0.25f; if (!quad) { a0.xRot = a1.xRot = -0.6f; } }
            case INVESTIGATING, SEARCHING -> head.yRot += Mth.sin(age * 0.12f) * 0.8f;
            case CHASING -> { if (!quad) { body.xRot = 0.25f; a0.xRot = a1.xRot = -1.4f; } l0.xRot *= 1.6f; l1.xRot *= 1.6f; }
            case ATTACKING -> { float s = Mth.sin(age * 0.9f); a0.xRot = -1.6f + s * 0.9f; a1.xRot = -1.6f - s * 0.9f; body.xRot = 0.2f; }
            case RETREATING -> { body.xRot = -0.2f; if (!quad) { a0.xRot = a1.xRot = 0.7f; } }
            case SPECIAL -> { a0.zRot = 1.3f; a1.zRot = -1.3f; a0.xRot = a1.xRot = 0; head.xRot -= 0.3f + Mth.sin(age * 0.4f) * 0.05f; }
            case DISAPPEARING -> { head.zRot += Mth.sin(age * 1.7f) * 0.3f; }
            default -> { }
        }
        if (id.equals("hollow")) { l1.xRot *= 0.4f; body.zRot = 0.08f; }                    // limp
        if (id.equals("echo")) head.yRot += Mth.sin(age * 0.9f) * 0.08f;                    // jitter
        if (id.equals("facet")) body.yRot = Mth.sin(age * 0.05f) * 0.2f;
        if (id.equals("lantern_widow") && extra != null) extra.xRot = Mth.sin(age * 0.1f) * 0.15f;
        // keep the head on top of a tilted body
        if (!quad) head.z = -Mth.sin(body.xRot) * bodyH * 0.8f;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, buf, light, overlay, r, g, b, a);
    }
}
