package controllers;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import entities.Book;               
import util.JpaUtil;                
import javax.persistence.EntityManager;

import Repositories.BookRepo;       

public class BooksController implements Initializable {

    @FXML
    private TextField titleField, authorField;
    @FXML
    private ComboBox<String> statusCombo;
    @FXML
    private Button addBtn, editBtn, deleteBtn;
    @FXML
    private TableView<Book> table;
    @FXML
    private TableColumn<Book, Integer> idCol;
    @FXML
    private TableColumn<Book, String> titleCol, authorCol, statusCol;

    @FXML
    private TextField textSearchFeild;
    @FXML
    private Button searchbtn, reserbtn;
    @FXML
    private ComboBox<String> sortComboBox;

    private final ObservableList<Book> data = FXCollections.observableArrayList();


    private final BookRepo bookRepo = new BookRepo();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        statusCombo.setItems(FXCollections.observableArrayList("Available", "Borrowed"));
        statusCombo.getSelectionModel().selectFirst();

        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        table.setItems(data);

        textSearchFeild.setPromptText("Enter book title");

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, v) -> {
            if (v == null) {
                clearFields();
            } else {
                titleField.setText(v.getTitle());
                authorField.setText(v.getAuthor());
                statusCombo.getSelectionModel().select(v.getStatus());
            }
            toggleButtons();
        });
        toggleButtons();

        refreshTable();

        searchbtn.setOnAction(this::handleSearchButton);
        reserbtn.setOnAction(this::handleResetBtn);

        sortComboBox.setItems(FXCollections.observableArrayList(
                "Sort by Title (A-Z)",
                "Sort by Title (Z-A)",
                "Sort by Author (A-Z)",
                "Sort by Author (Z-A)"
        ));
        sortComboBox.setOnAction(e -> {
            String key = sortComboBox.getValue();
            if (key != null) {
                applySort(key);
            }
        });
    }

    @FXML
    private void handleAdd() {
        Book sel = table.getSelectionModel().getSelectedItem();
        String title = t(titleField), author = t(authorField), status = statusCombo.getValue();

        if (sel != null) {
            handleEdit();
            return;
        }
        if (!valid(title, author, status)) {
            return;
        }
        if (existsTitle(title, -1)) {
            warn("Duplicate Title", "This title already exists.");
            return;
        }

        try {
            Book b = new Book(title, author);
            b.setStatus(status);
            bookRepo.add(b);                 
            info("Success", "Book added.");
            clearAndUnselect();
            refreshTable();
        } catch (Exception e) {
            error("Insert Error", e.getMessage());
        }
    }

    @FXML
    private void handleEdit() {
        Book sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("No Selection", "Select a book to edit.");
            return;
        }

        String title = t(titleField), author = t(authorField), status = statusCombo.getValue();
        if (!valid(title, author, status)) {
            return;
        }
        if (existsTitle(title, sel.getId())) {
            warn("Duplicate Title", "Another book has the same title.");
            return;
        }

        try {
            sel.setTitle(title);
            sel.setAuthor(author);
            sel.setStatus(status);
            bookRepo.update(sel);            // JPA: تحديث
            info("Updated", "Book updated.");
            clearAndUnselect();
            refreshTable();
        } catch (Exception e) {
            error("Update Error", e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        Book sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("No Selection", "Select a book to delete.");
            return;
        }

        if (hasActiveBorrow(sel.getId())) {
            warn("Can't delete", "This book is currently borrowed. Please mark it as returned first.");
            return;
        }

        try {
            bookRepo.deleteById(sel.getId()); // JPA: حذف
            info("Deleted", "Book deleted.");
            clearAndUnselect();
            refreshTable();
        } catch (Exception e) {
            error("Delete Error", e.getMessage());
        }
    }

    @FXML
    private void handleSearchButton(ActionEvent event) {
        String q = t(textSearchFeild);
        if (q.isEmpty()) {
            warn("Empty Search", "Please enter a book title to search.");
            return;
        }

        EntityManager em = JpaUtil.getEntityManager();
        try {
            List<Book> list = em.createQuery(
                    "SELECT b FROM Book b WHERE LOWER(b.title) LIKE :q ORDER BY b.title",
                    Book.class
            )
                    .setParameter("q", "%" + q.toLowerCase() + "%")
                    .getResultList();

            data.setAll(list);
            table.setItems(data);
            table.refresh();
            info("Search Results", "Found " + data.size() + " matching book(s).");
        } catch (Exception e) {
            error("Search Error", e.getMessage());
        } finally {
            em.close();
        }
    }

    @FXML
    private void handleResetBtn(ActionEvent event) {
        textSearchFeild.clear();
        sortComboBox.setValue(null);
        refreshTable();
        info("Search Reset", "Showing all books.");
    }

private void applySort(String key) {
    if (key == null || key.isBlank()) {
        return;
    }

    String jpqlOrder = switch (key) {
        case "Sort by Title (A-Z)" -> "ORDER BY b.title ASC";
        case "Sort by Title (Z-A)" -> "ORDER BY b.title DESC";
        case "Sort by Author (A-Z)" -> "ORDER BY b.author ASC";
        case "Sort by Author (Z-A)" -> "ORDER BY b.author DESC";
        default -> "ORDER BY b.id DESC";
    };

    EntityManager em = JpaUtil.getEntityManager();
    try {
        List<Book> list = em.createQuery(
                "SELECT b FROM Book b " + jpqlOrder, Book.class
        ).getResultList();

        data.setAll(list);
        table.refresh();
    } catch (Exception e) {
        error("Sort Error", e.getMessage());
    } finally {
        em.close(); 
    }
}


    private void refreshTable() {
        try {
            data.setAll(bookRepo.findAll());
        } catch (Exception e) {
            error("Load Error", e.getMessage());
        }
    }

    private boolean existsTitle(String title, int ignoreId) {
        String t = title == null ? "" : title.trim().toLowerCase();
        EntityManager em = JpaUtil.getEntityManager();
        try {
            if (ignoreId <= 0) {
                Long cnt = em.createQuery(
                        "SELECT COUNT(b) FROM Book b WHERE LOWER(b.title) = :t", Long.class)
                        .setParameter("t", t)
                        .getSingleResult();
                return cnt != null && cnt > 0;
            } else {
                Long cnt = em.createQuery(
                        "SELECT COUNT(b) FROM Book b WHERE LOWER(b.title) = :t AND b.id <> :id",
                        Long.class)
                        .setParameter("t", t)
                        .setParameter("id", ignoreId)
                        .getSingleResult();
                return cnt != null && cnt > 0;
            }
        } catch (Exception e) {
            return false;
        } finally {
            em.close();
        }
    }

    private boolean hasActiveBorrow(int bookId) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            List<Integer> any = em.createQuery(
                    "SELECT 1 FROM Borrowing br WHERE br.book.id = :bid", Integer.class)
                    .setParameter("bid", bookId)
                    .setMaxResults(1)
                    .getResultList();
            return !any.isEmpty();
        } catch (Exception e) {
            return true;
        } finally {
            em.close();
        }
    }

    private boolean valid(String title, String author, String status) {
        if (title.isEmpty() || author.isEmpty() || status == null || status.isBlank()) {
            warn("Missing Fields", "Please fill Title, Author and Status.");
            return false;
        }
        if (!author.matches("[\\p{L} .'-]+")) {
            warn("Invalid Author", "Author must contain letters only.");
            return false;
        }
        if (!status.equals("Available") && !status.equals("Borrowed")) {
            warn("Invalid Status", "Status must be Available or Borrowed.");
            return false;
        }
        return true;
    }

    private void clearFields() {
        titleField.clear();
        authorField.clear();
        if (!statusCombo.getItems().isEmpty()) {
            statusCombo.getSelectionModel().selectFirst();
        }
    }

    private void clearAndUnselect() {
        clearFields();
        table.getSelectionModel().clearSelection();
        toggleButtons();
    }

    private void toggleButtons() {
        boolean has = table.getSelectionModel().getSelectedItem() != null;
        addBtn.setText(has ? "Save" : "Add");
        editBtn.setDisable(!has);
        deleteBtn.setDisable(!has);
    }

    private String t(TextField c) {
        return c.getText() == null ? "" : c.getText().trim();
    }

    private void warn(String h, String m) {
        alert(Alert.AlertType.WARNING, h, m);
    }

    private void info(String h, String m) {
        alert(Alert.AlertType.INFORMATION, h, m);
    }

    private void error(String h, String m) {
        alert(Alert.AlertType.ERROR, h, m);
    }

    private void alert(Alert.AlertType t, String h, String m) {
        Alert a = new Alert(t);
        a.setTitle("Info");
        a.setHeaderText(h);
        a.setContentText(m);
        a.showAndWait();
    }

    @FXML
    private void handleBackToDashboard(ActionEvent e) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/smartlibrarymanager_project/fxml_files/dashboard.fxml"));
            Stage st = (Stage) ((Node) e.getSource()).getScene().getWindow();
            st.setScene(new Scene(root));
            st.centerOnScreen();
        } catch (Exception ex) {
            warn("Navigation Error", ex.getMessage());
        }
    }
}
