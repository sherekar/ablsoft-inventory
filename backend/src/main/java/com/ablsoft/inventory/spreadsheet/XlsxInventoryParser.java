package com.ablsoft.inventory.spreadsheet;

import com.ablsoft.inventory.error.InvalidRequestException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class XlsxInventoryParser {
    private static final List<String> HEADERS = List.of(
        "Product SKU", "Product Name", "Category", "Purchase Date", "Unit Price", "Quantity");
    private final Clock clock;
    private final int maxRows;
    private final int maxErrors;

    public XlsxInventoryParser(Clock clock, @Value("${inventory.import.max-rows}") int maxRows,
                               @Value("${inventory.import.max-errors}") int maxErrors) {
        this.clock = clock;
        this.maxRows = maxRows;
        this.maxErrors = maxErrors;
    }

    public List<ImportRow> parse(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new InvalidRequestException("Select a non-empty .xlsx workbook.");
        }
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getWorkbookType() != XSSFWorkbookType.XLSX || workbook.getNumberOfSheets() == 0) {
                throw new InvalidRequestException("Only standard .xlsx workbooks are supported.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeaders(sheet.getRow(0));
            if (sheet.getLastRowNum() > maxRows) {
                throw new InvalidRequestException("The worksheet exceeds the limit of " + maxRows + " data rows.");
            }
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            var rows = new ArrayList<ImportRow>();
            var errors = new ArrayList<RowError>();
            var seen = new HashMap<ImportRow.Key, Integer>();
            int totalErrors = 0;
            LocalDate today = LocalDate.now(clock);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (isEmpty(row, columns.values())) continue;
                try {
                    var item = new ImportRow(index + 1,
                        text(row, columns, "Product SKU", 100, evaluator).toUpperCase(Locale.ROOT),
                        text(row, columns, "Product Name", 200, evaluator),
                        text(row, columns, "Category", 100, evaluator),
                        date(row, columns, evaluator, workbook.isDate1904(), today),
                        price(row, columns, evaluator), quantity(row, columns, evaluator));
                    Integer previous = seen.putIfAbsent(item.key(), item.rowNumber());
                    if (previous != null) {
                        throw new FieldError("Product SKU", "SKU and purchase date duplicate row " + previous + ".");
                    }
                    rows.add(item);
                } catch (FieldError error) {
                    totalErrors++;
                    if (errors.size() < maxErrors) errors.add(new RowError(index + 1, error.column, error.getMessage()));
                }
            }
            if (totalErrors > 0) throw new ImportValidationException(errors, totalErrors);
            if (rows.isEmpty()) throw new InvalidRequestException("The first worksheet has no inventory rows.");
            return rows;
        } catch (InvalidRequestException | ImportValidationException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new InvalidRequestException("The file is not a readable .xlsx workbook. Check that it is not corrupted or password protected.");
        }
    }

    private Map<String, Integer> readHeaders(Row header) {
        if (header == null) throw new InvalidRequestException("The first row must contain the six column headers.");
        var columns = new HashMap<String, Integer>();
        for (Cell cell : header) {
            if (cell.getCellType() != CellType.STRING) continue;
            String value = cell.getStringCellValue().strip();
            for (String required : HEADERS) {
                if (required.equalsIgnoreCase(value) && columns.putIfAbsent(required, cell.getColumnIndex()) != null) {
                    throw new InvalidRequestException("Duplicate column header: " + required + ".");
                }
            }
        }
        List<String> missing = HEADERS.stream().filter(h -> !columns.containsKey(h)).toList();
        if (!missing.isEmpty()) throw new InvalidRequestException("Missing column headers: " + String.join(", ", missing) + ".");
        return columns;
    }

    private boolean isEmpty(Row row, Collection<Integer> columns) {
        if (row == null) return true;
        return columns.stream().allMatch(index -> {
            Cell cell = row.getCell(index);
            return cell == null || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
        });
    }

    private CellValue value(Row row, Map<String, Integer> columns, String column, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(columns.get(column));
        CellValue result;
        try {
            result = cell == null ? null : evaluator.evaluate(cell);
        } catch (RuntimeException error) {
            throw new FieldError(column, "Formula could not be evaluated. Check the function and workbook references.");
        }
        if (result == null || result.getCellType() == CellType.BLANK
            || (result.getCellType() == CellType.STRING && result.getStringValue().isBlank())) {
            throw new FieldError(column, "A value is required.");
        }
        if (result.getCellType() == CellType.ERROR) {
            String code = FormulaError.forInt(result.getErrorValue()).getString();
            throw new FieldError(column, "Excel error " + code + ". Correct the cell or formula.");
        }
        return result;
    }

    private String text(Row row, Map<String, Integer> columns, String column, int limit, FormulaEvaluator evaluator) {
        CellValue result = value(row, columns, column, evaluator);
        if (result.getCellType() != CellType.STRING) {
            throw new FieldError(column, "Use a text cell" + (column.equals("Product SKU") ? " to preserve leading zeros." : "."));
        }
        String text = result.getStringValue().strip();
        if (text.length() > limit) throw new FieldError(column, "Maximum length is " + limit + " characters.");
        return text;
    }

    private BigDecimal number(Row row, Map<String, Integer> columns, String column, FormulaEvaluator evaluator) {
        CellValue result = value(row, columns, column, evaluator);
        if (result.getCellType() != CellType.NUMERIC || !Double.isFinite(result.getNumberValue())) {
            throw new FieldError(column, "Use a numeric cell or a formula that returns a number.");
        }
        BigDecimal number = new BigDecimal(NumberToTextConverter.toText(result.getNumberValue()));
        if (number.signum() < 0) throw new FieldError(column, "Must be zero or greater.");
        return number;
    }

    private BigDecimal price(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator) {
        BigDecimal price = number(row, columns, "Unit Price", evaluator);
        try {
            price = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new FieldError("Unit Price", "Use no more than two decimal places.");
        }
        if (price.precision() > 14) throw new FieldError("Unit Price", "Maximum price is 999999999999.99.");
        return price;
    }

    private int quantity(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator) {
        try {
            return number(row, columns, "Quantity", evaluator).intValueExact();
        } catch (ArithmeticException error) {
            throw new FieldError("Quantity", "Use a whole number between 0 and 2147483647.");
        }
    }

    private LocalDate date(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator,
                           boolean date1904, LocalDate today) {
        String column = "Purchase Date";
        Cell cell = row.getCell(columns.get(column));
        CellValue result = value(row, columns, column, evaluator);
        LocalDate date;
        try {
            if (result.getCellType() == CellType.STRING) {
                date = LocalDate.parse(result.getStringValue().strip());
            } else if (result.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                       && DateUtil.isValidExcelDate(result.getNumberValue())) {
                date = DateUtil.getLocalDateTime(result.getNumberValue(), date1904).toLocalDate();
            } else {
                throw new FieldError(column, "Use YYYY-MM-DD text or an Excel date cell.");
            }
        } catch (DateTimeParseException error) {
            throw new FieldError(column, "Use a valid date in YYYY-MM-DD format.");
        }
        if (date.isAfter(today)) throw new FieldError(column, "Purchase date cannot be in the future.");
        if (date.getYear() < 1900 || date.getYear() > 9999) throw new FieldError(column, "Use a date from 1900 onwards.");
        return date;
    }

    private static class FieldError extends RuntimeException {
        private final String column;
        FieldError(String column, String message) { super(message); this.column = column; }
    }
}
