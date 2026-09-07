package com.anish.ib.dto;

import java.util.List;

public record SearchResponse(String query, int total, List<QuestionDto> results) {}
