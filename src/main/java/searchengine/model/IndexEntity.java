package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "`index`")
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity pageId;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemmaId;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
