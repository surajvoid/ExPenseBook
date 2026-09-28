package com.expensebook.controller;

import com.expensebook.model.Category;
import com.expensebook.model.User;
import com.expensebook.service.CategoryService;
import com.expensebook.service.UserService;
import com.expensebook.util.DBConnection;
import com.expensebook.util.DateUtil;
import com.expensebook.util.SVGIconUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AdminController implements Initializable {

    // Metrics
    @FXML private Label totalUsersLabel;
    @FXML private Label activeUsersLabel;
    @FXML private Label systemCategoriesCountLabel;
    @FXML private Label dbEngineLabel;

    // Users Table
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> colUserId;
    @FXML private TableColumn<User, String> colUserName;
    @FXML private TableColumn<User, String> colUserEmail;
    @FXML private TableColumn<User, String> colUserRole;
    @FXML private TableColumn<User, String> colUserMode;
    @FXML private TableColumn<User, String> colUserStatus;
    @FXML private TableColumn<User, Void> colUserActions;

    // System Categories Table
    @FXML private TableView<Category> defaultCategoriesTable;
    @FXML private TableColumn<Category, Category> colDefaultCatName;
    @FXML private TableColumn<Category, String> colDefaultCatIcon;

    private final UserService userService = new UserService();
    private final CategoryService categoryService = new CategoryService();
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final ObservableList<Category> catList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUserTable();
        setupCategoryTable();
        loadAdminData();
    }

    private void setupUserTable() {
        colUserId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUserName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colUserEmail.setCellValueFactory(new PropertyValueFactory<>("email"));

        colUserRole.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getRole()));
        colUserRole.setCellFactory(col -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label b = new Label(item);
                    b.getStyleClass().addAll("badge", "ADMIN".equalsIgnoreCase(item) ? "badge-peach" : "badge-sage");
                    setGraphic(b);
                }
            }
        });

        colUserMode.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getFinancialMode().getDisplayName()
        ));

        colUserStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().isActive() ? "Active" : "Disabled"
        ));
        colUserStatus.setCellFactory(col -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label b = new Label(item);
                    b.getStyleClass().addAll("badge", "Active".equals(item) ? "badge-sage" : "badge-coral");
                    setGraphic(b);
                }
            }
        });

        colUserActions.setCellFactory(col -> new TableCell<User, Void>() {
            private final Button btnToggle = new Button();
            private final Button btnDelete = new Button("Delete");
            private final HBox pane = new HBox(6, btnToggle, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);
                btnToggle.getStyleClass().addAll("btn-secondary", "btn-pill-small");
                btnDelete.getStyleClass().addAll("btn-danger", "btn-pill-small");

                btnToggle.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    try {
                        userService.toggleUserStatus(u.getId(), !u.isActive());
                        loadAdminData();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

                btnDelete.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Delete User");
                    alert.setHeaderText("Permanently delete account: " + u.getEmail() + "?");
                    alert.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.OK) {
                            try {
                                userService.deleteUser(u.getId());
                                loadAdminData();
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
                if (empty) {
                    setGraphic(null);
                } else {
                    User u = getTableView().getItems().get(getIndex());
                    btnToggle.setText(u.isActive() ? "Disable" : "Enable");
                    // Cannot delete or disable self
                    boolean isSelf = u.getId() == com.expensebook.util.SessionManager.getCurrentUserId();
                    pane.setDisable(isSelf);
                    setGraphic(pane);
                }
            }
        });

        usersTable.setItems(userList);
    }

    private void setupCategoryTable() {
        colDefaultCatName.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colDefaultCatName.setCellFactory(col -> new TableCell<Category, Category>() {
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

        colDefaultCatIcon.setCellValueFactory(new PropertyValueFactory<>("iconName"));
        defaultCategoriesTable.setItems(catList);
    }

    public void loadAdminData() {
        try {
            int total = userService.getTotalUsers();
            int active = userService.getActiveUsers();
            totalUsersLabel.setText(String.valueOf(total));
            activeUsersLabel.setText(String.valueOf(active));

            List<Category> systemCats = categoryService.getSystemDefaultCategories();
            systemCategoriesCountLabel.setText(String.valueOf(systemCats.size()));
            catList.setAll(systemCats);

            dbEngineLabel.setText(DBConnection.getDatabaseStatusText());

            List<User> users = userService.listAllUsers();
            userList.setAll(users);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
