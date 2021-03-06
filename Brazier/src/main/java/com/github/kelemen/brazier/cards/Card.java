package com.github.kelemen.brazier.cards;

import com.github.kelemen.brazier.Damage;
import com.github.kelemen.brazier.DamageSource;
import com.github.kelemen.brazier.Keyword;
import com.github.kelemen.brazier.LabeledEntity;
import com.github.kelemen.brazier.Player;
import com.github.kelemen.brazier.PlayerProperty;
import com.github.kelemen.brazier.UndoableResult;
import com.github.kelemen.brazier.abilities.AuraAwareIntProperty;
import com.github.kelemen.brazier.actions.CardRef;
import com.github.kelemen.brazier.actions.ManaCostAdjuster;
import com.github.kelemen.brazier.actions.UndoAction;
import com.github.kelemen.brazier.minions.Minion;
import com.github.kelemen.brazier.minions.MinionDescr;
import java.util.List;
import java.util.Set;
import org.jtrim.utils.ExceptionHelper;

public final class Card implements PlayerProperty, LabeledEntity, CardRef, DamageSource {
    private final Player owner;
    private final CardDescr cardDescr;
    private final Minion minion;

    private final AuraAwareIntProperty manaCost;

    public Card(Player owner, CardDescr cardDescr) {
        ExceptionHelper.checkNotNullArgument(cardDescr, "cardDescr");

        this.owner = owner;
        this.cardDescr = cardDescr;
        this.manaCost = new AuraAwareIntProperty(cardDescr.getManaCost());
        this.manaCost.addRemovableBuff(this::adjustManaCost);

        MinionDescr minionDescr = cardDescr.getMinion();
        this.minion = minionDescr != null ? new Minion(owner, cardDescr.getMinion()) : null;
    }

    private int adjustManaCost(int baseCost) {
        List<ManaCostAdjuster> costAdjusters = cardDescr.getManaCostAdjusters();
        int result = baseCost;
        if (!costAdjusters.isEmpty()) {
            for (ManaCostAdjuster adjuster: costAdjusters) {
                result = adjuster.adjustCost(this, result);
            }
        }
        return result;
    }

    public Minion getMinion() {
        return minion;
    }

    @Override
    public Card getCard() {
        return this;
    }

    @Override
    public Player getOwner() {
        return owner;
    }

    @Override
    public Set<Keyword> getKeywords() {
        return cardDescr.getKeywords();
    }

    @Override
    public UndoableResult<Damage> createDamage(int damage) {
        if (minion != null) {
            return minion.createDamage(damage);
        }

        if (cardDescr.getCardType() == CardType.SPELL) {
            return new UndoableResult<>(getOwner().getSpellDamage(damage));
        }

        return new UndoableResult<>(new Damage(this, damage));
    }

    public CardDescr getCardDescr() {
        return cardDescr;
    }

    public AuraAwareIntProperty getRawManaCost() {
        // Note that the final value might be negative.
        return manaCost;
    }

    public UndoAction decreaseManaCost(int amount) {
        return manaCost.addBuff(-amount);
    }

    public int getActiveManaCost() {
        return Math.max(0, manaCost.getValue());
    }

    public boolean isMinionCard() {
        return cardDescr.getMinion() != null;
    }

    @Override
    public String toString() {
        return cardDescr.toString();
    }
}
