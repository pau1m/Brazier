package com.github.kelemen.brazier.events;

import com.github.kelemen.brazier.Keyword;
import com.github.kelemen.brazier.LabeledEntity;
import com.github.kelemen.brazier.Player;
import com.github.kelemen.brazier.PlayerProperty;
import com.github.kelemen.brazier.actions.CardPlayRef;
import com.github.kelemen.brazier.cards.Card;
import java.util.Set;
import org.jtrim.utils.ExceptionHelper;

public final class CardPlayedEvent implements PlayerProperty, LabeledEntity, CardPlayRef {
    private final Card card;
    private final int manaCost;

    public CardPlayedEvent(Card card, int manaCost) {
        ExceptionHelper.checkNotNullArgument(card, "card");

        this.card = card;
        this.manaCost = manaCost;
    }

    @Override
    public Set<Keyword> getKeywords() {
        return card.getKeywords();
    }

    @Override
    public Player getOwner() {
        return card.getOwner();
    }

    @Override
    public Card getCard() {
        return card;
    }

    @Override
    public int getManaCost() {
        return manaCost;
    }
}
