package com.reviewsales.menu;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "pos_name", nullable = false)
    private String posName;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "aliases", columnDefinition = "text[]", nullable = false)
    private List<String> aliases = new ArrayList<>();

    protected Menu() {
    }

    public Menu(Long storeId, String posName, List<String> aliases) {
        this.storeId = storeId;
        rename(posName);
        setAliases(aliases);
    }

    public void rename(String posName) {
        this.posName = posName.strip();
        this.normalizedName = MenuNameNormalizer.normalize(posName);
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases == null ? new ArrayList<>()
                : new ArrayList<>(aliases.stream().map(String::strip).filter(a -> !a.isEmpty()).distinct().toList());
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getPosName() {
        return posName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
