package com.github.kelemen.hearthstone.emulator;

import com.github.kelemen.hearthstone.emulator.cards.CardDescr;
import com.github.kelemen.hearthstone.emulator.minions.MinionDescr;
import com.github.kelemen.hearthstone.emulator.parsing.CardParser;
import com.github.kelemen.hearthstone.emulator.parsing.EntityParser;
import com.github.kelemen.hearthstone.emulator.parsing.HeroPowerParser;
import com.github.kelemen.hearthstone.emulator.parsing.JsonDeserializer;
import com.github.kelemen.hearthstone.emulator.parsing.MinionParser;
import com.github.kelemen.hearthstone.emulator.parsing.ObjectParsingException;
import com.github.kelemen.hearthstone.emulator.parsing.ParserUtils;
import com.github.kelemen.hearthstone.emulator.parsing.UseTrackerJsonTree;
import com.github.kelemen.hearthstone.emulator.parsing.WeaponParser;
import com.github.kelemen.hearthstone.emulator.weapons.WeaponDescr;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jtrim.utils.ExceptionHelper;

public final class HearthStoneDb {
    private final HearthStoneEntityDatabase<MinionDescr> minionDb;
    private final HearthStoneEntityDatabase<CardDescr> cardDb;
    private final HearthStoneEntityDatabase<WeaponDescr> weaponDb;
    private final HearthStoneEntityDatabase<HeroPowerDef> heroPowerDb;

    public HearthStoneDb(
            HearthStoneEntityDatabase<WeaponDescr> weaponDb,
            HearthStoneEntityDatabase<MinionDescr> minionDb,
            HearthStoneEntityDatabase<CardDescr> cardDb,
            HearthStoneEntityDatabase<HeroPowerDef> heroPowerDb) {
        ExceptionHelper.checkNotNullArgument(minionDb, "minionDb");
        ExceptionHelper.checkNotNullArgument(cardDb, "cardDb");
        ExceptionHelper.checkNotNullArgument(weaponDb, "weaponDb");
        ExceptionHelper.checkNotNullArgument(heroPowerDb, "heroPowerDb");

        this.weaponDb = weaponDb;
        this.minionDb = minionDb;
        this.cardDb = cardDb;
        this.heroPowerDb = heroPowerDb;
    }

    private static Path tryGetDefaultCardDbPath() {
        URI jarUri;

        try {
            jarUri = HearthStoneDb.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException ex) {
            return null;
        }

        Path dir = Paths.get(jarUri).getParent();
        if (dir == null) {
            return null;
        }

        return dir.resolve("card-db.zip");
    }

    private static Path tryGetCardDbPath() {
        String cardDbPath = System.getProperty("CARD_DB");
        if (cardDbPath == null) {
            return tryGetDefaultCardDbPath();
        }
        else {
            return Paths.get(cardDbPath);
        }
    }

    public static HearthStoneDb readDefault() throws IOException, ObjectParsingException {
        Path cardDbPath = tryGetCardDbPath();
        if (cardDbPath == null) {
            throw new IllegalStateException("Missing card database.");
        }

        return fromPath(cardDbPath);
    }

    public static HearthStoneDb fromPath(Path path) throws IOException, ObjectParsingException {
        if (Files.isDirectory(path)) {
            return fromRoot(path);
        }
        else {
            FileSystem zipFS = FileSystems.newFileSystem(path, null);
            Iterator<Path> roots = zipFS.getRootDirectories().iterator();
            if (!roots.hasNext()) {
                throw new IOException("No root dir in " + path);
            }
            return fromRoot(roots.next());
        }
    }

    private static HearthStoneDb fromRoot(Path root) throws IOException, ObjectParsingException {
        Path weaponDir = root.resolve("weapons");
        Path minionDir = root.resolve("minions");
        Path cardDir = root.resolve("cards");
        Path powerDir = root.resolve("powers");

        AtomicReference<HearthStoneEntityDatabase<CardDescr>> cardDbRef = new AtomicReference<>();
        AtomicReference<HearthStoneDb> resultRef = new AtomicReference<>();

        JsonDeserializer objectParser = ParserUtils.createDefaultDeserializer(resultRef::get);

        HearthStoneEntityDatabase<WeaponDescr> weaponDb
                = createWeaponDb(weaponDir, objectParser);
        HearthStoneEntityDatabase<MinionDescr> minionDb
                = createMinionDb(minionDir, objectParser, cardDbRef::get);
        HearthStoneEntityDatabase<CardDescr> cardDb
                = createCardDb(cardDir, objectParser, minionDb);
        HearthStoneEntityDatabase<HeroPowerDef> heroPowerDb
                = createHeroPowerDb(powerDir, objectParser);

        cardDbRef.set(cardDb);

        HearthStoneDb result = new HearthStoneDb(weaponDb, minionDb, cardDb, heroPowerDb);
        resultRef.set(result);

        return result;
    }

    private static boolean hasExt(Path path, String ext) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(ext);
    }

    private static HearthStoneEntityDatabase<WeaponDescr> createWeaponDb(
            Path weaponDir,
            JsonDeserializer objectParser) throws IOException, ObjectParsingException {

        return createEntityDb(weaponDir, ".weapon", new WeaponParser(objectParser));
    }

    private static HearthStoneEntityDatabase<MinionDescr> createMinionDb(
            Path minionDir,
            JsonDeserializer objectParser,
            Supplier<HearthStoneEntityDatabase<CardDescr>> cardDbRef) throws IOException, ObjectParsingException {

        return createEntityDb(minionDir, ".minion", new MinionParser(cardDbRef, objectParser));
    }

    private static HearthStoneEntityDatabase<CardDescr> createCardDb(
            Path cardDir,
            JsonDeserializer objectParser,
            HearthStoneEntityDatabase<MinionDescr> minionDb) throws IOException, ObjectParsingException {

        return createEntityDb(cardDir, ".card", new CardParser(minionDb, objectParser));
    }

    private static HearthStoneEntityDatabase<HeroPowerDef> createHeroPowerDb(
            Path powerDir,
            JsonDeserializer objectParser) throws IOException, ObjectParsingException {

        return createEntityDb(powerDir, ".power", new HeroPowerParser(objectParser));
    }

    private static <T extends HearthStoneEntity> HearthStoneEntityDatabase<T> createEntityDb(
            Path powerDir,
            String extension,
            EntityParser<T> parser) throws IOException, ObjectParsingException {

        HearthStoneEntityDatabase.Builder<T> result = new HearthStoneEntityDatabase.Builder<>();

        try (DirectoryStream<Path> entityFiles = Files.newDirectoryStream(powerDir)) {
            for (Path entityFile: entityFiles) {
                if (hasExt(entityFile, extension)) {
                    try {
                        JsonObject entityObj = ParserUtils.fromJsonFile(entityFile);
                        UseTrackerJsonTree trackedTree = new UseTrackerJsonTree(entityObj);
                        result.addEntity(parser.fromJson(trackedTree));
                        trackedTree.checkRequestedAllElements();
                    } catch (Exception ex) {
                        throw new ObjectParsingException("Failed to parse " + entityFile.getFileName(), ex);
                    }
                }
            }
        }

        return result.create();
    }

    public HearthStoneEntityDatabase<HeroPowerDef> getHeroPowerDb() {
        return heroPowerDb;
    }

    public HearthStoneEntityDatabase<WeaponDescr> getWeaponDb() {
        return weaponDb;
    }

    public HearthStoneEntityDatabase<MinionDescr> getMinionDb() {
        return minionDb;
    }

    public HearthStoneEntityDatabase<CardDescr> getCardDb() {
        return cardDb;
    }
}