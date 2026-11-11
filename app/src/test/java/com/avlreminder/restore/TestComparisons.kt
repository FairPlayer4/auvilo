package com.avlreminder.restore

import com.avlreminder.data.AvlDatabase

/** Every table the same, one row each. */
fun sameEverywhere() = Comparison(AvlDatabase.VERSION, BackupTable.entries.map { TableDiff(it, 1, 1, 0, 0, 0) }, true)

/** One reminder changed. */
fun oneReminderChanged() = Comparison(
    AvlDatabase.VERSION,
    BackupTable.entries.map { TableDiff(it, 1, 1, if (it == BackupTable.REMINDER) 1 else 0, 0, 0) },
    true,
)
