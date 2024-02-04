package searchengine.parser;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.*;
import java.util.stream.Collectors;

public class Lemmatizer {
    //    private static final String REPLACE_REGEX = "[-,.?:;\\d+!\"«»“”()#_@№{}/…©®]";
    private static final String REPLACE_REGEX = "[^а-яА-ЯёЁ]";
    private static final String MORPH_REGEX = "(^.+\\|[jhlnpfekoqR].+)";
    private Set<String> lemmasSet;

    public Set<String> getLemmas(String text) {
        List<String> words = Arrays.stream(text.toLowerCase()
                        .replaceAll(REPLACE_REGEX, " ").split("\\s+"))
                .filter(w -> w.length() > 1).collect(Collectors.toList());
        if (!words.isEmpty()) {
//             System.out.println("Очищенные слова: " + words);
            lemmasSet = new HashSet<>();
            getRusLemmas(words);
        }
        return lemmasSet;
    }

    private void getRusLemmas(List<String> words) {
        try {
            LuceneMorphology luceneMorphRus = new RussianLuceneMorphology();

            for (String word : words) {
                List<String> wordMorphInfo = luceneMorphRus.getMorphInfo(word);
                if (!wordMorphInfo.get(0).matches(MORPH_REGEX)) {
                    lemmasSet.add(luceneMorphRus.getNormalForms(word).get(0));
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
