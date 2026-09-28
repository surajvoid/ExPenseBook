package com.expensebook.controller;

import com.expensebook.model.Category;
import com.expensebook.model.PaymentMode;
import com.expensebook.model.RecurringExpense;
import com.expensebook.service.CategoryService;
import com.expensebook.service.ExpenseService;
import com.expensebook.service.RecurringService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import com.expensebook.util.SessionManager;
import com.expensebook.util.ValidationUtil;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class RecurringController implements Initializable {

    // Add Form
    @FXML private TextField titleField;
    @FXML private TextField amountField;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private ComboBox<String> frequencyCombo;
    @FXML private ComboBox<PaymentMode> paymentModeCombo;
    @FXML private DatePicker nextDatePicker;
    @FXML private Button btnAddRecurring;

    // Table
    @FXML private TableView<RecurringExpense> recurringTable;
    @FXML private TableColumn<RecurringExpense, RecurringExpense> colTitle;
    @FXML private TableColumn<RecurringExpense, String> colFrequency;
    @FXML private TableColumn<RecurringExpense, String> colNextDue;
    @FXML private TableColumn<RecurringExpense, String> colAmount;
    @FXML private TableColumn<RecurringExpense, Void> colActions;

    private final RecurringService recurringService = new RecurringService();
    private final CategoryService categoryService = new CategoryService();
    private final ExpenseService expenseService = new ExpenseService();
    private final ObservableList<RecurringExpense> tableData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupForm();
        setupTable();
        loadRecurringList();
    }

    private void setupForm() {
        int userId = SessionManager.getCurrentUserId();
        try {
            List<Category> cats = categoryService.getCategoriesForUser(userId);
            categoryCombo.setItems(FXCollections.observableArrayList(cats));
            categoryCombo.setCellFactory(lv -> new ListCell<Category>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            categoryCombo.setButtonCell(new ListCell<Category>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            if (!cats.isEmpty()) categoryCombo.getSelectionModel().selectFirst();
        } catch (Exception e) {
            e.printStackTrace();
        }

        frequencyCombo.setItems(FXCollections.observableArrayList("MONTHLY", "WEEKLY", "YEARLY", "DAILY"));
        frequencyCombo.getSelectionModel().selectFirst();

        paymentModeCombo.setItems(FXCollections.observableArrayList(PaymentMode.values()));
        paymentModeCombo.getSelectionModel().select(PaymentMode.UPI);

        nextDatePicker.setValue(LocalDate.now().plusMonths(1));
    }

    private void setupTable() {
        colTitle.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colTitle.setCellFactory(col -> new TableCell<RecurringExpense, RecurringExpense>() {
            @Override
            protected void updateItem(RecurringExpense item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.getChildren().add(SVGIconUtil.createCategoryBadge(item.getCategoryIcon(), item.getCategoryColor(), 24));
                    Label title = new Label(item.getTitle());
                    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
                    box.getChildren().add(title);
                    setGraphic(box);
                }
            }
        });

        colFrequency.setCellValueFactory(new PropertyValueFactory<>("frequency"));
        colFrequency.setCellFactory(col -> new TableCell<RecurringExpense, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label b = new Label(item);
                    b.getStyleClass().addAll("badge", "badge-blue");
                    setGraphic(b);
                }
            }
        });

        colNextDue.setCellValueFactory(data -> {
            RecurringExpense re = data.getValue();
            String dateText = DateUtil.formatDate(re.getNextDueDate());
            if (re.isDueOrOverdue()) {
                dateText += " (Due)";
            }
            return new javafx.beans.property.SimpleStringProperty(dateText);
        });

        colAmount.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(CurrencyUtil.format(data.getValue().getAmount())));
        colAmount.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");

        colActions.setCellFactory(col -> new TableCell<RecurringExpense, Void>() {
            private final Button btnProcess = new Button("Log Now");
            private final Button btnDelete = new Button("Delete");
            private final HBox pane = new HBox(6, btnProcess, btnDelete);
            {
                pane.setAlignment(Pos.CENTER);
                btnProcess.getStyleClass().addAll("btn-primary", "btn-pill-small");
                btnDelete.getStyleClass().addAll("btn-danger", "btn-pill-small");

                btnProcess.setOnAction(e -> {
                    RecurringExpense item = getTableView().getItems().get(getIndex());
                    try {
                        recurringService.processDueRecurringExpense(item, expenseService);
                        loadRecurringList();
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Expense Logged");
                        alert.setHeaderText("Successfully recorded " + item.getTitle());
                        alert.setContentText("Added " + CurrencyUtil.format(item.getAmount()) + " to expenses. Next due date updated.");
                        alert.showAndWait();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

                btnDelete.setOnAction(e -> {
                    RecurringExpense item = getTableView().getItems().get(getIndex());
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Delete Recurring Expense");
                    alert.setHeaderText("Delete '" + item.getTitle() + "'?");
                    alert.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.OK) {
                            try {
                                recurringService.deleteRecurring(item.getId(), SessionManager.getCurrentUserId());
                                loadRecurringList();
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
                setGraphic(empty ? null : pane);
            }
        });

        recurringTable.setItems(tableData);
    }

    public void loadRecurringList() {
        int userId = SessionManager.getCurrentUserId();
        if (userId <= 0) return;
        try {
            List<RecurringExpense> list = recurringService.getRecurringList(userId);
            tableData.setAll(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddRecurring() {
        String title = titleField.getText();
        String amtStr = amountField.getText();
        Category cat = categoryCombo.getValue();
        String freq = frequencyCombo.getValue();
        PaymentMode mode = paymentModeCombo.getValue();
        LocalDate nextDate = nextDatePicker.getValue();

        if (!ValidationUtil.isNonEmpty(title)) {
            showAlert("Invalid Title", "Please enter a recurring expense title (e.g. Netflix, Rent).");
            return;
        }
        if (!ValidationUtil.isValidAmount(amtStr)) {
            showAlert("Invalid Amount", "Please enter a valid positive amount.");
            return;
        }
        if (cat == null) {
            showAlert("Category Required", "Please select a category.");
            return;
        }
        if (nextDate == null) {
            showAlert("Due Date Required", "Please pick a starting due date.");
            return;
        }

        try {
            double amount = Double.parseDouble(amtStr.trim());
            int userId = SessionManager.getCurrentUserId();

            recurringService.addRecurring(userId, title, cat.getId(), amount, freq, mode, nextDate);

            titleField.clear();
            amountField.clear();
            nextDatePicker.setValue(LocalDate.now().plusMonths(1));

            loadRecurringList();
            showAlert("Success", "Recurring expense added!");

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
