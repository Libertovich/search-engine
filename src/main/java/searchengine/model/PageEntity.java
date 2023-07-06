package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "page", indexes = @Index(name = "path_idx", columnList = "path, site_id" /*, unique = true*/))
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY) //, cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id", nullable = false)
//    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteEntity siteId;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
