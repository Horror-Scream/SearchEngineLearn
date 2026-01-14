package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LemmaProcessor {

    @Autowired
    private LuceneMorphology luceneMorphology;

    private static final Pattern CYRILLIC_PATTERN = Pattern.compile(".*[а-яА-ЯёЁ].*");
    private static final Pattern LATIN_PATTERN = Pattern.compile(".*[a-zA-Z].*");

    public Map<String, Integer> extractLemmas(String content) {
        Map<String, Integer> lemmas = new HashMap<>();

        if (content == null || content.isEmpty()) {
            return lemmas;
        }

        try {
            Document doc = Jsoup.parse(content);
            String text = doc.body().text();

            String[] words = text.replaceAll("\\s+", " ").trim().split("\\s+");
            for (String word : words) {
                if (word.length() < 3 || word.matches(".*\\d.*")) {
                    continue;
                }

                try {
                    if (isCyrillic(word)) {
                        processRussianWord(word, lemmas);
                    } else if (isLatin(word)) {
                        lemmas.put(word, lemmas.getOrDefault(word, 0) + 1);
                    }
                } catch (Exception e) {
                    log.error("Ошибка обработки слова '{}':{}", word, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Ошибка парсинга HTML для извлечения текста: ", e);
        }
        return lemmas;
    }

    private void processRussianWord(String word, Map<String, Integer> lemmas) {
        if (!luceneMorphology.checkString(word)) {
            return;
        }

        List<String> normalForms = luceneMorphology.getNormalForms(word);
        for (String lemma : normalForms) {
            if (isValidPartOfSpeech(lemma)) {
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        }
    }

    private boolean isValidPartOfSpeech(String morphInfo) {
        return !morphInfo.contains("ПРЕДЛ")
                && !morphInfo.contains("СОЮЗ")
                && !morphInfo.contains("ЧАСТ")
                && !morphInfo.contains("МЕЖД");
    }

    private boolean isCyrillic(String word) {
        return CYRILLIC_PATTERN.matcher(word).matches();
    }

    private boolean isLatin(String word) {
        return LATIN_PATTERN.matcher(word).matches() && !isCyrillic(word);
    }
}