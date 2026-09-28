package com.expensebook.controller;

import com.expensebook.model.Expense;
import com.expensebook.service.ExpenseService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import com.expensebook.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class CalendarController implements Initializable {

    @FXML private Label currentMonthYearLabel;
    @FXML private Button btnPrevMonth;
    @FXML private Button btnNextMonth;
    @FXML private GridPane calendarGrid;

    // Selected Day Drawer
    @FXML private Label selectedDateLabel;
    @FXML private Label daySummaryLabel;
    @FXML private VBox dayTransactionsContainer;
    @FXML private ScrollPane dayTransactionsScroll;

    private YearMonth displayedMonth;
    private LocalDate selectedDate;
    private final ExpenseService expenseService = new ExpenseService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        displayedMonth = YearMonth.now();
        selectedDate = LocalDate.now();

        renderCalendar();
        loadSelectedDayDetails();
    }

    @FXML
    private void handlePrevMonth() {
        displayedMonth = displayedMonth.minusMonths(1);
        renderCalendar();
    }

    @FXML
    private void handleNextMonth() {
        displayedMonth = displayedMonth.plusMonths(1);
        renderCalendar();
    }

    private void renderCalendar() {
        calendarGrid.getChildren().clear();
        currentMonthYearLabel.setText(DateUtil.formatMonthYear(displayedMonth.getMonthValue(), displayedMonth.getYear()));

        int userId = SessionManager.getCurrentUserId();
        Map<Integer, Double> dailySpending;
        try {
            dailySpending = expenseService.getDailySpending(userId, displayedMonth.getMonthValue(), displayedMonth.getYear());
        } catch (Exception e) {
            dailySpending = Map.of();
        }

        // Headers (Sun - Sat)
        String[] daysOfWeek = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int col = 0; col < 7; col++) {
            Label header = new Label(daysOfWeek[col]);
            header.setStyle("-fx-font-weight: bold; -fx-text-fill: #71807A; -fx-padding: 6 0 6 0;");
            header.setMaxWidth(Double.MAX_VALUE);
            header.setAlignment(Pos.CENTER);
            calendarGrid.add(header, col, 0);
        }

        LocalDate firstDayOfMonth = displayedMonth.atDay(1);
        int dayOfWeekValue = firstDayOfMonth.getDayOfWeek().getValue() % 7; // Sunday = 0, Monday = 1 ...

        int totalDays = displayedMonth.lengthOfMonth();
        int row = 1;
        int col = dayOfWeekValue;

        LocalDate today = LocalDate.now();

        for (int day = 1; day <= totalDays; day++) {
            LocalDate date = displayedMonth.atDay(day);
            double spent = dailySpending.getOrDefault(day, 0.0);

            VBox cell = new VBox(4);
            cell.setAlignment(Pos.TOP_CENTER);
            cell.setPadding(new Insets(8, 4, 8, 4));
            cell.setPrefSize(75, 75);
            cell.setMinSize(60, 60);

            boolean isToday = date.equals(today);
            boolean isSelected = date.equals(selectedDate);

            // Styling cell
            String style = "-fx-background-radius: 14px; -fx-cursor: hand; ";
            if (isSelected) {
                style += "-fx-background-color: #E6F3EE; -fx-border-color: #78BFA0; -fx-border-width: 2px; -fx-border-radius: 14px;";
            } else if (isToday) {
                style += "-fx-background-color: #F8F6F0; -fx-border-color: #E2DBD0; -fx-border-width: 1.5px; -fx-border-radius: 14px;";
            } else {
                style += "-fx-background-color: #FFFFFF; -fx-border-color: #F4EFE6; -fx-border-width: 1px; -fx-border-radius: 14px;";
            }
            cell.setStyle(style);

            Label dayNumLbl = new Label(String.valueOf(day));
            dayNumLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + (isSelected ? "#2E6950" : "#26332F") + ";");
            cell.getChildren().add(dayNumLbl);

            if (spent > 0) {
                Label amtBadge = new Label(CurrencyUtil.formatCompact(spent));
                amtBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-color: #FDF1E8; " +
                        "-fx-text-fill: #995A2A; -fx-background-radius: 8px; -fx-padding: 2 6 2 6;");
                cell.getChildren().add(amtBadge);
            }

            final LocalDate finalDate = date;
            cell.setOnMouseClicked(e -> {
                selectedDate = finalDate;
                renderCalendar();
                loadSelectedDayDetails();
            });

            calendarGrid.add(cell, col, row);

            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
    }

    private void loadSelectedDayDetails() {
        if (selectedDate == null) return;

        selectedDateLabel.setText(DateUtil.formatDate(selectedDate));

        int userId = SessionManager.getCurrentUserId();
        try {
            List<Expense> expenses = expenseService.getExpensesForDate(userId, selectedDate);
            double totalDaySpent = 0;
            for (Expense exp : expenses) {
                totalDaySpent += exp.getAmount();
            }

            daySummaryLabel.setText(expenses.size() + " transactions • Total: " + CurrencyUtil.format(totalDaySpent));

            dayTransactionsContainer.getChildren().clear();
            if (expenses.isEmpty()) {
                Label noExp = new Label("No expenses recorded on this day.");
                noExp.getStyleClass().add("text-muted");
                noExp.setPadding(new Insets(14, 0, 14, 0));
                dayTransactionsContainer.getChildren().add(noExp);
                return;
            }

            for (Expense exp : expenses) {
                HBox card = new HBox(10);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(8, 10, 8, 10));
                card.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 12px; -fx-border-color: #F0EAE1; -fx-border-radius: 12px;");

                card.getChildren().add(SVGIconUtil.createCategoryBadge(exp.getCategoryIcon(), exp.getCategoryColor(), 28));

                VBox details = new VBox(2);
                HBox.setHgrow(details, Priority.ALWAYS);

                Label titleLbl = new Label(!exp.getDescription().isEmpty() ? exp.getDescription() : exp.getCategoryName());
                titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");

                Label modeLbl = new Label(exp.getCategoryName() + " • " +
                        (exp.getPaymentMode() != null ? exp.getPaymentMode().getDisplayName() : "UPI"));
                modeLbl.getStyleClass().add("text-muted");
                details.getChildren().addAll(titleLbl, modeLbl);

                Label amtLbl = new Label(CurrencyUtil.format(exp.getAmount()));
                amtLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #26332F;");

                card.getChildren().addAll(details, amtLbl);
                dayTransactionsContainer.getChildren().add(card);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
