package controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;

import javax.persistence.EntityManager;

import util.JpaUtil;

public class DashboardController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Button btnReports;

    private static final String BOOKS_FXML = "/smartlibrarymanager_project/fxml_files/BooksPage.fxml";
    private static final String MEMBERS_FXML = "/smartlibrarymanager_project/fxml_files/MembersPage.fxml";
    private static final String BORROW_FXML = "/smartlibrarymanager_project/fxml_files/BorrowingPage.fxml";
    private static final String REPORTS_FXML = "/smartlibrarymanager_project/fxml_files/Reports.fxml";
    private static final String LOGIN_FXML = "/smartlibrarymanager_project/fxml_files/login.fxml";

    @FXML
    public void initialize() {
        String email = AppSession.userEmail;
        String display = (email == null || email.isBlank())
                ? "" : fetchFirstNameByEmail(email);

        if (display == null || display.isBlank()) {
            display = (AppSession.userName != null && !AppSession.userName.isBlank())
                    ? AppSession.userName : (email == null ? "" : email);
        }

        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome, " + display + "!");
        }
    }

    private String fetchFirstNameByEmail(String email) {
        EntityManager em = JpaUtil.getEntityManager();
        try {
            var list = em.createQuery(
                    "SELECT u.firstName FROM User u WHERE LOWER(u.email) = :e",
                    String.class
            )
                    .setParameter("e", email.toLowerCase())
                    .setMaxResults(1)
                    .getResultList();

            return list.isEmpty() ? null : list.get(0);
        } catch (Exception ignored) {
            return null;
        } finally {
            em.close();
        }
    }

    /* ===================== Navigation handlers ===================== */
    @FXML
    private void handleOpenBooks(ActionEvent e) {
        goTo(e, BOOKS_FXML, "Books");
    }

    @FXML
    private void handleOpenMembers(ActionEvent e) {
        goTo(e, MEMBERS_FXML, "Members");
    }

    @FXML
    private void handleOpenBorrowing(ActionEvent e) {
        goTo(e, BORROW_FXML, "Borrowing");
    }

    @FXML
    private void handleOpenReports(ActionEvent e) {
        goTo(e, REPORTS_FXML, "Reports");
    }

    @FXML
    private void handleSignOut(ActionEvent e) {
        AppSession.clear();
        goTo(e, LOGIN_FXML, "Smart Library Manager - Login");
    }

    private void goTo(ActionEvent event, String fxml, String title) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Couldn't open: " + fxml + "\n" + ex.getMessage())
                    .showAndWait();
        }
    }

    /* ===================== Session update ===================== */
    public void setUser(String name, String email) {
        AppSession.userName = name;
        AppSession.userEmail = email;
        if (welcomeLabel != null && (name != null && !name.isBlank())) {
            welcomeLabel.setText("Welcome, " + name + "!");
        }
    }
}
