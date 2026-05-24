package com.mentalhealth.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

// Value object carrying the filter/sort criteria the user selects in
// HistoryController. Passed to HistoryService which translates it into
// a parameterised SQL WHERE clause.
public class HistoryFilter {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> moods;
    private Double minConfidence;
    private Double maxConfidence;
    private List<String> severities;
    private String searchText;
    private String sortBy = "date_desc"; // date_desc | date_asc | confidence_desc | mood
    private boolean showStarredOnly = false;
    private List<String> tags;

    public HistoryFilter() {}

    public HistoryFilter(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public List<String> getMoods() { return moods; }
    public void setMoods(List<String> moods) { this.moods = moods; }

    public Double getMinConfidence() { return minConfidence; }
    public void setMinConfidence(Double minConfidence) { this.minConfidence = minConfidence; }

    public Double getMaxConfidence() { return maxConfidence; }
    public void setMaxConfidence(Double maxConfidence) { this.maxConfidence = maxConfidence; }

    public List<String> getSeverities() { return severities; }
    public void setSeverities(List<String> severities) { this.severities = severities; }

    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public boolean isShowStarredOnly() { return showStarredOnly; }
    public void setShowStarredOnly(boolean showStarredOnly) { this.showStarredOnly = showStarredOnly; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryFilter that = (HistoryFilter) o;
        return showStarredOnly == that.showStarredOnly
                && Objects.equals(startDate, that.startDate)
                && Objects.equals(endDate, that.endDate)
                && Objects.equals(sortBy, that.sortBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startDate, endDate, sortBy, showStarredOnly);
    }

    @Override
    public String toString() {
        return "HistoryFilter{startDate=" + startDate + ", endDate=" + endDate + ", sortBy='" + sortBy + "'}";
    }
}
