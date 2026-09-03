package com.ablsoft.inventory.spreadsheet;

import static com.ablsoft.inventory.WorkbookFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.ablsoft.inventory.error.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

class XlsxInventoryParserTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
    private final XlsxInventoryParser parser = new XlsxInventoryParser(clock, 10000, 100);

    @Test
    void normalizesSkuAndPreservesLeadingZeros() throws Exception {
        var file = workbook(w -> w.getSheetAt(0).getRow(1).getCell(0).setCellValue(" 001-a "));
        var row = parser.parse(file).getFirst();
        assertThat(row.sku()).isEqualTo("001-A");
        assertThat(row.unitPrice()).isEqualByComparingTo("12.50");
        assertThat(row.purchaseDate()).isEqualTo(LocalDate.of(2026, 8, 24));
    }

    @Test
    void evaluatesFormulaInsteadOfUsingStaleCachedValue() throws Exception {
        var file = workbook(w -> {
            var row = w.getSheetAt(0).getRow(1);
            row.getCell(4).setCellFormula("F2*6.25");
            w.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(row.getCell(4));
            row.getCell(5).setCellValue(4);
        });
        assertThat(parser.parse(file).getFirst().unitPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    void acceptsAnExcelDateAndFormulaQuantity() throws Exception {
        var file = workbook(w -> {
            var row = w.getSheetAt(0).getRow(1);
            var style = w.createCellStyle();
            style.setDataFormat(w.createDataFormat().getFormat("yyyy-mm-dd"));
            row.getCell(3).setCellValue(LocalDate.of(2026, 8, 24));
            row.getCell(3).setCellStyle(style);
            row.getCell(5).setCellFormula("SUM(2,3)");
        });
        assertThat(parser.parse(file).getFirst().quantity()).isEqualTo(5);
    }

    @Test
    void rejectsCaseInsensitiveDuplicatesWithinWorkbook() throws Exception {
        var file = workbook(w -> addRow(w, 2, "001-a", "2026-08-24", 9, 1));
        assertThatThrownBy(() -> parser.parse(file)).isInstanceOfSatisfying(ImportValidationException.class,
            error -> assertThat(error.getErrors()).containsExactly(new RowError(3, "Product SKU", "SKU and purchase date duplicate row 2.")));
    }

    @Test
    void skipsEmptyRowsButRejectsPartialRows() throws Exception {
        var file = workbook(w -> {
            w.getSheetAt(0).createRow(2);
            w.getSheetAt(0).createRow(3).createCell(0).setCellValue("PARTIAL");
        });
        assertThatThrownBy(() -> parser.parse(file)).isInstanceOfSatisfying(ImportValidationException.class,
            error -> assertThat(error.getErrors().getFirst()).isEqualTo(new RowError(4, "Product Name", "A value is required.")));
    }

    @ParameterizedTest
    @CsvSource({"4,-1,Unit Price", "4,1.999,Unit Price", "5,1.5,Quantity", "5,-2,Quantity", "5,2147483648,Quantity", "0,123,Product SKU"})
    void rejectsInvalidNumericValues(int column, double value, String name) throws Exception {
        var file = workbook(w -> w.getSheetAt(0).getRow(1).getCell(column).setCellValue(value));
        assertThatThrownBy(() -> parser.parse(file)).isInstanceOfSatisfying(ImportValidationException.class,
            error -> assertThat(error.getErrors().getFirst().column()).isEqualTo(name));
    }

    @ParameterizedTest
    @CsvSource({"2026-02-30", "03/09/2026", "2026-09-04"})
    void rejectsInvalidOrFutureDates(String date) throws Exception {
        var file = workbook(w -> w.getSheetAt(0).getRow(1).getCell(3).setCellValue(date));
        assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(ImportValidationException.class);
    }

    @Test
    void reportsFormulaErrorsAtTheCell() throws Exception {
        var file = workbook(w -> w.getSheetAt(0).getRow(1).getCell(4).setCellFormula("1/0"));
        assertThatThrownBy(() -> parser.parse(file)).isInstanceOfSatisfying(ImportValidationException.class,
            error -> assertThat(error.getErrors().getFirst().message()).contains("#DIV/0!"));
    }

    @Test
    void reportsUnsupportedFormula() throws Exception {
        var file = workbook(w -> w.getSheetAt(0).getRow(1).getCell(4).setCellFormula("FOO(1)"));
        assertThatThrownBy(() -> parser.parse(file)).isInstanceOfSatisfying(ImportValidationException.class,
            error -> assertThat(error.getErrors().getFirst().column()).isEqualTo("Unit Price"));
    }

    @Test
    void requiresAllHeadersAndRejectsDuplicateHeader() throws Exception {
        var missing = workbook(w -> w.getSheetAt(0).getRow(0).getCell(0).setBlank());
        assertThatThrownBy(() -> parser.parse(missing)).isInstanceOf(InvalidRequestException.class).hasMessageContaining("Product SKU");
        var duplicate = workbook(w -> w.getSheetAt(0).getRow(0).createCell(6).setCellValue("Product SKU"));
        assertThatThrownBy(() -> parser.parse(duplicate)).isInstanceOf(InvalidRequestException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsSpoofedAndEmptyFiles() {
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("file", "fake.xlsx", "application/octet-stream", new byte[]{1, 2})))
            .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0])))
            .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsEmptyWorksheetAndLimitsRowsAndErrorResponse() throws Exception {
        var empty = workbook(w -> w.getSheetAt(0).removeRow(w.getSheetAt(0).getRow(1)));
        assertThatThrownBy(() -> parser.parse(empty)).isInstanceOf(InvalidRequestException.class).hasMessageContaining("no inventory rows");
        var tooMany = workbook(w -> addRow(w, 2, "B", "2026-08-01", 1, 1));
        assertThatThrownBy(() -> new XlsxInventoryParser(clock, 1, 1).parse(tooMany)).isInstanceOf(InvalidRequestException.class);
        var invalid = workbook(w -> {
            w.getSheetAt(0).getRow(1).getCell(4).setCellValue(-1);
            addRow(w, 2, "B", "2026-08-01", -1, 1);
        });
        assertThatThrownBy(() -> new XlsxInventoryParser(clock, 10, 1).parse(invalid))
            .isInstanceOfSatisfying(ImportValidationException.class, e -> {
                assertThat(e.getTotalErrors()).isEqualTo(2);
                assertThat(e.getErrors()).hasSize(1);
            });
    }
}
