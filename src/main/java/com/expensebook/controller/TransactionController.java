package com.expensebook.controller;

import com.expensebook.model.Category;
import com.expensebook.model.Expense;
import com.expensebook.model.PaymentMode;
import com.expensebook.service.CategoryService;
import com.expensebook.service.ExpenseService;
import com.expensebook.util.CurrencyUtil;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import com.expensebook.util.SessionManager;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class TransactionController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<Category> categoryFilterCombo;
    @FXML private ComboBox<String> paymentFilterCombo;
    @FXML private ComboBox<String> dateRangeCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> sortByCombo;
    @FXML private Label resultsCountLabel;
    @FXML private Label totalFilteredAmountLabel;

    @FXML private TableView<Expense> transactionsTable;
    @FXML private TableColumn<Expense, String> colDate;
    @FXML private TableColumn<Expense, Expense> colCategory;
    @FXML private TableColumn<Expense, String> colDescription;
    @FXML private TableColumn<Expense, String> colPaymentMode;
    @FXML private TableColumn<Expense, String> colAmount;
    @FXML private TableColumn<Expense, Void> colActions;

    private final ExpenseService expenseService = new ExpenseService();
    private final CategoryService categoryService = new CategoryService();
    private final ObservableList<Expense> tableData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFilters();
        setupTableColumns();
        loadTransactions();

        // Reactive reload
        expenseService.addDataChangeListener(this::loadTransactions);
    }

    private void setupFilters() {
        int userId = SessionManager.getCurrentUserId();

        // Categories filter
        try {
            List<Category> cats = new ArrayList<>();
            Category all = new Category();
            all.setId(0);
            all.setName("All Categories");
            cats.add(all);
            cats.addAll(categoryService.getCategoriesForUser(userId));
            categoryFilterCombo.setItems(FXCollections.observableArrayList(cats));
            categoryFilterCombo.getSelectionModel().selectFirst();
            categoryFilterCombo.setCellFactory(lv -> new ListCell<Category>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            categoryFilterCombo.setButtonCell(new ListCell<Category>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Payment filter
        List<String> payments = new ArrayList<>();
        payments.add("All Modes");
        for (PaymentMode pm : PaymentMode.values()) {
            payments.add(pm.getDisplayName());
        }
        paymentFilterCombo.setItems(FXCollections.observableArrayList(payments));
        paymentFilterCombo.getSelectionModel().selectFirst();

        // Date Range preset
        dateRangeCombo.setItems(FXCollections.observableArrayList(
                "This Month", "Last Month", "This Year", "All Time", "Custom Range"
        ));
        dateRangeCombo.getSelectionModel().selectFirst();

        // Sort By
        sortByCombo.setItems(FXCollections.observableArrayList(
                "Newest", "Oldest", "Highest Amount", "Lowest Amount"
        ));
        sortByCombo.getSelectionModel().selectFirst();

        // Listeners
        searchField.textProperty().addListener((obs, oldV, newV) -> loadTransactions());
        categoryFilterCombo.setOnAction(e -> loadTransactions());
        paymentFilterCombo.setOnAction(e -> loadTransactions());
        sortByCombo.setOnAction(e -> loadTransactions());

        dateRangeCombo.setOnAction(e -> {
            String selected = dateRangeCombo.getValue();
            boolean isCustom = "Custom Range".equals(selected);
            startDatePicker.setVisible(isCustom);
            startDatePicker.setManaged(isCustom);
            endDatePicker.setVisible(isCustom);
            endDatePicker.setManaged(isCustom);
            loadTransactions();
        });

        startDatePicker.setOnAction(e -> loadTransactions());
        endDatePicker.setOnAction(e -> loadTransactions());
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(data -> new SimpleStringProperty(DateUtil.formatDate(data.getValue().getExpenseDate())));

        colCategory.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colCategory.setCellFactory(col -> new TableCell<Expense, Expense>() {
            @Override
            protected void updateItem(Expense item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.getChildren().add(SVGIconUtil.createCategoryBadge(item.getCategoryIcon(), item.getCategoryColor(), 24));
                    Label nameLbl = new Label(item.getCategoryName());
                    nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
                    box.getChildren().add(nameLbl);
                    setGraphic(box);
                }
            }
        });

        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        colPaymentMode.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPaymentMode() != null ? data.getValue().getPaymentMode().getDisplayName() : "UPI"
        ));
        colPaymentMode.setCellFactory(col -> new TableCell<Expense, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().addAll("badge", "badge-blue");
                    setGraphic(badge);
                }
            }
        });

        colAmount.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getAmount())));
        colAmount.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");

        // Action buttons: Edit & Delete
        colActions.setCellFactory(col -> new TableCell<Expense, Void>() {
            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Delete");
            private final HBox pane = new HBox(6, btnEdit, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);
                btnEdit.getStyleClass().addAll("btn-secondary", "btn-pill-small");
                btnDelete.getStyleClass().addAll("btn-danger", "btn-pill-small");

                btnEdit.setOnAction(e -> {
                    Expense exp = getTableView().getItems().get(getIndex());
                    if (MainLayoutController.getInstance() != null) {
                        MainLayoutController.getInstance().openExpenseModal(exp);
                    }
                });

                btnDelete.setOnAction(e -> {
                    Expense exp = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete Expense");
                    confirm.setHeaderText("Delete transaction of " + CurrencyUtil.format(exp.getAmount()) + "?");
                    confirm.setContentText("Category: " + exp.getCategoryName() + "\nDate: " + DateUtil.formatDate(exp.getExpenseDate()));
                    confirm.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.OK) {
                            try {
                                expenseService.deleteExpense(exp.getId(), SessionManager.getCurrentUserId());
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

        transactionsTable.setItems(tableData);
    }

    public void loadTransactions() {
        int userId = SessionManager.getCurrentUserId();
        if (userId <= 0) return;

        LocalDate start = null;
        LocalDate end = null;

        String range = dateRangeCombo.getValue();
        LocalDate now = LocalDate.now();

        if ("This Month".equals(range)) {
            start = DateUtil.getStartOfMonth(now.getMonthValue(), now.getYear());
            end = DateUtil.getEndOfMonth(now.getMonthValue(), now.getYear());
        } else if ("Last Month".equals(range)) {
            LocalDate lastMonth = now.minusMonths(1);
            start = DateUtil.getStartOfMonth(lastMonth.getMonthValue(), lastMonth.getYear());
            end = DateUtil.getEndOfMonth(lastMonth.getMonthValue(), lastMonth.getYear());
        } else if ("This Year".equals(range)) {
            start = LocalDate.of(now.getYear(), 1, 1);
            end = LocalDate.of(now.getYear(), 12, 31);
        } else if ("Custom Range".equals(range)) {
            start = startDatePicker.getValue();
            end = endDatePicker.getValue();
        }

        Category selectedCat = categoryFilterCombo.getValue();
        Integer catId = (selectedCat != null && selectedCat.getId() > 0) ? selectedCat.getId() : null;

        String payVal = paymentFilterCombo.getValue();
        String payment = (payVal != null && !"All Modes".equals(payVal)) ? payVal : null;

        String search = searchField.getText();
        String sort = sortByCombo.getValue();

        try {
            List<Expense> list = expenseService.filterExpenses(userId, start, end, catId, payment, search, sort);
            tableData.setAll(list);

            double sum = 0;
            for (Expense exp : list) {
                sum += exp.getAmount();
            }

            resultsCountLabel.setText("Showing " + list.size() + " transactions");
            totalFilteredAmountLabel.setText("Total: " + CurrencyUtil.format(sum));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleResetFilters() {
        searchField.clear();
        categoryFilterCombo.getSelectionModel().selectFirst();
        paymentFilterCombo.getSelectionModel().selectFirst();
        dateRangeCombo.getSelectionModel().selectFirst();
        sortByCombo.getSelectionModel().selectFirst();
        loadTransactions();
    }
}
