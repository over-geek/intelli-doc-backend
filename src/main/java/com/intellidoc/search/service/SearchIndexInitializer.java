package com.intellidoc.search.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexInitializer implements ApplicationRunner {

    private final SearchIndexManagementService searchIndexManagementService;

    public SearchIndexInitializer(SearchIndexManagementService searchIndexManagementService) {
        this.searchIndexManagementService = searchIndexManagementService;
    }

    @Override
    public void run(ApplicationArguments args) {
        searchIndexManagementService.ensureChunkIndex();
    }
}
