package controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.event.ActionEvent;

import entities.Member;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import util.JpaUtil;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

public class MembersController implements Initializable {

    @FXML
    private TextField nameField, contactField;
    @FXML
    private Button addBtn, editBtn, deleteBtn, clearBtn;
    @FXML
    private TableView<Member> table;
    @FXML
    private TableColumn<Member, Integer> idCol;
    @FXML
    private TableColumn<Member, String> nameCol, contactCol;

    @FXML
    private TextField textSearchFeild;
    @FXML
    private Button searchbtn, reserbtn;
    @FXML
    private ComboBox<String> sortComboBox;

    private final ObservableList<Member> data = FXCollections.observableArrayList();

    @FXML
    private void handleAdd() {
        Member sel = table.getSelectionModel().getSelectedItem();
        if (sel != null) {
            handleEdit();
            return;
        }

        String name = t(nameField), contact = t(contactField);
        if (!valid(name, contact)) {
            return;
        }

        boolean dup = data.stream().anyMatch(m
                -> m.getContact().equalsIgnoreCase(contact)
                || (m.getName().equalsIgnoreCase(name) && m.getContact().equalsIgnoreCase(contact)));
        if (dup) {
            warn("Duplicate", "Member already exists.");
            return;
        }

        data.add(new Member(nextId(), name, contact));
        save();           
        clear();
        table.getSelectionModel().selectLast();
        table.scrollTo(data.size() - 1);
    }

    @FXML
    private void handleEdit() {
        Member sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("No Selection", "Select a member to edit.");
            return;
        }

        String name = t(nameField), contact = t(contactField);
        if (!valid(name, contact)) {
            return;
        }

        boolean dup = data.stream().anyMatch(m
                -> m != sel && (m.getContact().equalsIgnoreCase(contact)
                || (m.getName().equalsIgnoreCase(name) && m.getContact().equalsIgnoreCase(contact))));
        if (dup) {
            warn("Duplicate", "Another member has same data.");
            return;
        }

        sel.setName(name);
        sel.setContact(contact);
        table.refresh();
        save();
    }

    @FXML
    private void handleDelete() {
        Member sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("No Selection", "Select a member to delete.");
            return;
        }

        if (memberHasActiveBorrowings(sel.getId())) {
            warn("Cannot Delete", "This member currently has borrowed books.");
            return;
        }

        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete selected member?",
                ButtonType.OK, ButtonType.CANCEL);
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        data.remove(sel);
        save();
        clear();
    }

    @FXML
    private void handleClear(ActionEvent e) {
        clear();
    }

    @FXML
    private void handleBackToDashboard(ActionEvent e) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/smartlibrarymanager_project/fxml_files/dashboard.fxml"));
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Smart Library Manager - Dashboard");
            stage.centerOnScreen();
        } catch (Exception ex) {
            warn("Navigation Error", ex.getMessage());
        }
    }

    @FXML
    private void handleSearchButton(ActionEvent event) {
        String searchText = textSearchFeild.getText().trim();
        if (searchText.isEmpty()) {
            warn("Empty Search", "Please enter a member name to search.");
            return;
        }

        ObservableList<Member> searchResults = data.stream()
                .filter(m -> m.getName().toLowerCase().contains(searchText.toLowerCase()))
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
        if (searchResults.isEmpty()) {
            warn("No Results", "No members found matching your search criteria.");
            return;
        }

        table.setItems(searchResults);
        table.refresh();
        info("Search Results", "Found " + searchResults.size() + " matching member(s).");
    }

    private void sortMembers(String sortStrategy) {
        ObservableList<Member> currentItems = table.getItems();
        switch (sortStrategy) {
            case "Sort by Name (A-Z)" ->
                currentItems.sort((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
            case "Sort by Name (Z-A)" ->
                currentItems.sort((m1, m2) -> m2.getName().compareToIgnoreCase(m1.getName()));
        }
        table.refresh();
    }

    private void load() {
        data.clear();
        EntityManager em = JpaUtil.getEntityManager();
        try {
            data.addAll(
                    em.createQuery("SELECT m FROM Member m ORDER BY m.id", Member.class)
                            .getResultList()
            );
        } catch (Exception ex) {
            warn("DB Load Error", ex.getMessage());
        } finally {
            em.close();
        }
        table.setItems(data);
    }


    private void save() {
        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            for (Member m : data) {
                if (m.getId() == null) {
                    em.persist(m);   // INSERT
                    em.flush();      
                } else {
                    em.merge(m);     // UPDATE
                }
            }

            tx.commit();
        } catch (Exception ex) {
            if (tx.isActive()) {
                tx.rollback();
            }
            warn("DB Save Error", ex.getMessage());
        } finally {
            em.close();
        }

        load();
    }

    private boolean memberHasActiveBorrowings(int memberId) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            var any = em.createQuery(
                    "SELECT 1 FROM Borrowing b WHERE b.member.id = :mid", Integer.class)
                    .setParameter("mid", memberId)
                    .setMaxResults(1)
                    .getResultList();
            return !any.isEmpty();
        } catch (Exception e) {
            return true;
        } finally {
            em.close();
        }
    }

  
    private int nextId() {
        int maxInList = data.stream().mapToInt(m -> m.getId() == null ? 0 : m.getId()).max().orElse(0);

        EntityManager em = JpaUtil.getEntityManager();
        int maxInDb = 0;
        try {
            Integer mx = em.createQuery("SELECT COALESCE(MAX(m.id), 0) FROM Member m", Integer.class)
                    .getSingleResult();
            if (mx != null) {
                maxInDb = mx;
            }
        } catch (Exception ignored) {
        } finally {
            em.close();
        }
        return Math.max(maxInList, maxInDb) + 1;
    }

    private void clear() {
        nameField.clear();
        contactField.clear();
        table.getSelectionModel().clearSelection();
        addBtn.setText("Add");
        toggleButtons();
    }

    private void toggleButtons() {
        boolean hasSel = table.getSelectionModel().getSelectedItem() != null;
        editBtn.setDisable(!hasSel);
        deleteBtn.setDisable(!hasSel);
    }

    private boolean valid(String name, String contact) {
        if (name.isEmpty() || contact.isEmpty()) {
            warn("Missing Fields", "Please fill Name and Contact.");
            return false;
        }
        if (name.contains("|") || contact.contains("|")) {
            warn("Invalid Character", "Character '|' is not allowed.");
            return false;
        }
        boolean phone = contact.matches("\\d{7,15}");
        boolean email = contact.matches(".+@.+\\..+");
        if (!phone && !email) {
            warn("Invalid Contact", "Use phone (7-15 digits) or a valid email.");
            return false;
        }
        return true;
    }

    private String t(TextField f) {
        return f.getText() == null ? "" : f.getText().trim();
    }

    private void warn(String h, String m) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Info");
        a.setHeaderText(h);
        a.setContentText(m);
        a.showAndWait();
    }

    private void info(String h, String m) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Info");
        a.setHeaderText(h);
        a.setContentText(m);
        a.showAndWait();
    }

    @FXML
    private void handleResetBtn(ActionEvent event) {
        textSearchFeild.clear();
        sortComboBox.setValue(null);
        table.setItems(data);
        table.refresh();
        info("Search Reset", "Showing all members.");
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        contactCol.setCellValueFactory(new PropertyValueFactory<>("contact"));
        table.setItems(data);

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, m) -> {
            if (m == null) {
                nameField.clear();
                contactField.clear();
                addBtn.setText("Add");
            } else {
                nameField.setText(m.getName());
                contactField.setText(m.getContact());
                addBtn.setText("Save");
            }
            toggleButtons();
        });
        toggleButtons();

        nameField.setOnAction(e -> handleAdd());
        contactField.setOnAction(e -> handleAdd());

        load();

        searchbtn.setOnAction(this::handleSearchButton);
        sortComboBox.setItems(FXCollections.observableArrayList(
                "Sort by Name (A-Z)", "Sort by Name (Z-A)"));
        sortComboBox.setPromptText("Select sorting option...");
        sortComboBox.setOnAction(e -> {
            String s = sortComboBox.getValue();
            if (s != null) {
                sortMembers(s);
            }
        });
        reserbtn.setOnAction(this::handleResetBtn);
    }
}
