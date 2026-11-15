package com.avlreminder.data

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// One explicit migration per version (design.md 13); no destructive fallback.

/** V1.1a: combinable proofs, proof window, "do not disturb", snooze buttons, "Startklar" answers. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofPhoto INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofTextMinChars INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofAudioMinSeconds INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN proofWindowMinutes INTEGER NOT NULL DEFAULT 10")
        db.execSQL("ALTER TABLE reminder ADD COLUMN ignoreDnd INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminder ADD COLUMN snoozeMinutes TEXT NOT NULL DEFAULT '10'")
        db.execSQL("ALTER TABLE reminder ADD COLUMN motivationAudio TEXT")
        db.execSQL("UPDATE reminder SET proof = 'EVIDENCE', proofPhoto = 1 WHERE proof = 'PHOTO'")
        db.execSQL("ALTER TABLE occurrence ADD COLUMN pausedUntil INTEGER")
        db.execSQL("ALTER TABLE proof ADD COLUMN text TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `prep_answer` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`reminderId` INTEGER NOT NULL, `question` INTEGER NOT NULL, `text` TEXT, `unknown` INTEGER NOT NULL, " +
                "`at` INTEGER NOT NULL, FOREIGN KEY(`reminderId`) REFERENCES `reminder`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_prep_answer_reminderId` ON `prep_answer` (`reminderId`)")
    }
}

/** V1.1b: arrival trigger with places, checklists. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN arrivalPlaceId INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN arrivalWhenAway TEXT")
        db.execSQL("ALTER TABLE occurrence ADD COLUMN firstProgressAt INTEGER")
        db.execSQL("ALTER TABLE proof ADD COLUMN itemId INTEGER")
        CREATE_3.forEach(db::execSQL)
    }
}

/** V1.2a: archive, suggestions. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN archivedAt INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `suggestion` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, " +
                "`important` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `screen` TEXT NOT NULL, `reminderId` INTEGER, " +
                "`reminderTitle` TEXT, `appVersion` TEXT NOT NULL, `status` TEXT NOT NULL, `statusAt` INTEGER)",
        )
    }
}

/** V1.2b: weekly goal. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN weekTarget INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN weekMax INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN weekMinimum INTEGER")
    }
}

/**
 * V1.2c: deadlines, notice before, blockers as tasks, templates. The newest answer to "Was
 * fehlt?" that names something becomes an open blocker.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN windowUntilMinutes INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN remindEveryMinutes INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN preNoticeEveningMinutes INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN preNoticeBeforeMinutes INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN prepFor INTEGER")
        db.execSQL("ALTER TABLE reminder ADD COLUMN solvedBlockerIsMinimum INTEGER NOT NULL DEFAULT 0")
        CREATE_6.forEach(db::execSQL)
        db.execSQL(
            "INSERT INTO blocker (reminderId, text, createdAt, solvedAt, origin, taskReminderId) " +
                "SELECT p.reminderId, trim(p.text), p.at, NULL, 'STARTKLAR', NULL FROM prep_answer p " +
                "WHERE p.question = 3 AND p.unknown = 0 AND p.text IS NOT NULL AND trim(p.text) NOT IN ('', 'Ja, sofort') " +
                "AND p.id = (SELECT q.id FROM prep_answer q WHERE q.reminderId = p.reminderId AND q.question = 3 " +
                "ORDER BY q.at DESC, q.id DESC LIMIT 1)",
        )
    }
}

/** V1.3a: several times a day, the snoozing hint. Only new nullable columns. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN moreTimes TEXT")
        db.execSQL("ALTER TABLE reminder ADD COLUMN hintQuietUntil INTEGER")
        db.execSQL("ALTER TABLE template ADD COLUMN moreTimes TEXT")
    }
}

/**
 * V1.3b0.2 (Review 2026-11-05, E5): when an occurrence first rang and whether it waits for an
 * arrival. firedAt is taken from its first DUE event, so occurrences that rang before keep that.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE occurrence ADD COLUMN firedAt INTEGER")
        db.execSQL("ALTER TABLE occurrence ADD COLUMN awaitsArrival INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE occurrence SET firedAt = (SELECT MIN(e.at) FROM event e WHERE e.occurrenceId = occurrence.id AND e.type = 'DUE')",
        )
    }
}

/**
 * V1.3b1 (Anf. 7.29): arrival window per reminder with the F3 defaults for existing ones (OZ9),
 * the WLANs of the places, the arrival that counts for an occurrence.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN arrivalLead INTEGER NOT NULL DEFAULT 90")
        db.execSQL("ALTER TABLE reminder ADD COLUMN arrivalBuffer INTEGER NOT NULL DEFAULT 5")
        db.execSQL("ALTER TABLE reminder ADD COLUMN arrivalCheck INTEGER NOT NULL DEFAULT 20")
        db.execSQL("ALTER TABLE occurrence ADD COLUMN arrivedAt INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `place_wifi` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `placeId` INTEGER NOT NULL, " +
                "`bssid` TEXT NOT NULL, `ssid` TEXT, " +
                "FOREIGN KEY(`placeId`) REFERENCES `place`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_place_wifi_placeId` ON `place_wifi` (`placeId`)")
    }
}

/** V1.3b (Anf. 7.11): the session with timer per reminder and its times per occurrence. Only new nullable columns. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (column in listOf("sessionCheck", "sessionGoal", "sessionMinimum", "sessionMax", "sessionFinal")) {
            db.execSQL("ALTER TABLE reminder ADD COLUMN $column INTEGER")
        }
        db.execSQL("ALTER TABLE reminder ADD COLUMN sessionCheckStrictness TEXT")
        for (column in listOf("startedAt", "nextCheckAt", "finalCallAt")) {
            db.execSQL("ALTER TABLE occurrence ADD COLUMN $column INTEGER")
        }
    }
}

/** V1.3c1 (Z2b): the postpone limit per occurrence, week or month; existing ones stay per occurrence. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("reminder", "template")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN postponePeriod TEXT NOT NULL DEFAULT 'OCCURRENCE'")
        }
    }
}

/** The new tables of version 6, as Room generates them (see schemas/…/6.json). */
private val CREATE_6 = listOf(
    "CREATE TABLE IF NOT EXISTS `blocker` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` INTEGER NOT NULL, " +
        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `solvedAt` INTEGER, `origin` TEXT NOT NULL, `taskReminderId` INTEGER, " +
        "FOREIGN KEY(`reminderId`) REFERENCES `reminder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_blocker_reminderId` ON `blocker` (`reminderId`)",
    "CREATE TABLE IF NOT EXISTS `template` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`usedAt` INTEGER NOT NULL, `character` TEXT NOT NULL, `strictness` TEXT NOT NULL, `scheduleType` TEXT NOT NULL, " +
        "`timeMinutes` INTEGER, `weekdays` INTEGER NOT NULL, `intervalDays` INTEGER, `windowUntilMinutes` INTEGER, " +
        "`remindEveryMinutes` INTEGER, `minimum` TEXT, `minimumLimitPerWeek` INTEGER, `proofKind` TEXT NOT NULL, " +
        "`proofPhoto` INTEGER NOT NULL, `proofTextMinChars` INTEGER, `proofAudioMinSeconds` INTEGER, " +
        "`proofWindowMinutes` INTEGER NOT NULL, `postponeLimit` INTEGER, `ignoreDnd` INTEGER NOT NULL, `snoozeMinutes` TEXT NOT NULL)",
)

/** The new tables of version 3, as Room generates them (see schemas/…/3.json). */
private val CREATE_3 = listOf(
    "CREATE TABLE IF NOT EXISTS `place` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`lat` REAL NOT NULL, `lon` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `checklist_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` INTEGER NOT NULL, " +
        "`position` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
        "FOREIGN KEY(`reminderId`) REFERENCES `reminder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_checklist_item_reminderId` ON `checklist_item` (`reminderId`)",
    "CREATE TABLE IF NOT EXISTS `item_done` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `occurrenceId` INTEGER NOT NULL, " +
        "`itemId` INTEGER NOT NULL, `doneAt` INTEGER NOT NULL, `withProof` INTEGER NOT NULL, " +
        "FOREIGN KEY(`occurrenceId`) REFERENCES `occurrence`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`itemId`) REFERENCES `checklist_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_item_done_occurrenceId` ON `item_done` (`occurrenceId`)",
    "CREATE INDEX IF NOT EXISTS `index_item_done_itemId` ON `item_done` (`itemId`)",
)
