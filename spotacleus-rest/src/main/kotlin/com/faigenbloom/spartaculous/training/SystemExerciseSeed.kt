package com.faigenbloom.spartaculous.training

import kotlinx.serialization.Serializable

@Serializable
data class SystemExerciseSeed(
    val key: SystemExerciseKey,       // standardized system key
    val name: String,
    val category: String,
    val iconKey: IconKey,
    val metrics: ExerciseMetricsDto? = null,
    val defaultSettings: ExerciseDefaultSettingsDto? = null
)

val SYSTEM_EXERCISES: List<SystemExerciseSeed> = listOf(
    // Strength — Chest
    SystemExerciseSeed(
        SystemExerciseKey.BENCH_PRESS,
        "Bench Press",
        "Strength",
        IconKey.FitnessCenter,
        defaultSettings = ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(5, 10), weightUnit = "kg")
    ),
    SystemExerciseSeed(SystemExerciseKey.DUMBBELL_BENCH_PRESS, "Dumbbell Bench Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.INCLINE_DUMBBELL_PRESS, "Incline Dumbbell Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.INCLINE_BARBELL_PRESS, "Incline Barbell Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.DUMBBELL_FLY, "Dumbbell Fly", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.CABLE_CROSSOVER, "Cable Crossover", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(
        SystemExerciseKey.PUSH_UPS,
        "Push-ups",
        "Strength",
        IconKey.FitnessCenter,
        metrics = ExerciseMetricsDto(
            mode = ExerciseMode.StrengthBodyweight,
            supportsSets = true,
            supportsReps = true,
            supportsExtraLoad = true,
            supportsRestTimer = true
        ),
        defaultSettings = ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(12, 25))
    ),
    SystemExerciseSeed(
        SystemExerciseKey.WEIGHTED_PUSH_UPS,
        "Weighted Push-ups",
        "Strength",
        IconKey.FitnessCenter,
        metrics = ExerciseMetricsDto(
            mode = ExerciseMode.StrengthBodyweight,
            supportsSets = true,
            supportsReps = true,
            supportsExtraLoad = true,
            supportsRestTimer = true
        ),
        defaultSettings = ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(8, 20), weightUnit = "kg")
    ),
    SystemExerciseSeed(SystemExerciseKey.DIPS, "Dips", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.MACHINE_CHEST_PRESS, "Machine Chest Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.DUMBBELL_PULLOVER, "Dumbbell Pullover", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.CABLE_PULLOVER, "Cable Pullover", "Strength", IconKey.FitnessCenter),

    // Strength — Back
    SystemExerciseSeed(
        SystemExerciseKey.WIDE_GRIP_PULL_UPS,
        "Wide-grip Pull-ups",
        "Strength",
        IconKey.FitnessCenter,
        metrics = ExerciseMetricsDto(
            mode = ExerciseMode.StrengthBodyweight,
            supportsSets = true,
            supportsReps = true,
            supportsExtraLoad = true,
            supportsRestTimer = true
        ),
        defaultSettings = ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(6, 12))
    ),
    SystemExerciseSeed(SystemExerciseKey.CHIN_UPS, "Chin-ups", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.WEIGHTED_PULL_UPS, "Weighted Pull-ups", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BARBELL_ROW, "Barbell Row", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.ONE_ARM_DUMBBELL_ROW, "One-arm Dumbbell Row", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.SEATED_CABLE_ROW, "Seated Cable Row", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.LAT_PULLDOWN, "Lat Pulldown", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.T_BAR_ROW, "T-bar Row", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(
        SystemExerciseKey.DEADLIFT,
        "Deadlift",
        "Strength",
        IconKey.FitnessCenter,
        defaultSettings = ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(3, 6), weightUnit = "kg")
    ),
    SystemExerciseSeed(SystemExerciseKey.ROMANIAN_DEADLIFT, "Romanian Deadlift", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BACK_EXTENSION, "Back Extension", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BARBELL_SHRUGS, "Barbell Shrugs", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.DUMBBELL_SHRUGS, "Dumbbell Shrugs", "Strength", IconKey.FitnessCenter),

    // Strength — Legs
    SystemExerciseSeed(
        SystemExerciseKey.SQUAT,
        "Squat",
        "Strength",
        IconKey.FitnessCenter,
        defaultSettings = ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(5, 12), weightUnit = "kg")
    ),
    SystemExerciseSeed(SystemExerciseKey.FRONT_SQUAT, "Front Squat", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.DUMBBELL_SQUAT, "Dumbbell Squat", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.HACK_SQUAT, "Hack Squat", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.LEG_PRESS, "Leg Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.FORWARD_LUNGE, "Forward Lunge", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.REVERSE_LUNGE, "Reverse Lunge", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BULGARIAN_SPLIT_SQUAT, "Bulgarian Split Squat", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.STIFF_LEG_DEADLIFT, "Stiff-leg Deadlift", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.LYING_LEG_CURL, "Lying Leg Curl", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.LEG_EXTENSION, "Leg Extension", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.STANDING_CALF_RAISE, "Standing Calf Raise", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.SEATED_CALF_RAISE, "Seated Calf Raise", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.PISTOL_SQUAT, "Pistol Squat", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.JUMP_SQUAT, "Jump Squat", "Strength", IconKey.FitnessCenter),

    // Strength — Shoulders
    SystemExerciseSeed(SystemExerciseKey.OVERHEAD_PRESS, "Overhead Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.SEATED_DUMBBELL_PRESS, "Seated Dumbbell Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.ARNOLD_PRESS, "Arnold Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.LATERAL_RAISE, "Lateral Raise", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.REVERSE_FLY, "Reverse Fly", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.UPRIGHT_ROW, "Upright Row", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.FRONT_RAISE, "Front Raise", "Strength", IconKey.FitnessCenter),

    // Strength — Arms
    SystemExerciseSeed(SystemExerciseKey.BARBELL_CURL, "Barbell Curl", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.DUMBBELL_CURL, "Dumbbell Curl", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.HAMMER_CURL, "Hammer Curl", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.CONCENTRATION_CURL, "Concentration Curl", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.CABLE_CURL, "Cable Curl", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.FRENCH_PRESS, "French Press", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.TRICEP_PUSHDOWN, "Tricep Pushdown", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.CLOSE_GRIP_PUSH_UPS, "Close-grip Push-ups", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.TRICEP_DIPS, "Tricep Dips", "Strength", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BENCH_DIPS, "Bench Dips", "Strength", IconKey.FitnessCenter),

    // Core
    SystemExerciseSeed(SystemExerciseKey.CRUNCH, "Crunch", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.LYING_LEG_RAISE, "Lying Leg Raise", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.HANGING_LEG_RAISE, "Hanging Leg Raise", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(
        SystemExerciseKey.PLANK,
        "Plank",
        "Core",
        IconKey.SelfImprovement,
        metrics = ExerciseMetricsDto(
            mode = ExerciseMode.Core,
            supportsSets = true,
            supportsDuration = true,
            supportsLevel = true,
            supportsRestTimer = true
        ),
        defaultSettings = ExerciseDefaultSettingsDto(durationStepSec = 30)
    ),
    SystemExerciseSeed(SystemExerciseKey.SIDE_PLANK, "Side Plank", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.BICYCLE_CRUNCH, "Bicycle Crunch", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.RUSSIAN_TWIST, "Russian Twist", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.MOUNTAIN_CLIMBERS, "Mountain Climbers", "Cardio", IconKey.DirectionsRun),
    SystemExerciseSeed(SystemExerciseKey.V_UPS, "V-ups", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.WOODCHOP, "Cable Woodchop", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.AB_WHEEL_ROLL_OUT, "Ab Wheel Roll-out", "Core", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.HOLLOW_BODY_HOLD, "Hollow Body Hold", "Core", IconKey.SelfImprovement),

    // Cardio
    SystemExerciseSeed(
        SystemExerciseKey.RUNNING,
        "Running",
        "Cardio",
        IconKey.DirectionsRun,
        metrics = ExerciseMetricsDto(
            mode = ExerciseMode.CardioDistance,
            supportsDuration = true,
            supportsDistance = true,
            supportsTempo = true
        ),
        defaultSettings = ExerciseDefaultSettingsDto(durationStepSec = 300, distanceUnit = "km")
    ),
    SystemExerciseSeed(
        SystemExerciseKey.SPRINT,
        "Sprint",
        "Cardio",
        IconKey.DirectionsRun,
        metrics = ExerciseMetricsDto(
            mode = ExerciseMode.CardioTime,
            supportsSets = true,
            supportsDuration = true,
            supportsTempo = true,
            supportsIntervals = true,
            supportsRestTimer = true
        ),
        defaultSettings = ExerciseDefaultSettingsDto(durationStepSec = 60)
    ),
    SystemExerciseSeed(SystemExerciseKey.STAIR_RUNNING, "Stair Running", "Cardio", IconKey.DirectionsRun),
    SystemExerciseSeed(SystemExerciseKey.WALKING, "Walking", "Cardio", IconKey.DirectionsWalk),
    SystemExerciseSeed(SystemExerciseKey.NORDIC_WALKING, "Nordic Walking", "Cardio", IconKey.DirectionsWalk),
    SystemExerciseSeed(
        SystemExerciseKey.CYCLING,
        "Cycling",
        "Cardio",
        IconKey.DirectionsBike,
        metrics = ExerciseMetricsDto(
            mode = ExerciseMode.CardioDistance,
            supportsDuration = true,
            supportsDistance = true,
            supportsTempo = true
        ),
        defaultSettings = ExerciseDefaultSettingsDto(durationStepSec = 300, distanceUnit = "km")
    ),
    SystemExerciseSeed(SystemExerciseKey.STATIONARY_BIKE, "Stationary Bike", "Cardio", IconKey.DirectionsBike),
    SystemExerciseSeed(SystemExerciseKey.ELLIPTICAL, "Elliptical", "Cardio", IconKey.DirectionsRun),
    SystemExerciseSeed(SystemExerciseKey.ROWING_MACHINE, "Rowing Machine", "Cardio", IconKey.Rowing),
    SystemExerciseSeed(SystemExerciseKey.STEPPER, "Stepper", "Cardio", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.JUMP_ROPE, "Jump Rope", "Cardio", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BURPEES, "Burpees", "Cardio", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.RUN_IN_PLACE, "Run in Place", "Cardio", IconKey.DirectionsRun),
    SystemExerciseSeed(SystemExerciseKey.INTERVAL_RUN, "Interval Run", "Cardio", IconKey.Timer),

    // Functional / Cross-style
    SystemExerciseSeed(
        SystemExerciseKey.THRUSTER,
        "Thruster",
        "Functional",
        IconKey.FitnessCenter,
        defaultSettings = ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(8, 12), weightUnit = "kg")
    ),
    SystemExerciseSeed(SystemExerciseKey.KETTLEBELL_SWING, "Kettlebell Swing", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.KETTLEBELL_SNATCH, "Kettlebell Snatch", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.KETTLEBELL_CLEAN_AND_JERK, "Kettlebell Clean & Jerk", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.FARMERS_WALK, "Farmer's Walk", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.TURKISH_GET_UP, "Turkish Get-up", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.MEDICINE_BALL_THROW, "Medicine Ball Throw", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BATTLE_ROPES, "Battle Ropes", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.SLED_PUSH, "Sled Push", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.BOX_JUMP, "Box Jump", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.WALL_BALL, "Wall Ball", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.CLEAN_AND_JERK, "Clean & Jerk", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.SNATCH, "Snatch", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.POWER_CLEAN, "Power Clean", "Functional", IconKey.FitnessCenter),
    SystemExerciseSeed(SystemExerciseKey.DEADLIFT_HIGH_PULL, "Deadlift High Pull", "Functional", IconKey.FitnessCenter),

    // Calisthenics / Bodyweight
    SystemExerciseSeed(SystemExerciseKey.MUSCLE_UP, "Muscle-up", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.INVERTED_ROW, "Inverted Row", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.L_SIT, "L-sit", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.HANDSTAND, "Handstand", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.HANDSTAND_PUSH_UP, "Handstand Push-up", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.AUSTRALIAN_PULL_UP, "Australian Pull-up", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.PLANCHE, "Planche", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.HUMAN_FLAG, "Human Flag", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.KIPPING_PULL_UP, "Kipping Pull-up", "Calisthenics", IconKey.SportsGymnastics),
    SystemExerciseSeed(SystemExerciseKey.LONG_JUMP, "Long Jump", "Calisthenics", IconKey.SportsGymnastics),

    // Combat
    SystemExerciseSeed(SystemExerciseKey.BOXING, "Boxing", "Combat", IconKey.SportsMartialArts),
    SystemExerciseSeed(SystemExerciseKey.KICKBOXING, "Kickboxing", "Combat", IconKey.SportsMartialArts),
    SystemExerciseSeed(SystemExerciseKey.MUAY_THAI, "Muay Thai", "Combat", IconKey.SportsMartialArts),
    SystemExerciseSeed(SystemExerciseKey.MMA, "MMA", "Combat", IconKey.SportsMma),
    SystemExerciseSeed(SystemExerciseKey.WRESTLING, "Wrestling", "Combat", IconKey.SportsKabaddi),
    SystemExerciseSeed(SystemExerciseKey.JUDO, "Judo", "Combat", IconKey.SportsMartialArts),
    SystemExerciseSeed(SystemExerciseKey.SAMBO, "Sambo", "Combat", IconKey.SportsMartialArts),
    SystemExerciseSeed(SystemExerciseKey.TAEKWONDO, "Taekwondo", "Combat", IconKey.SportsMartialArts),
    SystemExerciseSeed(SystemExerciseKey.KARATE, "Karate", "Combat", IconKey.SportsMartialArts),
    SystemExerciseSeed(SystemExerciseKey.SHADOW_BOXING, "Shadow Boxing", "Combat", IconKey.SportsMartialArts),

    // Mobility & Flexibility
    SystemExerciseSeed(SystemExerciseKey.YOGA, "Yoga", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.PILATES, "Pilates", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.LEG_STRETCH, "Leg Stretch", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.BACK_STRETCH, "Back Stretch", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.SPLITS, "Splits", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.BRIDGE, "Bridge", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.DYNAMIC_WARMUP, "Dynamic Warm-up", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.JOINT_MOBILITY, "Joint Mobility", "Mobility", IconKey.SelfImprovement),
    SystemExerciseSeed(SystemExerciseKey.STRETCHING, "Stretching", "Mobility", IconKey.SelfImprovement),

    // Team & racket sports
    SystemExerciseSeed(SystemExerciseKey.FOOTBALL, "Football", "Sports", IconKey.SportsSoccer),
    SystemExerciseSeed(SystemExerciseKey.FUTSAL, "Futsal", "Sports", IconKey.SportsSoccer),
    SystemExerciseSeed(SystemExerciseKey.BASKETBALL, "Basketball", "Sports", IconKey.SportsBasketball),
    SystemExerciseSeed(SystemExerciseKey.VOLLEYBALL, "Volleyball", "Sports", IconKey.SportsVolleyball),
    SystemExerciseSeed(SystemExerciseKey.TENNIS, "Tennis", "Sports", IconKey.SportsTennis),
    SystemExerciseSeed(SystemExerciseKey.TABLE_TENNIS, "Table Tennis", "Sports", IconKey.SportsEsports),
    SystemExerciseSeed(SystemExerciseKey.BADMINTON, "Badminton", "Sports", IconKey.SportsHandball),
    SystemExerciseSeed(SystemExerciseKey.HANDBALL, "Handball", "Sports", IconKey.SportsHandball),
    SystemExerciseSeed(SystemExerciseKey.RUGBY, "Rugby", "Sports", IconKey.SportsRugby),
    SystemExerciseSeed(SystemExerciseKey.HOCKEY, "Hockey", "Sports", IconKey.SportsHockey),

    // Outdoor & other activities
    SystemExerciseSeed(SystemExerciseKey.SWIMMING, "Swimming", "Outdoor", IconKey.Pool),
    SystemExerciseSeed(SystemExerciseKey.TRIATHLON, "Triathlon", "Outdoor", IconKey.DirectionsRun),
    SystemExerciseSeed(SystemExerciseKey.MOUNTAINEERING, "Mountaineering", "Outdoor", IconKey.Hiking),
    SystemExerciseSeed(SystemExerciseKey.ROCK_CLIMBING, "Rock Climbing", "Outdoor", IconKey.Hiking),
    SystemExerciseSeed(SystemExerciseKey.SKIING, "Skiing", "Outdoor", IconKey.DownhillSkiing),
    SystemExerciseSeed(SystemExerciseKey.SNOWBOARD, "Snowboard", "Outdoor", IconKey.Snowboarding),
    SystemExerciseSeed(SystemExerciseKey.ICE_SKATING, "Ice Skating", "Outdoor", IconKey.IceSkating),
    SystemExerciseSeed(SystemExerciseKey.ROLLERBLADING, "Rollerblading", "Outdoor", IconKey.RollerSkating),
    SystemExerciseSeed(SystemExerciseKey.ROWING_OUTDOOR, "Rowing", "Outdoor", IconKey.Rowing),
    SystemExerciseSeed(SystemExerciseKey.KAYAKING, "Kayaking", "Outdoor", IconKey.Kayaking)
)
