package com.battlegrounds.client.anim;

/** Поза тела: углы в радианах. Изгибы: локти и колени, спина вперёд и вбок. */
public final class BodyPose {
    public float rArmX, rArmY, rArmZ;
    public float lArmX, lArmY, lArmZ;
    public float rLegX, rLegY, rLegZ;
    public float lLegX, lLegY, lLegZ;
    public float rElbow, lElbow, rKnee, lKnee;
    public float spineForward, spineSide;
    public float bodyY;
    public float itemRoll, itemPitch;

    public BodyPose set(BodyPose o) {
        rArmX = o.rArmX; rArmY = o.rArmY; rArmZ = o.rArmZ;
        lArmX = o.lArmX; lArmY = o.lArmY; lArmZ = o.lArmZ;
        rLegX = o.rLegX; rLegY = o.rLegY; rLegZ = o.rLegZ;
        lLegX = o.lLegX; lLegY = o.lLegY; lLegZ = o.lLegZ;
        rElbow = o.rElbow; lElbow = o.lElbow; rKnee = o.rKnee; lKnee = o.lKnee;
        spineForward = o.spineForward; spineSide = o.spineSide;
        bodyY = o.bodyY;
        itemRoll = o.itemRoll; itemPitch = o.itemPitch;
        return this;
    }

    /** this = lerp(this, o, t) */
    public BodyPose blend(BodyPose o, float t) {
        if (t <= 0f) return this;
        if (t >= 1f) return set(o);
        rArmX += (o.rArmX - rArmX) * t; rArmY += (o.rArmY - rArmY) * t; rArmZ += (o.rArmZ - rArmZ) * t;
        lArmX += (o.lArmX - lArmX) * t; lArmY += (o.lArmY - lArmY) * t; lArmZ += (o.lArmZ - lArmZ) * t;
        rLegX += (o.rLegX - rLegX) * t; rLegY += (o.rLegY - rLegY) * t; rLegZ += (o.rLegZ - rLegZ) * t;
        lLegX += (o.lLegX - lLegX) * t; lLegY += (o.lLegY - lLegY) * t; lLegZ += (o.lLegZ - lLegZ) * t;
        rElbow += (o.rElbow - rElbow) * t; lElbow += (o.lElbow - lElbow) * t;
        rKnee += (o.rKnee - rKnee) * t; lKnee += (o.lKnee - lKnee) * t;
        spineForward += (o.spineForward - spineForward) * t;
        spineSide += (o.spineSide - spineSide) * t;
        bodyY += (o.bodyY - bodyY) * t;
        itemRoll += (o.itemRoll - itemRoll) * t;
        itemPitch += (o.itemPitch - itemPitch) * t;
        return this;
    }

    /** Только руки: this.arms = lerp(this.arms, o.arms, t) */
    public BodyPose blendArms(BodyPose o, float t) {
        if (t <= 0f) return this;
        if (t > 1f) t = 1f;
        rArmX += (o.rArmX - rArmX) * t; rArmY += (o.rArmY - rArmY) * t; rArmZ += (o.rArmZ - rArmZ) * t;
        lArmX += (o.lArmX - lArmX) * t; lArmY += (o.lArmY - lArmY) * t; lArmZ += (o.lArmZ - lArmZ) * t;
        rElbow += (o.rElbow - rElbow) * t; lElbow += (o.lElbow - lElbow) * t;
        itemRoll += (o.itemRoll - itemRoll) * t;
        itemPitch += (o.itemPitch - itemPitch) * t;
        return this;
    }
}
