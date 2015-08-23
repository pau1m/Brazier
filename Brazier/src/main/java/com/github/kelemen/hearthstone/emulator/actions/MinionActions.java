package com.github.kelemen.hearthstone.emulator.actions;

import com.github.kelemen.hearthstone.emulator.BoardLocationRef;
import com.github.kelemen.hearthstone.emulator.Damage;
import com.github.kelemen.hearthstone.emulator.Deck;
import com.github.kelemen.hearthstone.emulator.Hand;
import com.github.kelemen.hearthstone.emulator.Hero;
import com.github.kelemen.hearthstone.emulator.Player;
import com.github.kelemen.hearthstone.emulator.SummonLocationRef;
import com.github.kelemen.hearthstone.emulator.UndoableIntResult;
import com.github.kelemen.hearthstone.emulator.UndoableResult;
import com.github.kelemen.hearthstone.emulator.World;
import com.github.kelemen.hearthstone.emulator.abilities.ActivatableAbility;
import com.github.kelemen.hearthstone.emulator.abilities.Aura;
import com.github.kelemen.hearthstone.emulator.cards.Card;
import com.github.kelemen.hearthstone.emulator.cards.CardDescr;
import com.github.kelemen.hearthstone.emulator.minions.Minion;
import com.github.kelemen.hearthstone.emulator.minions.MinionBody;
import com.github.kelemen.hearthstone.emulator.minions.MinionDescr;
import com.github.kelemen.hearthstone.emulator.minions.MinionProvider;
import com.github.kelemen.hearthstone.emulator.parsing.NamedArg;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.jtrim.utils.ExceptionHelper;

public final class MinionActions {
    public static final MinionAction IMMUNE_THIS_TURN = (world, minion) -> {
        return ActionUtils.doTemporary(world, () -> {
            return minion.getProperties().getBody().getImmuneProperty().addRemovableBuff(true);
        });
    };

    public static final MinionAction RESTORES_SELF_HEALTH = (world, minion) -> {
        MinionBody body = minion.getBody();
        int damage = body.getCurrentHp() - body.getMaxHp();
        if (damage >= 0) {
            return UndoAction.DO_NOTHING;
        }
        return ActionUtils.damageCharacter(minion, damage, minion);
    };

    public static final MinionAction SELF_DESTRUCT = (world, minion) -> {
        return minion.poison();
    };

    public static final MinionAction GIVE_DIVINE_SHIELD = (world, minion) -> {
        return minion.getProperties().getBody().setDivineShield(true);
    };

    public static final MinionAction CHARGE = (world, minion) -> {
        return minion.setCharge(true);
    };

    public static final MinionAction DOUBLE_ATTACK = (world, minion) -> {
        return minion.getProperties().getBuffableAttack().addBuff((prev) -> 2 * prev);
    };

    public static final MinionAction SHUFFLE_MINION = (world, minion) -> {
        Player owner = minion.getOwner();
        Deck deck = owner.getBoard().getDeck();
        CardDescr baseCard = minion.getBaseDescr().getBaseCard();

        UndoAction removeUndo = minion.getLocationRef().removeFromBoard();
        UndoAction shuffleUndo = deck.putToRandomPosition(world.getRandomProvider(), baseCard);
        return () -> {
            shuffleUndo.undo();
            removeUndo.undo();
        };
    };

    public static final MinionAction STEALTH = (world, minion) -> {
        return minion.getBody().setStealth(true);
    };

    public static final MinionAction TAUNT = (world, minion) -> {
        return minion.getBody().setTaunt(true);
    };

    public static final MinionAction TWILIGHT_BUFF = (world, minion) -> {
        return buff(minion, 0, minion.getOwner().getHand().getCardCount());
    };

    public static final MinionAction RETURN_MINION = returnMinion(0);

    public static final MinionAction SWAP_WITH_MINION_IN_HAND = (World world, Minion minion) -> {
        Hand hand = minion.getOwner().getHand();
        int cardIndex = hand.chooseRandomCardIndex(Card::isMinionCard);
        if (cardIndex < 0) {
            return UndoAction.DO_NOTHING;
        }

        CardDescr newCard = minion.getBaseDescr().getBaseCard();
        UndoableResult<Card> replaceCardRef = hand.replaceAtIndex(cardIndex, newCard);
        Minion newMinion = replaceCardRef.getResult().getMinion();
        if (newMinion == null) {
            throw new IllegalStateException("Selected a card with no minion.");
        }

        UndoAction replaceMinionUndo = minion.getLocationRef().replace(newMinion);
        return () -> {
            replaceMinionUndo.undo();
            replaceCardRef.undo();
        };
    };

    public static final MinionAction SUMMON_COPY_FOR_OPPONENT = (World world, Minion minion) -> {
        Player receiver = minion.getOwner().getOpponent();
        Minion newMinion = new Minion(receiver, minion.getBaseDescr());
        newMinion.copyOther(minion);

        return receiver.summonMinion(newMinion);
    };

    public static final MinionAction RESUMMON_RIGHT = (World world, Minion minion) -> {
        return minion.getLocationRef().summonRight(minion.getBaseDescr());
    };

    public static MinionAction addThisTurnAura(@NamedArg("aura") Aura<? super Minion, ? super Minion> aura) {
        ExceptionHelper.checkNotNullArgument(aura, "aura");
        return addThisTurnAura((Minion self) -> aura.applyAura(self.getWorld(), self, self));
    }

    public static MinionAction addThisTurnAura(@NamedArg("ability") ActivatableAbility<? super Minion> ability) {
        ExceptionHelper.checkNotNullArgument(ability, "ability");
        return (World world, Minion minion) -> {
            return ActionUtils.doTemporary(world, () -> ability.activate(minion));
        };
    }

    public static MinionAction addDeathRattle(
            @NamedArg("action") WorldEventAction<? super Minion, ? super Minion> action) {
        ExceptionHelper.checkNotNullArgument(action, "action");

        return (World world, Minion minion) -> {
            return minion.addDeathRattle(action);
        };
    }

    public static MinionAction returnMinion(@NamedArg("costReduction") int costReduction) {
        return (World world, Minion minion) -> {
            Player owner = minion.getOwner();
            CardDescr baseCard = minion.getBaseDescr().getBaseCard();

            UndoBuilder result = new UndoBuilder();
            result.addUndo(minion.getLocationRef().removeFromBoard());

            Card card = new Card(owner, baseCard);
            if (costReduction != 0) {
                result.addUndo(card.decreaseManaCost(costReduction));
            }

            result.addUndo(owner.getHand().addCard(card));

            return result;
        };
    }

    public static MinionAction applyToAdjacentMinionsToo(
            @NamedArg("action") MinionAction action) {
        ExceptionHelper.checkNotNullArgument(action, "action");

        return (World world, Minion minion) -> {
            UndoBuilder result = new UndoBuilder(3);
            result.addUndo(action.alterWorld(world, minion));

            SummonLocationRef locationRef = minion.getLocationRef();
            BoardLocationRef left = locationRef.tryGetLeft();
            if (left != null) {
                result.addUndo(action.alterWorld(world, left.getMinion()));
            }
            BoardLocationRef right = locationRef.tryGetRight();
            if (right != null) {
                result.addUndo(action.alterWorld(world, right.getMinion()));
            }
            return result;
        };
    }

    public static MinionAction reincarnate(@NamedArg("newMinionAction") MinionAction newMinionAction) {
        ExceptionHelper.checkNotNullArgument(newMinionAction, "newMinionAction");

        return (World world, Minion minion) -> {
            Player owner = minion.getOwner();

            UndoBuilder result = new UndoBuilder();
            result.addUndo(minion.poison());
            result.addUndo(world.endPhase());

            Minion newMinion = new Minion(owner, minion.getBaseDescr());
            result.addUndo(owner.summonMinion(newMinion));
            result.addUndo(newMinionAction.alterWorld(world, newMinion));

            return result;
        };
    }

    public static MinionAction transformMinion(@NamedArg("minion") MinionProvider[] minion) {
        List<MinionProvider> minionCopy = new ArrayList<>(Arrays.asList(minion));
        ExceptionHelper.checkNotNullElements(minionCopy, "minion");

        return transformMinion((originalMinion) -> {
            MinionProvider selected = ActionUtils.pickRandom(originalMinion.getWorld(), minionCopy);
            return selected != null ? selected.getMinion() : null;
        });
    }

    public static MinionAction transformMinion(Function<? super Minion, MinionDescr> newMinionGetter) {
        ExceptionHelper.checkNotNullArgument(newMinionGetter, "newMinionGetter");

        return (World world, Minion minion) -> {
            MinionDescr newMinion = newMinionGetter.apply(minion);
            return minion.transformTo(newMinion);
        };
    }

    public static MinionAction activateAbility(
            @NamedArg("ability") ActivatableAbility<? super Minion> ability) {
        return (World world, Minion minion) -> {
            return ability.activate(minion);
        };
    }

    public static MinionAction damageSelf(@NamedArg("damage") int damage) {
        return (World world, Minion self) -> {
            return ActionUtils.damageCharacter(self, damage, self);
        };
    }

    public static MinionAction buffHp(@NamedArg("hp") int hp) {
        return buff(0, hp);
    }

    public static MinionAction buffAttack(@NamedArg("attack") int attack) {
        return buff(attack, 0);
    }

    private static UndoAction buff(Minion minion, int attack, int hp) {
        if (attack == 0) {
            return minion.getBody().getHp().buffHp(hp);
        }
        if (hp == 0) {
            return minion.addAttackBuff(attack);
        }

        UndoAction attackBuffUndo = minion.addAttackBuff(attack);
        UndoAction hpBuffUndo = minion.getBody().getHp().buffHp(hp);
        return () -> {
            hpBuffUndo.undo();
            attackBuffUndo.undo();
        };
    }

    public static MinionAction buffAttack(
            @NamedArg("minAttack") int minAttack,
            @NamedArg("maxAttack") int maxAttack) {
        return (World world, Minion minion) -> {
            int buff = world.getRandomProvider().roll(minAttack, maxAttack);
            return minion.addAttackBuff(buff);
        };
    }

    public static MinionAction setAttack(@NamedArg("attack") int attack) {
        return (World world, Minion minion) -> {
            return minion.getProperties().getBuffableAttack().setValueTo(attack);
        };
    }

    public static MinionAction setHp(@NamedArg("hp") int hp) {
        return (World world, Minion minion) -> {
            return minion.getBody().getHp().setCurrentHp(hp);
        };
    }

    public static MinionAction setMaxHp(@NamedArg("hp") int hp) {
        return (World world, Minion minion) -> {
            return minion.getBody().getHp().setMaxHp(hp);
        };
    }

    public static MinionAction buff(
            @NamedArg("attack") int attack,
            @NamedArg("hp") int hp) {
        return (World world, Minion minion) -> {
            return buff(minion, attack, hp);
        };
    }

    public static MinionAction vancleefBuff(
            @NamedArg("attack") int attack,
            @NamedArg("hp") int hp) {
        return (World world, Minion minion) -> {
            int mul = minion.getOwner().getCardsPlayedThisTurn() - 1;
            if (mul <= 0) {
                return UndoAction.DO_NOTHING;
            }

            return buff(minion, attack * mul, hp * mul);
        };
    }

    public static MinionAction buff(
            @NamedArg("ability") ActivatableAbility<? super Minion> ability) {
        ExceptionHelper.checkNotNullArgument(ability, "ability");

        return (World world, Minion minion) -> {
            return minion.addAndActivateAbility(ability);
        };
    }

    private static UndoAction buffAt(
            World world,
            BoardLocationRef locationRef,
            WorldObjectAction<? super Minion> buff) {
        if (locationRef != null) {
            return buff.alterWorld(world, locationRef.getMinion());
        }
        else {
            return UndoAction.DO_NOTHING;
        }
    }

    public static MinionAction buffNeighbours(
            @NamedArg("buff") WorldObjectAction<? super Minion> buff) {
        return (World world, Minion minion) -> {
            SummonLocationRef locationRef = minion.getLocationRef();

            UndoAction buffLeftUndo = buffAt(world, locationRef.tryGetLeft(), buff);
            UndoAction buffRightUndo = buffAt(world, locationRef.tryGetRight(), buff);

            return () -> {
                buffRightUndo.undo();
                buffLeftUndo.undo();
            };
        };
    }

    public static MinionAction addAbilityToNeighbours(
            @NamedArg("ability") ActivatableAbility<? super Minion> ability) {
        ExceptionHelper.checkNotNullArgument(ability, "ability");

        return buffNeighbours((World world, Minion minion) -> {
            return minion.addAndActivateAbility(ability);
        });
    }

    public static MinionAction summonMinionLeft(@NamedArg("minion") MinionProvider minion) {
        return (world, sourceMinion) -> {
            return sourceMinion.getLocationRef().summonLeft(minion.getMinion());
        };
    }

    public static MinionAction summonMinionRight(@NamedArg("minion") MinionProvider minion) {
        return (world, sourceMinion) -> {
            return sourceMinion.getLocationRef().summonRight(minion.getMinion());
        };
    }

    public static MinionAction resummonMinionWithHp(@NamedArg("hp") int hp) {
        return (World world, Minion minion) -> {
            Minion newMinion = new Minion(minion.getOwner(), minion.getBaseDescr());

            UndoAction summonUndo = minion.getLocationRef().summonRight(newMinion);
            UndoAction updateHpUndo = newMinion.getProperties().getBody().getHp().setCurrentHp(1);
            return () -> {
                updateHpUndo.undo();
                summonUndo.undo();
            };
        };
    }

    public static MinionAction damageOwnHero(@NamedArg("damage") int damage) {
        return (world, minion) -> {
            Hero hero = minion.getOwner().getHero();
            UndoableResult<Damage> damageRef = minion.createDamage(damage);
            UndoableIntResult damageUndo = hero.damage(damageRef.getResult());
            return () -> {
                damageUndo.undo();
                damageRef.undo();
            };
        };
    }

    public static MinionAction damageOpponentHero(@NamedArg("damage") int damage) {
        return (world, minion) -> {
            Hero hero = minion.getOwner().getOpponent().getHero();
            UndoableResult<Damage> damageRef = minion.createDamage(damage);
            UndoableIntResult damageUndo = hero.damage(damageRef.getResult());
            return () -> {
                damageUndo.undo();
                damageRef.undo();
            };
        };
    }

    private MinionActions() {
        throw new AssertionError();
    }
}
