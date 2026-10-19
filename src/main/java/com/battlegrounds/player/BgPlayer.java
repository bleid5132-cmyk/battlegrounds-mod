package com.battlegrounds.player;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Дополнительные данные игрока (стойка, наклон, перелезание).
 * Реализуется миксином на PlayerEntity и синхронизируется со всеми игроками.
 */
public interface BgPlayer {
    int STAND = 0;
    int CROUCH = 1;
    int PRONE = 2;

    int bg$getStance();

    void bg$setStance(int stance);

    /** -1 влево, 0 прямо, 1 вправо */
    int bg$getLean();

    void bg$setLean(int lean);

    boolean bg$isMantling();

    void bg$setMantling(boolean mantling);

    /** Плавное значение наклона для отрисовки (только на клиенте). */
    float bg$getLeanAnim(float tickDelta);

    static BgPlayer of(PlayerEntity player) {
        return (BgPlayer) player;
    }
}
