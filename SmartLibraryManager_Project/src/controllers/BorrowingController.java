package controllers;

import classes.Book;
import classes.Borrowing;
import classes.Member;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.control.ListCell;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;

import javafx.concurrent.Task;
import util.JpaUtil;

public class BorrowingController implements Initializable {

    // ===== عناصر الواجهة =====
    @FXML
    private ComboBox<Book> bookCombo;
    @FXML
    private ComboBox<Member> memberCombo;
    @FXML
    private DatePicker borrowDate;

    @FXML
    private TableView<Borrowing> historyTable;
    @FXML
    private TableColumn<Borrowing, Integer> hIdCol;
    @FXML
    private TableColumn<Borrowing, String> hBookCol, hMemberCol, hDateCol, hReturnCol;

    @FXML
    private Button borrowBtn;
    @FXML
    private Button markReturnedBtn;

    @FXML
    private TextField textSearchFeild;
    @FXML
    private DatePicker searchByDatePicker;
    @FXML
    private Button searchbtn;
    @FXML
    private ComboBox<String> sortComboBox;
    @FXML
    private Button resetbtn;

    // Responsive UI (مطابقة للـ FXML)
    @FXML
    private ProgressIndicator progressBorrow;
    @FXML
    private Label statusBorrow;
    @FXML
    private Button btnRefreshHistory, btnGenerateReminders, btnCalculateFines, btnCancelTask;
    @FXML
    private TextArea outputBorrow;

    // ===== بيانات الواجهة =====
    private final ObservableList<Book> availableBooks = FXCollections.observableArrayList();
    private final ObservableList<Member> members = FXCollections.observableArrayList();
    private final ObservableList<Borrowing> history = FXCollections.observableArrayList();
    private final Map<Integer, String> bookTitle = new HashMap<>();
    private final Map<Integer, String> memberName = new HashMap<>();
    private final List<Book> allBooks = new ArrayList<>();

    // سياسات (قابلة للتعديل لاحقاً)
    private static final boolean POLICY_MAX_ACTIVE = true;
    private static final int MAX_ACTIVE_BORROWS = 5;
    private static final boolean POLICY_BLOCK_IF_OVERDUE = true;

    // مؤشر المهمة الحالية (للإلغاء)
    private Task<?> currentBorrowTask;

    @Override
    public void initialize(URL url, java.util.ResourceBundle rb) {
        borrowDate.setValue(LocalDate.now());

        // أعمدة الجدول
        hIdCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getId()));
        hBookCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                bookTitle.getOrDefault(c.getValue().getBookId(), "Unknown (" + c.getValue().getBookId() + ")")));
        hMemberCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                memberName.getOrDefault(c.getValue().getMemberId(), "Unknown (" + c.getValue().getMemberId() + ")")));
        hDateCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getBorrowDate() == null ? "" : c.getValue().getBorrowDate().toString()));
        hReturnCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getReturnDate() == null ? "" : c.getValue().getReturnDate().toString()));
        historyTable.setItems(history);

        // تحميل القوائم
        loadMembers();
        loadBooks();
        setupComboRenderers();
        bookCombo.setItems(availableBooks);
        memberCombo.setItems(members);
        loadHistory();

        // بحث/فرز/إعادة ضبط
        searchbtn.setOnAction(this::handleSearchButton);
        sortComboBox.setItems(FXCollections.observableArrayList(
                "Sort by Borrowing Date (Newest First)",
                "Sort by Borrowing Date (Oldest First)"
        ));
        sortComboBox.setOnAction(e -> {
            String s = sortComboBox.getValue();
            if (s != null) {
                sortBorrowingRecords(s);
            }
        });
        resetbtn.setOnAction(this::handleResetBtn);

        if (markReturnedBtn != null) {
            markReturnedBtn.setOnAction(this::handleMarkReturned);
        }
    }

    // ====== الإجراءات الأساسية ======
    @FXML
    private void handleBorrow(ActionEvent e) {
        Book b = bookCombo.getValue();
        Member m = memberCombo.getValue();
        LocalDate d = borrowDate.getValue();

        if (b == null || m == null || d == null) {
            warn("Missing Fields", "Select book, member and date.");
            return;
        }
        if (d.isAfter(LocalDate.now())) {
            warn("Invalid Date", "Future dates are not allowed.");
            return;
        }

        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            // قفل تشارُكي على صف الكتاب لمنع السباق
            entities.Book eb = em.find(entities.Book.class, b.getId(), LockModeType.PESSIMISTIC_WRITE);
            if (eb == null) {
                tx.rollback();
                warn("Book Missing", "Selected book no longer exists.");
                reloadLists();
                return;
            }
            if (!"available".equalsIgnoreCase(eb.getStatus())) {
                tx.rollback();
                warn("Unavailable", "Selected book is already borrowed.");
                reloadLists();
                return;
            }

            entities.Member emem = em.find(entities.Member.class, m.getId());
            if (emem == null) {
                tx.rollback();
                warn("Member Missing", "Selected member no longer exists.");
                reloadLists();
                return;
            }

            // سياسات: حدّ أقصى إعارات نشطة
            Long activeCount = em.createQuery(
                    "SELECT COUNT(br) FROM Borrowing br " +
                            "WHERE br.member.id = :mid AND br.returnDate IS NULL", Long.class)
                    .setParameter("mid", m.getId())
                    .getSingleResult();

            if (POLICY_MAX_ACTIVE && activeCount >= MAX_ACTIVE_BORROWS) {
                tx.rollback();
                warn("Policy Violation",
                        "Member already has " + activeCount + " active borrowings (limit " + MAX_ACTIVE_BORROWS + ").");
                return;
            }

            // سياسات: حظر إذا لدى العضو متأخرات
            Long overdueCount = em.createQuery(
                    "SELECT COUNT(br) FROM Borrowing br " +
                            "WHERE br.member.id = :mid AND br.returnDate IS NULL AND br.borrowDate < :cutoff",
                    Long.class)
                    .setParameter("mid", m.getId())
                    .setParameter("cutoff", LocalDate.now().minusDays(14))
                    .getSingleResult();

            if (POLICY_BLOCK_IF_OVERDUE && overdueCount > 0) {
                tx.rollback();
                warn("Member has overdue items", "Member has overdue items.");
                return;
            }

            entities.Borrowing br = new entities.Borrowing();
            br.setBook(eb);
            br.setMember(emem);
            br.setBorrowDate(d);
            br.setReturnDate(null);
            em.persist(br);

            eb.setStatus("Borrowed");
            em.merge(eb);

            tx.commit();

            allBooks.stream().filter(x -> x.getId() == b.getId()).findFirst().ifPresent(x -> x.setStatus("Borrowed"));
            availableBooks.removeIf(x -> x.getId() == b.getId());
            history.add(0, new Borrowing(br.getId(), b.getId(), m.getId(), d, null));
            info("Success", "Borrow recorded.");

            bookCombo.getSelectionModel().clearSelection();
            memberCombo.getSelectionModel().clearSelection();
            borrowDate.setValue(LocalDate.now());

        } catch (Exception ex) {
            if (tx.isActive()) {
                tx.rollback();
            }
            warn("DB Error", ex.getMessage());
        } finally {
            em.close();
        }
    }

    @FXML
    private void handleMarkReturned(ActionEvent e) {
        Borrowing sel = historyTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("No Selection", "Select a borrowing record from the table.");
            return;
        }
        if (sel.getReturnDate() != null) {
            warn("Already Returned", "This record is already marked as returned.");
            return;
        }

        LocalDate today = LocalDate.now();

        EntityManager em = JpaUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            entities.Borrowing br = em.find(entities.Borrowing.class, sel.getId());
            if (br == null) {
                tx.rollback();
                warn("Missing", "Borrowing record not found.");
                return;
            }

            br.setReturnDate(today);
            em.merge(br);

            entities.Book eb = em.find(entities.Book.class, sel.getBookId());
            if (eb != null) {
                eb.setStatus("Available");
                em.merge(eb);
            }

            tx.commit();

            sel.setReturnDate(today);
            historyTable.refresh();

            allBooks.stream().filter(x -> x.getId() == sel.getBookId()).findFirst()
                    .ifPresent(x -> x.setStatus("Available"));

            boolean exists = availableBooks.stream().anyMatch(x -> x.getId() == sel.getBookId());
            if (!exists) {
                String title = bookTitle.getOrDefault(sel.getBookId(), "Book #" + sel.getBookId());
                availableBooks.add(new Book(sel.getBookId(), title, "", "Available"));
            }

            info("Success", "Book marked as returned.");
        } catch (Exception ex) {
            if (tx.isActive()) {
                tx.rollback();
            }
            warn("DB Error", ex.getMessage());
        } finally {
            em.close();
        }
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

    // ====== تحميل البيانات ======
    private void loadBooks() {
        allBooks.clear();
        availableBooks.clear();
        bookTitle.clear();
        EntityManager em = JpaUtil.getEntityManager();
        try {
            var list = em.createQuery("SELECT b FROM Book b ORDER BY b.id", entities.Book.class).getResultList();
            for (entities.Book eb : list) {
                Book bk = new Book(eb.getId(), eb.getTitle(), eb.getAuthor(), eb.getStatus());
                allBooks.add(bk);
                bookTitle.put(eb.getId(), eb.getTitle());
                if ("available".equalsIgnoreCase(eb.getStatus())) {
                    availableBooks.add(bk);
                }
            }
        } catch (Exception ignored) {
        } finally {
            em.close();
        }
    }

    private void loadMembers() {
        members.clear();
        memberName.clear();
        EntityManager em = JpaUtil.getEntityManager();
        try {
            var list = em.createQuery("SELECT m FROM Member m ORDER BY m.id", entities.Member.class).getResultList();
            for (entities.Member mm : list) {
                members.add(new Member(mm.getId(), mm.getName(), mm.getContact()));
                memberName.put(mm.getId(), mm.getName());
            }
        } catch (Exception ignored) {
        } finally {
            em.close();
        }
    }

    private void loadHistory() {
        history.clear();
        EntityManager em = JpaUtil.getEntityManager();
        try {
            var list = em.createQuery("SELECT br FROM Borrowing br ORDER BY br.id DESC", entities.Borrowing.class)
                    .getResultList();
            for (entities.Borrowing br : list) {
                int bId = br.getBook() == null ? -1 : br.getBook().getId();
                int mId = br.getMember() == null ? -1 : br.getMember().getId();
                history.add(new Borrowing(br.getId(), bId, mId, br.getBorrowDate(), br.getReturnDate()));
            }
            historyTable.refresh();
        } catch (Exception ignored) {
        } finally {
            em.close();
        }
    }

    private void reloadLists() {
        loadBooks();
        loadHistory();
        historyTable.refresh();
    }

    private void setupComboRenderers() {
        // Book
        bookCombo.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Book item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getTitle());
            }
        });
        ListCell<Book> bookBtnCell = new ListCell<>() {
            @Override
            protected void updateItem(Book item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getTitle());
            }
        };
        bookCombo.setButtonCell(bookBtnCell);

        // Member
        memberCombo.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getName());
            }
        });
        ListCell<Member> memBtnCell = new ListCell<>() {
            @Override
            protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId() + " — " + item.getName());
            }
        };
        memberCombo.setButtonCell(memBtnCell);
    }

    // ====== مساعدات عامة ======
    private void warn(String h, String m) {
        show(Alert.AlertType.WARNING, h, m);
    }

    private void info(String h, String m) {
        show(Alert.AlertType.INFORMATION, h, m);
    }

    private void show(Alert.AlertType t, String h, String m) {
        Alert a = new Alert(t);
        a.setTitle("Info");
        a.setHeaderText(h);
        a.setContentText(m);
        a.showAndWait();
    }

    // ====== البحث والفرز ======
    @FXML
    private void handleSearchButton(ActionEvent e) {
        String q = textSearchFeild.getText() == null ? "" : textSearchFeild.getText().trim();
        LocalDate dt = searchByDatePicker.getValue();

        if ((q == null || q.isEmpty()) && dt == null) {
            warn("No Input to search", "Please enter a search term or select a date to search.");
            return;
        }

        ObservableList<Borrowing> results = history.stream().filter(bor -> {
            boolean okText = true, okDate = true;

            if (!q.isEmpty()) {
                if (q.matches("\\d+")) {
                    okText = String.valueOf(bor.getMemberId()).equals(q);
                } else {
                    String title = bookTitle.getOrDefault(bor.getBookId(), "");
                    okText = title != null && title.toLowerCase().contains(q.toLowerCase());
                }
            }
            if (dt != null) {
                okDate = dt.equals(bor.getBorrowDate());
            }
            return okText && okDate;
        }).collect(Collectors.toCollection(FXCollections::observableArrayList));

        if (results.isEmpty()) {
            warn("No Results", "No borrowing records found matching your search criteria.");
            return;
        }
        historyTable.setItems(results);
        historyTable.refresh();
        info("Search Results", "Found " + results.size() + " matching borrowing record(s).");
    }

    private void sortBorrowingRecords(String key) {
        ObservableList<Borrowing> list = historyTable.getItems();
        switch (key) {
            case "Sort by Borrowing Date (Newest First)":
                list.sort((a, b) -> {
                    LocalDate da = a.getBorrowDate(), db = b.getBorrowDate();
                    if (da == null && db == null) {
                        return 0;
                    }
                    if (da == null) {
                        return 1;
                    }
                    if (db == null) {
                        return -1;
                    }
                    return db.compareTo(da);
                });
                break;
            case "Sort by Borrowing Date (Oldest First)":
                list.sort((a, b) -> {
                    LocalDate da = a.getBorrowDate(), db = b.getBorrowDate();
                    if (da == null && db == null) {
                        return 0;
                    }
                    if (da == null) {
                        return 1;
                    }
                    if (db == null) {
                        return -1;
                    }
                    return da.compareTo(db);
                });
                break;
        }
        historyTable.refresh();
    }

    @FXML
    private void handleResetBtn(ActionEvent event) {
        textSearchFeild.clear();
        searchByDatePicker.setValue(null);
        sortComboBox.setValue(null);
        historyTable.setItems(history);
        historyTable.refresh();
        info("Search Reset", "Showing all borrowing records.");
    }

    // ====== نسخة مبسطة لتشغيل المهام الخلفية ======
    private void lockUI(boolean running) {
        if (progressBorrow != null) {
            progressBorrow.setVisible(running);
        }
        if (btnCancelTask != null) {
            btnCancelTask.setVisible(running);
        }
        if (btnRefreshHistory != null) {
            btnRefreshHistory.setDisable(running);
        }
        if (btnGenerateReminders != null) {
            btnGenerateReminders.setDisable(running);
        }
        if (btnCalculateFines != null) {
            btnCalculateFines.setDisable(running);
        }
    }

    private <T> void runBorrowTask(Task<T> task, Consumer<T> onSuccess) {
        // قبل البدء
        lockUI(true);
        if (statusBorrow != null) {
            statusBorrow.setText("Working...");
        }
        currentBorrowTask = task;

        // نجاح
        task.setOnSucceeded(e -> {
            lockUI(false);
            if (statusBorrow != null) {
                statusBorrow.setText("Done.");
            }
            if (onSuccess != null) {
                onSuccess.accept(task.getValue());
            }
            currentBorrowTask = null;
        });

        // فشل
        task.setOnFailed(e -> {
            lockUI(false);
            if (statusBorrow != null) {
                statusBorrow.setText("Failed: " + task.getException().getMessage());
            }
            currentBorrowTask = null;
        });

        // إلغاء
        task.setOnCancelled(e -> {
            lockUI(false);
            if (statusBorrow != null) {
                statusBorrow.setText("Canceled.");
            }
            currentBorrowTask = null;
        });

        new Thread(task, "BorrowingTask").start();
    }

    @FXML
    private void handleCancelBorrowTask() {
        if (currentBorrowTask != null) {
            currentBorrowTask.cancel();
        }
    }

    // ====== الهاندلرز الخاصة بالمهام ======
    @FXML
    private void handleRefreshHistory() {
        tasks.LoadHistoryTask task = new tasks.LoadHistoryTask();
        runBorrowTask(task, data -> {
            history.clear();
            for (entities.Borrowing br : data) {
                int bId = br.getBook() == null ? -1 : br.getBook().getId();
                int mId = br.getMember() == null ? -1 : br.getMember().getId();
                history.add(new Borrowing(br.getId(), bId, mId, br.getBorrowDate(), br.getReturnDate()));
            }
            historyTable.setItems(history);
            historyTable.refresh();
            if (outputBorrow != null) {
                outputBorrow.appendText("History refreshed. Records: " + data.size() + "\n");
            }
        });
    }

    @FXML
    private void handleGenerateReminders() {
        if (outputBorrow != null) {
            outputBorrow.clear();
        }
        tasks.GenerateRemindersTask task = new tasks.GenerateRemindersTask();
        runBorrowTask(task, text -> {
            if (text == null || text.isBlank()) {
                text = "No upcoming due items.\n";
            }
            if (outputBorrow != null) {
                outputBorrow.appendText(text);
            }
        });
    }

    @FXML
    private void handleCalculateFines() {
        if (outputBorrow != null) {
            outputBorrow.clear();
        }
        tasks.CalculateFinesTask task = new tasks.CalculateFinesTask();
        runBorrowTask(task, text -> {
            if (outputBorrow != null) {
                outputBorrow.appendText(text);
            }
        });
    }

}
