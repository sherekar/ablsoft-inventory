package com.ablsoft.inventory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

public final class WorkbookFixtures {
    private WorkbookFixtures() { }

    public static MockMultipartFile workbook(Consumer<XSSFWorkbook> customize) throws IOException {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Inventory");
            var header = sheet.createRow(0);
            String[] columns = {"Product SKU", "Product Name", "Category", "Purchase Date", "Unit Price", "Quantity"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            addRow(workbook, 1, "001-A", "2026-08-24", 12.50, 2);
            customize.accept(workbook);
            workbook.write(bytes);
            return new MockMultipartFile("file", "inventory.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    public static void addRow(XSSFWorkbook workbook, int index, String sku, String date, double price, double quantity) {
        var row = workbook.getSheetAt(0).createRow(index);
        row.createCell(0).setCellValue(sku);
        row.createCell(1).setCellValue("Desk lamp");
        row.createCell(2).setCellValue("Lighting");
        row.createCell(3).setCellValue(date);
        row.createCell(4).setCellValue(price);
        row.createCell(5).setCellValue(quantity);
    }
}
