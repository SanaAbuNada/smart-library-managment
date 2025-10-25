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
import javax.persistence.EntityTransaction;

import util.JpaUtil;             
import entities.User;           

public class SignUpController {

    @FXML
    private TextField firstNameField, lastNameField, emailField;
    @FXML
    private PasswordField passwordField, confirmPasswordField;

    private static final String LOGIN_FXML = "/smartlibrarymanager_project/fxml_files/login.fxml";

    @FXML
    private void handleSignUp(ActionEvent event) {
        String first = t(firstNameField);
        String last  = t(lastNameField);
        String email = t(emailField).toLowerCase();
        String pass  = t(passwordField);
        String pass2 = t(confirmPasswordField);

        // Validations
        if (first.isEmpty() || last.isEmpty() || email.isEmpty() || pass.isEmpty() || pass2.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Missing Fields", "Please fill in all fields.");
            return;
        }
        if (!isEmail(email)) {
            alert(Alert.AlertType.WARNING, "Invalid Email", "Enter a valid email (e.g., name@example.com).");
            return;
        }
        if (!pass.equals(pass2)) {
            alert(Alert.AlertType.WARNING, "Password Mismatch", "Password and Confirm Password do not match.");
            return;
        }

        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            Long cnt = em.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = :e", Long.class)
                    .setParameter("e", email)
                    .getSingleResult();

            if (cnt != null && cnt > 0) {
                alert(Alert.AlertType.WARNING, "Email Exists", "This email is already registered. Try signing in.");
                return;
            }

            String md5 = md5(pass);
            tx.begin();

            User u = new User(); 
            u.setFirstName(first);
            u.setLastName(last);
            u.setEmail(email);
            u.setPasswordHash(md5);

            em.persist(u); // add new user

            tx.commit();

            alert(Alert.AlertType.INFORMATION, "Success", "Account created! You can now log in.");
            goBackToLogin(event);

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            alert(Alert.AlertType.ERROR, "DB Error", e.getMessage());
        } finally {
            em.close();
        }
    }

    @FXML
    private void goBackToLogin(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(LOGIN_FXML));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Smart Library Manager — Login");
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
        } catch (IOException e) {
            alert(Alert.AlertType.ERROR, "Navigation Error", "Couldn't open Login page.\n" + e.getMessage());
        }
    }

    private String t(TextField c) {
        return c.getText() == null ? "" : c.getText().trim();
    }

    private boolean isEmail(String e) {
        int at = e.indexOf('@'), dot = e.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < e.length() - 1;
    }

    private String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void alert(Alert.AlertType type, String header, String msg) {
        Alert a = new Alert(type);
        a.setTitle("Info");
        a.setHeaderText(header);
        a.setContentText(msg);
        a.showAndWait();
    }
}
