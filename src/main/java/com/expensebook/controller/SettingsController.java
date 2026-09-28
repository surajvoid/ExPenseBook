package com.expensebook.controller;

import com.expensebook.model.Category;
import com.expensebook.model.FinancialMode;
import com.expensebook.model.User;
import com.expensebook.service.CategoryService;
import com.expensebook.service.UserService;
import com.expensebook.util.DBConnection;
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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    // Profile
    @FXML private TextField profileNameField;
    @FXML private TextField profileEmailField;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private Button btnSaveProfile;

    // Change Password
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button btnChangePassword;

    // Financial Mode Switching
    @FXML private RadioButton radioModeTrackOnly;
    @FXML private RadioButton radioModeBudget;
    @FXML private ToggleGroup modeToggleGroup;
    @FXML private Button btnSaveMode;

    // Custom Categories
    @FXML private TextField newCatNameField;
    @FXML private ComboBox<String> newCatIconCombo;
    @FXML private ComboBox<String> newCatColorCombo;
    @FXML private Button btnAddCategory;
    @FXML private TableView<Category> categoriesTable;
    @FXML private TableColumn<Category, Category> colCatName;
    @FXML private TableColumn<Category, String> colCatType;
    @FXML private TableColumn<Category, Void> colCatAction;

    // Database Connection
    @FXML private TextField dbHostField;
    @FXML private TextField dbPortField;
    @FXML private TextField dbNameField;
    @FXML private TextField dbUserField;
    @FXML private PasswordField dbPasswordField;
    @FXML private Label dbStatusLabel;
    @FXML private Button btnTestDB;
    @FXML private Button btnSaveDB;

    private final UserService userService = new UserService();
    private final CategoryService categoryService = new CategoryService();
    private final ObservableList<Category> categoryList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupProfile();
        setupModeSwitching();
        setupCategoryManager();
        setupDatabaseSettings();
    }

    private void setupProfile() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            profileNameField.setText(user.getFullName());
            profileEmailField.setText(user.getEmail());
            profileEmailField.setDisable(true); // Email is fixed identifier
        }

        currencyCombo.setItems(FXCollections.observableArrayList("₹", "$", "€", "£", "¥"));
        currencyCombo.getSelectionModel().select(SessionManager.getCurrentCurrency());
    }

    private void setupModeSwitching() {
        boolean isBudget = SessionManager.isBudgetMode();
        radioModeBudget.setSelected(isBudget);
        radioModeTrackOnly.setSelected(!isBudget);
    }

    private void setupCategoryManager() {
        newCatIconCombo.setItems(FXCollections.observableArrayList(
                "FOOD", "GROCERIES", "TRAVEL", "SHOPPING", "EDUCATION", "HEALTH",
                "ENTERTAINMENT", "BILLS", "RECHARGE", "RENT", "FITNESS", "PERSONAL_CARE",
                "SUBSCRIPTION", "ELECTRONICS", "GIFTS", "OTHERS"
        ));
        newCatIconCombo.getSelectionModel().select("OTHERS");

        newCatColorCombo.setItems(FXCollections.observableArrayList(
                "#78BFA0", "#A9CFE0", "#F5C6A5", "#C9B9E8", "#E88C8C", "#71807A"
        ));
        newCatColorCombo.getSelectionModel().selectFirst();

        colCatName.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colCatName.setCellFactory(col -> new TableCell<Category, Category>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.getChildren().add(SVGIconUtil.createCategoryBadge(item.getIconName(), item.getColor(), 24));
                    Label l = new Label(item.getName());
                    l.setStyle("-fx-font-weight: bold; -fx-text-fill: #26332F;");
                    box.getChildren().add(l);
                    setGraphic(box);
                }
            }
        });

        colCatType.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().isDefault() ? "Default" : "Custom"
        ));

        colCatAction.setCellFactory(col -> new TableCell<Category, Void>() {
            private final Button btnDel = new Button("Delete");
            {
                btnDel.getStyleClass().addAll("btn-danger", "btn-pill-small");
                btnDel.setOnAction(e -> {
                    Category cat = getTableView().getItems().get(getIndex());
                    if (cat.isDefault()) {
                        showAlert("System Default", "Default categories cannot be deleted.");
                        return;
                    }
                    try {
                        categoryService.deleteCategory(cat.getId(), SessionManager.getCurrentUserId());
                        loadCategories();
                    } catch (Exception ex) {
                        showAlert("Error", ex.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Category cat = getTableView().getItems().get(getIndex());
                    setGraphic(cat.isDefault() ? null : btnDel);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        categoriesTable.setItems(categoryList);
        loadCategories();
    }

    private void loadCategories() {
        int userId = SessionManager.getCurrentUserId();
        try {
            List<Category> list = categoryService.getCategoriesForUser(userId);
            categoryList.setAll(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupDatabaseSettings() {
        dbHostField.setText("localhost");
        dbPortField.setText("3306");
        dbNameField.setText("expensebook");
        dbUserField.setText("root");
        dbStatusLabel.setText("Current: " + DBConnection.getDatabaseStatusText());
    }

    @FXML
    private void handleSaveProfile() {
        String name = profileNameField.getText();
        String currency = currencyCombo.getValue();

        if (!ValidationUtil.isNonEmpty(name)) {
            showAlert("Invalid Name", "Full name cannot be empty.");
            return;
        }

        try {
            userService.updateProfile(SessionManager.getCurrentUserId(), name, currency);
            showAlert("Success", "Profile updated successfully!");
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    private void handleChangePassword() {
        String curPass = currentPasswordField.getText();
        String newPass = newPasswordField.getText();
        String confPass = confirmPasswordField.getText();

        try {
            userService.changePassword(SessionManager.getCurrentUserId(), curPass, newPass, confPass);
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            showAlert("Success", "Password updated successfully!");
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    private void handleSaveMode() {
        FinancialMode newMode = radioModeBudget.isSelected() ?
                FinancialMode.TRACK_AND_BUDGET : FinancialMode.TRACK_ONLY;

        try {
            userService.switchFinancialMode(SessionManager.getCurrentUserId(), newMode);
            showAlert("Mode Updated", "Financial mode switched to: " + newMode.getDisplayName());
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    private void handleAddCategory() {
        String name = newCatNameField.getText();
        String icon = newCatIconCombo.getValue();
        String color = newCatColorCombo.getValue();

        if (!ValidationUtil.isNonEmpty(name)) {
            showAlert("Invalid Name", "Category name cannot be empty.");
            return;
        }

        try {
            categoryService.addCustomCategory(SessionManager.getCurrentUserId(), name, icon, color);
            newCatNameField.clear();
            loadCategories();
            showAlert("Success", "Custom category '" + name + "' added!");
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    private void handleTestDatabase() {
        String host = dbHostField.getText();
        int port = Integer.parseInt(dbPortField.getText().trim());
        String db = dbNameField.getText();
        String user = dbUserField.getText();
        String pass = dbPasswordField.getText();

        boolean ok = DBConnection.testConnection(host, port, db, user, pass);
        if (ok) {
            showAlert("Connection Success", "Successfully connected to MySQL database: " + db);
        } else {
            showAlert("Connection Failed", "Could not connect to MySQL with the provided credentials. Please check host, port, user and password.");
        }
    }

    @FXML
    private void handleSaveDatabase() {
        String host = dbHostField.getText();
        int port = Integer.parseInt(dbPortField.getText().trim());
        String db = dbNameField.getText();
        String user = dbUserField.getText();
        String pass = dbPasswordField.getText();

        DBConnection.saveProperties(host, port, db, user, pass);
        dbStatusLabel.setText("Current: " + DBConnection.getDatabaseStatusText());
        showAlert("Settings Saved", "Database connection configuration saved.");
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
