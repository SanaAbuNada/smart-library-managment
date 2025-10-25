package controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.persistence.EntityManager;

import util.JpaUtil;
import entities.User;

public class LoginController {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button LoginButton;
    @FXML
    private Hyperlink createAccountLink;

    private static final String SIGNUP_FXML = "/smartlibrarymanager_project/fxml_files/signup.fxml";
    private static final String DASHBOARD_FXML = "/smartlibrarymanager_project/fxml_files/dashboard.fxml";

    @FXML
    private void initialize() {
        if (LoginButton != null) {
            LoginButton.setOnAction(this::handleLogin);
        }
        if (createAccountLink != null) {
            createAccountLink.setOnAction(this::goToSignUp);
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String email = text(emailField).toLowerCase();
        String pass = text(passwordField);

        if (email.isEmpty() || pass.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Missing data", "Please fill Email and Password.");
            return;
        }

        EntityManager em = JpaUtil.getEntityManager();
        try {
            var list = em.createQuery(
                    "SELECT u FROM User u WHERE LOWER(u.email) = :email", User.class)
                    .setParameter("email", email)
                    .getResultList();

            if (list.isEmpty()) {
                alert(Alert.AlertType.WARNING, "Not found", "This email doesn't exist.");
                return;
            }

            User u = list.get(0);
            String storedHash = u.getPasswordHash();
            String enteredHashMd5 = toMD5(pass);

            if (storedHash == null || !enteredHashMd5.equalsIgnoreCase(storedHash)) {
                alert(Alert.AlertType.WARNING, "Wrong password", "Please try again.");
                return;
            }

            AppSession.userName = u.getFirstName();
            AppSession.userEmail = u.getEmail();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(DASHBOARD_FXML));
            Parent root = loader.load();
            DashboardController dc = loader.getController();
            dc.setUser(AppSession.userName, AppSession.userEmail);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Smart Library Manager - Dashboard");
            stage.centerOnScreen();

        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "DB error", e.getMessage());
        } finally {
            em.close();
        }
    }

    @FXML
    private void goToSignUp(ActionEvent e) {
        try {
            goTo(e, SIGNUP_FXML, "Create Your Account");
        } catch (IOException ex) {
            alert(Alert.AlertType.ERROR, "Open Sign Up", ex.getMessage());
        }
    }

    private String toMD5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String text(TextInputControl c) {
        return c.getText() == null ? "" : c.getText().trim();
    }

    private void alert(Alert.AlertType t, String h, String m) {
        Alert a = new Alert(t);
        a.setTitle("Info");
        a.setHeaderText(h);
        a.setContentText(m);
        a.showAndWait();
    }

    private void goTo(ActionEvent e, String fxml, String title) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(fxml));
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.centerOnScreen();
    }
}
