package com.talentlens.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MatcherService {
    private static final Set<String> SKILL_LEXICON = Set.of(
            "python", "javascript", "typescript", "java", "c++", "c#", "go", "rust",
            "react", "angular", "vue", "node.js", "fastapi", "flask", "django",
            "sql", "postgresql", "mysql", "mongodb", "redis", "docker", "kubernetes",
            "aws", "azure", "gcp", "terraform", "git", "github actions", "ci/cd",
            "machine learning", "deep learning", "nlp", "natural language processing",
            "pytorch", "tensorflow", "scikit-learn", "pandas", "numpy", "spark",
            "data analysis", "data visualization", "tableau", "power bi", "excel",
            "rest api", "graphql", "microservices", "agile", "scrum", "communication",
            "leadership", "project management", "figma", "ui/ux", "html", "css");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any",
            "are", "as", "at", "be", "because", "been", "before", "being", "below", "between",
            "both", "but", "by", "can", "could", "did", "do", "does", "doing", "down", "during",
            "each", "few", "for", "from", "further", "had", "has", "have", "having", "he", "her",
            "here", "hers", "herself", "him", "himself", "his", "how", "i", "if", "in", "into",
            "is", "it", "its", "itself", "just", "me", "more", "most", "my", "myself", "no",
            "nor", "not", "of", "off", "on", "once", "only", "or", "other", "our", "ours",
            "ourselves", "out", "over", "own", "same", "she", "should", "so", "some", "such",
            "than", "that", "the", "their", "theirs", "them", "themselves", "then", "there",
            "these", "they", "this", "those", "through", "to", "too", "under", "until", "up",
            "very", "was", "we", "were", "what", "when", "where", "which", "while", "who",
            "whom", "why", "will", "with", "would", "you", "your", "yours", "yourself", "yourselves",
            "also", "years", "work", "working", "using", "required", "responsibilities", "experience");
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}_]{2,}");
    private static final Pattern KEYWORD_WORDS = Pattern.compile("[a-z][a-z0-9+#./-]{2,}");

    public Map<String, Object> analyze(String jobDescription, List<CandidateInput> candidates) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (CandidateInput candidate : candidates) {
            results.add(analyzeCandidate(jobDescription, candidate.text(), candidate.name()));
        }
        results.sort(Comparator.comparingInt(result -> -((Number) result.get("score")).intValue()));

        List<String> jobSkills = extractSkills(jobDescription);
        List<String> jobKeywords = extractKeywords(jobDescription, 12);
        int totalScore = results.stream().mapToInt(result -> ((Number) result.get("score")).intValue()).sum();
        int topScore = ((Number) results.getFirst().get("score")).intValue();
        Map<String, Object> summary = Map.of(
                "candidates", results.size(),
                "average_score", Math.round((double) totalScore / results.size()),
                "top_score", topScore);
        return Map.of(
                "job", Map.of("skills", jobSkills, "keywords", jobKeywords),
                "results", results,
                "summary", summary);
    }

    public Map<String, Object> analyzeCandidate(String jobText, String resumeText, String candidateName) {
        double similarity = tfidfSimilarity(jobText, resumeText);
        Set<String> jobSkills = new HashSet<>(extractSkills(jobText));
        Set<String> resumeSkills = new HashSet<>(extractSkills(resumeText));
        List<String> matchedSkills = jobSkills.stream().filter(resumeSkills::contains).sorted().toList();
        List<String> missingSkills = jobSkills.stream().filter(skill -> !resumeSkills.contains(skill)).sorted().toList();
        String normalizedResume = normalize(resumeText);
        List<String> keywordHits = extractKeywords(jobText, 14).stream()
                .filter(keyword -> normalizedResume.contains(keyword)).toList();
        double skillScore = jobSkills.isEmpty() ? 0 : (double) matchedSkills.size() / jobSkills.size();
        int score = (int) Math.round(Math.min(100, (similarity * 0.65 + skillScore * 0.35) * 100));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", candidateName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""));
        if (((String) result.get("id")).isEmpty()) {
            result.put("id", "candidate");
        }
        result.put("candidate", candidateName);
        result.put("score", score);
        result.put("similarity", (int) Math.round(similarity * 100));
        result.put("skills", resumeSkills.stream().sorted().toList());
        result.put("matched_skills", matchedSkills);
        result.put("missing_skills", missingSkills);
        result.put("keyword_hits", keywordHits);
        result.put("experience_signal", experienceSignal(resumeText));
        result.put("preview", preview(resumeText));
        return result;
    }

    private double tfidfSimilarity(String first, String second) {
        List<Map<String, Integer>> documents = List.of(termCounts(first), termCounts(second));
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Map<String, Integer> document : documents) {
            document.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
        }
        Map<String, Double> firstVector = tfidfVector(documents.getFirst(), documentFrequency);
        Map<String, Double> secondVector = tfidfVector(documents.getLast(), documentFrequency);
        double dot = 0;
        double firstNorm = 0;
        double secondNorm = 0;
        for (Map.Entry<String, Double> entry : firstVector.entrySet()) {
            double value = entry.getValue();
            firstNorm += value * value;
            dot += value * secondVector.getOrDefault(entry.getKey(), 0.0);
        }
        for (double value : secondVector.values()) {
            secondNorm += value * value;
        }
        return firstNorm == 0 || secondNorm == 0 ? 0 : dot / Math.sqrt(firstNorm * secondNorm);
    }

    private Map<String, Integer> termCounts(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORDS.matcher(normalize(text));
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        for (int index = 0; index < words.size(); index++) {
            counts.merge(words.get(index), 1, Integer::sum);
            if (index + 1 < words.size()) {
                counts.merge(words.get(index) + " " + words.get(index + 1), 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<String, Double> tfidfVector(Map<String, Integer> counts, Map<String, Integer> documentFrequency) {
        Map<String, Double> vector = new HashMap<>();
        counts.forEach((term, frequency) -> {
            double inverseDocumentFrequency = Math.log(3.0 / (1 + documentFrequency.get(term))) + 1;
            vector.put(term, frequency * inverseDocumentFrequency);
        });
        return vector;
    }

    private List<String> extractSkills(String text) {
        String normalized = normalize(text);
        return SKILL_LEXICON.stream()
                .filter(normalized::contains)
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private List<String> extractKeywords(String text, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = KEYWORD_WORDS.matcher(normalize(text));
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOP_WORDS.contains(word)) {
                counts.merge(word, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String experienceSignal(String text) {
        Matcher milestones = Pattern.compile("(?:19|20)\\d{2}").matcher(text);
        int milestoneCount = 0;
        while (milestones.find()) {
            milestoneCount++;
        }
        if (milestoneCount >= 2) {
            return milestoneCount + " career milestones detected";
        }
        Matcher years = Pattern.compile("(\\d+)\\+?\\s+years?").matcher(normalize(text));
        return years.find() ? years.group(1) + " years experience" : "Early-career profile";
    }

    private String preview(String text) {
        String clean = text.replaceAll("\\s+", " ").strip();
        return clean.length() > 220 ? clean.substring(0, 220) + "..." : clean;
    }

    private String normalize(String text) {
        return text.toLowerCase().replaceAll("\\s+", " ").strip();
    }
}