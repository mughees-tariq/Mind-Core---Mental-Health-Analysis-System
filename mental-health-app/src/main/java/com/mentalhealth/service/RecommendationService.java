package com.mentalhealth.service;

import com.mentalhealth.database.DatabaseManager;
import com.mentalhealth.repository.RecommendationProgressRepository;
import com.mentalhealth.service.DashboardService.DashboardData;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

// Service that generates personalised self-care recommendations based on mood trends
public class RecommendationService implements IRecommendationService {

    private static final Logger LOG = Logger.getLogger(RecommendationService.class.getName());

    private final DashboardService dashboardService;
    private final RecommendationProgressRepository progressRepo;

    public RecommendationService() {
        this.dashboardService = new DashboardService();
        this.progressRepo = new RecommendationProgressRepository();
    }

    //  DATA CLASSES 

    /** A single actionable recommendation item. */
    public static class RecItem {
        public String key;
        public String emoji;
        public String title;
        public String description;
        public String duration;
        public String difficulty;
        public String whyItHelps;
        public List<String> steps;
        public boolean completed;

        public RecItem(String key, String emoji, String title, String description,
                       String duration, String difficulty, String whyItHelps, List<String> steps) {
            this.key = key;
            this.emoji = emoji;
            this.title = title;
            this.description = description;
            this.duration = duration;
            this.difficulty = difficulty;
            this.whyItHelps = whyItHelps;
            this.steps = steps;
            this.completed = false;
        }
    }

    /** A section grouping related recommendation items by condition. */
    public static class RecSection {
        public String sectionKey;
        public String emoji;
        public String title;
        public String conditionBadge;
        public String badgeColor;
        public List<RecItem> items = new ArrayList<>();

        public RecSection(String sectionKey, String emoji, String title, String conditionBadge, String badgeColor) {
            this.sectionKey = sectionKey;
            this.emoji = emoji;
            this.title = title;
            this.conditionBadge = conditionBadge;
            this.badgeColor = badgeColor;
        }
    }

    /** Aggregate recommendation data including sections, progress, and weekly stats. */
    public static class RecommendationData {
        public String dominantMood;
        public String moodColor;
        public String riskLevel;
        public String riskColor;
        public List<RecSection> sections = new ArrayList<>();
        public int totalItems;
        public int completedToday;
        public Map<String, Integer> weeklyStats = new LinkedHashMap<>();
    }

    //  MAIN METHOD 

    public RecommendationData getRecommendations(int userId) {
        RecommendationData data = new RecommendationData();

        DashboardData dash = dashboardService.getDashboardData(userId);
        // Use LATEST prediction so recommendations change instantly on new analysis
        String mood = dash.latestPrediction != null && !"None".equals(dash.latestPrediction)
                ? dash.latestPrediction : (dash.dominantMood != null ? dash.dominantMood : "Normal");
        data.dominantMood = mood;
        data.moodColor = dash.latestColor != null && !"#757575".equals(dash.latestColor)
                ? dash.latestColor : dash.dominantMoodColor;
        data.riskLevel = dash.riskLevel;
        data.riskColor = dash.riskColor != null ? dash.riskColor : "#94a3b8";

        String badge = "For " + mood;
        String color = data.moodColor;

        // Keys include mood prefix so progress resets when mood changes
        data.sections.add(buildActivities(mood, badge, color));
        data.sections.add(buildMeditations(mood, badge, color));
        data.sections.add(buildExercises(mood, badge, color));
        data.sections.add(buildCBT(mood, badge, color));
        data.sections.add(buildSleepHygiene(mood, badge, color));
        data.sections.add(buildNutrition(mood, badge, color));

        Set<String> completed = getTodayProgress(userId);
        int total = 0, done = 0;
        for (RecSection section : data.sections) {
            for (RecItem item : section.items) {
                total++;
                if (completed.contains(item.key)) {
                    item.completed = true;
                    done++;
                }
            }
        }
        data.totalItems = total;
        data.completedToday = done;
        data.weeklyStats = getWeeklyStats(userId);
        return data;
    }

    // mood prefix ensures keys change when mood changes
    private String moodKey(String mood, String base) {
        String prefix = mood.toLowerCase().replaceAll("[^a-z]", "");
        return prefix + "_" + base;
    }

    //  SECTION BUILDERS (3 items each, with steps + whyItHelps) ──

    private RecSection buildActivities(String mood, String badge, String color) {
        RecSection s = new RecSection("activities", "\uD83D\uDCC5", "Personalized Activity Plan", badge, color);
        switch (mood) {
            case "Anxiety" -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\uD83C\uDF05", "Morning Grounding Routine",
                    "A sensory grounding exercise that pulls you out of anxious thoughts and anchors you in the present moment.",
                    "5 min", "Easy",
                    "Grounding activates the parasympathetic nervous system, reducing cortisol and calming the fight-or-flight response that fuels anxiety.",
                    List.of("Stand barefoot on the floor and feel the texture beneath your feet",
                            "Name 5 things you can see around you right now",
                            "Touch 4 different textures — your shirt, the wall, a cup, your hair",
                            "Listen for 3 distinct sounds in your environment",
                            "Take 3 slow deep breaths: inhale 4 seconds, exhale 6 seconds")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\u270D\uFE0F", "Worry Deconstruction Journal",
                    "A structured writing exercise that transforms vague anxiety into concrete, manageable pieces.",
                    "10 min", "Medium",
                    "Writing externalizes worries from your mind onto paper, reducing their emotional intensity by up to 43% according to research.",
                    List.of("Write down the worry exactly as it appears in your mind",
                            "Ask: What is the worst realistic outcome? (not catastrophic, realistic)",
                            "Ask: What is the most likely outcome based on past experience?",
                            "Write one small action you can take today about this worry",
                            "If you cannot act on it today, write: 'I will revisit this on [specific date]'")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83C\uDF3F", "Evening Nature Reset",
                    "A slow, intentional walk focused on engaging your senses with the natural world around you.",
                    "20 min", "Easy",
                    "Nature exposure lowers cortisol, blood pressure, and heart rate. Even 20 minutes of green space reduces anxiety biomarkers significantly.",
                    List.of("Leave your phone behind or switch it to silent mode",
                            "Walk slowly — half your normal speed",
                            "Focus on colors: count how many shades of green you notice",
                            "Stop for 1 minute and close your eyes — listen to ambient sounds",
                            "Before heading back, take 5 deep breaths with your eyes closed")));
            }
            case "Depression" -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\u2600\uFE0F", "Sunlight Activation",
                    "Get natural light exposure early in the day to regulate your circadian rhythm and boost serotonin.",
                    "15 min", "Easy",
                    "Morning sunlight suppresses melatonin and triggers serotonin production. Even on cloudy days, outdoor light is 10x brighter than indoor light.",
                    List.of("Within 30 minutes of waking, go to a window or step outside",
                            "Face the direction of the sun (don't stare directly at it)",
                            "Stand or sit for 15 minutes — you can have your morning drink",
                            "If it's cloudy, still go outside — overcast daylight is still beneficial",
                            "Notice the warmth on your skin and take 3 slow breaths")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\uD83D\uDCDE", "Micro Social Connection",
                    "Reach out to one person today with a small, low-pressure interaction to combat isolation.",
                    "5 min", "Medium",
                    "Social isolation worsens depression. Even brief interactions release oxytocin and activate reward pathways, breaking the withdrawal cycle.",
                    List.of("Choose one person: a friend, family member, or colleague",
                            "Send a simple message: 'Hey, was thinking of you. How are you?'",
                            "If texting feels too hard, react to their social media post — it counts",
                            "No pressure to have a long conversation — the initiation IS the win",
                            "After sending, note how you feel. Often it's better than expected")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83D\uDE4F", "Three Good Things",
                    "A research-backed gratitude practice that rewires your brain to notice positive experiences.",
                    "5 min", "Easy",
                    "This exercise, developed by Martin Seligman, increases happiness scores for up to 6 months when practiced daily. It counteracts depression's negativity bias.",
                    List.of("At the end of the day, write down 3 things that went okay",
                            "They can be tiny: 'I drank water' or 'The sun was out'",
                            "For each one, write WHY it happened or why it mattered",
                            "Read them back to yourself once",
                            "Keep a running list — review past entries when you feel low")));
            }
            case "Stress" -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\uD83D\uDCDD", "Top-3 Priority Reset",
                    "Cut through overwhelm by identifying and committing to only your 3 most important tasks today.",
                    "10 min", "Easy",
                    "Decision fatigue and task overload are primary stress amplifiers. Constraining to 3 priorities reduces cognitive load and restores a sense of control.",
                    List.of("Write down everything on your mind — dump it all onto paper",
                            "Circle the 3 items that would make the biggest difference if completed",
                            "For each, write the very first physical action needed to start",
                            "Cross out or defer everything else — give yourself permission",
                            "Commit: 'Today I will focus on these 3 things only'")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\u2615", "50-10 Mindful Breaks",
                    "Work in focused 50-minute blocks followed by 10-minute restorative breaks.",
                    "Ongoing", "Easy",
                    "The ultradian rhythm research shows humans naturally cycle through 90-minute energy periods. Regular breaks prevent burnout and maintain cortisol at healthy levels.",
                    List.of("Set a timer for 50 minutes and work with full focus",
                            "When the timer rings, STOP — even mid-sentence",
                            "Stand up and stretch your arms overhead for 30 seconds",
                            "Walk to get water or look out a window for 2 minutes",
                            "Take 5 slow breaths before starting the next 50-minute block")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83D\uDCF5", "Digital Sunset",
                    "Create a firm boundary between your connected day and your restorative evening.",
                    "60 min", "Medium",
                    "Screen time before bed increases cortisol and delays melatonin release by 90 minutes. A digital boundary lets your nervous system downshift.",
                    List.of("Choose a cutoff time (ideally 1 hour before bed)",
                            "Put your phone in another room on a charger",
                            "Switch to an offline activity: reading, stretching, cooking",
                            "If you need your phone for an alarm, use Do Not Disturb mode",
                            "Notice how your mind feels after 30 minutes of no screens")));
            }
            case "Normal" -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\uD83C\uDF1E", "Morning Intention Setting",
                    "Start each day with a clear, positive intention that guides your focus and energy.",
                    "5 min", "Easy",
                    "Intention-setting activates the prefrontal cortex and primes your brain's reticular activating system to notice opportunities aligned with your goals.",
                    List.of("Before checking your phone, sit for 1 minute in stillness",
                            "Ask yourself: 'What is one thing I want to accomplish today?'",
                            "Write it down in one sentence",
                            "Visualize yourself completing it — see the end result",
                            "Carry this intention through your day")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\uD83E\uDD1D", "Intentional Kindness",
                    "Perform one deliberate act of kindness for someone else today.",
                    "10 min", "Easy",
                    "Acts of kindness boost serotonin and oxytocin in both the giver and receiver. During stable periods, building social bonds creates resilience for harder days.",
                    List.of("Choose someone in your life — a friend, colleague, or stranger",
                            "Do one kind thing: a compliment, holding a door, sending an encouraging text",
                            "Make it specific and genuine — not generic",
                            "Notice how you feel afterward",
                            "Try to make this a daily habit while your mood is stable")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83D\uDCD3", "Evening Reflection",
                    "End your day with a brief structured reflection that builds self-awareness.",
                    "10 min", "Easy",
                    "Regular reflection strengthens metacognition and emotional intelligence, making you more resilient when challenges arise.",
                    List.of("Write what went well today and why",
                            "Write one thing you'd do differently",
                            "Write one thing you're looking forward to tomorrow",
                            "Rate your overall mood 1-10",
                            "Close the journal and let the day go")));
            }
            case "Bipolar" -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\uD83D\uDCCA", "Mood Charting",
                    "Track your mood, sleep, and energy levels at set times each day to identify episode patterns early.",
                    "5 min", "Easy",
                    "Mood charting is the gold standard for bipolar management. It helps you and your care team detect manic or depressive shifts 2-3 days before they fully develop.",
                    List.of("Rate your mood on a scale from -5 (depressed) to 0 (stable) to +5 (manic) three times daily",
                            "Record hours of sleep you got last night",
                            "Note your energy level: Low / Normal / High / Excessive",
                            "Record any irritability, racing thoughts, or impulsive urges",
                            "Look for patterns over the week — are numbers trending up or down?",
                            "Share this chart with your therapist or psychiatrist at your next visit")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\u23F0", "Rigid Routine Anchoring",
                    "Maintain extremely consistent daily routines — the most powerful non-medication tool for bipolar stability.",
                    "Ongoing", "Medium",
                    "Social Rhythm Therapy research shows that disrupted routines are the #1 trigger for bipolar episodes. Consistent meal, sleep, and activity times stabilize circadian rhythms.",
                    List.of("Set fixed times for waking, meals, and bedtime — write them down",
                            "Wake at the same time every day including weekends (non-negotiable)",
                            "Eat meals within 30 minutes of your scheduled time",
                            "Schedule physical activity at the same time each day",
                            "Avoid spontaneous late nights or schedule changes when possible",
                            "If your routine is disrupted, return to it the very next day")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83D\uDEB7", "Stimulation Management",
                    "Monitor and moderate your exposure to stimulating environments, substances, and activities.",
                    "Ongoing", "Medium",
                    "Overstimulation can trigger manic episodes while understimulation can trigger depression. Learning to calibrate stimulation is key to bipolar stability.",
                    List.of("Track caffeine intake — limit to 1 cup before noon",
                            "Avoid alcohol completely — it destabilizes mood cycles",
                            "Monitor social events — too many in a row can trigger hypomania",
                            "If you feel 'unusually great,' pause and check your mood chart",
                            "When energy feels excessive, channel it into calm activities (walking, organizing)",
                            "If energy is very low, do one small activity to prevent a depressive spiral")));
            }
            case "Personality Disorder" -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\uD83C\uDFAF", "Values-Based Action",
                    "Identify your core values and take one deliberate action aligned with them today, regardless of how you feel.",
                    "15 min", "Medium",
                    "Personality disorders often create a gap between values and actions. Acting on values even during emotional turbulence builds a stable sense of identity over time.",
                    List.of("Write your top 3 personal values (e.g., honesty, connection, growth)",
                            "Choose ONE value to focus on today",
                            "Write one specific action that represents this value",
                            "Do this action even if your emotions resist — the action IS the practice",
                            "Afterward, note how acting on your values made you feel",
                            "Repeat daily — this gradually strengthens your sense of self")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\uD83D\uDCDD", "Interpersonal Effectiveness Log",
                    "After each significant interaction today, reflect on what went well and what you'd adjust.",
                    "10 min", "Medium",
                    "Tracking interpersonal patterns builds awareness of reactive behaviors. Over time, this awareness creates a pause between trigger and response — the space where change happens.",
                    List.of("After an important interaction, write: Who, What happened, How I felt",
                            "Rate the interaction 1-10: How effective was my communication?",
                            "Did I express my needs clearly? If not, what got in the way?",
                            "Did I respect the other person's boundaries?",
                            "One thing I did well in this interaction",
                            "One thing I'd do differently next time")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83D\uDE4F", "Self-Validation Practice",
                    "Practice acknowledging and validating your own emotions without judgment, 3 times today.",
                    "5 min", "Easy",
                    "Chronic invalidation is a core wound in personality disorders. Self-validation rebuilds the internal emotional support system that may not have developed naturally.",
                    List.of("When you notice an emotion, pause and name it: 'I am feeling ___'",
                            "Say to yourself: 'It makes sense that I feel this way given my experience'",
                            "Do NOT add 'but I shouldn't feel this way' — just validate",
                            "Practice this at least 3 times today with different emotions",
                            "Even 'negative' emotions are valid — they carry information",
                            "Over time, self-validation reduces emotional intensity naturally")));
            }
            case "Suicidal" -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\uD83D\uDCDE", "Reach Out to Support",
                    "Contact one trusted person or crisis service today. You don't need to go through this alone.",
                    "10 min", "Easy",
                    "Isolation amplifies suicidal thoughts. A single connection — even a text — can reduce crisis intensity. You deserve support right now.",
                    List.of("Choose one option that feels manageable right now:",
                            "Call/text 988 Suicide & Crisis Lifeline (available 24/7)",
                            "Text HOME to 741741 (Crisis Text Line)",
                            "Call a trusted friend or family member",
                            "Message your therapist or counselor",
                            "If you're in immediate danger, call emergency services or go to your nearest ER",
                            "Remember: reaching out is the bravest thing you can do")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\uD83D\uDEE1\uFE0F", "Safety Plan Review",
                    "Review or create your personal safety plan — a set of steps to follow when thoughts become overwhelming.",
                    "15 min", "Medium",
                    "A written safety plan reduces suicide attempts by 43%. Having concrete steps ready means you don't need to think clearly during a crisis — the plan thinks for you.",
                    List.of("Write your warning signs: What thoughts/feelings signal a crisis is building?",
                            "List 3 coping strategies you can do alone (walking, ice on wrists, breathing)",
                            "List 3 people you can call for distraction (friends, family)",
                            "List 3 professionals/crisis lines you can call",
                            "Write one reason to keep going — even a small one",
                            "Remove or secure access to anything that could be used for self-harm",
                            "Keep this plan somewhere visible — phone wallpaper, bedside table, wallet")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83C\uDF1F", "One Gentle Thing",
                    "Do one small, gentle thing for yourself today. Self-care during a crisis is not selfish — it's survival.",
                    "10 min", "Easy",
                    "When everything feels hopeless, tiny acts of self-care send a signal to your brain that you are worth caring for. This interrupts the hopelessness cycle, even briefly.",
                    List.of("Choose ONE from this list — whichever feels least impossible:",
                            "Take a warm shower and notice the sensation of the water",
                            "Hold a warm cup of tea or cocoa — feel the warmth in your hands",
                            "Step outside for 5 minutes and feel the air on your face",
                            "Wrap yourself in a blanket and listen to calming sounds",
                            "If nothing feels possible, just lie down and breathe — that counts too",
                            "You survived today. That is enough.")));
            }
            default -> {
                s.items.add(new RecItem(moodKey(mood, "act_1"), "\uD83C\uDF1E", "Morning Intention Setting",
                    "Start each day with a clear, positive intention.", "5 min", "Easy",
                    "Intention-setting activates the prefrontal cortex and primes your brain to notice aligned opportunities.",
                    List.of("Sit for 1 minute in stillness", "Ask: 'What do I want to accomplish today?'",
                            "Write it down", "Visualize completing it", "Carry this intention through your day")));
                s.items.add(new RecItem(moodKey(mood, "act_2"), "\uD83E\uDD1D", "Intentional Kindness",
                    "Perform one deliberate act of kindness today.", "10 min", "Easy",
                    "Acts of kindness boost serotonin and oxytocin in both giver and receiver.",
                    List.of("Choose someone", "Do one kind thing", "Make it specific", "Notice how you feel", "Build the habit")));
                s.items.add(new RecItem(moodKey(mood, "act_3"), "\uD83D\uDCD3", "Evening Reflection",
                    "End your day with structured reflection.", "10 min", "Easy",
                    "Regular reflection strengthens metacognition and emotional intelligence.",
                    List.of("Write what went well", "Write one thing to improve", "Rate mood 1-10", "Look forward to tomorrow", "Close the journal")));
            }
        }
        return s;
    }

    private RecSection buildMeditations(String mood, String badge, String color) {
        RecSection s = new RecSection("meditations", "\uD83E\uDDD8", "Guided Meditation", badge, color);
        switch (mood) {
            case "Anxiety" -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\uD83C\uDF2C\uFE0F", "4-7-8 Calming Breath",
                    "A clinically proven breathing pattern that activates your vagus nerve and calms anxiety within minutes.",
                    "5 min", "Easy",
                    "Developed by Dr. Andrew Weil, this technique forces a longer exhale which stimulates the vagus nerve, shifting your body from fight-or-flight to rest-and-digest.",
                    List.of("Sit comfortably with your back straight",
                            "Place the tip of your tongue behind your upper front teeth",
                            "Exhale completely through your mouth with a whoosh sound",
                            "Inhale quietly through your nose for 4 seconds",
                            "Hold your breath for 7 seconds",
                            "Exhale completely through your mouth for 8 seconds",
                            "Repeat for 4 full cycles")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83D\uDCA7", "Progressive Muscle Relaxation",
                    "Systematically tense and release each muscle group to release physical tension that anxiety stores in your body.",
                    "12 min", "Easy",
                    "Anxiety creates unconscious muscle tension. PMR breaks this cycle by teaching your body the contrast between tension and relaxation, reducing anxiety by up to 50%.",
                    List.of("Lie down or sit in a comfortable position",
                            "Start with your feet: curl your toes tightly for 5 seconds, then release",
                            "Move to calves: flex them hard for 5 seconds, then release",
                            "Continue upward: thighs, stomach, fists, arms, shoulders",
                            "Scrunch your face tightly for 5 seconds, then release",
                            "Finish by tensing your ENTIRE body for 5 seconds, then release everything",
                            "Lie still for 2 minutes feeling the relaxation spread")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\u2693", "5-4-3-2-1 Grounding Meditation",
                    "A sensory meditation that quickly anchors you in the present when anxiety pulls you into future fears.",
                    "5 min", "Easy",
                    "This technique interrupts the anxiety loop by redirecting neural resources from the amygdala (fear center) to the sensory cortex (present-moment processing).",
                    List.of("Sit still and take 3 deep breaths to begin",
                            "Name 5 things you can SEE — describe their color and shape",
                            "Name 4 things you can TOUCH — reach out and feel their texture",
                            "Name 3 things you can HEAR — close your eyes and listen",
                            "Name 2 things you can SMELL — breathe in deeply",
                            "Name 1 thing you can TASTE — notice what's in your mouth",
                            "Take 3 more deep breaths and notice how your body feels now")));
            }
            case "Depression" -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\u2600\uFE0F", "Loving-Kindness Meditation",
                    "Direct compassionate phrases toward yourself and others to counter the self-criticism that depression amplifies.",
                    "10 min", "Medium",
                    "Research shows loving-kindness meditation increases positive emotions and self-compassion while decreasing self-criticism — depression's primary fuel.",
                    List.of("Sit comfortably and close your eyes",
                            "Place your hand on your heart and feel its warmth",
                            "Silently repeat: 'May I be happy. May I be healthy. May I be safe.'",
                            "Now think of someone you love — repeat the phrases for them",
                            "Think of a neutral person (a neighbor, cashier) — repeat for them",
                            "End by sending kindness to all beings: 'May all beings be at peace'",
                            "Sit quietly for 1 minute and notice any shift in how you feel")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83C\uDF1F", "Safe Place Visualization",
                    "Create a vivid mental sanctuary you can return to whenever depression feels overwhelming.",
                    "10 min", "Easy",
                    "Visualization activates the same brain regions as real experience. Building a mental safe place gives you an instant emotional refuge that strengthens with practice.",
                    List.of("Close your eyes and imagine a place where you feel completely safe",
                            "Make it vivid: What does the air feel like? What temperature is it?",
                            "Add sounds: waves, birds, rain, silence — whatever feels peaceful",
                            "Add colors and lighting — golden sunlight, soft moonlight",
                            "Imagine sitting or lying down there, completely at ease",
                            "Spend 5 minutes just being in this space",
                            "Before opening your eyes, tell yourself: 'I can return here anytime'")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\uD83D\uDCA8", "Energizing Breath Work",
                    "A short, active breathing practice that boosts energy and alertness when depression makes you feel heavy.",
                    "5 min", "Medium",
                    "Rapid breathing increases oxygen levels and stimulates the sympathetic nervous system, providing a natural energy boost that counteracts depression's fatigue.",
                    List.of("Sit upright with your spine straight",
                            "Take 20 rapid belly breaths: sharp inhale through nose, passive exhale",
                            "After 20 breaths, inhale deeply and hold for 15 seconds",
                            "Exhale slowly and breathe normally for 30 seconds",
                            "Repeat the cycle 3 times",
                            "End with 3 slow, deep breaths",
                            "Notice any tingling, warmth, or increased alertness")));
            }
            case "Stress" -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\uD83D\uDCA8", "Box Breathing Reset",
                    "The Navy SEALs' go-to technique for staying calm under pressure. Equal-count breathing that resets your stress response.",
                    "5 min", "Easy",
                    "Box breathing equalizes the autonomic nervous system by making inhale, hold, and exhale durations identical. This signals safety to your brain and lowers cortisol rapidly.",
                    List.of("Sit with your back straight and feet flat on the floor",
                            "Exhale all air from your lungs completely",
                            "Inhale slowly through your nose for 4 seconds",
                            "Hold your breath for 4 seconds (don't tense up)",
                            "Exhale slowly through your mouth for 4 seconds",
                            "Hold empty for 4 seconds",
                            "Repeat for 5 minutes — aim for at least 6 full cycles")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83C\uDF0A", "Body Scan for Tension Release",
                    "A slow scan through your entire body to find and release the specific places where you store stress.",
                    "15 min", "Easy",
                    "Most people store stress unconsciously in their jaw, shoulders, and lower back. A body scan builds awareness so you can release tension before it becomes pain.",
                    List.of("Lie down or sit comfortably. Close your eyes.",
                            "Start at the top of your head — notice any tightness",
                            "Move to your forehead, eyes, jaw — consciously soften each",
                            "Check your shoulders — let them drop away from your ears",
                            "Scan through arms, hands, chest, stomach",
                            "Move through hips, thighs, calves, feet",
                            "Wherever you found tension, breathe into that area for 3 breaths")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\uD83E\uDDD8", "Mindful Minute Practice",
                    "A micro-meditation you can do anywhere, anytime — between meetings, in traffic, or at your desk.",
                    "1 min", "Easy",
                    "Even 60 seconds of mindful breathing reduces cortisol and prevents stress from accumulating throughout the day. Small doses add up.",
                    List.of("Stop whatever you're doing",
                            "Close your eyes or soften your gaze",
                            "Take one deep breath and let it go with a sigh",
                            "For the next 60 seconds, just breathe naturally",
                            "When thoughts come, note them and return to your breath",
                            "After 1 minute, open your eyes slowly",
                            "Resume your day — you've just reset your nervous system")));
            }
            case "Normal" -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\uD83E\uDDD8", "Mindful Breathing",
                    "A foundational meditation that strengthens attention and emotional regulation.",
                    "10 min", "Easy",
                    "Regular mindful breathing thickens the prefrontal cortex and shrinks the amygdala, building resilience for future challenges.",
                    List.of("Sit comfortably with eyes closed", "Breathe naturally", "Focus on breath at your nostrils",
                            "When mind wanders, gently return", "Don't judge — the return IS the practice",
                            "Continue for 10 minutes", "End by noticing how your body feels")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83C\uDF3F", "Walking Meditation",
                    "Transform a walk into a meditative practice connecting mind and body.", "10 min", "Easy",
                    "Walking meditation combines movement and mindfulness, improving mood and the mind-body connection.",
                    List.of("Choose a short path", "Stand still and take 3 breaths", "Walk very slowly — feel heel, ball, toes",
                            "At the end, pause, turn slowly, walk back", "Keep gaze soft", "Refocus when mind wanders", "Continue 10 minutes")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\uD83D\uDE4F", "Gratitude Meditation",
                    "Train your brain to notice positive experiences through contemplation.", "8 min", "Easy",
                    "Gratitude meditation releases dopamine and serotonin while increasing prefrontal cortex activity.",
                    List.of("Sit quietly, close eyes", "Picture someone you're grateful for", "Feel warmth in your chest",
                            "Think of one good experience this week", "Think of one thing you appreciate about yourself",
                            "Sit with gratitude for 2 minutes", "Carry this feeling forward")));
            }
            case "Bipolar" -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\u2696\uFE0F", "Equanimity Meditation",
                    "Practice observing thoughts and emotions without attaching to or resisting them — essential for mood stability.",
                    "12 min", "Medium",
                    "Equanimity — the ability to observe without reacting — helps prevent emotional states from escalating into full episodes. It builds the gap between feeling and action.",
                    List.of("Sit comfortably and close your eyes", "Breathe naturally for 2 minutes",
                            "Notice whatever arises: thoughts, emotions, sensations", "Label each: 'thinking,' 'feeling,' 'sensation'",
                            "Practice not pushing away unpleasant experiences", "Practice not grasping at pleasant ones either",
                            "Remind yourself: 'All states are temporary. I am the observer.'")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83C\uDF0A", "Emotional Surfing",
                    "Learn to ride emotional waves without being pulled under — a core skill for bipolar management.",
                    "10 min", "Medium",
                    "Urge surfing, adapted from addiction therapy, teaches that every emotional wave peaks and passes. This prevents impulsive actions during manic or depressive surges.",
                    List.of("When a strong emotion arises, pause and sit with it", "Rate its intensity 1-10",
                            "Visualize the emotion as an ocean wave", "Watch it rise — don't fight it or act on it",
                            "Notice: the wave naturally peaks and begins to fall", "Keep watching until intensity drops by at least 2 points",
                            "Acknowledge: 'I rode the wave without it controlling me'")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\uD83D\uDCA4", "Sleep Transition Meditation",
                    "A calming meditation specifically designed to support the consistent sleep schedule critical for bipolar stability.",
                    "10 min", "Easy",
                    "Sleep disruption is the strongest trigger for bipolar episodes. This meditation creates a reliable bridge from wakefulness to sleep, protecting your circadian rhythm.",
                    List.of("Do this at the same time every night (non-negotiable)", "Lie in bed with lights off",
                            "Take 10 slow breaths, counting each exhale", "Mentally scan from head to toes, relaxing each area",
                            "Visualize a calming scene: a quiet lake, a gentle rain", "If thoughts arise, label them 'thinking' and return to your scene",
                            "Continue until you feel drowsy — don't force sleep, let it come")));
            }
            case "Personality Disorder" -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\uD83D\uDD25", "TIPP Crisis Skill",
                    "A DBT emergency technique that rapidly changes your body chemistry when emotions reach crisis intensity.",
                    "5 min", "Easy",
                    "TIPP (Temperature, Intense exercise, Paced breathing, Paired muscle relaxation) activates the dive reflex and parasympathetic system, reducing emotional intensity within 60 seconds.",
                    List.of("T — Temperature: Hold ice cubes or splash cold water on your face for 30 seconds",
                            "I — Intense exercise: Do 20 jumping jacks or run in place for 1 minute",
                            "P — Paced breathing: Breathe in 4 counts, out 6 counts for 2 minutes",
                            "P — Paired relaxation: Tense each muscle group 5 seconds then release",
                            "Rate your emotion before and after — the drop is usually 3-4 points",
                            "This is a SKILL, not a cure — use it to get below crisis level, then use other tools")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83E\uDDD8\u200D\u2640\uFE0F", "Wise Mind Meditation",
                    "Access the overlap between your emotional mind and rational mind — the 'wise mind' that integrates both.",
                    "10 min", "Medium",
                    "Wise Mind is a core DBT concept. It's the state where emotion and reason work together. Regular practice makes this balanced state more accessible during crises.",
                    List.of("Sit comfortably and take 5 deep breaths", "Imagine your emotional mind as a hot flame on one side",
                            "Imagine your rational mind as cool ice on the other", "Now picture a warm, golden light where they meet in the center",
                            "This center is your Wise Mind — it honors feelings AND facts",
                            "Ask your Wise Mind one question you're struggling with", "Listen quietly — the answer may come as a feeling, image, or word")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\uD83C\uDF3F", "Radical Acceptance Practice",
                    "Practice fully accepting reality as it is right now — without approving, fighting, or denying it.",
                    "10 min", "Medium",
                    "Radical acceptance (a DBT skill) reduces suffering by ending the war with reality. Pain is unavoidable, but suffering = pain + non-acceptance.",
                    List.of("Identify something you're currently fighting against or refusing to accept",
                            "Say: 'This is what is happening right now. I don't have to like it.'",
                            "Notice where resistance lives in your body — tension, tightness", "Breathe into that area and soften it",
                            "Repeat: 'Fighting this causes me more pain than accepting it'",
                            "Acceptance doesn't mean approval — it means 'I stop wasting energy fighting what is'",
                            "Sit with this for 5 minutes and notice any shift in your suffering level")));
            }
            case "Suicidal" -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\u2744\uFE0F", "Cold Water Reset",
                    "Use cold water on your face to activate your body's dive reflex, instantly slowing your heart rate and calming intense distress.",
                    "3 min", "Easy",
                    "Cold water on the face triggers the mammalian dive reflex, which immediately slows heart rate by 10-25% and redirects blood flow. It's the fastest way to reduce emotional overwhelm.",
                    List.of("Fill a bowl with cold water and ice if available", "Take a deep breath and hold it",
                            "Submerge your face (forehead and cheeks) for 15-30 seconds",
                            "If a bowl isn't available, hold ice packs or cold wet cloth on your face",
                            "Repeat 2-3 times", "Notice your heart rate slowing and thoughts becoming less intense",
                            "This buys you time — use it to call someone or do the next coping step")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83D\uDCA8", "Extended Exhale Breathing",
                    "A simple breathing pattern that directly activates your calming nervous system when everything feels unbearable.",
                    "5 min", "Easy",
                    "A longer exhale than inhale stimulates the vagus nerve, which controls your body's relaxation response. This works even when your mind can't focus on anything else.",
                    List.of("You can do this lying down, sitting, or even curled up", "Inhale through your nose for 3 counts",
                            "Exhale through your mouth for 6 counts — slow and steady", "If 3-6 is hard, try 2-4 — the ratio matters more than the count",
                            "Continue for 5 minutes or until you feel any shift", "Place a hand on your chest to feel it slow down",
                            "You are telling your body: 'I am safe right now, in this moment'")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\uD83C\uDF1F", "Five Senses Grounding",
                    "Reconnect to the present moment through your five senses when your mind feels far away or dark.",
                    "5 min", "Easy",
                    "Grounding through senses pulls your brain out of abstract hopeless thinking and into concrete present-moment reality, where you are safe right now.",
                    List.of("Look around and name 5 things you can see — say them out loud",
                            "Touch 4 different textures and describe how they feel", "Listen for 3 sounds you can identify",
                            "Find 2 things you can smell — even your own skin or clothing", "Notice 1 taste in your mouth",
                            "Take 3 slow breaths", "Repeat to yourself: 'I am here. I am present. This moment will pass.'")));
            }
            default -> {
                s.items.add(new RecItem(moodKey(mood, "med_1"), "\uD83E\uDDD8", "Mindful Breathing",
                    "A foundational meditation practice.", "10 min", "Easy",
                    "Regular mindful breathing builds resilience by strengthening the prefrontal cortex.",
                    List.of("Sit comfortably", "Breathe naturally", "Focus on breath", "Return attention when it wanders",
                            "Continue for 10 minutes", "Notice how you feel")));
                s.items.add(new RecItem(moodKey(mood, "med_2"), "\uD83C\uDF3F", "Walking Meditation",
                    "Transform a walk into meditation.", "10 min", "Easy",
                    "Combines movement and mindfulness for improved mood.",
                    List.of("Choose a short path", "Walk slowly — feel each step", "Pause and turn at the end",
                            "Refocus when mind wanders", "Continue 10 minutes")));
                s.items.add(new RecItem(moodKey(mood, "med_3"), "\uD83D\uDE4F", "Gratitude Meditation",
                    "Train your brain to notice the positive.", "8 min", "Easy",
                    "Releases dopamine and serotonin naturally.",
                    List.of("Sit quietly", "Think of someone you're grateful for", "Recall a positive experience",
                            "Appreciate something about yourself", "Carry the feeling forward")));
            }
        }
        return s;
    }

    private RecSection buildExercises(String mood, String badge, String color) {
        RecSection s = new RecSection("exercises", "\uD83C\uDFCB\uFE0F", "Exercise Therapy", badge, color);
        switch (mood) {
            case "Anxiety" -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83E\uDDD8\u200D\u2640\uFE0F", "Anxiety-Release Yoga Flow",
                    "A gentle yoga sequence specifically designed to open areas where anxiety stores tension: hips, shoulders, and chest.",
                    "20 min", "Easy",
                    "Yoga combines physical movement with breath awareness, reducing cortisol by 27% and GABA levels (the calming neurotransmitter) increase after just one session.",
                    List.of("Start in Child's Pose — rest forehead on mat, arms extended, breathe 5 times",
                            "Move to Cat-Cow — arch and round your spine slowly for 1 minute",
                            "Forward Fold — hang loosely, nod yes and no to release neck tension",
                            "Warrior II — hold each side for 30 seconds, focus on steady breathing",
                            "Pigeon Pose — hold each side 1 minute to release stored hip tension",
                            "End in Savasana — lie flat for 3 minutes, let everything go")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83D\uDEB6", "Rhythmic Walking",
                    "A steady-pace walk that uses rhythm and counting to quiet an anxious mind.",
                    "25 min", "Easy",
                    "Rhythmic bilateral movement (left-right stepping) activates both brain hemispheres, similar to EMDR therapy, helping process and reduce anxiety.",
                    List.of("Start walking at a comfortable, steady pace",
                            "Count your steps: inhale for 4 steps, exhale for 6 steps",
                            "Maintain this rhythm — let the counting occupy your mind",
                            "After 10 minutes, drop the counting and just walk",
                            "Notice the sounds, sights, and air around you",
                            "Walk for a total of 25 minutes before heading back")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83D\uDE4B", "Tension-Release Stretching",
                    "Target the specific muscle groups where anxiety lives: jaw, neck, shoulders, and chest.",
                    "12 min", "Easy",
                    "Anxiety causes chronic muscle contraction. Targeted stretching breaks the feedback loop between tense muscles and an anxious brain.",
                    List.of("Jaw: Open mouth wide, move jaw side to side, then massage with fingers for 30s",
                            "Neck: Slowly tilt ear to shoulder — hold 20s each side",
                            "Shoulders: Roll them backward 10 times, then forward 10 times",
                            "Chest: Interlace hands behind your back, lift and open your chest for 30s",
                            "Wrists & Hands: Shake them vigorously for 15 seconds (release nervous energy)",
                            "Full body: Reach arms overhead, stretch as tall as possible, then fold forward and hang")));
            }
            case "Depression" -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83D\uDEB6\u200D\u2642\uFE0F", "10-Minute Starter Walk",
                    "The lowest-barrier exercise that reliably boosts mood. No gear, no prep — just step outside and move.",
                    "10 min", "Easy",
                    "A 10-minute walk increases endorphins for up to 2 hours. Research shows walking is as effective as antidepressants for mild-to-moderate depression.",
                    List.of("Put on shoes and step outside — that's the hardest part, and you've done it",
                            "Walk at whatever pace feels natural — no pressure",
                            "If 10 minutes feels too long, commit to 5 and see how you feel",
                            "Notice the air on your skin, the ground under your feet",
                            "If thoughts get heavy, count your steps to stay present",
                            "When you return, acknowledge: 'I did something good for myself'")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83D\uDCAA", "Minimal Bodyweight Circuit",
                    "The smallest possible strength workout that still creates a meaningful chemical change in your brain.",
                    "8 min", "Medium",
                    "Resistance exercise increases BDNF (brain-derived neurotrophic factor), which promotes neural growth and is consistently low in people with depression.",
                    List.of("5 Wall Push-ups — stand arm's length from wall, push in and out",
                            "5 Squats — sit back like sitting in a chair, stand back up",
                            "5 Lunges per leg — step forward, lower, push back",
                            "Rest for 60 seconds and breathe",
                            "Repeat the circuit ONE more time",
                            "That's it — 8 minutes, done. You moved your body.")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83C\uDFB5", "Movement to Music",
                    "Put on a song and move however your body wants to. No choreography, no judgment.",
                    "5 min", "Easy",
                    "Music activates dopamine reward pathways. Combined with movement, it's one of the fastest natural mood elevators available.",
                    List.of("Pick ONE song that used to make you feel good (or still does)",
                            "Press play and stand up",
                            "Start with just swaying or tapping your foot",
                            "Let the movement grow naturally — arms, hips, head",
                            "No one is watching — move however feels right",
                            "When the song ends, notice any shift in your energy")));
            }
            case "Stress" -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83C\uDFC3", "Interval Power Walk",
                    "Alternate between brisk and gentle walking to burn off stress hormones through controlled physical intensity.",
                    "20 min", "Easy",
                    "Interval-style exercise is more effective at reducing cortisol than steady-state cardio. The intensity fluctuations mirror and then resolve your stress response.",
                    List.of("Start with 2 minutes of gentle walking to warm up",
                            "Walk briskly for 2 minutes — like you're late for something",
                            "Slow down to a gentle pace for 1 minute",
                            "Repeat the brisk/gentle cycle 5 more times",
                            "End with 2 minutes of slow walking to cool down",
                            "Total: 20 minutes of stress-busting movement")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83E\uDD4A", "Stress-Release Shadow Boxing",
                    "Channel frustration and tension into controlled, powerful movements.",
                    "10 min", "Medium",
                    "Physical expression of stress through controlled aggression activates catharsis and burns adrenaline, the stress hormone that keeps you wired and on edge.",
                    List.of("Stand with feet shoulder-width apart, fists up near your chin",
                            "Throw alternating jabs at the air — controlled, not wild",
                            "Add hooks (circular punches) — exhale sharply with each punch",
                            "Do 2-minute rounds with 30-second rest between",
                            "Complete 3-4 rounds",
                            "End by shaking your hands and arms loose for 30 seconds")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83E\uDDD8\u200D\u2642\uFE0F", "Power Yoga for Stress",
                    "Active yoga that uses strong poses and controlled breathing to transform stress energy into calm strength.",
                    "25 min", "Medium",
                    "Power yoga combines strength-building with nervous system regulation, giving you the physical release of exercise plus the calming effects of breathwork.",
                    List.of("3 Sun Salutations — flow through the sequence slowly with breath",
                            "Hold Warrior I for 5 breaths each side — feel your legs engage",
                            "Chair Pose — hold for 10 breaths, feel the burn in your thighs",
                            "Plank — hold for 30 seconds, engage your entire core",
                            "Seated Forward Fold — surrender into it for 1 minute",
                            "Final Savasana — lie flat for 3 minutes, let your body absorb the work")));
            }
            case "Normal" -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83C\uDFC3\u200D\u2642\uFE0F", "30-Minute Jog",
                    "Maintain your fitness and mood stability with a moderate-intensity run.",
                    "30 min", "Medium",
                    "Regular aerobic exercise is one of the strongest protective factors against mental health decline. It builds the neurochemical reserves your brain draws on during tough times.",
                    List.of("Warm up with 3 minutes of walking", "Begin jogging at a conversational pace",
                            "Focus on steady breathing: in through nose, out through mouth",
                            "If you need to walk, walk — then resume jogging",
                            "At 27 minutes, slow to a walk for your cooldown",
                            "Stretch your calves, quads, and hamstrings for 3 minutes")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83D\uDCAA", "Bodyweight Strength Circuit",
                    "A full-body circuit that builds physical and mental resilience during your stable period.",
                    "20 min", "Medium",
                    "Strength training increases self-efficacy, improves sleep, and builds the physical resilience that supports mental health.",
                    List.of("10 Push-ups (modify on knees if needed)", "15 Squats with arms extended forward",
                            "10 Lunges per leg", "30-second Plank hold",
                            "10 Tricep dips on a chair edge", "Rest 60 seconds, then repeat 2 more rounds")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83E\uDDD8", "Flexibility & Recovery",
                    "A full-body stretching routine that prevents injury and promotes relaxation.",
                    "15 min", "Easy",
                    "Flexibility work activates the parasympathetic nervous system and prevents the muscle tension that accumulates from daily stress.",
                    List.of("Neck rolls: 5 slow circles each direction",
                            "Shoulder stretch: pull each arm across your chest, hold 20s",
                            "Standing quad stretch: hold each side 20s",
                            "Seated hamstring stretch: reach for toes, hold 30s",
                            "Hip opener: pigeon pose each side, hold 30s",
                            "Final child's pose: rest here for 1 minute")));
            }
            case "Bipolar" -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83D\uDEB6", "Steady-State Walking",
                    "A consistent, moderate-pace walk that stabilizes mood without triggering manic energy spikes.",
                    "30 min", "Easy",
                    "Intense exercise can trigger hypomania in bipolar individuals. Steady-state walking provides mood benefits without the overstimulation risk, and it regulates circadian rhythm.",
                    List.of("Walk at a comfortable, consistent pace — not too fast, not too slow",
                            "Avoid competitive or high-intensity urges — steady is the goal",
                            "Walk the same route at the same time daily for routine anchoring",
                            "Focus on your footsteps and breathing, not racing thoughts",
                            "If you feel the urge to speed up or run, consciously slow down",
                            "30 minutes is the target — not more, not less. Consistency matters.")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83E\uDDD8\u200D\u2640\uFE0F", "Stabilizing Yoga",
                    "Gentle, grounding yoga that emphasizes balance and body awareness without overstimulation.",
                    "20 min", "Easy",
                    "Yoga improves interoception (body awareness), which helps you detect mood shifts earlier. Gentle practice avoids the adrenaline spikes that can trigger episodes.",
                    List.of("Start in Mountain Pose — feel both feet firmly on the ground",
                            "Tree Pose — hold each side 30 seconds, focus on balance",
                            "Warrior II — hold each side 30 seconds, feel grounded strength",
                            "Seated Forward Fold — hold 1 minute, surrender to gravity",
                            "Reclined Spinal Twist — hold each side 1 minute",
                            "Savasana — lie flat for 3 minutes, observe your energy level without judging it")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83C\uDFCA", "Swimming or Water Walking",
                    "Water-based exercise that provides resistance without the mood-destabilizing effects of high-intensity workouts.",
                    "25 min", "Easy",
                    "Water provides natural resistance while its temperature and pressure have a calming sensory effect. Swimming is uniquely mood-stabilizing because it demands rhythmic breathing.",
                    List.of("Swim laps at an easy pace or walk in the shallow end",
                            "Focus on rhythmic breathing — inhale, stroke, exhale, stroke",
                            "If no pool available, a warm bath with stretching is an alternative",
                            "Keep intensity moderate — you should be able to hold a conversation",
                            "25 minutes maximum — resist the urge to push harder during high-energy days",
                            "The water's sensory input naturally calms the nervous system")));
            }
            case "Personality Disorder" -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83E\uDD4A", "Controlled Intensity Release",
                    "Channel intense emotions into structured physical movements that provide release without loss of control.",
                    "15 min", "Medium",
                    "People with emotional dysregulation benefit from physical outlets that are intense enough to match their emotions but structured enough to maintain self-control.",
                    List.of("Start with 2 minutes of jumping jacks to match your energy level",
                            "Move to shadow boxing: controlled punches with sharp exhales",
                            "Do 10 burpees — intensity channels emotion into physical effort",
                            "30-second mountain climbers — fast but controlled",
                            "End with 2 minutes of slow walking in circles to cool down",
                            "Notice: you expressed intensity WITHOUT losing control. That's the skill.")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83E\uDDD8", "DBT-Informed Yoga",
                    "Yoga practice that incorporates Dialectical Behavior Therapy principles: mindfulness, distress tolerance, and balance.",
                    "20 min", "Easy",
                    "This combines physical practice with DBT's core skill of holding opposites — strength and softness, effort and ease — which builds the emotional flexibility needed for interpersonal stability.",
                    List.of("Start in Child's Pose — say: 'I accept where I am right now'",
                            "Cat-Cow for 1 minute — notice the shift between opposites",
                            "Warrior I: 'I am strong' — hold 30 seconds each side",
                            "Forward Fold: 'I can also surrender' — hold 1 minute",
                            "Tree Pose: balance = holding two things at once — 30 seconds each side",
                            "Savasana for 3 minutes — practice tolerating stillness without reacting")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83D\uDEB6\u200D\u2640\uFE0F", "Mindful Walking with Body Check",
                    "A walking practice that builds the mind-body connection needed for recognizing emotional states early.",
                    "20 min", "Easy",
                    "Emotional dysregulation often involves disconnection from physical sensations. This walk rebuilds that connection, helping you detect emotional shifts before they escalate.",
                    List.of("Walk at a natural pace — no destination needed",
                            "Every 3 minutes, do a rapid body check: 'What am I feeling physically?'",
                            "Name the sensations: tight chest, relaxed shoulders, clenched jaw, warm hands",
                            "Then ask: 'What emotion does this match?'",
                            "Don't judge — just observe and name. This builds interoceptive awareness.",
                            "After 20 minutes, note: your ability to detect body signals is improving")));
            }
            case "Suicidal" -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83D\uDEB6", "5-Minute Doorstep Walk",
                    "The absolute smallest step: open the door, walk for 2.5 minutes, turn around, come back.",
                    "5 min", "Easy",
                    "When you're in crisis, even standing up feels impossible. A 5-minute walk changes your neurochemistry just enough to create a small shift. That shift can save your life.",
                    List.of("Stand up. That's step one — you've already done something.",
                            "Put on shoes. You don't need to look good or be ready.",
                            "Open the door and step outside. Feel the air on your skin.",
                            "Walk in any direction for 2.5 minutes — count steps if it helps",
                            "Turn around and walk back",
                            "You moved your body when everything said not to. That matters.",
                            "If going outside feels too hard, walk around inside your home for 5 minutes")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83E\uDDF4", "Squeeze and Release",
                    "Use physical tension and release to discharge overwhelming emotional energy from your body.",
                    "5 min", "Easy",
                    "Extreme emotional pain gets stored as physical tension. The squeeze-and-release technique gives your body permission to let go, which creates a small but meaningful emotional shift.",
                    List.of("Sit or lie wherever you are — no need to move",
                            "Make tight fists with both hands — squeeze as hard as you can for 5 seconds",
                            "Release and feel the blood flow back into your fingers",
                            "Curl your toes tight for 5 seconds — then release",
                            "Shrug shoulders to your ears for 5 seconds — then drop them",
                            "Tighten your whole body for 5 seconds — then release everything at once",
                            "Repeat 3 times. Notice the wave of warmth and release each time.")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83D\uDCA7", "Cold Shower or Splash",
                    "Use cold water exposure to activate your body's survival response, which overrides emotional pain signals.",
                    "3 min", "Easy",
                    "Cold water triggers the dive reflex, rapidly lowering heart rate and releasing norepinephrine. It interrupts suicidal thinking by forcing your brain into survival mode — a neurological reset.",
                    List.of("Go to a sink or shower", "Splash cold water on your face — cheeks and forehead especially",
                            "If possible, run cold water over your wrists for 30 seconds",
                            "For a stronger effect, hold ice cubes in your hands",
                            "Or take a 30-second cold shower — just long enough for the shock",
                            "The jolt pulls your brain into the present moment and away from dark thoughts",
                            "Follow up by calling someone or doing the next coping step")));
            }
            default -> {
                s.items.add(new RecItem(moodKey(mood, "ex_1"), "\uD83C\uDFC3\u200D\u2642\uFE0F", "30-Minute Jog",
                    "Maintain your fitness and mood stability with a moderate-intensity run.",
                    "30 min", "Medium",
                    "Regular aerobic exercise is one of the strongest protective factors against mental health decline.",
                    List.of("Warm up with 3 minutes of walking", "Begin jogging at a conversational pace",
                            "Focus on steady breathing", "If you need to walk, walk — then resume jogging",
                            "At 27 minutes, slow to a walk for your cooldown",
                            "Stretch your calves, quads, and hamstrings for 3 minutes")));
                s.items.add(new RecItem(moodKey(mood, "ex_2"), "\uD83D\uDCAA", "Bodyweight Strength Circuit",
                    "A full-body circuit that builds physical and mental resilience.",
                    "20 min", "Medium",
                    "Strength training increases self-efficacy, improves sleep, and builds physical resilience.",
                    List.of("10 Push-ups (modify on knees if needed)", "15 Squats with arms extended forward",
                            "10 Lunges per leg", "30-second Plank hold",
                            "10 Tricep dips on a chair edge", "Rest 60 seconds, then repeat 2 more rounds")));
                s.items.add(new RecItem(moodKey(mood, "ex_3"), "\uD83E\uDDD8", "Flexibility & Recovery",
                    "A full-body stretching routine that promotes relaxation.",
                    "15 min", "Easy",
                    "Flexibility work activates the parasympathetic nervous system.",
                    List.of("Neck rolls: 5 slow circles each direction",
                            "Shoulder stretch: pull each arm across your chest, hold 20s",
                            "Standing quad stretch: hold each side 20s",
                            "Seated hamstring stretch: reach for toes, hold 30s",
                            "Hip opener: pigeon pose each side, hold 30s",
                            "Final child's pose: rest here for 1 minute")));
            }
        }
        return s;
    }

    private RecSection buildCBT(String mood, String badge, String color) {
        RecSection s = new RecSection("cbt", "\uD83E\uDDE0", "CBT Techniques", badge, color);
        switch (mood) {
            case "Anxiety" -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83D\uDCDD", "Thought Record",
                    "The gold-standard CBT tool for identifying and challenging anxious thoughts with evidence.",
                    "15 min", "Medium",
                    "Anxious thoughts feel 100% true in the moment. Writing them down and examining evidence creates cognitive distance, reducing their emotional power by up to 50%.",
                    List.of("Write the situation: What triggered the anxiety?",
                            "Write the automatic thought: What went through your mind?",
                            "Rate the belief strength 0-100%: How much do you believe it?",
                            "Evidence FOR the thought: What supports this thought being true?",
                            "Evidence AGAINST: What contradicts it? What would a friend say?",
                            "Write a balanced alternative thought",
                            "Re-rate the original belief — notice how the number dropped")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\u2753", "The Worry Decision Tree",
                    "A structured decision-making tool that sorts worries into actionable and non-actionable categories.",
                    "10 min", "Easy",
                    "70% of anxious worries are about things we cannot control. This technique saves mental energy by identifying which worries deserve action and which need acceptance.",
                    List.of("Write down the worry",
                            "Ask: 'Can I do something about this RIGHT NOW?'",
                            "If YES: Write the smallest possible action step. Do it.",
                            "If NO: Ask: 'Can I do something about this LATER?'",
                            "If YES later: Schedule it. Write the date and time. Let it go until then.",
                            "If NO at all: Practice saying: 'This is not in my control. I choose to let it go.'",
                            "Move to the next worry and repeat")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\u23F0", "Worry Time Containment",
                    "Designate a specific 15-minute window for worrying — and defer all anxiety to that window.",
                    "15 min", "Medium",
                    "Paradoxically, scheduling worry time reduces total worry by 35%. Your brain relaxes knowing worries will be addressed, just not right now.",
                    List.of("Choose a daily 15-minute window (e.g., 6:00-6:15 PM)",
                            "When an anxious thought appears outside this window, jot it on a 'worry list'",
                            "Tell yourself: 'I'll think about this during worry time'",
                            "When worry time arrives, review the list",
                            "For each item: worry about it fully for 1-2 minutes",
                            "You'll notice many worries have already resolved or feel less intense",
                            "After 15 minutes, close the list and move on")));
            }
            case "Depression" -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83D\uDCC8", "Behavioral Activation Plan",
                    "The #1 CBT intervention for depression: scheduling small pleasurable and mastery activities to break the inactivity cycle.",
                    "10 min", "Medium",
                    "Depression tells you nothing will feel good, so you stop trying. Behavioral activation proves this wrong by creating small wins that rebuild motivation gradually.",
                    List.of("Draw a simple schedule for tomorrow with 3 time blocks",
                            "Block 1 — PLEASURE: One small thing you used to enjoy (coffee, a show, music)",
                            "Block 2 — MASTERY: One small productive thing (washing dishes, one email, making bed)",
                            "Block 3 — SOCIAL: One small interaction (text someone, say hi to a neighbor)",
                            "Rate predicted enjoyment 0-10 BEFORE doing each",
                            "Do each activity, then rate actual enjoyment 0-10 AFTER",
                            "Compare: the actual rating is almost always higher than predicted")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\uD83D\uDD0D", "Cognitive Distortion Detector",
                    "Learn to spot the 3 most common thinking traps depression uses against you.",
                    "10 min", "Easy",
                    "Depression distorts thinking in predictable patterns. Once you can name the distortion, it loses 40-60% of its power over your mood.",
                    List.of("Write your most negative thought from today",
                            "Check: ALL-OR-NOTHING? (Using words like 'always', 'never', 'completely')",
                            "Check: MIND READING? (Assuming what others think without evidence)",
                            "Check: CATASTROPHIZING? (Jumping to the worst possible outcome)",
                            "Name the distortion(s) you found",
                            "Rewrite the thought without the distortion — make it factual",
                            "Example: 'I always fail' becomes 'I struggled with this one task today'")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\uD83C\uDFC6", "Achievement Tracking",
                    "Combat depression's lie that 'you did nothing today' by recording even the smallest accomplishments.",
                    "5 min", "Easy",
                    "Depression filters out achievements and magnifies failures. A written record creates objective evidence against the 'I'm worthless' narrative.",
                    List.of("At the end of the day, write down 3 things you did",
                            "They can be ANYTHING: got out of bed, ate food, read a message",
                            "For each one, rate the difficulty 1-5 given how you felt",
                            "If difficulty was 3+, that's a genuine achievement given your state",
                            "Read the list back to yourself",
                            "Keep a running list — review it when depression says 'you never do anything'")));
            }
            case "Stress" -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83D\uDCC1", "Problem Decomposition",
                    "Break an overwhelming stressor into small, concrete, actionable sub-problems.",
                    "15 min", "Medium",
                    "Stress is amplified by overwhelm. Decomposing a large problem into parts activates the prefrontal cortex (problem-solving) and deactivates the amygdala (panic).",
                    List.of("Write the stressor in one sentence",
                            "Break it into 3-5 smaller sub-problems",
                            "For each sub-problem, write the very first physical action needed",
                            "Put them in order: which is easiest to tackle first?",
                            "Do the first action TODAY — even if it takes 5 minutes",
                            "Cross it off and schedule the next one",
                            "Notice: the big problem is now a to-do list, not a threat")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\u26A0\uFE0F", "Perspective Scale",
                    "Rate your current stress against a calibrated scale to gain instant perspective on its true severity.",
                    "5 min", "Easy",
                    "Stress hijacks perspective, making everything feel catastrophic. A calibrated scale activates rational thinking and reduces emotional reactivity by 30-40%.",
                    List.of("Rate your current stress 1-10",
                            "Now recall the most stressful event of your entire life — that's your 10",
                            "Recall a mildly annoying situation — that's your 2",
                            "Re-rate today's stress on this calibrated scale",
                            "Ask: 'Will this matter in 1 week? 1 month? 1 year?'",
                            "Write the re-rated number and the time horizon",
                            "Notice the emotional shift when you gain perspective")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\uD83E\uDDED", "Values Alignment Check",
                    "Check whether your current stressors align with what actually matters most to you.",
                    "10 min", "Medium",
                    "50% of stress comes from overcommitting to things that don't align with your core values. Identifying misalignment gives you permission to let go.",
                    List.of("List your top 3 values (family, health, growth, creativity, etc.)",
                            "List your top 3 current stressors",
                            "For each stressor, ask: 'Does this serve any of my top values?'",
                            "If YES: It's meaningful stress — worth the effort. Plan to address it.",
                            "If NO: Ask: 'Can I reduce, delegate, or drop this?'",
                            "Write one commitment you'll reduce this week",
                            "Redirect that energy toward a value-aligned activity")));
            }
            case "Normal" -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83D\uDCDD", "Daily Thought Journal",
                    "Build self-awareness by capturing and examining your automatic thoughts throughout the day.",
                    "10 min", "Easy",
                    "Regular thought journaling builds metacognition — the ability to observe your own thinking — which is the foundation of emotional intelligence and resilience.",
                    List.of("Carry a small notebook or use your phone's notes app",
                            "3 times today, pause and write: 'Right now I'm thinking...'",
                            "Don't judge the thoughts — just capture them accurately",
                            "At the end of the day, review all captured thoughts",
                            "Notice patterns: are they mostly positive, neutral, or negative?",
                            "This awareness alone begins shifting your thinking patterns")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\uD83C\uDF1F", "Strengths Identification",
                    "Identify and leverage your personal character strengths to build confidence.",
                    "10 min", "Easy",
                    "Knowing and using your signature strengths increases happiness and reduces depression risk for up to 6 months.",
                    List.of("Write down 3 things you're good at (skills, qualities, talents)",
                            "For each, write a specific example of when you used it recently",
                            "Choose one strength to intentionally use tomorrow",
                            "Plan exactly how and when you'll use it",
                            "After using it, note how it felt")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\uD83D\uDCCB", "Weekly SMART Goal",
                    "Set one specific, measurable, achievable, relevant, time-bound goal for this week.",
                    "10 min", "Easy",
                    "Goal-setting during stable periods builds momentum and creates structure that protects you when mood dips.",
                    List.of("Write a goal for this week using SMART format",
                            "Specific: What exactly will you do?",
                            "Measurable: How will you know it's done?",
                            "Achievable: Is this realistic given your week?",
                            "Relevant: Does this matter to you personally?",
                            "Time-bound: By what day/time will it be complete?",
                            "Write it down and check in with yourself mid-week")));
            }
            case "Bipolar" -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83D\uDCC9", "Mood Monitoring Log",
                    "Track your mood at 3 set times daily to detect episode onset early — the most powerful tool in bipolar management.",
                    "5 min", "Easy",
                    "Bipolar episodes build gradually. Catching a mood shift at 3/10 intensity (instead of 8/10) gives you time to activate coping strategies before it becomes unmanageable.",
                    List.of("Set 3 daily alarms: morning, afternoon, evening",
                            "Rate your mood -5 (very low) to +5 (very high). 0 is baseline.",
                            "Note sleep hours last night and energy level 1-10",
                            "Note any warning signs: racing thoughts, irritability, withdrawal, grandiosity",
                            "If your score is trending away from 0 for 2+ days, alert your support system",
                            "Review weekly — look for patterns around triggers, sleep, and events")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\u26A0\uFE0F", "Mania Warning Signs Checklist",
                    "Review a personalized list of your early manic warning signs daily to catch episodes before they escalate.",
                    "5 min", "Easy",
                    "Most people can identify their early warning signs in hindsight. This checklist moves that awareness to real-time, giving you a 24-48 hour head start on intervention.",
                    List.of("Review each sign: Am I sleeping less but feeling energized?",
                            "Am I talking faster than usual or feeling unusually witty?",
                            "Am I spending money impulsively or making big plans?",
                            "Am I more irritable or easily frustrated than baseline?",
                            "Am I feeling invincible or exceptionally confident?",
                            "If 2+ signs are present: activate your stability plan — reduce stimulation, ensure 8 hours sleep, contact your support person")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\uD83D\uDD04", "Opposite Action Technique",
                    "When an extreme mood urges action, deliberately do the opposite to prevent episode escalation.",
                    "10 min", "Medium",
                    "Opposite action breaks the behavioral feedback loop that maintains mood episodes. Acting opposite to the urge sends a corrective signal to your brain's emotion centers.",
                    List.of("Identify your current mood urge — what does it want you to DO?",
                            "If manic urge (spend, create, socialize excessively): do the opposite — pause, simplify, be still",
                            "If depressive urge (isolate, sleep, withdraw): do the opposite — reach out, move, engage",
                            "The action doesn't have to be big — just opposite to the urge's direction",
                            "Example: Urge says 'text everyone at 2 AM' → Put phone in another room",
                            "Example: Urge says 'cancel all plans' → Text one friend 'thinking of you'",
                            "Notice: acting opposite doesn't feel natural. That's how you know it's working.")));
            }
            case "Personality Disorder" -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83C\uDF21\uFE0F", "Emotion Thermometer",
                    "Rate your emotional intensity throughout the day to build awareness of escalation patterns.",
                    "5 min", "Easy",
                    "Emotional dysregulation often means emotions go from 0 to 100 instantly. The thermometer builds the gap between trigger and reaction by making intensity visible and trackable.",
                    List.of("Draw a thermometer scale 0-100 on a card you carry with you",
                            "0 = completely calm, 50 = noticeably upset, 100 = crisis level",
                            "Check in 4 times today: rate your current level",
                            "Note what happened just before each check-in",
                            "Identify your 'action zone' — at what number do you typically act impulsively?",
                            "Goal: catch yourself BELOW your action zone and use a coping skill",
                            "Over time, the gap between trigger and reaction will widen")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\uD83D\uDC65", "Interpersonal Effectiveness Script",
                    "Prepare and practice a script for a difficult conversation using the DEAR MAN technique from DBT.",
                    "15 min", "Medium",
                    "Interpersonal conflicts are a primary trigger for emotional crises. DEAR MAN provides a structured way to assert needs without damaging relationships.",
                    List.of("Choose a current interpersonal situation that needs addressing",
                            "D — Describe: State the facts only. 'When X happens...'",
                            "E — Express: Share your feeling. 'I feel...'",
                            "A — Assert: State your need. 'I would like...'",
                            "R — Reinforce: Explain the benefit. 'This would help because...'",
                            "M — Mindful: Stay on topic, don't get derailed",
                            "A — Appear confident: Even if you don't feel it, stand tall",
                            "N — Negotiate: Be willing to give to get")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\uD83E\uDDE9", "Black-and-White Thinking Challenge",
                    "Identify and challenge all-or-nothing thinking patterns that fuel emotional extremes.",
                    "10 min", "Medium",
                    "Splitting (seeing things as all good or all bad) is a core pattern. Practicing gray-area thinking reduces emotional volatility and improves relationship stability.",
                    List.of("Write down a recent situation where you felt strongly negative about someone or something",
                            "Identify the black-and-white thought: 'They ALWAYS...' or 'I NEVER...'",
                            "Challenge: Write 3 pieces of evidence that this isn't 100% true",
                            "Rewrite the thought in gray: 'Sometimes they... and sometimes they...'",
                            "Rate how the gray version feels compared to the extreme version",
                            "Practice this daily — the brain can learn to default to nuance over time")));
            }
            case "Suicidal" -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83D\uDCDE", "Safety Plan Review",
                    "Review or create your personal safety plan — the most effective tool for surviving a crisis.",
                    "15 min", "Easy",
                    "A written safety plan reduces suicide attempts by 43%. Having it ready BEFORE a crisis means you don't have to think clearly when your brain can't — you just follow the steps.",
                    List.of("Step 1: Write your personal warning signs — what thoughts or feelings signal danger?",
                            "Step 2: List 3 internal coping strategies you can do alone (breathing, cold water, walking)",
                            "Step 3: List 3 people you can contact for distraction (friends, family)",
                            "Step 4: List professional contacts: therapist, doctor, crisis line (988)",
                            "Step 5: List emergency contacts: 911, nearest ER, trusted person who has a key",
                            "Step 6: Remove or restrict access to means — give items to a trusted person",
                            "Keep this plan visible — on your phone, by your bed, in your wallet")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\u23F3", "Reasons for Living List",
                    "Write down your personal reasons for staying alive — connections, hopes, responsibilities, curiosities.",
                    "10 min", "Easy",
                    "During crisis, the brain narrows focus to pain and hopelessness. A pre-written list of reasons provides counter-evidence your brain can't generate in the moment.",
                    List.of("Write the heading: 'Reasons I Want To Stay'",
                            "List people who would be affected: family, friends, pets",
                            "List things you're curious about: a show, a season, a future event",
                            "List responsibilities that matter: a pet to feed, a project to finish",
                            "List sensory pleasures: your favorite food, a warm shower, sunshine",
                            "Add anything else, no matter how small — every reason counts",
                            "Read this list when the pain feels unbearable. These are YOUR reasons.")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\uD83D\uDD52", "Hope Box / Coping Kit",
                    "Assemble a physical box of items that ground you, comfort you, and remind you of reasons to live.",
                    "20 min", "Easy",
                    "A physical Hope Box engages multiple senses, which is more effective than abstract thinking during crisis. Touching real objects anchors you in the present moment.",
                    List.of("Find a small box, bag, or container",
                            "Add photos of people and pets you love",
                            "Add a letter to yourself written on a good day — what would you say?",
                            "Add sensory items: a smooth stone, scented hand cream, soft fabric",
                            "Add your Reasons for Living list",
                            "Add crisis numbers written on a card: 988, Crisis Text Line (text HOME to 741741)",
                            "Keep the box somewhere accessible — reach for it BEFORE reaching crisis level")));
            }
            default -> {
                s.items.add(new RecItem(moodKey(mood, "cbt_1"), "\uD83D\uDCDD", "Daily Thought Journal",
                    "Build self-awareness by capturing and examining your automatic thoughts.",
                    "10 min", "Easy",
                    "Regular thought journaling builds metacognition — the foundation of emotional intelligence.",
                    List.of("Carry a small notebook or use your phone's notes app",
                            "3 times today, pause and write: 'Right now I'm thinking...'",
                            "Don't judge the thoughts — just capture them accurately",
                            "At the end of the day, review all captured thoughts",
                            "Notice patterns: are they mostly positive, neutral, or negative?",
                            "This awareness alone begins shifting your thinking patterns")));
                s.items.add(new RecItem(moodKey(mood, "cbt_2"), "\uD83C\uDF1F", "Strengths Identification",
                    "Identify and leverage your personal character strengths.",
                    "10 min", "Easy",
                    "Knowing and using your signature strengths increases happiness.",
                    List.of("Write down 3 things you're good at",
                            "For each, write a specific example of when you used it recently",
                            "Choose one strength to intentionally use tomorrow",
                            "Plan exactly how and when you'll use it",
                            "After using it, note how it felt")));
                s.items.add(new RecItem(moodKey(mood, "cbt_3"), "\uD83D\uDCCB", "Weekly SMART Goal",
                    "Set one specific, measurable, achievable, relevant, time-bound goal.",
                    "10 min", "Easy",
                    "Goal-setting builds momentum and creates structure that protects you.",
                    List.of("Write a goal for this week using SMART format",
                            "Specific: What exactly will you do?",
                            "Measurable: How will you know it's done?",
                            "Achievable: Is this realistic given your week?",
                            "Relevant: Does this matter to you personally?",
                            "Time-bound: By what day/time will it be complete?",
                            "Write it down and check in with yourself mid-week")));
            }
        }
        return s;
    }

    private RecSection buildSleepHygiene(String mood, String badge, String color) {
        RecSection s = new RecSection("sleep", "\uD83D\uDE34", "Sleep Hygiene", badge, color);
        switch (mood) {
            case "Anxiety" -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\uD83D\uDCF1", "Screen Curfew Protocol",
                    "Create a firm 45-minute buffer between screens and sleep to calm an anxious mind.",
                    "45 min", "Medium",
                    "Blue light suppresses melatonin, but for anxious people the content is worse — news, social media, and messages keep the threat-detection system active.",
                    List.of("Set a daily alarm for 45 minutes before your target bedtime",
                            "When it rings, put ALL screens in another room",
                            "Switch to: a physical book, gentle stretching, or a warm drink",
                            "If you must use your phone, enable Night Shift + grayscale mode",
                            "Keep a notepad by your bed for any urgent thoughts that arise",
                            "After 1 week, notice the difference in how quickly you fall asleep")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\uD83C\uDF1C", "Pre-Sleep Body Scan",
                    "A targeted relaxation technique designed for the moment you get into bed.",
                    "10 min", "Easy",
                    "Anxiety at bedtime often manifests as physical tension you don't notice. A body scan in bed gives your mind a task (scanning) instead of its default (worrying).",
                    List.of("Lie on your back with arms at your sides, palms up",
                            "Starting at your toes, clench them for 3 seconds, then release",
                            "Move to calves, thighs, buttocks — clench and release each",
                            "Clench your fists, then release. Let arms go heavy.",
                            "Shrug shoulders to ears, hold 3 seconds, release",
                            "Scrunch your entire face, hold, release",
                            "Take 3 breaths and notice how heavy and relaxed your body feels")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\u2615", "Caffeine Awareness",
                    "Track and reduce caffeine to prevent it from amplifying nighttime anxiety.",
                    "All day", "Medium",
                    "Caffeine has a half-life of 5-6 hours. A 2 PM coffee means 50% of the caffeine is still in your system at 8 PM, directly fueling anxious thoughts at bedtime.",
                    List.of("Today, write down every caffeinated drink and the time",
                            "Set a hard cutoff: no caffeine after 1 PM (or noon if sensitive)",
                            "Replace afternoon coffee with: chamomile tea, decaf, or warm water with lemon",
                            "Green tea is a good compromise — it has L-theanine which calms anxiety",
                            "After 3 days of the cutoff, rate your bedtime anxiety 1-10",
                            "Compare with your usual — most people see a 2-3 point drop")));
            }
            case "Depression" -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\u23F0", "Anchor Wake Time",
                    "The single most impactful sleep change for depression: waking at the same time every day, no exceptions.",
                    "Ongoing", "Medium",
                    "Depression disrupts the circadian rhythm. A fixed wake time is the strongest signal to reset your internal clock, which regulates mood, energy, and motivation.",
                    List.of("Choose a wake time you can maintain 7 days a week",
                            "Set an alarm — place it across the room so you must stand up",
                            "When it rings, stand up immediately — don't negotiate",
                            "Open curtains or turn on bright lights within 1 minute",
                            "Do NOT go back to sleep or nap before noon",
                            "Yes, weekends too — consistency is what resets the clock",
                            "After 1 week, your sleep onset will naturally regulate")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\u2600\uFE0F", "Morning Light Therapy",
                    "Use bright light within 30 minutes of waking to reset your circadian rhythm and boost energy.",
                    "15 min", "Easy",
                    "Morning light exposure suppresses melatonin and triggers cortisol (the alerting hormone). For depression, this natural wake-up signal combats oversleeping and fatigue.",
                    List.of("Within 30 minutes of waking, get to natural light",
                            "Step outside if possible — even overcast sky is 10,000+ lux",
                            "If staying indoors, sit by the brightest window",
                            "Face the direction of the light (don't stare at the sun)",
                            "Stay for 15 minutes — have your morning drink during this time",
                            "On very dark winter days, consider a 10,000 lux light therapy lamp")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\uD83C\uDF19", "Wind-Down Ritual",
                    "Create a consistent 20-minute bedtime routine that signals your brain it's time to sleep.",
                    "20 min", "Easy",
                    "A predictable pre-sleep routine creates a conditioned response — your brain learns that these activities mean sleep is coming, making it easier to fall asleep.",
                    List.of("Set an alarm for 20 minutes before your target bedtime",
                            "Step 1: Warm drink (herbal tea, warm milk — no caffeine)",
                            "Step 2: Light stretching — 3 minutes of gentle neck and shoulder rolls",
                            "Step 3: Write one thing you're grateful for from today",
                            "Step 4: Get into bed and read something light for 5 minutes",
                            "Step 5: Lights out",
                            "Do this EXACT sequence every night — the routine IS the sleep aid")));
            }
            case "Stress" -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\uD83D\uDCD3", "Brain Dump Before Bed",
                    "Get every thought, worry, and to-do out of your head and onto paper before you try to sleep.",
                    "10 min", "Easy",
                    "Unfinished tasks stay active in working memory (the Zeigarnik Effect). Writing them down signals your brain that they're 'captured' and safe to release.",
                    List.of("Keep a notepad on your nightstand",
                            "15 minutes before bed, write EVERYTHING on your mind",
                            "Don't organize — just dump: worries, tasks, random thoughts, ideas",
                            "For any task, write the next action step beside it",
                            "Close the notebook and tell yourself: 'This is captured. I can deal with it tomorrow.'",
                            "If a thought returns in bed, remind yourself: 'It's in the notebook'")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\uD83C\uDFB5", "Sleep Soundscape",
                    "Use consistent ambient sounds to mask stress-triggering noises and signal sleep onset.",
                    "Ongoing", "Easy",
                    "Consistent ambient sound reduces the startle response that stress-elevated nervous systems experience from random nighttime noises.",
                    List.of("Choose ONE sound: rain, ocean waves, white noise, or fan",
                            "Use the same sound every night — consistency creates association",
                            "Set volume low enough to be background, not foreground",
                            "Start playing it during your wind-down routine, not just at lights-out",
                            "Use a timer to auto-stop after 60 minutes (you'll be asleep by then)",
                            "Avoid music with lyrics or changing tempos — they engage attention")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\uD83D\uDCA8", "4-7-8 Sleep Breathing",
                    "The most effective breathing pattern for falling asleep when stress keeps you awake.",
                    "5 min", "Easy",
                    "The extended exhale (8 seconds) activates the vagus nerve more powerfully than any other breathing pattern, inducing the drowsiness response.",
                    List.of("Lie in your sleeping position with lights off",
                            "Place tongue tip behind upper front teeth",
                            "Exhale completely with a 'whoosh' sound",
                            "Inhale through nose: count 1-2-3-4",
                            "Hold: count 1-2-3-4-5-6-7",
                            "Exhale through mouth: count 1-2-3-4-5-6-7-8",
                            "Repeat 4-8 cycles — most people fall asleep before finishing")));
            }
            case "Normal" -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\u23F0", "Consistent Sleep Schedule",
                    "Go to bed and wake up at the same time daily to optimize your circadian rhythm.",
                    "Ongoing", "Easy",
                    "A regular sleep schedule is the single strongest predictor of sleep quality. Even 30 minutes of variation can reduce deep sleep by 20%.",
                    List.of("Choose a bedtime and wake time that gives you 7-8 hours",
                            "Set alarms for BOTH bedtime and wake time",
                            "Maintain this schedule on weekends (max 30 min variation)",
                            "If you can't fall asleep within 20 minutes, get up and do something calm",
                            "Return to bed only when sleepy",
                            "After 2 weeks, your body will naturally feel sleepy and alert at the right times")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\uD83C\uDF1C", "Evening Wind-Down",
                    "Create a calming pre-sleep routine that bridges the gap between your active day and restful night.",
                    "20 min", "Easy",
                    "A predictable evening routine trains your brain to start producing melatonin on cue.",
                    List.of("Set screens to night mode 1 hour before bed",
                            "Dim lights in your home 30 minutes before bed",
                            "Make herbal tea or warm water",
                            "Do 5 minutes of gentle stretching",
                            "Read something light for 10 minutes",
                            "Lights out at your scheduled bedtime")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\uD83C\uDF21\uFE0F", "Sleep Environment Optimization",
                    "Set up your bedroom for optimal sleep: dark, cool, quiet.",
                    "Ongoing", "Easy",
                    "Environmental factors account for 30% of sleep quality.",
                    List.of("Temperature: Set bedroom to 65-68°F (18-20°C)",
                            "Light: Use blackout curtains or a sleep mask",
                            "Sound: Use earplugs or a white noise machine if needed",
                            "Remove all visible clocks (clock-watching increases sleep anxiety)",
                            "Keep phone face-down or in another room",
                            "Reserve bed for sleep only — no working or scrolling in bed")));
            }
            case "Bipolar" -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\u23F0", "Non-Negotiable Sleep Schedule",
                    "The single most critical intervention for bipolar stability: rigid, consistent sleep and wake times 7 days a week.",
                    "Ongoing", "Medium",
                    "Sleep disruption is the #1 trigger for bipolar episodes — both manic and depressive. A rigid schedule is more important for bipolar than for any other condition.",
                    List.of("Choose a bedtime and wake time — write them down and post visibly",
                            "Set TWO alarms: one for bedtime prep (30 min before) and one for waking",
                            "NO exceptions for weekends, social events, or 'I'm not tired' feelings",
                            "If you can't sleep within 20 minutes, read in dim light — don't use screens",
                            "Even if you slept poorly, get up at your wake time — no sleeping in",
                            "Track adherence: mark each day you hit both times. Aim for 7/7.",
                            "This schedule is your anchor — treat it like medication")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\uD83D\uDEAB", "Stimulation Curfew",
                    "Eliminate all activating stimuli 90 minutes before bed to prevent hypomania-triggered insomnia.",
                    "90 min", "Medium",
                    "Bipolar brains are more sensitive to stimulation. Evening activation can trigger racing thoughts that prevent sleep and escalate into full manic episodes.",
                    List.of("90 minutes before bed: no screens, exciting content, or intense conversations",
                            "No social media — it's stimulating even when it feels passive",
                            "No work emails or planning — they activate the problem-solving brain",
                            "No exciting music, action movies, or heated discussions",
                            "Instead: dim lights, gentle music, light reading, warm bath",
                            "If your brain starts racing, write thoughts in a 'parking lot' notebook",
                            "Tell yourself: 'These are tomorrow-brain's problems. Tonight I rest.'")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\uD83D\uDCA1", "Light Exposure Protocol",
                    "Use strategic light exposure to regulate your circadian rhythm — the biological clock most disrupted in bipolar disorder.",
                    "Ongoing", "Easy",
                    "Light is the strongest signal for your circadian clock. Strategic exposure (bright morning, dim evening) stabilizes the sleep-wake cycle that bipolar disorder disrupts.",
                    List.of("Morning: Get 15-30 minutes of bright light within 1 hour of waking",
                            "Go outside or use a 10,000-lux therapy lamp",
                            "Afternoon: Take a 10-minute outdoor break for light exposure",
                            "Evening: Begin dimming lights 2 hours before bed",
                            "Use warm-toned bulbs only after sunset — no cool/blue-toned lighting",
                            "Wear blue-light blocking glasses if you must use screens after dark",
                            "Consistency is key — same light pattern every day, including weekends")));
            }
            case "Personality Disorder" -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\uD83D\uDECF\uFE0F", "Safe Sleep Space Creation",
                    "Transform your bedroom into a sensory-safe environment that reduces nighttime emotional activation.",
                    "Ongoing", "Easy",
                    "People with emotional dysregulation often experience heightened sensory sensitivity at night. A carefully controlled sleep environment reduces triggers and promotes security.",
                    List.of("Remove clutter — visual chaos can trigger emotional distress",
                            "Choose bedding that feels safe and comforting (texture matters)",
                            "Keep room temperature consistent and slightly cool (65-68°F)",
                            "Use a small nightlight if complete darkness feels unsafe",
                            "Keep a grounding object within arm's reach: smooth stone, soft blanket",
                            "Avoid sleeping near your phone — notifications trigger emotional reactions",
                            "Your bedroom should feel like a sanctuary, not a battleground")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\uD83D\uDCDD", "Evening Processing Journal",
                    "Process the day's interpersonal events BEFORE bed so they don't replay in your mind all night.",
                    "10 min", "Easy",
                    "Unprocessed interpersonal events are the #1 cause of sleep disruption in personality disorders. Writing them down closes the mental loop and signals 'this is handled.'",
                    List.of("30 minutes before bed, write for exactly 10 minutes — set a timer",
                            "Write about any interpersonal events that are stuck in your mind",
                            "For each event, write: What happened? How did I feel? What do I need?",
                            "If you're ruminating about someone, write what you'd say to them (don't send it)",
                            "End with: 'I've captured this. My sleeping brain doesn't need to process it.'",
                            "Close the notebook and physically put it away — symbolic closure")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\uD83C\uDFB6", "Sensory Sleep Routine",
                    "Use a multi-sensory pre-sleep routine that engages your body and calms your nervous system.",
                    "15 min", "Easy",
                    "Engaging multiple senses creates a stronger calming signal than any single technique. The routine becomes a conditioned cue for safety and sleep.",
                    List.of("Touch: Warm shower or apply lotion with slow, deliberate strokes",
                            "Smell: Use a consistent scent — lavender pillow spray or essential oil",
                            "Sound: Play the same calming track every night (condition association)",
                            "Taste: Sip warm chamomile tea slowly, focusing on the warmth",
                            "Sight: Dim all lights to warm, low levels",
                            "Do these in the SAME order every night — the predictability itself is calming",
                            "After 2 weeks, starting the routine will automatically signal sleepiness")));
            }
            case "Suicidal" -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\uD83D\uDECF\uFE0F", "Night Safety Protocol",
                    "Prepare your nighttime environment to reduce risk during the hours when suicidal thoughts often intensify.",
                    "Ongoing", "Easy",
                    "Nighttime isolation amplifies suicidal ideation. A safety-focused sleep setup reduces access to means and ensures connection is available if needed.",
                    List.of("Remove or lock away any items that could be used for self-harm",
                            "Keep your phone charged and within reach — with crisis numbers saved",
                            "988 Suicide & Crisis Lifeline: call or text 988 (available 24/7)",
                            "Crisis Text Line: text HOME to 741741",
                            "Tell one person that nighttime is hard for you — ask if you can text them",
                            "Keep a nightlight on if darkness increases distress",
                            "Place your Reasons for Living list where you can see it from bed")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\uD83C\uDFA7", "Guided Sleep Companion",
                    "Use a guided sleep meditation or audio to provide a comforting voice during the vulnerable transition to sleep.",
                    "30 min", "Easy",
                    "A human voice providing guided relaxation counters the isolation that intensifies suicidal thoughts at night. It gives your brain something safe to follow instead of spiraling.",
                    List.of("Choose a sleep meditation app or YouTube guided sleep talk",
                            "Look for: calm voice, slow pace, body scan or story format",
                            "Set it to play on a timer — 30 minutes is usually enough",
                            "Keep volume low — just audible enough to follow",
                            "The same guide every night creates a sense of companionship and safety",
                            "If you wake in the night, restart the audio — don't lie in silence with your thoughts")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\uD83E\uDDE1", "Tomorrow's One Thing",
                    "Before bed, identify one small thing to look forward to tomorrow — giving your brain a reason to wake up.",
                    "5 min", "Easy",
                    "Suicidal thinking narrows the future to hopelessness. Planting even one small positive expectation for tomorrow creates a tiny bridge to the next day.",
                    List.of("Before turning off the light, complete this sentence:",
                            "'Tomorrow, one thing I can look forward to is...'",
                            "It can be anything: a warm drink, a TV show, sunshine, a pet's greeting",
                            "Write it on a sticky note and place it where you'll see it when waking",
                            "This isn't toxic positivity — it's building the smallest possible bridge",
                            "If you can't think of anything, the thing to look forward to is: 'I survived another night'",
                            "That IS enough. Every morning you wake up is a victory.")));
            }
            default -> {
                s.items.add(new RecItem(moodKey(mood, "slp_1"), "\u23F0", "Consistent Sleep Schedule",
                    "Go to bed and wake up at the same time daily.",
                    "Ongoing", "Easy",
                    "A regular sleep schedule is the strongest predictor of sleep quality.",
                    List.of("Choose a bedtime and wake time that gives you 7-8 hours",
                            "Set alarms for BOTH bedtime and wake time",
                            "Maintain this schedule on weekends",
                            "If you can't fall asleep within 20 minutes, get up and do something calm",
                            "Return to bed only when sleepy",
                            "After 2 weeks, your body will naturally regulate")));
                s.items.add(new RecItem(moodKey(mood, "slp_2"), "\uD83C\uDF1C", "Evening Wind-Down",
                    "Create a calming pre-sleep routine.",
                    "20 min", "Easy",
                    "A predictable evening routine trains your brain to produce melatonin on cue.",
                    List.of("Set screens to night mode 1 hour before bed",
                            "Dim lights 30 minutes before bed",
                            "Make herbal tea or warm water",
                            "Do 5 minutes of gentle stretching",
                            "Read something light for 10 minutes",
                            "Lights out at your scheduled bedtime")));
                s.items.add(new RecItem(moodKey(mood, "slp_3"), "\uD83C\uDF21\uFE0F", "Sleep Environment Optimization",
                    "Set up your bedroom for optimal sleep.",
                    "Ongoing", "Easy",
                    "Environmental factors account for 30% of sleep quality.",
                    List.of("Temperature: Set bedroom to 65-68°F (18-20°C)",
                            "Light: Use blackout curtains or a sleep mask",
                            "Sound: Use earplugs or a white noise machine if needed",
                            "Remove all visible clocks",
                            "Keep phone face-down or in another room",
                            "Reserve bed for sleep only")));
            }
        }
        return s;
    }

    private RecSection buildNutrition(String mood, String badge, String color) {
        RecSection s = new RecSection("nutrition", "\uD83E\uDD57", "Nutrition & Hydration", badge, color);
        switch (mood) {
            case "Anxiety" -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\uD83D\uDCA7", "Hydration Tracking",
                    "Maintain consistent water intake throughout the day — dehydration directly worsens anxiety symptoms.",
                    "All day", "Easy",
                    "Even 1.5% dehydration increases cortisol, the stress hormone. Studies show adequate hydration reduces anxiety scores by 14% on average.",
                    List.of("Start your day with a full glass of water before anything else",
                            "Set 4 reminders throughout the day to drink a glass",
                            "Keep a water bottle visible at your desk or workspace",
                            "Goal: 8 glasses (2 liters) by end of day",
                            "Track with tally marks on a sticky note — visual progress helps",
                            "Replace one caffeinated drink with water or herbal tea")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83E\uDD5C", "Magnesium-Rich Meal",
                    "Include magnesium-rich foods in at least one meal today — magnesium is nature's anti-anxiety mineral.",
                    "Meal", "Easy",
                    "Magnesium regulates the HPA axis (your stress response system). 68% of people don't get enough, and deficiency directly correlates with anxiety severity.",
                    List.of("Choose at least 2 magnesium-rich foods for today:",
                            "Options: almonds, spinach, dark chocolate (70%+), avocado, banana, cashews",
                            "Best time: include them at lunch or as an afternoon snack",
                            "Example meal: Spinach salad with almonds and avocado",
                            "Example snack: Dark chocolate square with a handful of cashews",
                            "Pair with vitamin B6 foods (chicken, fish) for better absorption")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\uD83C\uDF1F", "Omega-3 Boost",
                    "Add omega-3 rich foods to reduce the inflammation that drives anxiety.",
                    "Meal", "Easy",
                    "Omega-3 fatty acids reduce neuroinflammation and have been shown to lower anxiety scores by 20% in clinical trials — comparable to low-dose medication.",
                    List.of("Choose an omega-3 source for one meal today:",
                            "Best sources: salmon, sardines, mackerel, walnuts, flaxseeds, chia seeds",
                            "Easy option: sprinkle ground flaxseed on yogurt or oatmeal",
                            "Quick option: a handful of walnuts as a snack",
                            "If you dislike fish, consider a quality fish oil supplement (1000mg EPA/DHA)",
                            "Aim for omega-3 foods at least 3 times this week")));
            }
            case "Depression" -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\uD83E\uDD5A", "Protein-First Breakfast",
                    "Start your day with protein to stabilize blood sugar and provide amino acids your brain needs for mood.",
                    "Morning", "Easy",
                    "Protein provides tryptophan (serotonin precursor) and tyrosine (dopamine precursor). A protein-rich breakfast prevents the blood sugar crash that worsens low mood.",
                    List.of("Eat protein within 1 hour of waking",
                            "Quick options: 2 eggs, Greek yogurt, handful of nuts, protein shake",
                            "Pair with complex carbs for sustained energy: oats, whole grain toast",
                            "Avoid: sugary cereals, pastries, juice (they cause a mood crash later)",
                            "If you have no appetite, start with just a handful of almonds and water",
                            "Even a small amount of protein is better than skipping breakfast")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83E\uDD66", "Folate & B-Vitamin Focus",
                    "Eat foods rich in folate and B-vitamins — the nutrients most commonly deficient in depression.",
                    "Meal", "Easy",
                    "Folate (B9) is essential for producing serotonin, dopamine, and norepinephrine. Low folate levels are found in up to 30% of people with depression.",
                    List.of("Include at least 2 folate-rich foods today:",
                            "Options: spinach, lentils, black beans, asparagus, eggs, broccoli",
                            "Also good: fortified cereals, oranges, avocado",
                            "Example lunch: Lentil soup with a side of spinach salad",
                            "Example dinner: Eggs with sauteed broccoli and black beans",
                            "B-vitamins work as a team — eat a variety for best effect")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\u2600\uFE0F", "Vitamin D Strategy",
                    "Boost vitamin D through food and sunlight — low levels are strongly linked to depression.",
                    "Meal + Outdoor", "Easy",
                    "Vitamin D receptors exist throughout the brain, especially in areas controlling mood. People with depression are 3x more likely to be vitamin D deficient.",
                    List.of("Get 15 minutes of midday sunlight on your skin",
                            "Eat vitamin D foods: fatty fish, egg yolks, fortified milk, mushrooms",
                            "Quick option: a glass of fortified milk or orange juice",
                            "If you live in a low-sunlight area, consider 2000 IU supplement",
                            "Best absorbed with fat — eat D-rich foods with healthy fats",
                            "Track your sun exposure this week — aim for 15 min daily")));
            }
            case "Stress" -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\uD83C\uDF4A", "Vitamin C Loading",
                    "Increase vitamin C intake to directly lower cortisol, your primary stress hormone.",
                    "Meal", "Easy",
                    "Vitamin C is rapidly depleted during stress. Supplementing with vitamin C (or eating C-rich foods) reduces cortisol by 25% and speeds stress recovery.",
                    List.of("Eat at least 2 vitamin C-rich foods today:",
                            "Best sources: bell peppers, oranges, strawberries, kiwi, broccoli",
                            "Bell peppers have 3x more vitamin C than oranges",
                            "Easy option: Add bell pepper slices to lunch, orange as afternoon snack",
                            "Have a citrus fruit with breakfast",
                            "Spread intake throughout the day — your body can't store vitamin C")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83C\uDF75", "Cortisol-Lowering Drink",
                    "Replace one caffeinated drink with a stress-reducing herbal alternative.",
                    "Afternoon", "Easy",
                    "Caffeine stimulates cortisol production, amplifying the stress cycle. Herbal alternatives like chamomile contain apigenin, which binds to GABA receptors and promotes calm.",
                    List.of("Choose your afternoon replacement drink:",
                            "Chamomile tea — binds to the same brain receptors as anti-anxiety medication",
                            "Green tea — has L-theanine which promotes calm alertness",
                            "Warm water with lemon and honey — simple, hydrating, soothing",
                            "Ashwagandha tea — an adaptogen that lowers cortisol by 30%",
                            "Make it a ritual: brew it slowly, hold the warm cup, sip mindfully")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\uD83C\uDF3D", "Anti-Stress Meal Planning",
                    "Prevent stress-eating by planning nutritious meals and eating at regular intervals.",
                    "All day", "Medium",
                    "Blood sugar drops (from skipped meals) trigger cortisol release and worsen stress. Regular, balanced meals keep your stress response stable.",
                    List.of("Plan 3 balanced meals for today — don't skip any",
                            "Eat every 3-4 hours to prevent blood sugar crashes",
                            "Each meal should include: protein + healthy fat + complex carbs",
                            "Avoid: heavy sugar, excessive caffeine, alcohol, highly processed foods",
                            "Prepare a healthy snack in advance: nuts, fruit, yogurt",
                            "When stress craving hits, drink water first — wait 10 minutes before eating")));
            }
            case "Normal" -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\uD83D\uDCA7", "Water Intake Goal",
                    "Maintain optimal hydration to support brain function and mood stability.",
                    "All day", "Easy",
                    "Your brain is 75% water. Even mild dehydration reduces cognitive performance by 25% and negatively affects mood and energy levels.",
                    List.of("Start your day with a glass of water",
                            "Set 4 reminders to drink water throughout the day",
                            "Goal: 8 glasses by end of day",
                            "Keep a water bottle visible at all times",
                            "Track intake with tally marks",
                            "Replace one sugary drink with water")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83E\uDD57", "Balanced Plate Method",
                    "Follow the simple plate method for optimal nutrition: 1/2 vegetables, 1/4 protein, 1/4 whole grains.",
                    "Meal", "Easy",
                    "Balanced nutrition provides steady fuel for your brain. The plate method simplifies healthy eating without calorie counting.",
                    List.of("At lunch and dinner, visually divide your plate",
                            "Half the plate: colorful vegetables or salad",
                            "Quarter of the plate: lean protein (chicken, fish, beans, tofu)",
                            "Quarter of the plate: whole grains (brown rice, quinoa, whole wheat bread)",
                            "Add a small amount of healthy fat: olive oil, avocado, nuts",
                            "Eat slowly and stop when 80% full")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\uD83E\uDD63", "Gut-Brain Connection",
                    "Feed your gut microbiome with probiotic and prebiotic foods that directly support mental health.",
                    "Meal", "Easy",
                    "90% of serotonin is produced in the gut. A healthy microbiome is now considered essential for mental health.",
                    List.of("Include one probiotic food today: yogurt, kefir, kimchi, or sauerkraut",
                            "Include one prebiotic food: bananas, oats, garlic, or onions",
                            "Probiotics = good bacteria. Prebiotics = food for good bacteria.",
                            "Easy combo: Greek yogurt topped with banana and oats for breakfast",
                            "Variety matters — try different fermented foods throughout the week",
                            "Reduce sugar and processed foods — they feed harmful gut bacteria")));
            }
            case "Bipolar" -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\u23F0", "Timed Meal Structure",
                    "Eat meals at the same times daily to stabilize your circadian rhythm and prevent mood-triggered eating patterns.",
                    "All day", "Medium",
                    "Regular meal timing reinforces the circadian rhythm that bipolar disorder disrupts. Skipping meals or binge-eating during episodes destabilizes blood sugar and worsens mood swings.",
                    List.of("Set 3 meal alarms: breakfast within 1 hour of waking, lunch, dinner",
                            "Eat at these times even if you don't feel hungry (mania suppresses appetite)",
                            "Eat at these times even if you want to eat more (depression increases cravings)",
                            "Keep each meal balanced: protein + complex carbs + healthy fat",
                            "Avoid large meals late at night — they disrupt sleep",
                            "Meal timing is as important as medication timing — treat it equally seriously")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83D\uDEAB", "Mood Destabilizer Avoidance",
                    "Identify and reduce foods and substances that directly destabilize bipolar mood cycles.",
                    "All day", "Medium",
                    "Certain substances directly interfere with mood-stabilizing medication and trigger episodes. Caffeine, alcohol, and high sugar are the top three destabilizers.",
                    List.of("Caffeine: Limit to 1 cup before noon — caffeine disrupts sleep, the #1 trigger",
                            "Alcohol: Reduce or eliminate — it interferes with mood stabilizers and disrupts sleep",
                            "Sugar: Avoid sugar spikes — they cause rapid mood fluctuations",
                            "Processed foods: High in inflammatory compounds that worsen episodes",
                            "Track what you eat on high-mood and low-mood days — look for patterns",
                            "Replace destabilizers with: herbal tea, sparkling water, whole fruits")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\uD83E\uDD5C", "Lithium-Friendly Nutrition",
                    "If on lithium, maintain consistent sodium and water intake to keep medication levels stable.",
                    "All day", "Easy",
                    "Lithium levels are affected by sodium and hydration. Sudden changes in salt or water intake can make lithium levels toxic or ineffective.",
                    List.of("Drink 8-10 glasses of water daily — dehydration concentrates lithium",
                            "Keep sodium intake CONSISTENT — don't suddenly go low-salt or high-salt",
                            "Avoid drastic diet changes — they affect medication absorption",
                            "If you sweat heavily (exercise, heat), increase water and salt slightly",
                            "Caffeine affects lithium clearance — keep coffee intake consistent",
                            "Always tell your doctor before making major dietary changes")));
            }
            case "Personality Disorder" -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\uD83C\uDF7D\uFE0F", "Structured Eating Schedule",
                    "Eat at regular intervals to prevent the blood sugar crashes that trigger emotional dysregulation.",
                    "All day", "Easy",
                    "Blood sugar instability directly worsens emotional reactivity. People with emotional dysregulation are more sensitive to hunger-triggered mood shifts (hanger is real and amplified).",
                    List.of("Eat 3 meals and 2 snacks at set times — don't skip",
                            "Never go more than 4 hours without eating",
                            "Each meal should include protein to stabilize blood sugar",
                            "If emotional eating urges hit, pause 10 minutes and drink water first",
                            "After 10 minutes, if still hungry, eat something nutritious",
                            "The goal isn't restriction — it's structure. Structure reduces impulsive eating.")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83E\uDDE0", "Brain-Calming Foods",
                    "Include foods that support GABA and serotonin production — the neurotransmitters that reduce emotional reactivity.",
                    "Meal", "Easy",
                    "GABA and serotonin are the brain's calming chemicals. Eating their precursors provides the raw materials your brain needs for emotional regulation.",
                    List.of("For serotonin (calm mood): turkey, eggs, cheese, nuts, salmon, tofu",
                            "For GABA (reduced anxiety): green tea, fermented foods, whole grains",
                            "For dopamine (motivation): bananas, avocados, almonds, dark chocolate",
                            "Example breakfast: Eggs with avocado and green tea",
                            "Example lunch: Salmon with brown rice and fermented vegetables",
                            "Avoid: excessive sugar, alcohol, caffeine — they deplete calming neurotransmitters")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\uD83C\uDF4E", "Mindful Eating Practice",
                    "Eat one meal today with full attention — no screens, no distractions — to build the connection between eating and emotions.",
                    "Meal", "Easy",
                    "Emotional eating is a common coping mechanism. Mindful eating rebuilds the connection between physical hunger and emotional need, reducing impulsive food use.",
                    List.of("Choose one meal to eat mindfully — turn off ALL screens",
                            "Before eating, rate your hunger 1-10 and your emotional state",
                            "Take the first 3 bites slowly — notice texture, temperature, flavor",
                            "Put your fork down between bites",
                            "Halfway through, pause and check: am I still hungry, or eating for another reason?",
                            "After the meal, rate satisfaction and emotional state again",
                            "Notice: feeding physical hunger is satisfying. Emotional eating never is.")));
            }
            case "Suicidal" -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\uD83C\uDF75", "One Warm Drink",
                    "Make yourself one warm, comforting drink. This small act of self-care matters more than you think.",
                    "5 min", "Easy",
                    "When you're in crisis, the act of preparing and holding a warm drink activates self-nurturing behavior. The warmth in your hands sends safety signals to your brain.",
                    List.of("Go to your kitchen — that's the first step, and it matters",
                            "Boil water and make ANY warm drink: tea, cocoa, warm milk, warm water with honey",
                            "Hold the mug with both hands — feel the warmth",
                            "Sip slowly — focus on the temperature, the taste, the sensation",
                            "You just did something caring for yourself. That's important.",
                            "If making the drink feels too hard, ask someone to make it for you — asking is okay")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83C\uDF4C", "One Small Meal",
                    "Eat one small, easy thing today. Not eating worsens depression and makes crisis thinking louder.",
                    "Meal", "Easy",
                    "Starvation directly worsens depression, impairs decision-making, and intensifies suicidal ideation. Even a small amount of food changes brain chemistry enough to help.",
                    List.of("You don't need a full meal — one small thing counts",
                            "Easy options: a banana, crackers, toast, a handful of nuts, yogurt",
                            "If chewing feels hard, a smoothie or soup works just as well",
                            "Eat whatever requires the least effort — this is about fuel, not perfection",
                            "Eating is not giving up on your pain — it's giving your brain what it needs to cope",
                            "If you can eat one thing, try to eat something small every 4 hours today")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\uD83D\uDCA7", "Sip Water",
                    "Drink one glass of water right now. Dehydration worsens confusion, fatigue, and dark thinking.",
                    "2 min", "Easy",
                    "Dehydration increases cortisol by 25% and impairs the prefrontal cortex (the part of your brain that helps you see beyond this moment). Water helps your brain think more clearly.",
                    List.of("Get a glass or bottle of water right now",
                            "Take one sip. Then another. You don't have to finish it.",
                            "If plain water is hard, add a squeeze of lemon or a splash of juice",
                            "Keep the water near you — take sips throughout the day",
                            "Hydration won't fix everything, but it helps your brain function better",
                            "A brain that's less dehydrated makes slightly better decisions. That matters.")));
            }
            default -> {
                s.items.add(new RecItem(moodKey(mood, "nut_1"), "\uD83D\uDCA7", "Water Intake Goal",
                    "Maintain optimal hydration for brain function.",
                    "All day", "Easy",
                    "Even mild dehydration reduces cognitive performance and affects mood.",
                    List.of("Start your day with a glass of water",
                            "Set 4 reminders to drink water throughout the day",
                            "Goal: 8 glasses by end of day",
                            "Keep a water bottle visible at all times",
                            "Track intake with tally marks",
                            "Replace one sugary drink with water")));
                s.items.add(new RecItem(moodKey(mood, "nut_2"), "\uD83E\uDD57", "Balanced Plate Method",
                    "Follow the simple plate method for optimal nutrition.",
                    "Meal", "Easy",
                    "Balanced nutrition provides steady fuel for your brain.",
                    List.of("At lunch and dinner, visually divide your plate",
                            "Half the plate: colorful vegetables or salad",
                            "Quarter: lean protein", "Quarter: whole grains",
                            "Add a small amount of healthy fat",
                            "Eat slowly and stop when 80% full")));
                s.items.add(new RecItem(moodKey(mood, "nut_3"), "\uD83E\uDD63", "Gut-Brain Connection",
                    "Feed your gut microbiome with probiotic and prebiotic foods.",
                    "Meal", "Easy",
                    "90% of serotonin is produced in the gut.",
                    List.of("Include one probiotic food today: yogurt, kefir, kimchi, or sauerkraut",
                            "Include one prebiotic food: bananas, oats, garlic, or onions",
                            "Easy combo: Greek yogurt with banana and oats",
                            "Variety matters — try different fermented foods",
                            "Reduce sugar and processed foods")));
            }
        }
        return s;
    }

    //  DATABASE METHODS 

    public Set<String> getTodayProgress(int userId) {
        return progressRepo.getTodayProgress(userId);
    }

    public void setRecItemCompleted(int userId, String itemKey, boolean completed) {
        progressRepo.setRecItemCompleted(userId, itemKey, completed);
    }

    public Map<String, Integer> getWeeklyStats(int userId) {
        return progressRepo.getWeeklyStats(userId);
    }
}
