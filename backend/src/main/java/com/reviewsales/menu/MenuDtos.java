package com.reviewsales.menu;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MenuDtos {

    private MenuDtos() {
    }

    public record CreateRequest(@NotBlank @Size(max = 200) String posName, List<@Size(max = 200) String> aliases) {
    }

    public record UpdateRequest(@Size(min = 1, max = 200) String posName, List<@Size(max = 200) String> aliases) {
    }

    public record MenuResponse(Long menuId, String posName, String normalizedName, List<String> aliases) {
        static MenuResponse of(Menu m) {
            return new MenuResponse(m.getId(), m.getPosName(), m.getNormalizedName(), List.copyOf(m.getAliases()));
        }
    }
}
