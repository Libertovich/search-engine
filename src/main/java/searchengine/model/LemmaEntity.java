package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "lemma")
public class LemmaEntity implements Comparable<LemmaEntity> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity siteId;

    @Column(name = "lemma", nullable = false) //unique=true добавить?
    private String lemma;

    // количество страниц, на которых слово встречается хотя бы один раз.
    // Максимальное значение не может превышать общее количество слов (???, может страниц?) на сайте
    @Column(name = "frequency", nullable = false)
    private int frequency;

    @Override
    public int compareTo(LemmaEntity other) {
        int result = this.lemma.compareTo(other.getLemma());
        if (result == 0) {
            result = this.siteId.getId().compareTo(other.getSiteId().getId());
        }
        return result;
    }
}
