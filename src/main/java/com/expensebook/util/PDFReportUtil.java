package com.expensebook.util;

import com.expensebook.model.Budget;
import com.expensebook.model.Expense;
import com.expensebook.model.User;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PDFReportUtil {

    public static void generateReport(File targetFile, User user, String periodTitle,
                                      double totalSpent, String topCategory, double topCategoryAmount,
                                      String topPayment, double topPaymentAmount,
                                      double dailyAverage, double prevMonthSpent,
                                      double percentageChange, Budget budget,
                                      List<Expense> recentExpenses) throws IOException {

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float startX = 50;
                float startY = 790;
                float currentY = startY;

                // Header Banner
                cs.setNonStrokingColor(new Color(120, 191, 160)); // #78BFA0 Sage green
                cs.addRect(startX - 10, currentY - 45, 515, 60);
                cs.fill();

                // Header Title
                cs.setNonStrokingColor(Color.WHITE);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 22);
                cs.newLineAtOffset(startX + 10, currentY - 15);
                cs.showText("ExPense Book");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(startX + 10, currentY - 32);
                cs.showText("Track • Analyse • Control | Personal Financial Report");
                cs.endText();

                currentY -= 75;

                // Report Details Box
                cs.setNonStrokingColor(new Color(38, 51, 47)); // #26332F Deep Charcoal
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(startX, currentY);
                cs.showText("Spending Statement: " + periodTitle);
                cs.endText();

                currentY -= 18;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(startX, currentY);
                cs.showText("Prepared for: " + (user != null ? user.getFullName() + " (" + user.getEmail() + ")" : "User"));
                cs.endText();

                currentY -= 25;

                // Key Financial Highlights
                drawHighlightRow(cs, startX, currentY, "Total Expenses:", CurrencyUtil.format(totalSpent), true);
                currentY -= 20;

                drawHighlightRow(cs, startX, currentY, "Daily Average:", CurrencyUtil.format(dailyAverage), false);
                currentY -= 20;

                if (topCategory != null && !topCategory.isEmpty()) {
                    drawHighlightRow(cs, startX, currentY, "Top Category:", topCategory + " (" + CurrencyUtil.format(topCategoryAmount) + ")", false);
                    currentY -= 20;
                }

                if (topPayment != null && !topPayment.isEmpty()) {
                    drawHighlightRow(cs, startX, currentY, "Top Payment Mode:", topPayment + " (" + CurrencyUtil.format(topPaymentAmount) + ")", false);
                    currentY -= 20;
                }

                if (prevMonthSpent > 0) {
                    String changeText = (percentageChange >= 0 ? "+ " : "- ") + String.format("%.1f%%", Math.abs(percentageChange));
                    drawHighlightRow(cs, startX, currentY, "Previous Month Spent:", CurrencyUtil.format(prevMonthSpent) + " | Change: " + changeText, false);
                    currentY -= 20;
                }

                // Budget Information (if available)
                if (budget != null && budget.getTotalBudget() > 0) {
                    currentY -= 10;
                    cs.setNonStrokingColor(new Color(169, 207, 224)); // Powder Blue banner
                    cs.addRect(startX - 5, currentY - 5, 505, 20);
                    cs.fill();

                    cs.setNonStrokingColor(new Color(38, 51, 47));
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                    cs.newLineAtOffset(startX, currentY);
                    cs.showText("MONTHLY BUDGET INSIGHTS");
                    cs.endText();

                    currentY -= 25;
                    drawHighlightRow(cs, startX, currentY, "Allocated Budget:", CurrencyUtil.format(budget.getTotalBudget()), false);
                    currentY -= 18;

                    double remaining = budget.getTotalBudget() - totalSpent;
                    double usedPercent = (totalSpent / budget.getTotalBudget()) * 100.0;
                    drawHighlightRow(cs, startX, currentY, "Remaining Budget:", CurrencyUtil.format(remaining) + " (" + String.format("%.1f%% used", usedPercent) + ")", false);
                    currentY -= 25;
                } else {
                    currentY -= 15;
                }

                // Recent Transactions Table
                cs.setNonStrokingColor(new Color(38, 51, 47));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(startX, currentY);
                cs.showText("Recorded Transactions");
                cs.endText();
                currentY -= 15;

                // Table Header
                cs.setNonStrokingColor(new Color(245, 198, 165)); // Peach header
                cs.addRect(startX, currentY - 5, 495, 18);
                cs.fill();

                cs.setNonStrokingColor(new Color(38, 51, 47));
                cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                cs.beginText();
                cs.newLineAtOffset(startX + 5, currentY);
                cs.showText("Date");
                cs.newLineAtOffset(70, 0);
                cs.showText("Category");
                cs.newLineAtOffset(110, 0);
                cs.showText("Description");
                cs.newLineAtOffset(160, 0);
                cs.showText("Payment Mode");
                cs.newLineAtOffset(90, 0);
                cs.showText("Amount");
                cs.endText();

                currentY -= 18;

                // Table Rows
                cs.setFont(PDType1Font.HELVETICA, 9);
                int count = 0;
                if (recentExpenses != null) {
                    for (Expense exp : recentExpenses) {
                        if (currentY < 60) break; // page limit
                        if (count % 2 == 1) {
                            cs.setNonStrokingColor(new Color(255, 249, 239)); // warm ivory alternating row
                            cs.addRect(startX, currentY - 4, 495, 15);
                            cs.fill();
                        }

                        cs.setNonStrokingColor(new Color(38, 51, 47));
                        cs.beginText();
                        cs.newLineAtOffset(startX + 5, currentY);
                        cs.showText(DateUtil.formatShortDate(exp.getExpenseDate()));
                        cs.newLineAtOffset(70, 0);
                        cs.showText(sanitize(exp.getCategoryName(), 15));
                        cs.newLineAtOffset(110, 0);
                        cs.showText(sanitize(exp.getDescription(), 22));
                        cs.newLineAtOffset(160, 0);
                        cs.showText(exp.getPaymentMode() != null ? exp.getPaymentMode().getDisplayName() : "UPI");
                        cs.newLineAtOffset(90, 0);
                        cs.showText(sanitize(CurrencyUtil.format(exp.getAmount()), 20));
                        cs.endText();

                        currentY -= 16;
                        count++;
                    }
                }

                // Footer
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                cs.setNonStrokingColor(new Color(113, 128, 122));
                cs.beginText();
                cs.newLineAtOffset(startX, 30);
                cs.showText("Generated by ExPense Book - " + DateUtil.formatDate(java.time.LocalDate.now()) + " - Page 1 of 1");
                cs.endText();
            }

            document.save(targetFile);
        }
    }

    private static void drawHighlightRow(PDPageContentStream cs, float x, float y, String label, String value, boolean isBold) throws IOException {
        cs.beginText();
        cs.setNonStrokingColor(new Color(113, 128, 122));
        cs.setFont(PDType1Font.HELVETICA, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(label, 40));
        cs.endText();

        cs.beginText();
        cs.setNonStrokingColor(new Color(38, 51, 47));
        cs.setFont(isBold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, 10);
        cs.newLineAtOffset(x + 160, y);
        cs.showText(sanitize(value, 50));
        cs.endText();
    }

    private static String sanitize(String text, int maxLen) {
        if (text == null) return "-";
        String clean = text.replace("₹", "Rs. ");
        clean = clean.replaceAll("[^\\x20-\\x7E]", ""); // keep printable ascii
        if (clean.length() > maxLen) {
            return clean.substring(0, maxLen - 2) + "..";
        }
        return clean.isEmpty() ? "-" : clean;
    }
}
