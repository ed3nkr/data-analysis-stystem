package com.reviewsales.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 매출의 상품명을 등록된 메뉴에 연결한다.
 * 정규화 이름이 메뉴의 normalized_name 또는 (정규화한) aliases 중 하나와 같으면 매칭.
 * normalized_name 일치가 alias 일치보다 우선한다.
 */
public class MenuMatcher {

    private final Map<String, Long> index = new HashMap<>();

    public MenuMatcher(List<Menu> menus) {
        for (Menu m : menus) {
            for (String alias : m.getAliases()) {
                String key = MenuNameNormalizer.normalize(alias);
                if (!key.isEmpty()) {
                    index.putIfAbsent(key, m.getId());
                }
            }
        }
        for (Menu m : menus) {
            index.put(m.getNormalizedName(), m.getId());
        }
    }

    /** @return 매칭된 menu id, 없으면 null */
    public Long match(String menuName) {
        String key = MenuNameNormalizer.normalize(menuName);
        return key.isEmpty() ? null : index.get(key);
    }
}
