package searchengine.parser;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.*;
import java.util.stream.Collectors;

public class Lemmatizer {
    //    private static final String REPLACE_REGEX = "[-,.?:;\\d+!\"«»“”()#_@№{}/…©®]";
    private static final String REPLACE_REGEX = "[^а-яА-ЯёЁ]";
    private static final String MORPH_REGEX = "^.+((\\|l)|(\\|n)|(\\|p)|(\\|f)|(\\|e)|(\\|k)|(\\|o)|(\\|q)|(\\|R))";
    private Map<String, Integer> lemmas;

     public Map<String, Integer> getLemmas(String text) {
        List<String> words = Arrays.stream(text.toLowerCase()
                .replaceAll(REPLACE_REGEX, " ").split("\\s+"))
                .filter(w -> w.length() > 1).collect(Collectors.toList());
//        System.out.println("Очищенные слова: " + words);

        lemmas = new HashMap<>();
        getRusLemmas(words);
//        lemmas.forEach((key, value) -> System.out.print(key + " - " + value));
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(lemmas.entrySet());
        System.out.println("Лист - " + list);
        return lemmas;
    }

    public void saveLemma(Map<String, Integer> lemmas) {
        StringBuilder builder = new StringBuilder();
//        builder.append("INSERT INTO lemma(lemma, frequency) VALUES");
        for (Map.Entry<String, Integer> map : lemmas.entrySet()) {
            builder.append(" (");
            builder.append(map.getKey());
            builder.append(", ");
            builder.append(map.getValue());
            builder.append("),");
        }
        builder.deleteCharAt(builder.lastIndexOf(","));

        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(lemmas.entrySet());
        System.out.println("Лист - " + list);
//        lemmaRepository.saveAll(list);
//        lemmaRepository.add(builder.toString());
    }

    private void getRusLemmas(List<String> words) {
        try {
            LuceneMorphology luceneMorphRus = new RussianLuceneMorphology();

            for (String word : words) {
                List<String> wordMorphInfo = luceneMorphRus.getMorphInfo(word);
//                wordMorphInfo.forEach(System.out::println);
                if (!wordMorphInfo.get(0).matches(MORPH_REGEX)) {
                    add(luceneMorphRus.getNormalForms(word).get(0));
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void add(String lemma) {
        if (!lemmas.containsKey(lemma)) {
            lemmas.put(lemma, 0);
        }
        lemmas.put(lemma, lemmas.get(lemma) + 1);
    }
}
