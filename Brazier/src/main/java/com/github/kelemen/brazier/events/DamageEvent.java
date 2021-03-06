package com.github.kelemen.brazier.events;

import com.github.kelemen.brazier.DamageSource;
import com.github.kelemen.brazier.TargetRef;
import com.github.kelemen.brazier.TargetableCharacter;
import org.jtrim.utils.ExceptionHelper;

public final class DamageEvent implements TargetRef {
    private final DamageSource damageSource;
    private final TargetableCharacter target;
    private final int damageDealt;

    public DamageEvent(
            DamageSource damageSource,
            TargetableCharacter target,
            int damageDealt) {
        ExceptionHelper.checkNotNullArgument(damageSource, "damageSource");
        ExceptionHelper.checkNotNullArgument(target, "target");

        this.damageSource = damageSource;
        this.target = target;
        this.damageDealt = damageDealt;
    }

    public DamageSource getDamageSource() {
        return damageSource;
    }

    @Override
    public TargetableCharacter getTarget() {
        return target;
    }

    public int getDamageDealt() {
        return damageDealt;
    }
}
