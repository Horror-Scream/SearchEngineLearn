package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class QueryProcessor {

    @Autowired
    private LuceneMorphology luceneMorphology;

    private static final Pattern CYRILLIC_PATTERN = Pattern.compile(".*[а-яА-ЯёЁ].*");
    private static final Pattern LATIN_PATTERN = Pattern.compile(".*[a-zA-Z].*");

    public List<String> processQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> lemmas = new HashSet<>();
        String[] words = query.trim().toLowerCase().split("\\s+");

        for (String word : words) {
            if (word.length() < 3 || word.matches(".*\\d.*")) {
                continue;
            }

            try {
                if (isCyrillic(word)) {
                    if (luceneMorphology.checkString(word)) {
                        lemmas.addAll(luceneMorphology.getNormalForms(word));
                    }
                } else if (isLatin(word)) {
                    lemmas.add(word);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new ArrayList<>(lemmas);
    }

    private boolean isCyrillic(String word) {
        return CYRILLIC_PATTERN.matcher(word).matches();
    }

    private boolean isLatin(String word) {
        return LATIN_PATTERN.matcher(word).matches() && !isCyrillic(word);
    }
}