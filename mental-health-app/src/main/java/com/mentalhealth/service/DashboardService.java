package com.mentalhealth.service;

import com.mentalhealth.model.Prediction;
import com.mentalhealth.repository.PredictionRepository;
import com.mentalhealth.repository.IPredictionRepository;
import com.mentalhealth.repository.SelfCareRepository;
import com.mentalhealth.util.MoodUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;

// Service class aggregating dashboard data: metrics, trends, insights, and notifications
public class DashboardService implements IDashboardService {

    private static final Logger LOG = Logger.getLogger(DashboardService.class.getName());

    private final IPredictionRepository predictionRepository;
    private final SelfCareRepository selfCareRepository;

    public DashboardService() {
        this.predictionRepository = new PredictionRepository();
        this.selfCareRepository = new SelfCareRepository();
    }

    public DashboardService(IPredictionRepository predictionRepository, SelfCareRepository selfCareRepository) {
        this.predictionRepository = predictionRepository;
        this.selfCareRepository = selfCareRepository;
    }

    // ─── DATA CLASSES ───────────────────────────────────────────

    /** Holds all dashboard metrics in one object */
    public static class DashboardData {
            // Weekly mood colors for trend bar
            public List<String> weeklyMoodColors = new ArrayList<>();
            
        // Metric cards
        public int totalAnalyses;
        public int streakDays;
        public String dominantMood;
        public String dominantMoodColor;
        public String riskLevel;
        public String riskColor;

        // Trend card
        public String trendDirection;    // "Improving", "Declining", "Stable"
        public String trendIcon;         // emoji
        public String trendDescription;
        public List<ChartPoint> last7DaysChart = new ArrayList<>();

        // Confidence card (latest analysis)
        public String latestPrediction;
        public double latestConfidence;
        public String latestSeverity;
        public String latestColor;

        // Insight cards
        public String patternInsight;
        public String secondaryIndicators;
        public String guidanceText;
        public String lastUpdateTime;

        // Notifications
        public List<String> notifications = new ArrayList<>();

        // Mood frequency breakdown (for dashboard bars)
        public List<MoodFrequency> moodBreakdown = new ArrayList<>();

       
        public boolean hasDataToday;
        public boolean isApiConnected;

        public String dailyTip;
        public String tipMood;
        public SelfCareState selfCare;
        public List<String> resourceLines = new ArrayList<>();
    }

    //Single chart data point 
    public static class ChartPoint {
        public String dateLabel;
        public double score;
        public String mood;

        public ChartPoint(String dateLabel, double score, String mood) {
            this.dateLabel = dateLabel;
            this.score = score;
            this.mood = mood;
        }
    }

    // Holds today's self-care checklist state 
    public static class SelfCareState {
        public boolean slept;
        public boolean water;
        public boolean exercise;
        public boolean outside;
        public boolean social;

        public int completedCount() {
            int count = 0;
            if (slept) count++;
            if (water) count++;
            if (exercise) count++;
            if (outside) count++;
            if (social) count++;
            return count;
        }
    }

    //  MAIN AGGREGATION METHOD 

    // Fetches all dashboard data for a user in one call (controller calls on initialize)
    public DashboardData getDashboardData(int userId) {
        DashboardData data = new DashboardData();
        List<Prediction> last7Days = getLast7DaysPredictions(userId);
        data.weeklyMoodColors = new ArrayList<>();
        for (Prediction p : last7Days) {
            data.weeklyMoodColors.add(getMoodColor(p.getPrediction()));
        }

        try {
            List<Prediction> allPredictions = getAllPredictions(userId);

            // 1. Total analyses count
            data.totalAnalyses = allPredictions.size();

            // 2. Streak (consecutive days with at least one entry)
            data.streakDays = calculateStreak(allPredictions);

            // 3. Dominant mood (most frequent in last 7 days)
            if (!last7Days.isEmpty()) {
                Map<String, Integer> moodCounts = new LinkedHashMap<>();
                for (Prediction p : last7Days) {
                    moodCounts.merge(p.getPrediction(), 1, Integer::sum);
                }
                data.dominantMood = moodCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("No Data");
                data.dominantMoodColor = getMoodColor(data.dominantMood);
            } else {
                data.dominantMood = "No Data";
                data.dominantMoodColor = "#94a3b8";
            }

            // 4. Risk level (based on latest prediction)
            if (!last7Days.isEmpty()) {
                Prediction latest = last7Days.get(last7Days.size() - 1);
                data.riskLevel = latest.getSeverity() != null ? latest.getSeverity() : "Unknown";
                data.riskColor = getRiskColor(data.riskLevel);

                data.latestPrediction = latest.getPrediction();
                data.latestConfidence = latest.getConfidence();
                data.latestSeverity = latest.getSeverity();
                data.latestColor = latest.getColor() != null ? latest.getColor() : "#757575";
            } else {
                data.riskLevel = "No Data";
                data.riskColor = "#94a3b8";
                data.latestPrediction = "None";
                data.latestConfidence = 0;
                data.latestSeverity = "N/A";
                data.latestColor = "#757575";
            }

            // 4b. Mood frequency breakdown for dashboard bars
            if (!last7Days.isEmpty()) {
                Map<String, Integer> freqCounts = new LinkedHashMap<>();
                for (Prediction p : last7Days) {
                    freqCounts.merge(p.getPrediction(), 1, Integer::sum);
                }
                int total = last7Days.size();
                freqCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> {
                        double pct = e.getValue() * 100.0 / total;
                        data.moodBreakdown.add(new MoodFrequency(e.getKey(), e.getValue(), pct, getMoodColor(e.getKey())));
                    });
            }

            // 5. Trend analysis
            computeTrend(data, last7Days);

            // 6. Chart points
            for (Prediction p : last7Days) {
                String label = formatChartDate(p.getCreatedAt());
                double score = getMoodScore(p.getPrediction());
                data.last7DaysChart.add(new ChartPoint(label, score, p.getPrediction()));
            }

            // 7. Insight cards
            computeInsights(data, last7Days, allPredictions);

            // 8. Last update time
            if (!last7Days.isEmpty()) {
                data.lastUpdateTime = formatRelativeTime(last7Days.get(last7Days.size() - 1).getCreatedAt());
            } else {
                data.lastUpdateTime = "No entries yet";
            }

            // 9. Has data today?
            data.hasDataToday = hasEntryToday(last7Days);

            // 10. Notifications
            computeNotifications(data, last7Days, userId);

            // 11. API status check
            data.isApiConnected = checkApiStatus();

            // 12. Daily tip (mood-based)
            data.tipMood = data.dominantMood;
            data.dailyTip = getDailyTip(data.dominantMood);

            // 13. Self-care checklist state
            data.selfCare = getSelfCareState(userId);

            // 14. Community resources
            data.resourceLines = getResourceLines(data.riskLevel);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "DashboardService error", e);
        }

        return data;
    }

    //  DATABASE QUERIES 

    @Override
    public List<Prediction> getLast7DaysPredictions(int userId) {
        return predictionRepository.getLast7Days(userId);
    }

    @Override
    public List<Prediction> getAllPredictions(int userId) {
        return predictionRepository.getAllByUser(userId);
    }

    //  HELPERS 

    private int calculateStreak(List<Prediction> allPredictions) {
        if (allPredictions.isEmpty()) return 0;

        ZoneId pkt = ZoneId.of("Asia/Karachi");
        Set<LocalDate> datesWithEntries = new TreeSet<>();
        DateTimeFormatter dbFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Prediction p : allPredictions) {
            try {
                if (p.getCreatedAt() != null && p.getCreatedAt().length() >= 19) {
                    LocalDate date = LocalDateTime.parse(p.getCreatedAt().substring(0, 19), dbFmt)
                            .atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(pkt)
                            .toLocalDate();
                    datesWithEntries.add(date);
                }
            } catch (Exception ignored) {}
        }

        if (datesWithEntries.isEmpty()) return 0;

        LocalDate today = LocalDate.now(pkt);
        int streak = 0;
        LocalDate checkDate = today;

        // Count backwards from today
        while (datesWithEntries.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }

        // If no entry today, check if yesterday started a streak
        if (streak == 0) {
            checkDate = today.minusDays(1);
            while (datesWithEntries.contains(checkDate)) {
                streak++;
                checkDate = checkDate.minusDays(1);
            }
        }

        return streak;
    }

    private void computeTrend(DashboardData data, List<Prediction> last7Days) {
        if (last7Days.size() < 2) {
            data.trendDirection = "Stable";
            data.trendIcon = "➡️";
            data.trendDescription = last7Days.isEmpty()
                    ? "Start analyzing to see trends"
                    : "Need more entries to determine trend";
            return;
        }

        int mid = last7Days.size() / 2;
        double avgFirst = last7Days.subList(0, mid).stream()
                .mapToDouble(p -> getMoodScore(p.getPrediction()))
                .average().orElse(50);
        double avgSecond = last7Days.subList(mid, last7Days.size()).stream()
                .mapToDouble(p -> getMoodScore(p.getPrediction()))
                .average().orElse(50);

        double threshold = 5.0;

        if (avgSecond < avgFirst - threshold) {
            data.trendDirection = "Improving";
            data.trendIcon = "🌱";
            data.trendDescription = "You’re showing positive momentum! Try to identify what’s working for you this week and build on those habits.";
        } else if (avgSecond > avgFirst + threshold) {
            data.trendDirection = "Declining";
            data.trendIcon = "🧭";
            data.trendDescription = "Your mood trend suggests new challenges. Reflect on recent changes, and try a new coping strategy or reach out for support.";
        } else {
            data.trendDirection = "Stable";
            data.trendIcon = "🔄";
            data.trendDescription = "Your mood has been steady. This is a great time to reinforce healthy routines or experiment with new positive activities.";
        }
    }

    private void computeInsights(DashboardData data, List<Prediction> last7Days, List<Prediction> all) {
        // Pattern Insight
        if (last7Days.size() >= 3) {
            Map<String, Integer> moodCounts = new HashMap<>();
            for (Prediction p : last7Days) {
                moodCounts.merge(p.getPrediction(), 1, Integer::sum);
            }

            long distinctMoods = moodCounts.size();
            if (distinctMoods == 1) {
                data.patternInsight = "Consistent " + last7Days.get(0).getPrediction().toLowerCase() + " pattern detected";
            } else if (distinctMoods >= 4) {
                data.patternInsight = "High emotional variability this week";
            } else {
                String top = moodCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse("mixed");
                data.patternInsight = top + " is your dominant pattern (" + moodCounts.get(top) + " occurrences)";
            }
        } else {
            data.patternInsight = "Track more to see patterns";
        }

        // Secondary Indicators
        if (last7Days.size() >= 2) {
            Set<String> moods = new HashSet<>();
            for (Prediction p : last7Days) moods.add(p.getPrediction());

            if (moods.contains("Anxiety") && moods.contains("Stress")) {
                data.secondaryIndicators = "Overlap with anxiety and stress signals";
            } else if (moods.contains("Depression") && moods.contains("Anxiety")) {
                data.secondaryIndicators = "Co-occurring depression and anxiety detected";
            } else if (moods.contains("Suicidal")) {
                data.secondaryIndicators = "Critical signals detected — please reach out for help";
            } else if (moods.size() == 1 && moods.contains("Normal")) {
                data.secondaryIndicators = "All signals are within healthy range";
            } else {
                data.secondaryIndicators = moods.size() + " distinct emotional states recorded";
            }
        } else {
            data.secondaryIndicators = "More entries needed for analysis";
        }

        // Guidance
        if (!last7Days.isEmpty()) {
            Prediction latest = last7Days.get(last7Days.size() - 1);
            data.guidanceText = switch (latest.getPrediction()) {
                case "Normal" -> "Keep up the great work! Maintain your routines";
                case "Stress" -> "Try deep breathing or a short walk today";
                case "Anxiety" -> "Consider grounding exercises or journaling";
                case "Depression" -> "Reach out to someone you trust today";
                case "Suicidal" -> "Please contact a crisis helpline: 0311-7786264 (PK)";
                default -> "Continue tracking for personalized guidance";
            };
        } else {
            data.guidanceText = "Start analyzing to receive guidance";
        }
    }

    private void computeNotifications(DashboardData data, List<Prediction> last7Days, int userId) {
        // No entry today reminder
        if (!data.hasDataToday) {
            data.notifications.add("📝 You haven't logged your mood today. How are you feeling?");
        }

        // Streak milestone
       if (data.streakDays >= 1 && data.streakDays <= 7) {
    String[] streakMessages = {
        "Day 1: Great start! Keep logging your mood.",
        "Day 2: Keep it up! Consistency is key.",
        "Day 3: 🔥 3-day streak! You're building a great habit!",
        "Day 4: Four days in a row! Awesome progress.",
        "Day 5: Five days tracked! Stay motivated.",
        "Day 6: Six days! Almost a week of tracking.",
        "🎉 7-day streak! A full week of tracking — amazing!"
    };
    data.notifications.add(streakMessages[data.streakDays - 1]);
       }

        // Concerning pattern alert based on cumulative percentage of mood categories
        if (!last7Days.isEmpty()) {
            Map<String, Integer> moodCounts = new HashMap<>();
            for (Prediction p : last7Days) {
                moodCounts.merge(p.getPrediction(), 1, Integer::sum);
            }
            int total = last7Days.size();
            double suicidalPct = moodCounts.getOrDefault("Suicidal", 0) * 100.0 / total;
            double depressionPct = moodCounts.getOrDefault("Depression", 0) * 100.0 / total;
            double normalPct = moodCounts.getOrDefault("Normal", 0) * 100.0 / total;
            double stressPct = moodCounts.getOrDefault("Stress", 0) * 100.0 / total;
            double anxietyPct = moodCounts.getOrDefault("Anxiety", 0) * 100.0 / total;
            double bipolarPct = moodCounts.getOrDefault("Bipolar", 0) * 100.0 / total;
            double personalityPct = moodCounts.getOrDefault("Personality Disorder", 0) * 100.0 / total;

            // thresholds for realistic notifications
            if (suicidalPct >= 20) {
                data.notifications.add("💛 High proportion of suicidal moods this week. Please reach out for help.");
            } else if (depressionPct >= 30) {
                data.notifications.add("💛 Significant depressive moods detected. Consider talking to someone you trust.");
            } else if (anxietyPct >= 30 || stressPct >= 30) {
                data.notifications.add("⚠️ Elevated anxiety or stress levels this week. Try relaxation techniques.");
            } else if (normalPct >= 60) {
                data.notifications.add("😊 Mostly healthy moods tracked this week. Keep it up!");
            } else if (bipolarPct >= 20 || personalityPct >= 20) {
                data.notifications.add("🔄 Notable mood variability detected. Monitor your patterns closely.");
            }
        }

        // Welcome message if no data 
        if (last7Days.isEmpty()) {
            data.notifications.add("👋 Welcome! Go to New Analysis to start tracking your mental health.");
        }
    }

    private boolean hasEntryToday(List<Prediction> last7Days) {
        String today = LocalDate.now(ZoneId.of("Asia/Karachi")).toString();
        for (Prediction p : last7Days) {
            if (p.getCreatedAt() != null && p.getCreatedAt().startsWith(today)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkApiStatus() {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://localhost:5000/health").openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    //  PROFILE ANALYTICS 

    /** Mood frequency data for distribution charts. */
    public static class MoodFrequency {
        public String mood;
        public int count;
        public double percentage;
        public String color;

        public MoodFrequency(String mood, int count, double percentage, String color) {
            this.mood = mood;
            this.count = count;
            this.percentage = percentage;
            this.color = color;
        }
    }

    // Summary analytics for the user's mood over the past 30 days
    public static class AnalyticsSummary {
        public List<MoodFrequency> topMoods = new ArrayList<>();
        public double avgConfidence;
        public String trendDirection30Day;
        public int totalLast30;
    }

    // Comparison card showing this week vs. the prior week. 
    public static class WeeklyReportCard {
        public int analysesThisWeek;
        public int analysesPriorWeek;
        public String dominantMoodThisWeek;
        public String dominantMoodPriorWeek;
        public String changeDescription;
        public String moodShift;
    }

    @Override
    public List<Prediction> getLast30DaysPredictions(int userId) {
        return predictionRepository.getLast30Days(userId);
    }

    private List<Prediction> getPriorWeekPredictions(int userId) {
        return predictionRepository.getPriorWeek(userId);
    }

    public AnalyticsSummary getAnalyticsSummary(int userId) {
        AnalyticsSummary summary = new AnalyticsSummary();
        List<Prediction> last30 = getLast30DaysPredictions(userId);
        summary.totalLast30 = last30.size();

        if (last30.isEmpty()) {
            summary.avgConfidence = 0;
            summary.trendDirection30Day = "No Data";
            return summary;
        }

        summary.avgConfidence = last30.stream()
                .mapToDouble(Prediction::getConfidence).average().orElse(0) * 100;

        Map<String, Integer> moodCounts = new LinkedHashMap<>();
        for (Prediction p : last30) moodCounts.merge(p.getPrediction(), 1, Integer::sum);

        List<Map.Entry<String, Integer>> sorted = moodCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList();

        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            double pct = e.getValue() * 100.0 / last30.size();
            summary.topMoods.add(new MoodFrequency(e.getKey(), e.getValue(), pct, getMoodColor(e.getKey())));
        }

        int mid = last30.size() / 2;
        double avgFirst = last30.subList(0, mid).stream()
                .mapToDouble(p -> getMoodScore(p.getPrediction())).average().orElse(50);
        double avgSecond = last30.subList(mid, last30.size()).stream()
                .mapToDouble(p -> getMoodScore(p.getPrediction())).average().orElse(50);

        if (avgSecond < avgFirst - 5.0) summary.trendDirection30Day = "Improving";
        else if (avgSecond > avgFirst + 5.0) summary.trendDirection30Day = "Declining";
        else summary.trendDirection30Day = "Stable";

        return summary;
    }

    public WeeklyReportCard getWeeklyReportCard(int userId) {
        WeeklyReportCard card = new WeeklyReportCard();
        List<Prediction> thisWeek = getLast7DaysPredictions(userId);
        List<Prediction> priorWeek = getPriorWeekPredictions(userId);

        card.analysesThisWeek = thisWeek.size();
        card.analysesPriorWeek = priorWeek.size();
        card.dominantMoodThisWeek = getDominantMoodFromList(thisWeek);
        card.dominantMoodPriorWeek = getDominantMoodFromList(priorWeek);

        int diff = card.analysesThisWeek - card.analysesPriorWeek;
        if (diff > 0) card.changeDescription = "+" + diff + " more than last week";
        else if (diff < 0) card.changeDescription = Math.abs(diff) + " fewer than last week";
        else card.changeDescription = "Same as last week";

        double avgThis = thisWeek.stream().mapToDouble(p -> getMoodScore(p.getPrediction())).average().orElse(50);
        double avgPrior = priorWeek.stream().mapToDouble(p -> getMoodScore(p.getPrediction())).average().orElse(50);
        if (avgThis < avgPrior - 5) card.moodShift = "Improved";
        else if (avgThis > avgPrior + 5) card.moodShift = "Declined";
        else card.moodShift = "Stable";

        return card;
    }

    private String getDominantMoodFromList(List<Prediction> predictions) {
        if (predictions.isEmpty()) return "No Data";
        Map<String, Integer> counts = new HashMap<>();
        for (Prediction p : predictions) counts.merge(p.getPrediction(), 1, Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("No Data");
    }

    @Override
    public Map<String, Double> getMoodHeatmapData(int userId, int months) {
        Map<String, Double> heatmap = new LinkedHashMap<>();
        
        // Get predictions from repository (aggregating based on months requested)
        List<Prediction> predictions = getLast30DaysPredictions(userId);
        if (months > 1) {
            predictions = predictionRepository.getLast30Days(userId);  // fallback to 30 days for now
        }
        
        Map<String, List<Double>> dayScores = new LinkedHashMap<>();
        for (Prediction p : predictions) {
            if (p.getCreatedAt() != null && p.getCreatedAt().length() >= 10) {
                String dateKey = p.getCreatedAt().substring(0, 10);
                dayScores.computeIfAbsent(dateKey, k -> new ArrayList<>())
                        .add(getMoodScore(p.getPrediction()));
            }
        }
        
        for (Map.Entry<String, List<Double>> entry : dayScores.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(d -> d).average().orElse(0);
            heatmap.put(entry.getKey(), avg);
        }
        
        return heatmap;
    }

    //  DAILY TIP METHODS 

    private Map<String, List<String>> buildTipMap() {
        Map<String, List<String>> tipMap = new LinkedHashMap<>();

        tipMap.put("Anxiety", List.of(
            "Try the 5-4-3-2-1 grounding technique: name 5 things you see, 4 you touch, 3 you hear, 2 you smell, 1 you taste.",
            "Box breathing: inhale 4s, hold 4s, exhale 4s, hold 4s. Repeat 4 times.",
            "Write down your three biggest worries, then write one small action for each.",
            "Progressive muscle relaxation: tense each muscle group for 5s then release, from toes upward.",
            "Limit caffeine today — it amplifies the physical sensations of anxiety."
        ));

        tipMap.put("Depression", List.of(
            "Start with one small task: make your bed, drink water, or step outside for 2 minutes.",
            "Reach out to one person today — even a text message counts.",
            "Write down three things that were objectively good today, no matter how small.",
            "Physical movement changes brain chemistry. Even a 10-minute walk helps.",
            "Sunlight and fresh air are free medicine. Spend 15 minutes outside before noon."
        ));

        tipMap.put("Stress", List.of(
            "Identify your top priority for today and focus only on that. Everything else can wait.",
            "Take a 5-minute break every 50 minutes. Stand up, stretch, and breathe deeply.",
            "Write your stress down. Externalizing worries reduces their mental load.",
            "Say no to one non-essential commitment this week.",
            "A cold splash of water on your face activates the diving reflex and lowers heart rate."
        ));

        tipMap.put("Normal", List.of(
            "Great time to build a habit. Consistency during good days protects you during hard ones.",
            "Reflect on what is working well in your life right now. Write it down.",
            "Invest in a relationship today — call a friend or family member to connect.",
            "Practice gratitude: list three specific things you are grateful for and why.",
            "Use this stable period to learn a new coping skill you can lean on later."
        ));

        return tipMap;
    }

    private List<String> getDefaultTips() {
        return List.of(
            "Be gentle with yourself today. You are doing better than you think.",
            "Consistency in small habits builds resilience. Focus on one positive action.",
            "Reach out to your support network today — connection is protective.",
            "Track your mood every day this week to discover your personal patterns.",
            "Remember: seeking help is a sign of strength, not weakness."
        );
    }

    public String getDailyTip(String dominantMood) {
        Map<String, List<String>> tipMap = buildTipMap();
        List<String> tips = tipMap.getOrDefault(
            dominantMood != null ? dominantMood.trim() : "", getDefaultTips());
        int index = LocalDate.now(ZoneId.of("Asia/Karachi")).getDayOfYear() % tips.size();
        return tips.get(index);
    }

    public String getDailyTipWithOffset(String dominantMood, int offset) {
        Map<String, List<String>> tipMap = buildTipMap();
        List<String> tips = tipMap.getOrDefault(
            dominantMood != null ? dominantMood.trim() : "", getDefaultTips());
        int index = (LocalDate.now(ZoneId.of("Asia/Karachi")).getDayOfYear() + offset) % tips.size();
        if (index < 0) index += tips.size();
        return tips.get(index);
    }

    //  SELF-CARE CHECKLIST METHODS 

    public SelfCareState getSelfCareState(int userId) {
        SelfCareState state = new SelfCareState();
        String today = LocalDate.now(ZoneId.of("Asia/Karachi")).toString();
        Map<String, Boolean> checklist = selfCareRepository.getChecklist(userId, today);

        state.slept    = checklist.getOrDefault("sleep", false);
        state.water    = checklist.getOrDefault("water", false);
        state.exercise = checklist.getOrDefault("exercise", false);
        state.outside  = checklist.getOrDefault("outside", false);
        state.social   = checklist.getOrDefault("social", false);

        return state;
    }

    public void setSelfCareItem(int userId, String itemKey, boolean checked) {
        String today = LocalDate.now(ZoneId.of("Asia/Karachi")).toString();
        selfCareRepository.updateItem(userId, today, itemKey, checked);
    }

    //  COMMUNITY RESOURCE METHODS 

    public List<String> getResourceLines(String riskLevel) {
        List<String> lines = new ArrayList<>();
        boolean isCritical = "Critical".equalsIgnoreCase(riskLevel) || "Suicidal".equalsIgnoreCase(riskLevel);
        boolean isHigh = "High".equalsIgnoreCase(riskLevel) || isCritical;

        if (isCritical) {
            lines.add(" CRISIS LINE (PK): 0311-7786264 ");
            lines.add(" Umang Helpline: 0311-7786264 ");
            lines.add(" Rozan Counseling: 0800-22444 ");
        }

        lines.add("Umang Mental Health: 0311-7786264");
        lines.add("Rozan Counseling: 0800-22444");
        lines.add("Madadgar National Helpline: 1098");
        lines.add("National Youth Helpline (HEC): 0800-69457");

        if (isHigh) {
            lines.add("Befrienders Worldwide: befrienders.org");
            lines.add("WHO MH Atlas: who.int/mental_health");
        }

        lines.add("Mental Health Foundation: mentalhealth.org.uk");
        return lines;
    }

    //  FORMATTING HELPERS 

    // Delegates to MoodUtils.getMoodScore
    public double getMoodScore(String mood) {
        return MoodUtils.getMoodScore(mood);
    }

    // Delegates to MoodUtils.getMoodColor
    public String getMoodColor(String mood) {
        return MoodUtils.getMoodColor(mood);
    }

    private String getRiskColor(String risk) {
        if (risk == null) return "#94a3b8";
        return switch (risk.trim()) {
            case "Low" -> "#10b981";
            case "Moderate" -> "#f59e0b";
            case "High" -> "#ef4444";
            case "Critical" -> "#991b1b";
            default -> "#94a3b8";
        };
    }

    private String formatChartDate(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.length() < 10) return "---";
        try {
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr.substring(0, 19).replace(" ", "T"));
            java.time.ZonedDateTime local = utc.atZone(java.time.ZoneOffset.UTC)
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"));
            String dayName = local.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.SHORT, Locale.ENGLISH);
            int hour = local.getHour();
            String ampm = hour >= 12 ? "PM" : "AM";
            hour = hour % 12;
            if (hour == 0) hour = 12;
            return dayName + " " + local.getMonthValue() + "/" + local.getDayOfMonth() + " " + hour + ampm;
        } catch (Exception e) {
            try {
                String[] parts = dateTimeStr.substring(0, 10).split("-");
                if (parts.length >= 3) return parts[1] + "/" + parts[2];
            } catch (Exception ignored) {}
            return dateTimeStr.substring(0, Math.min(10, dateTimeStr.length()));
        }
    }

    private String formatRelativeTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.length() < 10) return "Unknown";
        try {
            LocalDateTime utc = LocalDateTime.parse(dateTimeStr.substring(0, 19).replace(" ", "T"));
            java.time.ZonedDateTime local = utc.atZone(java.time.ZoneOffset.UTC)
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Karachi"));
            LocalDate entryDate = local.toLocalDate();
            LocalDate now = LocalDate.now(java.time.ZoneId.of("Asia/Karachi"));

            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(entryDate, now);
            if (daysBetween == 0) {
                int hour = local.getHour();
                int min = local.getMinute();
                String ampm = hour >= 12 ? "PM" : "AM";
                hour = hour % 12;
                if (hour == 0) hour = 12;
                return "Today at " + hour + ":" + String.format("%02d", min) + " " + ampm;
            }
            if (daysBetween == 1) return "Yesterday";
            if (daysBetween < 7) return daysBetween + " days ago";
            return entryDate.format(DateTimeFormatter.ofPattern("MMM dd"));
        } catch (Exception e) {
            return dateTimeStr.substring(0, Math.min(10, dateTimeStr.length()));
        }
    }
}