package com.expensebook.controller;

import com.expensebook.model.Income;
import com.expensebook.service.ExpenseService;
import com.expensebook.service.IncomeService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SessionManager;
import com.expensebook.util.ValidationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class IncomeController implements Initializable {

    // Filter selectors
    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> yearCombo;

    // Summary Cards
    @FXML private Label totalIncomeLabel;
    @FXML private Label totalExpensesLabel;
    @FXML private Label netBalanceLabel;
    @FXML private Label netBalanceCardTag;

    // Add Income Form
    @FXML private TextField amountField;
    @FXML private ComboBox<String> sourceCombo;
    @FXML private DatePicker datePicker;
    @FXML private TextField descriptionField;
    @FXML private Button btnAddIncome;

    // Income Table
    @FXML private TableView<Income> incomeTable;
    @FXML private TableColumn<Income, String> colDate;
    @FXML private TableColumn<Income, String> colSource;
    @FXML private TableColumn<Income, String> colDescription;
    @FXML private TableColumn<Income, String> colAmount;
    @FXML private TableColumn<Income, Void> colActions;

    private final IncomeService incomeService = new IncomeService();
    private final ExpenseService expenseService = new ExpenseService();
    private final ObservableList<Income> incomeData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupForm();
        setupTable();
        loadIncomeData();
    }

    private void setupForm() {
        // Month / Year
        for (int m = 1; m <= 12; m++) {
            monthCombo.getItems().add(Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        int curMonth = LocalDate.now().getMonthValue();
        monthCombo.getSelectionModel().select(curMonth - 1);

        int curYear = LocalDate.now().getYear();
        for (int y = curYear - 2; y <= curYear + 1; y++) {
            yearCombo.getItems().add(y);
        }
        yearCombo.getSelectionModel().select(Integer.valueOf(curYear));

        monthCombo.setOnAction(e -> loadIncomeData());
        yearCombo.setOnAction(e -> loadIncomeData());

        // Sources
        sourceCombo.setItems(FXCollections.observableArrayList(
                "Salary", "Freelancing", "Allowance", "Business", "Investments", "Other"
        ));
        sourceCombo.getSelectionModel().selectFirst();

        datePicker.setValue(LocalDate.now());
    }

    private void setupTable() {
        colDate.setCellValueFactory(data -> new SimpleStringProperty(DateUtil.formatDate(data.getValue().getIncomeDate())));
        colSource.setCellValueFactory(new PropertyValueFactory<>("source"));
        colSource.setCellFactory(col -> new TableCell<Income, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().addAll("badge", "badge-sage");
                    setGraphic(badge);
                }
            }
        });

        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colAmount.setCellValueFactory(data -> new SimpleStringProperty("+ " + CurrencyUtil.format(data.getValue().getAmount())));
        colAmount.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #397A5E;");

        colActions.setCellFactory(col -> new TableCell<Income, Void>() {
            private final Button btnDelete = new Button("Delete");
            {
                btnDelete.getStyleClass().addAll("btn-danger", "btn-pill-small");
                btnDelete.setOnAction(e -> {
                    Income item = getTableView().getItems().get(getIndex());
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Delete Income");
                    alert.setHeaderText("Delete income of " + CurrencyUtil.format(item.getAmount()) + "?");
                    alert.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.OK) {
                            try {
                                incomeService.deleteIncome(item.getId(), SessionManager.getCurrentUserId());
                                loadIncomeData();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDelete);
                setAlignment(Pos.CENTER);
            }
        });

        incomeTable.setItems(incomeData);
    }

    public void loadIncomeData() {
        int userId = SessionManager.getCurrentUserId();
        if (userId <= 0) return;

        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearCombo.getValue();
        if (year == null) year = LocalDate.now().getYear();

        try {
            List<Income> list = incomeService.getIncomeForMonth(userId, month, year);
            incomeData.setAll(list);

            double totalIncome = incomeService.getTotalIncomeForMonth(userId, month, year);
            double totalExpenses = expenseService.getTotalSpentMonth(userId, month, year);
            double balance = incomeService.calculateNetBalance(totalIncome, totalExpenses);

            totalIncomeLabel.setText(CurrencyUtil.format(totalIncome));
            totalExpensesLabel.setText(CurrencyUtil.format(totalExpenses));
            netBalanceLabel.setText((balance >= 0 ? "+ " : "- ") + CurrencyUtil.format(Math.abs(balance)));

            if (balance >= 0) {
                netBalanceLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #397A5E;");
                netBalanceCardTag.setText("🟢 Surplus Savings");
            } else {
                netBalanceLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #C94A4A;");
                netBalanceCardTag.setText("🔴 Deficit Spending");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddIncome() {
        String amtStr = amountField.getText();
        if (!ValidationUtil.isValidAmount(amtStr)) {
            showAlert("Invalid Amount", "Please enter a valid positive income amount.");
            return;
        }

        String source = sourceCombo.getValue();
        LocalDate date = datePicker.getValue();
        String desc = descriptionField.getText();

        try {
            double amount = Double.parseDouble(amtStr.trim());
            int userId = SessionManager.getCurrentUserId();

            incomeService.addIncome(userId, source, amount, date, desc);

            amountField.clear();
            descriptionField.clear();
            datePicker.setValue(LocalDate.now());

            loadIncomeData();
            showAlert("Success", "Income recorded successfully!");

        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
