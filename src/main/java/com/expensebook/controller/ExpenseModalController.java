package com.expensebook.controller;

import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.model.PaymentMode;
import com.expensebook.service.CategoryService;
import com.expensebook.service.ExpenseService;
import com.expensebook.util.SessionManager;
import com.expensebook.util.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class ExpenseModalController implements Initializable {

    @FXML private Label modalTitle;
    @FXML private TextField amountField;
    @FXML private ComboBox<Category> categoryComboBox;
    @FXML private ComboBox<PaymentMode> paymentModeComboBox;
    @FXML private DatePicker datePicker;
    @FXML private TextField descriptionField;
    @FXML private Label errorLabel;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private final ExpenseService expenseService = new ExpenseService();
    private final CategoryService categoryService = new CategoryService();
    private Expense expenseToEdit;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        errorLabel.setVisible(false);
        datePicker.setValue(LocalDate.now());

        // Load Categories
        try {
            int userId = SessionManager.getCurrentUserId();
            List<Category> categories = categoryService.getCategoriesForUser(userId);
            categoryComboBox.setItems(FXCollections.observableArrayList(categories));

            categoryComboBox.setCellFactory(lv -> new ListCell<Category>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            categoryComboBox.setButtonCell(new ListCell<Category>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });

            if (!categories.isEmpty()) {
                categoryComboBox.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load Payment Modes
        paymentModeComboBox.setItems(FXCollections.observableArrayList(PaymentMode.values()));
        paymentModeComboBox.getSelectionModel().select(PaymentMode.UPI);
    }

    public void setExpenseToEdit(Expense expense) {
        this.expenseToEdit = expense;
        modalTitle.setText("Edit Expense");
        btnSave.setText("Update Expense");

        amountField.setText(String.format("%.2f", expense.getAmount()));
        datePicker.setValue(expense.getExpenseDate());
        descriptionField.setText(expense.getDescription());
        paymentModeComboBox.getSelectionModel().select(expense.getPaymentMode());

        for (Category c : categoryComboBox.getItems()) {
            if (c.getId() == expense.getCategoryId()) {
                categoryComboBox.getSelectionModel().select(c);
                break;
            }
        }
    }

    @FXML
    private void handleSave() {
        errorLabel.setVisible(false);

        String amountStr = amountField.getText();
        if (!ValidationUtil.isValidAmount(amountStr)) {
            showError("Please enter a valid amount greater than ₹0.");
            return;
        }

        Category selectedCategory = categoryComboBox.getValue();
        if (selectedCategory == null) {
            showError("Please select a category.");
            return;
        }

        PaymentMode selectedPayment = paymentModeComboBox.getValue();
        if (selectedPayment == null) {
            showError("Please select a payment method.");
            return;
        }

        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate == null) {
            showError("Please select a date.");
            return;
        }

        double amount = Double.parseDouble(amountStr.trim());
        String description = descriptionField.getText();

        try {
            int userId = SessionManager.getCurrentUserId();

            if (expenseToEdit == null) {
                // New Expense
                expenseService.createExpense(
                        userId,
                        selectedCategory.getId(),
                        amount,
                        selectedPayment,
                        selectedDate,
                        description
                );
            } else {
                // Update Expense
                expenseToEdit.setCategoryId(selectedCategory.getId());
                expenseToEdit.setAmount(amount);
                expenseToEdit.setPaymentMode(selectedPayment);
                expenseToEdit.setExpenseDate(selectedDate);
                expenseToEdit.setDescription(description);
                expenseService.updateExpense(expenseToEdit);
            }

            closeWindow();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void closeWindow() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}
