package com.anish.ib.api;

import com.anish.ib.dto.SearchResponse;
import com.anish.ib.search.HybridSearchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final HybridSearchService search;

    public SearchController(HybridSearchService search) {
        this.search = search;
    }

    @GetMapping
    public SearchResponse search(@RequestParam(name = "q") String query,
                                 @RequestParam(name = "type", required = false) String type) {
        return search.search(query, type);
    }
}
