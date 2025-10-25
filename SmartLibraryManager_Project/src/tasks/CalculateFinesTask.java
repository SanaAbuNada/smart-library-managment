package tasks;

import entities.Borrowing;
import util.JpaUtil;
import javafx.concurrent.Task;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class CalculateFinesTask extends Task<String> {
    @Override
    protected String call() {
        updateMessage("Calculating fines...");
        EntityManager em = null;
        try {
            em = JpaUtil.getEntityManager();
            LocalDate today = LocalDate.now();

            List<Borrowing> active = em.createQuery(
                    "SELECT br FROM Borrowing br " +
                    "WHERE br.returnDate IS NULL " +
                    "ORDER BY br.id DESC",
                    Borrowing.class).getResultList();

            if (isCancelled()) return "Canceled.\n";
            if (active.isEmpty()) return "No fines due.\n";

            StringBuilder sb = new StringBuilder();
            int i = 0, total = Math.max(active.size(), 1);
            int countOverdue = 0;

            for (Borrowing br : active) {
                if (isCancelled()) return "Canceled.\n";
                i++; updateProgress(i, total);
                updateMessage("Processing " + i + "/" + total);

                LocalDate borrow = br.getBorrowDate();
                if (borrow == null) continue;

                long daysSince = ChronoUnit.DAYS.between(borrow, today);
                long overdue = daysSince - 14;
                if (overdue > 0) {
                    countOverdue++;
                    sb.append(String.format(
                        "%s – Member: %s – Book: %s – Days Overdue: %d – Fine: $%.2f%n",
                        today,
                        br.getMember().getName(),
                        br.getBook().getTitle(),
                        overdue,
                        (double) overdue
                    ));
                }
            }

            updateMessage("Done.");
            return countOverdue == 0 ? "No fines due.\n" : sb.toString();

        } finally {
            if (em != null && em.isOpen()) em.close();
        }
    }
}
