package controllers;

import entities.Book;
import entities.Borrowing;
import entities.Member;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import util.JpaUtil;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ReportsController {

    @FXML private ComboBox<String> cbType;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private ComboBox<Member> cbMember;
    @FXML private TextField tfFilter;
    @FXML private Button btnGenerate, btnCancel, btnBack;
    @FXML private ProgressIndicator pi;
    @FXML private Label lblStatus;
    @FXML private TextArea taOutput;

    private Task<String> currentTask;

    private static final int LOAN_GRACE_DAYS = 14;        
    private static final double DAILY_FINE   = 1.0;       //هان حطينا غرامة دولار لكل يوم تأخير
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    public void initialize() {
        // أنواع التقارير
        cbType.getItems().setAll("Overdue Books", "Borrowing Stats", "Member Activity");

        // تعبئة الأعضاء 
        EntityManager em = null;
        try {
            em = JpaUtil.getEntityManager();
            java.util.List<Member> members = em.createQuery(
                    "SELECT m FROM Member m ORDER BY m.id", Member.class
            ).getResultList();
            cbMember.getItems().setAll(members);
        } catch (Exception ignored) {
        } finally {
            if (em != null) try { em.close(); } catch (Exception ignore) {}
        }

        // عرض الاي دي للميمبر
        cbMember.setButtonCell(new MemberCell());
        cbMember.setCellFactory(v -> new MemberCell());

        // قيم افتراضية
        dpFrom.setValue(LocalDate.now().minusMonths(1));
        dpTo.setValue(LocalDate.now());

        // أزرار
        btnGenerate.setOnAction(e -> onGenerate());
        btnCancel.setOnAction(e -> onCancel());
    }

    /* ===================== Generate / Cancel ===================== */

    private void onGenerate() {
        String type = cbType.getValue();
        if (type == null || type.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a report type.");
            return;
        }

        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            showAlert(Alert.AlertType.WARNING, "Validation", "`From` must be <= `To`.");
            return;
        }

        String textFilter = (tfFilter.getText() == null) ? "" : tfFilter.getText().trim().toLowerCase();
        Member memberFilter = cbMember.getValue();

        // نبني مهمة حسب نوع التقرير
        switch (type) {
            case "Overdue Books":
                currentTask = taskOverdue(from, to, textFilter);
                break;
            case "Borrowing Stats":
                currentTask = taskStats(from, to, textFilter);
                break;
            case "Member Activity":
                currentTask = taskMemberActivity(from, to, memberFilter, textFilter);
                break;
            default:
                return;
        }

        //نعمل بايند
        pi.visibleProperty().bind(currentTask.runningProperty());
        pi.progressProperty().bind(currentTask.progressProperty());
        lblStatus.textProperty().bind(currentTask.messageProperty());
        btnGenerate.disableProperty().bind(currentTask.runningProperty());
        btnCancel.disableProperty().bind(currentTask.runningProperty().not());

        // ما بعد التنفيذ
        currentTask.setOnSucceeded(e -> {
            unbindUI();
            String text = currentTask.getValue();
            taOutput.setText((text == null || text.isBlank()) ? "No records found." : text);
            lblStatus.setText("Done");
        });

        currentTask.setOnCancelled(e -> {
            unbindUI();
            lblStatus.setText("Canceled");
            showAlert(Alert.AlertType.INFORMATION, "Report canceled.", "Report canceled.");
        });

        currentTask.setOnFailed(e -> {
            unbindUI();
            lblStatus.setText("Failed");
            Throwable ex = currentTask.getException();
            showAlert(Alert.AlertType.ERROR, "Error", "Report failed: " + (ex == null ? "" : ex.getMessage()));
        });

        // Multithreading
        new Thread(currentTask, "reports-task").start();
    }

    private void onCancel() {
        if (currentTask != null && currentTask.isRunning()) currentTask.cancel();
    }

    private void unbindUI() {
        try { pi.visibleProperty().unbind(); } catch (Exception ignore) {}
        try { pi.progressProperty().unbind(); } catch (Exception ignore) {}
        try { lblStatus.textProperty().unbind(); } catch (Exception ignore) {}
        try { btnGenerate.disableProperty().unbind(); } catch (Exception ignore) {}
        try { btnCancel.disableProperty().unbind(); } catch (Exception ignore) {}
        pi.setVisible(false);
        pi.setProgress(0);
        btnCancel.setDisable(true);
        btnGenerate.setDisable(false);
    }

    /* ===================== Tasks==================== */

    // 1) Overdue Books
    private Task<String> taskOverdue(LocalDate from, LocalDate to, String ft) {
        return new Task<>() {
            @Override protected String call() {
                updateMessage("Loading overdue...");
                EntityManager em = JpaUtil.getEntityManager();
                try {
             
                    java.util.List<Borrowing> active = em.createQuery(
                            "SELECT b FROM Borrowing b WHERE b.returnDate IS NULL",
                            Borrowing.class
                    ).getResultList();

                    LocalDate today = LocalDate.now();
                    java.util.List<Borrowing> list = new ArrayList<>();

     
                    for (Borrowing b : active) {
                        if (b == null || b.getBorrowDate() == null) continue;

                        // متأخرة؟
                        LocalDate due = b.getBorrowDate().plusDays(LOAN_GRACE_DAYS);
                        if (!due.isBefore(today)) continue;

                        // ضمن المدى؟
                        if (from != null && b.getBorrowDate().isBefore(from)) continue;
                        if (to   != null && b.getBorrowDate().isAfter(to)) continue;

                        //فلترة على العنوان والكاتب
                        if (ft != null && !ft.isBlank()) {
                            Book bk = b.getBook();
                            String author = (bk != null && bk.getAuthor() != null) ? bk.getAuthor().toLowerCase() : "";
                            String title  = (bk != null && bk.getTitle()  != null) ? bk.getTitle().toLowerCase()  : "";
                            if (!(author.contains(ft) || title.contains(ft))) continue;
                        }
                        list.add(b);
                    }

                    StringBuilder out = new StringBuilder();
                    int n = list.size();
                    for (int i = 0; i < n; i++) {
                        if (isCancelled()) return "";
                        Borrowing br = list.get(i);

                        LocalDate due = br.getBorrowDate().plusDays(LOAN_GRACE_DAYS);
                        long daysLate = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(due, today));
                        double fine = daysLate * DAILY_FINE;

                        String memberName = (br.getMember() != null && br.getMember().getName() != null && !br.getMember().getName().isBlank())
                                ? br.getMember().getName()
                                : memberIdOrUnknown(br.getMember());
                        String bookTitle = (br.getBook() != null && br.getBook().getTitle() != null && !br.getBook().getTitle().isBlank())
                                ? br.getBook().getTitle()
                                : "<unknown>";

                        out.append(String.format(
                                "%s – Member: %s – Book: %s – Days Overdue: %d – Fine: $%.2f",
                                DF.format(br.getBorrowDate()), memberName, bookTitle, daysLate, fine
                        )).append("\n");

                        updateMessage("Scanning " + (i + 1) + "/" + n);
                        updateProgress(i + 1, Math.max(1, n));
                    }
                    return out.toString();
                } finally {
                    em.close();
                }
            }
        };
    }

    // 2) Borrowing Stats
    private Task<String> taskStats(LocalDate from, LocalDate to, String ft) {
        return new Task<>() {
            @Override protected String call() {
                updateMessage("Loading stats...");
                EntityManager em = JpaUtil.getEntityManager();
                try {
                    java.util.List<Borrowing> all = em.createQuery(
                            "SELECT b FROM Borrowing b", Borrowing.class
                    ).getResultList();

                    // فلترة يدوية
                    java.util.List<Borrowing> list = new ArrayList<>();
                    for (Borrowing b : all) {
                        if (b == null || b.getBorrowDate() == null) continue;
                        if (from != null && b.getBorrowDate().isBefore(from)) continue;
                        if (to   != null && b.getBorrowDate().isAfter(to)) continue;
                        if (ft != null && !ft.isBlank()) {
                            Book bk = b.getBook();
                            String author = (bk != null && bk.getAuthor() != null) ? bk.getAuthor().toLowerCase() : "";
                            String title  = (bk != null && bk.getTitle()  != null) ? bk.getTitle().toLowerCase()  : "";
                            if (!(author.contains(ft) || title.contains(ft))) continue;
                        }
                        list.add(b);
                    }

                    //تجميعات
                    Map<String, Long> byBook = new HashMap<>();
                    Map<String, Long> byAuthor = new HashMap<>();
                    Map<String, Long> byDate = new HashMap<>();

                    for (Borrowing b : list) {
                        String t = safeTitle(b.getBook());
                        String a = safeAuthor(b.getBook());
                        String d = DF.format(b.getBorrowDate());

                        byBook.put(t, byBook.getOrDefault(t, 0L) + 1);
                        byAuthor.put(a, byAuthor.getOrDefault(a, 0L) + 1);
                        byDate.put(d, byDate.getOrDefault(d, 0L) + 1);
                    }

                    StringBuilder out = new StringBuilder();
                    out.append("== Counts per Book ==\n");   appendCounts(out, byBook);
                    out.append("\n== Counts per Author ==\n"); appendCounts(out, byAuthor);
                    out.append("\n== Counts per Date ==\n");   appendCounts(out, byDate);

                    updateMessage("Scanning " + list.size() + "/" + list.size());
                    updateProgress(1, 1);
                    return out.toString();
                } finally {
                    em.close();
                }
            }
        };
    }

    // 3) Member Activity
    private Task<String> taskMemberActivity(LocalDate from, LocalDate to, Member filterMember, String ft) {
        return new Task<>() {
            @Override protected String call() {
                updateMessage("Loading member activity...");
                EntityManager em = JpaUtil.getEntityManager();
                try {
                    java.util.List<Borrowing> all = em.createQuery(
                            "SELECT b FROM Borrowing b", Borrowing.class
                    ).getResultList();

                    Integer memberId = (filterMember == null) ? null : filterMember.getId();
                    java.util.List<Borrowing> list = new ArrayList<>();

                    for (Borrowing b : all) {
                        if (b == null || b.getBorrowDate() == null) continue;
                        if (from != null && b.getBorrowDate().isBefore(from)) continue;
                        if (to   != null && b.getBorrowDate().isAfter(to)) continue;
                        if (memberId != null) {
                            if (b.getMember() == null || b.getMember().getId() == null) continue;
                            if (!Objects.equals(b.getMember().getId(), memberId)) continue;
                        }
                        if (ft != null && !ft.isBlank()) {
                            Book bk = b.getBook();
                            String author = (bk != null && bk.getAuthor() != null) ? bk.getAuthor().toLowerCase() : "";
                            String title  = (bk != null && bk.getTitle()  != null) ? bk.getTitle().toLowerCase()  : "";
                            if (!(author.contains(ft) || title.contains(ft))) continue;
                        }
                        list.add(b);
                    }

                    // تجميع حسب العضو
                    Map<Member, List<Borrowing>> byMember = new HashMap<>();
                    for (Borrowing b : list) {
                        Member m = b.getMember();
                        if (m == null) continue;
                        byMember.computeIfAbsent(m, k -> new ArrayList<>()).add(b);
                    }

                    StringBuilder out = new StringBuilder();
                    int total = 0;
                    for (List<Borrowing> l : byMember.values()) total += l.size();
                    int scanned = 0;

                    for (Map.Entry<Member, List<Borrowing>> e : byMember.entrySet()) {
                        if (isCancelled()) return "";
                        Member m = e.getKey();
                        List<Borrowing> l = e.getValue();

                        long active = 0;
                        for (Borrowing b : l) if (b.getReturnDate() == null) active++;
                        long returned = l.size() - active;

                        String label = (m.getName() != null && !m.getName().isBlank())
                                ? m.getName()
                                : ("Member #" + (m.getId() == null ? "?" : m.getId()));

                        out.append(String.format("%s — Active: %d, Returned: %d\n", label, active, returned));

                        scanned += l.size();
                        updateMessage("Scanning " + scanned + "/" + Math.max(1, total));
                        updateProgress(scanned, Math.max(1, total));
                    }
                    return out.toString();
                } finally {
                    em.close();
                }
            }
        };
    }

    /* ===================== Helpers بسيطة ===================== */

    private String safeTitle(Book b) {
        return (b == null || b.getTitle() == null || b.getTitle().isBlank()) ? "<unknown>" : b.getTitle();
    }

    private String safeAuthor(Book b) {
        return (b == null || b.getAuthor() == null || b.getAuthor().isBlank()) ? "<unknown>" : b.getAuthor();
    }

    private void appendCounts(StringBuilder out, Map<String, Long> map) {
        // ترتيب تنازلي حسب العدد
        List<Map.Entry<String, Long>> entries = new ArrayList<>(map.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Long> e : entries) {
            out.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
    }

    private String memberIdOrUnknown(Member m) {
        if (m == null) return "<unknown>";
        Integer id = m.getId();
        return (id == null) ? "Member" : ("Member #" + id);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type, msg);
        a.setHeaderText(title);
        a.show();
    }

    // خلية لعرض العضو ببساطة
    private static class MemberCell extends ListCell<Member> {
        @Override protected void updateItem(Member item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText("");
            } else if (item.getName() != null && !item.getName().isBlank()) {
                setText(item.getName());
            } else {
                Integer id = item.getId();
                setText(id == null ? "Member" : ("Member #" + id));
            }
        }
    }

    /* ===================== Back to Dashboard ===================== */
    @FXML
    private void handleBackToDashboard() {
        try { if (currentTask != null && currentTask.isRunning()) currentTask.cancel(); } catch (Exception ignore) {}
        try {
            javafx.scene.Parent root = javafx.fxml.FXMLLoader.load(
                    getClass().getResource("/smartlibrarymanager_project/fxml_files/Dashboard.fxml"));
            javafx.stage.Stage stage = (javafx.stage.Stage) btnBack.getScene().getWindow();
            stage.setTitle("Smart Library Manager - Dashboard");
            stage.setScene(new javafx.scene.Scene(root));
            stage.centerOnScreen();
            stage.show();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Navigation", "Couldn't open Dashboard:\n" + ex.getMessage());
        }
    }
}
