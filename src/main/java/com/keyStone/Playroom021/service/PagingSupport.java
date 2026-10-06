package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Shared paging, sorting and search-term helpers for the Customer and Site services. */
final class PagingSupport {

    static final int MAX_PAGE_SIZE = 100;

    private PagingSupport() {
    }

    /**
     * Builds a validated PageRequest. Sort fields are checked against a whitelist so a
     * client can never sort by an arbitrary property (which would surface as a 500).
     */
    static Pageable pageable(int page, int size, String sortBy, String direction,
                             Set<String> allowedSortFields, String defaultSortField) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String field = (sortBy == null || sortBy.isBlank()) ? defaultSortField : sortBy.trim();
        if (!allowedSortFields.contains(field)) {
            throw new InvalidRequestException("sortBy must be one of " + new TreeSet<>(allowedSortFields));
        }
        Sort.Direction dir;
        if (direction == null || direction.isBlank() || direction.equalsIgnoreCase("asc")) {
            dir = Sort.Direction.ASC;
        } else if (direction.equalsIgnoreCase("desc")) {
            dir = Sort.Direction.DESC;
        } else {
            throw new InvalidRequestException("direction must be 'asc' or 'desc'");
        }
        // id is added as a tiebreaker so paging is stable when many rows share the sort value.
        Sort sort = Sort.by(dir, field);
        if (!"id".equals(field)) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(page, size, sort);
    }

    /**
     * Turns a user search term into a lower-cased LIKE pattern ("%term%") with the LIKE
     * wildcards in the term itself escaped, so searching for "50%" matches a literal
     * "50%" rather than everything. Returns null when there is no search term.
     * Pair with an escape character of '\\'.
     */
    static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
