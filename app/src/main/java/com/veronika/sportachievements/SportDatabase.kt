package com.veronika.sportachievements

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SportDatabase(
    context: Context,
    databaseName: String = "sport_achievements.db"
) : SQLiteOpenHelper(
    context.applicationContext,
    databaseName,
    null,
    1
) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profile (
                singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
                id TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE exercises (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                activity_type TEXT NOT NULL,
                metric_type TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE workouts (
                id TEXT PRIMARY KEY NOT NULL,
                workout_date TEXT NOT NULL,
                activity_type TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE results (
                id TEXT PRIMARY KEY NOT NULL,
                workout_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                weight_kg REAL,
                reps INTEGER,
                distance_m INTEGER,
                duration_sec INTEGER,

                FOREIGN KEY (workout_id)
                    REFERENCES workouts(id) ON DELETE CASCADE,

                FOREIGN KEY (exercise_id)
                    REFERENCES exercises(id),

                CHECK (
                    (
                        weight_kg IS NOT NULL
                        AND weight_kg >= 0
                        AND reps IS NOT NULL
                        AND reps > 0
                        AND distance_m IS NULL
                        AND duration_sec IS NULL
                    )
                    OR
                    (
                        weight_kg IS NULL
                        AND reps IS NULL
                        AND distance_m IS NOT NULL
                        AND distance_m > 0
                        AND duration_sec IS NOT NULL
                        AND duration_sec > 0
                    )
                )
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE goals (
                id TEXT PRIMARY KEY NOT NULL,
                exercise_id TEXT NOT NULL,
                metric_type TEXT NOT NULL,
                reps INTEGER,
                distance_m INTEGER,
                target_value REAL NOT NULL,
                started_at TEXT NOT NULL,
                deadline TEXT NOT NULL,

                FOREIGN KEY (exercise_id)
                    REFERENCES exercises(id),

                CHECK (deadline >= started_at),

                CHECK (
                    (
                        metric_type = 'WEIGHT_REPS'
                        AND reps IS NOT NULL
                        AND reps > 0
                        AND distance_m IS NULL
                        AND target_value >= 0
                    )
                    OR
                    (
                        metric_type = 'DISTANCE_TIME'
                        AND reps IS NULL
                        AND distance_m IS NOT NULL
                        AND distance_m > 0
                        AND target_value > 0
                    )
                )
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_workouts_date
            ON workouts(workout_date)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_workouts_activity_date
            ON workouts(activity_type, workout_date)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_results_workout
            ON results(workout_id, position)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_results_exercise
            ON results(exercise_id, workout_id)
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Перед збільшенням версії бази тут додаються міграції.
        // Автоматичне видалення таблиць не використовується.
        throw IllegalStateException(
            "Потрібна міграція бази з версії $oldVersion до $newVersion"
        )
    }
}