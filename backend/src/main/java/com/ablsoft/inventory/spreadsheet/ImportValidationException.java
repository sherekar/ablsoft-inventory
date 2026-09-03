package com.ablsoft.inventory.spreadsheet;

import java.util.List;

public class ImportValidationException extends RuntimeException {
    private final List<RowError> errors;
    private final int totalErrors;

    public ImportValidationException(List<RowError> errors, int totalErrors) {
        super("Import rejected. Correct the errors and try again. No rows were saved.");
        this.errors = List.copyOf(errors);
        this.totalErrors = totalErrors;
    }
    public List<RowError> getErrors() { return errors; }
    public int getTotalErrors() { return totalErrors; }
}
