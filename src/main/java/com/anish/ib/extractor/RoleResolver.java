package com.anish.ib.extractor;

import com.anish.ib.domain.Role;
import com.anish.ib.repository.RoleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class RoleResolver {
    private final RoleRepository roleRepo;

    public RoleResolver(RoleRepository roleRepo) {
        this.roleRepo = roleRepo;
    }

    @Transactional
    public Role resolve(String rawRole, String seniority) {
        String needle = normalize(rawRole);
        List<Role> all = roleRepo.findAll();
        for (Role r : all) {
            if (r.getCanonicalName().equalsIgnoreCase(needle) || r.getSlug().equalsIgnoreCase(needle)) return r;
            for (String alias : r.getAliases()) {
                if (alias.equalsIgnoreCase(needle)) return r;
                if (needle.contains(alias)) return r;
            }
        }
        // seniority-informed fallback
        if ("senior".equalsIgnoreCase(seniority)) {
            return findBySlug(all, "senior-sde").orElse(findBySlug(all, "sde-3").orElse(fallback(all)));
        }
        if ("lead".equalsIgnoreCase(seniority)) {
            return findBySlug(all, "tech-lead").orElse(fallback(all));
        }
        if ("entry".equalsIgnoreCase(seniority)) {
            return findBySlug(all, "sde-1").orElse(fallback(all));
        }
        return findBySlug(all, "sde-2").orElse(fallback(all));
    }

    private static Optional<Role> findBySlug(List<Role> all, String slug) {
        return all.stream().filter(r -> r.getSlug().equals(slug)).findFirst();
    }

    private static Role fallback(List<Role> all) {
        return all.isEmpty() ? null : all.get(0);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }
}
